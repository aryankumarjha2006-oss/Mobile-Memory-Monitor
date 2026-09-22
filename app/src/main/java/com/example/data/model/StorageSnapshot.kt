package com.example.data.model

/**
 * Snapshot model representing real StatFs storage metrics.
 */
data class StorageSnapshot(
    val usedBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val usedPercent: Int = 0
)
