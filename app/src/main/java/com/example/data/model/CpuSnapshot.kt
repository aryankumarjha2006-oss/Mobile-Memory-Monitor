package com.example.data.model

enum class CpuSource(val label: String, val badgeLabel: String) {
    SYSTEM("System CPU Usage", "System"),
    APP("App CPU Usage", "App"),
    UNAVAILABLE("CPU Usage", "Unavailable")
}

/**
 * Snapshot model representing real CPU utilization (System-wide or App-process fallback),
 * per-core metrics, and thermal readings.
 */
data class CpuSnapshot(
    val totalUsagePercent: Int = -1, // -1 means unavailable
    val cpuSource: CpuSource = CpuSource.UNAVAILABLE,
    val perCoreUsage: List<Int> = emptyList(), // per-core usage % if SYSTEM
    val coresCount: Int = Runtime.getRuntime().availableProcessors(),
    val temperatureCelsius: Float = -1.0f, // -1.0f means unavailable
    val isAvailable: Boolean = false,
    val statusMessage: String = "Measuring..."
)

data class CpuSample(
    val timestamp: Long,
    val usagePercent: Int
)
