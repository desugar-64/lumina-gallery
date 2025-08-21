# Atlas Generation Rules & Trigger Events

## Overview

This document defines the comprehensive rules and trigger events for texture atlas generation in LuminaGallery. The atlas system is responsible for efficiently packing and rendering photos across multiple zoom levels using a sophisticated Level-of-Detail (LOD) system with device-aware optimizations.

## Atlas Generation Architecture

### Core Components

#### Atlas Generation Layer
- **EnhancedAtlasGenerator**: Primary atlas generation coordinator using multi-atlas system
- **DynamicAtlasPool**: Manages multiple atlas sizes (2K/4K/8K) based on device capabilities
- **AtlasManager**: High-level atlas lifecycle coordinator with trigger event handling
- **TexturePacker**: Shelf packing algorithm for efficient photo placement
- **PhotoLODProcessor**: Photo processing for specific LOD levels with Result-type error handling
- **SmartMemoryManager**: Memory pressure monitoring and emergency cleanup

#### Functional Composition Layer
- **AtlasStrategySelector**: Pure functions for strategy selection logic with rule composition
- **AtlasRegenerationDecider**: Pure functions for regeneration decision logic with decision rules
- **EnhancedAtlasComposer**: Pure functions for atlas generation pipeline configuration
- **PhotoAtlasComposer**: Pure functions for photo distribution and LOD-aware calculations
- **DeviceCapabilityComposer**: Pure functions for device capability decisions and memory pressure
- **MemoryAllocationComposer**: Pure functions for memory management and leak detection
- **HexGridLayoutComposer**: Pure functions for grid layout generation without mutable state

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

#### Strategy Selection Process
- **AtlasStrategySelector.selectStrategy()**: Pure function determines optimal strategy using context analysis
- **Rule-Based Selection**: Strategy selectors with appliesTo() predicates evaluate conditions sequentially
- **Context-Driven Decisions**: Memory pressure, device capabilities, and photo priorities influence selection
- **Function Composition**: First-match semantics with strategy selectors for clean decision logic

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

#### Functional Memory Decisions
- **MemoryAllocationComposer.selectRecommendedLOD()**: Pure LOD selection logic based on memory constraints
- **Memory Leak Detection**: Functional analysis of memory snapshots using atlas count stability metrics
- **Allocation Decisions**: Pure functions for memory constraint evaluation and budget calculations
- **Predictive Analysis**: Memory usage prediction using pure functions without side effects

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

#### Comprehensive Result Type Usage
- **PhotoProcessingResult**: Primary photo processing error handling with Success/Failed variants
- **Error Propagation**: Result types flow through the entire atlas generation pipeline
- **Pattern Matching**: when expressions replace try/catch blocks for cleaner error handling
- **Future Result Types**: Additional Result type candidates for atlas generation and memory management operations

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
- **Result-Type Error Handling**: PhotoProcessingResult sealed class with Success/Failed variants
- **Explicit Error Context**: Detailed error messages with retry information and failure causes
- **Functional Error Composition**: Pattern matching with when expressions instead of nullable checks

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

## Functional Programming Architecture

### Pure Function Extraction

The atlas generation system uses functional programming patterns with pure function objects that separate decision logic from side effects:

#### Strategy Selection (AtlasStrategySelector)
- **selectStrategy()**: Pure strategy selection based on context with rule composition
- **selectDistributionStrategy()**: Rule-based distribution strategy selection with strategy selectors
- **Function Composition**: Strategy selectors with appliesTo() predicates and decision pipelines
- **Rule Pipeline**: Sequential evaluation with first-match semantics for strategy selection

#### Decision Making (AtlasRegenerationDecider)  
- **determineRegenerationDecision()**: Pure regeneration decision logic using decision rules
- **Rule Composition**: checkNoAtlasesAvailable, checkRecycledBitmaps, checkCellSetChanged, checkLODLevelChanged
- **Decision Types**: NO_REGENERATION/SELECTIVE_REGENERATION/FULL_REGENERATION based on context
- **Functional Pipeline**: Rules applied in sequence with first-match decision selection

