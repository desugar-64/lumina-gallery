package dev.serhiiyaremych.lumina.ui.datelabel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

internal fun DrawScope.drawLabelBackground(
    labelPosition: Offset,
    textBoundsWidth: Float,
    textBoundsHeight: Float,
    config: DateLabelRenderConfig
) {
    val hp = BACKGROUND_HORIZONTAL_PADDING.toPx() / config.zoom
    val vp = BACKGROUND_VERTICAL_PADDING.toPx() / config.zoom
    drawRect(
        color = config.placeholderColor.copy(alpha = BACKGROUND_ALPHA),
        topLeft = Offset(
            labelPosition.x - textBoundsWidth / 2f - hp,
            labelPosition.y - textBoundsHeight / 2f - vp
        ),
        size = androidx.compose.ui.geometry.Size(
            textBoundsWidth + hp * 2f,
            textBoundsHeight + vp * 2f
        )
    )
}

internal fun DrawScope.drawLabelText(
    labelPosition: Offset,
    pathData: CachedPathData,
    config: DateLabelRenderConfig,
    alpha: Float
) {
    withTransform({
        translate(
            left = labelPosition.x - pathData.centerX,
            top = labelPosition.y - pathData.centerY
        )
    }) {
        drawPath(
            path = pathData.path.asComposePath(),
            color = config.dateLabelTextColor.copy(alpha = alpha)
        )
    }
}
