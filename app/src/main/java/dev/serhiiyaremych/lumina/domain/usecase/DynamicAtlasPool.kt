package dev.serhiiyaremych.lumina.domain.usecase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.compose.ui.unit.IntSize
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.data.ImageToPack
import dev.serhiiyaremych.lumina.data.PackResult
import dev.serhiiyaremych.lumina.data.ScaleStrategy
import dev.serhiiyaremych.lumina.data.ShelfTexturePacker
import dev.serhiiyaremych.lumina.domain.model.AtlasOptimizationConfig
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.PhotoPriority
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.domain.usecase.ProcessedPhoto
import dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager.AtlasKey
import dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager.MemoryPressure
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dynamic Atlas Pool manages multiple atlas sizes based on device capabilities and content requirements.
 *
 * This component provides:
 * - Device-aware atlas size selection (2K/4K/8K)
 * - Intelligent photo distribution across multiple atlases
 * - Parallel atlas generation for optimal performance
 * - Emergency fallback strategies for oversized content
 * - Memory-efficient atlas allocation
 */
@Singleton
class DynamicAtlasPool @Inject constructor(
    private val deviceCapabilities: DeviceCapabilities,
    private val smartMemoryManager: SmartMemoryManager,
    private val photoLODProcessor: PhotoLODProcessor,
    private val bitmapPool: dev.serhiiyaremych.lumina.data.BitmapPool,
    private val externalScope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {

    // Configuration for atlas optimization based on zoom levels
    private val optimizationConfig = AtlasOptimizationConfig.default()

    /**
     * Update the zoom threshold for atlas optimization
     */
    fun updateZoomThreshold(threshold: Float) {
        // For future implementation if needed - currently using immutable config
        Log.d(TAG, "Atlas optimization zoom threshold: $threshold (current: ${optimizationConfig.lowQualityZoomThreshold})")
    }

    companion object {
        private const val TAG = "DynamicAtlasPool"
        private const val ATLAS_PADDING = 2

        // Atlas size constants
        private const val ATLAS_2K = 2048
        private const val ATLAS_4K = 4096
        private const val ATLAS_8K = 8192

        // Distribution strategy constants
        private const val UTILIZATION_TARGET = 0.9f // Target 90% utilization
        private const val MIN_PHOTOS_PER_ATLAS_DEFAULT = 4 // Default minimum photos to justify an atlas
    }

    // Memory pressure monitoring - use injected scope instead of creating new one

    init {
        // Monitor memory pressure and clean bitmap pool accordingly
        smartMemoryManager.memoryPressure
            .onEach { pressureLevel ->
                when (pressureLevel) {
                    MemoryPressure.LOW,
                    MemoryPressure.MEDIUM,
                    MemoryPressure.HIGH -> {
                        try {
                            bitmapPool.clearOnMemoryPressure(pressureLevel)
                            Log.d(TAG, "Cleaned bitmap pool due to memory pressure: $pressureLevel")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to clean bitmap pool during memory pressure", e)
                        }
                    }
                    else -> { /* No action needed for NORMAL and CRITICAL pressure */ }
                }
            }
            .launchIn(externalScope)
    }

    /**
     * Atlas generation strategy based on device capabilities and content
     */
    data class AtlasStrategy(
        val atlasSizes: List<IntSize>,
        val distributionStrategy: DistributionStrategy,
        val maxParallelAtlases: Int
    )

    /**
     * Photo distribution strategy
     */
    enum class DistributionStrategy {
        SINGLE_SIZE, // Use single atlas size for all photos
        MULTI_SIZE, // Use multiple atlas sizes based on content
        PRIORITY_BASED // Use atlas sizes based on photo priority
    }

    /**
     * Multi-atlas generation result
     */
    data class MultiAtlasResult(
        val atlases: List<TextureAtlas>,
        val failed: List<Uri>,
        val totalPhotos: Int,
        val processedPhotos: Int,
        val strategy: AtlasStrategy
    ) {
        val packedPhotos: Int get() = atlases.sumOf { it.photoCount }
        val successRate: Float get() = if (totalPhotos > 0) packedPhotos.toFloat() / totalPhotos else 0f
        val averageUtilization: Float get() = if (atlases.isNotEmpty()) atlases.map { it.utilization }.average().toFloat() else 0f
    }

    /**
     * Generate multi-atlas system with immediate empty atlases and background population
     */
    suspend fun generateMultiAtlasImmediate(
        photoUris: List<Uri>,
        lodLevel: LODLevel,
        currentZoom: Float,
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER,
        priorityMapping: Map<Uri, PhotoPriority> = emptyMap(),
        onPhotoReady: (Uri, AtlasRegion) -> Unit = { _, _ -> }
    ): MultiAtlasResult = trace(BenchmarkLabels.ATLAS_GENERATOR_GENERATE_ATLAS) {
        if (photoUris.isEmpty()) {
            return@trace MultiAtlasResult(
                atlases = emptyList(),
                failed = emptyList(),
                totalPhotos = 0,
                processedPhotos = 0,
                strategy = getDefaultStrategy()
            )
        }

        // Step 1: Process all photos for the specified LOD level
        val processedPhotos = processPhotosForLOD(photoUris, lodLevel, scaleStrategy, priorityMapping)
        val processingFailed = photoUris.filterNot { uri -> processedPhotos.any { it.id == uri } }

        Log.d(TAG, "Photo processing complete: ${processedPhotos.size}/${photoUris.size} succeeded, ${processingFailed.size} failed at $lodLevel")

        if (processedPhotos.isEmpty()) {
            return@trace MultiAtlasResult(
                atlases = emptyList(),
                failed = photoUris,
                totalPhotos = photoUris.size,
                processedPhotos = 0,
                strategy = getDefaultStrategy()
            )
        }

        // Step 2: Determine optimal atlas generation strategy
        val strategy = determineAtlasStrategy(processedPhotos, lodLevel, currentZoom)

        // Step 3: Distribute photos across atlases
        val photoGroups = distributePhotos(processedPhotos, strategy, lodLevel)
        Log.d(TAG, "Photo distribution: ${processedPhotos.size} photos → ${photoGroups.size} groups")

        // Step 4: Create EMPTY atlases immediately
        val emptyAtlases = createEmptyAtlasesImmediate(photoGroups, lodLevel)
        Log.d(TAG, "Created ${emptyAtlases.size} empty atlases immediately")

        // Step 5: Start background population (don't wait)
        externalScope.launch(defaultDispatcher) {
            try {
                populateAtlasesInBackground(emptyAtlases, photoGroups, lodLevel, currentZoom, onPhotoReady)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to populate atlases in background", e)
            }
        }

        return@trace MultiAtlasResult(
            atlases = emptyAtlases,
            failed = processingFailed, // Only processing failures for now, generation failures will be reported via callback
            totalPhotos = photoUris.size,
            processedPhotos = processedPhotos.size,
            strategy = strategy
        )
    }

    /**
     * Create empty atlases immediately with reactive regions initialized
     */
    private fun createEmptyAtlasesImmediate(
        photoGroups: List<PhotoGroup>,
        lodLevel: LODLevel
    ): List<TextureAtlas> = photoGroups.map { photoGroup ->
        // Create empty atlas bitmap
        val atlasBitmap = Bitmap.createBitmap(
            photoGroup.atlasSize.width,
            photoGroup.atlasSize.height,
            Bitmap.Config.ARGB_8888
        )

        // Initialize reactive regions for all photos (null initially)
        val reactiveRegions = mutableMapOf<Uri, androidx.compose.runtime.MutableState<AtlasRegion?>>()
        photoGroup.photos.forEach { processedPhoto ->
            reactiveRegions[processedPhoto.id] = androidx.compose.runtime.mutableStateOf(null)
        }

        // Create atlas with initialized reactive regions (all start as null)
        TextureAtlas(
            bitmap = atlasBitmap,
            reactiveRegions = reactiveRegions,
            lodLevel = lodLevel,
            size = photoGroup.atlasSize
        )
    }

    /**
     * Populate atlases in background with progressive photo updates
     */
    private suspend fun populateAtlasesInBackground(
        emptyAtlases: List<TextureAtlas>,
        photoGroups: List<PhotoGroup>,
        lodLevel: LODLevel,
        currentZoom: Float,
        onPhotoReady: (Uri, AtlasRegion) -> Unit
    ) {
        Log.d(TAG, "Starting background population of ${emptyAtlases.size} atlases")

        emptyAtlases.zip(photoGroups).forEach { (atlas, photoGroup) ->
            populateSingleAtlasBackground(atlas, photoGroup, lodLevel, currentZoom, onPhotoReady)
        }
    }

    /**
     * Populate a single atlas bitmap in background with progressive updates
     */
    private suspend fun populateSingleAtlasBackground(
        atlas: TextureAtlas,
        photoGroup: PhotoGroup,
        lodLevel: LODLevel,
        currentZoom: Float,
        onPhotoReady: (Uri, AtlasRegion) -> Unit
    ) = trace("populateSingleAtlasBackground_$lodLevel") {
        try {
            val imagesToPack = photoGroup.photos.map { ImageToPack(it) }
            Log.d(TAG, "Background populating atlas: ${photoGroup.photos.size} photos → ${photoGroup.atlasSize.width}x${photoGroup.atlasSize.height} atlas at $lodLevel")

            // Pack photos into atlas to get positions
            val packResult = trace(BenchmarkLabels.ATLAS_GENERATOR_PACK_TEXTURES) {
                val texturePacker = ShelfTexturePacker(photoGroup.atlasSize, ATLAS_PADDING)
                texturePacker.pack(imagesToPack)
            }

            Log.d(TAG, "Packing result: ${packResult.packedImages.size}/${imagesToPack.size} photos packed")

            if (packResult.packedImages.isEmpty()) {
                Log.e(TAG, "Background atlas population failed: no photos could be packed")
                return@trace
            }

            // Create canvas for drawing and photosMap for accessing processed bitmaps
            val canvas = Canvas(atlas.bitmap)
            val photosMap = photoGroup.photos.associateBy { it.id }

            // Draw each photo individually with progressive updates
            packResult.packedImages.forEach { packedImage ->
                try {
                    // Test delay for visibility - ensure cancellation is checked
                    delay(100)
                    currentCoroutineContext().ensureActive()

                    val processedPhoto = photosMap[packedImage.id]
                    if (processedPhoto != null && !processedPhoto.bitmap.isRecycled) {
                        // Draw photo onto atlas bitmap
                        trace(BenchmarkLabels.ATLAS_GENERATOR_SOFTWARE_CANVAS) {
                            canvas.drawBitmap(
                                processedPhoto.bitmap,
                                null,
                                android.graphics.RectF(
                                    packedImage.rect.left,
                                    packedImage.rect.top,
                                    packedImage.rect.right,
                                    packedImage.rect.bottom
                                ),
                                null
                            )
                        }

                        // Create atlas region
                        val region = AtlasRegion(
                            photoId = packedImage.id,
                            atlasRect = androidx.compose.ui.geometry.Rect(
                                left = packedImage.rect.left,
                                top = packedImage.rect.top,
                                right = packedImage.rect.right,
                                bottom = packedImage.rect.bottom
                            ),
                            originalSize = processedPhoto.originalSize,
                            scaledSize = processedPhoto.scaledSize,
                            aspectRatio = processedPhoto.aspectRatio,
                            lodLevel = lodLevel.level
                        )

                        // Update reactive region on Main thread
                        withContext(mainDispatcher) {
                            atlas.reactiveRegions[packedImage.id]?.value = region
                            android.util.Log.d("DynamicAtlasPool", "🟢 REACTIVE UPDATE: photo ${packedImage.id.lastPathSegment} now available")
                        }

                        // Trigger callback
                        onPhotoReady(packedImage.id, region)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to draw photo ${packedImage.id} in background", e)
                }
            }

            Log.d(TAG, "Background atlas population complete: ${packResult.packedImages.size} photos drawn")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during background atlas population", e)
        }
    }

    /**
     * Generate multiple atlases using optimal strategy for the given photos
     */
    suspend fun generateMultiAtlas(
        photoUris: List<Uri>,
        lodLevel: LODLevel,
        currentZoom: Float,
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER,
        priorityMapping: Map<Uri, PhotoPriority> = emptyMap(),
        onPhotoReady: (Uri, AtlasRegion) -> Unit = { _, _ -> }
    ): MultiAtlasResult = trace(BenchmarkLabels.ATLAS_GENERATOR_GENERATE_ATLAS) {
        if (photoUris.isEmpty()) {
            return@trace MultiAtlasResult(
                atlases = emptyList(),
                failed = emptyList(),
                totalPhotos = 0,
                processedPhotos = 0,
                strategy = getDefaultStrategy()
            )
        }

        // Step 1: Process all photos for the specified LOD level with priority-based LOD boost
        val processedPhotos = processPhotosForLOD(photoUris, lodLevel, scaleStrategy, priorityMapping)
        val processingFailed = photoUris.filterNot { uri -> processedPhotos.any { it.id == uri } }

        Log.d(TAG, "Photo processing complete: ${processedPhotos.size}/${photoUris.size} succeeded, ${processingFailed.size} failed at $lodLevel")
        if (processingFailed.isNotEmpty()) {
            Log.w(TAG, "Failed to process ${processingFailed.size} photos at $lodLevel: ${processingFailed.take(3)}")
        }

        if (processedPhotos.isEmpty()) {
            return@trace MultiAtlasResult(
                atlases = emptyList(),
                failed = photoUris,
                totalPhotos = photoUris.size,
                processedPhotos = 0,
                strategy = getDefaultStrategy()
            )
        }

        // Step 2: Determine optimal atlas generation strategy
        val strategy = determineAtlasStrategy(processedPhotos, lodLevel, currentZoom)

        // Step 3: Distribute photos across atlases
        val photoGroups = distributePhotos(processedPhotos, strategy, lodLevel)
        Log.d(TAG, "Photo distribution: ${processedPhotos.size} photos → ${photoGroups.size} groups")
        photoGroups.forEachIndexed { index, group ->
            Log.d(TAG, "Group $index: ${group.photos.size} photos, atlas ${group.atlasSize.width}x${group.atlasSize.height}")
        }

        // Step 4: Generate atlases in parallel
        val atlasResults = generateAtlasesInParallel(photoGroups, strategy, lodLevel, currentZoom)

        // Step 5: Collect results and clean up
        val allAtlases = atlasResults.mapNotNull { it.primaryAtlas }
        val generationFailed = atlasResults.flatMap { it.failed }
        val allFailed = processingFailed + generationFailed

        val totalPackedPhotos = allAtlases.sumOf { it.photoCount }

        // Detailed gap analysis logging
        Log.d(TAG, "=== ATLAS GENERATION PIPELINE ANALYSIS ===")
        Log.d(TAG, "Input: ${photoUris.size} photos requested")
        Log.d(TAG, "Step 1 - Photo Processing:")
        Log.d(TAG, "  - Processed successfully: ${processedPhotos.size}")
        Log.d(TAG, "  - Processing failed: ${processingFailed.size}")
        if (processingFailed.isNotEmpty()) {
            Log.w(TAG, "  - Processing failed URIs: ${processingFailed.take(3)}...")
        }

        Log.d(TAG, "Step 2 - Photo Grouping:")
        Log.d(TAG, "  - Photo groups created: ${photoGroups.size}")
        photoGroups.forEachIndexed { index, group ->
            Log.d(TAG, "    Group $index: ${group.photos.size} photos for ${group.atlasSize.width}x${group.atlasSize.height} atlas")
        }

        Log.d(TAG, "Step 3 - Atlas Generation:")
        Log.d(TAG, "  - Atlas generation attempts: ${atlasResults.size}")
        Log.d(TAG, "  - Successful atlases: ${allAtlases.size}")
        atlasResults.forEachIndexed { index, result ->
            Log.d(TAG, "    Result $index: ${result.totalPhotos} input, ${result.primaryAtlas?.photoCount ?: 0} packed, ${result.failed.size} failed")
        }

        Log.d(TAG, "Step 4 - Final Results:")
        Log.d(TAG, "  - Total photos packed: $totalPackedPhotos")
        Log.d(TAG, "  - Total photos failed: ${allFailed.size}")
        Log.d(TAG, "  - Success rate: ${if (photoUris.isNotEmpty()) (totalPackedPhotos * 100) / photoUris.size else 0}%")

        if (allFailed.isNotEmpty()) {
            Log.w(TAG, "Failed photos breakdown:")
            Log.w(TAG, "  - Processing failures: ${processingFailed.size}")
            Log.w(TAG, "  - Generation failures: ${generationFailed.size}")
            Log.w(TAG, "  - Sample failed URIs: ${allFailed.take(5)}")
        }

        // Check for photo loss (photos that should have been packed but weren't)
        val expectedPhotos = photoUris.size
        val actualPhotos = totalPackedPhotos + allFailed.size
        if (expectedPhotos != actualPhotos) {
            Log.e(TAG, "PHOTO LOSS DETECTED!")
            Log.e(TAG, "  - Expected photos: $expectedPhotos")
            Log.e(TAG, "  - Accounted photos: $actualPhotos")
            Log.e(TAG, "  - Missing photos: ${expectedPhotos - actualPhotos}")
        }
        Log.d(TAG, "=== END PIPELINE ANALYSIS ===")

        Log.d(TAG, "Atlas generation results: ${allAtlases.size} atlases created, $totalPackedPhotos photos packed, ${allFailed.size} failed")

        // Clean up processed photo bitmaps - direct recycling (not pool) for stability
        cleanupProcessedPhotos(processedPhotos)

        return@trace MultiAtlasResult(
            atlases = allAtlases,
            failed = allFailed,
            totalPhotos = photoUris.size,
            processedPhotos = processedPhotos.size,
            strategy = strategy
        )
    }

    /**
     * Process photos for LOD level with error handling, priority-based LOD boosting, and parallel processing
     * OPTIMIZED: Uses device-aware parallel processing for individual photos
     */
    private suspend fun processPhotosForLOD(
        photoUris: List<Uri>,
        lodLevel: LODLevel,
        scaleStrategy: ScaleStrategy,
        priorityMapping: Map<Uri, PhotoPriority> = emptyMap()
    ): List<ProcessedPhoto> = trace(BenchmarkLabels.ATLAS_GENERATOR_PROCESS_PHOTOS) {
        if (photoUris.isEmpty()) {
            return@trace emptyList()
        }

        // Determine parallel processing limits based on device capabilities and memory pressure
        val memoryStatus = smartMemoryManager.getMemoryStatus()
        val capabilities = deviceCapabilities.getCapabilities()

        val maxParallelPhotos = when {
            memoryStatus.pressureLevel >= MemoryPressure.HIGH -> 2
            capabilities.performanceTier == DeviceCapabilities.PerformanceTier.HIGH -> 6
            capabilities.performanceTier == DeviceCapabilities.PerformanceTier.MEDIUM -> 4
            else -> 2
        }

        Log.d(TAG, "Processing ${photoUris.size} photos with up to $maxParallelPhotos parallel workers (memory: ${memoryStatus.pressureLevel}, performance: ${capabilities.performanceTier})")

        // Process photos in parallel chunks to manage memory usage
        return@trace coroutineScope {
            photoUris.chunked(maxParallelPhotos).flatMap { chunk ->
                chunk.map { uri ->
                    async {
                        currentCoroutineContext().ensureActive()

                        // Use explicit LOD level - streaming system has explicit control
                        val priority = priorityMapping[uri] ?: PhotoPriority.NORMAL
                        val effectiveLODLevel = lodLevel // Always respect the explicitly requested LOD level

                        runCatching {
                            photoLODProcessor.processPhotoForLOD(uri, effectiveLODLevel, scaleStrategy, priority)
                        }.fold(
                            onSuccess = { processed ->
                                if (processed != null) {
                                    if (priority == PhotoPriority.HIGH) {
                                        Log.d(TAG, "HIGH priority photo processed: $uri at max $effectiveLODLevel (original: $lodLevel)")
                                    }
                                    processed
                                } else {
                                    null
                                }
                            },
                            onFailure = { e ->
                                Log.e(TAG, "Failed to process photo: $uri", e)
                                if (e is CancellationException) throw e
                                null
                            }
                        )
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }

    /**
     * Determine optimal atlas generation strategy with memory budget awareness
     */
    private fun determineAtlasStrategy(
        processedPhotos: List<ProcessedPhoto>,
        lodLevel: LODLevel,
        currentZoom: Float
    ): AtlasStrategy {
        val capabilities = deviceCapabilities.getCapabilities()
        val recommendedSizes = deviceCapabilities.getRecommendedAtlasSizes()

        // Apply zoom-based optimization config
        val optimizedSizes = if (optimizationConfig.shouldUseLowQuality(currentZoom)) {
            // Use optimized atlas size for low zoom levels
            val optimizedSize = optimizationConfig.getAtlasSize(currentZoom, capabilities.maxAtlasSize)
            listOf(optimizedSize)
        } else {
            // Use device-recommended sizes for high zoom levels
            recommendedSizes
        }

        val totalPhotoArea = processedPhotos.sumOf { it.scaledSize.width * it.scaledSize.height }
        val estimatedAtlasesNeeded = estimateAtlasesNeeded(totalPhotoArea, lodLevel)

        val memoryStatus = smartMemoryManager.getMemoryStatus()

        // Check if we have high priority photos to determine if priority-based distribution is beneficial
        val hasHighPriorityPhotos = processedPhotos.any { it.priority == PhotoPriority.HIGH }

        val distributionStrategy = when {
            // Critical memory pressure always uses single size to conserve memory
            memoryStatus.pressureLevel >= MemoryPressure.CRITICAL ->
                DistributionStrategy.SINGLE_SIZE

            // High priority photos with good memory conditions use priority-based distribution
            hasHighPriorityPhotos && memoryStatus.pressureLevel < MemoryPressure.CRITICAL ->
                DistributionStrategy.PRIORITY_BASED

            // Single atlas is sufficient for small photo sets
            estimatedAtlasesNeeded <= 1 ->
                DistributionStrategy.SINGLE_SIZE

            // High memory pressure uses single size to reduce memory usage
            memoryStatus.pressureLevel >= MemoryPressure.HIGH ->
                DistributionStrategy.SINGLE_SIZE

            // High performance devices can handle priority-based distribution
            capabilities.performanceTier == DeviceCapabilities.PerformanceTier.HIGH ->
                DistributionStrategy.PRIORITY_BASED

            // Default to multi-size distribution for optimal atlas utilization
            else -> DistributionStrategy.MULTI_SIZE
        }

        Log.d(TAG, "Strategy selection: zoom=$currentZoom, lowQuality=${optimizationConfig.shouldUseLowQuality(currentZoom)}, hasHighPriorityPhotos=$hasHighPriorityPhotos, estimatedAtlasesNeeded=$estimatedAtlasesNeeded, memoryPressure=${memoryStatus.pressureLevel}, performanceTier=${capabilities.performanceTier} → $distributionStrategy")

        // TEMPORARILY DISABLED: Memory safety cautions for testing
        val maxParallelAtlases = when {
            // memoryStatus.pressureLevel >= SmartMemoryManager.MemoryPressure.HIGH -> 1
            capabilities.performanceTier == DeviceCapabilities.PerformanceTier.HIGH -> 6 // Increased
            capabilities.performanceTier == DeviceCapabilities.PerformanceTier.MEDIUM -> 4 // Increased
            else -> 2 // Increased
        }

        val availableMemoryMB = (memoryStatus.availableBytes / (1024 * 1024)).toInt()
        val totalBudgetMB = (memoryStatus.totalBudgetBytes / (1024 * 1024)).toInt()

        Log.d(TAG, "Memory budget analysis: ${availableMemoryMB}MB available / ${totalBudgetMB}MB total, current pressure: ${memoryStatus.pressureLevel}")

        return AtlasStrategy(
            atlasSizes = optimizedSizes,
            distributionStrategy = distributionStrategy,
            maxParallelAtlases = maxParallelAtlases
        )
    }

    /**
     * Distribute photos across multiple atlases based on strategy
     */
    private fun distributePhotos(
        processedPhotos: List<ProcessedPhoto>,
        strategy: AtlasStrategy,
        lodLevel: LODLevel
    ): List<PhotoGroup> = when (strategy.distributionStrategy) {
        DistributionStrategy.SINGLE_SIZE -> distributeSingleSize(processedPhotos, strategy.atlasSizes.first())
        DistributionStrategy.MULTI_SIZE -> distributeMultiSize(processedPhotos, strategy.atlasSizes, lodLevel)
        DistributionStrategy.PRIORITY_BASED -> distributePriorityBased(processedPhotos, strategy.atlasSizes, lodLevel)
    }

    /**
     * Distribute photos using single atlas size
     */
    private fun distributeSingleSize(
        processedPhotos: List<ProcessedPhoto>,
        atlasSize: IntSize
    ): List<PhotoGroup> {
        val groups = mutableListOf<PhotoGroup>()
        val remainingPhotos = processedPhotos.toMutableList()

        while (remainingPhotos.isNotEmpty()) {
            val groupPhotos = mutableListOf<ProcessedPhoto>()
            var currentArea = 0
            val maxArea = (atlasSize.width * atlasSize.height * UTILIZATION_TARGET).toInt()

            val iterator = remainingPhotos.iterator()
            while (iterator.hasNext() && currentArea < maxArea) {
                val photo = iterator.next()
                val photoArea = photo.scaledSize.width * photo.scaledSize.height

                if (currentArea + photoArea <= maxArea) {
                    groupPhotos.add(photo)
                    currentArea += photoArea
                    iterator.remove()
                }
            }

            if (groupPhotos.isNotEmpty()) {
                groups.add(PhotoGroup(groupPhotos, atlasSize))
            } else {
                // If no photos fit, take the first one anyway (emergency fallback)
                if (remainingPhotos.isNotEmpty()) {
                    groups.add(PhotoGroup(listOf(remainingPhotos.removeAt(0)), atlasSize))
                }
            }
        }

        return groups
    }

    /**
     * Distribute photos using multiple atlas sizes
     */
    private fun distributeMultiSize(
        processedPhotos: List<ProcessedPhoto>,
        atlasSizes: List<IntSize>,
        lodLevel: LODLevel
    ): List<PhotoGroup> {
        val groups = mutableListOf<PhotoGroup>()
        val remainingPhotos = processedPhotos.toMutableList()

        // Sort photos by size (largest first)
        remainingPhotos.sortByDescending { it.scaledSize.width * it.scaledSize.height }

        // LOD-aware minimum photos threshold - high LOD levels with large photos need smaller minimums
        val minPhotosPerAtlas = when {
            lodLevel.level >= 5 -> 1 // LOD 5 (1024px): Allow single photos per atlas
            lodLevel.level >= 4 -> 2 // LOD 4 (512px): Allow 2 photos minimum
            lodLevel.level >= 2 -> 3 // LOD 2-3 (128px-256px): Allow 3 photos minimum
            else -> MIN_PHOTOS_PER_ATLAS_DEFAULT // LOD 0-1: Use default (4 photos)
        }

        Log.d(TAG, "Using $minPhotosPerAtlas minimum photos per atlas for $lodLevel")

        // For LOD 5, prioritize largest atlas sizes first to accommodate large photos
        val sortedAtlasSizes = if (lodLevel.level >= 5) {
            atlasSizes.sortedByDescending { it.width * it.height }
        } else {
            atlasSizes
        }

        Log.d(TAG, "Atlas size priority for $lodLevel: ${sortedAtlasSizes.map { "${it.width}x${it.height}" }}")
        Log.d(TAG, "Photo sizes: ${remainingPhotos.map { "${it.scaledSize.width}x${it.scaledSize.height}" }}")

        // Optimized distribution with multiple passes to maximize utilization
        for (atlasSize in sortedAtlasSizes) {
            if (remainingPhotos.isEmpty()) break

            // Multiple passes to fill atlas efficiently
            var passCount = 0
            val maxPasses = 3 // Limit passes to avoid infinite loops

            while (remainingPhotos.isNotEmpty() && passCount < maxPasses) {
                val groupPhotos = mutableListOf<ProcessedPhoto>()
                var currentArea = 0
                val maxArea = (atlasSize.width * atlasSize.height * UTILIZATION_TARGET).toInt()

                Log.d(TAG, "Pass ${passCount + 1}: Filling ${atlasSize.width}x${atlasSize.height} atlas (max area: $maxArea)")

                // Try to fill as much as possible in this pass
                var addedInThisPass = false
                val iterator = remainingPhotos.iterator()
                while (iterator.hasNext()) {
                    val photo = iterator.next()
                    val photoArea = photo.scaledSize.width * photo.scaledSize.height

                    // Check if photo will fit in shelf packing algorithm
                    val paddedHeight = photo.scaledSize.height + ATLAS_PADDING * 2
                    val canFitInShelf = canPhotoFitInAtlas(photo, atlasSize, groupPhotos)

                    // Area check + shelf fitting check
                    if (currentArea + photoArea <= maxArea && canFitInShelf) {
                        groupPhotos.add(photo)
                        currentArea += photoArea
                        iterator.remove()
                        addedInThisPass = true
                        Log.d(TAG, "Added photo ${photo.scaledSize.width}x${photo.scaledSize.height} (area: $photoArea, total: $currentArea/$maxArea)")
                    } else if (!canFitInShelf) {
                        Log.d(TAG, "Skipping photo ${photo.scaledSize.width}x${photo.scaledSize.height} for ${atlasSize.width}x${atlasSize.height} - won't fit in shelf (height: $paddedHeight)")
                    }
                }

                // Create atlas if we have enough photos to justify it
                val utilizationPercent = if (maxArea > 0) currentArea.toFloat() / maxArea else 0f
                val minUtilization = if (lodLevel.level >= 4) 0.3f else 0.5f // Lower threshold for high LOD
                val hasEnoughPhotos = groupPhotos.size >= minPhotosPerAtlas

                if (groupPhotos.isNotEmpty() && (hasEnoughPhotos && utilizationPercent >= minUtilization || passCount == maxPasses - 1)) {
                    groups.add(PhotoGroup(groupPhotos, atlasSize))
                    Log.d(TAG, "Created group with ${groupPhotos.size} photos for ${atlasSize.width}x${atlasSize.height} (${(utilizationPercent * 100).toInt()}% utilization)")
                    break // Move to next atlas size
                } else if (groupPhotos.isNotEmpty()) {
                    // Put photos back if utilization is too low
                    Log.d(TAG, "Utilization too low (${(utilizationPercent * 100).toInt()}%), putting ${groupPhotos.size} photos back")
                    remainingPhotos.addAll(0, groupPhotos)
                }

                passCount++
                if (!addedInThisPass) break // No progress made, move to next size
            }
        }

        // Handle any remaining photos with memory-aware strategy to prevent thrashing
        if (remainingPhotos.isNotEmpty()) {
            Log.w(TAG, "Handling ${remainingPhotos.size} remaining photos for $lodLevel")

            if (lodLevel.level >= 5) {
                // For LOD 5, prioritize memory efficiency over number of atlases
                Log.d(TAG, "LOD 5 memory-aware distribution: ${remainingPhotos.size} remaining photos")

                // Try to add remaining photos to existing groups first (better utilization)
                val updatedGroups = mutableListOf<PhotoGroup>()
                val stillRemaining = remainingPhotos.toMutableList()

                for (group in groups) {
                    val currentArea = group.photos.sumOf { it.scaledSize.width * it.scaledSize.height }
                    val maxArea = (group.atlasSize.width * group.atlasSize.height * UTILIZATION_TARGET).toInt()
                    val availableArea = maxArea - currentArea

                    val photosToAdd = mutableListOf<ProcessedPhoto>()
                    val iterator = stillRemaining.iterator()

                    while (iterator.hasNext() && availableArea > 0) {
                        val photo = iterator.next()
                        val photoArea = photo.scaledSize.width * photo.scaledSize.height

                        // More generous fitting check for remaining photos
                        val paddedHeight = photo.scaledSize.height + 4
                        val canFitInShelf = paddedHeight <= (group.atlasSize.height - 100) // More generous buffer

                        if (photoArea <= availableArea && canFitInShelf) {
                            photosToAdd.add(photo)
                            iterator.remove()
                            Log.d(TAG, "Added remaining photo ${photo.scaledSize.width}x${photo.scaledSize.height} to existing group (better utilization)")
                        }
                    }

                    if (photosToAdd.isNotEmpty()) {
                        updatedGroups.add(PhotoGroup(group.photos + photosToAdd, group.atlasSize))
                        Log.d(TAG, "Enhanced group: ${group.photos.size + photosToAdd.size} total photos")
                    } else {
                        updatedGroups.add(group)
                    }
                }

                // Replace original groups with updated ones
                groups.clear()
                groups.addAll(updatedGroups)

                // For truly remaining photos, create ONE optimally-sized atlas instead of multiple small ones
                if (stillRemaining.isNotEmpty()) {
                    Log.w(TAG, "Creating single optimized atlas for ${stillRemaining.size} remaining photos to prevent memory thrashing")

                    // Calculate optimal atlas size based on total area and photo constraints
                    val totalRemainingArea = stillRemaining.sumOf { it.scaledSize.width * it.scaledSize.height }
                    val largestPhoto = stillRemaining.maxByOrNull { it.scaledSize.height } ?: stillRemaining.first()
                    val maxHeight = largestPhoto.scaledSize.height + 4

                    // Choose atlas size that can fit all photos efficiently, BUT respect available atlas sizes
                    val idealAtlasSize = when {
                        maxHeight > 1020 && totalRemainingArea > 6000000 -> IntSize(4096, 4096) // Need 4K for large photos
                        totalRemainingArea > 2000000 -> IntSize(4096, 4096) // Dense packing benefits from 4K
                        else -> IntSize(2048, 2048) // Can fit in 2K
                    }

                    // CRITICAL: Respect the atlas sizes provided to this method - don't exceed them
                    val availableAtlasSize = atlasSizes.find { it.width >= idealAtlasSize.width } ?: atlasSizes.last()

                    Log.d(TAG, "Remaining photos atlas sizing: ideal=${idealAtlasSize.width}x${idealAtlasSize.height}, available sizes=${atlasSizes.map { "${it.width}x${it.height}" }}, chosen=${availableAtlasSize.width}x${availableAtlasSize.height}")

                    // Create single group for all remaining photos
                    groups.add(PhotoGroup(stillRemaining, availableAtlasSize))
                    Log.d(TAG, "Single optimized group: ${stillRemaining.size} photos in ${availableAtlasSize.width}x${availableAtlasSize.height} (area: $totalRemainingArea px², max height: $maxHeight)")
                }
            } else {
                // For lower LOD levels, use single size distribution for remaining photos
                // Use the smallest available atlas size to respect priority-based sizing
                val smallestAtlasSize = atlasSizes.minByOrNull { it.width } ?: atlasSizes.last()
                groups.addAll(distributeSingleSize(remainingPhotos, smallestAtlasSize))
                Log.d(TAG, "Lower LOD fallback: using ${smallestAtlasSize.width}x${smallestAtlasSize.height} for ${remainingPhotos.size} remaining photos")
            }
        }

        return groups
    }

    /**
     * Distribute photos based on priority with different atlas sizes for each priority level
     */
    private fun distributePriorityBased(
        processedPhotos: List<ProcessedPhoto>,
        atlasSizes: List<IntSize>,
        lodLevel: LODLevel
    ): List<PhotoGroup> {
        // Separate photos by priority
        val highPriorityPhotos = processedPhotos.filter {
            it.priority == PhotoPriority.HIGH
        }
        val normalPriorityPhotos = processedPhotos.filter {
            it.priority == PhotoPriority.NORMAL
        }

        Log.d(TAG, "Priority-based distribution: ${highPriorityPhotos.size} high priority, ${normalPriorityPhotos.size} normal priority photos")

        val allGroups = mutableListOf<PhotoGroup>()

        // Distribute HIGH priority photos using full atlas sizes
        // FIXED: Respect explicit LOD level instead of overriding to LEVEL_7
        if (highPriorityPhotos.isNotEmpty()) {
            val highPriorityGroups = distributeMultiSize(highPriorityPhotos, atlasSizes, lodLevel)
            // Use the explicitly requested LOD level, no longer override to max LOD
            allGroups.addAll(highPriorityGroups)
            Log.d(TAG, "High priority photos distributed into ${highPriorityGroups.size} dedicated atlases at explicit LOD ($lodLevel)")
        }

        // Distribute NORMAL priority photos using LOD-appropriate atlas sizes
        if (normalPriorityPhotos.isNotEmpty()) {
            val normalAtlasSizes = getNormalPriorityAtlasSizes(lodLevel, atlasSizes)
            val normalPriorityGroups = distributeMultiSize(normalPriorityPhotos, normalAtlasSizes, lodLevel)
            allGroups.addAll(normalPriorityGroups)
            Log.d(TAG, "Normal priority photos distributed into ${normalPriorityGroups.size} atlases using ${normalAtlasSizes.map { "${it.width}x${it.height}" }} sizes")
        }

        Log.d(TAG, "Priority-based distribution complete: ${allGroups.size} total atlas groups")
        return allGroups
    }

    /**
     * Get appropriate atlas sizes for normal priority photos based on LOD level
     */
    private fun getNormalPriorityAtlasSizes(lodLevel: LODLevel, fullAtlasSizes: List<IntSize>): List<IntSize> = when {
        // For high LOD levels (4+), normal photos can use smaller atlases
        lodLevel.level >= 4 -> {
            // Use 2K atlas for normal priority photos when LOD is high
            listOf(IntSize(ATLAS_2K, ATLAS_2K))
        }

        // For medium LOD levels (2-3), use 2K-4K atlases
        lodLevel.level >= 2 -> {
            // Prefer smaller sizes for normal priority
            fullAtlasSizes.filter { it.width <= ATLAS_4K }
        }

        // For low LOD levels (0-1), use all available sizes efficiently
        else -> {
            fullAtlasSizes
        }
    }

    /**
     * Generate atlases in parallel
     */
    private suspend fun generateAtlasesInParallel(
        photoGroups: List<PhotoGroup>,
        strategy: AtlasStrategy,
        lodLevel: LODLevel,
        currentZoom: Float
    ): List<EnhancedAtlasGenerator.EnhancedAtlasResult> = coroutineScope {
        photoGroups.chunked(strategy.maxParallelAtlases).flatMap { chunk ->
            chunk.map { group ->
                async {
                    generateSingleAtlas(group, lodLevel, currentZoom)
                }
            }.awaitAll()
        }
    }

    /**
     * Generate a single atlas from a photo group
     */
    private suspend fun generateSingleAtlas(
        photoGroup: PhotoGroup,
        lodLevel: LODLevel,
        currentZoom: Float
    ): EnhancedAtlasGenerator.EnhancedAtlasResult = trace(BenchmarkLabels.ATLAS_GENERATOR_GENERATE_ATLAS) {
        val imagesToPack = photoGroup.photos.map { ImageToPack(it) }

        Log.d(TAG, "Generating atlas: ${photoGroup.photos.size} photos → ${photoGroup.atlasSize.width}x${photoGroup.atlasSize.height} atlas at $lodLevel")

        // Pack photos into atlas
        val packResult = trace(BenchmarkLabels.ATLAS_GENERATOR_PACK_TEXTURES) {
            val texturePacker = ShelfTexturePacker(photoGroup.atlasSize, ATLAS_PADDING)
            texturePacker.pack(imagesToPack)
        }

        Log.d(TAG, "Packing result: ${packResult.packedImages.size}/${imagesToPack.size} photos packed, ${packResult.failed.size} failed, utilization: ${String.format("%.1f", packResult.utilization * 100)}%")
        if (packResult.failed.isNotEmpty()) {
            Log.w(TAG, "Packing failures: ${packResult.failed.take(3).map { "${it.id}" }}")
        }

        if (packResult.packedImages.isEmpty()) {
            Log.e(TAG, "Atlas generation failed: no photos could be packed in ${photoGroup.atlasSize.width}x${photoGroup.atlasSize.height} atlas")
            return@trace EnhancedAtlasGenerator.EnhancedAtlasResult(
                primaryAtlas = null,
                additionalAtlases = emptyList(),
                failed = photoGroup.photos.map { it.id },
                totalPhotos = photoGroup.photos.size,
                processedPhotos = photoGroup.photos.size,
                strategy = EnhancedAtlasGenerator.AtlasStrategy.SINGLE_ATLAS,
                fallbackUsed = true
            )
        }

        // Handle partial packing failures - if some photos failed, log them but continue with successful ones
        if (packResult.failed.isNotEmpty()) {
            Log.w(TAG, "Partial packing failure: ${packResult.failed.size}/${photoGroup.photos.size} photos failed in ${photoGroup.atlasSize.width}x${photoGroup.atlasSize.height} atlas")
            packResult.failed.forEach { failedImage ->
                Log.w(TAG, "Failed photo: ${failedImage.id}, size: ${failedImage.size.width}x${failedImage.size.height}")
            }
        }

        // Create atlas bitmap
        val atlasBitmap = createAtlasBitmap(photoGroup.photos, packResult, photoGroup.atlasSize, currentZoom)

        // Initialize reactive regions for all photos (will be populated during background processing)
        val reactiveRegions = mutableMapOf<Uri, androidx.compose.runtime.MutableState<AtlasRegion?>>()
        photoGroup.photos.forEach { processedPhoto ->
            reactiveRegions[processedPhoto.id] = androidx.compose.runtime.mutableStateOf(null)
        }

        // Immediately populate reactive regions for successfully packed photos
        val photosMap = photoGroup.photos.associateBy { it.id }
        packResult.packedImages.forEach { packedImage ->
            val processedPhoto = photosMap[packedImage.id]
            if (processedPhoto != null) {
                val region = AtlasRegion(
                    photoId = packedImage.id,
                    atlasRect = androidx.compose.ui.geometry.Rect(
                        left = packedImage.rect.left,
                        top = packedImage.rect.top,
                        right = packedImage.rect.right,
                        bottom = packedImage.rect.bottom
                    ),
                    originalSize = processedPhoto.originalSize,
                    scaledSize = processedPhoto.scaledSize,
                    aspectRatio = processedPhoto.aspectRatio,
                    lodLevel = lodLevel.level
                )
                reactiveRegions[packedImage.id]?.value = region
            }
        }

        // Use effective LOD level if specified, otherwise use the original LOD level
        val effectiveLODLevel = photoGroup.effectiveLODLevel ?: lodLevel

        // Create atlas using only reactive regions
        Log.d(TAG, "Creating TextureAtlas with lodLevel=$effectiveLODLevel for ${packResult.packedImages.size} photos")
        val atlas = TextureAtlas(
            bitmap = atlasBitmap,
            reactiveRegions = reactiveRegions,
            lodLevel = effectiveLODLevel,
            size = photoGroup.atlasSize
        )
        Log.d(TAG, "Created TextureAtlas: actual lodLevel=${atlas.lodLevel.name}, resolution=${atlas.lodLevel.resolution}")

        val memoryKey = AtlasKey(
            lodLevel = lodLevel,
            atlasSize = photoGroup.atlasSize,
            photosHash = photoGroup.photos.map { it.id }.hashCode()
        )

        // CRITICAL: Protect atlas BEFORE registration to prevent race condition
        // Emergency cleanup can trigger immediately during registration if memory pressure is high
        smartMemoryManager.addProtectedAtlases(setOf(memoryKey))
        Log.d(TAG, "Pre-protected atlas $memoryKey before registration to prevent emergency cleanup race")

        // Now register atlas - it's already protected from emergency cleanup
        smartMemoryManager.registerAtlas(memoryKey, atlas)

        return@trace EnhancedAtlasGenerator.EnhancedAtlasResult(
            primaryAtlas = atlas,
            additionalAtlases = emptyList(),
            failed = packResult.failed.map { it.id },
            totalPhotos = photoGroup.photos.size,
            processedPhotos = photoGroup.photos.size,
            strategy = EnhancedAtlasGenerator.AtlasStrategy.SINGLE_ATLAS,
            fallbackUsed = false
        )
    }

    /**
     * Create atlas bitmap from packed photos
     */
    private suspend fun createAtlasBitmap(
        processedPhotos: List<ProcessedPhoto>,
        packResult: PackResult,
        atlasSize: IntSize,
        currentZoom: Float
    ): Bitmap {
        val bitmapConfig = optimizationConfig.getBitmapConfig(currentZoom)
        val atlasBitmap = bitmapPool.acquire(atlasSize.width, atlasSize.height, bitmapConfig)

        Log.d(TAG, "Creating atlas bitmap: ${atlasSize.width}x${atlasSize.height} with config $bitmapConfig (zoom: $currentZoom, lowQuality: ${optimizationConfig.shouldUseLowQuality(currentZoom)})")
        val photosMap = processedPhotos.associateBy { it.id }

        trace(BenchmarkLabels.ATLAS_GENERATOR_SOFTWARE_CANVAS) {
            val canvas = Canvas(atlasBitmap)
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

            for (packedImage in packResult.packedImages) {
                currentCoroutineContext().ensureActive()

                val processedPhoto = photosMap[packedImage.id]
                if (processedPhoto != null && !processedPhoto.bitmap.isRecycled) {
                    canvas.drawBitmap(
                        processedPhoto.bitmap,
                        null,
                        android.graphics.RectF(
                            packedImage.rect.left,
                            packedImage.rect.top,
                            packedImage.rect.right,
                            packedImage.rect.bottom
                        ),
                        paint
                    )
                }
            }
        }

        return atlasBitmap
    }

    /**
     * Clean up processed photo bitmaps with direct recycling (no pool)
     */
    private fun cleanupProcessedPhotos(processedPhotos: List<ProcessedPhoto>) {
        processedPhotos.forEach { photo ->
            if (!photo.bitmap.isRecycled) {
                photo.bitmap.recycle()
            }
        }
    }

    /**
     * Release atlas bitmap back to pool instead of recycling
     */
    fun releaseAtlasBitmap(atlasBitmap: Bitmap) {
        if (!atlasBitmap.isRecycled) {
            bitmapPool.release(atlasBitmap)
        }
    }

    /**
     * Estimate number of atlases needed based on LOD level and device capabilities
     */
    private fun estimateAtlasesNeeded(totalPhotoArea: Int, lodLevel: LODLevel): Int {
        // Get optimal atlas size for this LOD level and device
        val recommendedSizes = deviceCapabilities.getRecommendedAtlasSizes()
        val optimalAtlasSize = when {
            // High LOD levels (detailed photos) benefit from larger atlases if device supports it
            lodLevel.level >= 4 && recommendedSizes.contains(IntSize(ATLAS_4K, ATLAS_4K)) -> ATLAS_4K
            lodLevel.level >= 2 && recommendedSizes.contains(IntSize(ATLAS_4K, ATLAS_4K)) -> ATLAS_4K
            else -> ATLAS_2K
        }

        val atlasArea = optimalAtlasSize * optimalAtlasSize
        val usableArea = atlasArea * UTILIZATION_TARGET

        // Add small buffer for packing inefficiencies at higher LOD levels
//        val packingEfficiency = when {
//            lodLevel.level >= 4 -> 0.9f // High detail photos pack less efficiently
//            lodLevel.level >= 2 -> 0.95f // Medium photos have good packing
//            else -> 1.0f // Small thumbnails pack very efficiently
//        }

        val adjustedUsableArea = (usableArea).toInt()
        val estimatedCount = (totalPhotoArea.toFloat() / adjustedUsableArea).toInt() + 1

        Log.d(TAG, "Atlas estimation: ${totalPhotoArea}px² total area, ${optimalAtlasSize}x$optimalAtlasSize atlas, ${adjustedUsableArea}px² usable → $estimatedCount atlases (LOD $lodLevel)")

        return estimatedCount
    }

    /**
     * Get default atlas strategy
     */
    private fun getDefaultStrategy(): AtlasStrategy = AtlasStrategy(
        atlasSizes = listOf(IntSize(ATLAS_2K, ATLAS_2K)),
        distributionStrategy = DistributionStrategy.SINGLE_SIZE,
        maxParallelAtlases = 1
    )

    /**
     * Dynamically calculate if a photo can fit in an atlas based on current shelf usage.
     * This replaces the magic number approach with proper shelf packing simulation.
     */
    private fun canPhotoFitInAtlas(
        photo: ProcessedPhoto,
        atlasSize: IntSize,
        currentPhotos: List<ProcessedPhoto>
    ): Boolean {
        val paddedPhotoSize = IntSize(
            width = photo.scaledSize.width + ATLAS_PADDING * 2,
            height = photo.scaledSize.height + ATLAS_PADDING * 2
        )

        // Check if photo exceeds atlas dimensions
        if (paddedPhotoSize.width > atlasSize.width || paddedPhotoSize.height > atlasSize.height) {
            return false
        }

        // If no photos in current group, this photo will definitely fit
        if (currentPhotos.isEmpty()) {
            return true
        }

        // Simulate shelf packing to calculate remaining height
        val shelfHeights = mutableListOf<Int>()
        var currentShelfHeight = 0

        // Sort current photos by height (descending) to simulate shelf packing algorithm
        val sortedPhotos = currentPhotos.sortedByDescending { it.scaledSize.height }

        for (existingPhoto in sortedPhotos) {
            val existingPaddedSize = IntSize(
                width = existingPhoto.scaledSize.width + ATLAS_PADDING * 2,
                height = existingPhoto.scaledSize.height + ATLAS_PADDING * 2
            )

            // Try to fit in existing shelves
            var fitsInExistingShelf = false
            for (i in shelfHeights.indices) {
                if (existingPaddedSize.height <= shelfHeights[i]) {
                    // Photo fits in this shelf height
                    fitsInExistingShelf = true
                    break
                }
            }

            // If doesn't fit in existing shelves, create new shelf
            if (!fitsInExistingShelf) {
                shelfHeights.add(existingPaddedSize.height)
                currentShelfHeight += existingPaddedSize.height
            }
        }

        // Check if new photo can fit in existing shelves
        for (shelfHeight in shelfHeights) {
            if (paddedPhotoSize.height <= shelfHeight) {
                return true // Fits in existing shelf
            }
        }

        // Check if new photo can fit in a new shelf
        val remainingHeight = atlasSize.height - currentShelfHeight
        return paddedPhotoSize.height <= remainingHeight
    }

    /**
     * Photo group for atlas generation
     */
    data class PhotoGroup(
        val photos: List<ProcessedPhoto>,
        val atlasSize: IntSize,
        val effectiveLODLevel: LODLevel? = null
    )
}
