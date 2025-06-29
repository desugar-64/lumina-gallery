# Atlas Texture System - Implementation Plan

> **📖 Reference Document**: `/.aider-desk/rules/atlas/atlas-system-design.md`  
> For detailed architectural patterns, performance targets, and implementation guidelines, refer to the comprehensive atlas system design document.

## Project Overview

This document outlines the implementation plan for integrating an advanced atlas texture system into LuminaGallery. The system will replace the current placeholder rectangles with efficient, GPU-optimized photo rendering using Level-of-Detail (LOD) and texture atlasing techniques.

## Current State Analysis

### Existing Architecture Strengths
- **Clean Architecture**: Well-separated domain/data/UI layers with proper abstractions
- **Sophisticated Coordinate System**: Hex grid with axial coordinates (q,r) fully implemented
- **Optimized Transformations**: Matrix-based pan/zoom with cached FloatArray for performance
- **Ready Integration Point**: `MediaHexVisualization` line 227-235 has placeholder for atlas rendering
- **Real Media Pipeline**: MediaStore integration ready, just needs atlas processing layer

### Current Limitations
- **Placeholder Rendering**: Colored rectangles instead of actual photo thumbnails
- **No Image Loading**: Post-Coil removal, no bitmap loading/processing system
- **Missing Performance Optimization**: No LOD system or texture atlasing for smooth zoom

## Atlas System Architecture

### Core Components Overview

```
AtlasManager (Coordinator)
├── PhotoLODProcessor (Image scaling)
├── AtlasGenerator (Texture packing)
├── AtlasCache (Memory management)
├── RegionCalculator (Viewport optimization)
└── TextureAtlas (Individual atlas data)
```

### LOD (Level of Detail) System

| LOD Level | Resolution | Zoom Range | Atlas Strategy | Use Case |
|-----------|------------|------------|----------------|----------|
| 0 | 32px | 0.0-0.5 | Fixed Grid | Far overview (tiny dots) |
| 2 | 128px | 0.5-2.0 | Fixed Grid | Medium thumbnails |
| 4 | 512px | 2.0-10.0 | Shelf Packing | High quality preview |

**Design Decisions:**
- **3 LOD levels initially**: Start simple, add intermediate levels later
- **Fixed Grid for small sizes**: Uniform thumbnails for LOD 0-2
- **Shelf Packing for larger sizes**: Better utilization for LOD 4+
- **Progressive loading**: Start with LOD 0, upgrade based on zoom level

## Implementation Tasks & Timeline

### Phase 1: Core Atlas Foundation (Week 1-2)
**Goal**: Replace placeholder rectangles with basic atlas-rendered thumbnails

#### ✅ Task 1.1: Core Data Models
**Files to create:**
- `domain/model/TextureAtlas.kt` - Individual atlas with packed regions
- `domain/model/AtlasRegion.kt` - Photo region within atlas
- `domain/model/LODLevel.kt` - Level-of-detail configuration

**Key Components:**
```kotlin
data class TextureAtlas(
    val bitmap: Bitmap,
    val regions: Map<String, AtlasRegion>,
    val lodLevel: Int,
    val size: Size
)

data class AtlasRegion(
    val photoId: String,
    val atlasRect: Rect,      // Position in atlas texture
    val originalSize: Size,   // Original photo dimensions
    val aspectRatio: Float
)

enum class LODLevel(val resolution: Int, val zoomRange: ClosedFloatingPointRange<Float>) {
    LEVEL_0(32, 0.0f..0.5f),
    LEVEL_2(128, 0.5f..2.0f),  
    LEVEL_4(512, 2.0f..10.0f)
}
```

#### ✅ Task 1.2: Photo Processing Pipeline
**Files created:**
- `domain/usecase/PhotoLODProcessor.kt` - Scales photos to LOD levels
- `data/PhotoScaler.kt` - Bitmap scaling implementation

**Responsibilities:**
- Load photos from MediaStore URIs
- Scale to appropriate LOD resolution
- Maintain aspect ratios
- Use high-quality scaling.
- **Decision**: While Lanczos filtering offers the highest quality, it is computationally expensive and slow to implement in pure Kotlin without the NDK. For the performance-critical task of generating many thumbnails quickly, we will use Android's built-in `Bitmap.createScaledBitmap` with its `filter` option enabled. This provides high-quality bilinear filtering that is hardware-accelerated and offers the best balance of speed and quality for this application.

#### ✅ Task 1.3: Atlas Generation
**Files created:**
- `domain/usecase/AtlasGenerator.kt` - Creates texture atlases
- `data/TexturePacker.kt` - Shelf packing algorithm implementation

