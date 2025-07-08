package dev.serhiiyaremych.lumina.ui

/**
 * Shared zoom constants for the transformable content system.
 * These values define the minimum and maximum zoom levels allowed throughout the application.
 * Updated to align with 8-level LOD system (LEVEL_0 to LEVEL_7).
 */
object ZoomConstants {
    const val MIN_ZOOM = 0.1f
    const val MAX_ZOOM = 16f // Covers all 8 LOD levels (0.0f-16.0f range)
}
