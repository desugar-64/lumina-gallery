package dev.serhiiyaremych.lumina.ui.datelabel

import android.graphics.Path

internal data class CachedPathData(
    val path: Path,
    val bounds: android.graphics.RectF,
    val centerX: Float,
    val centerY: Float,
    val lastUsed: Long = System.currentTimeMillis()
)

internal data class PathCacheKey(
    val text: String,
    val fontSize: Float
)

/** Simple LRU path cache by text and font size */
internal object PathCache {
    private val cache = mutableMapOf<PathCacheKey, CachedPathData>()
    private const val MAX_CACHE_SIZE = 100
    private const val EVICTION_BATCH_SIZE = 20

    fun getOrCreate(key: PathCacheKey, builder: (PathCacheKey) -> Path): CachedPathData {
        cache[key]?.let { cached ->
            cache[key] = cached.copy(lastUsed = System.currentTimeMillis())
            return cached
        }
        val textPath = builder(key)
        val pathBounds = android.graphics.RectF()
        textPath.computeBounds(pathBounds, true)
        val data = CachedPathData(
            path = textPath,
            bounds = pathBounds,
            centerX = (pathBounds.right + pathBounds.left) / 2f,
            centerY = (pathBounds.bottom + pathBounds.top) / 2f
        )
        cache[key] = data
        evictIfNeeded()
        return data
    }

    private fun evictIfNeeded() {
        if (cache.size > MAX_CACHE_SIZE) {
            val sorted = cache.entries.sortedBy { it.value.lastUsed }
            repeat(EVICTION_BATCH_SIZE) { idx ->
                if (idx < sorted.size) cache.remove(sorted[idx].key)
            }
        }
    }
}