**Features:**
- **Shelf Packing Algorithm**: Efficient for photos with varied aspect ratios
- **2048x2048 Atlas Size**: Safe compatibility across Android devices
- **Padding**: 2-pixel padding between photos to prevent texture bleeding
- **Fallback Handling**: Graceful handling when photos don't fit

#### ⏳ Task 1.4: Atlas Manager
**Files to create:**
- `domain/usecase/AtlasManager.kt` - Central coordinator

**Responsibilities:**
- Coordinate atlas lifecycle (creation, caching, eviction)
- Map viewport to required LOD levels
- Manage background atlas generation
- Handle memory pressure events

### Phase 2: Viewport Optimization (Week 3)
**Goal**: Efficient memory usage with region-based loading

#### ⏳ Task 2.1: Spatial Indexing
**Files to create:**
- `domain/usecase/RegionCalculator.kt` - Viewport to hex region mapping
- `domain/model/AtlasRegionKey.kt` - Unique region identification

**Features:**
- **Viewport Extension**: Load 1-2 hex rings beyond visible area
- **Region-based Loading**: Divide hex grid into manageable atlas regions
- **Distance-based Priority**: Closer regions loaded first

#### ⏳ Task 2.2: Atlas Cache Management
**Files to create:**
- `data/AtlasCache.kt` - LRU memory-bounded cache
- `domain/model/MemoryMonitor.kt` - Memory pressure handling

**Features:**
- **LRU Eviction**: Least recently used atlases removed first
- **Memory Budget**: Target < 256MB for atlas textures
- **Background Generation**: Atlas creation on background threads
- **Progressive Quality**: Quick preview → full quality pipeline

### Phase 3: Integration & Performance (Week 4)
**Goal**: Production-ready performance and smooth user experience

#### ⏳ Task 3.1: MediaHexVisualization Integration
**Files to modify:**
- `ui/MediaHexVisualization.kt` - Replace placeholder rectangles

**Changes:**
- Replace `drawRect()` calls with atlas texture sampling
- Add texture coordinate calculations for hex positioning
- Implement fallback to placeholders when atlas not ready
- Add smooth LOD transitions with blending

#### ⏳ Task 3.2: ViewModel State Management
**Files to modify:**
- `ui/gallery/GalleryViewModel.kt` - Add atlas state management

**New State:**
- Atlas loading progress
- Current LOD levels per region
- Memory usage monitoring
- Error handling for atlas generation failures

#### ⏳ Task 3.3: Performance Optimization
**Files to create:**
- `domain/model/PredictiveLoader.kt` - Scroll-based preloading
- `ui/AtlasRenderer.kt` - Optimized drawing pipeline

**Features:**
- **Predictive Loading**: Preload based on scroll velocity
- **Memory Pressure Handling**: Android `onTrimMemory` integration
- **Smooth Transitions**: Temporal blending between LOD levels
- **Performance Monitoring**: Frame time and memory usage tracking

## Technical Specifications

### Performance Targets
- **Frame Rate**: Maintain 60fps during pan/zoom operations
- **Memory Usage**: < 256MB for atlas textures
- **Cache Hit Rate**: > 90% for smooth scrolling experience
- **Texture Switches**: < 10 per frame for optimal GPU performance

### Memory Management Strategy
```kotlin
// Target memory calculation
val maxAtlasMemory = min(
    Runtime.getRuntime().maxMemory() * 0.25, // 25% of available heap
    256_000_000L // 256MB absolute maximum
)

// Atlas size calculation (ARGB_8888)
val atlasMemoryUsage = width * height * 4 // bytes per atlas
```

### Atlas Configuration
- **Atlas Size**: 2048x2048 (safe for all Android devices)
- **Texture Format**: ARGB_8888 for quality, RGB_565 for memory saving
- **Padding**: 2 pixels between packed photos
- **Maximum Atlases**: Dynamically calculated based on available memory

## Integration Points

### Key Files to Modify
1. **MediaHexVisualization.kt** (line 227-235): Replace placeholder drawing
2. **GalleryViewModel.kt**: Add atlas state management  
3. **App.kt**: Connect viewport changes to atlas loading triggers
4. **AppModule.kt**: Add dependency injection for new components

