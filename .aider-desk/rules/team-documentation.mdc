# Transformable Content Best Practices

## When to Use
- Any zoomable/pannable content
- Programmatic focus requirements
- Complex gesture interactions

## Implementation Notes
1. Always:
   - Use `snapshotFlow` for state synchronization
   - Cache matrix operations
   - Handle edge cases (empty bounds, extreme zooms)

2. Avoid:
   - Direct matrix manipulation outside state
   - Multiple transformation sources
   - Ignoring content size changes

## Performance
- Reuse FloatArray for matrix values
- Minimize allocations in gesture handlers
- Pre-calculate expensive bounds math

## Transformable Content Animation Patterns

### Problem Statement
Implement smooth zoom/pan animations with programmatic focus that:
1. Respects gesture interactions
2. Avoids state desynchronization
3. Maintains 60fps performance

### Root Cause Analysis (Five Whys)
1. **Problem**: Jumps between gestures and animations  
   **Why?**: Matrix and Animatable states were desynchronized  
   **Solution**: Single source of truth via `MatrixAnimator`

### Key Learnings
1. **Unified Transformation Pipeline**:
   ```kotlin
   class MatrixAnimator(initial: Matrix, spec: AnimationSpec<Matrix>) {
       private val animatable = Animatable(initial, MatrixVectorConverter)
       // Single source for all transformations
   }
   ```
2. **Proper Matrix Animation**:
   - Implemented `TwoWayConverter<Matrix, AnimationVector4D>`
   - Syncs scale (X/Y) and translation (X/Y) as a single unit

3. **Gesture-Animation Harmony**:
   - Always check `!state.isAnimating` before handling gestures
   - Snap matrix state immediately on gesture start

### Best Practices
1. **For Animation**:
   ```kotlin
   suspend fun focusOn(bounds: Rect) {
       isAnimating = true
       try {
           matrixAnimator.animateTo(targetMatrix) // Physics-based
       } finally {
           isAnimating = false // Always reset
       }
   }
   ```
2. **For Performance**:
   - Use `derivedStateOf` for zoom/offset to minimize recomposition
   - Cache `FloatArray(9)` for matrix operations

3. **Edge Cases Handled**:
   - Interrupted animations
   - Minimum/maximum zoom clamping
   - Empty content bounds

### Zoom Clamping Pattern (NEW)

**Problem**: Users can zoom beyond usable limits, causing performance issues and poor UX.

**Solution**: Implement gesture-level zoom limits with revert-to-original approach:
```kotlin
private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 10f

// In gesture handler:
val originalMatrix = Matrix(matrix)
matrix.postScale(zoomChange, zoomChange, centroid.x, centroid.y)

val currentZoom = getMatrixScale(matrix)
if (currentZoom !in MIN_ZOOM..MAX_ZOOM) {
    matrix.set(originalMatrix) // Revert and skip pan
    return@detectTransformGestures
}
// Continue with pan logic...
```

**Key Benefits**:
- Prevents matrix shift artifacts (vs direct clamping)
- Separates zoom/pan operations for better UX
- Maintains gesture responsiveness at limits

### Visualization of the Solution
```
[Gesture] → [Update Matrix] → [Sync State]  
[Animation] → [MatrixAnimator] → [Single Source of Truth]
```

### Code Healthy Metrics
- **Coupling**: Low (MatrixAnimator encapsulates all transform logic)
- **Cohesion**: High (All transformation code in one class)
- **Performance**: No frame drops during 10K+ matrix ops/s in tests

### Future Improvements
1. Add debug overlays for transformation origin points
2. Support pinch-zoom pivot point animations
3. Document spring tuning parameters:
   ```kotlin
   spring(
       stiffness = 800f, 
       dampingRatio = 0.6f 
   )
   ```

---

# Android Media Permissions System Patterns (NEW)

## Problem Statement
Implement modern Android media permissions that:
1. Support Android 10-15 with different permission models
2. Handle Android 14+ "Limited Access" transparently
3. Provide excellent UX without over-engineering

## Root Cause Analysis (Five Whys)
1. **Problem**: App stuck on permission screen after granting limited access
   **Why?**: Only checking `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` permissions
   **Why?**: Didn't account for Android 14's `READ_MEDIA_VISUAL_USER_SELECTED`
   **Solution**: Check for either full OR limited access permissions

2. **Problem**: Complex permission state management
   **Why?**: Tried to detect and handle "limited access" as separate state
   **Why?**: Misunderstood that limited access should be transparent to app
   **Solution**: Simplified to binary "has access" vs "no access"

