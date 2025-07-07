package dev.serhiiyaremych.lumina.ui.debug

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager
import dev.serhiiyaremych.lumina.ui.ZoomConstants
import kotlin.math.roundToInt

/**
 * Enhanced debug overlay system with toggle button and comprehensive atlas information
 */
@Composable
fun EnhancedDebugOverlay(
    atlasState: MultiAtlasUpdateResult?,
    isAtlasGenerating: Boolean,
    currentZoom: Float,
    memoryStatus: SmartMemoryManager.MemoryStatus?,
    modifier: Modifier = Modifier
) {
    var isDebugVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Debug toggle button - always visible
        Surface(
            onClick = { isDebugVisible = !isDebugVisible },
            modifier = Modifier
                .zIndex(10f)
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp),
            shape = CircleShape,
            color = if (isDebugVisible) Color(0xFF4CAF50) else Color(0xFF2196F3),
            shadowElevation = 8.dp
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Toggle Debug Info",
                tint = Color.White,
                modifier = Modifier.padding(12.dp)
            )
        }

        // Debug panel - only visible when toggled
        if (isDebugVisible) {
            DebugPanel(
                atlasState = atlasState,
                isAtlasGenerating = isAtlasGenerating,
                currentZoom = currentZoom,
                memoryStatus = memoryStatus,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun DebugPanel(
    atlasState: MultiAtlasUpdateResult?,
    isAtlasGenerating: Boolean,
    currentZoom: Float,
    memoryStatus: SmartMemoryManager.MemoryStatus?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(360.dp),
        shape = RoundedCornerShape(23.dp),
        color = Color.Black.copy(alpha = 0.9f),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "🐛 Debug Panel",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Memory Section
            memoryStatus?.let { memory ->
                MemorySection(memory = memory)
            }

            // Zoom and LOD Section
            ZoomAndLODSection(
                currentZoom = currentZoom,
                isAtlasGenerating = isAtlasGenerating
            )

            // Atlas Information Section
            AtlasInformationSection(
                atlasState = atlasState,
                isAtlasGenerating = isAtlasGenerating
            )
        }
    }
}

