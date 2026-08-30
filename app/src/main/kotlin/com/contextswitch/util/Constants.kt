package com.contextswitch.util

object Constants {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"
    const val AUTO_DISMISS_MILLIS = 10_000L
    const val PREFS_NAME = "context_switch_prefs"

    object Routes {
        const val ONBOARDING = "onboarding"
        const val MAIN = "main"
        const val HISTORY = "history"
        const val SETTINGS = "settings"
    }
}
