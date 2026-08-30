package com.revia.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ContentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var lastScreenText: String? = null
            private set
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        lastScreenText = extractText(root)
    }

    private fun extractText(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 5) return null
        node.text?.let { if (it.isNotBlank()) return it.toString() }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractText(child, depth + 1)?.let { return it }
        }
        return null
    }

    override fun onInterrupt() {}
}
