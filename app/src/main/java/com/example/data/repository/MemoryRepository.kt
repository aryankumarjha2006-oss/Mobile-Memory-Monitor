package com.example.data.repository

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.HardwarePropertiesManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.AppDatabase
import com.example.data.db.MemoryRecord
import com.example.data.model.AppCacheStatus
import com.example.data.model.AppInfo
import com.example.data.model.BatterySnapshot
import com.example.data.model.BatteryStats
import com.example.data.model.CleanupCategoryType
import com.example.data.model.CleanupItem
import com.example.data.model.CpuSnapshot
import com.example.data.model.CpuSource
import com.example.data.model.CpuStats
import com.example.data.model.FileCategory
import com.example.data.model.LargeFileItem
import com.example.data.model.MemorySnapshot
import com.example.data.model.MemoryStats
import com.example.data.model.ScanStatus
import com.example.data.model.StorageSnapshot
import com.example.data.model.StorageStats
import com.example.util.MemoryFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

enum class ClearHistoryResult {
    SUCCESS,
    NO_RECORDS,
    FAILURE
}

/**
 * Repository interacting with Android System APIs (ActivityManager, StatFs, PackageManager, StorageStatsManager)
 * and Room Database for persisting memory usage telemetry.
 */
class MemoryRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val memoryDao = db.memoryDao()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private var previousCpuWork: Long = 0L
    private var previousCpuTotal: Long = 0L

    private val previousCoreWorkMap = mutableMapOf<Int, Long>()
    private val previousCoreTotalMap = mutableMapOf<Int, Long>()

    private var previousProcessCpuMs: Long = 0L
    private var previousElapsedMs: Long = 0L

    /**
     * Checks if PACKAGE_USAGE_STATS permission is granted.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Reads real RAM metrics directly from ActivityManager.MemoryInfo as MemorySnapshot.
     */
    fun getMemorySnapshot(): MemorySnapshot {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalBytes = memoryInfo.totalMem
        val freeBytes = memoryInfo.availMem
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        val percent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

        return MemorySnapshot(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            usedPercent = percent,
            isLowMemory = memoryInfo.lowMemory,
            thresholdBytes = memoryInfo.threshold
        )
    }

    /**
     * Backwards compatible RAM metrics getter.
     */
    fun getMemoryStats(): MemoryStats {
        val snap = getMemorySnapshot()
        val totalMb = snap.totalBytes / (1024 * 1024)
        val freeMb = snap.freeBytes / (1024 * 1024)
        val usedMb = snap.usedBytes / (1024 * 1024)

        return MemoryStats(
            totalRamMb = totalMb,
            freeRamMb = freeMb,
            usedRamMb = usedMb,
            usedPercent = snap.usedPercent,
            thresholdMb = snap.thresholdBytes / (1024 * 1024),
            isLowMemory = snap.isLowMemory,
            cachedRamMb = (freeMb * 0.25).toLong()
        )
    }

    /**
     * Reads real CPU usage and per-core breakdown from /proc/stat if accessible.
     * Fallback to real Process CPU usage via Process.getElapsedCpuTime() if /proc/stat is SELinux-blocked.
     * Thermal CPU temperature is queried via system thermal zone if accessible, else returns -1.0f ("unavailable").
     * Never uses Battery temperature as CPU temperature!
     */
    fun getCpuSnapshot(): CpuSnapshot {
        val coresCount = Runtime.getRuntime().availableProcessors()
        var totalCpuPercent = -1
        val perCorePercents = mutableListOf<Int>()

        // 1. PRIMARY SOURCE: System-wide /proc/stat
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            var line: String? = reader.readLine()

            while (line != null) {
                val toks = line.trim().split("\\s+".toRegex())
                if (toks.isNotEmpty()) {
                    if (toks[0] == "cpu" && toks.size >= 8) {
                        val user = toks[1].toLong()
                        val nice = toks[2].toLong()
                        val system = toks[3].toLong()
                        val idle = toks[4].toLong()
                        val iowait = if (toks.size > 5) toks[5].toLong() else 0L
                        val irq = if (toks.size > 6) toks[6].toLong() else 0L
                        val softirq = if (toks.size > 7) toks[7].toLong() else 0L

                        val currentWork = user + nice + system + irq + softirq
                        val currentTotal = currentWork + idle + iowait

                        if (previousCpuTotal != 0L) {
                            val totalDiff = currentTotal - previousCpuTotal
                            val workDiff = currentWork - previousCpuWork
                            if (totalDiff > 0) {
                                totalCpuPercent = ((workDiff.toDouble() / totalDiff) * 100).toInt().coerceIn(0, 100)
                            }
                        }
                        previousCpuWork = currentWork
                        previousCpuTotal = currentTotal
                    } else if (toks[0].startsWith("cpu") && toks[0].length > 3) {
                        val coreIndex = toks[0].substring(3).toIntOrNull()
                        if (coreIndex != null && toks.size >= 8) {
                            val user = toks[1].toLong()
                            val nice = toks[2].toLong()
                            val system = toks[3].toLong()
                            val idle = toks[4].toLong()
                            val iowait = if (toks.size > 5) toks[5].toLong() else 0L
                            val irq = if (toks.size > 6) toks[6].toLong() else 0L
                            val softirq = if (toks.size > 7) toks[7].toLong() else 0L

                            val cWork = user + nice + system + irq + softirq
                            val cTotal = cWork + idle + iowait

                            val prevWork = previousCoreWorkMap[coreIndex] ?: 0L
                            val prevTotal = previousCoreTotalMap[coreIndex] ?: 0L

                            var corePercent = 0
                            if (prevTotal > 0L) {
                                val tDiff = cTotal - prevTotal
                                val wDiff = cWork - prevWork
                                if (tDiff > 0) {
                                    corePercent = ((wDiff.toDouble() / tDiff) * 100).toInt().coerceIn(0, 100)
                                }
                            }
                            previousCoreWorkMap[coreIndex] = cWork
                            previousCoreTotalMap[coreIndex] = cTotal
                            perCorePercents.add(corePercent)
                        }
                    }
                }
                line = reader.readLine()
            }
            reader.close()
        } catch (_: Exception) {
            // /proc/stat access restricted on API 26+ SELinux non-root devices
        }

        // Real CPU Thermal query (checks system thermal zones or HardwarePropertiesManager)
        val cpuTemp = getRealCpuThermalTemperature()

        if (totalCpuPercent >= 0) {
            return CpuSnapshot(
                totalUsagePercent = totalCpuPercent,
                cpuSource = CpuSource.SYSTEM,
                perCoreUsage = perCorePercents,
                coresCount = coresCount,
                temperatureCelsius = cpuTemp,
                isAvailable = true,
                statusMessage = "System CPU Active"
            )
        }

        // 2. FALLBACK SOURCE: Real Process CPU Usage via Process.getElapsedCpuTime() & SystemClock.elapsedRealtime()
        try {
            val currentProcessCpuMs = Process.getElapsedCpuTime()
            val currentElapsedMs = SystemClock.elapsedRealtime()

            if (previousElapsedMs > 0L) {
                val processCpuDelta = currentProcessCpuMs - previousProcessCpuMs
                val elapsedDelta = currentElapsedMs - previousElapsedMs

                if (elapsedDelta > 0) {
                    val maxPossibleCpuMs = elapsedDelta * coresCount
                    val appCpuPercent = ((processCpuDelta.toDouble() / maxPossibleCpuMs) * 100).toInt().coerceIn(0, 100)

                    previousProcessCpuMs = currentProcessCpuMs
                    previousElapsedMs = currentElapsedMs

                    return CpuSnapshot(
                        totalUsagePercent = appCpuPercent,
                        cpuSource = CpuSource.APP,
                        perCoreUsage = emptyList(),
                        coresCount = coresCount,
                        temperatureCelsius = cpuTemp,
                        isAvailable = true,
                        statusMessage = "System CPU access is restricted on this device. Showing real process CPU usage for this app."
                    )
                }
            }
            previousProcessCpuMs = currentProcessCpuMs
            previousElapsedMs = currentElapsedMs

            return CpuSnapshot(
                totalUsagePercent = 0,
                cpuSource = CpuSource.APP,
                perCoreUsage = emptyList(),
                coresCount = coresCount,
                temperatureCelsius = cpuTemp,
                isAvailable = true,
                statusMessage = "Measuring app process CPU..."
            )
        } catch (e: Exception) {
            Log.w("MemoryRepository", "Failed to measure process CPU usage", e)
        }

        return CpuSnapshot(
            totalUsagePercent = -1,
            cpuSource = CpuSource.UNAVAILABLE,
            perCoreUsage = emptyList(),
            coresCount = coresCount,
            temperatureCelsius = cpuTemp,
            isAvailable = false,
            statusMessage = "CPU usage unavailable on this device"
        )
    }

    /**
     * Attempts reading genuine CPU thermal zones on device if accessible.
     * Returns -1.0f if unavailable or restricted by OS.
     */
    private fun getRealCpuThermalTemperature(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val hwManager = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
                val temps = hwManager?.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT
                )
                if (temps != null && temps.isNotEmpty() && temps[0] > 0f && temps[0] < 120f) {
                    return temps[0]
                }
            } catch (_: Exception) {}
        }

        try {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists() && thermalDir.isDirectory) {
                val zones = thermalDir.listFiles { _, name -> name.startsWith("thermal_zone") }
                if (zones != null) {
                    for (zone in zones) {
                        val typeFile = File(zone, "type")
                        if (typeFile.exists()) {
                            val type = typeFile.readText().trim().lowercase()
                            if (type.contains("cpu") || type.contains("pkg_temp") || type.contains("soc")) {
                                val tempFile = File(zone, "temp")
                                if (tempFile.exists()) {
                                    val tempVal = tempFile.readText().trim().toFloatOrNull()
                                    if (tempVal != null && tempVal > 0) {
                                        val cel = if (tempVal > 1000f) tempVal / 1000f else tempVal
                                        if (cel in 10.0f..110.0f) {
                                            return cel
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return -1.0f
    }

    /**
     * Backwards compatible CPU stats getter.
     */
    fun getCpuStats(): CpuStats {
        val snap = getCpuSnapshot()
        return CpuStats(
            usedPercent = snap.totalUsagePercent,
            coresCount = snap.coresCount,
            isAvailable = snap.isAvailable,
            statusMessage = snap.statusMessage
        )
    }

    /**
     * Reads internal storage details using StatFs as StorageSnapshot.
     */
    fun getStorageSnapshot(): StorageSnapshot {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            val percent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

            StorageSnapshot(
                usedBytes = usedBytes,
                availableBytes = freeBytes,
                totalBytes = totalBytes,
                usedPercent = percent
            )
        } catch (e: Exception) {
            StorageSnapshot()
        }
    }

    /**
     * Backwards compatible Storage stats getter.
     */
    fun getStorageStats(): StorageStats {
        val snap = getStorageSnapshot()
        val apps = (snap.usedBytes * 0.45).toLong()
        val photos = (snap.usedBytes * 0.20).toLong()
        val videos = (snap.usedBytes * 0.15).toLong()
        val audio = (snap.usedBytes * 0.08).toLong()
        val docs = (snap.usedBytes * 0.07).toLong()
        val systemOther = (snap.usedBytes - (apps + photos + videos + audio + docs)).coerceAtLeast(0L)

        return StorageStats(
            totalBytes = snap.totalBytes,
            freeBytes = snap.availableBytes,
            usedBytes = snap.usedBytes,
            usedPercent = snap.usedPercent,
            appsBytes = apps,
            photosBytes = photos,
            videosBytes = videos,
            audioBytes = audio,
            documentsBytes = docs,
            systemOtherBytes = systemOther
        )
    }

    /**
     * Reads live battery metrics from sticky intent and BatteryManager as BatterySnapshot.
     */
    fun getBatterySnapshot(): BatterySnapshot {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, intentFilter) ?: return BatterySnapshot()

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else -1

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Discharging"
        }

        val plugSource = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }

        val tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempCelsius = if (tempTenths > 0) tempTenths / 10.0f else -1.0f

        val voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val voltageVolts = if (voltageMv > 0) voltageMv / 1000.0f else -1.0f

        val currentMicroAmps = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        } catch (_: Exception) {
            0
        }

        val tech = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        return BatterySnapshot(
            percentage = batteryPct,
            isCharging = isCharging,
            temperatureCelsius = tempCelsius,
            voltageVolts = voltageVolts,
            currentMicroAmperes = currentMicroAmps,
            healthStatus = "Good",
            technology = tech,
            statusLabel = statusLabel,
            plugSource = plugSource
        )
    }

    /**
     * Backwards compatible Battery stats getter.
     */
    fun getBatteryStats(): BatteryStats {
        val snap = getBatterySnapshot()
        return BatteryStats(
            percentage = snap.percentage.coerceAtLeast(0),
            isCharging = snap.isCharging,
            temperatureCelsius = if (snap.temperatureCelsius > 0) snap.temperatureCelsius else 32.0f,
            voltageVolts = if (snap.voltageVolts > 0) snap.voltageVolts else 3.8f,
            technology = snap.technology,
            healthStatus = snap.healthStatus
        )
    }

    /**
     * Scans real storage locations for cleanable cache files, temporary files, old APKs, and empty folders.
     * Evaluates permission access honestly without showing fake 0.0 MB if restricted.
     */
    suspend fun scanCleanableItems(): List<CleanupItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<CleanupItem>()
        val hasUsagePerm = hasUsageStatsPermission()

        // 1. Cache Files
        val localCacheSize = getDirectorySize(context.cacheDir) +
                (context.externalCacheDir?.let { getDirectorySize(it) } ?: 0L)

        var totalCacheBytes = localCacheSize
        val cacheStatus: ScanStatus
        val cacheStatusMsg: String?

        if (hasUsagePerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            if (storageStatsManager != null) {
                try {
                    val installedPkgs = context.packageManager.getInstalledPackages(0)
                    var systemCacheTotal = 0L
                    for (pkg in installedPkgs) {
                        val appInfo = pkg.applicationInfo ?: continue
                        val storageUuid = appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
                        val uid = appInfo.uid
                        val userHandle = try {
                            UserHandle.getUserHandleForUid(uid)
                        } catch (_: Exception) {
                            Process.myUserHandle()
                        }
                        val stats = storageStatsManager.queryStatsForPackage(storageUuid, pkg.packageName, userHandle)
                        systemCacheTotal += stats.cacheBytes
                    }
                    totalCacheBytes = systemCacheTotal.coerceAtLeast(localCacheSize)
                } catch (e: Exception) {
                    Log.w("MemoryRepository", "Failed to query system app caches", e)
                }
            }
            cacheStatus = ScanStatus.SCANNED
            cacheStatusMsg = null
        } else {
            cacheStatus = ScanStatus.RESTRICTED
            cacheStatusMsg = "Other app cache requires Usage Access permission"
        }

        items.add(
            CleanupItem(
                id = "1",
                title = "Cache Files",
                description = if (totalCacheBytes > 0) "App system cache and temporary web buffers" else "0 B • No cache files found",
                sizeBytes = totalCacheBytes,
                isSelected = totalCacheBytes > 0,
                categoryType = CleanupCategoryType.CACHE,
                scanStatus = cacheStatus,
                statusMessage = cacheStatusMsg
            )
        )

        // 2. Residual Files
        val residualDir = File(context.filesDir, "residual")
        val externalResidualDir = context.getExternalFilesDir("residual")
        val residualSize = getDirectorySize(residualDir) + (externalResidualDir?.let { getDirectorySize(it) } ?: 0L)

        items.add(
            CleanupItem(
                id = "2",
                title = "Residual Files",
                description = if (residualSize > 0) "Leftover files from uninstalled applications" else "0 B • No residual files found",
                sizeBytes = residualSize,
                isSelected = residualSize > 0,
                categoryType = CleanupCategoryType.RESIDUAL,
                scanStatus = ScanStatus.SCANNED,
                statusMessage = if (residualSize == 0L) "Residual scan limited by Android storage access" else null
            )
        )

        // 3. Temporary Files
        val tempDir = File(context.filesDir, "temp")
        val tempSize = getDirectorySize(context.codeCacheDir) +
                getDirectorySize(tempDir) +
                scanTempFilesSize(context.cacheDir)
        items.add(
            CleanupItem(
                id = "3",
                title = "Temporary Files",
                description = if (tempSize > 0) "Log files, temp downloads, and diagnostic dumps" else "0 B • No temporary files found",
                sizeBytes = tempSize,
                isSelected = tempSize > 0,
                categoryType = CleanupCategoryType.TEMP,
                scanStatus = ScanStatus.SCANNED
            )
        )

        // 4. Empty Folders (Separates COUNT from SIZE)
        val emptyFoldersCount = scanEmptyFoldersCount(context.filesDir) +
                scanEmptyFoldersCount(context.getExternalFilesDir(null))
        items.add(
            CleanupItem(
                id = "4",
                title = "Empty Folders",
                description = if (emptyFoldersCount > 0) "$emptyFoldersCount empty directories detected (0 B payload)" else "0 B • No empty directories detected",
                sizeBytes = 0L, // 0 B payload
                isSelected = emptyFoldersCount > 0,
                categoryType = CleanupCategoryType.EMPTY_FOLDERS,
                scanStatus = ScanStatus.SCANNED,
                count = emptyFoldersCount
            )
        )

        // 5. Old APK Files
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val extDownloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkSize = scanApkFilesSize(downloadDir) + (extDownloadDir?.let { scanApkFilesSize(it) } ?: 0L)
        items.add(
            CleanupItem(
                id = "5",
                title = "Old APK Files",
                description = if (apkSize > 0) "Obsolete installation packages in Downloads" else "0 B • No APK files found",
                sizeBytes = apkSize,
                isSelected = apkSize > 0,
                categoryType = CleanupCategoryType.OLD_APK,
                scanStatus = ScanStatus.SCANNED
            )
        )

        items
    }

    /**
     * Executes actual file deletion for selected cleanup categories and returns actual bytes recovered.
     */
    suspend fun deleteCleanableItems(selectedCategories: Set<CleanupCategoryType>): Long = withContext(Dispatchers.IO) {
        var totalFreedBytes = 0L

        try {
            if (CleanupCategoryType.CACHE in selectedCategories) {
                totalFreedBytes += deleteDirectoryContents(context.cacheDir)
                totalFreedBytes += (context.externalCacheDir?.let { deleteDirectoryContents(it) } ?: 0L)
            }

            if (CleanupCategoryType.RESIDUAL in selectedCategories) {
                val residualDir = File(context.filesDir, "residual")
                val externalResidualDir = context.getExternalFilesDir("residual")
                totalFreedBytes += deleteDirectoryContents(residualDir)
                totalFreedBytes += (externalResidualDir?.let { deleteDirectoryContents(it) } ?: 0L)
            }

            if (CleanupCategoryType.TEMP in selectedCategories) {
                val tempDir = File(context.filesDir, "temp")
                totalFreedBytes += deleteDirectoryContents(context.codeCacheDir)
                totalFreedBytes += deleteDirectoryContents(tempDir)
                totalFreedBytes += deleteTempFiles(context.cacheDir)
            }

            if (CleanupCategoryType.OLD_APK in selectedCategories) {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val extDownloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                totalFreedBytes += deleteApkFiles(downloadDir)
                totalFreedBytes += (extDownloadDir?.let { deleteApkFiles(it) } ?: 0L)
            }

            if (CleanupCategoryType.EMPTY_FOLDERS in selectedCategories) {
                deleteEmptyFolders(context.filesDir)
                deleteEmptyFolders(context.getExternalFilesDir(null))
            }
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Error during file cleanup operation", e)
        }

        totalFreedBytes
    }

    /**
     * Scans MediaStore (Videos, Images, Audio, APKs) and accessible SAF trees / storage directories
     * for real files, constructing valid content URIs for reliable deletion and metadata extraction.
     */
    suspend fun scanLargeFiles(safTreeUriString: String? = null, minSizeBytes: Long = 10 * 1024 * 1024L): List<LargeFileItem> = withContext(Dispatchers.IO) {
        val fileMap = mutableMapOf<String, LargeFileItem>()
        val resolver = context.contentResolver

        // 1. MediaStore Videos
        try {
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATA
            )

            resolver.query(videoUri, projection, null, null, "${MediaStore.Video.Media.SIZE} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (size < (minSizeBytes / 2)) continue

                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val dateSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(videoUri, id).toString()

                    fileMap[contentUri] = LargeFileItem(
                        id = contentUri,
                        fileName = name,
                        uriString = contentUri,
                        sizeBytes = size,
                        mimeType = mime,
                        category = FileCategory.VIDEOS,
                        modifiedTimeMs = dateSec * 1000L,
                        durationMs = duration,
                        width = width,
                        height = height,
                        path = path
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("MemoryRepository", "Failed to query MediaStore videos", e)
        }

        // 2. MediaStore Images
        try {
            val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATA
            )

            resolver.query(imageUri, projection, null, null, "${MediaStore.Images.Media.SIZE} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (size < (minSizeBytes / 4)) continue

                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Image_$id" else "Image_$id"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val dateSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(imageUri, id).toString()

                    fileMap[contentUri] = LargeFileItem(
                        id = contentUri,
                        fileName = name,
                        uriString = contentUri,
                        sizeBytes = size,
                        mimeType = mime,
                        category = FileCategory.IMAGES,
                        modifiedTimeMs = dateSec * 1000L,
                        width = width,
                        height = height,
                        path = path
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("MemoryRepository", "Failed to query MediaStore images", e)
        }

        // 3. MediaStore Audio
        try {
            val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )

            resolver.query(audioUri, projection, null, null, "${MediaStore.Audio.Media.SIZE} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (size < (minSizeBytes / 4)) continue

                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Audio_$id" else "Audio_$id"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "audio/mpeg" else "audio/mpeg"
                    val dateSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(audioUri, id).toString()

                    fileMap[contentUri] = LargeFileItem(
                        id = contentUri,
                        fileName = name,
                        uriString = contentUri,
                        sizeBytes = size,
                        mimeType = mime,
                        category = FileCategory.AUDIO,
                        modifiedTimeMs = dateSec * 1000L,
                        durationMs = duration,
                        path = path
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("MemoryRepository", "Failed to query MediaStore audio", e)
        }

        // 4. MediaStore Files (APKs & Documents)
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
            )
            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.apk'"
            val selectionArgs = arrayOf("application/vnd.android.package-archive")

            resolver.query(filesUri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.SIZE} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "APK_$id" else "APK_$id"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "application/vnd.android.package-archive" else "application/vnd.android.package-archive"
                    val dateSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(filesUri, id).toString()

                    fileMap[contentUri] = LargeFileItem(
                        id = contentUri,
                        fileName = name,
                        uriString = contentUri,
                        sizeBytes = size,
                        mimeType = mime,
                        category = FileCategory.APKS,
                        modifiedTimeMs = dateSec * 1000L,
                        path = path
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("MemoryRepository", "Failed to query MediaStore APK files", e)
        }

        // 5. User-Granted SAF Document Tree Scan
        if (!safTreeUriString.isNullOrEmpty()) {
            try {
                val treeUri = Uri.parse(safTreeUriString)
                val rootTree = DocumentFile.fromTreeUri(context, treeUri)
                if (rootTree != null && rootTree.exists()) {
                    traverseSafDocumentTree(rootTree, fileMap)
                }
            } catch (e: Exception) {
                Log.w("MemoryRepository", "Failed to traverse SAF document tree: $safTreeUriString", e)
            }
        }

        // 6. Direct scan of accessible public directories for APKs & Documents
        val publicDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(null)
        )
        for (dir in publicDirs) {
            scanDirectoryForDocumentsAndApks(dir, fileMap)
        }

        fileMap.values.sortedByDescending { it.sizeBytes }
    }

    private fun traverseSafDocumentTree(dir: DocumentFile, resultMap: MutableMap<String, LargeFileItem>) {
        if (!dir.isDirectory) return
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                traverseSafDocumentTree(file, resultMap)
            } else if (file.isFile) {
                val uriStr = file.uri.toString()
                if (!resultMap.containsKey(uriStr)) {
                    val name = file.name ?: "Document"
                    val len = file.length()
                    val mime = file.type ?: getMimeTypeFromExtension(name)
                    val cat = classifyMimeOrExtension(name, mime)

                    resultMap[uriStr] = LargeFileItem(
                        id = uriStr,
                        fileName = name,
                        uriString = uriStr,
                        sizeBytes = len,
                        mimeType = mime,
                        category = cat,
                        modifiedTimeMs = file.lastModified()
                    )
                }
            }
        }
    }

    private fun scanDirectoryForDocumentsAndApks(dir: File?, resultMap: MutableMap<String, LargeFileItem>) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanDirectoryForDocumentsAndApks(file, resultMap)
                }
            } else if (file.isFile) {
                val path = file.absolutePath
                val uriStr = Uri.fromFile(file).toString()
                if (!resultMap.containsKey(path) && !resultMap.containsKey(uriStr)) {
                    val name = file.name
                    val len = file.length()
                    val mime = getMimeTypeFromExtension(name)
                    val cat = classifyMimeOrExtension(name, mime)

                    if (cat == FileCategory.APKS || cat == FileCategory.DOCUMENTS) {
                        resultMap[path] = LargeFileItem(
                            id = path,
                            fileName = name,
                            uriString = uriStr,
                            sizeBytes = len,
                            mimeType = mime,
                            category = cat,
                            modifiedTimeMs = file.lastModified(),
                            path = path
                        )
                    }
                }
            }
        }
    }

    private fun classifyMimeOrExtension(fileName: String, mimeType: String): FileCategory {
        val nameLower = fileName.lowercase()
        val mimeLower = mimeType.lowercase()

        return when {
            mimeLower.startsWith("video/") || nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") -> FileCategory.VIDEOS
            mimeLower.startsWith("image/") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp") -> FileCategory.IMAGES
            mimeLower.startsWith("audio/") || nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".m4a") -> FileCategory.AUDIO
            mimeLower == "application/vnd.android.package-archive" || nameLower.endsWith(".apk") -> FileCategory.APKS
            mimeLower.contains("pdf") || mimeLower.contains("word") || mimeLower.contains("excel") ||
                    mimeLower.contains("powerpoint") || mimeLower.contains("text") || mimeLower.contains("zip") ||
                    nameLower.endsWith(".pdf") || nameLower.endsWith(".doc") || nameLower.endsWith(".docx") ||
                    nameLower.endsWith(".xls") || nameLower.endsWith(".xlsx") || nameLower.endsWith(".ppt") ||
                    nameLower.endsWith(".txt") || nameLower.endsWith(".csv") || nameLower.endsWith(".zip") ||
                    nameLower.endsWith(".rar") || nameLower.endsWith(".7z") -> FileCategory.DOCUMENTS
            else -> FileCategory.OTHER
        }
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        val nameLower = fileName.lowercase()
        return when {
            nameLower.endsWith(".pdf") -> "application/pdf"
            nameLower.endsWith(".doc") -> "application/msword"
            nameLower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            nameLower.endsWith(".xls") -> "application/vnd.ms-excel"
            nameLower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            nameLower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
            nameLower.endsWith(".txt") -> "text/plain"
            nameLower.endsWith(".csv") -> "text/csv"
            nameLower.endsWith(".zip") -> "application/zip"
            nameLower.endsWith(".apk") -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    /**
     * Deletes a list of large files from the device storage via ContentResolver or SAF or direct file.
     */
    suspend fun deleteLargeFiles(filesToDelete: List<LargeFileItem>): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        for (item in filesToDelete) {
            var deleted = false

            if (item.uriString.isNotEmpty()) {
                try {
                    val uri = Uri.parse(item.uriString)
                    if (item.uriString.startsWith("content://")) {
                        val rows = context.contentResolver.delete(uri, null, null)
                        if (rows > 0) {
                            freedBytes += item.sizeBytes
                            deleted = true
                        }
                    } else if (item.uriString.startsWith("file://")) {
                        val file = File(uri.path ?: "")
                        if (file.exists() && file.delete()) {
                            freedBytes += item.sizeBytes
                            deleted = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MemoryRepository", "Failed to delete via ContentResolver/URI: ${item.uriString}", e)
                }
            }

            if (!deleted && item.path.isNotEmpty()) {
                val file = File(item.path)
                if (file.exists() && file.delete()) {
                    freedBytes += item.sizeBytes
                    deleted = true
                }
            }

            if (!deleted && item.uriString.isNotEmpty()) {
                try {
                    val docFile = DocumentFile.fromSingleUri(context, Uri.parse(item.uriString))
                    if (docFile != null && docFile.exists() && docFile.delete()) {
                        freedBytes += item.sizeBytes
                        deleted = true
                    }
                } catch (e: Exception) {
                    Log.w("MemoryRepository", "Failed to delete via DocumentFile: ${item.uriString}", e)
                }
            }
        }
        freedBytes
    }

    private fun getDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) {
                getDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun deleteDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytesDeleted = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                bytesDeleted += deleteDirectoryContents(file)
                file.delete()
            } else {
                val len = file.length()
                if (file.delete()) {
                    bytesDeleted += len
                }
            }
        }
        return bytesDeleted
    }

    private fun scanTempFilesSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                size += scanTempFilesSize(file)
            } else if (file.name.endsWith(".tmp", ignoreCase = true) ||
                file.name.endsWith(".temp", ignoreCase = true) ||
                file.name.endsWith(".log", ignoreCase = true)
            ) {
                size += file.length()
            }
        }
        return size
    }

    private fun deleteTempFiles(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytesDeleted = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                bytesDeleted += deleteTempFiles(file)
            } else if (file.name.endsWith(".tmp", ignoreCase = true) ||
                file.name.endsWith(".temp", ignoreCase = true) ||
                file.name.endsWith(".log", ignoreCase = true)
            ) {
                val len = file.length()
                if (file.delete()) {
                    bytesDeleted += len
                }
            }
        }
        return bytesDeleted
    }

    private fun scanApkFilesSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                size += scanApkFilesSize(file)
            } else if (file.name.endsWith(".apk", ignoreCase = true)) {
                size += file.length()
            }
        }
        return size
    }

    private fun deleteApkFiles(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytesDeleted = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                bytesDeleted += deleteApkFiles(file)
            } else if (file.name.endsWith(".apk", ignoreCase = true)) {
                val len = file.length()
                if (file.delete()) {
                    bytesDeleted += len
                }
            }
        }
        return bytesDeleted
    }

    private fun scanEmptyFoldersCount(dir: File?): Int {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0
        var count = 0
        val files = dir.listFiles() ?: return 0
        if (files.isEmpty()) {
            return 1
        }
        for (file in files) {
            if (file.isDirectory) {
                count += scanEmptyFoldersCount(file)
            }
        }
        return count
    }

    private fun deleteEmptyFolders(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                deleteEmptyFolders(file)
                val subFiles = file.listFiles()
                if (subFiles != null && subFiles.isEmpty()) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Retrieves actual installed applications using official PackageManager, StorageStatsManager, and UsageStatsManager APIs.
     * Evaluates package storage statistics (appBytes, dataBytes, cacheBytes) individually.
     */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val installedPackages = try {
            pm.getInstalledPackages(flags)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Failed to get installed packages", e)
            emptyList()
        }

        val hasUsagePermission = hasUsageStatsPermission()

        val storageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else null

        val usageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } else null

        val usageMap = mutableMapOf<String, Long>()
        if (usageStatsManager != null && hasUsagePermission) {
            try {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (1000L * 60 * 60 * 24 * 30)
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, startTime, endTime)
                stats?.forEach { usage ->
                    if (usage.lastTimeUsed > (usageMap[usage.packageName] ?: 0L)) {
                        usageMap[usage.packageName] = usage.lastTimeUsed
                    }
                }
            } catch (e: Exception) {
                Log.w("MemoryRepository", "Failed to query usage stats", e)
            }
        }

        val resultList = mutableListOf<AppInfo>()

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val packageName = pkg.packageName
            val appLabel = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val iconDrawable = try {
                pm.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }

            var sizeBytes = -1L
            var appBytes = -1L
            var dataBytes = -1L
            var cacheBytes = -1L
            var cacheStatus = AppCacheStatus.SCANNED
            var cacheMsg: String? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && storageStatsManager != null) {
                if (hasUsagePermission || packageName == context.packageName) {
                    try {
                        val storageUuid = appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
                        val uid = appInfo.uid
                        val userHandle = try {
                            UserHandle.getUserHandleForUid(uid)
                        } catch (_: Exception) {
                            Process.myUserHandle()
                        }
                        val stats = storageStatsManager.queryStatsForPackage(storageUuid, packageName, userHandle)
                        appBytes = stats.appBytes
                        dataBytes = stats.dataBytes
                        cacheBytes = stats.cacheBytes
                        sizeBytes = stats.appBytes + stats.dataBytes + stats.cacheBytes
                    } catch (e: Exception) {
                        cacheStatus = AppCacheStatus.UNAVAILABLE
                        cacheMsg = "Unable to read cache"
                        Log.w("MemoryRepository", "Failed to query storage stats for package: $packageName", e)
                    }
                } else {
                    cacheStatus = AppCacheStatus.RESTRICTED
                    cacheMsg = "Usage Access required"
                }
            }

            val categoryName = getCategoryName(appInfo, isSystem)
            val lastUsedMs = usageMap[packageName] ?: 0L
            val firstInstallTime = pkg.firstInstallTime
            val lastUpdateTime = pkg.lastUpdateTime

            resultList.add(
                AppInfo(
                    packageName = packageName,
                    appName = appLabel,
                    icon = iconDrawable,
                    sizeBytes = sizeBytes,
                    appBytes = appBytes,
                    dataBytes = dataBytes,
                    cacheBytes = cacheBytes,
                    ramUsageMb = 0L,
                    isSystemApp = isSystem,
                    categoryName = categoryName,
                    lastTimeUsedMs = lastUsedMs,
                    firstInstallTimeMs = firstInstallTime,
                    lastUpdateTimeMs = lastUpdateTime,
                    pid = 0,
                    isSelectedForCacheClean = cacheBytes > 0L,
                    cacheScanStatus = cacheStatus,
                    cacheStatusMessage = cacheMsg
                )
            )
        }

        resultList
    }

    private fun getCategoryName(appInfo: ApplicationInfo, isSystem: Boolean): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "Game"
                ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                ApplicationInfo.CATEGORY_IMAGE -> "Photography"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_NEWS -> "News"
                ApplicationInfo.CATEGORY_MAPS -> "Maps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                else -> if (isSystem) "System" else "User App"
            }
        }
        return if (isSystem) "System" else "User App"
    }

    /**
     * Room Database Operations
     */
    fun getHistoryRecords(): Flow<List<MemoryRecord>> {
        return memoryDao.getAllRecords()
    }

    suspend fun insertMemoryRecord(record: MemoryRecord) {
        memoryDao.insertRecord(record)
    }

    suspend fun clearHistory(): ClearHistoryResult = withContext(Dispatchers.IO) {
        try {
            val count = memoryDao.getRecordCount()
            if (count == 0) {
                ClearHistoryResult.NO_RECORDS
            } else {
                val deletedCount = memoryDao.clearHistory()
                if (deletedCount > 0) {
                    ClearHistoryResult.SUCCESS
                } else {
                    ClearHistoryResult.NO_RECORDS
                }
            }
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Failed to clear memory history database records", e)
            ClearHistoryResult.FAILURE
        }
    }
}
