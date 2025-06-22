package dev.serhiiyaremych.lumina.ui

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 10f

/**
 * Handles pan/zoom transformations and programmatic focus.
 *
 * Key Features:
 * - Matrix-based transformation pipeline
 * - Focus-to-bounds calculation
 * - Gesture integration
 *
 * Usage:
 * 1. Attach to TransformableContent
 * 2. Call focusOn(bounds) to programmatically center content
 * 3. State is preserved across configuration changes
 *
 * Note: All transformations respect MIN_ZOOM/MAX_ZOOM constraints
 */
@Stable
class TransformableState(
    initialZoom: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    private val animationSpec: AnimationSpec<Float> = spring(),
    private val offsetAnimationSpec: AnimationSpec<Offset> = spring()
) {
    val zoomAnimatable = Animatable(initialZoom)
    val offsetAnimatable = Animatable(initialOffset, Offset.VectorConverter)
    var isAnimating by mutableStateOf(false)
    var contentSize by mutableStateOf(Size.Zero)

    // Public state
    val zoom: Float by derivedStateOf { zoomAnimatable.value }
    val offset: Offset by derivedStateOf { offsetAnimatable.value }

    fun updateContentSize(size: Size) {
        contentSize = size
    }

    suspend fun focusOn(bounds: Rect) {
        if (bounds.isEmpty || contentSize.isEmpty()) return

        isAnimating = true
        try {
            val targetZoom = calculateZoomToFit(bounds).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val targetOffset = calculateCenteringOffset(bounds, targetZoom)

            coroutineScope {
                launch { zoomAnimatable.animateTo(targetZoom, animationSpec) }
                launch { offsetAnimatable.animateTo(targetOffset, offsetAnimationSpec) }
            }
        } finally {
            isAnimating = false
        }
    }

    private fun calculateZoomToFit(bounds: Rect): Float {
        return min(
            contentSize.width / bounds.width,
            contentSize.height / bounds.height
        )
    }

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
    initialOffset: Offset = Offset.Zero,
    animationSpec: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    ),
    offsetAnimationSpec: AnimationSpec<Offset> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )
): TransformableState = rememberSaveable(saver = TransformerStateSaver) {
    TransformableState(
        initialZoom = initialZoom,
        initialOffset = initialOffset,
        animationSpec = animationSpec,
        offsetAnimationSpec = offsetAnimationSpec
    )
}

private object TransformerStateSaver : Saver<TransformableState, List<Any>> {
    override fun restore(value: List<Any>): TransformableState {
        return TransformableState(
            initialZoom = value[0] as Float,
            initialOffset = Offset(value[1] as Float, value[2] as Float)
        )
    }

    override fun SaverScope.save(state: TransformableState): List<Any> {
        return listOf(
            state.zoomAnimatable.value,
            state.offsetAnimatable.value.x,
            state.offsetAnimatable.value.y
        )
    }
}

@Composable
fun TransformableContent(
    modifier: Modifier = Modifier,
    state: TransformableState = rememberTransformableState(),
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(this.constraints) {
            state.updateContentSize(Size(
                constraints.maxWidth.toFloat(),
                constraints.maxHeight.toFloat()
            ))
        }

        // Restore matrix-based transformation
        val matrix = remember { Matrix() }
        val cachedValues = remember { FloatArray(9) }

        // Sync state → matrix (both for animations and direct changes)
        LaunchedEffect(state.zoom, state.offset) {
            matrix.reset()
            matrix.postScale(state.zoom, state.zoom)
            matrix.postTranslate(state.offset.x, state.offset.y)
        }
        val coroutineScope = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (!state.isAnimating) {
                            matrix.getValues(cachedValues)
                            val currentZoom = cachedValues[Matrix.MSCALE_X]
                            val newZoom = (currentZoom * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)

                            coroutineScope.launch {
                                matrix.postScale(
                                    zoom, zoom,
                                    centroid.x, centroid.y
                                )
                                matrix.postTranslate(pan.x, pan.y)

                                matrix.getValues(cachedValues)
                                state.zoomAnimatable.snapTo(cachedValues[Matrix.MSCALE_X])
                                state.offsetAnimatable.snapTo(
                                    Offset(
                                        cachedValues[Matrix.MTRANS_X],
                                        cachedValues[Matrix.MTRANS_Y]
                                    )
                                )
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}
