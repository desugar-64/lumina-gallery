package dev.serhiiyaremych.lumina.ui

import android.graphics.Matrix
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.min

private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 10f

@Stable
class TransformableState(initialZoom: Float = 1f, initialOffset: Offset = Offset.Zero) {
    var zoom by mutableFloatStateOf(initialZoom)
    var offset by mutableStateOf(initialOffset)

    // Track content size (set by TransformableContent)
    private var contentSize by mutableStateOf(Size.Zero)

    // Public method to update size
    fun updateContentSize(size: Size) {
        contentSize = size
    }

    // Core logic: Focus on a Rect
    fun focusOn(bounds: Rect) {
        if (bounds.isEmpty || contentSize.isEmpty()) return

        val targetZoom = calculateZoomToFit(bounds)
        zoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        offset = calculateCenteringOffset(bounds, zoom)
    }

    // Calculate zoom to fit bounds within contentSize
    private fun calculateZoomToFit(bounds: Rect): Float {
        return min(
            contentSize.width / bounds.width,
            contentSize.height / bounds.height
        )
    }

    // Center the bounds after zoom
    private fun calculateCenteringOffset(bounds: Rect, zoom: Float): Offset {
        return Offset(
            (contentSize.width / 2) - (bounds.center.x * zoom),
            (contentSize.height / 2) - (bounds.center.y * zoom)
        )
    }
}

@Composable
fun rememberTransformableState(
    initialZoom: Float = 1f,
    initialOffset: Offset = Offset.Zero
): TransformableState = rememberSaveable(saver = TransformerStateSaver) {
    TransformableState(initialZoom, initialOffset)
}

private object TransformerStateSaver : Saver<TransformableState, List<Any>> {
    override fun restore(value: List<Any>): TransformableState = TransformableState(
        initialZoom = (value[0] as Float),
        initialOffset = Offset(value[1] as Float, value[2] as Float)
    )

    override fun SaverScope.save(state: TransformableState): List<Any> = listOf(
        state.zoom,
        state.offset.x,
        state.offset.y
    )
}

@Composable
fun TransformableContent(
    modifier: Modifier = Modifier,
    state: TransformableState = rememberTransformableState(),
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(constraints) {
            state.updateContentSize(Size(
                constraints.maxWidth.toFloat(),
                constraints.maxHeight.toFloat()
            ))
        }

        // Matrix is the single source of truth for transformations
        val matrix = remember { Matrix() }
        val cachedValues = remember { FloatArray(9) }

        // Sync state → matrix when zoom/offset changes (including from focusOn())
        LaunchedEffect(state.zoom, state.offset) {
            matrix.reset()
            matrix.postScale(state.zoom, state.zoom)
            matrix.postTranslate(state.offset.x, state.offset.y)
        }

        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, translation, scale, _ ->
                        matrix.getValues(cachedValues)
                        val currentZoom = cachedValues[Matrix.MSCALE_X]
                        val newZoom = currentZoom * scale

                        if (newZoom >= MIN_ZOOM && newZoom <= MAX_ZOOM) {
                            matrix.postScale(scale, scale, centroid.x, centroid.y)
                            matrix.postTranslate(translation.x, translation.y)
                        } else {
                            matrix.postTranslate(translation.x, translation.y)
                            if ((currentZoom <= MIN_ZOOM && scale > 1f) ||
                                (currentZoom >= MAX_ZOOM && scale < 1f)
                            ) {
                                matrix.postScale(scale, scale, centroid.x, centroid.y)
                            }
                        }

                        matrix.getValues(cachedValues)
                        state.offset = Offset(
                            cachedValues[Matrix.MTRANS_X],
                            cachedValues[Matrix.MTRANS_Y]
                        )
                        state.zoom = cachedValues[Matrix.MSCALE_X]
                    }
                }
        ) {
            content()
        }
    }
}
