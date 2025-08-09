package dev.serhiiyaremych.lumina.domain.model

/**
 * Defines priority levels for photo rendering in the atlas system.
 *
 * Higher priority photos receive better quality treatment:
 * - Higher LOD levels for improved visual quality
 * - Dedicated atlas placement for optimal rendering
 * - Priority processing in atlas generation pipeline
 */
enum class PhotoPriority {
    /**
     * High priority photos requiring enhanced quality.
     * Examples: Currently selected photos, favorites, recently viewed items.
     *
     * Treatment:
     * - Boosted LOD level (+1 or +2 from zoom-based calculation)
     * - Dedicated atlas groups for optimal quality
     * - Priority processing during atlas generation
     */
    HIGH,

    /**
     * Normal priority photos using standard quality levels.
     * The default priority for most photos in the gallery.
     *
     * Treatment:
     * - Standard zoom-based LOD level calculation
     * - Standard atlas distribution and grouping
     * - Regular processing pipeline
     */
    NORMAL
}
