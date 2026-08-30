package com.contextswitch.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interruptions")
data class Interruption(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val appName: String,
    val context: String,
    val timestamp: Long,
    val summary: String,
    val isDismissed: Boolean = false
)
