package com.revia.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap

private const val MAX_TRACKED_APPS = 32
private const val MAX_SNIPPETS = 6
private const val MAX_TEXT_LENGTH = 300

class ContentAccessibilityService : AccessibilityService() {

    companion object {
        private val textByPackage = ConcurrentHashMap<String, String>()

        /** Text last seen on screen in [packageName], captured before the user left it. */
        fun textFor(packageName: String): String? = textByPackage[packageName]
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != packageName) return

        val text = collectText(root)
        if (text.isBlank()) return

        if (textByPackage.size > MAX_TRACKED_APPS) textByPackage.clear()
        textByPackage[packageName] = text
    }

    private fun collectText(root: AccessibilityNodeInfo): String {
        val snippets = LinkedHashSet<String>()
        walk(root, snippets, 0)
        return snippets.joinToString(" · ").take(MAX_TEXT_LENGTH)
    }

    private fun walk(node: AccessibilityNodeInfo, into: LinkedHashSet<String>, depth: Int) {
        if (depth > 12 || into.size >= MAX_SNIPPETS) return
        node.text?.toString()?.trim()?.let { if (it.length > 1) into.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, into, depth + 1)
            if (into.size >= MAX_SNIPPETS) return
        }
    }

    override fun onInterrupt() {}
}
