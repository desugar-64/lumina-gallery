package dev.serhiiyaremych.lumina.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaManager
import dev.serhiiyaremych.lumina.ui.animation.HexCellBounceAnimationManager
import dev.serhiiyaremych.lumina.ui.animation.rememberHexCellBounceAnimationManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * State holder for media hex visualization.
 * Manages all state variables, animations, and side effects.
 */
data class MediaHexState(
    val animationManager: AnimatableMediaManager,
    val geometryReader: GeometryReader,
    val clickedMedia: Media?,
    val clickedHexCell: HexCell?,
    val ripplePosition: Offset?,
    val revealAnimationTarget: AnimatableMediaItem?,
    val bounceAnimationManager: HexCellBounceAnimationManager,
    val setClickedMedia: (Media?) -> Unit,
    val setClickedHexCell: (HexCell?) -> Unit,
    val setRipplePosition: (Offset?) -> Unit,
    val setRevealAnimationTarget: (AnimatableMediaItem?) -> Unit
)

/**
 * Composable function that manages all state and side effects for media hex visualization.
 * Handles animation cleanup, selection management, and visible cell monitoring.
 */
@Composable
fun rememberMediaHexState(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    selectedMedia: Media?,
    onVisibleCellsChanged: (List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia>) -> Unit,
    provideZoom: () -> Float,
    provideOffset: () -> Offset
): MediaHexState {
    val geometryReader = remember { GeometryReader() }
    var clickedMedia by remember { mutableStateOf<Media?>(null) }
    var clickedHexCell by remember { mutableStateOf<HexCell?>(null) }
    var ripplePosition by remember { mutableStateOf<Offset?>(null) }
    var revealAnimationTarget by remember { mutableStateOf<AnimatableMediaItem?>(null) }

    // Animation manager for media items
    val animationManager = remember { AnimatableMediaManager() }
    
    // Bounce animation manager for hex cell Material 3 bounce animations
    val bounceAnimationManager = rememberHexCellBounceAnimationManager()

    // Remember coroutine scope for immediate animation triggering
    val animationScope = rememberCoroutineScope()

    // Capture latest callback to avoid restarting effect when callback changes
    val currentOnVisibleCellsChanged by rememberUpdatedState(onVisibleCellsChanged)

    // Return early if no layout data
    if (hexGridLayout.hexCellsWithMedia.isEmpty()) {
        return MediaHexState(
            animationManager = animationManager,
            geometryReader = geometryReader,
            clickedMedia = null,
            clickedHexCell = null,
            ripplePosition = null,
            revealAnimationTarget = null,
            bounceAnimationManager = bounceAnimationManager,
            setClickedMedia = { clickedMedia = it },
            setClickedHexCell = { clickedHexCell = it },
            setRipplePosition = { ripplePosition = it },
            setRevealAnimationTarget = { revealAnimationTarget = it }
        )
    }

    // Clean up unused animations when layout changes
    LaunchedEffect(hexGridLayout) {
        val currentMediaSet = hexGridLayout.hexCellsWithMedia.flatMap { it.mediaItems.map { it.media } }.toSet()
        animationManager.cleanupUnused(currentMediaSet)
    }

    // Track current and previous selected media for targeted animations
    var currentSelectedItem by remember { mutableStateOf<AnimatableMediaItem?>(null) }
    var previousSelectedItem by remember { mutableStateOf<AnimatableMediaItem?>(null) }

    // Handle selection changes - only animate affected items
    LaunchedEffect(selectedMedia) {
        // Store previous selection
        previousSelectedItem = currentSelectedItem

        // Update current selection using the animation manager
        currentSelectedItem = selectedMedia?.let { media ->
            // Find the MediaWithPosition for this media
            val mediaWithPosition = hexGridLayout.hexCellsWithMedia
                .flatMap { it.mediaItems }
                .find { it.media == media }
            
            // Get or create AnimatableMediaItem for proper animation setup
            mediaWithPosition?.let { 
                animationManager.getOrCreateAnimatable(it).apply {
                    updateSelection(true)
                }
            }
        }

        // Update previous selection state
        previousSelectedItem?.updateSelection(false)

        // Animate both items concurrently
        listOf(currentSelectedItem, previousSelectedItem).forEach { item ->
            item?.let {
                launch { it.animateToSelectionState() }
            }
        }
    }

    // Layout change cleanup effect
    LaunchedEffect(hexGridLayout) {
        geometryReader.clear()
        clickedMedia = null
        clickedHexCell = null

        // Clean up reveal animation immediately on layout change
        if (revealAnimationTarget != null) {
            val allAnimatableItems = hexGridLayout.hexCellsWithMedia.flatMap { cell ->
                cell.mediaItems.map { mediaWithPos ->
                    animationManager.getOrCreateAnimatable(mediaWithPos)
                }
            }

            allAnimatableItems.forEach { item ->
                launch { item.resetRevealState() }
            }
        }

        revealAnimationTarget = null
    }

    // Monitor zoom/offset changes and report visible cells
    LaunchedEffect(hexGridLayout, currentOnVisibleCellsChanged) {
        snapshotFlow {
            provideZoom() to provideOffset()
        }.distinctUntilChanged()
        .collect { (zoom, offset) ->
            val visibleCells = calculateVisibleCells(
                hexGridLayout = hexGridLayout,
                zoom = zoom,
                offset = offset
            )
            currentOnVisibleCellsChanged(visibleCells)
        }
    }

    return MediaHexState(
        animationManager = animationManager,
        geometryReader = geometryReader,
        clickedMedia = clickedMedia,
        clickedHexCell = clickedHexCell,
        ripplePosition = ripplePosition,
        revealAnimationTarget = revealAnimationTarget,
        bounceAnimationManager = bounceAnimationManager,
        setClickedMedia = { clickedMedia = it },
        setClickedHexCell = { clickedHexCell = it },
        setRipplePosition = { ripplePosition = it },
        setRevealAnimationTarget = { revealAnimationTarget = it }
    )
}

/**
 * Calculates which hex cells are visible in the current viewport.
 * This is called from LaunchedEffect and has access to current zoom/offset/viewport state.
 */
private fun calculateVisibleCells(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    zoom: Float,
    offset: Offset
): List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia> {
    // Simple approach: return all cells for now
    // TODO: Implement actual viewport intersection based on zoom/offset
    return hexGridLayout.hexCellsWithMedia
}

/**
 * Extension function to create MediaInputConfig from MediaHexState.
 * Eliminates duplication by reusing state properties.
 */
fun MediaHexState.toInputConfig(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    provideZoom: () -> Float,
    provideOffset: () -> Offset,
    selectedMedia: Media? = null,
    onMediaClicked: (Media) -> Unit = {},
    onHexCellClicked: (HexCell) -> Unit = {},
    onFocusRequested: (Rect) -> Unit = {},
    cellFocusManager: CellFocusManager? = null
) = MediaInputConfig(
    hexGridLayout = hexGridLayout,
    animationManager = animationManager,
    geometryReader = geometryReader,
    provideZoom = provideZoom,
    provideOffset = provideOffset,
    selectedMedia = selectedMedia,
    onMediaClicked = onMediaClicked,
    onHexCellClicked = onHexCellClicked,
    onFocusRequested = onFocusRequested,
    onRipplePosition = setRipplePosition,
    onClickedMedia = setClickedMedia,
    onClickedHexCell = setClickedHexCell,
    onRevealAnimationTarget = setRevealAnimationTarget,
    cellFocusManager = cellFocusManager,
    bounceAnimationManager = bounceAnimationManager
)