#### Photo Distribution (PhotoAtlasComposer)
- **calculateMinPhotosPerAtlas()**: LOD-aware photo threshold calculation using pure functions
- **sortAtlasSizesByLOD()**: Atlas size sorting optimized for LOD requirements (LOD 5+ gets largest first)
- **requiresMemoryAwareHandling()**: Memory-aware handling decisions for high LOD levels
- **Pure Logic**: No side effects, deterministic output based on LOD level input

#### Device Capabilities (DeviceCapabilityComposer)
- **selectOptimalAtlasSize()**: Memory tier-based atlas size selection (HIGH→8K, MEDIUM→4K, LOW→4K, MINIMAL→2K)
- **buildRecommendedAtlasSizes()**: Functional list composition replacing mutable list building
- **calculateMemoryPressure()**: Pure memory pressure calculation as percentage (0.0-1.0)
- **Immutable Operations**: listOfNotNull() and other functional list operations

#### Memory Management (MemoryAllocationComposer)
- **selectRecommendedLOD()**: LOD selection based on memory constraints with fallback logic
- **detectMemoryLeak()**: Memory leak detection from snapshot analysis using atlas count stability
- **Pure Analysis**: Memory decision logic extracted from SmartMemoryManager side effects
- **Context-Based Decisions**: Memory allocation decisions based on usage predictions

#### Grid Layout (HexGridLayoutComposer)
- **generateHexCellsWithMedia()**: Grid layout generation without mutable state accumulation
- **Functional Composition**: mapIndexedNotNull() replaces imperative mutable list building
- **Pure Transformations**: Immutable data transformations for hex cell generation

### Result Type System

#### PhotoProcessingResult Sealed Class
Replaces nullable return types with explicit error handling:

```kotlin
sealed class PhotoProcessingResult {
    data class Success(val processedPhoto: ProcessedPhoto) : PhotoProcessingResult()
    data class Failed(
        val photoUri: Uri,
        val error: String,
        val retryable: Boolean,
        val cause: Throwable? = null
    ) : PhotoProcessingResult()
}
```

#### Error Handling Pipeline
- **No Null Returns**: All photo processing returns explicit Result types with context
- **Error Propagation**: Failed results propagate with detailed error messages through processing pipeline
- **Pattern Matching**: when expressions for Result type handling replace try/catch blocks
- **Retry Logic**: Retryable flag indicates whether operation should be attempted again
- **Debugging Support**: Optional cause field preserves original exception for detailed analysis
- **Explicit Context**: photoUri and error description provide clear failure information

#### Benefits of Result Types
- **Type Safety**: Compiler enforces error handling at call sites
- **Explicit Error States**: No hidden null pointer exceptions from missing null checks
- **Better Debugging**: Detailed error context with original URI and failure reasons
- **Functional Composition**: Result types work naturally with functional programming patterns
- **Improved Testing**: Easy to test both success and failure cases with explicit types

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

1. **Trigger Detection**: Event evaluation using AtlasRegenerationDecider pure functions
2. **Strategy Selection**: AtlasStrategySelector determines optimal generation strategy using rule composition
3. **Photo Processing**: PhotoLODProcessor with Result-type error handling (Success/Failed variants)
4. **Photo Distribution**: PhotoAtlasComposer pure functions for LOD-aware distribution calculations
5. **Shelf Packing**: Efficient photo placement using functional composition patterns
6. **Atlas Creation**: DeviceCapabilityComposer-guided bitmap composition with memory tier decisions
7. **Memory Management**: MemoryAllocationComposer for allocation decisions and leak detection
8. **State Updates**: Atomic state updates with functional decision composition and race condition prevention

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
- **Functional Overhead**: Minimal performance impact from pure function extraction (~1-2% overhead)
- **Decision Caching**: Pure functions enable better decision caching opportunities for repeated operations
- **Testability Performance**: Faster test execution due to pure function isolation and reduced side effects

## Conclusion

This atlas generation system provides efficient, device-aware photo rendering with intelligent trigger events that balance performance, memory usage, and visual quality. The LOD-based approach ensures optimal resource utilization across all zoom levels while the sophisticated trigger system prevents unnecessary regeneration during normal user interactions.

The functional programming architecture with pure function composition, Result-type error handling, and extracted decision logic provides improved testability, maintainability, and debugging capabilities while preserving 100% behavioral compatibility with the original imperative implementation. The separation of pure decision logic from side effects enables better reasoning about the system behavior and easier testing of complex atlas generation scenarios.