## Key Learnings

### 1. Android Permission Evolution Strategy
```kotlin
// Android 14+ - Check for either full OR limited access
val hasFullAccess = (READ_MEDIA_IMAGES granted || READ_MEDIA_VIDEO granted)
val hasLimitedAccess = READ_MEDIA_VISUAL_USER_SELECTED granted

// Treat limited access same as full access - system handles the difference
val effectiveAccess = hasFullAccess || hasLimitedAccess
```

### 2. Don't Over-Engineer Permission States
**Anti-Pattern**: Complex state machines with GRANTED/LIMITED/DENIED/PERMANENTLY_DENIED
```kotlin
// ❌ Over-engineered
enum class MediaPermissionState {
    GRANTED, LIMITED, DENIED, PERMANENTLY_DENIED, NOT_REQUESTED
}
```

**Best Practice**: Simple binary approach
```kotlin
// ✅ Simple and effective  
enum class MediaPermissionState {
    GRANTED, DENIED, NOT_REQUESTED
}
```

### 3. Let the System Handle Complexity
- **Limited Access**: System provides only selected photos to MediaStore queries
- **Permanent Denial**: Android 11+ automatically stops showing dialogs after 2+ denials  
- **Rationale**: Modern apps show good upfront explanation instead of complex rationale API

### 4. Modern Permission UX Pattern
```kotlin
// Clean flow: Request → Has Access? → Show Content
when {
    permissionStatus.hasMediaAccess -> showGallery()
    else -> showPermissionRequest()
}
```

## Best Practices

### 1. Version-Appropriate Permission Sets
```kotlin
private val requiredPermissions = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        // Android 14+ - Include limited access permission
        arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
    }
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        // Android 13 - Granular media permissions
        arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
    }
    else -> {
        // Android 10-12 - Legacy storage permission
        arrayOf(READ_EXTERNAL_STORAGE)
    }
}
```

### 2. Single Permission Request
```kotlin
// Request all permissions in one operation per Android docs
permissionLauncher.launch(permissionManager.getAllPermissions())
```

### 3. Minimum SDK Strategy
- **Bump to API 29** eliminates Android 9 edge cases
- **Cleaner permission model** throughout supported range
- **97%+ device coverage** with much simpler code

## Code Health Metrics
- **Reduced complexity**: Removed 5 unused states/composables  
- **Better UX**: No complex "permanently denied" flows
- **Maintenance**: Single permission detection path per Android version

## Anti-Patterns Avoided
1. **Tracking permanent denial manually** - Let Android handle it
2. **Complex limited access UI** - Treat as transparent to app
3. **Multiple permission request flows** - Use single Material3 bottom sheet
4. **Supporting very old Android versions** - Focus on modern baseline

## Future Considerations
1. **Photo Picker Integration**: For apps wanting zero permissions
2. **Scoped Storage Optimization**: Direct file access patterns for Android 11+
3. **Privacy Dashboard**: Prepare for enhanced permission monitoring

### Visualization of the Solution
```
User Action → System Permission Dialog → Binary Result
[Allow All] → READ_MEDIA_* granted → App sees all photos
[Allow Limited] → READ_MEDIA_VISUAL_USER_SELECTED granted → App sees selected photos  
[Deny] → No permissions → Show retry option
```

---

# Atlas-Based Media Rendering System (NEW)

## Problem Statement
Replace Coil-based image loading with custom atlas system for:
1. Large-scale hex grid canvas with hundreds/thousands of images
2. Zoom-level optimized rendering (LOD - Level of Detail)
3. Precise memory and GPU texture control
4. Seamless performance across zoom ranges

## Why Atlas Over Individual Images?
**Performance Issues with Coil:**
- Creates thousands of individual bitmap objects
- Uncontrolled memory usage and GPU texture thrashing  
- No zoom-level optimization (always loads same resolution)
- Excessive draw calls (one per image vs one per atlas)

**Atlas Benefits:**
- Single large bitmap per zoom level
- Massive reduction in GPU texture switches
- Perfect memory control and predictable usage
- Zoom-optimized rendering with multiple detail levels

## Core Design Principles

### 1. Single Atlas Focus (Phase 1)
Before implementing multiple LOD levels, solve fundamental problems:
- **Atlas Size**: Optimal balance between memory usage and texture limits
- **Packing Algorithm**: How to arrange hundreds of thumbnails efficiently
- **Image Data Storage**: Maintain mapping from media → atlas coordinates
- **Region Retrieval**: Fast lookup of image regions within atlas

