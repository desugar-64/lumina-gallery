# Atlas System Design for Photo Gallery Application

## Overview

This document outlines a comprehensive atlas-based rendering system for an Android photo gallery application that displays user photos on a zoomable hex grid canvas. The system must handle hundreds to thousands of photos with varying sizes, aspect ratios, and resolutions while maintaining 60fps performance.

> **📋 Implementation Reference**  
> This document serves as the comprehensive reference for atlas system design and implementation details. When implementing the atlas system in LuminaGallery, refer to this document for:
> - Detailed architectural patterns and interfaces
> - Performance targets and optimization strategies  
> - Algorithm specifications and implementation guidelines
> - Memory management and caching strategies
> - Platform-specific considerations and constraints
> 
> **Use this as a checkpoint** when questions arise during implementation - the answers are likely documented here with specific code examples and decision rationale.

## Core Requirements

### Performance Targets
- 60fps pan/zoom on Android devices
- Support for 100-1000+ user photos per view
- Predictable, bounded memory usage
- Smooth LOD transitions during zoom
- Minimal texture switches per frame (<10)

### Content Characteristics
- **Source**: User photos with unpredictable dimensions
- **Sizes**: From small (few hundred pixels) to large (4K+ resolution)
- **Aspect ratios**: Portrait, landscape, square, panoramic (highly variable)
- **Quality**: Must preserve original photo quality at full zoom
- **Processing**: All LOD generation must happen on-device

### Platform Constraints
- Target: Android with Jetpack Compose Canvas
- GPU texture size limits: 2048x2048 (safe) to 4096x4096 (modern devices)
- Memory budget: ~256MB for texture atlases
- Texture formats: ARGB_8888 (quality) or RGB_565 (memory saving)
- **Initial Implementation**: Pure Kotlin + Android SDK
- **Future Optimization**: NDK/C++ for performance-critical paths

## System Architecture

### 1. Multi-Tier Atlas System

```kotlin
interface AtlasManager {
    fun updateViewport(viewport: Rect, zoomLevel: Float)
    fun getTextureForImage(imageId: String, lodLevel: Int): TextureRegion?
    fun preloadRegion(region: Rect, lodLevel: Int)
    fun evictDistantAtlases()
}

data class LODConfiguration(
    val level: Int,
    val resolution: Int,
    val zoomRange: ClosedFloatingPointRange<Float>,
    val atlasStrategy: AtlasStrategy
)

enum class AtlasStrategy {
    FIXED_GRID,      // For uniform thumbnails
    SHELF_PACKING,   // For similar heights
    MAX_RECTS,       // For variable sizes
    STREAMING        // For individual high-res images
}
```

### 2. LOD Level Design

**LOD Definition**: Level 0 = Farthest zoom (tiny thumbnails), Level 5 = Closest zoom (original quality)

| LOD Level | Resolution | Zoom Range | Atlas Strategy | Bitmap Config | Use Case |
|-----------|------------|------------|----------------|---------------|----------|
| 0 | 32px | 0.0-0.25 | FIXED_GRID | RGB_565 | Far overview (photos are tiny dots) |
| 1 | 64px | 0.25-0.5 | FIXED_GRID | RGB_565 | Small thumbnails |
| 2 | 128px | 0.5-1.0 | FIXED_GRID | RGB_565 | Medium thumbnails |
| 3 | 256px | 1.0-2.0 | SHELF_PACKING | ARGB_8888 | Large thumbnails (quality matters) |
| 4 | 512px | 2.0-4.0 | SHELF_PACKING | ARGB_8888 | High quality preview |
| 5 | Original | 4.0+ | NO_ATLAS | Original | Full resolution (stream individually) |

**Key Decisions**:
- LOD 0-2: Use RGB_565 (50% memory saving, quality loss invisible at small sizes)
- LOD 0-2: Square crop acceptable (details indistinguishable)
- LOD 3+: Use ARGB_8888 (preserve quality when details visible)
- LOD 3+: Preserve aspect ratio (users can see photo content)
- LOD 5: No atlasing (photos too large, stream individually)

### 3. Spatial Organization

