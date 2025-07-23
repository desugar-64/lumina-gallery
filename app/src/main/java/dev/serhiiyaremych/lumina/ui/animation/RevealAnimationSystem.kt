package dev.serhiiyaremych.lumina.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Animation state for reveal animations when photos are obscured.
 */
@Stable
data class RevealAnimationState(
    val slideOffset: Offset = Offset.Zero,
    val breathingScale: Float = 1f,
    val zIndex: Float = 0f,
    val alpha: Float = 1f
)

/**
 * Strategy interface for different reveal animation approaches.
 */
interface RevealAnimationStrategy {
    /**
     * Determines if a photo needs reveal animation based on visibility.
     */
    fun shouldAnimate(visibilityRatio: Float): Boolean
    
    /**
     * Calculates animation state for the clicked photo.
     */
    suspend fun animateReveal(
        item: AnimatableMediaItem,
        visibilityRatio: Float,
        animationSpec: AnimationSpec<Float> = tween(AnimationConstants.ANIMATION_DURATION_MS)
    ): RevealAnimationState
    
    /**
     * Calculates animation state for overlapping photos (if needed).
     */
    suspend fun animateOverlapping(
        overlappingItems: List<AnimatableMediaItem>,
        clickedItem: AnimatableMediaItem,
        animationSpec: AnimationSpec<Float> = tween(AnimationConstants.ANIMATION_DURATION_MS)
    ): Map<AnimatableMediaItem, RevealAnimationState>
    
    /**
     * Cleanup animation - returns items to original state.
     */
    suspend fun animateCleanup(
        allItems: List<AnimatableMediaItem>,
        animationSpec: AnimationSpec<Float> = tween(AnimationConstants.FAST_ANIMATION_DURATION_MS)
    )
}

/**
 * Enhanced wrapper class for media items with animation capabilities.
 * Manages selection state and provides explicit animation functions.
 */
@Stable
class AnimatableMediaItem(
    val mediaWithPosition: dev.serhiiyaremych.lumina.domain.model.MediaWithPosition
) {
    private val rotationAnimatable = Animatable(mediaWithPosition.rotationAngle)
    private val slideOffsetAnimatable = Animatable(Offset.Zero, Offset.VectorConverter)
    private val breathingScaleAnimatable = Animatable(1f)
    private val zIndexAnimatable = Animatable(0f)
    private val alphaAnimatable = Animatable(1f)

    // Pure state - no side effects
    var isSelected: Boolean by mutableStateOf(false)
        private set

    // Read-only animation state
    val currentRotation: Float get() = rotationAnimatable.value
    val currentSlideOffset: Offset get() = slideOffsetAnimatable.value
    val currentBreathingScale: Float get() = breathingScaleAnimatable.value
    val currentZIndex: Float get() = zIndexAnimatable.value
    val currentAlpha: Float get() = alphaAnimatable.value
    val isAnimating: Boolean get() = listOf(
        rotationAnimatable, slideOffsetAnimatable, breathingScaleAnimatable, 
        zIndexAnimatable, alphaAnimatable
    ).any { it.isRunning }
    val originalRotation: Float = mediaWithPosition.rotationAngle

    // Pure state update (no side effects)
    fun updateSelection(selected: Boolean) {
        isSelected = selected
    }

    // Explicit animation function (called from composable)
    suspend fun animateToSelectionState(
        animationSpec: AnimationSpec<Float> = spring(
            // Material 3 Expressive spring physics for natural rotation restoration
            stiffness = 800f, // Higher stiffness for responsive rotation changes
            dampingRatio = 0.7f // Moderate damping for smooth, natural motion without bounce
        )
    ) {
        val targetRotation = if (isSelected) 0f else originalRotation
        rotationAnimatable.animateTo(targetRotation, animationSpec)
    }
    
    // Reveal animation functions
    suspend fun animateToRevealState(
        state: RevealAnimationState,
        animationSpec: AnimationSpec<Float> = tween(AnimationConstants.ANIMATION_DURATION_MS)
    ) {
        coroutineScope {
            launch { slideOffsetAnimatable.animateTo(state.slideOffset, animationSpec.convertToOffset()) }
            launch { breathingScaleAnimatable.animateTo(state.breathingScale, animationSpec) }
            launch { zIndexAnimatable.animateTo(state.zIndex, animationSpec) }
            launch { alphaAnimatable.animateTo(state.alpha, animationSpec) }
        }
    }
    
    suspend fun resetRevealState(
        animationSpec: AnimationSpec<Float> = tween(AnimationConstants.FAST_ANIMATION_DURATION_MS)
    ) {
        coroutineScope {
            launch { slideOffsetAnimatable.animateTo(Offset.Zero, animationSpec.convertToOffset()) }
            launch { breathingScaleAnimatable.animateTo(1f, animationSpec) }
            launch { zIndexAnimatable.animateTo(0f, animationSpec) }
            launch { alphaAnimatable.animateTo(1f, animationSpec) }
        }
    }
}

