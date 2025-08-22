package dev.serhiiyaremych.lumina.ui.gallery

import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.HexGridLayout
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.domain.usecase.GroupingPeriod
import dev.serhiiyaremych.lumina.ui.SelectionMode
import java.time.LocalDate

/**
 * Unified UI State for Gallery following UDF (Unidirectional Data Flow) architecture.
 *
 * This single data class represents the complete UI state, eliminating the need for
 * multiple StateFlow subscriptions and providing a clear, predictable state structure.
 *
 * Based on Android's recommended UDF architecture:
 * https://developer.android.com/develop/ui/compose/architecture
 */
data class GalleryUiState(
    // Media Data State
    val media: List<Media> = emptyList(),
    val groupedMedia: Map<LocalDate, List<Media>> = emptyMap(),
    val groupingPeriod: GroupingPeriod = GroupingPeriod.MONTHLY,

    // Layout State
    val hexGridLayout: HexGridLayout? = null,

    // Atlas State - Streaming System
    val streamingAtlases: Map<LODLevel, List<TextureAtlas>> = emptyMap(),
    val persistentCache: List<TextureAtlas>? = null,

    // Atlas Generation State
    val isAtlasGenerating: Boolean = false,
    val atlasGenerationStatus: String? = null,

    // Extended Loading State with Cooldown (for smooth animation)
    val isLoadingWithCooldown: Boolean = false,

    // Selection State
    val selectedMedia: Media? = null,
    val selectionMode: SelectionMode = SelectionMode.CELL_MODE,
    val selectedCellWithMedia: HexCellWithMedia? = null,

    // Permission State
    val permissionGranted: Boolean = false,

    // Loading State
    val isLoading: Boolean = false,
    val error: String? = null
) {
    companion object {
        /**
         * Initial state when the app starts
         */
        fun initial() = GalleryUiState(
            isLoading = true
        )

        /**
         * Loading state during media loading
         */
        fun loading() = GalleryUiState(
            isLoading = true
        )

        /**
         * Error state when something goes wrong
         */
        fun error(message: String) = GalleryUiState(
            error = message,
            isLoading = false
        )
    }

    // Derived state properties for convenience

    /**
     * Whether the app has media and layout ready for display
     */
    val isContentReady: Boolean
        get() = media.isNotEmpty() && hexGridLayout != null && permissionGranted

    /**
     * Whether atlas generation is in progress for UI feedback
     */
    val showAtlasProgress: Boolean
        get() = isAtlasGenerating && atlasGenerationStatus != null

    /**
     * Best available atlases for rendering (non-empty streaming atlases or persistent cache)
     */
    val availableAtlases: Map<LODLevel, List<TextureAtlas>>
        get() = if (streamingAtlases.isNotEmpty()) {
            streamingAtlases
        } else if (persistentCache != null) {
            mapOf(LODLevel.LEVEL_0 to persistentCache)
        } else {
            emptyMap()
        }

    /**
     * Whether there are any atlases available for rendering
     */
    val hasAtlases: Boolean
        get() = streamingAtlases.isNotEmpty() || persistentCache != null

    /**
     * Whether focused cell panel should be shown
     */
    val showFocusedCellPanel: Boolean
        get() = selectedCellWithMedia != null && isContentReady

    /**
     * Summary text for current atlas state (for debugging)
     */
    val atlasStateSummary: String
        get() = when {
            streamingAtlases.isNotEmpty() -> {
                val summary = streamingAtlases.entries.joinToString(", ") { (lod, atlases) ->
                    "${lod.name}(${atlases.sumOf { it.photoCount }})"
                }
                "Streaming: $summary"
            }
            persistentCache != null -> "Cache: L0(${persistentCache.sumOf { it.photoCount }})"
            else -> "No atlases"
        }
}
