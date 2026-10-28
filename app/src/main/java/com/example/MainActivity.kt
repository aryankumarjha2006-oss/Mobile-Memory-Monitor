package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.CleanupScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LargeFilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StorageScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MemoryMonitorTheme
import com.example.ui.viewmodel.MemoryViewModel
import com.example.util.RootDetectionManager
import com.example.util.RootWarningDialog
import com.example.util.ScreenProtectionManager
import com.example.util.SessionManager

class MainActivity : ComponentActivity() {

    private val viewModel: MemoryViewModel by viewModels()
    private var isRooted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenProtectionManager.applyScreenProtection(this)
        enableEdgeToEdge()

        val sessionManager = SessionManager.getInstance(this)
        sessionManager.updateUserActivity()

        val rootManager = RootDetectionManager.getInstance()
        isRooted = rootManager.isDeviceRooted(this)
        if (isRooted) {
            RootWarningDialog.showRootWarningDialog(this)
        }

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            MemoryMonitorTheme(darkTheme = isDarkMode) {
                MainAppScreen(viewModel = viewModel, isRooted = isRooted)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sessionManager = SessionManager.getInstance(this)
        sessionManager.updateUserActivity()
    }
}

data class NavigationTab(
    val title: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun MainAppScreen(viewModel: MemoryViewModel, isRooted: Boolean = false) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLargeFilesScreen by remember { mutableStateOf(false) }

    val tabs = listOf(
        NavigationTab("Home", Icons.Default.Home, "nav_dashboard_tab"),
        NavigationTab("Storage", Icons.Default.SdCard, "nav_storage_tab"),
        NavigationTab("Clean", Icons.Default.CleaningServices, "nav_clean_tab"),
        NavigationTab("Apps", Icons.Default.Apps, "nav_apps_tab"),
        NavigationTab("Settings", Icons.Default.Settings, "nav_settings_tab")
    )

    val memoryStats by viewModel.memoryStats.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val batteryStats by viewModel.batteryStats.collectAsStateWithLifecycle()
    val cpuStats by viewModel.cpuStats.collectAsStateWithLifecycle()
    val cpuSnapshot by viewModel.cpuSnapshot.collectAsStateWithLifecycle()
    val batterySnapshot by viewModel.batterySnapshot.collectAsStateWithLifecycle()
    val liveMemorySamples by viewModel.liveMemorySamples.collectAsStateWithLifecycle()
    val liveStorageSamples by viewModel.liveStorageSamples.collectAsStateWithLifecycle()
    val liveCpuSamples by viewModel.liveCpuSamples.collectAsStateWithLifecycle()
    val storageScanState by viewModel.storageScanState.collectAsStateWithLifecycle()
    val lastScanTimestamp by viewModel.lastScanTimestamp.collectAsStateWithLifecycle()

    val isRamMonitorRunning by viewModel.isRamMonitorServiceRunning.collectAsStateWithLifecycle()
    val latestTriggerEvent by viewModel.latestTriggerEvent.collectAsStateWithLifecycle()

    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val appSortOption by viewModel.appSortOption.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val alertThresholdPercent by viewModel.alertThresholdPercent.collectAsStateWithLifecycle()
    val isNotificationsEnabled by viewModel.isNotificationsEnabled.collectAsStateWithLifecycle()
    val isBoosting by viewModel.isBoosting.collectAsStateWithLifecycle()
    val lastFreedMb by viewModel.lastFreedMb.collectAsStateWithLifecycle()

    val cleanupItems by viewModel.cleanupItems.collectAsStateWithLifecycle()
    val selectedCleanupBytes by viewModel.selectedCleanupBytes.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isCleanSuccess by viewModel.isCleanSuccess.collectAsStateWithLifecycle()
    val lastCleanedBytes by viewModel.lastCleanedBytes.collectAsStateWithLifecycle()

    val filteredLargeFiles by viewModel.filteredLargeFiles.collectAsStateWithLifecycle()
    val isLoadingLargeFiles by viewModel.isLoadingLargeFiles.collectAsStateWithLifecycle()
    val selectedLargeFileCategory by viewModel.selectedLargeFileCategory.collectAsStateWithLifecycle()
    val largeFileSortOption by viewModel.largeFileSortOption.collectAsStateWithLifecycle()
    val safTreeUri by viewModel.safTreeUri.collectAsStateWithLifecycle()

    // Per-App Cache Manager StateFlow Collections
    val filteredAppCacheList by viewModel.filteredAppCacheList.collectAsStateWithLifecycle()
    val totalAppCacheBytes by viewModel.totalAppCacheBytes.collectAsStateWithLifecycle()
    val appsWithCacheCount by viewModel.appsWithCacheCount.collectAsStateWithLifecycle()
    val selectedAppCacheBytes by viewModel.selectedAppCacheBytes.collectAsStateWithLifecycle()
    val appCacheSearchQuery by viewModel.appCacheSearchQuery.collectAsStateWithLifecycle()
    val appCacheFilterSystem by viewModel.appCacheFilterSystem.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!showLargeFilesScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (showLargeFilesScreen) {
                LargeFilesScreen(
                    largeFiles = filteredLargeFiles,
                    selectedCategory = selectedLargeFileCategory,
                    isLoading = isLoadingLargeFiles,
                    currentSortOption = largeFileSortOption,
                    safTreeUri = safTreeUri,
                    onCategorySelect = { viewModel.setLargeFileCategoryFilter(it) },
                    onSortOptionChange = { viewModel.setLargeFileSortOption(it) },
                    onToggleSelect = { viewModel.toggleLargeFileSelection(it) },
                    onSelectAll = { viewModel.selectAllLargeFiles(it) },
                    onDeleteSelected = { viewModel.deleteSelectedLargeFiles() },
                    onSafTreeUriSelected = { viewModel.setSafTreeUri(it) },
                    onRefresh = { viewModel.loadLargeFiles() },
                    onBack = { showLargeFilesScreen = false }
                )
            } else {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        memoryStats = memoryStats,
                        storageStats = storageStats,
                        batteryStats = batteryStats,
                        cpuStats = cpuStats,
                        cpuSnapshot = cpuSnapshot,
                        batterySnapshot = batterySnapshot,
                        liveMemorySamples = liveMemorySamples,
                        liveCpuSamples = liveCpuSamples,
                        cleanableBytes = selectedCleanupBytes,
                        storageScanState = storageScanState,
                        lastScanTimestamp = lastScanTimestamp,
                        isBoosting = isBoosting,
                        lastFreedMb = lastFreedMb,
                        isRooted = isRooted,
                        isRamMonitorRunning = isRamMonitorRunning,
                        latestTriggerEvent = latestTriggerEvent,
                        onToggleRamMonitor = { enable -> viewModel.toggleRamMonitorService(enable) },
                        onOptimizeClick = { viewModel.optimizeMemory() },
                        onScanStorage = { viewModel.scanAndRefreshStorage() },
                        onStartLiveMonitoring = {
                            viewModel.startLiveMemoryMonitoring()
                            viewModel.startLiveCpuMonitoring()
                        },
                        onStopLiveMonitoring = {
                            viewModel.stopLiveMemoryMonitoring()
                            viewModel.stopLiveCpuMonitoring()
                        },
                        onNavigateTab = { tabIndex -> selectedTab = tabIndex },
                        onOpenLargeFiles = { showLargeFilesScreen = true }
                    )
                    1 -> StorageScreen(
                        storageStats = storageStats,
                        liveStorageSamples = liveStorageSamples,
                        storageScanState = storageScanState,
                        onScanStorage = { viewModel.scanAndRefreshStorage() },
                        onStartLiveMonitoring = { viewModel.startLiveStorageMonitoring() },
                        onStopLiveMonitoring = { viewModel.stopLiveStorageMonitoring() }
                    )
                    2 -> CleanupScreen(
                        cleanupItems = cleanupItems,
                        selectedCleanupBytes = selectedCleanupBytes,
                        isScanning = isScanning,
                        isCleanSuccess = isCleanSuccess,
                        lastCleanedBytes = lastCleanedBytes,
                        appCacheList = filteredAppCacheList,
                        totalAppCacheBytes = totalAppCacheBytes,
                        appsWithCacheCount = appsWithCacheCount,
                        selectedAppCacheBytes = selectedAppCacheBytes,
                        isLoadingAppCache = isLoadingApps,
                        hasUsagePermission = viewModel.hasUsageStatsPermission(),
                        appCacheSearchQuery = appCacheSearchQuery,
                        appCacheFilterSystem = appCacheFilterSystem,
                        onToggleItem = { viewModel.toggleCleanupItem(it) },
                        onStartClean = { viewModel.triggerSmartScanAndClean() },
                        onResetCleanSuccess = { viewModel.resetCleanupSuccess() },
                        onResetItems = { viewModel.resetCleanupItems() },
                        onToggleAppCacheSelect = { viewModel.toggleAppCacheSelection(it) },
                        onSelectAllAppCache = { viewModel.selectAllAppCache(it) },
                        onAppCacheSearchChange = { viewModel.setAppCacheSearchQuery(it) },
                        onAppCacheFilterSystemChange = { viewModel.setAppCacheFilterSystem(it) },
                        onRefreshAppCache = { viewModel.refreshAppCacheStats() }
                    )
                    3 -> AppsScreen(
                        apps = filteredApps,
                        searchQuery = searchQuery,
                        showSystemApps = showSystemApps,
                        isLoadingApps = isLoadingApps,
                        hasUsagePermission = viewModel.hasUsageStatsPermission(),
                        currentSortOption = appSortOption,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onShowSystemAppsChange = { viewModel.setShowSystemApps(it) },
                        onSortOptionChange = { viewModel.setAppSortOption(it) },
                        onRefresh = { viewModel.loadInstalledApps() }
                    )
                    4 -> SettingsScreen(
                        isDarkMode = isDarkMode,
                        alertThresholdPercent = alertThresholdPercent,
                        isNotificationsEnabled = isNotificationsEnabled,
                        onDarkModeToggle = { viewModel.toggleDarkMode(it) },
                        onAlertThresholdChange = { viewModel.setAlertThreshold(it) },
                        onNotificationsEnabledChange = { viewModel.setNotificationsEnabled(it) },
                        onClearHistory = { onResult -> viewModel.clearHistory(onResult) }
                    )
                }
            }
        }
    }
}
