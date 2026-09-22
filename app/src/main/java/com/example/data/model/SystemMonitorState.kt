package com.example.data.model

/**
 * Consolidated real-time state for CPU, Memory, Battery, Storage, and FPS.
 */
data class SystemMonitorState(
    val memory: MemorySnapshot = MemorySnapshot(),
    val cpu: CpuSnapshot = CpuSnapshot(),
    val battery: BatterySnapshot = BatterySnapshot(),
    val storage: StorageSnapshot = StorageSnapshot(),
    val cpuHistory: List<CpuSample> = emptyList(),
    val memoryHistory: List<MemorySample> = emptyList(),
    val fps: Int = -1, // -1 means unavailable or unsupported
    val isLoading: Boolean = false,
    val error: String? = null
)
