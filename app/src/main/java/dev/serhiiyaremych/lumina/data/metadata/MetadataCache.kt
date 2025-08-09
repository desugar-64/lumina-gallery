package dev.serhiiyaremych.lumina.data.metadata

import android.content.Context
import android.util.LruCache
import androidx.core.content.edit
import dev.serhiiyaremych.lumina.domain.model.MediaMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Multi-layer cache for media metadata with memory and disk persistence.
 * Uses LRU eviction policy for memory management.
 */
@Singleton
class MetadataCache @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val MEMORY_CACHE_SIZE = 100 // ~5MB assuming 50KB per metadata entry
        private const val PREFS_NAME = "metadata_cache"
        private const val DISK_CACHE_MAX_ENTRIES = 500
    }

    private val memoryCache = LruCache<String, MediaMetadata>(MEMORY_CACHE_SIZE)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Retrieves metadata from cache (memory first, then disk).
     */
    suspend fun getMetadata(mediaId: String): MediaMetadata? {
        // Check memory cache first
        memoryCache.get(mediaId)?.let { return it }

        // Check disk cache
        return withContext(Dispatchers.IO) {
            val diskData = prefs.getString(mediaId, null)
            if (diskData != null) {
                try {
                    val metadata = json.decodeFromString<MediaMetadata>(diskData)
                    // Put back in memory cache
                    memoryCache.put(mediaId, metadata)
                    metadata
                } catch (e: Exception) {
                    // Remove corrupted data
                    prefs.edit { remove(mediaId) }
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * Stores metadata in both memory and disk caches.
     */
    suspend fun putMetadata(mediaId: String, metadata: MediaMetadata) {
        // Store in memory cache
        memoryCache.put(mediaId, metadata)

        // Store in disk cache
        withContext(Dispatchers.IO) {
            try {
                val jsonData = json.encodeToString(metadata)
                prefs.edit {
                    putString(mediaId, jsonData)
                }

                // Clean up old entries if we're at capacity
                cleanupDiskCacheIfNeeded()
            } catch (e: Exception) {
                // Ignore serialization errors
            }
        }
    }

    /**
     * Clears both memory and disk caches.
     */
    suspend fun clearAll() {
        memoryCache.evictAll()

        withContext(Dispatchers.IO) {
            prefs.edit { clear() }
        }
    }

    /**
     * Clears memory cache only (useful during memory pressure).
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    /**
     * Gets cache statistics for debugging.
     */
    fun getCacheStats(): CacheStats = CacheStats(
        memoryHitCount = memoryCache.hitCount().toLong(),
        memoryMissCount = memoryCache.missCount().toLong(),
        memorySize = memoryCache.size(),
        memoryMaxSize = memoryCache.maxSize(),
        diskSize = prefs.all.size
    )

    /**
     * Prefetches metadata for a list of media IDs (loads into memory cache).
     */
    suspend fun prefetchMetadata(mediaIds: List<String>) {
        withContext(Dispatchers.IO) {
            mediaIds.forEach { mediaId ->
                if (memoryCache.get(mediaId) == null) {
                    getMetadata(mediaId) // This will load from disk to memory
                }
            }
        }
    }

    private fun cleanupDiskCacheIfNeeded() {
        val allEntries = prefs.all
        if (allEntries.size > DISK_CACHE_MAX_ENTRIES) {
            // Remove oldest entries (simple approach - remove random 10%)
            val entriesToRemove = allEntries.keys.shuffled().take(allEntries.size / 10)
            prefs.edit {
                entriesToRemove.forEach { remove(it) }
            }
        }
    }
}

/**
 * Statistics about cache performance.
 */
data class CacheStats(
    val memoryHitCount: Long,
    val memoryMissCount: Long,
    val memorySize: Int,
    val memoryMaxSize: Int,
    val diskSize: Int
)