### Dependency Injection Setup
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AtlasModule {
    
    @Provides
    @Singleton
    fun provideAtlasManager(/* dependencies */): AtlasManager
    
    @Provides
    @Singleton  
    fun providePhotoLODProcessor(): PhotoLODProcessor
    
    @Provides
    @Singleton
    fun provideAtlasCache(): AtlasCache
}
```

## Testing Strategy

### Unit Tests
- **PhotoLODProcessor**: Verify correct scaling and aspect ratio preservation
- **AtlasGenerator**: Test packing efficiency and edge cases
- **AtlasCache**: Validate LRU eviction and memory management

### Performance Tests  
- **Memory Usage**: Monitor atlas memory consumption
- **Frame Rate**: Verify 60fps during intensive pan/zoom operations
- **Cache Efficiency**: Measure hit rates and loading times

### Integration Tests
- **End-to-end Flow**: Photo loading → LOD generation → atlas packing → rendering
- **Memory Pressure**: Test behavior under low memory conditions
- **Large Datasets**: Validate with 1000+ photos

## Risk Mitigation

### Potential Issues & Solutions
1. **Memory Spikes**: Pre-allocate bitmap pools, limit concurrent generation
2. **Atlas Fragmentation**: Periodic regeneration during idle time
3. **Loading Performance**: Progressive quality with quick previews
4. **Device Compatibility**: Dynamic atlas size based on GPU capabilities

## Future Improvements (Post Phase 1)

### Batch Processing Enhancement
**Current Approach**: Sequential processing of all photos at once
**Future Enhancement**: Memory-aware batch processing for large photo sets

```kotlin
// Future implementation concept
class AtlasGenerator {
    suspend fun generateAtlas(
        photoUris: List<Uri>, 
        lodLevel: LODLevel,
        batchSize: Int = 50  // Configurable batch size
    ): AtlasResult {
        // Process photos in chunks with memory monitoring
        photoUris.chunked(batchSize).forEach { batch ->
            if (memoryMonitor.isMemoryPressure()) {
                // Handle memory pressure gracefully
            }
            // Process batch...
        }
    }
}
```

**Benefits of Batch Processing:**
- **Memory Control**: Process large photo sets without OOM
- **Progress Reporting**: User feedback during long operations
- **Cancellation Support**: Can interrupt processing between batches
- **Memory Pressure Handling**: Adaptive behavior under low memory
- **Multiple Atlas Support**: Generate multiple atlases for very large sets

**Implementation Prerequisites:**
- MemoryMonitor component (Task 2.2)
- Progress reporting system
- Atlas cache management (Task 2.2)
- Multiple atlas coordination logic

### Fallback Mechanisms
- **Atlas Generation Failure**: Fall back to colored placeholders
- **Memory Pressure**: Reduce atlas count and LOD levels
- **Performance Issues**: Disable advanced features on low-end devices

## Success Metrics

### Performance Indicators
- Smooth 60fps pan/zoom with 500+ photos
- < 2 second initial load time for visible photos
- Memory usage stays within budget under normal operation
- No visible "pop-in" artifacts during scrolling

### User Experience Goals  
- Immediate visual feedback (placeholder → preview → full quality)
- Smooth transitions between zoom levels
- Responsive touch interaction with no dropped frames
- Graceful degradation on older/slower devices

## Current Implementation Status

### ✅ Completed Tasks
- **Project analysis and architecture planning**
- **Reference documentation setup**
- **Task 1.1: Core Data Models** - TextureAtlas, AtlasRegion, LODLevel
- **Task 1.2: Photo Processing Pipeline** - PhotoLODProcessor, PhotoScaler with hardware-accelerated bilinear filtering
- **Task 1.3: Atlas Generation** - AtlasGenerator coordination layer, TexturePacker shelf algorithm

### 🔄 Phase 1 Progress: 75% Complete
**What's Working:**
- Complete atlas generation pipeline: URI → PhotoLODProcessor → TexturePacker → Atlas bitmap
- Hardware-accelerated scaling with bilinear filtering 
- Comprehensive failure tracking and partial success handling
- Memory-safe bitmap management with explicit cleanup
- Index-based coordinate mapping for reliable photo-to-atlas region tracking

**Key Implementation Decisions Made:**
- **Bilinear over Lanczos filtering**: Hardware-accelerated performance vs theoretical quality
- **Sequential processing**: Simple approach for Phase 1, batch processing documented for Phase 2
- **Immediate memory cleanup**: Explicit bitmap recycling to prevent accumulation
- **Partial success model**: Continue processing despite individual photo failures

### ⏳ Next Steps
- **Task 1.4: Atlas Manager** - Central coordinator for atlas lifecycle
- **Task 3.1: UI Integration** - Replace placeholder rectangles in MediaHexVisualization
- **Phase 2: Viewport Optimization** - Spatial indexing, cache management, memory monitoring
- **Phase 3: Performance & Integration** - ViewModel state management, predictive loading

---

## Checkpoint: December 29, 2025

### 📊 **Phase 1 Milestone Achieved**
Successfully implemented the core atlas generation foundation with a **working end-to-end pipeline**:

```
Photo URIs → PhotoLODProcessor → TexturePacker → AtlasGenerator → TextureAtlas
```

### 🎯 **Ready for Integration**
The atlas system is now functionally complete and ready for:
1. **UI Integration**: Replace colored rectangles with actual photo atlases
2. **Atlas Manager**: Add lifecycle coordination and caching
3. **Testing**: Validate with real photo datasets

### 📝 **Files Implemented**
- `data/PhotoScaler.kt` - Hardware-accelerated bitmap scaling
- `domain/usecase/PhotoLODProcessor.kt` - Photo processing for LOD levels  
- `data/TexturePacker.kt` - Shelf packing algorithm
- `domain/usecase/AtlasGenerator.kt` - Complete atlas coordination
- `domain/model/TextureAtlas.kt` - Atlas data structure
- `domain/model/AtlasRegion.kt` - Photo region metadata
- `domain/model/LODLevel.kt` - Level-of-detail configuration

### 🏗️ **Architecture Validated**
- **Clean separation**: Each component has single responsibility
- **Memory efficient**: Explicit cleanup and hardware acceleration
- **Failure resilient**: Comprehensive error handling and partial success
- **Performance focused**: Bilinear filtering and immediate cleanup
- **Well documented**: Patterns captured in team documentation

### 🚀 **Next Checkpoint Goals**
1. Complete Task 1.4 (AtlasManager) to finish Phase 1
2. Implement Task 3.1 (UI Integration) to see working photos
3. Begin Phase 2 (Viewport Optimization) for production readiness

---

## Checkpoint: December 29, 2025 - Architecture Refactoring Complete

### 🏗️ **Major Architecture Refactoring Achieved**
Successfully completed a comprehensive refactoring to create a clean, maintainable architecture:

#### ✅ **Task 1.5: Architecture Refactoring (COMPLETED)**
**Files created/modified:**
- `domain/model/HexCellWithMedia.kt` - Clean data structure with pre-computed positioning
- `domain/model/MediaWithPosition.kt` - Media items with absolute/relative coordinates  
- `domain/usecase/GenerateHexGridLayoutUseCase.kt` - Composed use case orchestrating layout generation
- `ui/MediaHexVisualization.kt` - **SIMPLIFIED**: Now accepts only `HexGridLayout`
- `ui/gallery/GalleryViewModel.kt` - **ENHANCED**: Generates hex grid layouts with coroutines
- `ui/App.kt` - **ENHANCED**: Manages layout generation and view centering

#### 🎯 **Architectural Benefits Achieved**
1. **Clean Separation of Concerns**:
   - **Domain Layer**: Contains all business logic (positioning, layout generation)
   - **UI Layer**: Pure rendering with no calculations
   - **ViewModel**: Orchestrates use cases and manages state

2. **Improved Maintainability**:
   - Positioning logic moved from UI to domain (testable)
   - Use case composition pattern for reusability
   - Clear data flow: `UseCase → ViewModel → UI`

3. **Performance Optimizations**:
   - Pre-computed positioning eliminates UI calculations
   - Preserved GeometryReader coordinate transformation system
   - Maintained cached FloatArray for matrix operations

#### 🎨 **Data Structure Design**
```kotlin
// NEW: Clean pre-computed layout structure
HexGridLayout {
    hexGrid: HexGrid                    // Geometry
    hexCellsWithMedia: List<HexCellWithMedia>  // Positioned content
    totalMediaCount: Int                // Computed properties
    bounds: Rect                        // Overall bounds
}

