package com.revia.data.summary

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ReviaOnDevice"

/**
 * Rewrites captured screen fragments into a resumption line, on the device.
 *
 * Uses the Prompt API rather than the summarization API: our input is a handful
 * of UI labels, not the 400+ characters of prose summarization expects.
 * Every failure path returns null so the caller can fall back.
 */
class OnDeviceSummarizer {

    suspend fun status(): String = withContext(Dispatchers.IO) {
        runCatching { describe(Generation.getClient().checkStatus()) }
            .getOrElse { "error: ${it.javaClass.simpleName}: ${it.message}" }
    }

    suspend fun download(onProgress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        runCatching {
            val model = Generation.getClient()
            model.download().collect { onProgress(it.toString()) }
            describe(model.checkStatus())
        }.getOrElse { "download error: ${it.javaClass.simpleName}: ${it.message}" }
    }

    suspend fun summarize(
        appName: String,
        screenText: String?,
        lastNotification: String?
    ): String? = withContext(Dispatchers.IO) {
        if (screenText.isNullOrBlank() && lastNotification.isNullOrBlank()) return@withContext null
        runCatching {
            val model = Generation.getClient()
            if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                Log.d(TAG, "on-device model not available")
                return@runCatching null
            }
            val response = model.generateContent(
                generateContentRequest(TextPart(buildPrompt(appName, screenText, lastNotification))) {
                    temperature = 0.3f
                    candidateCount = 1
                }
            )
            response.candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
        }.getOrElse {
            Log.w(TAG, "on-device generation failed", it)
            null
        }
    }

    private fun buildPrompt(appName: String, screenText: String?, notification: String?): String =
        """
        Someone was using an app and got interrupted. From the fragments below,
        write ONE sentence starting with "You were" that would help them pick up
        where they left off.

        Rules: at most 18 words. Describe the task, not the app's buttons. Use only
        what is given; invent nothing. Output the sentence alone.

        App: $appName
        On screen: ${screenText ?: "(nothing captured)"}
        Interrupted by: ${notification ?: "(unknown)"}
        """.trimIndent()

    private fun describe(code: Int): String = when (code) {
        FeatureStatus.AVAILABLE -> "available"
        FeatureStatus.DOWNLOADABLE -> "downloadable"
        FeatureStatus.DOWNLOADING -> "downloading"
        FeatureStatus.UNAVAILABLE -> "unavailable"
        else -> "unknown($code)"
    }
}
