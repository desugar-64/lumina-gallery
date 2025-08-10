package dev.serhiiyaremych.lumina.ui.gallery.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.ui.debug.EnhancedDebugOverlay
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel

@Composable
fun GalleryDebugOverlay(
    uiState: GalleryUiState,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    currentZoom: Float,
    modifier: Modifier = Modifier
) {
    EnhancedDebugOverlay(
        atlasState = null, // Legacy atlas state (not used with streaming)
        isAtlasGenerating = uiState.isAtlasGenerating,
        currentZoom = currentZoom,
        memoryStatus = null, // TODO: Get memory status from streaming manager
        smartMemoryManager = streamingGalleryViewModel.getStreamingAtlasManager().getSmartMemoryManager(),
        deviceCapabilities = null, // TODO: Add device capabilities to streaming system
        significantCells = uiState.selectedCell?.let { setOf(it) } ?: emptySet(),
        streamingAtlases = uiState.availableAtlases,
        modifier = modifier.fillMaxSize()
    )
}
