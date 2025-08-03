package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaManager
import dev.serhiiyaremych.lumina.ui.animation.AnimationConstants
import dev.serhiiyaremych.lumina.ui.animation.HexCellBounceAnimationManager
import dev.serhiiyaremych.lumina.ui.animation.PileShuffleRevealStrategy
import dev.serhiiyaremych.lumina.ui.animation.calculateVisibilityRatio
import kotlinx.coroutines.launch

/**
 * Input handling configuration for media hex visualization.
 */
data class MediaInputConfig(
    val hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    val animationManager: AnimatableMediaManager,
    val geometryReader: GeometryReader,
    val provideZoom: () -> Float,
    val provideOffset: () -> Offset,
    val selectedMedia: Media? = null,
    val onMediaClicked: (Media) -> Unit = {},
    val onHexCellClicked: (HexCell) -> Unit = {},
    val onFocusRequested: (Rect) -> Unit = {},
    val onRipplePosition: (Offset?) -> Unit = {},
    val onClickedMedia: (Media?) -> Unit = {},
    val onClickedHexCell: (HexCell?) -> Unit = {},
    val onRevealAnimationTarget: (AnimatableMediaItem?) -> Unit = {},
    val cellFocusManager: CellFocusManager? = null,
    val bounceAnimationManager: HexCellBounceAnimationManager? = null
)

/**
 * Modifier extension for handling tap gestures in media hex visualization.
 * Encapsulates all input detection, hit testing, and animation triggering logic.
 */
@Composable
fun Modifier.mediaHexInput(config: MediaInputConfig): Modifier {
    val animationScope = rememberCoroutineScope()
    val revealStrategy = remember { PileShuffleRevealStrategy() }

    return this.pointerInput(config.selectedMedia, config.provideZoom, config.provideOffset) {
        detectTapGestures { tapOffset ->
            val zoom = config.provideZoom()
            val offset = config.provideOffset()
            val clampedZoom = zoom.coerceIn(0.01f, 100f)
            val transformedPos = Offset(
                (tapOffset.x - offset.x) / clampedZoom,
                (tapOffset.y - offset.y) / clampedZoom
            )

            config.onRipplePosition(tapOffset)

            // Check for media hits with rotation-aware hit testing
            val hitAnimatableMedia = findAnimatableMediaAtPosition(
                transformedPos,
                config.hexGridLayout,
                config.animationManager
            )

            if (hitAnimatableMedia != null) {
                handleMediaTap(
                    hitAnimatableMedia = hitAnimatableMedia,
                    config = config,
                    animationScope = animationScope,
                    revealStrategy = revealStrategy
                )
            } else {
                handleHexCellTap(
                    transformedPos = transformedPos,
                    config = config,
                    animationScope = animationScope
                )
            }
        }
    }
}

/**
 * Handles tap on media item with reveal animation or deselection cleanup.
 */
