package dev.serhiiyaremych.lumina.ui.debug

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serhiiyaremych.lumina.domain.usecase.AtlasUpdateResult

/**
 * Debug overlay that displays atlas state and generation status.
 * Shows atlas bitmap, status messages, and LOD level information.
 */
@Composable
fun AtlasDebugOverlay(
    atlasState: AtlasUpdateResult?,
    isAtlasGenerating: Boolean,
    currentZoom: Float,
    modifier: Modifier = Modifier
) {
    // Debug: Display atlas bitmap when ready
    atlasState?.let { state ->
        when (state) {
            is AtlasUpdateResult.Success -> {
                // Safety check: only render if bitmap is not recycled
                if (!state.atlas.bitmap.isRecycled) {
                    Box(
                        modifier = modifier
                            .fillMaxWidth()
                            .aspectRatio(1.0f)
                            .border(0.5.dp, Color.Magenta),
                    ) {
                        Image(
                            bitmap = state.atlas.bitmap.asImageBitmap(),
                            contentDescription = "Debug Atlas",
                            modifier = Modifier.matchParentSize()
                        )
                        
                        // Atlas info overlay
                        Text(
                            text = "Atlas Ready\n${state.atlas.regions.size} regions",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(4.dp)
                                .align(Alignment.TopStart)
                        )
                    }
                } else {
                    // Fallback when bitmap is recycled
                    Box(
                        modifier = modifier
                            .background(Color.Yellow.copy(alpha = 0.7f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Atlas Recycled",
                            color = Color.Black,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            is AtlasUpdateResult.GenerationFailed -> {
                Box(
                    modifier = modifier
                        .background(Color.Red.copy(alpha = 0.7f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Atlas Failed",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
            is AtlasUpdateResult.Error -> {
                Box(
                    modifier = modifier
                        .background(Color.Red.copy(alpha = 0.7f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Atlas Error",
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
    modifier: Modifier = Modifier
) {
    if (isAtlasGenerating) {
        val lodLevel = when {
            currentZoom <= 0.5f -> "LOD_0 (32px)"
            currentZoom <= 2.0f -> "LOD_2 (128px)"
            else -> "LOD_4 (512px)"
        }
        
        Box(
            modifier = modifier
                .background(Color.Blue.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Generating Atlas\n$lodLevel\nZoom: ${String.format("%.2f", currentZoom)}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}