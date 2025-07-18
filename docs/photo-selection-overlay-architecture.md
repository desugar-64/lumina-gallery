
## ️🔄 2025-07-18 Update: Active-Cell–Only Metadata Prefetching

The data-flow diagram now includes an *Active-Cell Prefetch* stage. Core changes:

```
UI Components ──▶ SelectionStateManager ──▶ MediaMetadataRepository
                                        └─▶ prefetchActiveCell()  (NEW)
```

Repository public API additions:

```kotlin
@Deprecated("Use prefetchActiveCell instead")
fun prefetchVisibleCells(visibleCells: List<HexCellWithMedia>,
                         loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL)

fun prefetchActiveCell(cell: HexCellWithMedia,
                       selectedMediaId: Long?,
                       loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL)
```

Only the media inside the active cell are loaded in small concurrent batches after cancelling any obsolete jobs, greatly reducing background work.

# Photo Selection Overlay Architecture

## Overview

The Photo Selection Overlay system is a sophisticated feature that provides rich metadata display and navigation capabilities for media items in the LuminaGallery hex grid. It implements a hybrid metadata fetching strategy with intelligent caching, viewport-aware selection logic, and reactive state management to deliver a responsive user experience that scales to thousands of images.

The system automatically shows detailed photo information when users zoom into images (>70% viewport occupation) or manually select photos, providing EXIF data, camera settings, location information, and cell-based navigation without compromising performance.

## System Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────────────┐
│                    Photo Selection Overlay System                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   UI Layer      │  │  State Layer    │  │  Data Layer     │ │
│  │                 │  │                 │  │                 │ │
│  │ PhotoSelection  │  │ PhotoSelection  │  │ MediaMetadata   │ │
│  │ Overlay         │  │ State           │  │ Repository      │ │
│  │                 │  │                 │  │                 │ │
│  │ PhotoMetadata   │  │ SelectionState  │  │ ExifData        │ │
│  │ Panel           │  │ Manager         │  │ Extractor       │ │
│  │                 │  │                 │  │                 │ │
│  │ CellNavigation  │  │ SelectionMode   │  │ MetadataCache   │ │
│  │ Panel           │  │ Controller      │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Data Flow Diagram                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User Action                                                    │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────┐    Events    ┌─────────────────┐          │
│  │ UI Components   │────────────▶ │ SelectionState  │          │
│  │ - Overlay       │              │ Manager         │          │
│  │ - Metadata      │              │                 │          │
│  │ - Navigation    │              │ State Updates   │          │
│  └─────────────────┘              └─────────────────┘          │
│                                             │                   │
│                                             ▼                   │
│  ┌─────────────────┐    Metadata   ┌─────────────────┐          │
│  │ MetadataCache   │◀──────────────│ MediaMetadata   │          │
│  │ - Memory LRU    │    Requests   │ Repository      │          │
│  │ - Disk Cache    │               │                 │          │
│  └─────────────────┘               │ Hybrid Fetching │          │
│                                    └─────────────────┘          │
│                                             │                   │
│                                             ▼                   │
│                                    ┌─────────────────┐          │
│                                    │ ExifData        │          │
│                                    │ Extractor       │          │
│                                    │                 │          │
│                                    │ Progressive     │          │
│                                    │ Loading         │          │
│                                    └─────────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Component Hierarchy

### 1. Data Layer

#### MediaMetadata System
- **MediaMetadata**: Progressive loading structure (Essential → Technical → Advanced)
- **MetadataLoadingState**: Tracks loading progress and error states
- **SerializableLocation**: Cacheable GPS coordinate representation

#### Repository Pattern
- **MediaMetadataRepository**: Orchestrates metadata fetching with hybrid strategy
- **ExifDataExtractor**: Extracts EXIF data using Android's ExifInterface
- **MetadataCache**: Multi-layer caching with LRU memory and disk persistence

### 2. State Management Layer

#### Selection State
- **PhotoSelectionState**: Comprehensive state tracking for selection, overlay, and navigation
- **SelectionStateManager**: Reactive state management with event-driven updates
- **PhotoSelectionConfig**: Configurable behavior thresholds and auto-mode settings

#### Events System
- **PhotoSelectionEvent**: Sealed class hierarchy for all selection-related events
- **SelectionMode**: Enum defining auto/manual selection modes

### 3. UI Layer (Planned)

#### Overlay Components
- **PhotoSelectionOverlay**: Main overlay container with bottom panel design
- **PhotoMetadataPanel**: Displays EXIF data and file information
- **CellNavigationPanel**: Thumbnail strip for cell-based navigation

