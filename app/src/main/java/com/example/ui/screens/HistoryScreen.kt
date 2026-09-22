package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MemoryRecord
import com.example.ui.theme.TechCyanLight
import com.example.ui.theme.TechEmerald
import com.example.ui.theme.TechRose
import com.example.util.MemoryFormatter

enum class HistoryTimeRange {
    ONE_MIN,
    FIVE_MINS,
    ONE_HOUR,
    ALL
}

@Composable
fun HistoryScreen(
    historyRecords: List<MemoryRecord>,
    onClearHistory: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedRange by remember { mutableStateOf(HistoryTimeRange.FIVE_MINS) }

    val filteredRecords = remember(historyRecords, selectedRange) {
        val now = System.currentTimeMillis()
        when (selectedRange) {
            HistoryTimeRange.ONE_MIN -> historyRecords.filter { now - it.timestamp <= 60_000 }
            HistoryTimeRange.FIVE_MINS -> historyRecords.filter { now - it.timestamp <= 300_000 }
            HistoryTimeRange.ONE_HOUR -> historyRecords.filter { now - it.timestamp <= 3600_000 }
            HistoryTimeRange.ALL -> historyRecords
        }.sortedBy { it.timestamp }
    }

    var selectedRecord by remember { mutableStateOf<MemoryRecord?>(null) }

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
            Column {
                Text(
                    text = "Room Telemetry Database",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Memory History Chart",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onClearHistory,
                modifier = Modifier.testTag("clear_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear History",
                    tint = TechRose
                )
            }
        }

        // Filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedRange == HistoryTimeRange.ONE_MIN,
                onClick = { selectedRange = HistoryTimeRange.ONE_MIN },
                label = { Text("1 Min") }
            )
            FilterChip(
                selected = selectedRange == HistoryTimeRange.FIVE_MINS,
                onClick = { selectedRange = HistoryTimeRange.FIVE_MINS },
                label = { Text("5 Mins") }
            )
            FilterChip(
                selected = selectedRange == HistoryTimeRange.ONE_HOUR,
                onClick = { selectedRange = HistoryTimeRange.ONE_HOUR },
                label = { Text("1 Hour") }
            )
            FilterChip(
                selected = selectedRange == HistoryTimeRange.ALL,
                onClick = { selectedRange = HistoryTimeRange.ALL },
                label = { Text("All Logged") }
            )
        }

        // Graph Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Graph",
                            tint = TechCyanLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RAM Usage % over Time",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${filteredRecords.size} records",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Logging telemetry to database... Check back in a few seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val avgPercent = filteredRecords.map { it.usedRamPercent }.average().toInt()
                    val maxPercent = filteredRecords.maxOf { it.usedRamPercent }
                    val minPercent = filteredRecords.minOf { it.usedRamPercent }

                    // Interactive Line Graph Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                        val primaryColor = TechCyanLight

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(filteredRecords) {
                                    detectTapGestures { offset ->
                                        val width = size.width
                                        val step = width / (filteredRecords.size - 1).coerceAtLeast(1)
                                        val index = (offset.x / step).toInt().coerceIn(0, filteredRecords.size - 1)
                                        selectedRecord = filteredRecords[index]
                                    }
                                }
                        ) {
                            val w = size.width
                            val h = size.height

                            // Draw Horizontal Percentage Gridlines (0%, 25%, 50%, 75%, 100%)
                            for (p in listOf(0, 25, 50, 75, 100)) {
                                val y = h - (h * (p / 100f))
                                drawLine(
                                    color = surfaceVariant,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // Draw Path
                            val path = Path()
                            val dx = w / (filteredRecords.size - 1).coerceAtLeast(1)

                            filteredRecords.forEachIndexed { i, record ->
                                val x = i * dx
                                val y = h - (h * (record.usedRamPercent / 100f))
                                if (i == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }

                            drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(width = 3.dp.toPx())
                            )

                            // Draw Point markers
                            filteredRecords.forEachIndexed { i, record ->
                                val x = i * dx
                                val y = h - (h * (record.usedRamPercent / 100f))
                                drawCircle(
                                    color = primaryColor,
                                    radius = 3.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Summary Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        GraphStatBox("Min Usage", "$minPercent%", TechEmerald)
                        GraphStatBox("Average", "$avgPercent%", TechCyanLight)
                        GraphStatBox("Peak Usage", "$maxPercent%", TechRose)
                    }

                    selectedRecord?.let { record ->
                        Surface(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Selected Point (${MemoryFormatter.formatTime(record.timestamp)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "RAM Used: ${record.usedRamPercent}% (${MemoryFormatter.formatMb(record.usedRamMb)} / ${MemoryFormatter.formatMb(record.totalRamMb)})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GraphStatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
