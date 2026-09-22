package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.LiveStoragePoint
import com.example.data.model.StorageScanState
import com.example.data.model.StorageStats
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CategoryAppsColor
import com.example.ui.theme.CategoryAudioColor
import com.example.ui.theme.CategoryDocsColor
import com.example.ui.theme.CategoryOtherColor
import com.example.ui.theme.CategoryPhotosColor
import com.example.ui.theme.CategoryVideosColor
import com.example.ui.theme.ElectricCyan
import com.example.util.MemoryFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StorageScreen(
    storageStats: StorageStats,
    liveStorageSamples: List<LiveStoragePoint> = emptyList(),
    storageScanState: StorageScanState = StorageScanState.Idle,
    onScanStorage: () -> Unit = {},
    onStartLiveMonitoring: () -> Unit = {},
    onStopLiveMonitoring: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                onStartLiveMonitoring()
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                onStopLiveMonitoring()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStopLiveMonitoring()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Storage Analytics",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Disk Details & Capacity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val isScanning = storageScanState is StorageScanState.Scanning
                IconButton(
                    onClick = onScanStorage,
                    enabled = !isScanning,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Storage",
                            tint = ElectricCyan
                        )
                    }
                }

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("manage_storage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Manage Storage",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manage", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Overview Capacity Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SdCard,
                            contentDescription = "Storage",
                            tint = ElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Internal Disk Volume",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricCyan.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${storageStats.usedPercent}% Used",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Total Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MemoryFormatter.formatBytes(storageStats.totalBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = "Used Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MemoryFormatter.formatBytes(storageStats.usedBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Free Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MemoryFormatter.formatBytes(storageStats.freeBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { storageStats.usedPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ElectricCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // ================= LIVE STORAGE USAGE MONITOR CARD =================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Live Monitor",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Storage Usage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${MemoryFormatter.formatBytes(storageStats.usedBytes)} used of ${MemoryFormatter.formatBytes(storageStats.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = CircleShape,
                            color = AccentEmerald.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Live • " + formatRelativeTime(liveStorageSamples.lastOrNull()?.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${storageStats.usedPercent}%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LiveStorageGraph(
                    samples = liveStorageSamples,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        }

        // Category Breakdown Section
        Text(
            text = "Category Breakdown",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val usedBytes = storageStats.usedBytes.coerceAtLeast(1L)

        DetailedCategoryCard(
            title = "Apps & Applications",
            sizeText = MemoryFormatter.formatBytes(storageStats.appsBytes),
            percentage = ((storageStats.appsBytes.toDouble() / usedBytes) * 100).toInt(),
            icon = Icons.Default.Apps,
            color = CategoryAppsColor
        )

        DetailedCategoryCard(
            title = "Photos & Gallery",
            sizeText = MemoryFormatter.formatBytes(storageStats.photosBytes),
            percentage = ((storageStats.photosBytes.toDouble() / usedBytes) * 100).toInt(),
            icon = Icons.Default.Image,
            color = CategoryPhotosColor
        )

        DetailedCategoryCard(
            title = "Videos & Clips",
            sizeText = MemoryFormatter.formatBytes(storageStats.videosBytes),
            percentage = ((storageStats.videosBytes.toDouble() / usedBytes) * 100).toInt(),
            icon = Icons.Default.VideoLibrary,
            color = CategoryVideosColor
        )

        DetailedCategoryCard(
            title = "Audio & Music",
            sizeText = MemoryFormatter.formatBytes(storageStats.audioBytes),
            percentage = ((storageStats.audioBytes.toDouble() / usedBytes) * 100).toInt(),
            icon = Icons.Default.AudioFile,
            color = CategoryAudioColor
        )

        DetailedCategoryCard(
            title = "Documents & Files",
            sizeText = MemoryFormatter.formatBytes(storageStats.documentsBytes),
            percentage = ((storageStats.documentsBytes.toDouble() / usedBytes) * 100).toInt(),
            icon = Icons.Default.Description,
            color = CategoryDocsColor
        )

        DetailedCategoryCard(
            title = "System OS & Reserved",
            sizeText = MemoryFormatter.formatBytes(storageStats.systemOtherBytes),
            percentage = ((storageStats.systemOtherBytes.toDouble() / usedBytes) * 100).toInt(),
            icon = Icons.Default.PermDeviceInformation,
            color = CategoryOtherColor
        )
    }
}

@Composable
fun LiveStorageGraph(
    samples: List<LiveStoragePoint>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(samples) { mutableStateOf<Int?>(null) }
    val primaryColor = ElectricCyan
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    if (samples.isEmpty()) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Starting live monitor…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val minBytes = samples.minOf { it.usedBytes }
    val maxBytes = samples.maxOf { it.usedBytes }
    val range = (maxBytes - minBytes).coerceAtLeast(1024 * 1024 * 50L) // Min 50MB Y-range

    Column(modifier = modifier) {
        val selectedPoint = selectedIndex?.let { samples.getOrNull(it) }
        if (selectedPoint != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormat.format(Date(selectedPoint.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Text(
                        text = "Used: ${MemoryFormatter.formatBytes(selectedPoint.usedBytes)} (${selectedPoint.usedPercent}%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Free: ${MemoryFormatter.formatBytes(selectedPoint.freeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentEmerald
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(samples) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val step = if (samples.size > 1) width / (samples.size - 1) else width
                            val index = if (samples.size > 1) {
                                (offset.x / step).toInt().coerceIn(0, samples.size - 1)
                            } else 0
                            selectedIndex = if (selectedIndex == index) null else index
                        }
                    }
            ) {
                val w = size.width
                val h = size.height - 18.dp.toPx()
                val dx = if (samples.size > 1) w / (samples.size - 1) else w / 2f

                for (p in listOf(0f, 0.5f, 1f)) {
                    val y = h * (1f - p)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (samples.size == 1) {
                    val y = h / 2f
                    drawLine(
                        color = primaryColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = AccentEmerald.copy(alpha = 0.3f),
                        radius = 7.dp.toPx(),
                        center = Offset(w / 2f, y)
                    )
                    drawCircle(
                        color = AccentEmerald,
                        radius = 4.dp.toPx(),
                        center = Offset(w / 2f, y)
                    )
                } else {
                    val path = Path()
                    val fillPath = Path()

                    samples.forEachIndexed { i, pt ->
                        val x = i * dx
                        val normalizedY = (pt.usedBytes - minBytes).toFloat() / range
                        val y = h - (normalizedY * (h * 0.75f)) - (h * 0.1f)

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }

                        if (i == samples.size - 1) {
                            fillPath.lineTo(x, h)
                            fillPath.close()
                        }
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    samples.forEachIndexed { i, pt ->
                        val x = i * dx
                        val normalizedY = (pt.usedBytes - minBytes).toFloat() / range
                        val y = h - (normalizedY * (h * 0.75f)) - (h * 0.1f)

                        val isLast = i == samples.size - 1
                        val isSelected = selectedIndex == i

                        if (isLast) {
                            drawCircle(
                                color = AccentEmerald.copy(alpha = 0.3f),
                                radius = 7.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = AccentEmerald,
                                radius = 4.dp.toPx(),
                                center = Offset(x, y)
                            )
                        } else if (isSelected) {
                            drawCircle(
                                color = AccentRose,
                                radius = 5.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }

        if (samples.size >= 2) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timeFormat.format(Date(samples.first().timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
                if (samples.size >= 4) {
                    val midIndex = samples.size / 2
                    Text(
                        text = timeFormat.format(Date(samples[midIndex].timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                }
                Text(
                    text = timeFormat.format(Date(samples.last().timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "Updated just now"
    val diffSec = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        diffSec < 3 -> "Updated just now"
        diffSec < 60 -> "Updated ${diffSec}s ago"
        else -> "Updated ${diffSec / 60}m ago"
    }
}

@Composable
fun DetailedCategoryCard(
    title: String,
    sizeText: String,
    percentage: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$percentage% of total used space",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
