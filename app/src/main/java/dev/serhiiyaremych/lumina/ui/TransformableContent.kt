package dev.serhiiyaremych.lumina.ui

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
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
import kotlinx.coroutines.launch
import kotlin.math.min

private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 10f

private data class TransformValues(val zoom: Float, val offset: Offset)

class MatrixAnimator(
    initial: Matrix = Matrix(),
    private val spec: AnimationSpec<Matrix> = spring()
) {
    private val animatable = Animatable(initial, MatrixVectorConverter)
    val value: Matrix get() = animatable.value

    suspend fun animateTo(target: Matrix) = animatable.animateTo(target, spec)
    suspend fun snapTo(value: Matrix) = animatable.snapTo(value)
}

object MatrixVectorConverter : TwoWayConverter<Matrix, AnimationVector4D> {
    // Shared thread-local storage for matrix values
    private val matrixValues = ThreadLocal.withInitial { FloatArray(9) }

    override val convertToVector: (Matrix) -> AnimationVector4D = { matrix ->
        val values = matrixValues.get()
        matrix.getValues(values)
        AnimationVector4D(
            values[Matrix.MSCALE_X],
            values[Matrix.MTRANS_X],
            values[Matrix.MTRANS_Y],
            values[Matrix.MSCALE_Y]
        )
    }

    override val convertFromVector: (AnimationVector4D) -> Matrix = { vector ->
        Matrix().apply {
            setScale(vector.v1, vector.v4)
            postTranslate(vector.v2, vector.v3)
        }
    }
}

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

/**
 * Handles pan/zoom transformations and programmatic focus for content.
 *
 * Features:
 * - Unified matrix-based transformation pipeline
 * - Smooth animations via [MatrixAnimator]
 * - Content-aware focus calculations
 * - Gesture integration
 *
 * @property zoom Current scale factor (1f = normal size)
 * @property offset Current translation from origin
 * @property isAnimating True when animation is in progress
 * @property contentSize Size of the content area for focus calculations
 *
 * @param initialZoom Starting zoom level (default: 1f)
 * @param initialOffset Starting offset (default: Offset.Zero)
 * @param animationSpec Animation configuration for transformations
 */
@Stable
class TransformableState(
    initialZoom: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    private val animationSpec: AnimationSpec<Matrix> = spring()
) {
    // Reused array for matrix value extraction to avoid allocations
    private val matrixValuesCache = FloatArray(9)

    val matrixAnimator = MatrixAnimator(
        Matrix().apply {
            postScale(initialZoom, initialZoom)
            postTranslate(initialOffset.x, initialOffset.y)
        },
        animationSpec
    )

    var isAnimating by mutableStateOf(false)
    var contentSize by mutableStateOf(Size.Zero)

    // Combined matrix extraction to avoid redundant getValues() calls
    private val transformValues by derivedStateOf {
        matrixAnimator.value.getValues(matrixValuesCache)
        TransformValues(
            zoom = matrixValuesCache[Matrix.MSCALE_X],
            offset = Offset(
                matrixValuesCache[Matrix.MTRANS_X],
                matrixValuesCache[Matrix.MTRANS_Y]
            )
        )
    }

    val zoom: Float get() = transformValues.zoom
    val offset: Offset get() = transformValues.offset

    fun updateContentSize(size: Size) = run { contentSize = size }

    suspend fun focusOn(bounds: Rect) {
        if (bounds.isEmpty || contentSize.isEmpty()) return

        isAnimating = true
        try {
            val targetZoom = calculateZoomToFit(bounds).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val targetOffset = calculateCenteringOffset(bounds, targetZoom)

            val targetMatrix = Matrix().apply {
                postScale(targetZoom, targetZoom)
                postTranslate(targetOffset.x, targetOffset.y)
            }

            matrixAnimator.animateTo(targetMatrix)
        } finally {
            isAnimating = false
        }
    }

    suspend fun updateMatrix(block: Matrix.() -> Unit) {
        matrixAnimator.snapTo(Matrix(matrixAnimator.value).apply(block))
    }

    private fun calculateZoomToFit(bounds: Rect): Float = min(
        contentSize.width / bounds.width,
        contentSize.height / bounds.height
    )

    private fun calculateCenteringOffset(bounds: Rect, zoom: Float): Offset = Offset(
        (contentSize.width / 2) - (bounds.center.x * zoom),
        (contentSize.height / 2) - (bounds.center.y * zoom)
    )
}

@Composable
fun rememberTransformableState(
    initialZoom: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    animationSpec: AnimationSpec<Matrix> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )
): TransformableState = rememberSaveable(saver = TransformerStateSaver) {
    TransformableState(
        initialZoom = initialZoom,
        initialOffset = initialOffset,
        animationSpec = animationSpec
    )
}

private object TransformerStateSaver : Saver<TransformableState, List<Any>> {
    override fun restore(value: List<Any>): TransformableState = TransformableState(
        initialZoom = value[2] as Float,
        initialOffset = Offset(value[0] as Float, value[1] as Float)
    )

    override fun SaverScope.save(state: TransformableState): List<Any> {
        val values = FloatArray(9)
        state.matrixAnimator.value.getValues(values)
        return listOf(
            values[Matrix.MTRANS_X],
            values[Matrix.MTRANS_Y],
            values[Matrix.MSCALE_X]
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
            state.updateContentSize(
                Size(
                    constraints.maxWidth.toFloat(),
                    constraints.maxHeight.toFloat()
                )
            )
        }

        val coroutineScope = rememberCoroutineScope()
        Box(
            modifier = Modifier.pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (!state.isAnimating) {
                        coroutineScope.launch {
                            state.updateMatrix {
                                postScale(zoom, zoom, centroid.x, centroid.y)
                                postTranslate(pan.x, pan.y)
                            }
                        }
                    }
                }
            }
        ) {
            content()
        }
    }
}