```kotlin
interface SpatialIndexing {
    fun addImage(imageId: String, hexCoord: HexCoord)
    fun queryVisibleImages(viewport: Rect): List<ImageInfo>
    fun getImagesForAtlasRegion(regionKey: AtlasRegionKey): List<ImageInfo>
}

data class AtlasRegionKey(
    val gridX: Int,
    val gridY: Int,
    val lodLevel: Int
)

interface RegionBasedAtlasSystem {
    val regionSize: Int // World units per region (e.g., 1024)
    fun calculateVisibleRegions(viewport: Rect, lodLevel: Int): Set<AtlasRegionKey>
    fun getAtlasForRegion(regionKey: AtlasRegionKey): Atlas?
}
```

### 4. Atlas Generation Pipeline

```kotlin
interface AtlasGenerator {
    fun generateAtlas(request: AtlasRequest): Atlas
    fun supportsProgressiveGeneration(): Boolean
}

data class AtlasRequest(
    val region: AtlasRegionKey,
    val images: List<ImageInfo>,
    val priority: Priority,
    val quality: Quality
)

enum class Priority { HIGH, MEDIUM, LOW }
enum class Quality { PREVIEW, BALANCED, HIGH }

interface ProgressiveAtlasGenerator : AtlasGenerator {
    fun generateQuickPreview(request: AtlasRequest): Atlas
    fun generateFullQuality(request: AtlasRequest): Atlas
}
```

### LOD Generation Pipeline (Photo Scaling)

```kotlin
interface PhotoLODPipeline {
    // Step 1: Load original photo
    fun loadOriginalPhoto(uri: Uri): Bitmap
    
    // Step 2: Generate LOD versions (This is where scaling happens!)
    fun generateLOD(
        originalPhoto: Bitmap,
        targetLOD: LODLevel,
        resizeStrategy: ResizeStrategy
    ): Bitmap
    
    // Step 3: Pack scaled photos into atlas
    fun packIntoAtlas(
        scaledPhotos: List<ScaledPhoto>,
        atlasConfig: AtlasConfig
    ): Atlas
}

// The complete flow
interface LODGenerationFlow {
    /**
     * Full pipeline:
     * 1. Load original photo (or from cache)
     * 2. Scale to target LOD size
     * 3. Pack into atlas with other photos
     * 4. Upload atlas to GPU
     */
    fun processPhotosForRegion(
        photos: List<PhotoInfo>,
        lodLevel: Int,
        regionKey: AtlasRegionKey
    ): Atlas {
        val scaledPhotos = photos.map { photo ->
            // THIS IS WHERE SCALING HAPPENS
            val original = loadPhoto(photo.uri)
            val scaled = scaleToLOD(original, lodLevel)
            ScaledPhoto(photo.id, scaled)
        }
        
        return packIntoAtlas(scaledPhotos, getAtlasConfig(lodLevel))
    }
}

// Scaling implementation details
interface PhotoScaler {
    fun scaleToLOD(source: Bitmap, lodLevel: Int): Bitmap {
        val targetSize = when(lodLevel) {
            0 -> 32    // Far zoom - tiny thumbnails
            1 -> 64    // Small thumbnails
            2 -> 128   // Medium thumbnails
            3 -> 256   // Large thumbnails
            4 -> 512   // High quality preview
            else -> source.width // Original size
        }
        
        return downscale(source, targetSize, ResampleFilter.BILINEAR)
    }
}

// Note on Resampling Filters
// While Lanczos provides the highest theoretical quality, it is significantly slower
// and not practical for a pure Kotlin implementation. Android's native, hardware-accelerated
// bilinear filtering (requested by `Bitmap.createScaledBitmap` with `filter=true`)
// provides an excellent trade-off between performance and quality for generating
// the small thumbnails required for LOD levels.
```

### When Scaling Occurs

1. **First View of Region**: When a hex grid region becomes visible
2. **On-Demand**: When zooming to a new LOD level
3. **Background Pre-generation**: For predicted scroll/zoom targets

### Scaling Triggers Timeline

