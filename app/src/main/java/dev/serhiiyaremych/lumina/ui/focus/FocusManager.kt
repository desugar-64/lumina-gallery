package dev.serhiiyaremych.lumina.ui.focus

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import dev.serhiiyaremych.lumina.domain.model.FocusRequest
import dev.serhiiyaremych.lumina.ui.geometry.GeometryReader
import javax.inject.Inject
import dev.serhiiyaremych.lumina.ui.MIN_ZOOM
import dev.serhiiyaremych.lumina.ui.MAX_ZOOM

private const val TARGET_VIEW_SCALE = 0.8f

data class MatrixUpdate(
    val newOffset: Offset,
    val newZoom: Float
)

class FocusManager @Inject constructor(
    private val geometryReader: GeometryReader
) {
    suspend fun focus(
        request: FocusRequest,
        currentMatrixValues: FloatArray,
        viewWidth: Float,
        viewHeight: Float
    ): MatrixUpdate? {
        val targetBounds = when (request) {
            is FocusRequest.HexCellFocus -> geometryReader.getHexCellBounds(request.hexCell)
            is FocusRequest.MediaFocus -> geometryReader.getMediaBounds(request.media)
        } ?: return null

        val targetCenterX = targetBounds.centerX()
        val targetCenterY = targetBounds.centerY()

        val requiredZoomForWidth = (viewWidth * TARGET_VIEW_SCALE) / targetBounds.width()
        val requiredZoomForHeight = (viewHeight * TARGET_VIEW_SCALE) / targetBounds.height()

        var newZoom = minOf(requiredZoomForWidth, requiredZoomForHeight)
        newZoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

        val newTranslateX = viewWidth / 2f - targetCenterX * newZoom
        val newTranslateY = viewHeight / 2f - targetCenterY * newZoom

        return MatrixUpdate(
            newOffset = Offset(newTranslateX, newTranslateY),
            newZoom = newZoom
        )
    }
}
