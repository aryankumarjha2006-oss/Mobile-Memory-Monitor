package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.db.RamTriggerEntity
import com.example.data.model.BatterySnapshot
import com.example.data.model.BatteryStats
import com.example.data.model.CpuSample
import com.example.data.model.CpuSnapshot
import com.example.data.model.CpuStats
import com.example.data.model.LiveMemoryPoint
import com.example.data.model.MemoryStats
import com.example.data.model.StorageScanState
import com.example.data.model.StorageStats
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.QuickActionCard
import com.example.ui.components.StorageProgressRing
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonPurple
import com.example.util.MemoryFormatter
import kotlinx.coroutines.android.awaitFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    memoryStats: MemoryStats,
    storageStats: StorageStats,
    batteryStats: BatteryStats,
    cpuStats: CpuStats = CpuStats(),
    cpuSnapshot: CpuSnapshot = CpuSnapshot(),
    batterySnapshot: BatterySnapshot = BatterySnapshot(),
    liveMemorySamples: List<LiveMemoryPoint> = emptyList(),
    liveCpuSamples: List<CpuSample> = emptyList(),
    cleanableBytes: Long = 0L,
    storageScanState: StorageScanState = StorageScanState.Idle,
    lastScanTimestamp: Long? = null,
    isBoosting: Boolean,
    lastFreedMb: Long?,
    isRooted: Boolean = false,
    isRamMonitorRunning: Boolean = false,
    latestTriggerEvent: RamTriggerEntity? = null,
    onToggleRamMonitor: (Boolean) -> Unit = {},
    onOptimizeClick: () -> Unit,
    onScanStorage: () -> Unit = {},
    onStartLiveMonitoring: () -> Unit = {},
    onStopLiveMonitoring: () -> Unit = {},
    onNavigateTab: (Int) -> Unit,
    onOpenLargeFiles: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }

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

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            onToggleRamMonitor(true)
        }
    }

    // App Rendering FPS Measurement
    var currentFps by remember { mutableIntStateOf(60) }
    LaunchedEffect(Unit) {
        var lastFrameTime = System.nanoTime()
        var frameCount = 0
        while (true) {
            awaitFrame()
            frameCount++
            val now = System.nanoTime()
            val diff = now - lastFrameTime
            if (diff >= 1_000_000_000L) {
                currentFps = ((frameCount * 1_000_000_000L) / diff).toInt().coerceIn(1, 120)
                frameCount = 0
                lastFrameTime = now
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Memory Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "System & RAM Monitor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { onNavigateTab(4) },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ================= HERO REAL RAM / MEMORY MONITOR SECTION =================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "RAM Monitor",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Memory Used",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = AccentEmerald.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                                    text = "Live • " + formatRelativeTime(liveMemorySamples.lastOrNull()?.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular RAM usage progress ring
                    StorageProgressRing(
                        usedPercent = memoryStats.usedPercent,
                        usedGbFormatted = MemoryFormatter.formatMb(memoryStats.usedRamMb),
                        totalGbFormatted = MemoryFormatter.formatMb(memoryStats.totalRamMb),
                        size = 190.dp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Sub-metrics row: Used RAM | Available RAM | Total RAM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HeroSubMetric(
                            label = "Used",
                            value = MemoryFormatter.formatMb(memoryStats.usedRamMb),
                            dotColor = ElectricCyan
                        )
                        HeroSubMetric(
                            label = "Available",
                            value = MemoryFormatter.formatMb(memoryStats.freeRamMb),
                            dotColor = AccentEmerald
                        )
                        HeroSubMetric(
                            label = "Total",
                            value = MemoryFormatter.formatMb(memoryStats.totalRamMb),
                            dotColor = NeonPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Live RAM Usage Graph
                    LiveMemoryGraph(
                        samples = liveMemorySamples,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // RAM Optimization Boost Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lastFreedMb != null) "Freed $lastFreedMb MB RAM!" else "Optimize active background RAM",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lastFreedMb != null) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = onOptimizeClick,
                            enabled = !isBoosting && !isRooted,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("boost_now_button")
                        ) {
                            if (isBoosting) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Boosting...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Boost RAM",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Boost RAM", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ================= SMART RAM MONITOR (60% THRESHOLD ALERT) CARD =================
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Smart RAM Monitor",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart RAM Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val (statusLabel, statusColor) = when {
                        !hasNotificationPermission -> "Permission Required" to AccentAmber
                        isRamMonitorRunning -> "Monitoring" to AccentEmerald
                        else -> "Stopped" to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // RAM Telemetry Summary Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current: ${memoryStats.usedPercent}% (Threshold: 60%)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (memoryStats.usedPercent >= 60) AccentRose else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Used: ${MemoryFormatter.formatMb(memoryStats.usedRamMb)} • Free: ${MemoryFormatter.formatMb(memoryStats.freeRamMb)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (memoryStats.usedPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (memoryStats.usedPercent >= 60) AccentRose else ElectricCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // MANDATORY RESULT CARD — Display Candidate App & Action Outcome
                if (latestTriggerEvent != null) {
                    val event = latestTriggerEvent
                    val (badgeText, badgeColor) = when (event.outcomeName) {
                        "FORCE_STOP_CONFIRMED" -> "Force-stop confirmed" to AccentEmerald
                        "MANUAL_FORCE_STOP_REQUIRED" -> "Manual force-stop required" to AccentAmber
                        else -> "Unable to stop app" to AccentRose
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Candidate App Result",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = badgeColor.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${event.appName} (${event.packageName})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "RAM at Trigger: ${event.usedPercent}% (Threshold: ${event.thresholdPercent}%) • ${timeFormat.format(Date(event.timestamp))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = event.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (event.outcomeName == "MANUAL_FORCE_STOP_REQUIRED" && event.packageName.isNotEmpty() && !event.packageName.startsWith("system.")) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${event.packageName}")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open App Info",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open App Info", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onNavigateTab(3) }, // Opens App Manager
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Manage Apps",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manage Apps")
                    }

                    Button(
                        onClick = {
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onToggleRamMonitor(!isRamMonitorRunning)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRamMonitorRunning) AccentRose else ElectricCyan
                        ),
                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = if (isRamMonitorRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isRamMonitorRunning) "Stop Monitoring" else "Start Monitoring",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRamMonitorRunning) "Stop Monitoring" else "Start 60% Monitor",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ================= CPU MONITOR CARD =================
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = "CPU Monitor",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cpuSnapshot.cpuSource.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricCyan.copy(alpha = 0.15f)
                    ) {
                        val cpuLabel = if (cpuSnapshot.isAvailable && cpuSnapshot.totalUsagePercent >= 0) {
                            "${cpuSnapshot.totalUsagePercent}% ${cpuSnapshot.cpuSource.badgeLabel}"
                        } else {
                            "Unavailable"
                        }
                        Text(
                            text = cpuLabel,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CPU Gauge Arc
                    val cpuUsageVal = if (cpuSnapshot.isAvailable && cpuSnapshot.totalUsagePercent >= 0) cpuSnapshot.totalUsagePercent else 0
                    GaugeArc(
                        percentage = cpuUsageVal,
                        label = if (cpuSnapshot.cpuSource == com.example.data.model.CpuSource.APP) "App Load" else "CPU Load",
                        size = 110.dp,
                        accentColor = ElectricCyan
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // CPU Temperature Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = "CPU Temperature",
                                tint = AccentAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val tempText = if (cpuSnapshot.temperatureCelsius > 0f) "${cpuSnapshot.temperatureCelsius}°C" else "CPU temp unavailable"
                            Text(
                                text = "Temperature: $tempText",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Cores Row
                        Text(
                            text = "Cores: ${cpuSnapshot.coresCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (cpuSnapshot.cpuSource == com.example.data.model.CpuSource.APP) {
                            Text(
                                text = "System CPU access is restricted on this device. Showing real process CPU usage for this app.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live CPU Curve
                Text(
                    text = if (cpuSnapshot.cpuSource == com.example.data.model.CpuSource.APP) "Live App CPU Usage Curve" else "Live CPU Usage Curve",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                LiveCpuGraph(
                    samples = liveCpuSamples,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )

                if (cpuSnapshot.perCoreUsage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Multi-Core Utilization",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cpuSnapshot.perCoreUsage.forEachIndexed { idx, usage ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Core $idx",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(50.dp)
                                )
                                LinearProgressIndicator(
                                    progress = { (usage / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = ElectricCyan,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$usage%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= BATTERY MONITOR CARD =================
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery Monitor",
                            tint = AccentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Battery Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentEmerald.copy(alpha = 0.15f)
                    ) {
                        val statusText = if (batterySnapshot.isCharging) "Charging (${batterySnapshot.plugSource})" else batterySnapshot.statusLabel
                        Text(
                            text = statusText,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val battPct = if (batterySnapshot.percentage >= 0) batterySnapshot.percentage else batteryStats.percentage
                    GaugeArc(
                        percentage = battPct,
                        label = "Battery",
                        size = 110.dp,
                        accentColor = AccentEmerald
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = "Battery Temperature",
                                tint = AccentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val battTemp = if (batterySnapshot.temperatureCelsius > 0f) "${batterySnapshot.temperatureCelsius}°C" else "${batteryStats.temperatureCelsius}°C"
                            Text(
                                text = "Battery Temperature: $battTemp",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val voltage = if (batterySnapshot.voltageVolts > 0f) "${batterySnapshot.voltageVolts} V" else "${batteryStats.voltageVolts} V"
                        Text(
                            text = "Voltage: $voltage • ${batterySnapshot.technology}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Health: ${batterySnapshot.healthStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ================= FPS MONITOR & STORAGE OVERVIEW ROW =================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // FPS Monitor Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "FPS",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FPS Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "$currentFps FPS",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElectricCyan
                    )

                    Text(
                        text = "App Rendering FPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Storage Overview Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SdCard,
                            contentDescription = "Storage",
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "${storageStats.usedPercent}%",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentEmerald
                    )

                    Text(
                        text = "${MemoryFormatter.formatBytes(storageStats.usedBytes)} used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ================= SYSTEM HEALTH & STATUS CARD =================
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "System Health",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "System Health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val (healthLabel, healthColor) = when {
                        memoryStats.isLowMemory || memoryStats.usedPercent > 95 -> "Critical" to AccentRose
                        memoryStats.usedPercent > 85 -> "Warning" to AccentAmber
                        memoryStats.usedPercent > 70 -> "Moderate" to ElectricCyan
                        else -> "Good" to AccentEmerald
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = healthColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = healthLabel,
                            color = healthColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val healthDescription = when {
                    memoryStats.isLowMemory -> "Android reports low available memory. Boost RAM or close background apps."
                    memoryStats.usedPercent > 85 -> "RAM usage is currently high. Consider optimizing memory."
                    else -> "RAM and system memory usage are currently stable."
                }

                Text(
                    text = healthDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ================= SMART CLEANUP CARD =================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.padding(18.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ElectricCyan.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CleaningServices,
                                        contentDescription = "Smart Cleanup",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Smart Cleanup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (cleanableBytes > 0) "${MemoryFormatter.formatBytes(cleanableBytes)} can be freed" else "Storage clean ✨",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    PrimaryActionButton(
                        text = "Scan & Clean",
                        onClick = { onNavigateTab(2) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("boost_now_button"),
                        icon = Icons.Default.AutoAwesome
                    )
                }
            }
        }

        // ================= QUICK ACTIONS GRID =================
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Clean Junk",
                icon = Icons.Default.CleaningServices,
                accentColor = ElectricCyan,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTab(2) }
            )
            QuickActionCard(
                title = "Large Files",
                icon = Icons.Default.Folder,
                accentColor = NeonPurple,
                modifier = Modifier.weight(1f),
                onClick = onOpenLargeFiles
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Duplicate Files",
                icon = Icons.Default.Search,
                accentColor = AccentEmerald,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTab(2) }
            )
            QuickActionCard(
                title = "App Manager",
                icon = Icons.Default.GridView,
                accentColor = AccentRose,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateTab(3) }
            )
        }
    }
}

@Composable
fun HeroSubMetric(
    label: String,
    value: String,
    dotColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun GaugeArc(
    percentage: Int,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 110.dp,
    strokeWidth: Dp = 12.dp,
    accentColor: Color = ElectricCyan
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percentage.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "gaugeArcAnimation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        val trackColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = strokeWidth.toPx()

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidthPx)
            )

            val sweep = (animatedPercent / 100f) * 270f
            drawArc(
                color = accentColor,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidthPx)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${animatedPercent.toInt()}%",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LiveCpuGraph(
    samples: List<CpuSample>,
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
                    text = "Starting live CPU monitor…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

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
                        text = "CPU Load: ${selectedPoint.usagePercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
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
                    val pt = samples[0]
                    val y = h - ((pt.usagePercent / 100f) * h)
                    drawLine(
                        color = primaryColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = ElectricCyan.copy(alpha = 0.3f),
                        radius = 7.dp.toPx(),
                        center = Offset(w / 2f, y)
                    )
                    drawCircle(
                        color = ElectricCyan,
                        radius = 4.dp.toPx(),
                        center = Offset(w / 2f, y)
                    )
                } else {
                    val path = Path()
                    val fillPath = Path()

                    samples.forEachIndexed { i, pt ->
                        val x = i * dx
                        val y = h - ((pt.usagePercent / 100f) * h)

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
                        val y = h - ((pt.usagePercent / 100f) * h)

                        val isLast = i == samples.size - 1
                        val isSelected = selectedIndex == i

                        if (isLast) {
                            drawCircle(
                                color = ElectricCyan.copy(alpha = 0.3f),
                                radius = 7.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = ElectricCyan,
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

@Composable
fun LiveMemoryGraph(
    samples: List<LiveMemoryPoint>,
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
                    text = "Starting live RAM monitor…",
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
                        text = "Avail: ${MemoryFormatter.formatBytes(selectedPoint.availBytes)}",
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
