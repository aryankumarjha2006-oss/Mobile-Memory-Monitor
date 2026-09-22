package com.example.data.model

enum class ScanStatus {
    SCANNED,
    RESTRICTED,
    UNAVAILABLE
}

/**
 * Represents a cleanable storage category (e.g. Cache, Residual, Temp files, Empty Folders, Old APKs).
 */
data class CleanupItem(
    val id: String,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val isSelected: Boolean = true,
    val categoryType: CleanupCategoryType = CleanupCategoryType.CACHE,
    val scanStatus: ScanStatus = ScanStatus.SCANNED,
    val statusMessage: String? = null,
    val count: Int = 0
)

enum class CleanupCategoryType {
    CACHE,
    RESIDUAL,
    TEMP,
    EMPTY_FOLDERS,
    OLD_APK,
    DUPLICATES,
    LARGE_FILES
}
