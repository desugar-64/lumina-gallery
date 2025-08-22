package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Animation timing constants
private const val ANIMATION_CYCLE_DURATION_MS = 10000 // 10 second animation cycles
private const val RETURN_TRANSITION_DURATION_MS = 800 // Smooth return transition
private const val GRID_COLOR_ALPHA = 0.5f // Major grid color alpha

@Stable
data class GridCanvasState(
    val majorGridSpacing: Dp = 56.dp
)

@Composable
fun rememberGridCanvasState(
    majorGridSpacing: Dp = 56.dp
): GridCanvasState = remember {
    GridCanvasState(
        majorGridSpacing = majorGridSpacing
    )
}

@Composable
fun GridCanvas(
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    offset: Offset = Offset.Zero,
    state: GridCanvasState = rememberGridCanvasState(),
    isLoading: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val gridRenderer = remember { GridRenderer() }

    // Animation time for breathing wave effect
    val infiniteTransition = rememberInfiniteTransition(label = "GridBreathingWave")
    val animationTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = ANIMATION_CYCLE_DURATION_MS.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(ANIMATION_CYCLE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BreathingWaveTime"
    )

    // Smooth return transition animation
    val returnTransition by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(
            durationMillis = RETURN_TRANSITION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "GridReturnTransition"
    )

    val majorGridColor = colorScheme.onSurfaceVariant.copy(alpha = GRID_COLOR_ALPHA).compositeOver(colorScheme.background)
    val minorGridColor = colorScheme.outline // Very subtle minor grid dots

    Box(
        modifier = modifier
            .drawWithContent {
                gridRenderer.drawGrid(
                    drawScope = this,
                    zoom = zoom,
                    offset = offset,
                    density = density,
                    majorGridSpacing = state.majorGridSpacing,
                    majorGridColor = majorGridColor,
                    minorGridColor = minorGridColor,
                    isLoading = isLoading,
                    animationTime = animationTime.toLong(),
                    returnTransitionProgress = returnTransition
                )

                drawContent()
            }
    ) {
        content()
    }
}