HexCellWithMedia {
    hexCell: HexCell                    // Hex geometry
    mediaItems: List<MediaWithPosition> // Positioned media
    bounds: Rect                        // Cached bounds
}

MediaWithPosition {
    media: Media                        // Original media data
    relativePosition: Offset            // Within hex cell
    size: Size                          // Computed size
    absoluteBounds: Rect                // World coordinates
    seed: Int                           // Consistent randomization
}
```

#### 🔧 **Use Case Composition Pattern**
```kotlin
// IMPLEMENTED: Clean composition of existing use cases
GenerateHexGridLayoutUseCase {
    constructor(
        getMediaUseCase: GetMediaUseCase,           // Data fetching
        groupMediaUseCase: GroupMediaUseCase,       // Grouping logic
        generateHexGridUseCase: GenerateHexGridUseCase,    // Grid geometry
        getHexGridParametersUseCase: GetHexGridParametersUseCase  // Configuration
    )
    
    suspend fun execute(density, canvasSize, groupingPeriod): HexGridLayout
    suspend fun execute(hexGrid, groupedMedia): HexGridLayout  // Alternative overload
}
```

### ✅ **Fixed Issues**

#### **Issue 1: Grid Centering (RESOLVED)**
- **Problem**: Canvas defaulted to (0,0) instead of showing hex grid center
- **Root Cause**: Grid generated with canvas-relative coordinates, but TransformableState initialized with no offset
- **Solution**: Added automatic view centering when layout is generated
```kotlin
// IMPLEMENTED in App.kt
LaunchedEffect(hexGridLayout) {
    hexGridLayout?.let { layout ->
        val gridCenter = layout.bounds.center
        val canvasCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val centerOffset = canvasCenter - gridCenter
        transformableState.updateMatrix {
            reset()
            postScale(1f, 1f)
            postTranslate(centerOffset.x, centerOffset.y)
        }
    }
}
```

#### **Issue 2: Click-to-Focus (PARTIALLY FIXED)**
- **Status**: Grid centering works, but click detection still not working
- **Progress**: 
  - ✅ Added click handlers to `MediaHexVisualization` call in App.kt
  - ✅ Preserved coordinate transformation logic
  - ✅ Maintained GeometryReader storage pattern
- **Remaining Issue**: Click coordinates not matching stored media bounds

### 🐛 **Outstanding Issues**

#### **Click Detection Not Working**
**Symptoms**:
- Grid displays correctly at center (0,0) cell visible
- Click handlers are called in App.kt (logs working)
- GeometryReader stores media bounds correctly
- But clicks don't trigger focus/zoom

**Debug Data Observed**:
```
Click: screen=Offset(490.0, 903.9), transformed=Offset(490.0, 903.9)
Stored 1 hex cells
Stored media 43 at Rect.fromLTRB(-1572.9, -1286.1, -1363.7, -1129.2)
```

**Analysis**:
- Click transformation: `screen == transformed` suggests `offset=Offset.Zero` and `zoom=1.0`
- Media bounds: Large negative coordinates suggest world coordinates are correct
- **Root Cause**: Coordinate space mismatch between click detection and GeometryReader storage

**Next Investigation Steps**:
1. Verify `transformableState.zoom` and `transformableState.offset` values during click
2. Check if GeometryReader bounds are being stored in correct coordinate space
3. Ensure click transformation uses current transform state, not initial state

### 🚀 **Ready for AtlasManager Implementation**

The architecture refactoring provides the perfect foundation for AtlasManager:

#### **Clean Integration Points**:
```kotlin
// READY: Clean interface for AtlasManager integration
interface AtlasManager {
    suspend fun updateViewport(viewport: Rect, zoomLevel: Float)
    fun getAtlasForRegion(regionKey: AtlasRegionKey): TextureAtlas?
    fun getPhotoRegion(photoId: String): AtlasRegion?
}

