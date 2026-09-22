package com.example.data.model

import android.graphics.drawable.Drawable

enum class AppCacheStatus {
    SCANNED,
    RESTRICTED,
    UNAVAILABLE
}

/**
 * Model representing an installed application with package metadata,
 * exact storage breakdown (app, data, cache), category, and usage statistics.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val sizeBytes: Long = -1L, // Total storage size (appBytes + dataBytes + cacheBytes)
    val appBytes: Long = -1L,
    val dataBytes: Long = -1L,
    val cacheBytes: Long = -1L, // REAL cacheBytes from StorageStatsManager ONLY
    val ramUsageMb: Long = 0L,
    val isSystemApp: Boolean = false,
    val categoryName: String = "App",
    val lastTimeUsedMs: Long = 0L,
    val firstInstallTimeMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val pid: Int = 0,
    val isSelectedForCacheClean: Boolean = false,
    val cacheScanStatus: AppCacheStatus = AppCacheStatus.SCANNED,
    val cacheStatusMessage: String? = null
)
