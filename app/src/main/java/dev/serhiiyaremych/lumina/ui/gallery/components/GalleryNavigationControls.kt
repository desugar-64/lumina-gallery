package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.ui.TransformableState
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun GalleryNavigationControls(
    uiState: GalleryUiState,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    transformableState: TransformableState,
    coroutineScope: CoroutineScope,
    showCenterButton: Boolean,
    modifier: Modifier = Modifier
) {
    if (showCenterButton) {
        Box(modifier = modifier) {
            FloatingActionButton(
                onClick = {
                    Log.d("CenterButton", "Tracking button clicked")
                    uiState.hexGridLayout?.let { layout ->
                        // Calculate the bounds that encompass all hex cells
                        val gridBounds = layout.bounds

                        Log.d("CenterButton", "Grid bounds: $gridBounds, isEmpty: ${gridBounds.isEmpty}")

                        // Safety check to ensure we have valid bounds and no animation is in progress
                        if (!gridBounds.isEmpty && !transformableState.isAnimating) {
                            // Close panel first to avoid conflicts
                            streamingGalleryViewModel.updateFocusedCell(null)

                            // Use focusOn with padding to center and fit the entire grid
                            coroutineScope.launch {
                                Log.d("CenterButton", "Launching focusOn animation")
                                // Stop any ongoing fling animation before starting focus animation
                                transformableState.stopAllAnimations()
                                transformableState.focusOn(gridBounds, padding = 32.dp)
                                Log.d("CenterButton", "FocusOn animation completed")
                            }
                        } else {
                            Log.w("CenterButton", "Skipping focusOn: bounds empty=${gridBounds.isEmpty}, animating=${transformableState.isAnimating}")
                        }
                    } ?: Log.w("CenterButton", "Skipping focusOn due to null layout")
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(
                    text = "🎯",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}
