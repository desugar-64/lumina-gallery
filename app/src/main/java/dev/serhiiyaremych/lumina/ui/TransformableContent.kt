package dev.serhiiyaremych.lumina.ui

import android.graphics.Matrix // Keep android.graphics.Matrix
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent // Import this
import androidx.compose.ui.graphics.withTransform // Import this

internal const val MIN_ZOOM = 0.1f
internal const val MAX_ZOOM = 10f

@Composable
fun TransformableContent(
    modifier: Modifier = Modifier,
    zoom: Float, // Zoom state from ViewModel
    offset: Offset, // Offset state from ViewModel
    onTransformChanged: (newZoom: Float, newOffset: Offset, newMatrix: Matrix) -> Unit,
    content: @Composable () -> Unit
) {
    val transformationMatrix = remember { Matrix() }
    val matrixValues = remember { FloatArray(9) }

    // When external zoom/offset change (e.g., due to focus via ViewModel, or initial state)
    // update the local transformationMatrix. This matrix is the source of truth for drawing.
    LaunchedEffect(zoom, offset) {
        transformationMatrix.reset()
        transformationMatrix.postScale(zoom, zoom) // Use current zoom from ViewModel state
        transformationMatrix.postTranslate(offset.x, offset.y) // Use current offset from ViewModel state
        // Report this new matrix state to the ViewModel.
        // This ensures the ViewModel's copy of the matrix (currentDisplayMatrix) is updated.
        onTransformChanged(zoom, offset, transformationMatrix)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    // Apply gesture transformations incrementally to the current transformationMatrix
                    transformationMatrix.getValues(matrixValues) // Get current scale and translate from matrix
                    val oldScale = matrixValues[Matrix.MSCALE_X]

                    val newScaleCalculated = (oldScale * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    // Avoid division by zero or issues if oldScale is very small or zero
                    val actualGestureScaleFactor = if (oldScale == 0f) 1f else newScaleCalculated / oldScale

                    transformationMatrix.postScale(actualGestureScaleFactor, actualGestureScaleFactor, centroid.x, centroid.y)
                    transformationMatrix.postTranslate(pan.x, pan.y)

                    // Report the new state of transformationMatrix after gesture
                    transformationMatrix.getValues(matrixValues)
                    val finalZoom = matrixValues[Matrix.MSCALE_X]
                    val finalOffset = Offset(matrixValues[Matrix.MTRANS_X], matrixValues[Matrix.MTRANS_Y])
                    onTransformChanged(finalZoom, finalOffset, transformationMatrix)
                }
            }
            .drawWithContent { // Apply transformation using drawWithContent
                this.withTransform({
                    // Apply the current state of transformationMatrix to the canvas
                    transform(transformationMatrix)
                }) {
                    // This will draw the children of the Box, which is content()
                    this.drawContent()
                }
            }
    ) {
        // The actual composable content provided to TransformableContent
        content()
    }
}