## State Management

### PhotoSelectionState Structure

```kotlin
data class PhotoSelectionState(
    val selectedMedia: Media? = null,
    val selectedMediaWithPosition: MediaWithPosition? = null,
    val selectionMode: SelectionMode = SelectionMode.NONE,
    val overlayVisible: Boolean = false,
    val currentCell: HexCellWithMedia? = null,
    val cellNavigationIndex: Int = 0,
    val isAutoMode: Boolean = true,
    val viewportOccupation: Float = 0f
)
```

### Event-Driven State Updates

```kotlin
sealed class PhotoSelectionEvent {
    data class PhotoSelected(/*...*/) : PhotoSelectionEvent()
    object PhotoDeselected : PhotoSelectionEvent()
    object NavigateNext : PhotoSelectionEvent()
    object NavigatePrevious : PhotoSelectionEvent()
    data class NavigateToIndex(val index: Int) : PhotoSelectionEvent()
    data class OverlayVisibilityChanged(val visible: Boolean) : PhotoSelectionEvent()
    data class ViewportOccupationChanged(val occupation: Float) : PhotoSelectionEvent()
    data class SelectionModeChanged(val mode: SelectionMode) : PhotoSelectionEvent()
}
```

## Behavior Specifications

### Auto-Mode Selection Logic