@Composable
private fun MemorySection(memory: SmartMemoryManager.MemoryStatus) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📊 Memory Status",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        // Memory progress bar
        val memoryPercent = memory.usagePercent
        val animatedPercent by animateFloatAsState(
            targetValue = memoryPercent,
            animationSpec = tween(durationMillis = 500),
            label = "memory_percent"
        )

        val memoryColor = when {
            memoryPercent < 0.5f -> Color.Green
            memoryPercent < 0.8f -> Color.Yellow
            memoryPercent < 0.95f -> Color(0xFFFF9800) // Orange
            else -> Color.Red
        }

        val animatedColor by animateColorAsState(
            targetValue = memoryColor,
            animationSpec = tween(durationMillis = 300),
            label = "memory_color"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedPercent)
                    .height(20.dp)
                    .background(animatedColor, RoundedCornerShape(10.dp))
            )

            Text(
                text = "${(memoryPercent * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Memory details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Used: ${formatBytes(memory.currentUsageBytes)}",
                color = Color.Cyan,
                fontSize = 9.sp
            )
            Text(
                text = "Free: ${formatBytes(memory.availableBytes)}",
                color = Color.Green,
                fontSize = 9.sp
            )
        }

        Text(
            text = "Pressure: ${memory.pressureLevel} | Atlases: ${memory.registeredAtlases}",
            color = Color.Gray,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun ZoomAndLODSection(
    currentZoom: Float,
    isAtlasGenerating: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "🔍 Zoom & LOD",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        val currentLOD = LODLevel.forZoom(currentZoom)

        // Zoom level indicator using shared constants
        val minZoom = ZoomConstants.MIN_ZOOM
        val maxZoom = ZoomConstants.MAX_ZOOM
        val zoomPercent = ((currentZoom - minZoom) / (maxZoom - minZoom)).coerceIn(0f, 1f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${minZoom}x",
                color = Color.Gray,
                fontSize = 9.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Zoom bar with indicator
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.Center)
                ) {
                    // Background bar
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Zoom indicator line
                    val indicatorX = size.width * zoomPercent
                    drawLine(
                        color = Color.White,
                        start = Offset(indicatorX, 0f),
                        end = Offset(indicatorX, size.height),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${maxZoom.toInt()}x",
                color = Color.Gray,
                fontSize = 9.sp
            )
        }

        // Current values
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Current: ${String.format("%.2f", currentZoom)}x",
                color = Color.White,
                fontSize = 10.sp
            )
            Text(
                text = "LOD: ${currentLOD.name}",
                color = if (isAtlasGenerating) Color.Yellow else Color.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Resolution: ${currentLOD.resolution}px | Range: ${currentLOD.zoomRange.start}-${currentLOD.zoomRange.endInclusive}",
            color = Color.Gray,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun AtlasInformationSection(
    atlasState: MultiAtlasUpdateResult?,
    isAtlasGenerating: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🗺️ Atlas Information",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (isAtlasGenerating) {
                Text(
                    text = "⏳ Generating...",
                    color = Color.Yellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        when (atlasState) {
            is MultiAtlasUpdateResult.Success -> {
                AtlasSuccessView(atlasState = atlasState)
            }
            is MultiAtlasUpdateResult.GenerationFailed -> {
                AtlasErrorView(
                    title = "Generation Failed",
                    message = atlasState.error
                )
            }
            is MultiAtlasUpdateResult.Error -> {
                AtlasErrorView(
                    title = "Atlas Error",
                    message = atlasState.exception.message ?: "Unknown error"
                )
            }
            null -> {
                Text(
                    text = "No atlas data available",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun AtlasSuccessView(atlasState: MultiAtlasUpdateResult.Success) {
    val totalRegions = atlasState.atlases.sumOf { it.regions.size }
    val avgUtilization = if (atlasState.atlases.isNotEmpty()) {
        atlasState.atlases.map { it.utilization }.average().toFloat()
    } else 0f
    val totalMemoryMB = atlasState.atlases.sumOf { atlas ->
        atlas.bitmap.allocationByteCount / (1024 * 1024)
    }
    val validAtlases = atlasState.atlases.filter { !it.bitmap.isRecycled }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Atlases: ${atlasState.atlases.size}",
                color = Color.White,
                fontSize = 10.sp
            )
            Text(
                text = "Photos: $totalRegions",
                color = Color.White,
                fontSize = 10.sp
            )
            Text(
                text = "Memory: ${totalMemoryMB}MB",
                color = Color.White,
                fontSize = 10.sp
            )
        }

        Text(
            text = "Avg Utilization: ${(avgUtilization * 100).roundToInt()}%",
            color = if (avgUtilization > 0.6f) Color.Green else Color.Yellow,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        // Atlas previews
        if (validAtlases.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(validAtlases) { atlas ->
                    AtlasPreviewCard(atlas = atlas)
                }
            }
        }
    }
}

@Composable
private fun AtlasPreviewCard(atlas: dev.serhiiyaremych.lumina.domain.model.TextureAtlas) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Image(
                bitmap = atlas.bitmap.asImageBitmap(),
                contentDescription = "Atlas Preview",
                modifier = Modifier.fillMaxWidth()
            )

            // Photo count badge
            Text(
                text = "${atlas.regions.size}",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(2.dp)
            )

            // Utilization badge
            Text(
                text = "${(atlas.utilization * 100).roundToInt()}%",
                color = if (atlas.utilization > 0.6f) Color.Green else Color.Yellow,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(2.dp)
            )
        }

        // Atlas info
        Text(
            text = "${atlas.size.width}x${atlas.size.height}",
            color = Color.Cyan,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )

        val memoryMB = atlas.bitmap.allocationByteCount / (1024 * 1024)
        Text(
            text = "${memoryMB}MB",
            color = Color.Gray,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AtlasErrorView(title: String, message: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = Color.Red,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            color = Color.Red.copy(alpha = 0.8f),
            fontSize = 9.sp
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
