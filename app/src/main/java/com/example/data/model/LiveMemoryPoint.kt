package com.example.data.model

/**
 * High-frequency in-memory RAM usage sample for real-time live memory graph.
 */
data class LiveMemoryPoint(
    val timestamp: Long,
    val usedBytes: Long,
    val availBytes: Long,
    val totalBytes: Long,
    val usedPercent: Int
)
