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
import com.revia.util.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val CHANNEL_ID = "revia_detection"
private const val NOTIFICATION_ID = 1
private const val POLL_INTERVAL_MILLIS = 1000L
private const val QUERY_OVERLAP_MILLIS = 10_000L

/**
 * Watches foreground app changes. Captures context when the user leaves an app,
 * then surfaces the resulting card when they come back to it.
 */
class InterruptionDetectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    private var currentApp: String? = null
    /** Timestamp of the newest event acted on, not wall clock - see [latestForegroundApp]. */
    private var lastEventTime = System.currentTimeMillis()
    private val awaitingReturn = ConcurrentHashMap<String, Interruption>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (pollJob == null) startPolling()
        return START_STICKY
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
        val leftApp = currentApp
        currentApp = newApp

        if (leftApp != null && AppInfo.isTrackable(applicationContext, leftApp)) {
            captureContextFor(leftApp)
        }
        awaitingReturn.remove(newApp)?.let { ServiceLocator.showCard(it) }
    }

    private fun captureContextFor(packageName: String) {
        scope.launch {
            val repository = ServiceLocator.repository(applicationContext)
            val interruption = repository.captureInterruption(
                appName = AppInfo.label(applicationContext, packageName),
                packageName = packageName,
                onScreenText = ContentAccessibilityService.textFor(packageName),
                lastNotification = AppNotificationListenerService.lastNotificationText
            )
            awaitingReturn[packageName] = interruption
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
