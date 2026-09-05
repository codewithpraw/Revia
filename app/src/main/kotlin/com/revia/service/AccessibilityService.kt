package com.revia.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap

private const val MAX_TRACKED_APPS = 32
private const val MAX_SNIPPETS = 14
private const val MAX_TEXT_LENGTH = 500
private const val MAX_DEPTH = 12
private const val MAX_NODES_VISITED = 900
private const val MIN_INTERVAL_MILLIS = 1200L
private const val MIN_SNIPPET_LENGTH = 3

/**
 * Short one- or two-word strings are almost always navigation chrome - "Search",
 * "Article", "Learn more" - while titles and prose run longer. Filtering them keeps
 * the model's input about what the user was reading rather than the app's furniture.
 */
private fun isLikelyChrome(snippet: String): Boolean =
    snippet.length < 12 && snippet.count { it == ' ' } < 2

class ContentAccessibilityService : AccessibilityService() {

    companion object {
        private val textByPackage = ConcurrentHashMap<String, String>()

        /**
         * Best text seen in [packageName], removed as it is read so a later
         * interruption can never be described with text from an earlier visit.
         */
        fun consumeTextFor(packageName: String): String? = textByPackage.remove(packageName)
    }

    private var lastHandledAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // Window changes are rare and mark a screen the user actually moved to, so
        // they always get a look. Content changes arrive in bursts and are throttled,
        // because a slow accessibility service gets disabled by the system.
        val now = System.currentTimeMillis()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (now - lastHandledAt < MIN_INTERVAL_MILLIS) return
        }
        lastHandledAt = now

        runCatching {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != packageName) return

            val text = collectText(root)
            if (text.isBlank()) return

            // Entering an app captures placeholders and loading skeletons; real content
            // arrives moments later. Keep whichever capture reads more like prose.
            val existing = textByPackage[packageName]
            if (existing == null || quality(text) > quality(existing)) {
                if (textByPackage.size > MAX_TRACKED_APPS) textByPackage.clear()
                textByPackage[packageName] = text
            }
        }
    }

    /** Letters carry meaning; underscores, dots and separators do not. */
    private fun quality(text: String): Int {
        val letters = text.count { it.isLetter() }
        return if (letters * 3 < text.length) 0 else letters
    }

    private fun collectText(root: AccessibilityNodeInfo): String {
        val snippets = LinkedHashSet<String>()
        walk(root, snippets, 0, intArrayOf(0))
        return snippets.joinToString(" · ").take(MAX_TEXT_LENGTH)
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        into: LinkedHashSet<String>,
        depth: Int,
        visited: IntArray
    ) {
        if (depth > MAX_DEPTH || into.size >= MAX_SNIPPETS) return
        if (visited[0]++ > MAX_NODES_VISITED) return

        // Never capture what the user is typing into a password field.
        if (!node.isPassword) {
            node.text?.toString()?.trim()?.let { snippet ->
                if (snippet.length >= MIN_SNIPPET_LENGTH &&
                    snippet.any { it.isLetter() } &&
                    !isLikelyChrome(snippet)
                ) {
                    into.add(snippet)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, into, depth + 1, visited)
            if (into.size >= MAX_SNIPPETS) return
        }
    }

    override fun onInterrupt() {}
}
