package com.revia.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.revia.R
import com.revia.data.ServiceLocator
import com.revia.data.db.Interruption
import com.revia.util.AppFilter
import com.revia.util.AppInfo
import com.revia.data.preferences.UserPreferences
import com.revia.ui.overlay.ResumptionCardActivity
import com.revia.util.PermissionUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "revia_detection"
private const val NOTIFICATION_ID = 1
private const val POLL_INTERVAL_MILLIS = 1000L
private const val QUERY_OVERLAP_MILLIS = 10_000L

// A dismissed card is not a resolved one. Someone still sitting in the app that pulled
// them away this long later has not gone back to the task, so it is raised again.
private const val RENAG_INTERVAL_MILLIS = 5 * 60 * 1000L

/**
 * Watches foreground app changes. Leaving a trackable app captures its on-screen
 * context and immediately raises a card over wherever the user landed.
 *
 * That app becomes the chain's root and stays it. Hopping onward - Instagram to
 * WhatsApp to somewhere else - extends one card's trail rather than raising a card per
 * hop, so "Jump back in" always points at the task that was actually interrupted
 * instead of the last thing touched. Only the root's screen is ever read; the apps
 * passed through on the way are named and nothing more. Returning to the root ends
 * the chain, however the user gets there.
 */
class InterruptionDetectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var nagJob: Job? = null

    @Volatile
    private var currentApp: String? = null

    /** The interrupted task. Non-null for as long as the user has not gone back to it. */
    @Volatile
    private var activeRoot: Interruption? = null

    /**
     * Apps entered since the root was left. Only ever touched from the poll loop, which
     * is a single coroutine, so it needs no synchronisation of its own.
     */
    private val chainTrail = mutableListOf<String>()

    /** Timestamp of the newest event acted on, not wall clock - see [latestForegroundApp]. */
    private var lastEventTime = System.currentTimeMillis()

     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (pollJob == null) {
            startPolling()
            watchForDismissal()
            trackObservedApps()
        }
        return START_STICKY
    }

    /**
     * The accessibility service loads this list too, but it cannot be relied on to: OEM
     * power management revokes accessibility, and the list living only there meant every
     * app read as unwatched afterwards - detection silently captured nothing while the
     * notification and the Home screen both still claimed to be watching. Detection does
     * not need accessibility to work, so it does not need it to know what to watch.
     */
    private fun trackObservedApps() {
        scope.launch {
            UserPreferences(applicationContext).observedApps.collectLatest {
                AppFilter.setObserved(it)
            }
        }
    }

    private fun startPolling() {
        pollJob = scope.launch {
            val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (true) {
                latestForegroundApp(usageStats)?.let { onForegroundChanged(it) }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * UsageStatsManager delivers events in batches, often a few seconds late. Advancing
     * the query window by wall clock therefore drops events that arrive after their
     * window has passed, so the window is anchored to the last event actually seen and
     * re-read with an overlap; events at or before [lastEventTime] are ignored.
     */
    private fun latestForegroundApp(usageStats: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(lastEventTime - QUERY_OVERLAP_MILLIS, now)

        val event = UsageEvents.Event()
        var latest: String? = null
        var latestTime = lastEventTime
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                event.timeStamp > lastEventTime
            ) {
                latest = event.packageName
                latestTime = maxOf(latestTime, event.timeStamp)
            }
        }
        lastEventTime = latestTime
        return latest?.takeIf { it != currentApp }
    }

    private fun onForegroundChanged(newApp: String) {
        // The home screen, system UI, and Revia's own card are passages rather than
        // places the user chose to be. Ignoring them outright also leaves currentApp
        // meaning "the app the user is really in", which the re-nag checks against -
        // and stops the card, being an Activity, from reading as a switch of its own.
        if (AppInfo.isTransition(applicationContext, newApp)) return

        val leftApp = currentApp
        currentApp = newApp

        val root = activeRoot
        // Called unconditionally so the flag is spent on the switch it was set for,
        // rather than surviving to be mistaken for a later one.
        val answeredTheCard = ServiceLocator.consumeIntentionalReturn(newApp)

        if (answeredTheCard || newApp == root?.packageName) {
            resolveChain()
            return
        }

        if (root != null) {
            // Still adrift from the same task. This hop is part of that detour, not a
            // new interruption - it neither reads this app nor replaces the card.
            chainTrail.add(newApp)
            showChainCard()
            return
        }

        if (leftApp != null && AppInfo.isTrackable(applicationContext, leftApp)) {
            startChain(rootApp = leftApp, firstHop = newApp)
        }
    }

    /**
     * Capture, then immediately read the same row back and show it. Both run in one
     * coroutine so the read can never race the write that produced it - a separate
     * launch per step previously left surfacing free to run before capture finished.
     */
    private fun startChain(rootApp: String, firstHop: String) {
        scope.launch {
            val repository = ServiceLocator.repository(applicationContext)
            repository.captureInterruption(
                appName = AppInfo.label(applicationContext, rootApp),
                packageName = rootApp,
                onScreenText = ContentAccessibilityService.consumeTextFor(rootApp),
                lastNotification = AppNotificationListenerService.lastNotificationText
            )
            val captured = repository.takePending(rootApp) ?: return@launch

            activeRoot = captured
            chainTrail.clear()
            chainTrail.add(firstHop)
            showChainCard()
        }
    }

    /** The task was resumed, so the detour is over and nothing more is owed for it. */
    private fun resolveChain() {
        nagJob?.cancel()
        nagJob = null
        activeRoot = null
        chainTrail.clear()
        ServiceLocator.setChainTrail(emptyList())
        ServiceLocator.clearCard()
    }

    /**
     * Puts the card over whatever app the user is in now. It also goes to
     * [ServiceLocator] so Revia's own Home screen shows it, which is the only place it
     * appears if the draw-over-apps permission was never granted. The card always
     * describes the root, so "Jump back in" needs no target of its own - the activity
     * reads it off the card.
     */
    private fun showChainCard() {
        val root = activeRoot ?: return

        ServiceLocator.setChainTrail(chainTrail.toList())
        ServiceLocator.showCard(root)
        if (!PermissionUtils.canDrawOverlays(applicationContext)) return

        scope.launch {
            if (!UserPreferences(applicationContext).cardsEnabled.first()) return@launch
            ResumptionCardActivity.show(applicationContext)
        }
    }

    /**
     * A card leaving the screen - dismissed, timed out, or jumped away from - arms the
     * re-nag. Every route clears the card through [ServiceLocator], so watching that is
     * enough to catch all of them. A resolved chain cancels the timer outright, so
     * going back to the task is never followed by a nag about it.
     */
    private fun watchForDismissal() {
        scope.launch {
            ServiceLocator.pendingCard.collect { card ->
                if (card == null) {
                    armRenag()
                    return@collect
                }
                nagJob?.cancel()
                // The summarizer replaces the card with a better-worded copy of itself.
                // The root has to follow, or the next re-nag or hop would re-show the
                // copy captured before the model ran and put the template text back.
                if (card.id == activeRoot?.id) activeRoot = card
            }
        }
    }

    private fun armRenag() {
        if (activeRoot == null) return
        val distractingApp = chainTrail.lastOrNull() ?: return

        nagJob?.cancel()
        nagJob = scope.launch {
            delay(RENAG_INTERVAL_MILLIS)
            // Still where they were when they waved the card away, so the task is still
            // abandoned. Having moved on since means a hop already refreshed the card.
            if (currentApp == distractingApp) showChainCard()
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.detection_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.detection_notification_title))
            .setSmallIcon(R.drawable.ic_stat_revia)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, InterruptionDetectionService::class.java)
            )
        }
    }
}