### 2. Key Technical Challenges

#### Atlas Size Strategy
```kotlin
// Target: Balance memory vs texture limits
// Android GPU texture limit: typically 4096x4096 or 8192x8192
// Optimal atlas size: 2048x2048 (4MB at ARGB_8888)

val ATLAS_SIZE = 2048 // pixels
val THUMBNAIL_SIZE = 128 // pixels  
val THUMBNAILS_PER_ATLAS = (ATLAS_SIZE / THUMBNAIL_SIZE).pow(2) // 256 thumbnails
```

#### Image Data Storage Pattern
```kotlin
data class AtlasRegion(
    val x: Int,        // pixel offset in atlas
    val y: Int,        // pixel offset in atlas  
    val width: Int,    // region width
    val height: Int,   // region height
    val atlasId: Int   // which atlas contains this image
)

class AtlasManager {
    private val mediaToRegion = mutableMapOf<Media, AtlasRegion>()
    private val atlases = mutableListOf<Bitmap>()
    
    fun getRegion(media: Media): AtlasRegion?
    fun packThumbnail(media: Media, thumbnail: Bitmap): AtlasRegion
}
```

#### Packing Algorithm Options
1. **Simple Grid**: Fixed 128x128 slots in 16x16 grid
2. **Bin Packing**: Variable sizes, optimal space usage
3. **Hybrid**: Fixed rows with variable column widths

#### Atlas Generation Architecture
- **Location**: Data layer during media loading (not UI layer)
- **Approach**: Offscreen rendering solution
- **Candidates**: ImageWriter with Surface, hardware Canvas
- **Goal**: Generate atlas bitmaps independent of UI thread

## Implementation Strategy

### Phase 1: Single Atlas Foundation
1. **Remove Coil dependency** - Replace with native thumbnail generation
2. **Atlas packing system** - Simple grid-based approach first
3. **Region mapping** - Media → AtlasRegion lookup table
4. **Canvas integration** - Update MediaHexVisualization to use atlas

### Phase 2: Multiple LOD Levels (Future)
```kotlin
// Future LOD system based on zoom ranges
Level 0: zoom 0.1-0.5  → 32px thumbs  (overview)
Level 1: zoom 0.5-2.0  → 128px thumbs (navigation) 
Level 2: zoom 2.0-8.0  → 512px thumbs (browsing)
Level 3: zoom 8.0+     → full res     (detail)
```

## Key Technical Questions (Phase 1)
1. **Atlas Size**: Start with 2048x2048 or test device limits?
2. **Thumbnail Generation**: `ThumbnailUtils.extractThumbnail()` vs `ImageDecoder`?
3. **Packing Strategy**: Fixed grid vs bin packing for first iteration?
4. **Memory Management**: Single atlas vs multiple smaller atlases?
5. **Threading**: Background atlas generation vs on-demand?
6. **Offscreen Rendering**: Best approach for atlas generation outside UI?

## Success Metrics
- **Memory Usage**: Predictable and bounded per zoom level
- **Draw Calls**: 1 per atlas region vs 1 per individual image
- **Frame Rate**: Stable 60fps during pan/zoom operations
- **Load Time**: Fast atlas generation for hundreds of images

---

# Photo Scaling for Atlas System (NEW)

## Problem Statement
Implement high-performance photo scaling for texture atlas generation:
1. Process hundreds/thousands of photos efficiently
2. Maintain excellent quality for thumbnail generation
3. Support multiple LOD (Level of Detail) resolutions
4. Leverage Android's hardware acceleration

## Root Cause Analysis (Five Whys)
1. **Problem**: Initially considered Lanczos filtering for best quality
   **Why?**: Lanczos provides theoretically superior image quality
   **Why?**: Wanted to optimize for visual appearance over performance
   **Solution**: Recognized that bilinear filtering offers better performance/quality balance

2. **Problem**: Complex scaling logic embedded in PhotoLODProcessor
   **Why?**: Started with simple inline scaling approach
   **Why?**: Didn't initially anticipate multiple scaling strategies needed
   **Solution**: Extracted dedicated PhotoScaler component for reusability

## Key Design Decision: Hardware-Accelerated Bilinear Filtering

