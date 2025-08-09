package dev.serhiiyaremych.lumina.domain.usecase.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.serhiiyaremych.lumina.domain.model.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Base interface for generating shape patterns within hex cells.
 * Each implementation creates a specific organic arrangement of photos.
 */
interface ShapePatternGenerator {
    /**
     * Generates base positions for media items following this pattern.
     *
     * @param mediaCount Number of media items to position
     * @param hexBounds Bounding rectangle of the hex cell
     * @param random Random generator with consistent seed
     * @param config Configuration for shape generation
     * @return Pattern result with base positions and metadata
     */
    fun generatePositions(
        mediaCount: Int,
        hexBounds: Rect,
        random: Random,
        config: ShapeGenerationConfig
    ): ShapePatternResult
}

/**
 * Spiral pattern generator - photos follow gentle spiral outward from center.
 * Creates energetic, dynamic feeling.
 */
class SpiralPatternGenerator : ShapePatternGenerator {

    override fun generatePositions(
        mediaCount: Int,
        hexBounds: Rect,
        random: Random,
        config: ShapeGenerationConfig
    ): ShapePatternResult {
        val positions = mutableListOf<Offset>()
        val centerX = hexBounds.width / 2
        val centerY = hexBounds.height / 2
        val maxRadius = min(hexBounds.width, hexBounds.height) / 2.5f

        // Spiral parameters
        val spiralTightness = 0.5f + random.nextFloat() * 0.3f // 0.5-0.8
        val startAngle = random.nextFloat() * 2 * PI.toFloat()

        for (i in 0 until mediaCount) {
            val progress = i.toFloat() / maxOf(1, mediaCount - 1)
            val angle = startAngle + progress * spiralTightness * 4 * PI.toFloat()
            val radius = progress * maxRadius

            val baseX = centerX + radius * cos(angle)
            val baseY = centerY + radius * sin(angle)

            // Apply intensity - mix pattern with randomness
            val randomX = random.nextFloat() * hexBounds.width
            val randomY = random.nextFloat() * hexBounds.height

            val finalX = baseX * config.intensity + randomX * (1 - config.intensity)
            val finalY = baseY * config.intensity + randomY * (1 - config.intensity)

            positions.add(Offset(finalX, finalY))
        }

        return ShapePatternResult(
            basePositions = positions,
            pattern = CellShapePattern.LOOSE_SPIRAL,
            metadata = ShapePatternMetadata(
                seed = random.nextInt(),
                mediaCount = mediaCount,
                hexBounds = hexBounds,
                patternParams = mapOf(
                    "spiralTightness" to spiralTightness,
                    "startAngle" to startAngle,
                    "maxRadius" to maxRadius
                )
            )
        )
    }
}

/**
 * Arc pattern generator - photos arranged in organic C or S-shaped curves.
 * Creates flowing, graceful arrangement.
 */
class ArcPatternGenerator : ShapePatternGenerator {

    override fun generatePositions(
        mediaCount: Int,
        hexBounds: Rect,
        random: Random,
        config: ShapeGenerationConfig
    ): ShapePatternResult {
        val positions = mutableListOf<Offset>()
        val width = hexBounds.width
        val height = hexBounds.height

        // Arc parameters
        val arcType = if (random.nextBoolean()) ArcType.C_CURVE else ArcType.S_CURVE
        val arcAmplitude = (min(width, height) / 4) * (0.6f + random.nextFloat() * 0.4f)
        val startOffset = random.nextFloat() * 0.3f

        for (i in 0 until mediaCount) {
            val progress = i.toFloat() / maxOf(1, mediaCount - 1)
            val t = startOffset + progress * (1 - 2 * startOffset)

            val (baseX, baseY) = when (arcType) {
                ArcType.C_CURVE -> {
                    val x = width * 0.2f + t * width * 0.6f
                    val y = height * 0.5f + arcAmplitude * sin(t * PI.toFloat())
                    Pair(x, y)
                }
                ArcType.S_CURVE -> {
                    val x = width * 0.2f + t * width * 0.6f
                    val y = height * 0.5f + arcAmplitude * sin(t * 2 * PI.toFloat())
                    Pair(x, y)
                }
            }

            // Apply intensity
            val randomX = random.nextFloat() * width
            val randomY = random.nextFloat() * height

            val finalX = baseX * config.intensity + randomX * (1 - config.intensity)
            val finalY = baseY * config.intensity + randomY * (1 - config.intensity)

            positions.add(Offset(finalX, finalY))
        }

        return ShapePatternResult(
            basePositions = positions,
            pattern = CellShapePattern.CURVED_ARC,
            metadata = ShapePatternMetadata(
                seed = random.nextInt(),
                mediaCount = mediaCount,
                hexBounds = hexBounds,
                patternParams = mapOf(
                    "arcType" to arcType,
                    "arcAmplitude" to arcAmplitude,
                    "startOffset" to startOffset
                )
            )
        )
    }

