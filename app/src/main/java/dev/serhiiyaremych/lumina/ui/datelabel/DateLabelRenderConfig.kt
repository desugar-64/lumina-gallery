package dev.serhiiyaremych.lumina.ui.datelabel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Immutable
data class DateLabelRenderConfig(
    val zoom: Float,
    val clampedZoom: Float,
    val offset: Offset,
    val canvasSize: Size,
    val dateLabelTextColor: Color,
    val placeholderColor: Color
)

