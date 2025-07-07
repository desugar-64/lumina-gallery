package dev.serhiiyaremych.lumina.domain.usecase

import android.net.Uri
import androidx.compose.ui.unit.IntSize
import dev.serhiiyaremych.lumina.data.ScaleStrategy
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that makes EnhancedAtlasGenerator compatible with the existing AtlasGenerator interface.
 * This allows seamless integration with AtlasManager without changing its existing code.
 */
@Singleton
class EnhancedAtlasAdapter @Inject constructor(
    private val enhancedAtlasGenerator: EnhancedAtlasGenerator
) {
    
    /**
     * Delegates to enhanced atlas generator with backward compatibility
     */
    suspend fun generateAtlas(
        photoUris: List<Uri>,
        lodLevel: LODLevel,
        atlasSize: IntSize = IntSize(2048, 2048),
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER
    ): AtlasGenerationResult {
        return enhancedAtlasGenerator.generateAtlas(photoUris, lodLevel, atlasSize, scaleStrategy)
    }
}