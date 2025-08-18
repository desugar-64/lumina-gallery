package dev.serhiiyaremych.lumina.domain.model

/**
 * Type-safe atlas generation priorities that determine photo processing quality and resource allocation.
 *
 * This system provides clear semantics about what each priority level means and how it affects
 * atlas generation, with compile-time safety and no magic numbers.
 *
 * Features:
 * ✅ Type-safe priority classes with clear semantics
 * ✅ PhotoQuality levels that map to processing resolution
 * ✅ TypeSafeLODPriority for atlas generation coordination
 * ✅ Reactive region system integration
 * ✅ Compile-time safety - no more magic numbers
 */
sealed class AtlasPriority {
    /**
     * The photo priority level that determines processing quality
     */
    abstract val photoQuality: PhotoQuality

    /**
     * Human-readable description of this priority
     */
    abstract val description: String

    /**
     * Persistent cache priority - used for all canvas photos at LEVEL_0 during app startup.
     * These photos are cached permanently for immediate UI feedback.
     */
    data object PersistentCache : AtlasPriority() {
        override val photoQuality = PhotoQuality.STANDARD
        override val description = "Persistent cache - ALL canvas photos"
    }

    /**
     * Visible cells priority - used for photos currently visible in viewport.
     * Quality matches the appropriate LOD level for current zoom.
     */
    data object VisibleCells : AtlasPriority() {
        override val photoQuality = PhotoQuality.STANDARD
        override val description = "Visible cells at current zoom"
    }

    /**
     * Active cell priority - used for the cell that takes most viewport area.
     * Gets enhanced quality (+1 LOD level above visible cells).
     */
    data object ActiveCell : AtlasPriority() {
        override val photoQuality = PhotoQuality.ENHANCED
        override val description = "Active cell enhancement"
    }

    /**
     * Selected photo priority - used for explicitly selected photos.
     * Always gets maximum quality regardless of zoom level.
     */
    data object SelectedPhoto : AtlasPriority() {
        override val photoQuality = PhotoQuality.MAXIMUM
        override val description = "Selected photo maximum quality"
    }
}

/**
 * Photo quality levels that determine processing resolution and atlas placement
 */
enum class PhotoQuality {
    /**
     * Standard quality - uses the requested LOD level as-is
     */
    STANDARD,

    /**
     * Enhanced quality - uses +1 LOD level above requested (capped at LEVEL_7)
     */
    ENHANCED,

    /**
     * Maximum quality - always uses LEVEL_7 regardless of requested LOD
     */
    MAXIMUM;

    /**
     * Convert to PhotoPriority for atlas generation
     */
    fun toPhotoPriority(): PhotoPriority = when (this) {
        STANDARD -> PhotoPriority.NORMAL
        ENHANCED -> PhotoPriority.HIGH
        MAXIMUM -> PhotoPriority.HIGH
    }

    /**
     * Calculate effective LOD level based on quality and requested LOD
     */
    fun calculateEffectiveLOD(requestedLOD: LODLevel): LODLevel = when (this) {
        STANDARD -> requestedLOD
        ENHANCED -> {
            // +1 LOD level, capped at maximum
            val currentIndex = LODLevel.entries.indexOf(requestedLOD)
            val enhancedIndex = (currentIndex + 1).coerceAtMost(LODLevel.entries.size - 1)
            LODLevel.entries[enhancedIndex]
        }
        MAXIMUM -> LODLevel.entries.last() // Always LEVEL_7
    }
}

/**
 * Type-safe LOD priority with clear semantics and photo quality determination
 */
data class TypeSafeLODPriority(
    val priority: AtlasPriority,
    val photos: List<Media.Image>,
    val reason: String
) {
    /**
     * Create priority mapping for all photos with consistent quality
     */
    fun createPriorityMapping(): Map<android.net.Uri, PhotoPriority> {
        val photoPriority = priority.photoQuality.toPhotoPriority()
        return photos.associate { it.uri to photoPriority }
    }
}
