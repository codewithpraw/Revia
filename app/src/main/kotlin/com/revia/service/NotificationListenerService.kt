package com.revia.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AppNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        var lastNotificationText: String? = null
            private set
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        lastNotificationText = listOfNotNull(title, text).joinToString(": ")
    }
}