private fun handleMediaTap(
    hitAnimatableMedia: AnimatableMediaItem,
    config: MediaInputConfig,
    animationScope: kotlinx.coroutines.CoroutineScope,
    revealStrategy: PileShuffleRevealStrategy
) {
    val media = hitAnimatableMedia.mediaWithPosition.media
    // Check selection state BEFORE calling callbacks
    val isCurrentlySelected = config.selectedMedia == media

    // Call the callbacks to update selection state
    config.onMediaClicked(media)
    config.onClickedMedia(media)
    config.onClickedHexCell(null)

    // Notify cell focus manager of media click
    config.cellFocusManager?.let { focusManager ->
        // Find the HexCellWithMedia containing this media
        config.hexGridLayout.hexCellsWithMedia.find { cellWithMedia ->
            cellWithMedia.mediaItems.any { it.media == media }
        }?.let { hexCellWithMedia ->
            focusManager.onCellClicked(
                hexCellWithMedia = hexCellWithMedia,
                allHexCellsWithMedia = config.hexGridLayout.hexCellsWithMedia
            )
        }
    }

    val allAnimatableItems = config.hexGridLayout.hexCellsWithMedia.flatMap { cell ->
        cell.mediaItems.map { mediaWithPos ->
            config.animationManager.getOrCreateAnimatable(mediaWithPos)
        }
    }

    if (isCurrentlySelected) {
        // Media was already selected, so this is a deselection - trigger cleanup animation
        animationScope.launch {
            // Reset all items simultaneously (same as hex cell tap)
            allAnimatableItems.forEach { item ->
                launch { item.resetRevealState() }
            }
        }

        config.onRevealAnimationTarget(null)
    } else {
        // Media was not selected, so this is a new selection - trigger reveal animation

        // Clean up previous animation IMMEDIATELY if needed
        config.onRevealAnimationTarget(null) // Clear previous target first

        // Start new animation immediately
        animationScope.launch {
            val visibilityRatio = calculateVisibilityRatio(hitAnimatableMedia, allAnimatableItems)

            // Animate the clicked item (stays in place, full opacity)
            val revealState = revealStrategy.animateReveal(
                hitAnimatableMedia,
                visibilityRatio,
                androidx.compose.animation.core.tween(AnimationConstants.ANIMATION_DURATION_MS)
            )
            hitAnimatableMedia.animateToRevealState(revealState)

            // Animate overlapping items (shuffle aside + fade) concurrently
            val overlappingAnimations = revealStrategy.animateOverlapping(
                allAnimatableItems.filter { it != hitAnimatableMedia },
                hitAnimatableMedia,
                androidx.compose.animation.core.tween(AnimationConstants.ANIMATION_DURATION_MS)
            )

            overlappingAnimations.forEach { (item, state) ->
                launch { item.animateToRevealState(state) }
            }
        }

        config.onRevealAnimationTarget(hitAnimatableMedia)

        // Trigger focus request with unrotated bounds for selected media
        config.geometryReader.getMediaBounds(media)?.let { bounds ->
            config.onFocusRequested(bounds)
        }
    }
}

/**
 * Handles tap on hex cell with cleanup of any active animations.
 */
private fun handleHexCellTap(
    transformedPos: Offset,
    config: MediaInputConfig,
    animationScope: kotlinx.coroutines.CoroutineScope
) {
    config.geometryReader.getHexCellAtPosition(transformedPos)?.let { cell ->
        config.onHexCellClicked(cell)
        config.onClickedHexCell(cell)
        config.onClickedMedia(null)

        // Notify cell focus manager of hex cell click
        config.cellFocusManager?.let { focusManager ->
            // Find the HexCellWithMedia for this cell
            config.hexGridLayout.hexCellsWithMedia.find { it.hexCell == cell }?.let { hexCellWithMedia ->
                focusManager.onCellClicked(
                    hexCellWithMedia = hexCellWithMedia,
                    allHexCellsWithMedia = config.hexGridLayout.hexCellsWithMedia
                )
            }
        }

        // Clear reveal animation IMMEDIATELY when clicking on cell
        val allAnimatableItems = config.hexGridLayout.hexCellsWithMedia.flatMap { hexCell ->
            hexCell.mediaItems.map { mediaWithPos ->
                config.animationManager.getOrCreateAnimatable(mediaWithPos)
            }
        }

        animationScope.launch {
            // Reset all items simultaneously
            allAnimatableItems.forEach { item ->
                launch { item.resetRevealState() }
            }
        }

        config.onRevealAnimationTarget(null)

        // Trigger Material 3 bounce animation for the clicked hex cell
        // We need to determine the cell state to pass to the bounce animation
        val cellState = when {
            config.selectedMedia != null -> {
                // Check if this cell contains the selected media
                val containsSelectedMedia = config.hexGridLayout.hexCellsWithMedia
                    .find { it.hexCell == cell }
                    ?.mediaItems?.any { it.media == config.selectedMedia } == true
                if (containsSelectedMedia) HexCellState.SELECTED else HexCellState.NORMAL
            }
            else -> HexCellState.NORMAL
        }
        config.bounceAnimationManager?.triggerBounce(cell, cellState)

        // Trigger focus request
        config.geometryReader.getHexCellBounds(cell)?.let { bounds ->
            config.onFocusRequested(bounds)
        }
    }
}
