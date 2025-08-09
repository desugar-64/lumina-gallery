package dev.serhiiyaremych.lumina.data.metadata

import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.MediaMetadata
import dev.serhiiyaremych.lumina.domain.model.MetadataLoadingState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing media metadata with hybrid fetching strategy.
 * Implements on-demand loading, cell-based prefetching, and intelligent caching.
 */
@Singleton
class MediaMetadataRepository @Inject constructor(
    private val exifExtractor: ExifDataExtractor,
    private val cache: MetadataCache,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _metadataStates = MutableStateFlow<Map<String, MetadataLoadingState>>(emptyMap())
    val metadataStates: StateFlow<Map<String, MetadataLoadingState>> = _metadataStates.asStateFlow()
    
    /**
     * Fetches metadata for a specific media item (on-demand loading).
     * Returns cached data immediately if available, otherwise triggers loading.
     */
    suspend fun getMetadata(
        media: Media,
        loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
    ): MediaMetadata? {
        val mediaId = media.id.toString()
        
        // Check cache first
        cache.getMetadata(mediaId)?.let { cached ->
            // Check if we have sufficient data for requested level
            if (hasSufficientData(cached, loadLevel)) {
                updateMetadataState(mediaId, MetadataLoadingState.Loaded(cached))
                return cached
            }
        }
        
        // Start loading if not already in progress
        if (!activeJobs.containsKey(mediaId)) {
            loadMetadataAsync(media, loadLevel)
        }
        
        return null
    }
    
    /**
     * Prefetches metadata for all media in a hex cell (background loading).
     */
    fun prefetchCellMetadata(
        cell: HexCellWithMedia,
        loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
    ) {
        repositoryScope.launch {
            val mediaItems = cell.mediaItems
            val uncachedItems = mediaItems.filter { mediaWithPosition ->
                val cached = cache.getMetadata(mediaWithPosition.media.id.toString())
                cached == null || !hasSufficientData(cached, loadLevel)
            }
            
            // Load in parallel with limited concurrency
            uncachedItems.chunked(5).forEach { chunk ->
                chunk.map { mediaWithPosition ->
                    async { loadMetadataAsync(mediaWithPosition.media, loadLevel) }
                }.awaitAll()
            }
        }
    }
    

    /**
     * Prefetches metadata only for the currently-active cell.
     *
     * Steps:
     * 1. Cancel any ongoing jobs that do NOT belong to the active cell.
     * 2. Load metadata for every media item within the cell (optionally excluding the focused
     *    media when a higher-level request is expected elsewhere).
     */
    fun prefetchActiveCell(
        cell: HexCellWithMedia,
        selectedMediaId: Long?,
        loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
    ) {
        repositoryScope.launch {
            // IDs that should remain active
            val activeIds = cell.mediaItems.map { it.media.id.toString() }.toSet()

            // 1. Cancel obsolete jobs
            activeJobs.entries.removeIf { (id, job) ->
                val obsolete = id !in activeIds
                if (obsolete) job.cancel()
                obsolete
            }

            // 2. Prefetch metadata for items in the active cell (except the focused one)
            val toPrefetch = cell.mediaItems.filter { it.media.id != selectedMediaId }
            toPrefetch.chunked(5).forEach { chunk ->
                chunk.map { mediaWithPosition ->
                    async { loadMetadataAsync(mediaWithPosition.media, loadLevel) }
                }.awaitAll()
            }
        }
    }
    
    /**
     * Loads metadata asynchronously and updates cache and state.
     */
    private fun loadMetadataAsync(
        media: Media,
        loadLevel: MetadataLoadLevel
    ): Job {
        val mediaId = media.id.toString()
        
        return repositoryScope.launch {
            // Cancel any existing job for this media
            activeJobs[mediaId]?.cancel()
            
            try {
                updateMetadataState(mediaId, MetadataLoadingState.Loading)
                
                val metadata = exifExtractor.extractMetadata(
                    uri = media.uri,
                    fileSize = media.size,
                    loadLevel = loadLevel
                )
                
                if (metadata != null) {
                    cache.putMetadata(mediaId, metadata)
                    updateMetadataState(mediaId, MetadataLoadingState.Loaded(metadata))
                } else {
                    updateMetadataState(mediaId, MetadataLoadingState.Error(Exception("Failed to extract metadata")))
                }
            } catch (e: Exception) {
                updateMetadataState(mediaId, MetadataLoadingState.Error(e))
            } finally {
                activeJobs.remove(mediaId)
            }
        }.also { job ->
            activeJobs[mediaId] = job
        }
    }
    
    /**
     * Updates metadata loading state and notifies observers.
     */
    private fun updateMetadataState(mediaId: String, state: MetadataLoadingState) {
        val currentStates = _metadataStates.value.toMutableMap()
        currentStates[mediaId] = state
        _metadataStates.value = currentStates
    }
    
    /**
     * Checks if cached metadata has sufficient data for the requested load level.
     */
    private fun hasSufficientData(metadata: MediaMetadata, requestedLevel: MetadataLoadLevel): Boolean {
        return when (requestedLevel) {
            MetadataLoadLevel.ESSENTIAL -> true // Essential data is always available
            MetadataLoadLevel.TECHNICAL -> metadata.technical != null
            MetadataLoadLevel.ADVANCED -> metadata.advanced != null
        }
    }
    
    /**
     * Cancels all pending metadata loading jobs.
     */
    fun cancelAllJobs() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
    
    /**
     * Clears all cached metadata (useful during memory pressure).
     */
    suspend fun clearCache() {
        cache.clearAll()
        _metadataStates.value = emptyMap()
    }
    
    /**
     * Clears memory cache only (keeps disk cache).
     */
    fun clearMemoryCache() {
        cache.clearMemoryCache()
    }
    
    /**
     * Gets cache statistics for debugging and monitoring.
     */
    fun getCacheStats(): CacheStats {
        return cache.getCacheStats()
    }
    
    /**
     * Gets current metadata loading state for a specific media item.
     */
    fun getMetadataState(mediaId: String): MetadataLoadingState {
        return _metadataStates.value[mediaId] ?: MetadataLoadingState.NotLoaded
    }
    
    /**
     * Preloads metadata for high-priority media items (e.g., currently focused photo).
     */
    suspend fun preloadHighPriorityMetadata(
        media: Media,
        loadLevel: MetadataLoadLevel = MetadataLoadLevel.ADVANCED
    ) {
        withContext(dispatcher) {
            loadMetadataAsync(media, loadLevel).join()
        }
    }
}