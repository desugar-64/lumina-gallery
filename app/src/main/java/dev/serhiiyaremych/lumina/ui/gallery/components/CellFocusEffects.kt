package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.ui.SelectionMode
import dev.serhiiyaremych.lumina.ui.TransformableState
import dev.serhiiyaremych.lumina.ui.calculateCellFocusBounds
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import kotlinx.coroutines.launch

/**
 * Data class representing cell focus events
 */
data class CellFocusEvent(
    val type: CellFocusEventType,
    val hexCellWithMedia: HexCellWithMedia,
    val coverage: Float = 0f
)

/**
 * Enum representing cell focus event types
 */
enum class CellFocusEventType {
    CELL_FOCUSED,
    CELL_UNFOCUSED
}

@Composable
fun HandleCellFocusEffects(
    cellFocusEvents: List<CellFocusEvent>,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    transformableState: TransformableState,
    onEventsHandled: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState = streamingGalleryViewModel.uiState.value

    LaunchedEffect(cellFocusEvents) {
        cellFocusEvents.forEach { event ->
            when (event.type) {
                CellFocusEventType.CELL_FOCUSED -> {
                    val hexCellWithMedia = event.hexCellWithMedia
                    val isManualClick = event.coverage >= 1.0f
                    Log.d("CellFocus", "Cell FOCUSED: (${hexCellWithMedia.hexCell.q}, ${hexCellWithMedia.hexCell.r}) coverage=${String.format("%.2f", event.coverage)} manual=$isManualClick")

                    // Calculate cell focus actions using pure function
                    val actions = calculateCellFocusActions(
                        hexCellWithMedia = hexCellWithMedia,
                        currentSelectedMedia = uiState.selectedMedia,
                        currentSelectionMode = uiState.selectionMode
                    )

                    // Update selected cell with media (unified state)
                    streamingGalleryViewModel.updateSelectedCellWithMedia(actions.updateFocusedCell)

                    // Clear selected media only if needed
                    if (actions.clearSelectedMedia) {
                        streamingGalleryViewModel.updateSelectedMedia(null)
                    }

                    // Set selection mode only if it changed
                    if (actions.updateSelectionMode != uiState.selectionMode) {
                        streamingGalleryViewModel.updateSelectionMode(actions.updateSelectionMode)
                        Log.d("CellFocus", "Selection mode: ${actions.updateSelectionMode} (focused cell, manual=$isManualClick)")
                    }

                    // Trigger focus animation for both manual clicks AND auto-detection (like original)
                    actions.triggerFocusAnimation?.let { cellBounds ->
                        Log.d("CellFocus", "Triggering focus animation to bounds: $cellBounds (${if (isManualClick) "manual click" else "auto-detection"})")
                        coroutineScope.launch {
                            // Cell focus uses larger padding for comfortable cell viewing
                            transformableState.focusOn(cellBounds, padding = actions.focusPadding)
                        }
                    }
                }

                CellFocusEventType.CELL_UNFOCUSED -> {
                    val hexCellWithMedia = event.hexCellWithMedia
                    val hexCell = hexCellWithMedia.hexCell
                    Log.d("CellFocus", "Cell UNFOCUSED: (${hexCell.q}, ${hexCell.r})")

                    // Check if this was the selected cell and clear if so
                    if (uiState.selectedCellWithMedia?.hexCell == hexCell) {
                        Log.d("CellFocus", "Clearing selected cell with media: (${hexCell.q}, ${hexCell.r})")
                        streamingGalleryViewModel.updateSelectedCellWithMedia(null)
                    }
                }
            }
        }

        // Clear processed events to prevent re-processing
        if (cellFocusEvents.isNotEmpty()) {
            onEventsHandled()
        }
    }
}