    private enum class ArcType { C_CURVE, S_CURVE }
}

/**
 * Cluster pattern generator - dense center with photos radiating outward.
 * Creates cozy, clustered feeling.
 */
class ClusterPatternGenerator : ShapePatternGenerator {

    override fun generatePositions(
        mediaCount: Int,
        hexBounds: Rect,
        random: Random,
        config: ShapeGenerationConfig
    ): ShapePatternResult {
        val positions = mutableListOf<Offset>()
        val centerX = hexBounds.width / 2
        val centerY = hexBounds.height / 2
        val maxRadius = min(hexBounds.width, hexBounds.height) / 3f

        // Cluster parameters
        val clusterRadius = maxRadius * (0.4f + random.nextFloat() * 0.3f)
        val radiationStrength = 0.3f + random.nextFloat() * 0.4f

        for (i in 0 until mediaCount) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val radiusVariation = random.nextFloat()

            // Create cluster with denser center
            val radius = clusterRadius * sqrt(radiusVariation) * radiationStrength

            val baseX = centerX + radius * cos(angle)
            val baseY = centerY + radius * sin(angle)

            // Apply intensity
            val randomX = random.nextFloat() * hexBounds.width
            val randomY = random.nextFloat() * hexBounds.height

            val finalX = baseX * config.intensity + randomX * (1 - config.intensity)
            val finalY = baseY * config.intensity + randomY * (1 - config.intensity)

            positions.add(Offset(finalX, finalY))
        }

        return ShapePatternResult(
            basePositions = positions,
            pattern = CellShapePattern.IRREGULAR_CLUSTER,
            metadata = ShapePatternMetadata(
                seed = random.nextInt(),
                mediaCount = mediaCount,
                hexBounds = hexBounds,
                patternParams = mapOf(
                    "clusterRadius" to clusterRadius,
                    "radiationStrength" to radiationStrength
                )
            )
        )
    }
}

/**
 * Flowing line pattern generator - photos follow wavy, river-like paths.
 * Creates natural, organic flow.
 */
class FlowingLinePatternGenerator : ShapePatternGenerator {

    override fun generatePositions(
        mediaCount: Int,
        hexBounds: Rect,
        random: Random,
        config: ShapeGenerationConfig
    ): ShapePatternResult {
        val positions = mutableListOf<Offset>()
        val width = hexBounds.width
        val height = hexBounds.height

        // Flow parameters
        val flowDirection = if (random.nextBoolean()) FlowDirection.HORIZONTAL else FlowDirection.VERTICAL
        val waveFrequency = 1.0f + random.nextFloat() * 2.0f
        val waveAmplitude = (min(width, height) / 6) * (0.5f + random.nextFloat() * 0.5f)

        for (i in 0 until mediaCount) {
            val progress = i.toFloat() / maxOf(1, mediaCount - 1)

            val (baseX, baseY) = when (flowDirection) {
                FlowDirection.HORIZONTAL -> {
                    val x = width * 0.1f + progress * width * 0.8f
                    val y = height * 0.5f + waveAmplitude * sin(progress * waveFrequency * PI.toFloat())
                    Pair(x, y)
                }
                FlowDirection.VERTICAL -> {
                    val x = width * 0.5f + waveAmplitude * sin(progress * waveFrequency * PI.toFloat())
                    val y = height * 0.1f + progress * height * 0.8f
                    Pair(x, y)
                }
            }

            // Apply intensity
            val randomX = random.nextFloat() * width
            val randomY = random.nextFloat() * height

            val finalX = baseX * config.intensity + randomX * (1 - config.intensity)
            val finalY = baseY * config.intensity + randomY * (1 - config.intensity)

            positions.add(Offset(finalX, finalY))
        }

        return ShapePatternResult(
            basePositions = positions,
            pattern = CellShapePattern.FLOWING_LINE,
            metadata = ShapePatternMetadata(
                seed = random.nextInt(),
                mediaCount = mediaCount,
                hexBounds = hexBounds,
                patternParams = mapOf(
                    "flowDirection" to flowDirection,
                    "waveFrequency" to waveFrequency,
                    "waveAmplitude" to waveAmplitude
                )
            )
        )
    }

    private enum class FlowDirection { HORIZONTAL, VERTICAL }
}

