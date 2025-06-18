package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
data class GridCanvasState(
    val majorGridSpacing: Dp = 56.dp,
    val majorGridColor: Color = Color.LightGray,
    val minorGridColor: Color = Color(0xFF808080)
)

@Composable
fun rememberGridCanvasState(
    majorGridSpacing: Dp = 56.dp,
    majorGridColor: Color = Color.LightGray,
    minorGridColor: Color = Color(0xFF808080)
): GridCanvasState = remember {
    GridCanvasState(
        majorGridSpacing = majorGridSpacing,
        majorGridColor = majorGridColor,
        minorGridColor = minorGridColor
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
    SideEffect { Log.d("GridCanvas", "GridCanvas: zoom=$zoom") }
    val density = LocalDensity.current
    val gridRenderer = remember { GridRenderer() }

    Box(
        modifier = modifier
            .drawWithContent {
                gridRenderer.drawGrid(
                    drawScope = this,
                    zoom = zoom,
                    offset = offset,
                    density = density,
                    majorGridSpacing = state.majorGridSpacing,
                    majorGridColor = state.majorGridColor,
                    minorGridColor = state.minorGridColor
                )

                drawContent()
            }
    ) {
        content()
    }
}
