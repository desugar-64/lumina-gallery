package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.serhiiyaremych.lumina.ui.SelectionMode
import dev.serhiiyaremych.lumina.ui.SimpleViewportConfig
import dev.serhiiyaremych.lumina.ui.SimpleViewportState
import dev.serhiiyaremych.lumina.ui.calculateSimpleViewportRect
import dev.serhiiyaremych.lumina.ui.determineSuggestedSelectionMode
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import dev.serhiiyaremych.lumina.ui.shouldDeselectMedia

@Composable
fun viewportStateManager(
    uiState: GalleryUiState,
    canvasSize: Size,
    zoom: Float,
    offset: Offset,
    viewportConfig: SimpleViewportConfig,
    onSelectionModeChanged: (SelectionMode) -> Unit,
    onMediaDeselected: () -> Unit
): SimpleViewportState? {
    // Unified viewport state monitoring
    val zoomProvider = rememberUpdatedState { zoom }
    val offsetProvider = rememberUpdatedState { offset }

    // Single source of truth for viewport state using derivedStateOf
    val simpleViewportState by remember(uiState.hexGridLayout, canvasSize, zoomProvider.value(), offsetProvider.value()) {
        derivedStateOf {
            uiState.hexGridLayout?.let { layout ->
                SimpleViewportState(
                    viewportRect = calculateSimpleViewportRect(canvasSize, zoomProvider.value(), offsetProvider.value()),
                    canvasSize = canvasSize,
                    zoom = zoomProvider.value(),
                    offset = offsetProvider.value(),
                    focusedCell = uiState.focusedCellWithMedia,
                    selectedMedia = uiState.selectedMedia,
                    selectionMode = uiState.selectionMode,
                    gridBounds = layout?.bounds
                )
            }
        }
    }

    // Derived state for center button visibility
    val showCenterButton by remember(simpleViewportState) {
        derivedStateOf {
            simpleViewportState?.let { state ->
                state.gridBounds?.let { gridBounds ->
                    !state.viewportRect.overlaps(gridBounds)
                } ?: false
            } ?: false
        }
    }

    LaunchedEffect(simpleViewportState) {
        simpleViewportState?.let { state ->
            // Apply selection mode changes
            val suggestedMode = determineSuggestedSelectionMode(
                cell = state.focusedCell,
                canvasSize = state.canvasSize,
                zoom = state.zoom,
                offset = state.offset,
                currentMode = uiState.selectionMode,
                hasSelectedMedia = state.selectedMedia != null,
                config = viewportConfig
            )
            if (suggestedMode != uiState.selectionMode) {
                onSelectionModeChanged(suggestedMode)
                Log.d("StreamingApp", "Selection mode changed: $suggestedMode (viewport-based)")
            }

            // Apply media deselection when out of viewport
            val shouldDeselect = shouldDeselectMedia(
                cell = state.focusedCell,
                selectedMedia = state.selectedMedia,
                viewportRect = state.viewportRect,
                config = viewportConfig
            )
            if (shouldDeselect && uiState.selectedMedia != null) {
                onMediaDeselected()
                Log.d("StreamingApp", "Media deselected: out of viewport")
            }
        }
    }

    return simpleViewportState
}
