package dev.serhiiyaremych.lumina

import android.os.Bundle
import android.view.Choreographer
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.ui.App
import dev.serhiiyaremych.lumina.ui.gallery.GalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val galleryViewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup atlas idleness tracking for benchmarking
        launchAtlasIdlenessTracking(galleryViewModel)

        // Setup compose idleness tracking for benchmarking
        launchIdlenessTracking()

        // Check for benchmark mode
        val isBenchmarkMode = intent.getBooleanExtra(BenchmarkLabels.BENCHMARK_MODE_EXTRA, false)
        val autoZoom = intent.getBooleanExtra(BenchmarkLabels.BENCHMARK_AUTO_ZOOM, false)

        setContent {
            LuminaGalleryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(
                        modifier = Modifier.padding(innerPadding),
                        galleryViewModel = galleryViewModel,
                        isBenchmarkMode = isBenchmarkMode,
                        autoZoom = autoZoom,
                    )
                }
            }
        }
    }

    /**
     * Launch atlas idleness tracking for benchmarking.
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