```
┌─────────────────────────────────────────────────────────────────┐
│                     Auto-Mode State Machine                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐  viewport > 70%  ┌─────────────────────────┐   │
│  │    NONE     │────────────────▶ │    AUTO_VIEWPORT        │   │
│  │             │                  │                         │   │
│  │ No Selection│                  │ Photo fills viewport    │   │
│  └─────────────┘                  └─────────────────────────┘   │
│         ▲                                     │                 │
│         │                                     │                 │
│         │ viewport < 30%                      │ overlay shown   │
│         │                                     ▼                 │
│         │                          ┌─────────────────────────┐  │
│         └──────────────────────────│    OVERLAY_ACTIVE       │  │
│                                    │                         │  │
│                                    │ Metadata displayed      │  │
│                                    │ Navigation available    │  │
│                                    └─────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Manual Selection Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Manual Selection Flow                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  User Tap on Photo                                              │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ Find Media in   │                                            │
│  │ Hex Grid        │                                            │
│  └─────────────────┘                                            │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ Determine Cell  │                                            │
│  │ and Index       │                                            │
│  └─────────────────┘                                            │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ Update State    │                                            │
│  │ Show Overlay    │                                            │
│  └─────────────────┘                                            │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ Trigger Metadata│                                            │
│  │ Loading         │                                            │
│  └─────────────────┘                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Metadata Loading Strategy

### Hybrid Fetching Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   Metadata Loading Strategy                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    Cache Hit    ┌─────────────────┐        │
│  │ Metadata        │──────────────▶  │ Return Cached   │        │
│  │ Request         │                 │ Data            │        │
│  └─────────────────┘                 └─────────────────┘        │
│         │                                                       │
│         │ Cache Miss                                            │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ Check Load      │                                            │
│  │ Level Required  │                                            │
│  └─────────────────┘                                            │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐    Essential    ┌─────────────────┐        │
│  │ Progressive     │────────────────▶ │ Camera Info     │        │
│  │ Loading         │                 │ Date, Location  │        │
│  │                 │    Technical    │ File Size       │        │
│  │                 │────────────────▶ ├─────────────────┤        │
│  │                 │                 │ Aperture, ISO   │        │
│  │                 │    Advanced     │ Focal Length    │        │
│  │                 │────────────────▶ ├─────────────────┤        │
│  │                 │                 │ Lens Info       │        │
│  │                 │                 │ Advanced EXIF   │        │
│  └─────────────────┘                 └─────────────────┘        │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ Cache Result    │                                            │
│  │ Memory + Disk   │                                            │
│  └─────────────────┘                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Caching Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                      Caching Architecture                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    Fast Access   ┌─────────────────┐       │
│  │ Memory Cache    │◀────────────────▶│ LRU Cache       │       │
│  │ (100 entries)   │                  │ ~5MB Budget     │       │
│  │                 │                  │                 │       │
│  └─────────────────┘                  └─────────────────┘       │
│         │                                     ▲                 │
│         │ Eviction                            │                 │
│         ▼                                     │                 │
│  ┌─────────────────┐   Persistence    ┌─────────────────┐       │
│  │ Disk Cache      │◀────────────────▶│ SharedPrefs     │       │
│  │ (500 entries)   │                  │ JSON Storage    │       │
│  │                 │                  │                 │       │
│  └─────────────────┘                  └─────────────────┘       │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────┐                                            │
│  │ Cleanup Policy  │                                            │
│  │ - Memory pressure response                                   │
│  │ - Cell-based eviction                                       │
│  │ - Size-based rotation                                       │
│  └─────────────────┘                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Performance Considerations

### Memory Management
- **Memory Cache**: LRU cache with 100 entries (~5MB budget)
- **Disk Cache**: SharedPreferences with 500 entry limit
- **Memory Pressure**: Automatic cache eviction during low memory conditions
- **Cell-based Eviction**: Remove cache entries when user navigates away from cells

### Loading Optimization
- **Progressive Loading**: Essential metadata loads first (~50ms), technical and advanced data loads on demand
- **Parallel Processing**: Multiple metadata extractions run concurrently with limited concurrency (5 concurrent operations)
- **Background Processing**: All I/O operations run on background threads to maintain UI responsiveness
- **Prefetching**: Metadata for current cell is prefetched in background

### Scalability
- **Handles thousands of images**: Efficient caching strategy prevents memory exhaustion
- **Viewport-aware**: Only processes metadata for visible or selected content
- **Lazy loading**: Metadata is only extracted when needed, not during initial media loading

## Integration Points

### Existing System Integration
- **Media Model**: Extended with `metadataState` field for loading state tracking
- **HexCellWithMedia**: Provides cell context for navigation and prefetching
- **MediaWithPosition**: Supplies positioning information for overlay display
- **StateFlow**: Reactive state management integrates with existing UI patterns

### Dependency Injection
- **Hilt Integration**: All components are properly annotated with `@Inject` and `@Singleton`
- **Repository Pattern**: Clean separation between data access and business logic
- **Testable Architecture**: Constructor injection enables easy unit testing

## Error Handling

### Metadata Loading Errors
- **IOException**: Network or file access errors are caught and result in `MetadataLoadingState.Error`
- **SecurityException**: Permission errors are handled gracefully with fallback to basic file information
- **Corruption Handling**: Corrupted cache entries are automatically removed and replaced
- **Graceful Degradation**: UI continues to function even if metadata loading fails

### Cache Error Recovery
- **Serialization Errors**: JSON parsing failures result in cache entry removal and fresh loading
- **Memory Pressure**: Cache automatically evicts entries and continues operating with reduced capacity
- **Disk Space**: Cache cleanup policies prevent disk space exhaustion

## Testing Strategy

### Unit Tests
- **State Management**: Test all event handling and state transitions
- **Metadata Extraction**: Test EXIF data parsing with various image formats
- **Caching Logic**: Test cache hit/miss scenarios and eviction policies
- **Progressive Loading**: Test different metadata load levels

### Integration Tests
- **End-to-End Flow**: Test complete user interaction from photo selection to metadata display
- **Performance Tests**: Measure metadata loading times and cache efficiency
- **Memory Tests**: Verify memory usage stays within acceptable bounds

### UI Tests
- **Overlay Visibility**: Test auto-show/hide behavior with different viewport occupations
- **Navigation**: Test cell-based navigation with swipe gestures
- **Error States**: Test UI behavior when metadata loading fails

## Related Components

### Core Dependencies
- **Media Model**: `domain/model/Media.kt` - Extended with metadata state
- **HexCellWithMedia**: `domain/model/HexCellWithMedia.kt` - Provides cell context
- **ExifInterface**: Android's built-in EXIF extraction library
- **Kotlinx Serialization**: JSON serialization for cache persistence

### Future Components
- **SelectionModeController**: Viewport-aware selection logic (planned)
- **PhotoSelectionOverlay**: Main UI overlay component (planned)
- **PhotoMetadataPanel**: Metadata display UI (planned)
- **CellNavigationPanel**: Cell navigation UI (planned)

## Configuration Options

### PhotoSelectionConfig
- **autoModeEnabled**: Enable/disable automatic overlay show/hide
- **autoShowThreshold**: Viewport occupation threshold for showing overlay (default: 70%)
- **autoHideThreshold**: Viewport occupation threshold for hiding overlay (default: 30%)
- **enableCellNavigation**: Enable cell-based photo navigation
- **enableSwipeGestures**: Enable swipe gestures for navigation
- **overlayAnimationDuration**: Overlay show/hide animation duration (default: 300ms)
- **metadataLoadingEnabled**: Enable/disable metadata loading entirely

This architecture provides a robust, scalable foundation for the photo selection overlay system while maintaining excellent performance characteristics and user experience quality.