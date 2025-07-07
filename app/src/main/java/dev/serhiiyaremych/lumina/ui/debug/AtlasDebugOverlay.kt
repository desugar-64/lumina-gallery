package dev.serhiiyaremych.lumina.ui.debug

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import kotlin.math.roundToInt

/**
 * Debug overlay that displays atlas state and generation status.
 * Shows atlas bitmap, status messages, and LOD level information.
 */
@Composable
fun AtlasDebugOverlay(
    atlasState: MultiAtlasUpdateResult?,
    modifier: Modifier = Modifier
) {
    // Debug: Display atlas bitmap when ready
    atlasState?.let { state ->
        when (state) {
            is MultiAtlasUpdateResult.Success -> {
                // Calculate detailed multi-atlas statistics
                val totalRegions = state.atlases.sumOf { it.regions.size }
                val avgUtilization = if (state.atlases.isNotEmpty()) {
                    state.atlases.map { it.utilization }.average().toFloat()
                } else 0f
                val totalMemoryMB = state.atlases.sumOf { atlas ->
                    val bitmapBytes = atlas.bitmap.allocationByteCount
                    bitmapBytes / (1024 * 1024)
                }
                val uniqueAtlasSizes = state.atlases.map { "${it.size.width}x${it.size.height}" }.toSet()
                val recycledCount = state.atlases.count { it.bitmap.isRecycled }
                val validAtlases = state.atlases.filter { !it.bitmap.isRecycled }

                Column(
                    modifier = modifier
                        .background(
                            Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Header
                    Text(
                        text = "Multi-Atlas Debug Info",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Atlas count and status
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Atlases: ${state.atlases.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Photos: $totalRegions",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                            if (recycledCount > 0) {
                                Text(
                                    text = "Recycled: $recycledCount",
                                    color = Color.Red,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Utilization: ${(avgUtilization * 100).roundToInt()}%",
                                color = if (avgUtilization > 0.6f) Color.Green else Color.Yellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Memory: ${totalMemoryMB}MB",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Seq: ${state.requestSequence}",
                                color = Color.Gray,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Atlas sizes breakdown
                    Text(
                        text = "Sizes: ${uniqueAtlasSizes.joinToString(", ")}",
                        color = Color.Cyan,
                        fontSize = 9.sp
                    )

                    // All atlas previews (if available and not recycled)
                    if (validAtlases.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Atlas Previews:",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(validAtlases) { atlas ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Gray.copy(alpha = 0.3f))
                                    ) {
                                        Image(
                                            bitmap = atlas.bitmap.asImageBitmap(),
                                            contentDescription = "Atlas ${atlas.size.width}x${atlas.size.height}",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        // Photo count badge
                                        Text(
                                            text = "${atlas.regions.size}",
                                            color = Color.White,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                                .padding(2.dp)
                                        )
                                        // Utilization badge
                                        Text(
                                            text = "${(atlas.utilization * 100).roundToInt()}%",
                                            color = if (atlas.utilization > 0.6f) Color.Green else Color.Yellow,
                                            fontSize = 6.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                                .padding(1.dp)
                                        )
                                    }
                                    // Atlas size label
                                    Text(
                                        text = "${atlas.size.width}x${atlas.size.height}",
                                        color = Color.Cyan,
                                        fontSize = 6.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    // Memory usage
                                    val memoryMB = atlas.bitmap.allocationByteCount / (1024 * 1024)
                                    Text(
                                        text = "${memoryMB}MB",
                                        color = Color.Gray,
                                        fontSize = 6.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is MultiAtlasUpdateResult.GenerationFailed -> {
                Box(
                    modifier = modifier
                        .background(Color.Red.copy(alpha = 0.7f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Multi-Atlas Failed\n${state.error}",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
            is MultiAtlasUpdateResult.Error -> {
                Box(
                    modifier = modifier
                        .background(Color.Red.copy(alpha = 0.7f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Multi-Atlas Error\n${state.exception.message ?: "Unknown error"}",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Debug overlay that shows atlas generation status with LOD level information.
 */
@Composable
fun AtlasGenerationStatusOverlay(
    isAtlasGenerating: Boolean,
    currentZoom: Float,
    atlasState: MultiAtlasUpdateResult? = null,
    modifier: Modifier = Modifier
) {
    val currentLODLevel = LODLevel.forZoom(currentZoom)
    val lodDisplayName = "${currentLODLevel.name} (${currentLODLevel.resolution}px)"

    // Calculate expected capacity for current LOD
    val expectedCapacity2K = LODLevel.getAtlasCapacity2K(currentLODLevel)
    val memoryUsageKB = LODLevel.getMemoryUsageKB(currentLODLevel)

    Column(
        modifier = modifier
            .background(
                if (isAtlasGenerating) Color.Blue.copy(alpha = 0.9f)
                else Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Status header
        Text(
            text = if (isAtlasGenerating) "⏳ Generating Atlas" else "📊 Current LOD",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        // LOD Level information
        Text(
            text = lodDisplayName,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )

        // Zoom level
        Text(
            text = "Zoom: ${String.format("%.2f", currentZoom)}x",
            color = Color.White,
            fontSize = 9.sp
        )

        // LOD capacity and memory info
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Cap: ${expectedCapacity2K}",
                color = Color.Cyan,
                fontSize = 8.sp
            )
            Text(
                text = "Mem: ${memoryUsageKB}KB",
                color = Color.Yellow,
                fontSize = 8.sp
            )
        }

        // Show zoom range for current LOD
        Text(
            text = "Range: ${currentLODLevel.zoomRange.start}-${currentLODLevel.zoomRange.endInclusive}",
            color = Color.Gray,
            fontSize = 8.sp
        )

        // Show actual atlas performance if available
        if (atlasState is MultiAtlasUpdateResult.Success && !isAtlasGenerating) {
            val actualPhotos = atlasState.atlases.sumOf { it.regions.size }
            val actualUtilization = if (atlasState.atlases.isNotEmpty()) {
                atlasState.atlases.map { it.utilization }.average().toFloat()
            } else 0f

            Text(
                text = "Actual: $actualPhotos photos, ${(actualUtilization * 100).roundToInt()}% util",
                color = if (actualUtilization > 0.6f) Color.Green else Color(0xFFFF9800),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
