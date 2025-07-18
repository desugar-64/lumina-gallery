package dev.serhiiyaremych.lumina.ui.debug

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager
import kotlin.math.roundToInt

/**
 * Debug overlay font size constants for centralized control
 */
private object DebugTextSizes {
    val TOGGLE_BUTTON = 16.sp          // Debug toggle button (×/◐)
    val LARGE_ICON = 14.sp             // Large icons (🔍, 📱)
    val PRIMARY_TEXT = 13.sp           // Primary values (zoom level)
    val SECONDARY_TEXT = 12.sp         // Secondary text (LOD level, icons, tier labels)
    val TERTIARY_TEXT = 11.sp          // Small labels (strategy name, memory %, atlas sizes)
    val DETAIL_TEXT = 10.sp            // Detail info (atlas count, photo count, dimensions)
    val MICRO_TEXT = 9.sp              // Very small text (compact atlas photo count, utilization)
}

/**
 * Lightweight, semi-transparent debug overlay focused on multi-LOD atlas visualization
 */
@Composable
fun EnhancedDebugOverlay(
    atlasState: MultiAtlasUpdateResult?,
    isAtlasGenerating: Boolean,
    currentZoom: Float,
    memoryStatus: SmartMemoryManager.MemoryStatus?,
    smartMemoryManager: SmartMemoryManager? = null,
    deviceCapabilities: dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities? = null,
    modifier: Modifier = Modifier
) {
    var isDebugVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Compact debug toggle - smaller and less intrusive
        Surface(
            onClick = { isDebugVisible = !isDebugVisible },
            modifier = Modifier
                .zIndex(10f)
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(8.dp)
                .size(32.dp),
            shape = CircleShape,
            color = if (isDebugVisible) Color(0x88FF6B35) else Color(0x664FC3F7),
            shadowElevation = 4.dp
        ) {
            Text(
                text = if (isDebugVisible) "×" else "◐",
                color = Color.White,
                fontSize = DebugTextSizes.TOGGLE_BUTTON,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }

        // Lightweight debug info - always visible when toggled, click-through for most areas
        if (isDebugVisible) {
            CompactDebugInfo(
                atlasState = atlasState,
                isAtlasGenerating = isAtlasGenerating,
                currentZoom = currentZoom,
                memoryStatus = memoryStatus,
                smartMemoryManager = smartMemoryManager,
                deviceCapabilities = deviceCapabilities,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun CompactDebugInfo(
    atlasState: MultiAtlasUpdateResult?,
    isAtlasGenerating: Boolean,
    currentZoom: Float,
    memoryStatus: SmartMemoryManager.MemoryStatus?,
    smartMemoryManager: SmartMemoryManager?,
    deviceCapabilities: dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Compact zoom and expected LOD info
        CompactZoomInfo(currentZoom, isAtlasGenerating)

        // Device capabilities info
        deviceCapabilities?.let { CompactDeviceInfo(it) }

        // Multi-LOD atlas visualization - main focus
        MultiLODAtlasView(atlasState)

        // Enhanced memory management display
        memoryStatus?.let { 
            EnhancedMemoryDisplay(it, smartMemoryManager)
        }
    }
}

@Composable
private fun CompactZoomInfo(currentZoom: Float, isAtlasGenerating: Boolean) {
    val expectedLOD = LODLevel.forZoom(currentZoom)

    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Zoom icon and value
        Text(
            text = "🔍",
            fontSize = DebugTextSizes.LARGE_ICON
        )

        Text(
            text = "${String.format(Locale.US, "%.1f", currentZoom)}×",
            color = Color.White,
            fontSize = DebugTextSizes.PRIMARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        // Arrow separator
        Text(
            text = "→",
            color = Color.Gray,
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        // LOD level with icon
        Text(
            text = "📷",
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        Text(
            text = expectedLOD.name.replace("LEVEL_", "L"),
            color = if (isAtlasGenerating) Color.Yellow else Color.Cyan,
            fontSize = DebugTextSizes.SECONDARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        if (isAtlasGenerating) {
            AnimatedGeneratingIndicator()
        }
    }
}

@Composable
private fun MultiLODAtlasView(atlasState: MultiAtlasUpdateResult?) {
    when (atlasState) {
        is MultiAtlasUpdateResult.Success -> {
            val validAtlases = atlasState.atlases.filter { !it.bitmap.isRecycled }
            val lodGroups = validAtlases.groupBy { LODLevel.fromLevel(it.lodLevel) }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Strategy info with priority distribution
                AtlasStrategyIndicator(atlasState)

                // LOD groups - main focus
                lodGroups.entries.sortedBy { it.key?.level ?: -1 }.forEach { (lod, atlases) ->
                    LODGroupView(lod, atlases)
                }
            }
        }

        is MultiAtlasUpdateResult.GenerationFailed -> {
            CompactErrorView("Gen Failed: ${atlasState.error.take(30)}...")
        }

        is MultiAtlasUpdateResult.Error -> {
            CompactErrorView("Error: ${atlasState.exception.message?.take(30) ?: "Unknown"}...")
        }

        null -> {
            Text(
                text = "No atlas data",
                color = Color.Gray.copy(alpha = 0.7f),
                fontSize = DebugTextSizes.TERTIARY_TEXT,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun AtlasStrategyIndicator(atlasState: MultiAtlasUpdateResult.Success) {
    // Detect strategy from atlas distribution
    val strategy = when {
        atlasState.atlases.size == 1 -> "SINGLE"
        atlasState.atlases.groupBy { LODLevel.fromLevel(it.lodLevel) }.size > 1 -> "PRIORITY"
        else -> "MULTI"
    }

    val strategyColor = when (strategy) {
        "PRIORITY" -> Color(0xFF4CAF50) // Green for priority-based
        "MULTI" -> Color(0xFF2196F3)   // Blue for multi-size
        else -> Color(0xFFFF9800)      // Orange for single
    }

    val strategyIcon = when (strategy) {
        "PRIORITY" -> "⭐"  // Star for priority
        "MULTI" -> "🔄"    // Recycle for multi
        else -> "📄"       // Single page for single
    }

    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = strategyIcon,
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        Text(
            text = strategy,
            color = strategyColor,
            fontSize = DebugTextSizes.TERTIARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "📋",
            fontSize = DebugTextSizes.DETAIL_TEXT
        )

        Text(
            text = "${atlasState.atlases.size} atlas${if (atlasState.atlases.size != 1) "es" else ""}",
            color = Color.White,
            fontSize = DebugTextSizes.DETAIL_TEXT,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "📸",
            fontSize = DebugTextSizes.DETAIL_TEXT
        )

        Text(
            text = "${atlasState.atlases.sumOf { it.regions.size }} photos",
            color = Color.White,
            fontSize = DebugTextSizes.DETAIL_TEXT,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LODGroupView(lod: LODLevel?, atlases: List<dev.serhiiyaremych.lumina.domain.model.TextureAtlas>) {
    var isExpanded by remember { mutableStateOf(false) }

    val lodColor = when (lod?.level) {
        0, 1 -> Color(0xFF9E9E9E) // Gray for low LOD
        2, 3 -> Color(0xFF2196F3) // Blue for medium LOD
        4, 5 -> Color(0xFFFF9800) // Orange for high LOD
        6, 7 -> Color(0xFFF44336) // Red for very high LOD
        else -> Color.White
    }

    val lodIcon = when (lod?.level) {
        0, 1 -> "🔽"  // Low quality
        2, 3 -> "📷"  // Medium quality
        4, 5 -> "📸"  // High quality
        6, 7 -> "🎯"  // Very high/precise quality
        else -> "❓"
    }

    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Clickable header row
        Row(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // LOD icon
            Text(
                text = lodIcon,
                fontSize = DebugTextSizes.SECONDARY_TEXT
            )

            // LOD level indicator
            Text(
                text = lod?.name?.replace("LEVEL_", "L") ?: "L?",
                color = Color.White,
                fontSize = DebugTextSizes.SECONDARY_TEXT,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(lodColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )

            // Expand/collapse indicator
            Text(
                text = if (isExpanded) "🔼" else "🔽",
                fontSize = DebugTextSizes.DETAIL_TEXT,
                color = Color.Gray
            )

            // Atlas count
            Text(
                text = "${atlases.size} atlas${if (atlases.size != 1) "es" else ""}",
                color = Color.White,
                fontSize = DebugTextSizes.DETAIL_TEXT
            )
        }

        // Atlas display - compact or expanded
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(atlases) { atlas ->
                if (isExpanded) {
                    ExpandedAtlasCard(atlas, lodColor)
                } else {
                    CompactAtlasCard(atlas, lodColor)
                }
            }
        }
    }
}

@Composable
private fun CompactAtlasCard(atlas: dev.serhiiyaremych.lumina.domain.model.TextureAtlas, lodColor: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Gray.copy(alpha = 0.3f))
    ) {
        Image(
            bitmap = atlas.bitmap.asImageBitmap(),
            contentDescription = "Atlas",
            modifier = Modifier.size(28.dp)
        )

        // Photo count
        Text(
            text = "${atlas.regions.size}",
            color = Color.White,
            fontSize = DebugTextSizes.MICRO_TEXT,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(lodColor.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                .padding(1.dp)
        )

        // Utilization indicator as colored border
        val utilizationColor =
            if (atlas.utilization > 0.7f) Color.Green else if (atlas.utilization > 0.4f) Color.Yellow else Color.Red
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color.Transparent)
                .clip(RoundedCornerShape(4.dp))
                .background(utilizationColor.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun ExpandedAtlasCard(atlas: dev.serhiiyaremych.lumina.domain.model.TextureAtlas, lodColor: Color) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Atlas bitmap at 3x size (84dp instead of 28dp)
        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Image(
                bitmap = atlas.bitmap.asImageBitmap(),
                contentDescription = "Expanded Atlas",
                modifier = Modifier.matchParentSize()
            )

            // Photo count in top-right corner
            Text(
                text = "${atlas.regions.size}",
                color = Color.White,
                fontSize = DebugTextSizes.TERTIARY_TEXT,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(lodColor.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )

            // Utilization indicator in bottom-left corner
            val utilizationColor =
                if (atlas.utilization > 0.7f) Color.Green else if (atlas.utilization > 0.4f) Color.Yellow else Color.Red
            Text(
                text = "${(atlas.utilization * 100).roundToInt()}%",
                color = Color.White,
                fontSize = DebugTextSizes.MICRO_TEXT,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(utilizationColor.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }

        // Atlas details
        Column {
            Text(
                text = "${atlas.size.width}×${atlas.size.height}",
                color = Color.White,
                fontSize = DebugTextSizes.DETAIL_TEXT,
                fontWeight = FontWeight.Bold
            )

            val lodLevel = LODLevel.fromLevel(atlas.lodLevel) ?: LODLevel.LEVEL_0
            Text(
                text = "LOD ${lodLevel.level}, ${lodLevel.resolution}x${lodLevel.resolution}px",
                color = lodColor,
                fontSize = DebugTextSizes.MICRO_TEXT,
                fontWeight = FontWeight.Bold
            )
            
            // Show bitmap config
            val configText = when (atlas.bitmap.config) {
                Bitmap.Config.RGB_565 -> "RGB565"
                Bitmap.Config.ARGB_8888 -> "ARGB8888"
                Bitmap.Config.ARGB_4444 -> "ARGB4444"
                Bitmap.Config.ALPHA_8 -> "ALPHA8"
                else -> "UNKNOWN"
            }
            
            val configColor = when (atlas.bitmap.config) {
                Bitmap.Config.RGB_565 -> Color(0xFF4CAF50) // Green for optimized
                Bitmap.Config.ARGB_8888 -> Color(0xFF2196F3) // Blue for full quality
                else -> Color.Gray
            }
            
            Text(
                text = "📊 $configText",
                color = configColor,
                fontSize = DebugTextSizes.MICRO_TEXT,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EnhancedMemoryDisplay(
    memory: SmartMemoryManager.MemoryStatus,
    smartMemoryManager: SmartMemoryManager?
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Primary memory indicator (always visible)
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Memory pressure icon
            val pressureIcon = when (memory.pressureLevel) {
                SmartMemoryManager.MemoryPressure.NORMAL -> "💚"
                SmartMemoryManager.MemoryPressure.LOW -> "💛"
                SmartMemoryManager.MemoryPressure.MEDIUM -> "🧡"
                SmartMemoryManager.MemoryPressure.HIGH -> "❤️"
                SmartMemoryManager.MemoryPressure.CRITICAL -> "🔴"
            }

            Text(
                text = pressureIcon,
                fontSize = DebugTextSizes.SECONDARY_TEXT
            )

            // Memory pressure level
            val pressureColor = when (memory.pressureLevel) {
                SmartMemoryManager.MemoryPressure.NORMAL -> Color(0xFF4CAF50)
                SmartMemoryManager.MemoryPressure.LOW -> Color(0xFFFFC107)
                SmartMemoryManager.MemoryPressure.MEDIUM -> Color(0xFFFF9800)
                SmartMemoryManager.MemoryPressure.HIGH -> Color(0xFFFF5722)
                SmartMemoryManager.MemoryPressure.CRITICAL -> Color(0xFFF44336)
            }

            Text(
                text = memory.pressureLevel.name.take(1), // N/L/M/H/C
                color = Color.White,
                fontSize = DebugTextSizes.SECONDARY_TEXT,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(pressureColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )

            // Memory usage bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(memory.usagePercent)
                        .height(4.dp)
                        .background(pressureColor, RoundedCornerShape(2.dp))
                )
            }

            Text(
                text = "${(memory.usagePercent * 100).roundToInt()}%",
                color = Color.White,
                fontSize = DebugTextSizes.TERTIARY_TEXT,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = formatBytes(memory.currentUsageBytes),
                color = Color.White,
                fontSize = DebugTextSizes.DETAIL_TEXT,
                fontWeight = FontWeight.Bold
            )

            // Expand/collapse indicator
            Text(
                text = if (isExpanded) "🔼" else "🔽",
                fontSize = DebugTextSizes.DETAIL_TEXT,
                color = Color.Gray
            )
        }

        // Expanded memory details
        if (isExpanded && smartMemoryManager != null) {
            SystemMemoryPressureIndicator(smartMemoryManager)
            MemoryLeakDetectionStatus(smartMemoryManager)
            MemoryBudgetInfo(memory, smartMemoryManager)
        }
    }
}

@Composable
private fun SystemMemoryPressureIndicator(smartMemoryManager: SmartMemoryManager) {
    // Note: We would need to expose system memory pressure from SmartMemoryManager
    // For now, showing atlas count and background app estimate
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "🌐",
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        Text(
            text = "SYS",
            color = Color.Cyan,
            fontSize = DebugTextSizes.TERTIARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        // System pressure would be calculated here if exposed
        Text(
            text = "~50%",
            color = Color.Yellow,
            fontSize = DebugTextSizes.TERTIARY_TEXT
        )

        Text(
            text = "📱",
            fontSize = DebugTextSizes.DETAIL_TEXT
        )

        Text(
            text = "~5 bg apps",
            color = Color.White,
            fontSize = DebugTextSizes.DETAIL_TEXT
        )
    }
}

@Composable
private fun MemoryLeakDetectionStatus(smartMemoryManager: SmartMemoryManager) {
    // Try to get leak detection result
    val leakReport = remember { smartMemoryManager.detectMemoryLeak() }
    
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val leakIcon = if (leakReport?.detected == true) {
            when {
                leakReport.severity > 0.8f -> "🆘"  // Critical leak
                leakReport.severity > 0.6f -> "⚠️"  // High leak
                leakReport.severity > 0.4f -> "🟡"  // Medium leak
                else -> "🟢"  // Low leak
            }
        } else "✅"  // No leak detected

        Text(
            text = leakIcon,
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        Text(
            text = "LEAK",
            color = if (leakReport?.detected == true) Color.Red else Color.Green,
            fontSize = DebugTextSizes.TERTIARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        if (leakReport?.detected == true) {
            Text(
                text = "${(leakReport.severity * 100).roundToInt()}%",
                color = Color.Red,
                fontSize = DebugTextSizes.TERTIARY_TEXT,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = "OK",
                color = Color.Green,
                fontSize = DebugTextSizes.TERTIARY_TEXT,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MemoryBudgetInfo(
    memory: SmartMemoryManager.MemoryStatus,
    smartMemoryManager: SmartMemoryManager
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "💰",
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        Text(
            text = "BUDGET",
            color = Color.Cyan,
            fontSize = DebugTextSizes.TERTIARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = formatBytes(memory.totalBudgetBytes),
            color = Color.White,
            fontSize = DebugTextSizes.TERTIARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "📊",
            fontSize = DebugTextSizes.DETAIL_TEXT
        )

        Text(
            text = "${memory.registeredAtlases} atlases",
            color = Color.White,
            fontSize = DebugTextSizes.DETAIL_TEXT
        )

        // Show tier info
        val tierIcon = when (memory.deviceCapabilities.memoryTier.name) {
            "HIGH" -> "🏆"
            "MEDIUM" -> "⚡"
            "MINIMAL" -> "🔋"
            else -> "❓"
        }

        Text(
            text = tierIcon,
            fontSize = DebugTextSizes.DETAIL_TEXT
        )

        Text(
            text = memory.deviceCapabilities.memoryTier.name.take(1),
            color = when (memory.deviceCapabilities.memoryTier.name) {
                "HIGH" -> Color.Green
                "MEDIUM" -> Color.Yellow
                "MINIMAL" -> Color.Red
                else -> Color.Gray
            },
            fontSize = DebugTextSizes.DETAIL_TEXT,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactErrorView(message: String) {
    Text(
        text = message,
        color = Color.Red,
        fontSize = DebugTextSizes.TERTIARY_TEXT,
        modifier = Modifier
            .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(4.dp)
    )
}


@Composable
private fun CompactDeviceInfo(deviceCapabilities: dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities) {
    val capabilities = deviceCapabilities.getCapabilities()
    val recommendedSizes = deviceCapabilities.getRecommendedAtlasSizes()
    
    // Atlas optimization config for debugging
    val optimizationConfig = dev.serhiiyaremych.lumina.domain.model.AtlasOptimizationConfig.default()

    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Device icon
        Text(
            text = "📱",
            fontSize = DebugTextSizes.LARGE_ICON
        )

        // Performance tier indicator with icon
        val tierColor = when (capabilities.performanceTier) {
            dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities.PerformanceTier.HIGH -> Color(0xFF4CAF50) // Green
            dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities.PerformanceTier.MEDIUM -> Color(0xFFFF9800) // Orange
            dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities.PerformanceTier.LOW -> Color(0xFFF44336) // Red
        }

        val tierIcon = when (capabilities.performanceTier) {
            dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities.PerformanceTier.HIGH -> "🚀"
            dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities.PerformanceTier.MEDIUM -> "⚡"
            dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities.PerformanceTier.LOW -> "🐌"
        }

        Text(
            text = tierIcon,
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        Text(
            text = capabilities.performanceTier.name.take(1), // H/M/L
            color = Color.White,
            fontSize = DebugTextSizes.SECONDARY_TEXT,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(tierColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp)
        )

        // Memory budget with icon
        Text(
            text = "💾",
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        Text(
            text = "${capabilities.memoryBudgetMB}MB",
            color = Color.White,
            fontSize = DebugTextSizes.SECONDARY_TEXT,
            fontWeight = FontWeight.Bold
        )

        // Atlas sizes with icon
        Text(
            text = "🗂️",
            fontSize = DebugTextSizes.SECONDARY_TEXT
        )

        val atlasSizesText = recommendedSizes.map { "${it.width / 1024}K" }.joinToString("/")
        Text(
            text = atlasSizesText,
            color = Color.Cyan,
            fontSize = DebugTextSizes.TERTIARY_TEXT,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnimatedGeneratingIndicator() {
    CircularProgressIndicator(Modifier.size(12.dp), color = Color.White)
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
