package com.example.data.model

/**
 * Model representing device internal storage details.
 * Serves as the single source of truth for storage statistics across all screens.
 */
data class StorageStats(
    val totalBytes: Long = 0,
    val freeBytes: Long = 0,
    val usedBytes: Long = 0,
    val usedPercent: Int = 0,
    val appsBytes: Long = 0,
    val photosBytes: Long = 0,
    val videosBytes: Long = 0,
    val audioBytes: Long = 0,
    val documentsBytes: Long = 0,
    val systemOtherBytes: Long = 0
) {
    val totalGb: Double
        get() = totalBytes / (1024.0 * 1024.0 * 1024.0)

    val freeGb: Double
        get() = freeBytes / (1024.0 * 1024.0 * 1024.0)

    val usedGb: Double
        get() = usedBytes / (1024.0 * 1024.0 * 1024.0)

    // Backward compatibility helper properties
    val systemEstimatedBytes: Long
        get() = systemOtherBytes

    val appsEstimatedBytes: Long
        get() = appsBytes

    val mediaEstimatedBytes: Long
        get() = photosBytes + videosBytes + audioBytes
}