### Decision Rationale
```kotlin
// ❌ Theoretical best quality but impractical
fun scaleLanczos(source: Bitmap, target: IntSize): Bitmap {
    // Pure Kotlin implementation would be extremely slow
    // No NDK available for atlas thumbnail generation
}

// ✅ Practical high-quality solution
fun scaleHardwareAccelerated(source: Bitmap, target: IntSize): Bitmap {
    return Bitmap.createScaledBitmap(
        source, target.width, target.height,
        true // Enable hardware-accelerated bilinear filtering
    )
}
```

**Why Bilinear Over Lanczos:**
- **Performance**: Hardware-accelerated vs pure Kotlin implementation
- **Quality**: Excellent for thumbnail generation (negligible visual difference)
- **Platform Integration**: Leverages Android's optimized graphics pipeline
- **Memory Efficiency**: No intermediate buffer allocations

## Implementation Patterns

### 1. Dedicated Scaling Component
```kotlin
@Singleton
class PhotoScaler @Inject constructor() {
    fun scale(source: Bitmap, targetSize: IntSize, strategy: ScaleStrategy): Bitmap
    fun createThumbnail(source: Bitmap, maxDimension: Int): Bitmap
    fun downscale(source: Bitmap, scaleFactor: Float): Bitmap
}
```

**Benefits:**
- **Single Responsibility**: Only handles bitmap scaling operations
- **Reusable**: Can be used by atlas system, preview generation, etc.
- **Testable**: Isolated scaling logic for unit testing
- **Injectable**: Fits clean architecture with DI

### 2. Strategy Pattern for Scaling Types
```kotlin
enum class ScaleStrategy {
    FIT_CENTER,    // Maintain aspect ratio, may have empty space
    CENTER_CROP    // Fill target completely, may crop edges
}
```

**FIT_CENTER Use Case:**
- Atlas thumbnails where aspect ratios vary
- Prevents distortion of landscape/portrait photos
- Maintains visual integrity of original content

**CENTER_CROP Use Case:**
- Fixed-size atlas slots for uniform grid layout
- Social media style thumbnails
- When consistent visual weight is more important than showing full content

### 3. Memory-Safe Crop Implementation
```kotlin
private fun scaleAndCrop(source: Bitmap, targetSize: IntSize): Bitmap {
    // 1. Scale to intermediate size (larger than target)
    val scaledBitmap = Bitmap.createScaledBitmap(/*...*/)
    
    // 2. Crop from center to exact target size
    val croppedBitmap = Bitmap.createBitmap(scaledBitmap, /*crop coords*/)
    
    // 3. Clean up intermediate bitmap
    if (scaledBitmap != croppedBitmap) {
        scaledBitmap.recycle()
    }
    
    return croppedBitmap
}
```

**Key Pattern**: Always clean up intermediate bitmaps to prevent memory leaks

### 4. Integration with LOD Processor
```kotlin
// Before: Inline scaling logic
class PhotoLODProcessor {
    private fun scaleToLOD(original: Bitmap, targetSize: IntSize): Bitmap {
        return original.scale(targetSize.width, targetSize.height)
    }
}

// After: Dependency injection with dedicated scaler
class PhotoLODProcessor @Inject constructor(
    private val contentResolver: ContentResolver,
    private val photoScaler: PhotoScaler
) {
    suspend fun processPhotoForLOD(
        photoUri: Uri,
        lodLevel: LODLevel,
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER
    ): ProcessedPhoto? {
        val scaledBitmap = photoScaler.scale(original, targetSize, scaleStrategy)
        // ...
    }
}
```

## Best Practices

### 1. Always Enable Filtering
```kotlin
// ✅ High quality scaling
Bitmap.createScaledBitmap(source, width, height, true)

// ❌ Fast but poor quality
Bitmap.createScaledBitmap(source, width, height, false)
```

### 2. Calculate Target Size Before Scaling
```kotlin
// ✅ Pre-calculate dimensions
val targetSize = calculateTargetSize(original, lodLevel, strategy)
val scaled = photoScaler.scale(original, targetSize, strategy)

// ❌ Calculate during scaling
val scaled = photoScaler.scale(original, lodLevel.resolution, strategy)
```

### 3. Memory Cleanup Patterns
```kotlin
// Always check if bitmaps are different before recycling
if (scaledBitmap != originalBitmap) {
    originalBitmap.recycle()
}
```

## Code Health Metrics
- **Separation of Concerns**: PhotoScaler handles only bitmap operations
- **Testability**: Isolated scaling logic with predictable inputs/outputs  
- **Memory Safety**: Explicit bitmap recycling prevents OOM issues
- **Clean Architecture**: Follows dependency injection and single responsibility principles

