package dev.serhiiyaremych.lumina.ui.animation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Stable
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Configuration for noise texture generation.
 */
@Stable
data class NoiseConfig(
    val size: Int = 512,
    val frequency: Float = 8f,
    val amplitude: Float = 1f,
    val octaves: Int = 4,
    val persistence: Float = 0.5f,
    val seed: Long = 12345L,
    val gradientSoftness: Float = 0.85f // Controls how soft the circular edge is (0f = hard, 1f = very soft)
)

/**
 * Generates noise textures for reveal animations using Perlin-like noise
 * with circular gradient masking.
 */
@Stable
class NoiseTextureGenerator(
    private val config: NoiseConfig = NoiseConfig()
) {
    private val random = Random(config.seed)
    private var cachedTexture: Bitmap? = null
    
    /**
     * Gets the cached noise texture, generating it if needed.
     */
    fun getNoiseTexture(): Bitmap {
        return cachedTexture ?: generateNoiseTexture().also { cachedTexture = it }
    }
    
    /**
     * Clears the cached texture to free memory.
     */
    fun clearCache() {
        cachedTexture?.recycle()
        cachedTexture = null
    }
    
    /**
     * Generates a new noise texture with circular gradient masking.
     */
    private fun generateNoiseTexture(): Bitmap {
        val bitmap = Bitmap.createBitmap(config.size, config.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Generate Perlin-like noise
        val noiseData = generatePerlinNoise()
        
        // Apply noise to bitmap with circular gradient
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Create circular gradient for masking
        val center = config.size / 2f
        val radius = center * config.gradientSoftness
        val gradientRadius = center // Full radius for gradient calculation
        
        val radialGradient = RadialGradient(
            center, center, gradientRadius,
            intArrayOf(
                0xFFFFFFFF.toInt(), // White center (fully opaque)
                0x80FFFFFF.toInt(), // 50% opacity at gradient radius * softness
                0x00FFFFFF.toInt()  // Transparent at edge
            ),
            floatArrayOf(0f, config.gradientSoftness, 1f),
            Shader.TileMode.CLAMP
        )
        
        paint.shader = radialGradient
        
        // Draw the noise pattern with circular masking
        for (y in 0 until config.size) {
            for (x in 0 until config.size) {
                val noiseValue = noiseData[y * config.size + x]
                val distanceFromCenter = sqrt(
                    ((x - center) * (x - center) + (y - center) * (y - center)).toDouble()
                ).toFloat()
                
                // Calculate alpha based on distance and noise
                val normalizedDistance = distanceFromCenter / gradientRadius
                val gradientAlpha = when {
                    normalizedDistance <= config.gradientSoftness -> 1f
                    normalizedDistance >= 1f -> 0f
                    else -> {
                        // Smooth easing curve for transition
                        val t = (normalizedDistance - config.gradientSoftness) / (1f - config.gradientSoftness)
                        1f - smoothstep(t)
                    }
                }
                
                // Combine noise with gradient for final alpha
                val finalAlpha = (noiseValue * gradientAlpha * 255).toInt().coerceIn(0, 255)
                val color = (finalAlpha shl 24) or 0x00FFFFFF // White with calculated alpha
                
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }
    
    /**
     * Generates Perlin-like noise data.
     */
    private fun generatePerlinNoise(): FloatArray {
        val noiseData = FloatArray(config.size * config.size)
        
        for (y in 0 until config.size) {
            for (x in 0 until config.size) {
                var value = 0f
                var amplitude = config.amplitude
                var frequency = config.frequency
                
                // Generate multiple octaves of noise
                for (octave in 0 until config.octaves) {
                    val sampleX = x.toFloat() / config.size * frequency
                    val sampleY = y.toFloat() / config.size * frequency
                    
                    val perlinValue = perlin(sampleX, sampleY)
                    value += perlinValue * amplitude
                    
                    amplitude *= config.persistence
                    frequency *= 2f
                }
                
                // Normalize to 0-1 range
                value = (value + 1f) / 2f
                value = value.coerceIn(0f, 1f)
                
                noiseData[y * config.size + x] = value
            }
        }
        
        return noiseData
    }
    
    /**
     * Simple Perlin-like noise function using gradient vectors.
     */
    private fun perlin(x: Float, y: Float): Float {
        // Grid coordinates
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1
        
        // Local coordinates within grid cell
        val sx = x - x0
        val sy = y - y0
        
        // Generate gradient vectors for grid corners
        val n00 = dotGridGradient(x0, y0, x, y)
        val n10 = dotGridGradient(x1, y0, x, y)
        val n01 = dotGridGradient(x0, y1, x, y)
        val n11 = dotGridGradient(x1, y1, x, y)
        
        // Interpolate
        val ix0 = lerp(n00, n10, smoothstep(sx))
        val ix1 = lerp(n01, n11, smoothstep(sx))
        return lerp(ix0, ix1, smoothstep(sy))
    }
    
    /**
     * Calculate dot product of gradient vector and distance vector.
     */
    private fun dotGridGradient(ix: Int, iy: Int, x: Float, y: Float): Float {
        // Get gradient vector (pseudo-random based on grid coordinates)
        val gradient = getGradient(ix, iy)
        
        // Distance vector
        val dx = x - ix
        val dy = y - iy
        
        // Dot product
        return dx * gradient.first + dy * gradient.second
    }
    
    /**
     * Get pseudo-random gradient vector for grid point.
     */
    private fun getGradient(ix: Int, iy: Int): Pair<Float, Float> {
        // Simple hash function to get consistent random vectors
        val hash = ((ix * 374761393) + (iy * 668265263)) xor config.seed.toInt()
        val angle = (hash and 0xFFFF) * (2.0 * Math.PI / 65536.0)
        return Pair(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
    }
    
    /**
     * Linear interpolation.
     */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
    
    /**
     * Smooth step function for easing.
     */
    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)
}