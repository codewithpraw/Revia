package com.revia.data.repository

import com.revia.data.db.Interruption
import com.revia.data.db.InterruptionDao
import com.revia.data.summary.OnDeviceSummarizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

// Inference normally takes a few seconds; this only guards against it never
// returning, which would stall the capture and lose the interruption entirely.
private const val ON_DEVICE_TIMEOUT_MILLIS = 20_000L

class InterruptionRepository(
    private val dao: InterruptionDao,
    private val onDevice: OnDeviceSummarizer = OnDeviceSummarizer()
) {

    fun observeRecent(limit: Int = 20): Flow<List<Interruption>> = dao.observeRecent(limit)

    /**
     * Replaces the placeholder summary with a real one. Call only while the app is in
     * the foreground: AICore blocks background inference, and capture runs in a service.
     *
     * Summarizing happens on the device or not at all - there is no server to fall back
     * to. When the model is unavailable or fails, the template written at capture time
     * stands, so a failure costs detail rather than the card itself.
     */
    suspend fun enrich(interruption: Interruption): Interruption {
        if (interruption.context.isBlank()) return interruption

        val better = withTimeoutOrNull(ON_DEVICE_TIMEOUT_MILLIS) {
            onDevice.summarize(
                appName = interruption.appName,
                screenText = interruption.context,
                lastNotification = null
            )
        } ?: return interruption

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
        // can be seconds, so nothing slow belongs here - and Gemini Nano refuses to run
        // from a background service anyway. The real summary is written later by
        // [enrich], once the app is in the foreground.
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

    private fun templateSummary(appName: String, onScreenText: String?): String {
        val preview = onScreenText?.take(60)
        return if (preview.isNullOrBlank()) {
            "You were in $appName"
        } else {
            "You were in $appName — $preview"
        }
    }
}
