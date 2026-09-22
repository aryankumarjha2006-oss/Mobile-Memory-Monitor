package com.example.data.model

/**
 * Model representing live system RAM statistics.
 */
data class MemoryStats(
    val totalRamMb: Long = 0,
    val freeRamMb: Long = 0,
    val usedRamMb: Long = 0,
    val usedPercent: Int = 0,
    val thresholdMb: Long = 0,
    val isLowMemory: Boolean = false,
    val cachedRamMb: Long = 0
) {
    val freePercent: Int
        get() = (100 - usedPercent).coerceIn(0, 100)

    val isWarning: Boolean
        get() = usedPercent >= 80

    val isCritical: Boolean
        get() = usedPercent >= 90
}
