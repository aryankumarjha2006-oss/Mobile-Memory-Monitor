package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a persistent 60% RAM threshold crossing event,
 * including candidate app details and force-stop action outcomes.
 */
@Entity(tableName = "ram_trigger_events")
data class RamTriggerEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val usedPercent: Int,
    val thresholdPercent: Int = 60,
    val appName: String,
    val packageName: String,
    val actionAttempted: String,
    val outcomeName: String, // "FORCE_STOP_CONFIRMED", "MANUAL_FORCE_STOP_REQUIRED", "UNABLE_TO_STOP_APP"
    val message: String
)
