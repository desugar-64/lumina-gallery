package dev.serhiiyaremych.lumina.domain.bucket

import android.net.Uri
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates four AtlasBucket instances that mirror the UI interaction contexts.
 *
 * 1. baseBucket      – persistent LEVEL_0 atlases (never cleared)
 * 2. focusBucket     – active cell (+1 LOD)
 * 3. highlightBucket – explicit selected photo (max LOD)
 * 4. rollingBucket   – general fallback cache, keeps a sliding window of the two most-recent LODs
 *
 * Whenever any bucket mutates we emit a combined snapshot through [atlasFlow].
 */
@Singleton
class AtlasBucketManager @Inject constructor() {

    private val baseBucket = AtlasBucket()
    private val focusBucket = AtlasBucket()
    private val highlightBucket = AtlasBucket()
    private val rollingBucket = RollingBucket()

    /** Protect multi-bucket snapshot creation */
    private val snapshotLock = Mutex()

    private val _atlasFlow = MutableStateFlow<List<TextureAtlas>>(emptyList())
    val atlasFlow: StateFlow<List<TextureAtlas>> = _atlasFlow

    /* ----------------------------------------------------------------------- */
    /*  Mutation helpers                                                       */
    /* ----------------------------------------------------------------------- */

    suspend fun populateBase(atlases: Collection<TextureAtlas>) {
        baseBucket.addAll(atlases)
        emitSnapshot()
    }

    suspend fun replaceFocus(atlases: Collection<TextureAtlas>) {
        focusBucket.clear()
        focusBucket.addAll(atlases)
        emitSnapshot()
    }

    suspend fun replaceHighlight(atlases: Collection<TextureAtlas>) {
        highlightBucket.clear()
        highlightBucket.addAll(atlases)
        emitSnapshot()
    }

    suspend fun addToRolling(atlases: Collection<TextureAtlas>) {
        rollingBucket.addAtlasesMaintainingWindow(atlases)
        emitSnapshot()
    }

    suspend fun clearFocus() {
        focusBucket.clear(); emitSnapshot()
    }

    suspend fun clearHighlight() {
        highlightBucket.clear(); emitSnapshot()
    }

    suspend fun clearTransient() {
        focusBucket.clear()
        highlightBucket.clear()
        rollingBucket.clear()
        emitSnapshot()
    }

    /* ----------------------------------------------------------------------- */
    /*  Lookup helpers                                                         */
    /* ----------------------------------------------------------------------- */

    suspend fun getBestRegion(uri: Uri): AtlasRegion? {
        highlightBucket.getRegionForPhoto(uri)?.let { return it }
        focusBucket.getRegionForPhoto(uri)?.let { return it }
        rollingBucket.getRegionForPhoto(uri)?.let { return it }
        return baseBucket.getRegionForPhoto(uri)
    }

    suspend fun isBaseBucketInitialized(): Boolean {
        return baseBucket.size() > 0
    }

    /** Get base bucket atlases (LEVEL_0 persistent cache) */
    suspend fun getBaseBucketAtlases(): List<TextureAtlas> {
        return baseBucket.snapshotAtlases()
    }

    /** Get atlases for specific LOD level from all buckets */
    suspend fun getAtlasesByLOD(lodLevel: LODLevel): List<TextureAtlas> {
        return snapshotAll().filter { it.lodLevel == lodLevel }
    }

    /** Get the highest LOD level available for a photo in focus bucket */
    suspend fun getFocusLODForPhoto(uri: Uri): LODLevel? {
        return focusBucket.getHighestLODForPhoto(uri)
    }

    /** Get the highest LOD level available for any photos from a cell in focus bucket */
    suspend fun getFocusLODForPhotos(uris: List<Uri>): LODLevel? {
        return focusBucket.getHighestLODForPhotos(uris)
    }

    /** Check if focus bucket has photo at or above required LOD */
    suspend fun hasFocusPhotoAtLOD(uri: Uri, minLODLevel: LODLevel): Boolean {
        return focusBucket.hasPhotoAtLOD(uri, minLODLevel)
    }

    /** Get the highest LOD level available for a photo in highlight bucket */
    suspend fun getHighlightLODForPhoto(uri: Uri): LODLevel? {
        return highlightBucket.getHighestLODForPhoto(uri)
    }

    /** Check if highlight bucket has photo at or above required LOD */
    suspend fun hasHighlightPhotoAtLOD(uri: Uri, minLODLevel: LODLevel): Boolean {
        return highlightBucket.hasPhotoAtLOD(uri, minLODLevel)
    }

    suspend fun snapshotAll(): List<TextureAtlas> = snapshotLock.withLock {
        baseBucket.snapshotAtlases() +
            focusBucket.snapshotAtlases() +
            highlightBucket.snapshotAtlases() +
            rollingBucket.snapshotAtlases()
    }

    /* ----------------------------------------------------------------------- */
    /*  Internal helpers                                                       */
    /* ----------------------------------------------------------------------- */

    private suspend fun emitSnapshot() {
        _atlasFlow.value = snapshotAll()
    }

    /**
     * RollingBucket enforces the "keep current + previous LOD" rule.
     */
    private class RollingBucket : AtlasBucket() {
        private val lodOrder = ArrayDeque<LODLevel>()
        private val orderLock = Mutex()

        suspend fun addAtlasesMaintainingWindow(atlases: Collection<TextureAtlas>) {
            if (atlases.isEmpty()) return
            orderLock.withLock {
                val incomingLods = atlases.map { it.lodLevel }.toSet()

                // Refresh order: move existing lods to end (most recent)
                incomingLods.forEach { lod ->
                    lodOrder.remove(lod)
                    lodOrder.addLast(lod)
                }

                // Enforce window size (2 distinct LODs)
                while (lodOrder.size > 2) {
                    val evictLod = lodOrder.removeFirst()
                    // Evict all atlases of that LOD (FIFO per-lod inside AtlasBucket)
                    while (evictOldest(evictLod)) {
                        /* keep evicting until none remain */
                    }
                }

                // Finally add atlases to underlying bucket storage
                addAll(atlases)
            }
        }
    }
}

