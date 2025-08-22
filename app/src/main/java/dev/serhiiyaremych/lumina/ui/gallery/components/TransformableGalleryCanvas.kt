package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.ui.GridCanvas
import dev.serhiiyaremych.lumina.ui.GridCanvasState
import dev.serhiiyaremych.lumina.ui.HexGridRenderer
import dev.serhiiyaremych.lumina.ui.MediaHexState
import dev.serhiiyaremych.lumina.ui.SelectionMode
import dev.serhiiyaremych.lumina.ui.TransformableContent
import dev.serhiiyaremych.lumina.ui.TransformableState
import dev.serhiiyaremych.lumina.ui.calculateCellFocusBounds
import dev.serhiiyaremych.lumina.ui.CellFocusHandler
import dev.serhiiyaremych.lumina.ui.CellFocusListener
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import dev.serhiiyaremych.lumina.ui.MediaHexVisualization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun TransformableGalleryCanvas(
    uiState: GalleryUiState,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    transformableState: TransformableState,
    gridState: GridCanvasState,
    hexGridRenderer: HexGridRenderer,
    cellFocusHandler: CellFocusHandler,
    cellFocusListener: CellFocusListener,
    coroutineScope: CoroutineScope,
    mediaHexState: MediaHexState?,
    onVisibleCellsChanged: (List<HexCellWithMedia>) -> Unit,
    modifier: Modifier = Modifier
) {
    TransformableContent(
        modifier = modifier.fillMaxSize(),
        state = transformableState
    ) {
        GridCanvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTagsAsResourceId = true
                    contentDescription = "Streaming gallery canvas"
                }
                .testTag(BenchmarkLabels.GALLERY_CANVAS_TEST_TAG),
            zoom = transformableState.zoom,
            offset = transformableState.offset,
            state = gridState,
            isLoading = uiState.isLoadingWithCooldown
        ) {
            // Draw media visualization with streaming atlas
            uiState.hexGridLayout?.let { layout ->
                mediaHexState?.let { state ->
                    // Debug: Log what atlases we're passing to the UI
                    Log.d("StreamingApp", "Passing to UI: ${uiState.availableAtlases.keys.map { "${it.name}(${uiState.availableAtlases[it]?.size ?: 0})" }}")

                    MediaHexVisualization(
                        hexGridLayout = layout,
                        hexGridRenderer = hexGridRenderer,
                        provideZoom = { transformableState.zoom },
                        provideOffset = { transformableState.offset },
                        streamingAtlases = uiState.availableAtlases,
                        selectedMedia = uiState.selectedMedia,
                        onMediaClicked = { media ->
                            Log.d("StreamingApp", "Media clicked: ${media.displayName}")
                            if (uiState.selectedMedia == media) {
                                Log.d("StreamingApp", "Deselecting media: ${media.displayName}")
                                streamingGalleryViewModel.updateSelectionMode(SelectionMode.CELL_MODE)
                                streamingGalleryViewModel.updateSelectedMedia(null)
                            } else {
                                streamingGalleryViewModel.updateSelectionMode(SelectionMode.PHOTO_MODE)
                                streamingGalleryViewModel.updateSelectedMedia(media)
                                Log.d("StreamingApp", "Selection mode: PHOTO_MODE (canvas click)")
                            }
                        },
                        onHexCellClicked = { hexCell ->
                            Log.d("StreamingApp", "Hex cell clicked: (${hexCell.q}, ${hexCell.r})")

                            // Clear selected media immediately when any hex cell is clicked
                            streamingGalleryViewModel.updateSelectedMedia(null)
                            streamingGalleryViewModel.updateSelectionMode(SelectionMode.CELL_MODE)
                            Log.d("StreamingApp", "Cleared selected media on hex cell click")

                            // Find the HexCellWithMedia for this clicked cell
                            layout.hexCellsWithMedia.find { it.hexCell == hexCell }?.let { hexCellWithMedia ->
                                cellFocusHandler.onCellFocused(hexCellWithMedia, 1.0f) // Max coverage for manual clicks
                            }
                        },
                        onFocusRequested = { bounds ->
                            Log.d("StreamingApp", "Focus requested: $bounds")
                            coroutineScope.launch {
                                // Canvas focus uses small padding in PHOTO_MODE, larger padding in CELL_MODE
                                val padding = if (uiState.selectionMode == SelectionMode.PHOTO_MODE) 4.dp else 64.dp
                                transformableState.focusOn(bounds, padding = padding)
                            }
                        },
                        onVisibleCellsChanged = { visibleCells ->
                            onVisibleCellsChanged(visibleCells)
                            Log.d("StreamingApp", "Visible cells changed: ${visibleCells.size} cells")
                            streamingGalleryViewModel.onVisibleCellsChanged(
                                visibleCells = visibleCells,
                                currentZoom = transformableState.zoom,
                                selectedMedia = uiState.selectedMedia,
                                selectionMode = uiState.selectionMode,
                                activeCell = uiState.selectedCellWithMedia
                            )
                        },
                        cellFocusListener = cellFocusListener,
                        cellFocusConfig = dev.serhiiyaremych.lumina.ui.CellFocusConfig(
                            significanceThreshold = 0.35f, // Match viewport config threshold
                            debugLogging = true
                        ),
                        onClearClickedMedia = {
                            Log.d("CellFocus", "Clearing clicked media state for red outline")
                        },
                        externalState = state,
                        significantCells = uiState.selectedCellWithMedia?.let { setOf(it.hexCell) } ?: emptySet()
                    )
                }
            }
        }
    }
}
