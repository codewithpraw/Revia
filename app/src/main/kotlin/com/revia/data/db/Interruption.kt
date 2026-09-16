package com.revia.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Looking up a pending interruption by package happens on every app switch, so the
// column it filters on is indexed.
@Entity(tableName = "interruptions", indices = [Index("packageName")])
data class Interruption(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val appName: String,
    /** Kept so the history list can load the app's own icon. Blank for pre-v2 rows. */
    val packageName: String = "",
    val context: String,
    val timestamp: Long,
    val summary: String,
    val isDismissed: Boolean = false,
    /**
     * Whether this interruption has already been shown as a card. Stored rather than
     * held in memory so a pending card survives the detection service being killed,
     * which OEM power management does routinely.
     */
    val surfaced: Boolean = false
)