```
User opens gallery → 
    Background: Start generating LOD 0-2 for all photos
    Immediate: Generate LOD for current viewport

User scrolls →
    Trigger: Generate LODs for newly visible regions
    Background: Predict scroll direction, pre-generate next regions

User zooms in →
    Trigger: Generate higher LOD for visible photos
    Example: Zooming from 0.3x to 0.8x triggers LOD 2 generation

User zooms to specific photo →
    LOD 0-4: Use pre-generated scaled versions
    LOD 5+: Stream original photo (no scaling)
```

### 5. Texture Packing Algorithms

```kotlin
interface TexturePacker {
    fun pack(images: List<ImageRect>, atlasSize: Size): PackResult
}

data class PackResult(
    val packedImages: List<PackedImage>,
    val utilization: Float,
    val failed: List<ImageRect> = emptyList()
)

data class PackedImage(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val rotated: Boolean = false
)

interface PackerFactory {
    fun getPackerForLOD(lodLevel: Int): TexturePacker
}
```

### 6. Memory Management

```kotlin
interface AtlasCache {
    val maxMemoryBudget: Long
    fun get(key: AtlasRegionKey): Atlas?
    fun put(key: AtlasRegionKey, atlas: Atlas)
    fun evictLRU()
    fun getCurrentUsage(): Long
}

interface MemoryMonitor {
    fun onMemoryPressure(level: MemoryPressureLevel)
    fun getRecommendedAtlasCount(): Int
    fun shouldEvictAtlas(atlas: Atlas, viewport: Rect): Boolean
}

enum class MemoryPressureLevel {
    NORMAL, MODERATE, CRITICAL
}
```

### 7. Predictive Loading

```kotlin
interface PredictiveLoader {
    fun predictNextRegions(
        currentViewport: Rect,
        velocity: Vector2,
        currentLOD: Int
    ): Set<AtlasRegionKey>
    
    fun calculateRegionPriority(
        region: AtlasRegionKey,
        viewport: Rect,
        velocity: Vector2
    ): Float
}

data class Vector2(val x: Float, val y: Float)
```

### 8. Rendering Pipeline

```kotlin
interface AtlasRenderer {
    fun render(canvas: Canvas, viewport: Rect, zoom: Float)
    fun setQualityMode(mode: QualityMode)
}

enum class QualityMode {
    PERFORMANCE,  // Prioritize FPS
    BALANCED,     // Balance quality and performance
    QUALITY       // Prioritize visual quality
}

interface RenderOptimizer {
    fun batchDrawCalls(visibleImages: List<VisibleImage>): List<DrawBatch>
    fun shouldUseFallbackLOD(image: ImageInfo, availableLODs: Set<Int>): Int?
}

data class DrawBatch(
    val atlas: Atlas,
    val drawCalls: List<DrawCall>
)
```

## Implementation Strategy

### Phase 1: Basic Atlas System
1. Implement fixed-size atlas generation with shelf packing
2. Create simple LRU cache for atlases
3. Build basic LOD system with 3 levels
4. Test with 100-200 images
5. **Preserve aspect ratios** in all thumbnail generation

### Phase 2: Dynamic Atlas Management  
1. Add spatial indexing for region-based loading
2. Implement **viewport extension** (load beyond visible area)
3. Add progressive atlas generation (preview → full quality)
4. Support 500+ images with aspect ratio grouping

### Phase 3: Advanced Optimization
1. Implement streaming for highest LOD using BitmapRegionDecoder
2. Add multiple packing algorithms based on LOD and aspect ratio
3. Optimize memory management with pressure monitoring
4. Scale to 1000+ images
5. Add **directional pre-loading** based on scroll velocity

### Phase 4: Polish and Performance
1. Add smooth LOD transitions with temporal blending
2. Implement quality modes for different devices
3. Fine-tune viewport extension parameters
4. Add comprehensive performance monitoring

## Key Design Decisions

### Atlas Size Selection
- **Default**: 2048x2048 for broad compatibility
- **Query GPU**: Check GL_MAX_TEXTURE_SIZE at runtime
- **Multiple atlases**: Better than one huge atlas
- **Memory calculation**: Width × Height × 4 bytes (ARGB_8888)

