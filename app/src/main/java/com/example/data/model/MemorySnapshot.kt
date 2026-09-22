package com.example.data.model

/**
 * Snapshot model representing real ActivityManager.MemoryInfo metrics.
 */
data class MemorySnapshot(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val usedPercent: Int = 0,
    val isLowMemory: Boolean = false,
    val thresholdBytes: Long = 0L
)

data class MemorySample(
    val timestamp: Long,
    val usedBytes: Long,
    val availBytes: Long,
    val totalBytes: Long,
    val usedPercent: Int
)
