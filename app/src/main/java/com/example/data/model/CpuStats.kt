package com.example.data.model

/**
 * Model representing CPU utilization statistics and hardware metrics.
 */
data class CpuStats(
    val usedPercent: Int = -1, // -1 means unavailable on this device/API
    val coresCount: Int = Runtime.getRuntime().availableProcessors(),
    val appCpuPercent: Double = 0.0,
    val isAvailable: Boolean = false,
    val statusMessage: String = "CPU usage unavailable on this device/API"
)
