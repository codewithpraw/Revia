package com.revia.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interruptions")
data class Interruption(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val appName: String,
    /** Kept so the history list can load the app's own icon. Blank for pre-v2 rows. */
    val packageName: String = "",
    val context: String,
    val timestamp: Long,
    val summary: String,
    val isDismissed: Boolean = false
)
