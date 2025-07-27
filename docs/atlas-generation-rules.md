# Atlas Generation Rules & Trigger Events

## Overview

This document defines the comprehensive rules and trigger events for texture atlas generation in LuminaGallery. The atlas system is responsible for efficiently packing and rendering photos across multiple zoom levels using a sophisticated Level-of-Detail (LOD) system with device-aware optimizations.

## Atlas Generation Architecture

### Core Components

- **EnhancedAtlasGenerator**: Primary atlas generation coordinator using multi-atlas system
- **DynamicAtlasPool**: Manages multiple atlas sizes (2K/4K/8K) based on device capabilities
- **AtlasManager**: High-level atlas lifecycle coordinator with trigger event handling
- **TexturePacker**: Shelf packing algorithm for efficient photo placement
- **PhotoLODProcessor**: Photo processing for specific LOD levels
- **SmartMemoryManager**: Memory pressure monitoring and emergency cleanup

### Multi-Atlas Support

- **Atlas Sizes**: 2K (2048×2048), 4K (4096×4096), 8K (8192×8192)
- **Device-Aware Selection**: Automatic size selection based on device performance tier
- **Parallel Generation**: Multiple atlases generated concurrently for optimal performance
- **Fallback Strategy**: Automatic degradation to smaller sizes when photos don't fit

## Core Atlas Generation Rules

### 1. Multi-Atlas Architecture Rules

- **Primary System**: `EnhancedAtlasGenerator` coordinates multi-atlas generation via `DynamicAtlasPool`
- **Atlas Size Support**: 2K (2048×2048), 4K (4096×4096), 8K (8192×8192) based on device capabilities
- **Fallback Strategy**: Automatic fallback from larger to smaller atlas sizes when photos don't fit
- **Zero Photo Loss**: Emergency fallback system ensures every photo renders

### 2. LOD (Level-of-Detail) System Rules

#### 8-Level LOD System
- **LEVEL_0**: 32px resolution, zoom 0.0f-0.3f (ultra-tiny previews, ~4KB per photo)
- **LEVEL_1**: 64px resolution, zoom 0.3f-0.8f (tiny thumbnails, ~16KB per photo)
- **LEVEL_2**: 128px resolution, zoom 0.8f-1.5f (standard gallery browsing, ~64KB per photo)
- **LEVEL_3**: 192px resolution, zoom 1.5f-2.5f (enhanced detail, ~147KB per photo)
- **LEVEL_4**: 256px resolution, zoom 2.5f-4.0f (face recognition level, ~256KB per photo)
- **LEVEL_5**: 384px resolution, zoom 4.0f-6.5f (high-quality thumbnails, ~590KB per photo)
- **LEVEL_6**: 512px resolution, zoom 6.5f-10.0f (focused examination, ~1MB per photo)
- **LEVEL_7**: 768px resolution, zoom 10.0f-16.0f (near-fullscreen, ~2.3MB per photo)

#### LOD Selection Rules
- **Zoom-Based Selection**: Each LOD has specific zoom ranges for optimal performance
- **Memory Optimization**: Photos loaded at appropriate resolution for current zoom level
- **Priority Boost**: High-priority photos (selected) use maximum LOD level regardless of zoom
- **Boundary Detection**: Atlas regeneration only occurs when crossing LOD boundaries

### 3. Photo Distribution Rules

#### Three Distribution Strategies

**SINGLE_SIZE Strategy**:
- Use one atlas size for all photos
- Applied during memory-constrained scenarios
- Reduces memory pressure and atlas count

**MULTI_SIZE Strategy**:
- Use multiple atlas sizes for optimal utilization
- Default strategy for normal memory conditions
- Maximizes packing efficiency across different photo sizes

**PRIORITY_BASED Strategy**:
- High-priority photos get full atlas sizes
- Normal photos get smaller, LOD-appropriate sizes
- Used when selected photos need maximum quality

#### LOD-Aware Distribution Thresholds
- **LOD 5+**: Allow 1-2 photos per atlas (large photos need space)
- **LOD 2-4**: 3-4 photos minimum per atlas (balanced approach)
- **LOD 0-1**: 4+ photos minimum per atlas (small thumbnails pack efficiently)

### 4. Shelf Packing Algorithm Rules

#### Packing Strategy
- **Sort by Height**: Photos sorted descending by height for optimal packing
- **Shelf Creation**: Horizontal shelves created when photos don't fit existing shelves
- **Padding**: 2px padding around each photo to prevent texture bleeding
- **Utilization Target**: 90% target utilization for efficient atlas usage

#### Packing Validation
- **Fit Checking**: Dynamic shelf fitting simulation before photo placement
- **Dimension Validation**: Photos exceeding atlas dimensions are rejected
- **Overlap Prevention**: Shelf algorithm prevents photo overlap
- **Emergency Handling**: Oversized photos handled with appropriate atlas size selection