### Packing Algorithm Selection
```
FOR user photo galleries with variable sizes:

IF generating uniform thumbnail grid (LOD 0-2):
    USE FixedGrid with standard thumbnail sizes
    (resize photos to fit, maintaining aspect ratio)
ELSE IF medium detail level (LOD 3-4):
    USE MaxRects as primary algorithm
    (best utilization for variable sizes: 85-95%)
    WITH Skyline as fallback for performance
ELSE IF high detail (LOD 5+):
    USE Individual streaming (no atlasing)
    (preserve original quality)

Note: ShelfPacking rarely applicable due to 
varied aspect ratios in user photos
```

### LOD Transition Strategy
- Use hysteresis bands (10% overlap) to prevent flickering
- Implement temporal blending over 100-200ms for smooth transitions
- Preload next LOD level based on zoom velocity
- Keep both LODs in memory during transition

### Memory Management Strategy
```
# Photo gallery specific calculations
Average photo memory usage (ARGB_8888):
- Thumbnail (128px): ~65KB
- Medium (512px): ~1MB  
- Full photo (4K): ~32MB

Target memory usage = min(
    Available RAM × 0.25,
    256MB for atlases + 128MB for streaming
)

Atlas eviction priority = 
    distance_from_viewport × 0.6 + 
    time_since_last_access × 0.3 +
    atlas_efficiency × 0.1

Where atlas_efficiency = used_pixels / total_pixels
(Important for variable-sized photos)
```

## Performance Guidelines

### Texture Bandwidth
- Average: < 1 GBps
- Peak: < 3 GBps
- L1 cache miss: < 10%

### Draw Calls
- Target: < 10 texture switches per frame
- Batch by atlas first, then by render state
- Use single draw call per atlas region

### Atlas Generation
- Background thread with priority queue
- Progressive generation: preview (50ms) → full (200ms)
- Use bitmap pools to avoid allocation churn

## Photo-Specific Considerations

### Aspect Ratio Handling
```kotlin
interface PhotoProcessor {
    fun generateThumbnail(
        photo: Bitmap,
        targetSize: Int,
        strategy: ResizeStrategy
    ): Bitmap
}

enum class ResizeStrategy {
    FIT_CENTER,      // Maintain aspect ratio, may have empty space
    CENTER_CROP,     // Fill target size, may crop edges  
    SMART_CROP      // AI-based cropping to preserve important content
}
```

### Viewport Extension Strategy

```kotlin
interface ViewportExtensionStrategy {
    /**
     * Extend visible region to preload adjacent photos
     * This reduces atlas regeneration during scrolling
     */
    fun extendViewport(
        visibleViewport: Rect,
        scrollVelocity: Vector2,
        extensionFactor: Float = 1.5f  // Load 50% more than visible
    ): Rect
    
    /**
     * Calculate which hex cells to include in atlas
     * even if partially visible
     */
    fun getExtendedHexRegion(
        viewport: Rect,
        hexGrid: HexGrid,
        marginInHexes: Int = 2  // Include 2 hex rings beyond visible
    ): Set<HexCoord>
}
```

### Hex Grid Specific Considerations

1. **Aspect Ratio Preservation in Atlas**:
   - Photos maintain original aspect ratio in display
   - Same aspect ratio MUST be preserved in atlas
   - Empty space around photos in hex cells is NOT included in atlas
   - This maximizes atlas space efficiency

2. **Packing Strategy for Hex Grid**:
   ```kotlin
   interface HexAwarePacker {
       fun packPhotosForHexGrid(
           photos: List<PhotoWithAspectRatio>,
           lodLevel: Int
       ): AtlasPackResult {
           // Group by similar aspect ratios for better packing
           val groups = photos.groupBy { photo ->
               when {
                   photo.aspectRatio < 0.7 -> AspectGroup.TALL
                   photo.aspectRatio > 1.4 -> AspectGroup.WIDE  
                   else -> AspectGroup.SQUARE
               }
           }
           
           // Pack each group efficiently
           return packGroupsIntoAtlas(groups)
       }
   }
   ```

