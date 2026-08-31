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

    companion object {
        private const val HISTORY = 5
        private val outcomes = ArrayDeque<String>()

        /** Recent on-device attempts, newest first. Surfaced in Settings while testing. */
        val lastOutcome: String
            get() = synchronized(outcomes) {
                if (outcomes.isEmpty()) "not attempted yet" else outcomes.joinToString(" | ")
            }

        internal fun record(app: String, outcome: String) = synchronized(outcomes) {
            outcomes.addFirst("$app=$outcome")
            while (outcomes.size > HISTORY) outcomes.removeLast()
        }
    }

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
        if (screenText.isNullOrBlank() && lastNotification.isNullOrBlank()) {
            record(appName, "no-text")
            return@withContext null
        }
        runCatching {
            val model = Generation.getClient()
            val st = model.checkStatus()
            if (st != FeatureStatus.AVAILABLE) {
                record(appName, describe(st))
                return@runCatching null
            }
            val response = model.generateContent(
                generateContentRequest(TextPart(buildPrompt(appName, screenText, lastNotification))) {
                    temperature = 0.3f
                    candidateCount = 1
                }
            )
            clean(response.candidates.firstOrNull()?.text)
                .also { record(appName, if (it == null) "empty" else "OK") }
        }.getOrElse {
            record(appName, "${it.javaClass.simpleName}:${it.message?.take(60)}")
            Log.w(TAG, "on-device generation failed", it)
            null
        }
    }

    // Nano follows an example far more reliably than it follows instructions,
    // hence the worked example rather than a longer list of rules.
    private fun buildPrompt(appName: String, screenText: String?, notification: String?): String =
        """
        Rewrite what someone was doing before an interruption, as one short sentence.

        Example
        App: Gmail
        On screen: Re: Q3 budget · Draft saved · To: priya@
        Answer: You were drafting a reply to Priya about the Q3 budget.

        Now do the same. Start with "You were". No bullet points. Under 18 words.
        Use only the given text.

        App: $appName
        On screen: ${screenText ?: "(nothing captured)"}
        Interrupted by: ${notification ?: "(unknown)"}
        Answer:
        """.trimIndent()

    /** Nano tends to prefix a bullet and sometimes echoes the "Answer:" label. */
    private fun clean(raw: String?): String? {
        var text = raw?.trim().orEmpty()
        text = text.removePrefix("Answer:").trim()
        text = text.trimStart('*', '-', '\u2022', ' ')
        text = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() }
    }

    private fun describe(code: Int): String = when (code) {
        FeatureStatus.AVAILABLE -> "available"
        FeatureStatus.DOWNLOADABLE -> "downloadable"
        FeatureStatus.DOWNLOADING -> "downloading"
        FeatureStatus.UNAVAILABLE -> "unavailable"
        else -> "unknown($code)"
    }
}
