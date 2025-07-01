package dev.serhiiyaremych.lumina

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
        
        setContent {
            LuminaGalleryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(
                        modifier = Modifier.padding(innerPadding),
                        galleryViewModel = galleryViewModel
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
}