3. **Extended Region Loading**:
   - **Why**: Prevents visible "pop-in" during scrolling
   - **How much**: Load 1-2 hex rings beyond visible area
   - **Dynamic adjustment**: Extend more in scroll direction
   ```
   Visible hexes: 20x15
   Extended region: 24x19 (2 hex margin)
   Velocity-adjusted: 26x19 (more in scroll direction)
   ```

### Dynamic LOD Generation
```kotlin
interface PhotoLODGenerator {
    // Generate all LOD levels on first access
    fun generateLODChain(photoUri: Uri): List<LODLevel>
    
    // Cache generated LODs for reuse
    fun getCachedLOD(photoId: String, lodLevel: Int): Bitmap?
    
    // Background processing for better UX
    fun preGenerateLODs(photoUris: List<Uri>, priority: Priority)
}
```

### Scaling Decision Tree
```
When user opens gallery:
├─ Immediately: Generate LOD 0-1 for visible photos only
├─ Background Priority 1: Generate LOD 0-2 for all photos
├─ Background Priority 2: Generate LOD 3 for nearby regions
└─ On-demand: Generate LOD 4+ when user zooms in

Scaling happens at these specific points:
1. Initial gallery load → Scale visible photos to 32px/64px
2. User scrolls → Scale newly visible photos for current LOD
3. User zooms in → Scale photos to next LOD level
4. Predictive load → Scale photos in predicted scroll direction

Memory optimization:
- Keep only scaled versions in atlas, not originals
- Cache frequently used LODs to disk
- Discard higher LODs when zoomed out
```

### Variable Size Handling
- **Problem**: User photos have unpredictable dimensions (100×100 to 6000×4000+)
- **Solution**: Normalize to standard sizes per LOD level
  - LOD 0-2: Fixed thumbnail sizes (32, 64, 128)
  - LOD 3-4: Proportional scaling (256, 512 max dimension)
  - LOD 5+: Original resolution (streamed individually)

### Atlas Efficiency Optimization
```kotlin
interface PhotoAtlasOptimizer {
    // Group photos by aspect ratio for better packing
    fun groupByAspectRatio(photos: List<PhotoInfo>): Map<AspectRatioClass, List<PhotoInfo>>
    
    // Create separate atlases for different aspect ratio classes
    fun createOptimizedAtlases(groups: Map<AspectRatioClass, List<PhotoInfo>>): List<Atlas>
}

enum class AspectRatioClass {
    SQUARE,          // 1:1 ± 10%
    LANDSCAPE,       // 4:3, 16:9, etc.
    PORTRAIT,        // 3:4, 9:16, etc.
    PANORAMIC        // > 2:1 or < 1:2
}

// Key insight: Photos in atlas preserve their display aspect ratio
data class AtlasPhoto(
    val id: String,
    val originalSize: Size,
    val atlasRect: Rect,     // Position in atlas  
    val contentRect: Rect,   // Actual photo within atlasRect (preserves aspect)
    val aspectRatio: Float
)
```

### Quality Preservation
- **Thumbnail Generation**: Use high-quality downsampling (Lanczos or bicubic)
- **Format Selection**: 
  - JPEG photos: Preserve as JPEG for LOD 4+ to save memory
  - UI overlays: Use ARGB_8888 for transparency
- **Color Space**: Maintain sRGB consistency across LOD levels

## Common Pitfalls and Solutions

### Problem: Texture Bleeding
**Solution**: Add 2-4 pixel padding between all packed textures

### Problem: LOD Flickering  
**Solution**: Implement hysteresis bands with 10% overlap

### Problem: Memory Spikes
**Solution**: Pre-allocate bitmap pools, limit concurrent atlas generation

### Problem: Slow High-Zoom Performance
**Solution**: Switch to individual texture streaming for LOD 5+

### Problem: Atlas Fragmentation
**Solution**: Regenerate atlases periodically during idle time

### Problem: Aspect Ratio Waste
**Solution**: Pack photos with preserved aspect ratios, group by aspect ratio similarity

### Problem: Visible Loading at Screen Edges
**Solution**: Extend viewport by 1-2 hex rings, adjust based on scroll velocity

