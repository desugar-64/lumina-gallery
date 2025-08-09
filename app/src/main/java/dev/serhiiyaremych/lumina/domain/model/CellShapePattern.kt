package dev.serhiiyaremych.lumina.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.random.Random

/**
 * Defines different organic shape patterns for arranging photos within hex cells.
 * Each pattern creates a unique visual "personality" for the cell while maintaining
 * the natural scattered photo aesthetic.
 */
enum class CellShapePattern {
    /**
     * Photos follow a gentle spiral path outward from center.
     * Creates energetic, dynamic feeling.
     */
    LOOSE_SPIRAL,

    /**
     * Photos arranged in organic C or S-shaped curves.
     * Creates flowing, graceful arrangement.
     */
    CURVED_ARC,

    /**
     * Dense center with photos radiating outward.
     * Creates cozy, clustered feeling.
     */
    IRREGULAR_CLUSTER,

    /**
     * Photos follow wavy, river-like paths.
     * Creates natural, organic flow.
     */
    FLOWING_LINE,

    /**
     * Photos loosely form circular or oval boundaries.
     * Creates contained, balanced arrangement.
     */
    SCATTERED_CIRCLE,

    /**
     * Photos spread like an opened hand or flower petals.
     * Creates radiating, expansive feeling.
     */
    FAN_PATTERN
}

/**
 * Configuration for shape generation within a hex cell.
 * Controls how strictly the pattern is followed and visibility constraints.
 */
data class ShapeGenerationConfig(
    /**
     * The shape pattern to use for this cell.
     */
    val pattern: CellShapePattern,

    /**
     * How strictly to follow the pattern (0.0 = completely random, 1.0 = strict pattern).
     * Default 0.7 provides good balance between pattern and natural randomness.
     */
    val intensity: Float = 0.7f,

    /**
     * Minimum visible area fraction for each photo (0.25 = 25% minimum visibility).
     * Ensures "breathing room" so no photo is completely hidden.
     */
    val breathingRoomFactor: Float = 0.25f,

    /**
     * Maximum attempts to adjust position for breathing room before giving up.
     * Prevents infinite loops in complex overlapping scenarios.
     */
    val maxAdjustmentAttempts: Int = 5
) {
    init {
        require(intensity in 0.0f..1.0f) { "Intensity must be between 0.0 and 1.0" }
        require(breathingRoomFactor in 0.1f..0.5f) { "Breathing room factor must be between 0.1 and 0.5" }
        require(maxAdjustmentAttempts > 0) { "Max adjustment attempts must be positive" }
    }
}

/**
 * Result of shape pattern generation for a hex cell.
 * Contains base positions that will be further refined with breathing room constraints.
 */
data class ShapePatternResult(
    /**
     * Base positions for each media item following the shape pattern.
     * These positions may be adjusted later to ensure breathing room.
     */
    val basePositions: List<Offset>,

    /**
     * The pattern that was used to generate these positions.
     */
    val pattern: CellShapePattern,

    /**
     * Metadata about the generation process (for debugging).
     */
    val metadata: ShapePatternMetadata
)

/**
 * Metadata about the shape pattern generation process.
 * Useful for debugging and performance monitoring.
 */
data class ShapePatternMetadata(
    /**
     * The random seed used for pattern generation.
     */
    val seed: Int,

    /**
     * Number of media items positioned.
     */
    val mediaCount: Int,

    /**
     * The hex cell bounds used for positioning.
     */
    val hexBounds: Rect,

    /**
     * Pattern-specific parameters (varies by pattern type).
     */
    val patternParams: Map<String, Any> = emptyMap()
)

/**
 * Selects a random shape pattern based on media count.
 * Uses deterministic selection to ensure consistent results for the same input.
 */
fun selectShapePattern(
    mediaCount: Int,
    random: Random
): CellShapePattern = when (mediaCount) {
    1 -> CellShapePattern.IRREGULAR_CLUSTER // Single photo works best as cluster
    2 -> if (random.nextBoolean()) CellShapePattern.CURVED_ARC else CellShapePattern.FLOWING_LINE
    3, 4 -> {
        val patterns = listOf(
            CellShapePattern.LOOSE_SPIRAL,
            CellShapePattern.CURVED_ARC,
            CellShapePattern.IRREGULAR_CLUSTER
        )
        patterns[random.nextInt(patterns.size)]
    }
    else -> {
        // For 5+ photos, all patterns are viable
        val allPatterns = CellShapePattern.entries
        allPatterns[random.nextInt(allPatterns.size)]
    }
}
