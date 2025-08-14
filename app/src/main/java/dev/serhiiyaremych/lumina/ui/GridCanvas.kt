package dev.serhiiyaremych.lumina.ui

import android.util.Log
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
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val gridRenderer = remember { GridRenderer() }

    val majorGridColor = colorScheme.onSurfaceVariant.copy(alpha = 0.5f).compositeOver(colorScheme.background)
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
                    minorGridColor = minorGridColor
                )

                drawContent()
            }
    ) {
        content()
    }
}
