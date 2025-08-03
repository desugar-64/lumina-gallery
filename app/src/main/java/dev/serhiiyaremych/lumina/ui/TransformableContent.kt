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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.min

// Import zoom constants from shared location
import dev.serhiiyaremych.lumina.ui.ZoomConstants.MIN_ZOOM
import dev.serhiiyaremych.lumina.ui.ZoomConstants.MAX_ZOOM

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
 * Handles pan/zoom transformations and programmatic focus for content.
 *
 * Features:
 * - Unified matrix-based transformation pipeline
 * - Smooth animations via [MatrixAnimator]
 * - Content-aware focus calculations with configurable padding
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
 * @param focusPadding Padding around focused content in dp (prevents edge-to-edge fitting)
 *
 * Note: All transformations respect MIN_ZOOM/MAX_ZOOM constraints
 */
@Stable
class TransformableState(
    initialZoom: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    private val animationSpec: AnimationSpec<Matrix> = spring(),
    private val focusPadding: androidx.compose.ui.unit.Dp = 48.dp
) {
    // Reused array for matrix value extraction to avoid allocations
    private val matrixValuesCache = FloatArray(9)
    // Cached matrix instance for focusOn to avoid allocations
    private val focusMatrix = Matrix()

    private val matrixAnimator = MatrixAnimator(
        Matrix().apply {
            postScale(initialZoom, initialZoom)
            postTranslate(initialOffset.x, initialOffset.y)
        },
        animationSpec
    )

    // Internal accessor for state saving
    internal val currentMatrix: Matrix get() = matrixAnimator.value

    var isAnimating by mutableStateOf(false)
    var contentSize by mutableStateOf(Size.Zero)
    var density: Density? by mutableStateOf(null)

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

    suspend fun focusOn(bounds: Rect, padding: androidx.compose.ui.unit.Dp = focusPadding) {
        if (bounds.isEmpty || contentSize.isEmpty()) return

        isAnimating = true
        try {
            val targetZoom = calculateZoomToFit(bounds, padding).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val targetOffset = calculateCenteringOffset(bounds, targetZoom)

            focusMatrix.reset()
            focusMatrix.postScale(targetZoom, targetZoom)
            focusMatrix.postTranslate(targetOffset.x, targetOffset.y)

            matrixAnimator.animateTo(Matrix(focusMatrix))
        } finally {
            isAnimating = false
        }
    }

    // Animation methods removed - using updateMatrix directly for benchmark automation

    suspend fun updateMatrix(block: Matrix.() -> Unit): Boolean {
        val originalMatrix = Matrix(matrixAnimator.value)
        val newMatrix = Matrix(originalMatrix).apply(block)

        // Extract and clamp zoom level
        newMatrix.getValues(matrixValuesCache)
        val currentZoom = matrixValuesCache[Matrix.MSCALE_X]
        val clampedZoom = currentZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

        val wasZoomClamped = clampedZoom != currentZoom

        // If zoom was clamped, revert to original and apply only the allowed zoom
        if (wasZoomClamped) {
            matrixAnimator.snapTo(originalMatrix)
            return true
        }

        matrixAnimator.snapTo(newMatrix)
        return false
    }

    private fun calculateZoomToFit(bounds: Rect, padding: androidx.compose.ui.unit.Dp): Float {
        // Convert padding from Dp to pixels
        val paddingPx = density?.run { padding.toPx() } ?: 0f
        val doublePadding = paddingPx * 2f // Padding on both sides
        
        // Calculate available screen space after reserving padding
        val availableWidth = contentSize.width - doublePadding
        val availableHeight = contentSize.height - doublePadding
        
        // Ensure we have minimum space (prevent division by zero or negative values)
        // Use very minimal constraint to allow tight padding - just 1px more than content
        val safeAvailableWidth = availableWidth.coerceAtLeast(bounds.width + 1f)
        val safeAvailableHeight = availableHeight.coerceAtLeast(bounds.height + 1f)
        
        // Calculate zoom to fit content within available space
        return min(
            safeAvailableWidth / bounds.width,
            safeAvailableHeight / bounds.height
        )
    }

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
        // Material 3 Expressive spring physics for smooth, natural motion
        stiffness = 700f, // Higher stiffness for quicker acceleration, avoiding abrupt start
        dampingRatio = 0.75f // Moderate damping for smooth deceleration without bounce
    ),
    focusPadding: androidx.compose.ui.unit.Dp = 48.dp
): TransformableState = rememberSaveable(saver = TransformerStateSaver) {
    TransformableState(
        initialZoom = initialZoom,
        initialOffset = initialOffset,
        animationSpec = animationSpec,
        focusPadding = focusPadding
    )
}

private object TransformerStateSaver : Saver<TransformableState, List<Any>> {
    override fun restore(value: List<Any>): TransformableState = TransformableState(
        initialZoom = value[2] as Float,
        initialOffset = Offset(value[0] as Float, value[1] as Float)
    )

    override fun SaverScope.save(state: TransformableState): List<Any> {
        val values = FloatArray(9)
        state.currentMatrix.getValues(values)
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
    val density = LocalDensity.current
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(this.constraints) {
            state.updateContentSize(
                Size(
                    constraints.maxWidth.toFloat(),
                    constraints.maxHeight.toFloat()
                )
            )
            state.density = density
        }

        val coroutineScope = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (!state.isAnimating) {
                            coroutineScope.launch {
                                // Apply zoom first and check if it was clamped
                                val wasZoomClamped = state.updateMatrix {
                                    postScale(zoom, zoom, centroid.x, centroid.y)
                                }

                                // Only apply pan if zoom wasn't clamped
                                if (!wasZoomClamped) {
                                    state.updateMatrix {
                                        postTranslate(pan.x, pan.y)
                                    }
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