/**
 * Scattered circle pattern generator - photos loosely form circular boundaries.
 * Creates contained, balanced arrangement.
 */
class ScatteredCirclePatternGenerator : ShapePatternGenerator {

    override fun generatePositions(
        mediaCount: Int,
        hexBounds: Rect,
        random: Random,
        config: ShapeGenerationConfig
    ): ShapePatternResult {
        val positions = mutableListOf<Offset>()
        val centerX = hexBounds.width / 2
        val centerY = hexBounds.height / 2
        val maxRadius = min(hexBounds.width, hexBounds.height) / 3f

        // Circle parameters
        val circleRadius = maxRadius * (0.6f + random.nextFloat() * 0.3f)
        val radiusVariation = 0.2f + random.nextFloat() * 0.3f

        for (i in 0 until mediaCount) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val radiusOffset = circleRadius * (1 + radiusVariation * (random.nextFloat() - 0.5f))

            val baseX = centerX + radiusOffset * cos(angle)
            val baseY = centerY + radiusOffset * sin(angle)

            // Apply intensity
            val randomX = random.nextFloat() * hexBounds.width
            val randomY = random.nextFloat() * hexBounds.height

            val finalX = baseX * config.intensity + randomX * (1 - config.intensity)
            val finalY = baseY * config.intensity + randomY * (1 - config.intensity)

            positions.add(Offset(finalX, finalY))
        }

        return ShapePatternResult(
            basePositions = positions,
            pattern = CellShapePattern.SCATTERED_CIRCLE,
            metadata = ShapePatternMetadata(
                seed = random.nextInt(),
                mediaCount = mediaCount,
                hexBounds = hexBounds,
                patternParams = mapOf(
                    "circleRadius" to circleRadius,
                    "radiusVariation" to radiusVariation
                )
            )
        )
    }
}

/**
 * Fan pattern generator - photos spread like opened hand or flower petals.
 * Creates radiating, expansive feeling.
 */
class FanPatternGenerator : ShapePatternGenerator {

    override fun generatePositions(
        mediaCount: Int,
        hexBounds: Rect,
        random: Random,
        config: ShapeGenerationConfig
    ): ShapePatternResult {
        val positions = mutableListOf<Offset>()
        val centerX = hexBounds.width / 2
        val centerY = hexBounds.height / 2
        val maxRadius = min(hexBounds.width, hexBounds.height) / 2.5f

        // Fan parameters
        val fanAngle = PI.toFloat() * (0.4f + random.nextFloat() * 0.4f) // 72° to 144°
        val startAngle = random.nextFloat() * 2 * PI.toFloat()
        val fanRadius = maxRadius * (0.5f + random.nextFloat() * 0.4f)

        for (i in 0 until mediaCount) {
            val progress = if (mediaCount > 1) i.toFloat() / (mediaCount - 1) else 0.5f
            val angle = startAngle + progress * fanAngle
            val radius = fanRadius * (0.3f + progress * 0.7f)

            val baseX = centerX + radius * cos(angle)
            val baseY = centerY + radius * sin(angle)

            // Apply intensity
            val randomX = random.nextFloat() * hexBounds.width
            val randomY = random.nextFloat() * hexBounds.height

            val finalX = baseX * config.intensity + randomX * (1 - config.intensity)
            val finalY = baseY * config.intensity + randomY * (1 - config.intensity)

            positions.add(Offset(finalX, finalY))
        }

        return ShapePatternResult(
            basePositions = positions,
            pattern = CellShapePattern.FAN_PATTERN,
            metadata = ShapePatternMetadata(
                seed = random.nextInt(),
                mediaCount = mediaCount,
                hexBounds = hexBounds,
                patternParams = mapOf(
                    "fanAngle" to fanAngle,
                    "startAngle" to startAngle,
                    "fanRadius" to fanRadius
                )
            )
        )
    }
}

/**
 * Factory for creating shape pattern generators.
 */
object ShapePatternGeneratorFactory {
    fun createGenerator(pattern: CellShapePattern): ShapePatternGenerator = when (pattern) {
        CellShapePattern.LOOSE_SPIRAL -> SpiralPatternGenerator()
        CellShapePattern.CURVED_ARC -> ArcPatternGenerator()
        CellShapePattern.IRREGULAR_CLUSTER -> ClusterPatternGenerator()
        CellShapePattern.FLOWING_LINE -> FlowingLinePatternGenerator()
        CellShapePattern.SCATTERED_CIRCLE -> ScatteredCirclePatternGenerator()
        CellShapePattern.FAN_PATTERN -> FanPatternGenerator()
    }
}
