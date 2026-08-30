package com.revia.data.api

import com.google.gson.annotations.SerializedName

data class InterruptionEvent(
    @SerializedName("app_name") val appName: String,
    @SerializedName("clipboard_text") val clipboardText: String?,
    @SerializedName("last_notification") val lastNotification: String?,
    @SerializedName("timestamp") val timestamp: String
)
