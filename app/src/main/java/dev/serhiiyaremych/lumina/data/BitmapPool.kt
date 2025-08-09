package dev.serhiiyaremych.lumina.data

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.util.LruCache
import androidx.compose.ui.unit.IntSize
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance bitmap pool with size-based buckets and memory pressure handling.
 * 
 * Provides bitmap reuse for:
 * - Scaled bitmaps in PhotoScaler
 * - Atlas bitmaps in DynamicAtlasPool
 * - Intermediate processing bitmaps
 * 
 * Features:
 * - Size-based bucket system for efficient lookup
 * - LRU eviction when memory pressure increases
 * - Thread-safe operations for concurrent atlas generation
 * - Integration with SmartMemoryManager for coordinated cleanup
 */
@Singleton
class BitmapPool @Inject constructor() {    
    companion object {
        private const val TAG = "BitmapPool"
        
        // Size thresholds for bucket classification
        private const val SMALL_THRESHOLD = 256 * 256      // 256x256 pixels
        private const val MEDIUM_THRESHOLD = 1024 * 1024   // 1024x1024 pixels  
        private const val LARGE_THRESHOLD = 4096 * 4096    // 4096x4096 pixels
        
        // Pool size limits (number of bitmaps per bucket)
        private const val SMALL_POOL_SIZE = 20
        private const val MEDIUM_POOL_SIZE = 15
        private const val LARGE_POOL_SIZE = 10
        private const val XLARGE_POOL_SIZE = 5
        
        // Memory size limits (bytes per bucket)
        private const val SMALL_MEMORY_LIMIT = 10 * 1024 * 1024    // 10MB
        private const val MEDIUM_MEMORY_LIMIT = 50 * 1024 * 1024   // 50MB
        private const val LARGE_MEMORY_LIMIT = 100 * 1024 * 1024   // 100MB
        private const val XLARGE_MEMORY_LIMIT = 200 * 1024 * 1024  // 200MB
    }
    
    /**
     * Bitmap size bucket for efficient pooling
     */
    enum class BucketSize {
        SMALL,      // 32x32 to 256x256 (thumbnails)
        MEDIUM,     // 256x256 to 1024x1024 (scaled photos)
        LARGE,      // 1024x1024 to 4096x4096 (atlas bitmaps)
        XLARGE      // 4096x4096+ (8K atlases)
    }
    
