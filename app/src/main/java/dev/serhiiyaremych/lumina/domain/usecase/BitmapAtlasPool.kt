package dev.serhiiyaremych.lumina.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bitmap Atlas Pool - Efficient Atlas Texture Reuse System
 *
 * Manages bitmap allocation and reuse for atlas textures to reduce memory pressure
 * and GC overhead. Separate pools for different atlas sizes (2K/4K/8K) with
 * automatic cleanup during memory pressure.
 *
 * Features:
 * - Size-specific bitmap pools (2K, 4K, 8K)
 * - Memory pressure-aware cleanup
 * - Thread-safe operations
 * - Configurable pool sizes
 * - Automatic bitmap validation
 */
@Singleton
class BitmapAtlasPool @Inject constructor() {
    
    companion object {
        private const val TAG = "BitmapAtlasPool"
        
        // Pool configuration
        private const val MAX_POOL_SIZE_2K = 4  // 2K atlases: ~16MB each
        private const val MAX_POOL_SIZE_4K = 2  // 4K atlases: ~64MB each  
        private const val MAX_POOL_SIZE_8K = 1  // 8K atlases: ~256MB each
        
        // Atlas size constants
        private const val ATLAS_2K = 2048
        private const val ATLAS_4K = 4096
        private const val ATLAS_8K = 8192
    }

    // Separate pools for different atlas sizes
    private val pool2K = mutableListOf<Bitmap>()
    private val pool4K = mutableListOf<Bitmap>()
    private val pool8K = mutableListOf<Bitmap>()
    
    // Mutex for thread-safe operations
    private val poolMutex = Mutex()
    
    // Pool statistics
    private var acquireCount = 0
    private var releaseCount = 0
    private var hitCount = 0
    private var missCount = 0

    /**
     * Acquire bitmap from pool or create new one
     */
    suspend fun acquire(
        width: Int, 
        height: Int, 
        config: Bitmap.Config
    ): Bitmap = poolMutex.withLock {
        acquireCount++
        
        val atlasSize = getAtlasSize(width, height)
        val pool = getPoolForSize(atlasSize)
        val maxPoolSize = getMaxPoolSize(atlasSize)
        
        // Try to find reusable bitmap in pool
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val bitmap = iterator.next()
            
            // Validate bitmap is still usable
            if (!bitmap.isRecycled && 
                bitmap.width == width && 
                bitmap.height == height && 
                bitmap.config == config) {
                
                iterator.remove()
                hitCount++
                
                // Clear bitmap for reuse
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                
                Log.d(TAG, "Pool HIT: ${atlasSize}K bitmap acquired (pool size: ${pool.size})")
                return@withLock bitmap
            } else {
                // Remove invalid bitmap
                iterator.remove()
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
        
        // Pool miss - create new bitmap
        missCount++
        val newBitmap = createBitmap(width, height, config)
        
        Log.d(TAG, "Pool MISS: Created new ${atlasSize}K bitmap (pool size: ${pool.size})")
        return@withLock newBitmap
    }

    /**
     * Release bitmap back to pool for reuse
     */
    suspend fun release(bitmap: Bitmap) = poolMutex.withLock {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Attempted to release recycled bitmap")
            return@withLock
        }
        
        releaseCount++
        
        val atlasSize = getAtlasSize(bitmap.width, bitmap.height)
        val pool = getPoolForSize(atlasSize)
        val maxPoolSize = getMaxPoolSize(atlasSize)
        
        // Add to pool if not full
        if (pool.size < maxPoolSize) {
            pool.add(bitmap)
            Log.d(TAG, "Released ${atlasSize}K bitmap to pool (pool size: ${pool.size})")
        } else {
            // Pool full - recycle bitmap
            bitmap.recycle()
            Log.d(TAG, "Pool full - recycled ${atlasSize}K bitmap")
        }
    }

    /**
     * Clear all pools and recycle bitmaps
     */
    suspend fun clearAllPools() = poolMutex.withLock {
        var recycledCount = 0
        
        listOf(pool2K, pool4K, pool8K).forEach { pool ->
            pool.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                    recycledCount++
                }
            }
            pool.clear()
        }
        
        Log.d(TAG, "Cleared all pools - recycled $recycledCount bitmaps")
    }

    /**
     * Clear specific pool based on memory pressure
     */
    suspend fun clearPoolForSize(atlasSize: Int) = poolMutex.withLock {
        val pool = getPoolForSize(atlasSize)
        var recycledCount = 0
        
        pool.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
                recycledCount++
            }
        }
        pool.clear()
        
        Log.d(TAG, "Cleared ${atlasSize}K pool - recycled $recycledCount bitmaps")
    }

    /**
     * Get pool statistics for debugging
     */
    suspend fun getPoolStatistics(): PoolStatistics = poolMutex.withLock {
        PoolStatistics(
            acquireCount = acquireCount,
            releaseCount = releaseCount,
            hitCount = hitCount,
            missCount = missCount,
            hitRate = if (acquireCount > 0) hitCount.toFloat() / acquireCount else 0f,
            pool2KSize = pool2K.size,
            pool4KSize = pool4K.size,
            pool8KSize = pool8K.size,
            totalPoolSize = pool2K.size + pool4K.size + pool8K.size
        )
    }

    // Helper methods

    private fun getAtlasSize(width: Int, height: Int): Int {
        return when {
            width <= ATLAS_2K && height <= ATLAS_2K -> ATLAS_2K
            width <= ATLAS_4K && height <= ATLAS_4K -> ATLAS_4K
            else -> ATLAS_8K
        }
    }

    private fun getPoolForSize(atlasSize: Int): MutableList<Bitmap> {
        return when (atlasSize) {
            ATLAS_2K -> pool2K
            ATLAS_4K -> pool4K
            ATLAS_8K -> pool8K
            else -> pool2K // Fallback
        }
    }

    private fun getMaxPoolSize(atlasSize: Int): Int {
        return when (atlasSize) {
            ATLAS_2K -> MAX_POOL_SIZE_2K
            ATLAS_4K -> MAX_POOL_SIZE_4K
            ATLAS_8K -> MAX_POOL_SIZE_8K
            else -> MAX_POOL_SIZE_2K // Fallback
        }
    }
}

/**
 * Pool statistics for debugging and monitoring
 */
data class PoolStatistics(
    val acquireCount: Int,
    val releaseCount: Int,
    val hitCount: Int,
    val missCount: Int,
    val hitRate: Float,
    val pool2KSize: Int,
    val pool4KSize: Int,
    val pool8KSize: Int,
    val totalPoolSize: Int
) {
    override fun toString(): String {
        return "PoolStats(acquire=$acquireCount, release=$releaseCount, " +
                "hit=$hitCount, miss=$missCount, hitRate=${String.format(java.util.Locale.US, "%.1f", hitRate * 100)}%, " +
                "pools=[2K:$pool2KSize, 4K:$pool4KSize, 8K:$pool8KSize], total=$totalPoolSize)"
    }
}