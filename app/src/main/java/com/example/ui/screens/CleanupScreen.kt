package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.AppCacheStatus
import com.example.data.model.AppInfo
import com.example.data.model.CleanupCategoryType
import com.example.data.model.CleanupItem
import com.example.data.model.ScanStatus
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PrimaryActionButton
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.ElectricCyan
import com.example.util.MemoryFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    cleanupItems: List<CleanupItem>,
    selectedCleanupBytes: Long = 0L,
    isScanning: Boolean,
    isCleanSuccess: Boolean,
    lastCleanedBytes: Long,
    // Per-App Cache Manager Parameters
    appCacheList: List<AppInfo> = emptyList(),
    totalAppCacheBytes: Long = 0L,
    appsWithCacheCount: Int = 0,
    selectedAppCacheBytes: Long = 0L,
    isLoadingAppCache: Boolean = false,
    hasUsagePermission: Boolean = true,
    appCacheSearchQuery: String = "",
    appCacheFilterSystem: Boolean? = null,
    onToggleItem: (String) -> Unit,
    onStartClean: () -> Unit,
    onResetCleanSuccess: () -> Unit,
    onResetItems: () -> Unit,
    onToggleAppCacheSelect: (String) -> Unit = {},
    onSelectAllAppCache: (Boolean) -> Unit = {},
    onAppCacheSearchChange: (String) -> Unit = {},
    onAppCacheFilterSystemChange: (Boolean?) -> Unit = {},
    onRefreshAppCache: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    val totalSelectedSmartBytes = if (selectedCleanupBytes > 0L) selectedCleanupBytes else cleanupItems.filter { it.isSelected && it.categoryType != CleanupCategoryType.EMPTY_FOLDERS }.sumOf { it.sizeBytes }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCleanSuccess) {
            // Polished Success Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AccentEmerald.copy(alpha = 0.15f),
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Storage Cleaned",
                                tint = AccentEmerald,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Storage Cleaned!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${MemoryFormatter.formatBytes(lastCleanedBytes)} recovered",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Cleanup completed successfully. Your storage is running at optimal efficiency.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResetItems,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Again",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Again")
                        }

                        PrimaryActionButton(
                            text = "Done",
                            onClick = onResetCleanSuccess,
                            modifier = Modifier.width(130.dp)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Screen Header
                Column {
                    Text(
                        text = "Smart Cleanup",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Cleanable Storage",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sub-tabs: Smart Cleanup vs Per-App Cache Manager
                SecondaryTabRow(
                    selectedTabIndex = selectedSubTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        text = {
                            Text(
                                text = "Smart Cleanup",
                                fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                    Tab(
                        selected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        text = {
                            Text(
                                text = "App Cache ($appsWithCacheCount)",
                                fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedSubTab == 0) {
                    // TAB 1: Smart Cleanup Categories
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (isScanning) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = ElectricCyan,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Scanning system storage & junk caches...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = ElectricCyan,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        } else if (cleanupItems.isEmpty()) {
                            EmptyStateView(
                                title = "Nothing to Clean",
                                description = "Your accessible temporary files are already cleared.",
                                actionLabel = "Re-scan Storage",
                                onAction = onResetItems
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Cleanable Storage",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = MemoryFormatter.formatBytes(totalSelectedSmartBytes),
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElectricCyan
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = ElectricCyan.copy(alpha = 0.12f),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CleaningServices,
                                                contentDescription = "Cleanup",
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Cleanup Categories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            cleanupItems.forEach { item ->
                                CleanupCategoryCardItem(
                                    item = item,
                                    onToggle = { onToggleItem(item.id) }
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            val buttonText = if (totalSelectedSmartBytes > 0L) {
                                "Clean ${MemoryFormatter.formatBytes(totalSelectedSmartBytes)}"
                            } else {
                                "Nothing to Clean"
                            }

                            PrimaryActionButton(
                                text = buttonText,
                                onClick = onStartClean,
                                enabled = totalSelectedSmartBytes > 0L,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("clean_selected_button")
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                } else {
                    // TAB 2: Per-App Cache Manager
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Summary Header Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Total App Cache",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = MemoryFormatter.formatBytes(totalAppCacheBytes),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElectricCyan
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Selected Cache",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = MemoryFormatter.formatBytes(selectedAppCacheBytes),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentEmerald
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "$appsWithCacheCount applications currently have cache stored.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (!hasUsagePermission) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Usage Access",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Usage Access permission is required to query app cache sizes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                                    ) {
                                        Text("Grant Access")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Search & Filter Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = appCacheSearchQuery,
                                onValueChange = onAppCacheSearchChange,
                                placeholder = { Text("Search apps...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .sizeIn(minHeight = 48.dp)
                            )

                            IconButton(
                                onClick = onRefreshAppCache,
                                enabled = !isLoadingAppCache,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                if (isLoadingAppCache) {
                                    CircularProgressIndicator(color = ElectricCyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Cache", tint = ElectricCyan)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // App Type Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = appCacheFilterSystem == null,
                                onClick = { onAppCacheFilterSystemChange(null) },
                                label = { Text("All Apps") },
                                modifier = Modifier.sizeIn(minHeight = 48.dp)
                            )
                            FilterChip(
                                selected = appCacheFilterSystem == false,
                                onClick = { onAppCacheFilterSystemChange(false) },
                                label = { Text("User Apps") },
                                modifier = Modifier.sizeIn(minHeight = 48.dp)
                            )
                            FilterChip(
                                selected = appCacheFilterSystem == true,
                                onClick = { onAppCacheFilterSystemChange(true) },
                                label = { Text("System Apps") },
                                modifier = Modifier.sizeIn(minHeight = 48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingAppCache) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = ElectricCyan)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Reading app cache sizes...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else if (appCacheList.isEmpty()) {
                            EmptyStateView(
                                title = "No Apps Found",
                                description = "No installed applications match your current search or filter criteria.",
                                actionLabel = "Refresh List",
                                onAction = onRefreshAppCache
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(appCacheList, key = { it.packageName }) { app ->
                                    AppCacheRowItem(
                                        app = app,
                                        onToggleSelect = { onToggleAppCacheSelect(app.packageName) },
                                        onOpenAppInfo = {
                                            try {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:${app.packageName}")
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bottom Clean Selected Cache Button
                            if (selectedAppCacheBytes > 0L) {
                                Button(
                                    onClick = {
                                        // Open first selected app's settings page
                                        val firstPkg = appCacheList.firstOrNull { it.isSelectedForCacheClean && it.cacheBytes > 0L }?.packageName
                                        if (firstPkg != null) {
                                            try {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:$firstPkg")
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .sizeIn(minHeight = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Clear Cache",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Clear Selected Cache in Settings (${MemoryFormatter.formatBytes(selectedAppCacheBytes)})",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppCacheRowItem(
    app: AppInfo,
    onToggleSelect: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    Card(
        onClick = onToggleSelect,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (app.isSelectedForCacheClean) ElectricCyan.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .sizeIn(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = app.isSelectedForCacheClean,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricCyan,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon.toBitmap(40, 40).asImageBitmap(),
                            contentDescription = app.appName,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = app.appName,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                val displaySizeText = when {
                    app.cacheScanStatus == AppCacheStatus.RESTRICTED -> "Restricted"
                    app.cacheBytes >= 0L -> MemoryFormatter.formatBytes(app.cacheBytes)
                    else -> "Unable to read"
                }

                Text(
                    text = displaySizeText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (app.cacheScanStatus == AppCacheStatus.RESTRICTED) MaterialTheme.colorScheme.onSurfaceVariant else ElectricCyan
                )

                IconButton(
                    onClick = onOpenAppInfo,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open App Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CleanupCategoryCardItem(
    item: CleanupItem,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (item.isSelected) ElectricCyan.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .sizeIn(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricCyan,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ElectricCyan.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (item.categoryType) {
                            CleanupCategoryType.CACHE -> Icons.Default.CleaningServices
                            CleanupCategoryType.RESIDUAL -> Icons.Default.DeleteSweep
                            CleanupCategoryType.TEMP -> Icons.Default.History
                            CleanupCategoryType.EMPTY_FOLDERS -> Icons.Default.Folder
                            CleanupCategoryType.OLD_APK -> Icons.Default.Info
                            else -> Icons.Default.AutoAwesome
                        },
                        contentDescription = item.title,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val displayDesc = if (item.statusMessage != null) {
                    item.statusMessage
                } else if (item.categoryType == CleanupCategoryType.EMPTY_FOLDERS) {
                    if (item.count > 0) "${item.count} empty directories detected (0 B payload)" else "No empty directories detected"
                } else {
                    item.description
                }

                Text(
                    text = displayDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            val displaySizeText = when {
                item.scanStatus == ScanStatus.RESTRICTED -> "Restricted"
                item.categoryType == CleanupCategoryType.EMPTY_FOLDERS -> "0 B"
                item.sizeBytes > 0L -> MemoryFormatter.formatBytes(item.sizeBytes)
                else -> "0 B"
            }

            Text(
                text = displaySizeText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (item.scanStatus == ScanStatus.RESTRICTED) MaterialTheme.colorScheme.onSurfaceVariant else ElectricCyan
            )
        }
    }
}