### 5. Memory Management Rules

#### Memory Pressure Handling
- **Memory Pressure Monitoring**: Automatic cleanup based on device memory status
- **Atlas Protection**: Critical atlases protected from emergency cleanup during generation
- **Bitmap Pool**: Reuse atlas bitmaps to reduce allocations and GC pressure
- **Direct Recycling**: Processed photo bitmaps recycled immediately after atlas creation

#### Memory Budget Management
- **Device-Aware Budgets**: Different memory budgets for HIGH/MEDIUM/LOW performance devices
- **Pressure Level Response**: NORMAL/MEDIUM/HIGH/CRITICAL pressure levels with appropriate actions
- **Emergency Cleanup**: Automatic atlas cleanup when critical memory pressure detected
- **Protected Atlas System**: Race condition prevention during atlas generation

### 6. Device-Aware Optimization Rules

#### Performance Tier Detection
- **HIGH Performance**: 6 parallel processors, 4K/8K atlas support, priority-based distribution
- **MEDIUM Performance**: 4 parallel processors, 2K/4K atlas support, multi-size distribution
- **LOW Performance**: 2 parallel processors, 2K atlas only, single-size distribution

#### Capability-Based Limits
- **Atlas Size Limits**: Device-specific maximum atlas sizes (2K/4K/8K)
- **Parallel Processing**: 2-6 parallel photo processors based on device capability
- **Memory Budget**: Respect device memory constraints for atlas generation
- **Emergency Degradation**: Automatic fallback to smaller atlas sizes on low-end devices

### 7. Quality and Rendering Rules

#### Zoom-Based Quality Configuration
- **Low Zoom Optimization**: RGB_565 bitmaps at zoom levels below 0.5f
- **High Zoom Quality**: ARGB_8888 bitmaps for detailed viewing
- **EXIF Orientation**: Automatic photo rotation based on EXIF metadata
- **Hardware Acceleration**: Bilinear filtering for bitmap scaling operations

#### Rendering Optimizations
- **Anti-aliasing**: Software canvas rendering with anti-aliasing enabled
- **Paint Configuration**: Optimized Paint settings for atlas composition
- **Bitmap Configuration**: Dynamic bitmap config based on zoom level
- **Canvas Performance**: Cached Paint objects to reduce allocations

### 8. Error Handling and Resilience Rules

#### Partial Success Handling
- **Continue on Failures**: Atlas generation continues even if some photos fail
- **Timeout Protection**: 10-second timeout per photo processing
- **Graceful Degradation**: Fallback to smaller atlas sizes on generation failures
- **Comprehensive Logging**: Detailed pipeline analysis for debugging and monitoring

#### Recovery Strategies
- **Atlas Restoration**: Automatic regeneration when atlas bitmaps are recycled
- **Memory Recovery**: Emergency cleanup and regeneration on memory pressure
- **Cancellation Support**: Coroutine cancellation checks throughout processing
- **Race Condition Prevention**: Sequence-based request tracking

### 9. Photo Processing Rules

#### LOD-Aware Loading
- **Sample Size Calculation**: Power-of-2 sample sizes for memory efficiency
- **Target Resolution Loading**: Photos loaded at target LOD resolution using sample size
- **Aspect Ratio Preservation**: Photos maintain original aspect ratios during scaling
- **EXIF Processing**: Orientation correction applied during loading

#### Processing Pipeline
- **I/O Separation**: Clear separation between disk I/O and memory operations
- **Parallel Processing**: Device-aware parallel photo processing
- **Memory Efficiency**: Direct bitmap allocation with immediate cleanup
- **Error Resilience**: Individual photo failures don't stop entire batch

### 10. Atlas Utilization and Optimization Rules

#### Utilization Thresholds
- **Minimum Utilization**: 30% for high LOD levels (LOD 4+), 50% for standard LOD levels
- **Target Utilization**: 90% optimal utilization across all atlas sizes
- **Atlas Size Selection**: Largest atlas first for LOD 5+, standard order for lower LODs
- **Remaining Photo Handling**: Single optimized atlas for leftover photos to prevent memory thrashing

#### Distribution Optimization
- **Multi-Pass Filling**: Multiple passes to maximize atlas utilization
- **Photo Grouping**: Intelligent photo grouping based on size and LOD requirements
- **Priority Separation**: High-priority and normal-priority photos distributed separately
- **Memory-Aware Decisions**: Distribution strategy adapted based on memory pressure

## Atlas Generation Trigger Events

### 1. Initial App Launch Events

