package dev.serhiiyaremych.lumina.ui

import android.graphics.Matrix
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer

internal const val MIN_ZOOM = 0.1f
internal const val MAX_ZOOM = 10f

@Composable
fun TransformableContent(
    modifier: Modifier = Modifier,
    zoom: Float,
    offset: Offset,
    onTransformChanged: (newZoom: Float, newOffset: Offset, newMatrix: Matrix) -> Unit,
    content: @Composable () -> Unit
) {
    val currentMatrix = remember { Matrix() }
    val matrixValues = remember { FloatArray(9) }

    LaunchedEffect(zoom, offset) {
        currentMatrix.reset()
        currentMatrix.postScale(zoom, zoom)
        currentMatrix.postTranslate(offset.x, offset.y)
        onTransformChanged(zoom, offset, currentMatrix)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    currentMatrix.getValues(matrixValues)
                    val oldZoom = matrixValues[Matrix.MSCALE_X]
                    val newZoomCalculated = (oldZoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)

                    val actualGestureZoom = newZoomCalculated / oldZoom

                    currentMatrix.postScale(actualGestureZoom, actualGestureZoom, centroid.x, centroid.y)
                    currentMatrix.postTranslate(pan.x, pan.y)

                    currentMatrix.getValues(matrixValues)
                    val finalZoom = matrixValues[Matrix.MSCALE_X]
                    val finalOffset = Offset(matrixValues[Matrix.MTRANS_X], matrixValues[Matrix.MTRANS_Y])

                    onTransformChanged(finalZoom, finalOffset, currentMatrix)
                }
            }
            .graphicsLayer(
                scaleX = zoom,
                scaleY = zoom,
                translationX = offset.x,
                translationY = offset.y
            )
    ) {
        content()
    }
}
