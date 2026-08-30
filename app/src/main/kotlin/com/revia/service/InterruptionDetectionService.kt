package com.revia.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InterruptionDetectionService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastForegroundApp: String? = null
    private var previousForegroundApp: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        job?.cancel()
        job = scope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (true) {
                pollForegroundApp(usageStatsManager)
                delay(1000L)
            }
        }
    }

    private fun pollForegroundApp(usageStatsManager: UsageStatsManager) {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000L
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latestForeground: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latestForeground = event.packageName
            }
        }

        if (latestForeground != null && latestForeground != lastForegroundApp) {
            val wasReturnToPrevious = latestForeground == previousForegroundApp
            previousForegroundApp = lastForegroundApp
            lastForegroundApp = latestForeground

            if (wasReturnToPrevious) {
                onReturnedToApp(latestForeground)
            }
        }
    }

    private fun onReturnedToApp(packageName: String) {
        // Bridged to MainViewModel via a shared repository/broadcast in a full build;
        // left as a hook here since it crosses the service/UI process boundary.
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
