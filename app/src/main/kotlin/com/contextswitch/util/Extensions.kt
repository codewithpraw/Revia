package com.contextswitch.util

import java.util.concurrent.TimeUnit

fun Long.toRelativeTimeString(nowMillis: Long = System.currentTimeMillis()): String {
    val diff = nowMillis - this
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${TimeUnit.MILLISECONDS.toHours(diff)} hr ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff)} d ago"
    }
}
