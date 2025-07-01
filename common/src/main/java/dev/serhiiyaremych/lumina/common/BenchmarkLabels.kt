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
    
    // AtlasGenerator trace labels
    const val ATLAS_GENERATOR_GENERATE_ATLAS = "AtlasGenerator.generateAtlas"
    const val ATLAS_GENERATOR_PROCESS_PHOTOS = "AtlasGenerator.processPhotos"
    const val ATLAS_GENERATOR_PACK_TEXTURES = "AtlasGenerator.packTextures"
    const val ATLAS_GENERATOR_CREATE_ATLAS_BITMAP = "AtlasGenerator.createAtlasBitmap"
    
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
