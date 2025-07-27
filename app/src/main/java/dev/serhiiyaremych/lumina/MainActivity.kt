package dev.serhiiyaremych.lumina

import android.os.Bundle
import android.view.Choreographer
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.ui.App
import dev.serhiiyaremych.lumina.ui.gallery.GalleryViewModel
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Use streaming atlas system for better performance
    private val streamingGalleryViewModel: StreamingGalleryViewModel by viewModels()

    // Keep legacy for benchmarking comparison if needed
    private val galleryViewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Configure window for proper edge-to-edge behavior
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Setup atlas idleness tracking for benchmarking (using streaming system)
        launchStreamingAtlasIdlenessTracking(streamingGalleryViewModel)

        // Setup compose idleness tracking for benchmarking
        launchIdlenessTracking()

        // Check for benchmark mode
        val isBenchmarkMode = intent.getBooleanExtra(BenchmarkLabels.BENCHMARK_MODE_EXTRA, false)
        val autoZoom = intent.getBooleanExtra(BenchmarkLabels.BENCHMARK_AUTO_ZOOM, false)

        setContent {
            LuminaGalleryTheme {
                // Use streaming atlas system for enhanced performance
                App(
                    modifier = Modifier.fillMaxSize(),
                    streamingGalleryViewModel = streamingGalleryViewModel,
                    isBenchmarkMode = isBenchmarkMode,
                    autoZoom = autoZoom,
                )
            }
        }
    }

    /**
     * Launch streaming atlas idleness tracking for benchmarking.
     * Updates content description to track streaming atlas generation state.
     */
    private fun launchStreamingAtlasIdlenessTracking(viewModel: StreamingGalleryViewModel) {
        val contentView: View = findViewById(android.R.id.content)

        // Track streaming atlas generation state
        lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                contentView.contentDescription = if (uiState.isAtlasGenerating) {
                    BenchmarkLabels.ATLAS_STATE_GENERATING
                } else {
                    BenchmarkLabels.ATLAS_STATE_IDLE
                }
            }
        }
    }

    /**
     * Launch atlas idleness tracking for benchmarking (legacy).
     * Updates content description to track atlas generation state.
     */
    private fun launchAtlasIdlenessTracking(viewModel: GalleryViewModel) {
        val contentView: View = findViewById(android.R.id.content)

        // Track atlas generation state
        lifecycleScope.launch {
            viewModel.isAtlasGenerating.collect { isGenerating ->
                contentView.contentDescription = if (isGenerating) {
                    BenchmarkLabels.ATLAS_STATE_GENERATING
                } else {
                    BenchmarkLabels.ATLAS_STATE_IDLE
                }
            }
        }
    }

    /**
     * Launch Compose idleness tracking for benchmarking.
     * Uses Choreographer to detect when Compose recomposition is idle.
     */
    private fun ComponentActivity.launchIdlenessTracking() {
        val contentView: View = findViewById(android.R.id.content)
        val callback: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (Recomposer.runningRecomposers.value.any { it.hasPendingWork }) {
                    contentView.contentDescription = BenchmarkLabels.COMPOSE_STATE_ANIMATING
                } else {
                    contentView.contentDescription = BenchmarkLabels.COMPOSE_STATE_IDLE
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
    }
}
