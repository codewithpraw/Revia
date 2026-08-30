package com.contextswitch.data.api

import com.google.gson.annotations.SerializedName

data class SummaryResponse(
    val id: Int,
    val summary: String
)

data class HistoryItemResponse(
    val id: Int,
    @SerializedName("app_name") val appName: String,
    val summary: String,
    val timestamp: String
)

data class HealthResponse(
    val status: String
)
