package com.example.data.model

/**
 * High-frequency in-memory storage sample for real-time live graph monitoring.
 */
data class LiveStoragePoint(
    val timestamp: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val usedPercent: Int
)