## Virtual Texturing Alternative for Android

Since we're using Android Canvas instead of OpenGL, traditional virtual texturing isn't applicable. Instead, use a **Region-Based Streaming System**:

### Region-Based Photo Streaming
```kotlin
interface RegionBasedPhotoStreaming {
    /**
     * Instead of virtual texturing, we stream individual photos
     * when they're too large for efficient atlasing (LOD 5+)
     */
    fun streamPhotoForRegion(
        photoId: String,
        screenBounds: Rect,
        quality: StreamQuality
    ): Bitmap?
    
    /**
     * Use BitmapRegionDecoder for very large photos
     * to load only the visible portion
     */
    fun loadPhotoRegion(
        photoUri: Uri,
        regionRect: Rect,
        sampleSize: Int
    ): Bitmap {
        val decoder = BitmapRegionDecoder.newInstance(
            contentResolver.openInputStream(photoUri),
            false
        )
        
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        
        return decoder.decodeRegion(regionRect, options)
    }
}

enum class StreamQuality {
    PREVIEW,  // Quick load, lower quality
    FULL      // Full quality for detailed viewing
}
```

This approach provides similar benefits to virtual texturing (loading only what's needed) but works within Android's framework constraints.

## Quality Settings

### Low-End Devices
- Max atlas size: 1024×1024
- Format: RGB_565
- Max atlases: 4
- Skip LOD 4-5

### Mid-Range Devices
- Max atlas size: 2048×2048
- Format: ARGB_8888
- Max atlases: 8
- All LOD levels

### High-End Devices
- Max atlas size: 4096×4096
- Format: ARGB_8888
- Max atlases: 16
- Virtual texturing enabled

## Monitoring and Profiling

### Key Metrics to Track
```kotlin
interface PerformanceMonitor {
    fun getAverageFrameTime(): Float
    fun getTextureMemoryUsage(): Long
    fun getAtlasCacheHitRate(): Float
    fun getDrawCallsPerFrame(): Int
    fun getTextureSwitchesPerFrame(): Int
    fun getLODTransitionsPerSecond(): Int
}
```

### Performance Targets
- Frame time: < 16.6ms (60fps)
- Cache hit rate: > 90%
- Memory usage: < 256MB
- Atlas utilization: > 80%

## Integration with Android

### Bitmap Configuration
- Use `Bitmap.Config.ARGB_8888` for quality
- Use `Bitmap.Config.RGB_565` for memory saving
- Enable `inPurgeable` and `inInputShareable` flags
- Use `BitmapFactory.Options.inSampleSize` for LOD generation

### Canvas Rendering
- Use `Canvas.drawBitmap()` with source/dest rectangles
- Enable hardware acceleration
- Batch draw calls by atlas
- Minimize state changes

### Memory Pressure Handling
```kotlin
override fun onTrimMemory(level: Int) {
    when (level) {
        TRIM_MEMORY_MODERATE -> reduceAtlasCache(50%)
        TRIM_MEMORY_COMPLETE -> clearAtlasCache()
    }
}
```

## Summary

This atlas-based system provides a scalable solution for rendering thousands of user photos at 60fps on Android devices. The key is adapting game industry techniques to the unique challenges of a photo gallery:

1. **Multi-tier LOD system** with fixed-size thumbnails for overview and variable-size handling for detail views
2. **Intelligent packing** that groups photos by aspect ratio for optimal atlas utilization
3. **On-device LOD generation** with background processing and caching
4. **Progressive loading** that prioritizes visible content while pre-generating nearby regions
5. **Individual streaming** for full-resolution viewing to preserve photo quality

The system handles the unpredictable nature of user photos (varying sizes, aspect ratios, and resolutions) by:
- Normalizing to standard sizes for lower LODs
- Using efficient packing algorithms (MaxRects) for medium LODs
- Switching to individual streaming for high-quality viewing
- Implementing smart caching strategies for generated thumbnails

Proper implementation of this design should achieve smooth 60fps performance even with 1000+ photos while maintaining the quality users expect from a photo gallery application.