/**
 * Manager for AnimatableMediaItem instances.
 * Handles creation, cleanup, and retrieval of animated media items.
 */
@Stable
class AnimatableMediaManager {
    private val animatableItems = mutableMapOf<dev.serhiiyaremych.lumina.domain.model.Media, AnimatableMediaItem>()

    fun getOrCreateAnimatable(mediaWithPosition: dev.serhiiyaremych.lumina.domain.model.MediaWithPosition): AnimatableMediaItem {
        return animatableItems.getOrPut(mediaWithPosition.media) {
            AnimatableMediaItem(mediaWithPosition)
        }
    }

    fun getAnimatable(media: dev.serhiiyaremych.lumina.domain.model.Media): AnimatableMediaItem? = animatableItems[media]

    fun cleanupUnused(currentMedia: Set<dev.serhiiyaremych.lumina.domain.model.Media>) {
        animatableItems.keys.retainAll(currentMedia)
    }

    fun hasAnimations(): Boolean = animatableItems.values.any { it.isAnimating }

    fun updateSelection(selectedMedia: dev.serhiiyaremych.lumina.domain.model.Media?) {
        animatableItems.values.forEach { item ->
            item.updateSelection(item.mediaWithPosition.media == selectedMedia)
        }
    }
}

/**
 * Solution 3: Pile Shuffle animation strategy with alpha fade.
 * When a photo is clicked, overlapping photos shuffle aside with alpha fade.
 */
class PileShuffleRevealStrategy : RevealAnimationStrategy {
    companion object {
        private const val ALPHA_FADE_VALUE = AnimationConstants.PILE_SHUFFLE_ALPHA
    }

    override fun shouldAnimate(visibilityRatio: Float): Boolean {
        return true // Always animate - no visibility threshold
    }

    override suspend fun animateReveal(
        item: AnimatableMediaItem,
        visibilityRatio: Float,
        animationSpec: AnimationSpec<Float>
    ): RevealAnimationState {
        // Clicked item stays solid and in place
        return RevealAnimationState(
            slideOffset = Offset.Zero,
            breathingScale = 1.0f,
            zIndex = 0f, // No z-index change for clicked item
            alpha = 1.0f // Full opacity
        )
    }

    override suspend fun animateOverlapping(
        overlappingItems: List<AnimatableMediaItem>,
        clickedItem: AnimatableMediaItem,
        animationSpec: AnimationSpec<Float>
    ): Map<AnimatableMediaItem, RevealAnimationState> {
        // Return empty map - no shuffle animation for overlapping items
        // This keeps surrounding images in place while preserving focus zoom
        return emptyMap()
    }

    override suspend fun animateCleanup(
        allItems: List<AnimatableMediaItem>,
        animationSpec: AnimationSpec<Float>
    ) {
        // Reset all items to original state
        allItems.forEach { item ->
            item.resetRevealState(animationSpec)
        }
    }
}

/**
 * Calculates how much of a photo is visible (not obscured by other photos).
 * Returns a ratio between 0.0 (completely obscured) and 1.0 (fully visible).
 */
fun calculateVisibilityRatio(
    targetItem: AnimatableMediaItem,
    allItems: List<AnimatableMediaItem>
): Float {
    val targetBounds = targetItem.mediaWithPosition.absoluteBounds
    var obscuredArea = 0f
    
    // Check each other item to see if it obscures the target
    allItems.forEach { otherItem ->
        if (otherItem != targetItem) {
            val otherBounds = otherItem.mediaWithPosition.absoluteBounds
            
            // Calculate intersection area
            val intersection = targetBounds.intersect(otherBounds)
            if (!intersection.isEmpty) {
                obscuredArea += intersection.width * intersection.height
            }
        }
    }
    
    val totalArea = targetBounds.width * targetBounds.height
    val visibleArea = totalArea - obscuredArea
    
    // Return visibility ratio, clamped between 0 and 1
    return (visibleArea / totalArea).coerceIn(0f, 1f)
}

/**
 * Extension function to normalize an Offset vector
 */
private fun Offset.normalized(): Offset {
    val length = kotlin.math.sqrt(x * x + y * y)
    return if (length > 0) Offset(x / length, y / length) else Offset.Zero
}

/**
 * Extension function to convert AnimationSpec<Float> to AnimationSpec<Offset>
 */
private fun AnimationSpec<Float>.convertToOffset(): AnimationSpec<Offset> {
    return when (this) {
        is androidx.compose.animation.core.TweenSpec -> tween(
            durationMillis = this.durationMillis,
            delayMillis = this.delay,
            easing = this.easing
        )
        else -> tween(AnimationConstants.ANIMATION_DURATION_MS) // Default fallback
    }
}