package dev.serhiiyaremych.lumina.ui.animation

/**
 * Shared animation constants for consistent timing across the app.
 */
object AnimationConstants {
    /**
     * Standard animation duration for focus and reveal animations.
     * Used by:
     * - Focus manager (transformable state focus animations)
     * - Reveal animations (photo shuffle and fade effects)
     * - Selection state animations
     */
    const val ANIMATION_DURATION_MS = 400
    
    /**
     * Fast animation duration for quick transitions.
     * Used for cleanup and reset animations.
     */
    const val FAST_ANIMATION_DURATION_MS = 200
    
    /**
     * Alpha value for faded photos in pile shuffle animation.
     */
    const val PILE_SHUFFLE_ALPHA = 0.3f
}