#### Startup Sequence
- **Media Loading Complete**: When `getMediaUseCase()` finishes loading photos from MediaStore
- **Hex Grid Layout Generation**: When `generateHexGridLayoutUseCase` creates the hexagonal grid layout with canvas size
- **First Viewport Calculation**: When UI becomes visible and `onVisibleCellsChanged()` is first called
- **Initial Centering**: When grid is centered on canvas and initial zoom level is established

### 2. User Interaction Events

#### Pan/Zoom Gestures

**LOD Boundary Crossings** (Only these trigger regeneration):
- **0.3f boundary**: LEVEL_0 ↔ LEVEL_1 transition (32px ↔ 64px)
- **0.8f boundary**: LEVEL_1 ↔ LEVEL_2 transition (64px ↔ 128px)
- **1.5f boundary**: LEVEL_2 ↔ LEVEL_3 transition (128px ↔ 192px)
- **2.5f boundary**: LEVEL_3 ↔ LEVEL_4 transition (192px ↔ 256px)
- **4.0f boundary**: LEVEL_4 ↔ LEVEL_5 transition (256px ↔ 384px)
- **6.5f boundary**: LEVEL_5 ↔ LEVEL_6 transition (384px ↔ 512px)
- **10.0f boundary**: LEVEL_6 ↔ LEVEL_7 transition (512px ↔ 768px)

**Non-Triggering Zoom Events**:
- **Within-LOD Zoom**: Zoom changes within LOD boundaries (e.g., 1.0f → 1.3f stays in LEVEL_2)
- **Smooth Gestures**: Continuous zoom gestures use existing atlases until boundary crossing
- **Minor Adjustments**: Small zoom adjustments don't trigger regeneration

#### Viewport Changes
- **Pan to Different Cells**: When user pans and visible hex cells change
- **Viewport Expansion**: When margin rings expand visible cell set
- **Cell Set Changes**: When the set of visible cells differs from current cached set

#### Photo Selection Events
- **Photo Selection Change**: When `selectedMedia` changes between different photos
- **Selection Mode Transitions**: CELL_MODE ↔ PHOTO_MODE mode changes
- **High Priority Selection**: Selected photos trigger **selective regeneration** at maximum LOD_7 quality
- **Deselection Events**: When photo selection is cleared

### 3. System-Level Events

#### Memory Pressure Events
## Disabled temporarily(!)
- **Critical Memory Pressure**: Triggers emergency cleanup and potential regeneration with smaller atlas sizes
- **High Memory Pressure**: Reduces parallel processing and atlas sizes
- **Memory Recovery**: When memory pressure subsides and larger atlases can be used again
- **GC Events**: Garbage collection events that may recycle atlas bitmaps

#### Application Lifecycle Events
- **App Restoration**: When app returns from background and atlas bitmaps are recycled
- **Process Recreation**: When Android recreates the app process
- **Memory Trimming**: When system requests memory trimming
- **Background/Foreground**: App lifecycle state changes

#### Configuration Changes
- **Screen Rotation**: Canvas size change triggers hex grid regeneration and subsequent atlas update
- **Multi-Window Mode**: Canvas size adjustments trigger viewport recalculation
- **Display Density Changes**: DPI changes that affect rendering
- **Dark/Light Theme**: Theme changes that might affect rendering quality

### 4. Viewport State Events

#### Primary Trigger Function
```kotlin
// From GalleryViewModel:177
fun onVisibleCellsChanged(
    visibleCells: List<HexCellWithMedia>,
    currentZoom: Float,
    selectedMedia: Media? = null,
    selectionMode: SelectionMode = SelectionMode.CELL_MODE
)
```

#### Trigger Conditions Evaluated
1. **Cell Set Changed**: `currentCellIds != cellSetKey` - Different visible cells
2. **LOD Level Changed**: `currentLODLevel != lodLevel` - Zoom boundary crossing
3. **Atlas Bitmaps Recycled**: `currentAtlases.any { it.bitmap.isRecycled }` - Memory pressure recovery
4. **No Atlases Available**: `currentAtlases.isEmpty()` - Initial generation or complete failure
5. **Selected Media Changed**: `currentSelectedMedia != selectedMedia` - Photo selection change

### 5. Regeneration Decision Logic

#### Three Regeneration Types

**FULL_REGENERATION** (Complete Rebuild):
- **First Launch**: No atlases available, initial generation required
- **LOD Boundary Crossing**: Zoom boundaries requiring different photo resolutions
- **Viewport Changes**: Different visible cells requiring new photo sets
- **Atlas Recovery**: Atlas bitmaps recycled due to memory pressure or app restoration
- **Configuration Changes**: Screen rotation or significant layout changes

**SELECTIVE_REGENERATION** (High-Priority Photo Only):
- **Photo Selection**: Selected media changed in PHOTO_MODE
- **Quality Boost**: Selected photo needs maximum quality (LOD_7)
- **Atlas Preservation**: Existing atlases preserved, only high-priority atlas added/replaced
- **Memory Efficiency**: Minimizes memory usage and generation time

