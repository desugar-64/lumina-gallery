package dev.serhiiyaremych.lumina.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.ui.OffscreenIndicator
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Configuration for directional indicator appearance
 */
data class DirectionalIndicatorStyle(
    val size: androidx.compose.ui.unit.Dp = 40.dp,
    val elevation: androidx.compose.ui.unit.Dp = 8.dp,
    val borderWidth: androidx.compose.ui.unit.Dp = 2.dp,
    val animationDurationMs: Int = 300,
    val minAlpha: Float = 0.6f,
    val maxAlpha: Float = 1.0f
)

/**
 * A circular directional indicator that appears on viewport edges to show
 * the direction of offscreen content. Similar to radar or minimap indicators.
 *
 * Features:
 * - Positioned precisely on viewport edges
 * - Arrow rotates to point toward offscreen content
 * - Fades based on content distance
 * - Material 3 styling with elevation and theming
 * - Smooth animations for position and rotation changes
 */
@Composable
fun DirectionalIndicator(
    indicator: OffscreenIndicator,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: DirectionalIndicatorStyle = DirectionalIndicatorStyle()
) {
    val rotationAngle = (indicator.direction * 180 / kotlin.math.PI).toFloat()

    // Animate rotation smoothly
    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = style.animationDurationMs),
        label = "indicator_rotation"
    )

    // Calculate alpha based on distance (closer = more opaque)
    val targetAlpha = style.minAlpha + (1f - indicator.distance) * (style.maxAlpha - style.minAlpha)
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = style.animationDurationMs),
        label = "indicator_alpha"
    )

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = indicator.position.x.roundToInt() - (style.size.roundToPx() / 2),
                    y = indicator.position.y.roundToInt() - (style.size.roundToPx() / 2)
                )
            }
            .size(style.size)
            .graphicsLayer {
                rotationZ = animatedRotation
                alpha = animatedAlpha
                clip = true
                shape = CircleShape
            }
            .background(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(style.elevation),
                shape = CircleShape
            )
            .border(
                width = style.borderWidth,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "➤",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

/**
 * Container for multiple directional indicators.
 * Handles positioning and coordination between indicators.
 */
@Composable
fun DirectionalIndicatorOverlay(
    indicators: List<OffscreenIndicator>,
    onIndicatorClick: (OffscreenIndicator) -> Unit,
    modifier: Modifier = Modifier,
    style: DirectionalIndicatorStyle = DirectionalIndicatorStyle()
) {
    Box(modifier = modifier) {
        indicators.forEach { indicator ->
            key(indicator.id) {
                DirectionalIndicator(
                    indicator = indicator,
                    onClick = { onIndicatorClick(indicator) },
                    style = style
                )
            }
        }
    }
}
