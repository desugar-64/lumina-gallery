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
import dev.serhiiyaremych.lumina.ui.shouldDeselectMedia

@Composable
fun viewportStateManager(
    uiState: GalleryUiState,
    canvasSize: Size,
    zoom: Float,
    offset: Offset,
    viewportConfig: SimpleViewportConfig,
    onSelectionModeChanged: (SelectionMode) -> Unit,
    onMediaDeselected: () -> Unit,
    onCellUnfocused: () -> Unit = {}
): SimpleViewportState? {
    // Unified viewport state monitoring
    val zoomProvider = rememberUpdatedState { zoom }
    val offsetProvider = rememberUpdatedState { offset }

    // Single source of truth for viewport state using derivedStateOf
    val simpleViewportState by remember(uiState.hexGridLayout, uiState.selectedCellWithMedia, canvasSize, zoomProvider.value(), offsetProvider.value()) {
        derivedStateOf {
            calculateViewportState(
                uiState = uiState,
                canvasSize = canvasSize,
                zoom = zoomProvider.value(),
                offset = offsetProvider.value()
            )
        }
    }

    // Handle viewport effects
    HandleViewportEffects(
        viewportState = simpleViewportState,
        uiState = uiState,
        viewportConfig = viewportConfig,
        onSelectionModeChanged = onSelectionModeChanged,
        onMediaDeselected = onMediaDeselected,
        onCellUnfocused = onCellUnfocused
    )

    return simpleViewportState
}

@Composable
fun HandleViewportEffects(
    viewportState: SimpleViewportState?,
    uiState: GalleryUiState,
    viewportConfig: SimpleViewportConfig,
    onSelectionModeChanged: (SelectionMode) -> Unit,
    onMediaDeselected: () -> Unit,
    onCellUnfocused: () -> Unit
) {
    LaunchedEffect(viewportState, uiState.selectedMedia, uiState.selectionMode) {
        viewportState?.let { state ->
            // Log current state for debugging
            Log.d("ViewportEffects", "Viewport state - focusedCell: ${state.focusedCell?.hexCell?.q},${state.focusedCell?.hexCell?.r}, selectedMedia: ${state.selectedMedia?.displayName}, uiSelectedMedia: ${uiState.selectedMedia?.displayName}")
            Log.d("ViewportEffects", "shouldDeselectMedia check - cell: ${state.focusedCell != null}, selectedMedia: ${state.selectedMedia != null}, uiSelectedMedia: ${uiState.selectedMedia != null}")

            if (state.focusedCell != null) {
                val coverage = dev.serhiiyaremych.lumina.ui.calculateCellCoverage(state.focusedCell, state.viewportRect)
                Log.d("ViewportEffects", "Cell coverage: $coverage, unfocusThreshold: ${viewportConfig.cellUnfocusThreshold}, minViewportCoverage: ${viewportConfig.minViewportCoverage}")
            }

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

            // Apply cell unfocusing when partially out of viewport
            val shouldUnfocus = dev.serhiiyaremych.lumina.ui.shouldUnfocusCell(
                cell = state.focusedCell,
                viewportRect = state.viewportRect,
                config = viewportConfig
            )

            Log.d("ViewportEffects", "shouldDeselect result: $shouldDeselect, shouldUnfocus: $shouldUnfocus")

            if (shouldDeselect && uiState.selectedMedia != null) {
                Log.d("StreamingApp", "Triggering media deselection: shouldDeselect=$shouldDeselect, uiSelectedMedia=${uiState.selectedMedia?.displayName}")
                onMediaDeselected()
                Log.d("StreamingApp", "Media deselected: out of viewport")
            }

            if (shouldUnfocus && uiState.selectedCellWithMedia != null) {
                val coverage = dev.serhiiyaremych.lumina.ui.calculateCellCoverage(state.focusedCell!!, state.viewportRect)
                Log.d("StreamingApp", "Triggering cell unfocusing: coverage $coverage below ${viewportConfig.cellUnfocusThreshold} threshold")
                // Use targeted callback to only clear focused cell
                onCellUnfocused()
                Log.d("StreamingApp", "Cell unfocused: out of viewport")
            }
        }
    }
}