**NO_REGENERATION** (Use Existing):
- **Stable State**: Same cells, same LOD level, same selection
- **Within-LOD Zoom**: Zoom changes within current LOD boundary
- **Valid Atlases**: All atlas bitmaps still valid and available
- **No Changes**: No trigger conditions met

### 6. Flow-Based Trigger System

#### Reactive Atlas Updates
```kotlin
// From GalleryViewModel:89-142
updateAtlasFlow
    .filter { it.first.isNotEmpty() }        // Only process non-empty cell sets
    .distinctUntilChanged()                  // Prevent duplicate generations
    .collectLatest { (visibleCells, currentZoom, selectedMedia, selectionMode) ->
        atlasManager.updateVisibleCells(...)
    }
```

#### Flow Processing Features
- **Debouncing**: Uses `distinctUntilChanged()` to prevent duplicate generations
- **Cancellation**: Previous atlas generation cancelled when new request arrives using `collectLatest`
- **Sequence Tracking**: Prevents stale results with monotonic `requestSequence` numbers
- **Error Handling**: Comprehensive error handling with fallback strategies

#### Race Condition Prevention
- **Request Sequencing**: Monotonically increasing sequence numbers ensure "latest wins"
- **Mutex Protection**: Atlas state updates protected by mutex for thread safety
- **Atomic Updates**: State changes applied atomically to prevent inconsistencies
- **Stale Result Filtering**: UI only updates with results newer than current sequence

### 7. Benchmark and Debug Events

#### Automated Testing Events
- **Auto-Zoom Sequences**: Programmatic zoom changes to test LOD transitions
- **Benchmark Mode**: Automated interaction sequences for performance testing
- **LOD Transition Testing**: Systematic testing of all LOD boundary crossings
- **Memory Stress Testing**: Artificial memory pressure to test emergency cleanup

#### Debug and Monitoring Events
- **Memory Status Updates**: Every 500ms for debug overlay responsiveness
- **Manual Refresh**: Debug panel refresh button triggers
- **Performance Metrics**: Atlas generation performance tracking
- **Debug Overlay**: Real-time atlas state visualization

### 8. Advanced Trigger Optimizations

#### Smart Trigger Prevention
- **Zoom Velocity Detection**: Fast zoom gestures may skip intermediate LOD levels
- **Gesture Completion**: Wait for gesture completion before triggering regeneration
- **Threshold Hysteresis**: Small buffer zones around LOD boundaries to prevent flickering
- **Memory-Aware Delays**: Delayed regeneration during high memory pressure

#### Performance Optimizations
- **Predictive Loading**: Pre-load adjacent LOD levels during stable periods
- **Background Generation**: Generate atlases for likely future states
- **Incremental Updates**: Partial atlas updates when possible
- **Cache Persistence**: Atlas caching across app sessions where appropriate

## Technical Implementation Details

### Atlas Generation Pipeline

1. **Trigger Detection**: Event evaluation and regeneration decision
2. **Photo Processing**: LOD-aware photo loading and scaling
3. **Photo Distribution**: Device-aware distribution across multiple atlases
4. **Shelf Packing**: Efficient photo placement using shelf algorithm
5. **Atlas Creation**: Bitmap composition with hardware acceleration
6. **Memory Management**: Registration, protection, and cleanup
7. **State Updates**: Atomic state updates with race condition prevention

### Key Optimization: LOD Boundary Detection

Atlas regeneration is **highly optimized** - it only occurs when crossing specific zoom thresholds (0.3f, 0.8f, 1.5f, 2.5f, 4.0f, 6.5f, 10.0f), not on every zoom change. This prevents excessive regeneration during smooth zoom gestures while ensuring optimal image quality at each zoom level.

### Memory Safety Guarantees

- **Protected Generation**: New atlases protected from cleanup during generation
- **Atomic Replacement**: Old atlases only released after new ones are ready
- **Emergency Fallback**: Critical memory pressure triggers immediate cleanup with regeneration
- **Leak Prevention**: All bitmap resources properly tracked and cleaned up

### Performance Characteristics

- **Generation Time**: Typically 100-500ms for standard photo sets
- **Memory Usage**: 10-50MB per atlas depending on LOD level and device capabilities
- **CPU Usage**: Parallelized across available cores with device-aware limits
- **I/O Efficiency**: Optimized photo loading with sample size calculation

## Conclusion

This atlas generation system provides efficient, device-aware photo rendering with intelligent trigger events that balance performance, memory usage, and visual quality. The LOD-based approach ensures optimal resource utilization across all zoom levels while the sophisticated trigger system prevents unnecessary regeneration during normal user interactions.