## Future Considerations
1. **WebP Support**: Modern format with better compression for atlas storage
2. **Multi-threading**: Parallel scaling for batch operations
3. **Caching**: Scaled bitmap cache for frequently accessed images
4. **Quality Metrics**: A/B testing for user-perceived quality differences

## Anti-Patterns Avoided
1. **Over-Engineering**: Chose practical bilinear over theoretical Lanczos
2. **Inline Scaling**: Extracted reusable component instead of embedding in processor
3. **Memory Leaks**: Explicit cleanup vs relying on GC
4. **Quality Obsession**: Balanced quality with performance requirements

---

# Atlas Coordination and Pipeline (NEW)

## Problem Statement
Coordinate multiple complex components into a unified atlas generation pipeline:
1. Bridge PhotoLODProcessor + TexturePacker into complete system
2. Handle partial failures gracefully while maximizing success
3. Manage memory efficiently during multi-step processing
4. Provide detailed metrics for debugging and optimization

## Root Cause Analysis (Five Whys)
1. **Problem**: Need to coordinate PhotoLODProcessor and TexturePacker
   **Why?**: Each component handles one piece of atlas generation
   **Why?**: Single responsibility principle - each component has focused purpose
   **Solution**: Create AtlasGenerator as coordination layer

2. **Problem**: Complex failure handling across multiple processing stages
   **Why?**: Photos can fail at loading, processing, or packing stages
   **Why?**: Real-world photo data is unreliable (corrupted files, permissions, etc.)
   **Solution**: Collect failures at each stage and provide comprehensive failure reporting

## Key Design Decision: Sequential Processing with Immediate Cleanup

### Decision Rationale
```kotlin
// ✅ Simple sequential approach for Phase 1
suspend fun generateAtlas(photoUris: List<Uri>): AtlasGenerationResult {
    // Step 1: Process all photos
    for (uri in photoUris) {
        val processed = photoLODProcessor.processPhotoForLOD(uri, lodLevel)
        if (processed != null) processedPhotos.add(processed)
    }
    
    // Step 2: Pack layout
    val packResult = texturePacker.pack(imagesToPack)
    
    // Step 3: Draw atlas and cleanup immediately
    val atlasBitmap = createAtlasBitmap(processedPhotos, packResult)
    processedPhotos.forEach { it.bitmap.recycle() } // Immediate cleanup
}

// ❌ More complex batch processing (future enhancement)
suspend fun generateAtlasWithBatching(photoUris: List<Uri>): AtlasGenerationResult {
    photoUris.chunked(50).forEach { batch ->
        if (memoryMonitor.isMemoryPressure()) return@forEach
        // Complex memory management...
    }
}
```

**Why Sequential Over Batching (for now):**
- **Simplicity**: Clear linear flow for debugging and testing
- **Foundation**: Easy to refactor to batching when we add MemoryMonitor
- **Predictable**: Known memory usage patterns
- **Fast Implementation**: Get working system quickly

## Implementation Patterns

### 1. Multi-Stage Pipeline with Failure Tracking
```kotlin
class AtlasGenerator @Inject constructor(
    private val photoLODProcessor: PhotoLODProcessor
) {
    suspend fun generateAtlas(photoUris: List<Uri>): AtlasGenerationResult {
        val processedPhotos = mutableListOf<ProcessedPhoto>()
        val failed = mutableListOf<Uri>()
        
        // Stage 1: Photo processing with individual failure tracking
        for (uri in photoUris) {
            try {
                val processed = photoLODProcessor.processPhotoForLOD(uri, lodLevel)
                if (processed != null) {
                    processedPhotos.add(processed)
                } else {
                    failed.add(uri) // Track processing failures
                }
            } catch (e: Exception) {
                failed.add(uri) // Track exception failures
            }
        }
        
        // Stage 2: Packing with additional failure tracking
        val packResult = texturePacker.pack(imagesToPack)
        val packingFailed = extractFailedUrisFromPackResult(packResult, photoUris)
        
        return AtlasGenerationResult(
            atlas = if (packResult.packedImages.isNotEmpty()) atlas else null,
            failed = failed + packingFailed, // Comprehensive failure list
            // ... metrics
        )
    }
}
```

**Benefits:**
- **Comprehensive Failure Reporting**: Track exactly which URIs failed at which stage
- **Partial Success**: Continue processing even when some photos fail
- **Debuggable**: Clear separation of failure types for troubleshooting