// READY: Viewport filtering with HexGridLayout
fun getVisibleHexCells(viewport: Rect, margin: Float = 0f): List<HexCellWithMedia> {
    return hexGridLayout.hexCellsWithMedia.filter { hexCellWithMedia ->
        hexCellWithMedia.isInViewport(viewport, margin)
    }
}
```

#### **Atlas Integration Strategy**:
1. **Phase 1A**: Implement basic AtlasManager with single-atlas approach
2. **Phase 1B**: Replace `drawRect()` in MediaHexVisualization with atlas rendering
3. **Phase 2**: Add viewport-based atlas optimization and caching

### 📝 **Implementation Notes for Resume**

#### **Current Codebase State**:
- ✅ **Architecture**: Clean, testable, maintainable
- ✅ **Data Flow**: `UseCase → ViewModel → UI` pattern established
- ✅ **Grid Display**: Properly centered and visible
- ❌ **Click Detection**: Coordinate transformation issue remains
- ⏳ **Atlas Integration**: Ready for implementation

#### **Next Session Priorities**:
1. **Fix click detection**: Debug coordinate transformation mismatch
2. **Basic AtlasManager**: Implement single-atlas generation for visible photos
3. **UI Integration**: Replace placeholder rectangles with atlas rendering

#### **Key Files for Next Session**:
- `ui/MediaHexVisualization.kt` - Fix click coordinate transformation
- `domain/usecase/AtlasManager.kt` - Implement viewport-based atlas generation
- `ui/App.kt` - Integrate AtlasManager with viewport updates

---

> **💡 Implementation Notes**  
> - Architecture refactoring complete - clean foundation established
> - Click detection needs coordinate space debugging
> - Ready for AtlasManager implementation with viewport filtering
> - Reference atlas-system-design.md for AtlasManager interface specification