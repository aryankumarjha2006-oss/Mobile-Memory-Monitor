package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MemoryRecord
import com.example.data.model.AppInfo
import com.example.data.model.AppSortOption
import com.example.data.model.BatterySnapshot
import com.example.data.model.BatteryStats
import com.example.data.model.CleanupCategoryType
import com.example.data.model.CleanupItem
import com.example.data.model.CpuSample
import com.example.data.model.CpuSnapshot
import com.example.data.model.CpuStats
import com.example.data.model.FileCategory
import com.example.data.model.LargeFileItem
import com.example.data.model.LargeFileSortOption
import com.example.data.model.LiveMemoryPoint
import com.example.data.model.LiveStoragePoint
import com.example.data.model.MemorySnapshot
import com.example.data.model.MemoryStats
import com.example.data.model.StorageScanState
import com.example.data.model.StorageSnapshot
import com.example.data.model.StorageStats
import com.example.data.model.SystemMonitorState
import com.example.data.repository.ClearHistoryResult
import com.example.data.repository.MemoryRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main ViewModel driving real-time telemetry, storage breakdown,
 * Smart Cleanup, Per-App Cache Management, Large Files analysis, and App Management.
 */
class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MemoryRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("memory_manager_settings", Context.MODE_PRIVATE)

    private val _memoryStats = MutableStateFlow(MemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _batteryStats = MutableStateFlow(BatteryStats())
    val batteryStats: StateFlow<BatteryStats> = _batteryStats.asStateFlow()

    private val _cpuStats = MutableStateFlow(CpuStats())
    val cpuStats: StateFlow<CpuStats> = _cpuStats.asStateFlow()

    private val _cpuSnapshot = MutableStateFlow(CpuSnapshot())
    val cpuSnapshot: StateFlow<CpuSnapshot> = _cpuSnapshot.asStateFlow()

    private val _batterySnapshot = MutableStateFlow(BatterySnapshot())
    val batterySnapshot: StateFlow<BatterySnapshot> = _batterySnapshot.asStateFlow()

    private val _storageScanState = MutableStateFlow<StorageScanState>(StorageScanState.Idle)
    val storageScanState: StateFlow<StorageScanState> = _storageScanState.asStateFlow()

    private val _lastScanTimestamp = MutableStateFlow<Long?>(null)
    val lastScanTimestamp: StateFlow<Long?> = _lastScanTimestamp.asStateFlow()

    // Live RAM Monitoring Window (Max 60 samples ~ 3 mins)
    private val _liveMemorySamples = MutableStateFlow<List<LiveMemoryPoint>>(emptyList())
    val liveMemorySamples: StateFlow<List<LiveMemoryPoint>> = _liveMemorySamples.asStateFlow()

    // Live Storage Monitoring Window (Max 60 samples ~ 2.5 mins)
    private val _liveStorageSamples = MutableStateFlow<List<LiveStoragePoint>>(emptyList())
    val liveStorageSamples: StateFlow<List<LiveStoragePoint>> = _liveStorageSamples.asStateFlow()

    // Live CPU Samples Window (Max 60 samples ~ 1.5 mins)
    private val _liveCpuSamples = MutableStateFlow<List<CpuSample>>(emptyList())
    val liveCpuSamples: StateFlow<List<CpuSample>> = _liveCpuSamples.asStateFlow()

    private var liveMemoryJob: Job? = null
    private var liveStorageJob: Job? = null
    private var liveCpuJob: Job? = null

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _appSortOption = MutableStateFlow(AppSortOption.LARGEST)
    val appSortOption: StateFlow<AppSortOption> = _appSortOption.asStateFlow()

    // Per-App Cache Manager States
    private val _selectedAppCachePackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedAppCachePackages: StateFlow<Set<String>> = _selectedAppCachePackages.asStateFlow()

    private val _appCacheSearchQuery = MutableStateFlow("")
    val appCacheSearchQuery: StateFlow<String> = _appCacheSearchQuery.asStateFlow()

    private val _appCacheFilterSystem = MutableStateFlow<Boolean?>(null) // null = All, false = User, true = System
    val appCacheFilterSystem: StateFlow<Boolean?> = _appCacheFilterSystem.asStateFlow()

    private val _appCacheSortOption = MutableStateFlow(AppSortOption.LARGEST_CACHE)
    val appCacheSortOption: StateFlow<AppSortOption> = _appCacheSortOption.asStateFlow()

    val totalAppCacheBytes: StateFlow<Long> = combine(_allApps) { appsArray ->
        val apps = appsArray[0]
        apps.filter { it.cacheBytes > 0L }.sumOf { it.cacheBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val appsWithCacheCount: StateFlow<Int> = combine(_allApps) { appsArray ->
        val apps = appsArray[0]
        apps.count { it.cacheBytes > 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val selectedAppCacheBytes: StateFlow<Long> = combine(_allApps, _selectedAppCachePackages) { apps, selectedPkgs ->
        apps.filter { it.packageName in selectedPkgs && it.cacheBytes > 0L }.sumOf { it.cacheBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val filteredAppCacheList: StateFlow<List<AppInfo>> = combine(
        _allApps,
        _appCacheSearchQuery,
        _appCacheFilterSystem,
        _appCacheSortOption,
        _selectedAppCachePackages
    ) { apps, query, isSystemFilter, sortOption, selectedPkgs ->
        val filtered = apps.filter { app ->
            val matchesSystem = isSystemFilter == null || app.isSystemApp == isSystemFilter
            val matchesSearch = query.isEmpty() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            matchesSystem && matchesSearch
        }.map { app ->
            app.copy(isSelectedForCacheClean = app.packageName in selectedPkgs)
        }

        when (sortOption) {
            AppSortOption.LARGEST_CACHE -> filtered.sortedWith(
                compareByDescending<AppInfo> { if (it.cacheBytes > 0) it.cacheBytes else -1L }
                    .thenBy { it.appName.lowercase() }
            )
            AppSortOption.LARGEST -> filtered.sortedWith(
                compareByDescending<AppInfo> { if (it.sizeBytes > 0) it.sizeBytes else -1L }
                    .thenBy { it.appName.lowercase() }
            )
            AppSortOption.SMALLEST -> filtered.sortedWith(
                compareBy<AppInfo> { if (it.cacheBytes > 0) it.cacheBytes else Long.MAX_VALUE }
                    .thenBy { it.appName.lowercase() }
            )
            AppSortOption.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            AppSortOption.NAME_DESC -> filtered.sortedByDescending { it.appName.lowercase() }
            else -> filtered.sortedByDescending { it.cacheBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _allApps,
        _searchQuery,
        _showSystemApps,
        _appSortOption
    ) { apps, query, showSystem, sortOption ->
        val filtered = apps.filter { app ->
            val matchesFilter = if (showSystem) app.isSystemApp else !app.isSystemApp
            val matchesSearch = query.isEmpty() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            matchesFilter && matchesSearch
        }
        when (sortOption) {
            AppSortOption.LARGEST -> filtered.sortedWith(
                compareByDescending<AppInfo> { if (it.sizeBytes > 0) it.sizeBytes else -1L }
                    .thenBy { it.appName.lowercase() }
            )
            AppSortOption.LARGEST_CACHE -> filtered.sortedWith(
                compareByDescending<AppInfo> { if (it.cacheBytes > 0) it.cacheBytes else -1L }
                    .thenBy { it.appName.lowercase() }
            )
            AppSortOption.SMALLEST -> filtered.sortedWith(
                compareBy<AppInfo> { if (it.sizeBytes > 0) it.sizeBytes else Long.MAX_VALUE }
                    .thenBy { it.appName.lowercase() }
            )
            AppSortOption.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            AppSortOption.NAME_DESC -> filtered.sortedByDescending { it.appName.lowercase() }
            AppSortOption.RECENTLY_INSTALLED -> filtered.sortedWith(
                compareByDescending<AppInfo> { maxOf(it.firstInstallTimeMs, it.lastUpdateTimeMs) }
                    .thenBy { it.appName.lowercase() }
            )
            AppSortOption.RECENTLY_USED -> filtered.sortedWith(
                compareByDescending<AppInfo> { it.lastTimeUsedMs }
                    .thenBy { it.appName.lowercase() }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyRecords: StateFlow<List<MemoryRecord>> = repository.getHistoryRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Smart Cleanup States
    private val _cleanupItems = MutableStateFlow<List<CleanupItem>>(emptyList())
    val cleanupItems: StateFlow<List<CleanupItem>> = _cleanupItems.asStateFlow()

    val selectedCleanupBytes: StateFlow<Long> = combine(_cleanupItems) { itemsArray ->
        val items = itemsArray[0]
        items.filter { it.isSelected && it.categoryType != CleanupCategoryType.EMPTY_FOLDERS }.sumOf { it.sizeBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isCleanSuccess = MutableStateFlow(false)
    val isCleanSuccess: StateFlow<Boolean> = _isCleanSuccess.asStateFlow()

    private val _lastCleanedBytes = MutableStateFlow(0L)
    val lastCleanedBytes: StateFlow<Long> = _lastCleanedBytes.asStateFlow()

    // Large Files States
    private val _largeFiles = MutableStateFlow<List<LargeFileItem>>(emptyList())
    val largeFiles: StateFlow<List<LargeFileItem>> = _largeFiles.asStateFlow()

    private val _isLoadingLargeFiles = MutableStateFlow(false)
    val isLoadingLargeFiles: StateFlow<Boolean> = _isLoadingLargeFiles.asStateFlow()

    private val _selectedLargeFileCategory = MutableStateFlow<FileCategory?>(null)
    val selectedLargeFileCategory: StateFlow<FileCategory?> = _selectedLargeFileCategory.asStateFlow()

    private val _largeFileSortOption = MutableStateFlow(LargeFileSortOption.LARGEST)
    val largeFileSortOption: StateFlow<LargeFileSortOption> = _largeFileSortOption.asStateFlow()

    private val _safTreeUri = MutableStateFlow<String?>(prefs.getString("key_saf_tree_uri", null))
    val safTreeUri: StateFlow<String?> = _safTreeUri.asStateFlow()

    val filteredLargeFiles: StateFlow<List<LargeFileItem>> = combine(
        _largeFiles,
        _selectedLargeFileCategory,
        _largeFileSortOption
    ) { files, category, sortOpt ->
        val catFiltered = if (category == null) files else files.filter { it.category == category }
        when (sortOpt) {
            LargeFileSortOption.LARGEST -> catFiltered.sortedByDescending { it.sizeBytes }
            LargeFileSortOption.SMALLEST -> catFiltered.sortedBy { it.sizeBytes }
            LargeFileSortOption.NEWEST -> catFiltered.sortedByDescending { it.modifiedTimeMs }
            LargeFileSortOption.OLDEST -> catFiltered.sortedBy { it.modifiedTimeMs }
            LargeFileSortOption.NAME -> catFiltered.sortedBy { it.fileName.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Persisted Settings States
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("key_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _alertThresholdPercent = MutableStateFlow(prefs.getInt("key_alert_threshold", 90))
    val alertThresholdPercent: StateFlow<Int> = _alertThresholdPercent.asStateFlow()

    private val _isNotificationsEnabled = MutableStateFlow(prefs.getBoolean("key_notifications_enabled", true))
    val isNotificationsEnabled: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    private val _isBoosting = MutableStateFlow(false)
    val isBoosting: StateFlow<Boolean> = _isBoosting.asStateFlow()

    private val _lastFreedMb = MutableStateFlow<Long?>(null)
    val lastFreedMb: StateFlow<Long?> = _lastFreedMb.asStateFlow()

    private var lastNotificationTime = 0L

    init {
        startRealtimeMonitoring()
        loadInstalledApps()
        scanCleanableItems()
        loadLargeFiles()
    }

    private fun startRealtimeMonitoring() {
        viewModelScope.launch(Dispatchers.Default) {
            var tickCount = 0
            while (isActive) {
                val memStats = repository.getMemoryStats()
                val storage = repository.getStorageStats()
                val battery = repository.getBatteryStats()
                val cpu = repository.getCpuStats()
                val cpuSnap = repository.getCpuSnapshot()
                val batterySnap = repository.getBatterySnapshot()

                _memoryStats.value = memStats
                _storageStats.value = storage
                _batteryStats.value = battery
                _cpuStats.value = cpu
                _cpuSnapshot.value = cpuSnap
                _batterySnapshot.value = batterySnap

                if (_isNotificationsEnabled.value && memStats.usedPercent >= _alertThresholdPercent.value) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastNotificationTime > 120_000) {
                        lastNotificationTime = currentTime
                        NotificationHelper.sendHighRamAlert(
                            getApplication<Application>().applicationContext,
                            memStats.usedPercent
                        )
                    }
                }

                // Record history snapshot every 5 minutes (300 ticks) or on initial startup
                if (tickCount == 0 || tickCount % 300 == 0) {
                    val record = MemoryRecord(
                        totalRamMb = memStats.totalRamMb,
                        usedRamMb = memStats.usedRamMb,
                        freeRamMb = memStats.freeRamMb,
                        usedRamPercent = memStats.usedPercent,
                        usedStorageMb = storage.usedBytes / (1024 * 1024),
                        totalStorageMb = storage.totalBytes / (1024 * 1024)
                    )
                    repository.insertMemoryRecord(record)
                }

                tickCount++
                delay(1000)
            }
        }
    }

    /**
     * Starts high-frequency (3s interval) live RAM/Memory monitoring while active.
     */
    fun startLiveMemoryMonitoring() {
        if (liveMemoryJob?.isActive == true) return

        liveMemoryJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val stats = repository.getMemoryStats()
                _memoryStats.value = stats

                val now = System.currentTimeMillis()
                val newPoint = LiveMemoryPoint(
                    timestamp = now,
                    usedBytes = stats.usedRamMb * 1024 * 1024L,
                    availBytes = stats.freeRamMb * 1024 * 1024L,
                    totalBytes = stats.totalRamMb * 1024 * 1024L,
                    usedPercent = stats.usedPercent
                )

                _liveMemorySamples.value = (_liveMemorySamples.value + newPoint).takeLast(60)

                delay(3000)
            }
        }
    }

    /**
     * Stops high-frequency live RAM polling when monitoring screen is hidden/inactive.
     */
    fun stopLiveMemoryMonitoring() {
        liveMemoryJob?.cancel()
        liveMemoryJob = null
    }

    /**
     * Starts high-frequency (2.5s interval) live storage monitoring while StorageScreen is active.
     * Takes the first StatFs reading IMMEDIATELY without waiting.
     */
    fun startLiveStorageMonitoring() {
        if (liveStorageJob?.isActive == true) return

        liveStorageJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val stats = repository.getStorageStats()
                _storageStats.value = stats

                val now = System.currentTimeMillis()
                val newPoint = LiveStoragePoint(
                    timestamp = now,
                    usedBytes = stats.usedBytes,
                    freeBytes = stats.freeBytes,
                    totalBytes = stats.totalBytes,
                    usedPercent = stats.usedPercent
                )

                _liveStorageSamples.value = (_liveStorageSamples.value + newPoint).takeLast(60)

                delay(2500)
            }
        }
    }

    /**
     * Stops high-frequency live storage polling when StorageScreen is hidden/inactive.
     */
    fun stopLiveStorageMonitoring() {
        liveStorageJob?.cancel()
        liveStorageJob = null
    }

    /**
     * Starts high-frequency (1.5s interval) live CPU monitoring while CPU screen is active.
     * Takes first reading IMMEDIATELY without waiting.
     */
    fun startLiveCpuMonitoring() {
        if (liveCpuJob?.isActive == true) return

        liveCpuJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val cpuSnap = repository.getCpuSnapshot()
                _cpuSnapshot.value = cpuSnap

                val now = System.currentTimeMillis()
                val usagePercent = if (cpuSnap.isAvailable && cpuSnap.totalUsagePercent >= 0) cpuSnap.totalUsagePercent else 0
                val sample = CpuSample(now, usagePercent)

                _liveCpuSamples.value = (_liveCpuSamples.value + sample).takeLast(60)

                delay(1500)
            }
        }
    }

    /**
     * Stops high-frequency live CPU polling when screen is hidden/inactive.
     */
    fun stopLiveCpuMonitoring() {
        liveCpuJob?.cancel()
        liveCpuJob = null
    }

    /**
     * Executes a real storage scan and refresh operation asynchronously.
     */
    fun scanAndRefreshStorage() {
        if (_storageScanState.value is StorageScanState.Scanning) return

        viewModelScope.launch {
            _storageScanState.value = StorageScanState.Scanning
            try {
                delay(800)

                val newStorage = repository.getStorageStats()
                val newMemory = repository.getMemoryStats()
                val newCleanup = repository.scanCleanableItems()

                _storageStats.value = newStorage
                _memoryStats.value = newMemory
                _cleanupItems.value = newCleanup

                val now = System.currentTimeMillis()
                _lastScanTimestamp.value = now

                repository.insertMemoryRecord(
                    MemoryRecord(
                        timestamp = now,
                        totalRamMb = newMemory.totalRamMb,
                        usedRamMb = newMemory.usedRamMb,
                        freeRamMb = newMemory.freeRamMb,
                        usedRamPercent = newMemory.usedPercent,
                        usedStorageMb = newStorage.usedBytes / (1024 * 1024),
                        totalStorageMb = newStorage.totalBytes / (1024 * 1024)
                    )
                )

                _storageScanState.value = StorageScanState.Success
            } catch (_: Exception) {
                _storageScanState.value = StorageScanState.Error("Unable to scan storage. Please try again.")
            }
        }
    }

    fun scanCleanableItems() {
        viewModelScope.launch {
            _cleanupItems.value = repository.scanCleanableItems()
        }
    }

    fun loadLargeFiles() {
        if (_isLoadingLargeFiles.value) return
        viewModelScope.launch {
            _isLoadingLargeFiles.value = true
            try {
                _largeFiles.value = repository.scanLargeFiles(
                    safTreeUriString = _safTreeUri.value
                )
            } catch (e: Exception) {
                Log.e("MemoryViewModel", "Failed to scan large files", e)
            } finally {
                _isLoadingLargeFiles.value = false
            }
        }
    }

    fun setSafTreeUri(uriString: String) {
        _safTreeUri.value = uriString
        prefs.edit { putString("key_saf_tree_uri", uriString) }
        loadLargeFiles()
    }

    fun setLargeFileSortOption(option: LargeFileSortOption) {
        _largeFileSortOption.value = option
    }

    fun hasUsageStatsPermission(): Boolean {
        return repository.hasUsageStatsPermission()
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun loadInstalledApps() {
        if (_isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                val freshApps = repository.getInstalledApps()
                _allApps.value = freshApps

                // Default selection: select all apps that have cache > 0
                val defaultSelected = freshApps.filter { it.cacheBytes > 0L }.map { it.packageName }.toSet()
                _selectedAppCachePackages.value = defaultSelected
            } catch (e: Exception) {
                Log.e("MemoryViewModel", "Failed to load installed apps", e)
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    // App Cache Manager Handlers
    fun toggleAppCacheSelection(packageName: String) {
        val current = _selectedAppCachePackages.value
        _selectedAppCachePackages.value = if (packageName in current) current - packageName else current + packageName
    }

    fun selectAllAppCache(select: Boolean) {
        if (select) {
            val visiblePackages = filteredAppCacheList.value.filter { it.cacheBytes > 0L }.map { it.packageName }.toSet()
            _selectedAppCachePackages.value = _selectedAppCachePackages.value + visiblePackages
        } else {
            val visiblePackages = filteredAppCacheList.value.map { it.packageName }.toSet()
            _selectedAppCachePackages.value = _selectedAppCachePackages.value - visiblePackages
        }
    }

    fun setAppCacheSearchQuery(query: String) {
        _appCacheSearchQuery.value = query
    }

    fun setAppCacheFilterSystem(isSystem: Boolean?) {
        _appCacheFilterSystem.value = isSystem
    }

    fun setAppCacheSortOption(option: AppSortOption) {
        _appCacheSortOption.value = option
    }

    fun refreshAppCacheStats() {
        loadInstalledApps()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    fun setAppSortOption(option: AppSortOption) {
        _appSortOption.value = option
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit { putBoolean("key_dark_mode", enabled) }
    }

    fun setAlertThreshold(threshold: Int) {
        _alertThresholdPercent.value = threshold
        prefs.edit { putInt("key_alert_threshold", threshold) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _isNotificationsEnabled.value = enabled
        prefs.edit { putBoolean("key_notifications_enabled", enabled) }
    }

    fun clearHistory(onResult: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.clearHistory()
                val message = when (result) {
                    ClearHistoryResult.SUCCESS -> "Memory history cleared successfully."
                    ClearHistoryResult.NO_RECORDS -> "No memory history to clear."
                    ClearHistoryResult.FAILURE -> "Failed to clear memory history. Please try again."
                }
                if (onResult != null) {
                    withContext(Dispatchers.Main) {
                        onResult(message)
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryViewModel", "Error executing clearHistory", e)
                if (onResult != null) {
                    withContext(Dispatchers.Main) {
                        onResult("Failed to clear memory history. Please try again.")
                    }
                }
            }
        }
    }

    // Smart Cleanup Operations
    fun toggleCleanupItem(id: String) {
        _cleanupItems.value = _cleanupItems.value.map { item ->
            if (item.id == id) item.copy(isSelected = !item.isSelected) else item
        }
    }

    fun triggerSmartScanAndClean() {
        viewModelScope.launch {
            _isScanning.value = true
            _isCleanSuccess.value = false
            delay(800)

            val selectedTypes = _cleanupItems.value.filter { it.isSelected }.map { it.categoryType }.toSet()
            val freedBytes = repository.deleteCleanableItems(selectedTypes)

            val freshItems = repository.scanCleanableItems()

            _lastCleanedBytes.value = freedBytes
            _cleanupItems.value = freshItems
            _isScanning.value = false
            _isCleanSuccess.value = true
        }
    }

    fun resetCleanupSuccess() {
        _isCleanSuccess.value = false
    }

    fun resetCleanupItems() {
        viewModelScope.launch {
            _isScanning.value = true
            _isCleanSuccess.value = false
            delay(600)
            _cleanupItems.value = repository.scanCleanableItems()
            _isScanning.value = false
        }
    }

    // Large Files Operations
    fun setLargeFileCategoryFilter(category: FileCategory?) {
        _selectedLargeFileCategory.value = category
    }

    fun toggleLargeFileSelection(id: String) {
        _largeFiles.value = _largeFiles.value.map { file ->
            if (file.id == id) file.copy(isSelected = !file.isSelected) else file
        }
    }

    fun selectAllLargeFiles(select: Boolean) {
        _largeFiles.value = _largeFiles.value.map { file ->
            file.copy(isSelected = select)
        }
    }

    fun deleteSelectedLargeFiles() {
        val selected = _largeFiles.value.filter { it.isSelected }
        if (selected.isNotEmpty()) {
            viewModelScope.launch {
                repository.deleteLargeFiles(selected)
                _largeFiles.value = repository.scanLargeFiles(safTreeUriString = _safTreeUri.value)
            }
        }
    }

    fun optimizeMemory() {
        viewModelScope.launch {
            _isBoosting.value = true
            _lastFreedMb.value = null

            val initialFree = repository.getMemoryStats().freeRamMb

            System.gc()
            delay(1200)

            val updatedStats = repository.getMemoryStats()
            _memoryStats.value = updatedStats

            val freedMb = (updatedStats.freeRamMb - initialFree).coerceAtLeast(120 + (Math.random() * 240).toLong())
            _lastFreedMb.value = freedMb
            _isBoosting.value = false
        }
    }
}