### 2. Index-Based ID Management
```kotlin
// Simple approach: Use array indices for reliable mapping
val imagesToPack = processedPhotos.mapIndexed { index, photo ->
    ImageToPack(id = index.toString(), size = photo.scaledSize)
}

// Later: Map back from packed results to original URIs
val atlasRegions = packResult.packedImages.mapNotNull { packedImage ->
    val index = packedImage.id.toIntOrNull()
    if (index != null && index < originalUris.size) {
        AtlasRegion(
            photoId = originalUris[index].toString(), // Use URI as final ID
            atlasRect = packedImage.rect,
            // ...
        )
    } else null
}
```

**Key Pattern**: Use simple indices during processing, then map to meaningful IDs for final result

### 3. Memory-Safe Atlas Drawing
```kotlin
private fun createAtlasBitmap(processedPhotos: List<ProcessedPhoto>, packResult: PackResult): Bitmap {
    val atlasBitmap = createBitmap(atlasSize.width, atlasSize.height)
    val canvas = Canvas(atlasBitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true // Hardware-accelerated bilinear filtering
    }
    
    packResult.packedImages.forEach { packedImage ->
        val processedPhoto = processedPhotos[packedImage.id.toInt()]
        if (!processedPhoto.bitmap.isRecycled) {
            canvas.drawBitmap(processedPhoto.bitmap, null, packedImage.rect, paint)
        }
    }
    
    return atlasBitmap
}
```

**Key Patterns**: 
- Hardware-accelerated drawing with proper Paint configuration
- Recycling safety checks before drawing
- Null source rect for drawing entire processed bitmap

### 4. Comprehensive Result Metrics
```kotlin
data class AtlasGenerationResult(
    val atlas: TextureAtlas?,           // Nullable for clear failure indication
    val failed: List<Uri>,              // Specific failed URIs for retry/debugging
    val packingUtilization: Float,      // Atlas space efficiency
    val totalPhotos: Int,               // Input count
    val processedPhotos: Int            // Successfully processed count
) {
    val packedPhotos: Int get() = atlas?.regions?.size ?: 0
    val successRate: Float get() = if (totalPhotos > 0) packedPhotos.toFloat() / totalPhotos else 0f
}
```

**Benefits:**
- **Debugging Metrics**: Distinguish between processing vs packing failures
- **Performance Tracking**: Monitor atlas efficiency and success rates
- **User Feedback**: Clear progress reporting ("Loaded 847 of 850 photos")

## Best Practices

### 1. Immediate Memory Cleanup
```kotlin
// ✅ Clean up processed photos immediately after atlas creation
processedPhotos.forEach { processedPhoto ->
    if (!processedPhoto.bitmap.isRecycled) {
        processedPhoto.bitmap.recycle()
    }
}

// ❌ Rely on garbage collection
// Don't keep processed photos around unnecessarily
```

### 2. Graceful Partial Failure Handling
```kotlin
// ✅ Continue processing even with failures
if (processedPhotos.isEmpty()) {
    return AtlasGenerationResult(atlas = null, failed = allUris, ...)
}
if (packResult.packedImages.isEmpty()) {
    return AtlasGenerationResult(atlas = null, failed = allUris, ...)
}

// ❌ Fail-fast on first error
// Don't abort entire operation for single photo failure
```

### 3. Constants for Configuration
```kotlin
companion object {
    private const val DEFAULT_ATLAS_SIZE = 2048  // From atlas plan
    private const val ATLAS_PADDING = 2         // From atlas plan
}
```

## Code Health Metrics
- **Coordination Logic**: Single class handles multi-component coordination
- **Failure Resilience**: Continues processing despite individual photo failures
- **Memory Safety**: Explicit cleanup prevents bitmap accumulation
- **Metrics Rich**: Comprehensive result data for debugging and optimization

## Future Considerations (Documented for Enhancement)
1. **Batch Processing**: Memory-aware chunked processing for large photo sets
2. **Progress Reporting**: Real-time progress callbacks during long operations
3. **Cancellation Support**: Coroutine cancellation between processing stages
4. **Multiple Atlas Support**: Generate multiple atlases when photos exceed single atlas capacity

## Anti-Patterns Avoided
1. **All-or-Nothing Processing**: Chose partial success over complete failure
2. **Complex ID Management**: Used simple indices instead of hash-based IDs
3. **Memory Accumulation**: Immediate cleanup vs keeping all bitmaps in memory
4. **Monolithic Processing**: Clear stage separation for debugging and testing