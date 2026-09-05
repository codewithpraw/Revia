package com.revia.data.repository

import com.revia.data.api.ReviaApi
import com.revia.data.api.InterruptionEvent
import com.revia.data.db.Interruption
import com.revia.data.db.InterruptionDao
import com.revia.data.summary.OnDeviceSummarizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val SUMMARY_TIMEOUT_MILLIS = 10_000L

// Inference normally takes a few seconds; this only guards against it never
// returning, which would stall the capture and lose the interruption entirely.
private const val ON_DEVICE_TIMEOUT_MILLIS = 20_000L

class InterruptionRepository(
    private val dao: InterruptionDao,
    private val api: ReviaApi,
    private val onDevice: OnDeviceSummarizer = OnDeviceSummarizer()
) {

    fun observeRecent(limit: Int = 20): Flow<List<Interruption>> = dao.observeRecent(limit)

    /**
     * Replaces the placeholder summary with a real one. Call only while the app is in
     * the foreground: AICore blocks background inference, and capture runs in a service.
     *
     * On-device first (private, free, offline); the server is the fallback for devices
     * without Gemini Nano; the stored template stands if neither answers.
     */
    suspend fun enrich(interruption: Interruption): Interruption {
        if (interruption.context.isBlank()) return interruption

        val better = withTimeoutOrNull(ON_DEVICE_TIMEOUT_MILLIS) {
            onDevice.summarize(
                appName = interruption.appName,
                screenText = interruption.context,
                lastNotification = null
            )
        } ?: fetchSummary(
            appName = interruption.appName,
            onScreenText = interruption.context,
            lastNotification = null,
            timestamp = interruption.timestamp
        ) ?: return interruption

        val updated = interruption.copy(summary = better)
        dao.update(updated)
        return updated
    }

    suspend fun deleteById(id: Int) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    suspend fun captureInterruption(
        appName: String,
        packageName: String,
        onScreenText: String?,
        lastNotification: String?
    ): Interruption {
        val timestamp = System.currentTimeMillis()
        // Deliberately cheap. The card has to be ready before the user returns, which
        // can be seconds, so nothing slow belongs here - neither the network nor Gemini
        // Nano, which refuses to run from a background service anyway. The real summary
        // is written later by [enrich], once the app is in the foreground.
        val summary = templateSummary(appName, onScreenText)

        val interruption = Interruption(
            appName = appName,
            packageName = packageName,
            context = onScreenText ?: lastNotification.orEmpty(),
            timestamp = timestamp,
            summary = summary
        )
        val id = dao.insert(interruption)
        return interruption.copy(id = id.toInt())
    }

    private suspend fun fetchSummary(
        appName: String,
        onScreenText: String?,
        lastNotification: String?,
        timestamp: Long
    // Generous because this runs at interrupt time, not when the card is shown -
    // the user is in the other app while it completes, so latency is hidden.
    ): String? = withTimeoutOrNull(SUMMARY_TIMEOUT_MILLIS) {
        runCatching {
            api.postResume(
                InterruptionEvent(
                    appName = appName,
                    clipboardText = onScreenText,
                    lastNotification = lastNotification,
                    timestamp = isoTimestamp(timestamp)
                )
            ).summary
        }.getOrNull()
    }

    private fun templateSummary(appName: String, onScreenText: String?): String {
        val preview = onScreenText?.take(60)
        return if (preview.isNullOrBlank()) {
            "You were in $appName"
        } else {
            "You were in $appName — $preview"
        }
    }

    private fun isoTimestamp(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        format.timeZone = TimeZone.getDefault()
        return format.format(Date(millis))
    }
}
