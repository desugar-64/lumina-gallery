package dev.serhiiyaremych.lumina.common

/**
 * Centralized benchmark trace labels for atlas texture system performance tracking.
 * 
 * These labels are used in both app and benchmark modules to ensure consistency
 * between trace collection and benchmark measurement.
 */
object BenchmarkLabels {
    
    // Primary optimization targets (mentioned in implementation plan)
    const val PHOTO_LOD_SCALE_BITMAP = "PhotoLODProcessor.scaleBitmap"
    const val ATLAS_GENERATOR_SOFTWARE_CANVAS = "AtlasGenerator.softwareCanvas"
    
    // PhotoLODProcessor trace labels
    const val PHOTO_LOD_PROCESS_PHOTO = "PhotoLODProcessor.processPhoto"
    const val PHOTO_LOD_LOAD_BITMAP = "PhotoLODProcessor.loadBitmap"
    
    // Disk I/O Operations (File System Access)
    const val PHOTO_LOD_DISK_OPEN_INPUT_STREAM = "PhotoLODProcessor.diskOpenInputStream"
    const val PHOTO_LOD_DISK_READ_FILE_HEADER = "PhotoLODProcessor.diskReadFileHeader"
    
    // Memory I/O Operations (Bitmap Processing in RAM)
    const val PHOTO_LOD_MEMORY_DECODE_BOUNDS = "PhotoLODProcessor.memoryDecodeBounds"
    const val PHOTO_LOD_MEMORY_DECODE_BITMAP = "PhotoLODProcessor.memoryDecodeBitmap"
    const val PHOTO_LOD_MEMORY_SAMPLE_SIZE_CALC = "PhotoLODProcessor.memorySampleSizeCalc"
    
    // PhotoScaler trace labels (Hardware-accelerated operations)
    const val PHOTO_SCALER_SCALE = "PhotoScaler.scale"
    const val PHOTO_SCALER_CREATE_SCALED_BITMAP = "PhotoScaler.createScaledBitmap"
    const val PHOTO_SCALER_CREATE_CROPPED_BITMAP = "PhotoScaler.createCroppedBitmap"
    const val PHOTO_SCALER_CALCULATE_DIMENSIONS = "PhotoScaler.calculateDimensions"
    
    // Memory Management (Load/Unload operations)
    const val ATLAS_MEMORY_BITMAP_ALLOCATE = "Atlas.bitmapAllocate"
    const val ATLAS_MEMORY_BITMAP_RECYCLE = "Atlas.bitmapRecycle"
    const val ATLAS_MEMORY_ATLAS_CLEANUP = "Atlas.atlasCleanup"
    const val ATLAS_MEMORY_PROCESSED_PHOTO_CLEANUP = "Atlas.processedPhotoCleanup"
    
    // Bitmap Pool trace labels
    const val BITMAP_POOL_ACQUIRE = "BitmapPool.acquire"
    const val BITMAP_POOL_RELEASE = "BitmapPool.release"
    const val BITMAP_POOL_CLEAR_PRESSURE = "BitmapPool.clearPressure"
    
    // AtlasGenerator trace labels
    const val ATLAS_GENERATOR_GENERATE_ATLAS = "AtlasGenerator.generateAtlas"
    const val ATLAS_GENERATOR_PROCESS_PHOTOS = "AtlasGenerator.processPhotos"
    const val ATLAS_GENERATOR_PACK_TEXTURES = "AtlasGenerator.packTextures"
    const val ATLAS_GENERATOR_CREATE_ATLAS_BITMAP = "AtlasGenerator.createAtlasBitmap"
    
    // TexturePacker trace labels (Algorithm performance)
    const val TEXTURE_PACKER_PACK_ALGORITHM = "TexturePacker.packAlgorithm"
    const val TEXTURE_PACKER_SORT_IMAGES = "TexturePacker.sortImages"
    const val TEXTURE_PACKER_PACK_SINGLE_IMAGE = "TexturePacker.packSingleImage"
    const val TEXTURE_PACKER_FIND_SHELF_FIT = "TexturePacker.findShelfFit"
    const val TEXTURE_PACKER_CREATE_NEW_SHELF = "TexturePacker.createNewShelf"
    
    // AtlasManager trace labels
    const val ATLAS_MANAGER_UPDATE_VISIBLE_CELLS = "AtlasManager.updateVisibleCells"
    const val ATLAS_MANAGER_SELECT_LOD_LEVEL = "AtlasManager.selectLODLevel"
    const val ATLAS_MANAGER_GENERATE_ATLAS = "AtlasManager.generateAtlas"
    
    
    // UI test tags for UIAutomator
    const val GALLERY_CANVAS_TEST_TAG = "gallery_canvas"
    
    // Atlas state descriptions for idleness tracking
    const val ATLAS_STATE_GENERATING = "ATLAS-GENERATING"
    const val ATLAS_STATE_IDLE = "ATLAS-IDLE"
    
    // Compose idleness state descriptions for UI animation tracking
    const val COMPOSE_STATE_ANIMATING = "COMPOSE-ANIMATING"
    const val COMPOSE_STATE_IDLE = "COMPOSE-IDLE"
    
    // Benchmark mode intent extras
    const val BENCHMARK_MODE_EXTRA = "benchmark_mode"
    const val BENCHMARK_AUTO_ZOOM = "auto_zoom"
    const val BENCHMARK_AUTO_PAN = "auto_pan"
}
