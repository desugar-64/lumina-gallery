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

#### ⏳ Task 1.2: Photo Processing Pipeline
**Files to create:**
- `domain/usecase/PhotoLODProcessor.kt` - Scales photos to LOD levels
- `data/PhotoScaler.kt` - Bitmap scaling implementation

**Responsibilities:**
- Load photos from MediaStore URIs
- Scale to appropriate LOD resolution
- Maintain aspect ratios
- Use high-quality scaling (Lanczos filtering)

#### ⏳ Task 1.3: Atlas Generation
**Files to create:**
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
- Project analysis and architecture planning
- Reference documentation setup

### 🔄 In Progress
- Task 1.1: Creating core atlas data models

### ⏳ Pending Tasks
- Photo processing pipeline implementation
- Atlas generation with texture packing
- Atlas manager and coordination logic
- Spatial indexing and viewport optimization
- Cache management and memory handling
- UI integration and state management
- Performance optimization and testing

---

> **💡 Implementation Notes**  
> - Start with Task 1.1 (Core Data Models) to establish the foundation
> - Reference the comprehensive design document for detailed specifications
> - Maintain clean architecture principles throughout implementation
> - Test incrementally after each major component completion