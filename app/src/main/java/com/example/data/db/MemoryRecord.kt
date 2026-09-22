package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a single memory usage sample captured over time.
 */
@Entity(tableName = "memory_history")
data class MemoryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalRamMb: Long,
    val usedRamMb: Long,
    val freeRamMb: Long,
    val usedRamPercent: Int,
    val usedStorageMb: Long,
    val totalStorageMb: Long
)
