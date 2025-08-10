package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.ui.OffscreenIndicatorManager
import dev.serhiiyaremych.lumina.ui.SimpleViewportState
import dev.serhiiyaremych.lumina.ui.TransformableState
import dev.serhiiyaremych.lumina.ui.components.DirectionalIndicatorOverlay
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun GalleryDirectionalIndicators(
    simpleViewportState: SimpleViewportState?,
    offscreenIndicatorManager: OffscreenIndicatorManager,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    transformableState: TransformableState,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    simpleViewportState?.let { state ->
        val indicators = if (state.gridBounds != null) {
            offscreenIndicatorManager.calculateIndicators(
                viewportRect = state.viewportRect,
                canvasSize = state.canvasSize,
                gridBounds = state.gridBounds
            )
        } else {
            emptyList()
        }

        DirectionalIndicatorOverlay(
            indicators = indicators,
            onIndicatorClick = { indicator ->
                Log.d("DirectionalIndicator", "Indicator clicked for bounds: ${indicator.contentBounds}")
                // Close panel first to avoid conflicts
                streamingGalleryViewModel.updateFocusedCell(null)

                // Navigate to the offscreen content
                if (!transformableState.isAnimating) {
                    coroutineScope.launch {
                        transformableState.stopAllAnimations()
                        transformableState.focusOn(indicator.contentBounds, padding = 32.dp)
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
