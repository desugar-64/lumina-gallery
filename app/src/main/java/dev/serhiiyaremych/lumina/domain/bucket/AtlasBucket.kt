package dev.serhiiyaremych.lumina.domain.bucket

import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.LODLevel

/**
 * Thread-safe container that groups TextureAtlases by LOD level and provides
 * fast lookup by photo Uri.
 *
 * Internally it keeps:
 *   1. bucketsByLod – Map<LODLevel, MutableList<TextureAtlas>>
 *   2. index – Map<Uri, TextureAtlas> for O(1) region lookup.
 *
 * The index is kept in sync whenever atlases are added or cleared.  This means
 * we pay a tiny overhead on insertion but avoid repeated linear scans during
 * rendering-time lookups.
 */
open class AtlasBucket {
    private val lock = Mutex()

    private val bucketsByLod: MutableMap<LODLevel, ArrayDeque<TextureAtlas>> = hashMapOf()

    /**  Quick lookup from photo -> atlas  */
    private val index: MutableMap<Uri, TextureAtlas> = hashMapOf()

    

    suspend fun addAll(atlases: Collection<TextureAtlas>) {
        lock.withLock {
            atlases.forEach { atlas ->
                val deque = bucketsByLod.getOrPut(atlas.lodLevel) { ArrayDeque() }
                deque.addLast(atlas)
                atlas.regions.keys.forEach { uri ->
                    index[uri] = atlas
                }
            }
        }
    }

    suspend fun clear() {
        lock.withLock {
            bucketsByLod.values.flatten().forEach { atlas ->
                if (!atlas.bitmap.isRecycled) atlas.bitmap.recycle()
            }
            bucketsByLod.clear()
            index.clear()
        }
    }

    /** FIFO eviction on a specific LOD list. Returns true if something was removed. */
    suspend fun evictOldest(lodLevel: LODLevel): Boolean {
        return lock.withLock {
            val deque = bucketsByLod[lodLevel] ?: return@withLock false
            if (deque.isEmpty()) return@withLock false
            val atlas = deque.removeFirst()
            atlas.regions.keys.forEach { index.remove(it) }
            if (!atlas.bitmap.isRecycled) atlas.bitmap.recycle()
            true
        }
    }

    suspend fun getAtlasForPhoto(uri: Uri): TextureAtlas? = lock.withLock { index[uri] }

    suspend fun getRegionForPhoto(uri: Uri): AtlasRegion? = lock.withLock {
        index[uri]?.regions?.get(uri)
    }

    /** Get the highest LOD level available for a specific photo */
    suspend fun getHighestLODForPhoto(uri: Uri): LODLevel? = lock.withLock {
        index[uri]?.lodLevel
    }

    /** Check if this bucket contains a photo at or above a specific LOD level */
    suspend fun hasPhotoAtLOD(uri: Uri, minLODLevel: LODLevel): Boolean = lock.withLock {
        index[uri]?.let { atlas ->
            atlas.lodLevel.level >= minLODLevel.level
        } ?: false
    }

    /** Get the highest LOD level available in this bucket for any of the given photos */
    suspend fun getHighestLODForPhotos(uris: List<Uri>): LODLevel? = lock.withLock {
        uris.mapNotNull { uri -> index[uri]?.lodLevel }
            .maxByOrNull { it.level }
    }

    /** Returns a snapshot list for rendering/debugging */
    suspend fun snapshotAtlases(): List<TextureAtlas> = lock.withLock {
        bucketsByLod.values.flatten().toList()
    }

    /** Current total atlases (all LODs) */
    suspend fun size(): Int = lock.withLock { index.size }
}