    /**
     * Bitmap pool entry with metadata
     */
    private data class PoolEntry(
        val bitmap: Bitmap,
        val size: IntSize,
        val config: Bitmap.Config,
        val memorySize: Int,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Pool statistics for monitoring
     */
    data class PoolStats(
        val smallPoolSize: Int,
        val mediumPoolSize: Int,
        val largePoolSize: Int,
        val xlargePoolSize: Int,
        val totalMemoryUsed: Long,
        val hitRate: Float,
        val totalRequests: Int,
        val totalHits: Int
    )
    
    // LRU caches for each bucket size
    private val smallPool = LruCache<String, PoolEntry>(SMALL_POOL_SIZE)
    private val mediumPool = LruCache<String, PoolEntry>(MEDIUM_POOL_SIZE)
    private val largePool = LruCache<String, PoolEntry>(LARGE_POOL_SIZE)
    private val xlargePool = LruCache<String, PoolEntry>(XLARGE_POOL_SIZE)
    
    // Pool access synchronization
    private val poolLock = Any()
    
    // Statistics tracking
    private var totalRequests = 0
    private var totalHits = 0
    
    /**
     * Acquire a bitmap from the pool or create a new one
     */
    fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap = trace(BenchmarkLabels.BITMAP_POOL_ACQUIRE) {
        val size = IntSize(width, height)
        val key = createKey(size, config)
        
        synchronized(poolLock) {
            totalRequests++
            
            val bucket = getBucket(size)
            val pool = getPoolForBucket(bucket)
            
            pool.get(key)?.let { entry ->
                if (!entry.bitmap.isRecycled && 
                    entry.bitmap.width == width && 
                    entry.bitmap.height == height &&
                    entry.bitmap.config == config) {
                    
                    totalHits++
                    android.util.Log.d(TAG, "Pool hit: ${width}x${height} ${config} from ${bucket}")
                    
                    // Remove from pool and return
                    pool.remove(key)
                    return@trace entry.bitmap
                }
            }
            
            // Pool miss - create new bitmap
            android.util.Log.d(TAG, "Pool miss: ${width}x${height} ${config} - creating new")
            return@trace trace(BenchmarkLabels.ATLAS_MEMORY_BITMAP_ALLOCATE) {
                createBitmap(width, height, config)
            }
        }
    }
    
    /**
     * Release a bitmap back to the pool
     */
    fun release(bitmap: Bitmap) = trace(BenchmarkLabels.BITMAP_POOL_RELEASE) {
        if (bitmap.isRecycled) {
            android.util.Log.w(TAG, "Attempted to release recycled bitmap")
            return@trace
        }
        
        val size = IntSize(bitmap.width, bitmap.height)
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val key = createKey(size, config)
        val memorySize = bitmap.byteCount
        
        synchronized(poolLock) {
            val bucket = getBucket(size)
            val pool = getPoolForBucket(bucket)
            
            // Check if pool has space and memory budget
            if (canAddToPool(bucket, memorySize)) {
                val entry = PoolEntry(
                    bitmap = bitmap,
                    size = size,
                    config = config,
                    memorySize = memorySize
                )
                
                pool.put(key, entry)
                android.util.Log.d(TAG, "Released bitmap to pool: ${size.width}x${size.height} ${config} to ${bucket}")
            } else {
                // Pool is full or memory budget exceeded - recycle bitmap
                android.util.Log.d(TAG, "Pool full, recycling bitmap: ${size.width}x${size.height} ${config}")
                trace(BenchmarkLabels.ATLAS_MEMORY_BITMAP_RECYCLE) {
                    bitmap.recycle()
                }
            }
        }
    }
    
    /**
     * Clear pool based on memory pressure
     */
    fun clearOnMemoryPressure(pressureLevel: SmartMemoryManager.MemoryPressure) {
        synchronized(poolLock) {
            when (pressureLevel) {
                SmartMemoryManager.MemoryPressure.LOW -> {
                    // Clear oldest 25% of each pool
                    clearOldestEntries(0.25f)
                }
                SmartMemoryManager.MemoryPressure.MEDIUM -> {
                    // Clear oldest 50% of each pool
                    clearOldestEntries(0.5f)
                }
                SmartMemoryManager.MemoryPressure.HIGH -> {
                    // Clear all pools
                    clearAllPools()
                }
                SmartMemoryManager.MemoryPressure.NORMAL -> {
                    // No action needed for normal pressure
                }
                SmartMemoryManager.MemoryPressure.CRITICAL -> {
                    // Clear all pools for critical pressure
                    clearAllPools()
                }
            }
        }
    }
    
    
    /**
     * Determine bucket size for given dimensions
     */
    private fun getBucket(size: IntSize): BucketSize {
        val pixelCount = size.width * size.height
        return when {
            pixelCount <= SMALL_THRESHOLD -> BucketSize.SMALL
            pixelCount <= MEDIUM_THRESHOLD -> BucketSize.MEDIUM
            pixelCount <= LARGE_THRESHOLD -> BucketSize.LARGE
            else -> BucketSize.XLARGE
        }
    }
    
    /**
     * Get pool for bucket size
     */
    private fun getPoolForBucket(bucket: BucketSize): LruCache<String, PoolEntry> {
        return when (bucket) {
            BucketSize.SMALL -> smallPool
            BucketSize.MEDIUM -> mediumPool
            BucketSize.LARGE -> largePool
            BucketSize.XLARGE -> xlargePool
        }
    }
    
    /**
     * Create cache key for bitmap
     */
    private fun createKey(size: IntSize, config: Bitmap.Config): String {
        return "${size.width}x${size.height}_${config.name}"
    }
    
    /**
     * Check if bitmap can be added to pool
     */
    private fun canAddToPool(bucket: BucketSize, memorySize: Int): Boolean {
        val currentMemory = calculateBucketMemoryUsed(bucket)
        val memoryLimit = when (bucket) {
            BucketSize.SMALL -> SMALL_MEMORY_LIMIT
            BucketSize.MEDIUM -> MEDIUM_MEMORY_LIMIT
            BucketSize.LARGE -> LARGE_MEMORY_LIMIT
            BucketSize.XLARGE -> XLARGE_MEMORY_LIMIT
        }
        
        return currentMemory + memorySize <= memoryLimit
    }
    
    /**
     * Calculate memory used by specific bucket
     */
    private fun calculateBucketMemoryUsed(bucket: BucketSize): Int {
        val pool = getPoolForBucket(bucket)
        var totalMemory = 0
        
        pool.snapshot().values.forEach { entry ->
            totalMemory += entry.memorySize
        }
        
        return totalMemory
    }
    
    /**
     * Calculate total memory used by all pools
     */
    private fun calculateTotalMemoryUsed(): Long {
        var totalMemory = 0L
        
        BucketSize.values().forEach { bucket ->
            totalMemory += calculateBucketMemoryUsed(bucket)
        }
        
        return totalMemory
    }
    
    /**
     * Clear oldest entries from all pools
     */
    private fun clearOldestEntries(percentage: Float) {
        listOf(smallPool, mediumPool, largePool, xlargePool).forEach { pool ->
            val snapshot = pool.snapshot()
            val entriesToRemove = (snapshot.size * percentage).toInt()
            
            val sortedEntries = snapshot.entries.sortedBy { it.value.createdAt }
            
            repeat(entriesToRemove) { index ->
                if (index < sortedEntries.size) {
                    val entry = sortedEntries[index]
                    pool.remove(entry.key)
                    
                    trace(BenchmarkLabels.ATLAS_MEMORY_BITMAP_RECYCLE) {
                        entry.value.bitmap.recycle()
                    }
                }
            }
        }
        
        android.util.Log.d(TAG, "Cleared ${(percentage * 100).toInt()}% of pool entries due to memory pressure")
    }
    
    /**
     * Clear all pools
     */
    private fun clearAllPools() {
        listOf(smallPool, mediumPool, largePool, xlargePool).forEach { pool ->
            pool.snapshot().values.forEach { entry ->
                trace(BenchmarkLabels.ATLAS_MEMORY_BITMAP_RECYCLE) {
                    entry.bitmap.recycle()
                }
            }
            pool.evictAll()
        }
        
        android.util.Log.d(TAG, "Cleared all bitmap pools due to high memory pressure")
    }
}