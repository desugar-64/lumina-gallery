# Photo Selection Overlay API Documentation

## Overview

This document provides comprehensive API documentation for the Photo Selection Overlay system, including all public interfaces, method signatures, and usage examples.

## Core Data Models

### MediaMetadata

```kotlin
@Serializable
data class MediaMetadata(
    val essential: EssentialMetadata,
    val technical: TechnicalMetadata? = null,
    val advanced: AdvancedMetadata? = null
)
```

#### EssentialMetadata
Essential metadata that loads quickly and is always needed.

```kotlin
@Serializable
data class EssentialMetadata(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val dateTime: String? = null,
    val location: SerializableLocation? = null,
    val orientation: Int? = null,
    val fileSizeFormatted: String
)
```

#### TechnicalMetadata
Technical metadata that provides detailed camera settings.

```kotlin
@Serializable
data class TechnicalMetadata(
    val aperture: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val exposureTime: String? = null,
    val whiteBalance: String? = null,
    val flash: String? = null,
    val imageLength: Int? = null,
    val imageWidth: Int? = null
)
```

#### AdvancedMetadata
Advanced metadata with detailed camera and lens information.

```kotlin
@Serializable
data class AdvancedMetadata(
    val lensMake: String? = null,
    val lensModel: String? = null,
    val sceneMode: String? = null,
    val meteringMode: String? = null,
    val colorSpace: String? = null,
    val software: String? = null,
    val gpsAltitude: Double? = null,
    val gpsTimestamp: String? = null
)
```

### MetadataLoadingState

```kotlin
sealed class MetadataLoadingState {
    object NotLoaded : MetadataLoadingState()
    object Loading : MetadataLoadingState()
    data class Loaded(val metadata: MediaMetadata) : MetadataLoadingState()
    data class Error(val exception: Exception) : MetadataLoadingState()
}
```

### SerializableLocation

```kotlin
@Serializable
data class SerializableLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f
) {
    companion object {
        fun fromLocation(location: Location): SerializableLocation
    }
    
    fun toLocation(): Location
}
```

## State Management

### PhotoSelectionState

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

#### Computed Properties

```kotlin
val totalPhotosInCell: Int
val hasMultiplePhotosInCell: Boolean
val currentPhotoPosition: Int
val shouldShowOverlay: Boolean
val shouldShowNavigation: Boolean
val cellMediaItems: List<MediaWithPosition>
val canNavigatePrevious: Boolean
val canNavigateNext: Boolean
```

### SelectionMode

```kotlin
enum class SelectionMode {
    NONE,              // No selection active
    AUTO_VIEWPORT,     // Automatic selection based on viewport occupation
    MANUAL_SELECTION,  // Manual selection triggered by user tap
    OVERLAY_ACTIVE     // Photo is focused and overlay is showing
}
```

### PhotoSelectionEvent

```kotlin
sealed class PhotoSelectionEvent {
    data class PhotoSelected(
        val media: Media,
        val mediaWithPosition: MediaWithPosition,
        val cell: HexCellWithMedia,
        val cellIndex: Int,
        val mode: SelectionMode
    ) : PhotoSelectionEvent()
    
    object PhotoDeselected : PhotoSelectionEvent()
    object NavigateNext : PhotoSelectionEvent()
    object NavigatePrevious : PhotoSelectionEvent()
    data class NavigateToIndex(val index: Int) : PhotoSelectionEvent()
    data class OverlayVisibilityChanged(val visible: Boolean) : PhotoSelectionEvent()
    data class ViewportOccupationChanged(val occupation: Float) : PhotoSelectionEvent()
    data class SelectionModeChanged(val mode: SelectionMode) : PhotoSelectionEvent()
}
```

### PhotoSelectionConfig

```kotlin
data class PhotoSelectionConfig(
    val autoModeEnabled: Boolean = true,
    val autoShowThreshold: Float = 0.7f,
    val autoHideThreshold: Float = 0.3f,
    val enableCellNavigation: Boolean = true,
    val enableSwipeGestures: Boolean = true,
    val overlayAnimationDuration: Long = 300L,
    val metadataLoadingEnabled: Boolean = true
)
```

#### Methods

```kotlin
fun shouldAutoShow(occupation: Float): Boolean
fun shouldAutoHide(occupation: Float): Boolean
```

## Repository Layer

### MediaMetadataRepository

```kotlin
@Singleton
class MediaMetadataRepository @Inject constructor(
    private val exifExtractor: ExifDataExtractor,
    private val cache: MetadataCache,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
)
```

#### Core Methods

```kotlin
suspend fun getMetadata(
    media: Media,
    loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
): MediaMetadata?
```
Fetches metadata for a specific media item with on-demand loading.

**Parameters:**
- `media`: The media item to fetch metadata for
- `loadLevel`: The level of metadata detail to load

**Returns:** Cached metadata if available, null if loading is in progress

```kotlin
fun prefetchCellMetadata(
    cell: HexCellWithMedia,
    loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
)
```
Prefetches metadata for all media in a hex cell in background.

**Parameters:**
- `cell`: The hex cell containing media to prefetch
- `loadLevel`: The level of metadata detail to load

```kotlin
fun prefetchVisibleCells(
    visibleCells: List<HexCellWithMedia>,
    loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
)
```
Prefetches metadata for visible cells in current viewport.

**Parameters:**
- `visibleCells`: List of visible hex cells
- `loadLevel`: The level of metadata detail to load

#### State Management

```kotlin
val metadataStates: StateFlow<Map<String, MetadataLoadingState>>
```
Reactive state flow for metadata loading states.

```kotlin
fun getMetadataState(mediaId: String): MetadataLoadingState
```
Gets current metadata loading state for a specific media item.

```kotlin
suspend fun preloadHighPriorityMetadata(
    media: Media,
    loadLevel: MetadataLoadLevel = MetadataLoadLevel.ADVANCED
)
```
Preloads metadata for high-priority media items with full detail.

#### Cache Management

```kotlin
suspend fun clearCache()
fun clearMemoryCache()
fun getCacheStats(): CacheStats
fun cancelAllJobs()
```

### ExifDataExtractor

```kotlin
@Singleton
class ExifDataExtractor @Inject constructor(
    private val context: Context
)
```

#### Core Method

```kotlin
suspend fun extractMetadata(
    uri: Uri,
    fileSize: Long,
    loadLevel: MetadataLoadLevel = MetadataLoadLevel.ESSENTIAL
): MediaMetadata?
```
Extracts metadata from a media file using progressive loading.

**Parameters:**
- `uri`: URI of the media file
- `fileSize`: Size of the file in bytes
- `loadLevel`: Level of metadata detail to extract

**Returns:** Extracted metadata or null if extraction fails

#### MetadataLoadLevel

```kotlin
enum class MetadataLoadLevel {
    ESSENTIAL,    // Basic info (camera, date, location) - fast loading
    TECHNICAL,    // Camera settings (aperture, ISO, etc.) - medium loading
    ADVANCED      // Full metadata including lens info - slower loading
}
```

### MetadataCache

```kotlin
@Singleton
class MetadataCache @Inject constructor(
    private val context: Context
)
```

#### Cache Operations

```kotlin
suspend fun getMetadata(mediaId: String): MediaMetadata?
suspend fun putMetadata(mediaId: String, metadata: MediaMetadata)
suspend fun removeMetadata(mediaId: String)
suspend fun clearAll()
fun clearMemoryCache()
```

#### Statistics

```kotlin
fun getCacheStats(): CacheStats
suspend fun prefetchMetadata(mediaIds: List<String>)
```

#### CacheStats

```kotlin
data class CacheStats(
    val memoryHitCount: Long,
    val memoryMissCount: Long,
    val memorySize: Int,
    val memoryMaxSize: Int,
    val diskSize: Int
) {
    val memoryHitRate: Float
}
```

## State Management

### SelectionStateManager

```kotlin
@Singleton
class SelectionStateManager @Inject constructor()
```

#### State Access

```kotlin
val state: StateFlow<PhotoSelectionState>
val config: StateFlow<PhotoSelectionConfig>
```

#### Event Handling

```kotlin
fun handleEvent(event: PhotoSelectionEvent)
```
Handles photo selection events and updates state accordingly.

#### Convenience Methods

```kotlin
fun selectPhoto(
    media: Media,
    cells: List<HexCellWithMedia>,
    mode: SelectionMode = SelectionMode.MANUAL_SELECTION
)
```
Convenience method to select a photo by finding it in the grid.

```kotlin
fun isSelected(media: Media): Boolean
```
Checks if a media item is currently selected.

```kotlin
fun getCurrentState(): PhotoSelectionState
fun getCurrentConfig(): PhotoSelectionConfig
fun updateConfig(config: PhotoSelectionConfig)
```

## Usage Examples

### Basic Metadata Loading

```kotlin
class GalleryViewModel @Inject constructor(
    private val metadataRepository: MediaMetadataRepository
) {
    suspend fun loadPhotoMetadata(media: Media) {
        val metadata = metadataRepository.getMetadata(
            media = media,
            loadLevel = MetadataLoadLevel.ESSENTIAL
        )
        
        if (metadata != null) {
            // Use cached metadata immediately
            updateUI(metadata)
        } else {
            // Metadata is loading in background
            // Subscribe to metadataStates for updates
        }
    }
}
```

### Selection State Management

```kotlin
class PhotoOverlayViewModel @Inject constructor(
    private val selectionStateManager: SelectionStateManager
) {
    val selectionState = selectionStateManager.state
    
    fun onPhotoTapped(media: Media, cells: List<HexCellWithMedia>) {
        selectionStateManager.selectPhoto(
            media = media,
            cells = cells,
            mode = SelectionMode.MANUAL_SELECTION
        )
    }
    
    fun onNavigateNext() {
        selectionStateManager.handleEvent(PhotoSelectionEvent.NavigateNext)
    }
    
    fun onViewportChanged(occupation: Float) {
        selectionStateManager.handleEvent(
            PhotoSelectionEvent.ViewportOccupationChanged(occupation)
        )
    }
}
```

### Metadata Caching

```kotlin
class PhotoDetailScreen @Inject constructor(
    private val metadataRepository: MediaMetadataRepository
) {
    fun prefetchCellMetadata(cell: HexCellWithMedia) {
        // Prefetch essential metadata for all photos in cell
        metadataRepository.prefetchCellMetadata(
            cell = cell,
            loadLevel = MetadataLoadLevel.ESSENTIAL
        )
    }
    
    suspend fun loadAdvancedMetadata(media: Media) {
        // Load full metadata for focused photo
        metadataRepository.preloadHighPriorityMetadata(
            media = media,
            loadLevel = MetadataLoadLevel.ADVANCED
        )
    }
}
```

### Configuration Management

```kotlin
class SettingsViewModel @Inject constructor(
    private val selectionStateManager: SelectionStateManager
) {
    fun updateOverlaySettings(
        autoModeEnabled: Boolean,
        showThreshold: Float,
        hideThreshold: Float
    ) {
        val config = PhotoSelectionConfig(
            autoModeEnabled = autoModeEnabled,
            autoShowThreshold = showThreshold,
            autoHideThreshold = hideThreshold
        )
        
        selectionStateManager.updateConfig(config)
    }
}
```

### Error Handling

```kotlin
class MetadataErrorHandler {
    fun handleMetadataState(state: MetadataLoadingState) {
        when (state) {
            is MetadataLoadingState.NotLoaded -> {
                // Show loading placeholder
            }
            is MetadataLoadingState.Loading -> {
                // Show loading indicator
            }
            is MetadataLoadingState.Loaded -> {
                // Display metadata
                displayMetadata(state.metadata)
            }
            is MetadataLoadingState.Error -> {
                // Handle error gracefully
                showErrorMessage("Failed to load photo details")
            }
        }
    }
}
```

## Performance Guidelines

### Memory Management
- Use `clearMemoryCache()` during memory pressure
- Monitor cache statistics with `getCacheStats()`
- Prefer essential metadata loading for list views

### Loading Optimization
- Use `prefetchCellMetadata()` for visible cells
- Load advanced metadata only when overlay is active
- Cancel unnecessary jobs with `cancelAllJobs()`

### State Management
- Subscribe to state flows for reactive updates
- Use events for all state changes
- Avoid direct state mutation

## Testing Utilities

### Mock Data Creation

```kotlin
fun createMockMetadata(): MediaMetadata {
    return MediaMetadata(
        essential = MediaMetadata.EssentialMetadata(
            cameraMake = "Apple",
            cameraModel = "iPhone 15 Pro",
            dateTime = "Dec 15, 2023 at 14:30",
            location = SerializableLocation(37.7749, -122.4194),
            orientation = 1,
            fileSizeFormatted = "2.3 MB"
        ),
        technical = MediaMetadata.TechnicalMetadata(
            aperture = "f/1.8",
            iso = "64",
            focalLength = "24mm",
            exposureTime = "1/120s",
            whiteBalance = "Auto",
            flash = "No Flash"
        )
    )
}
```

### Test State Creation

```kotlin
fun createTestSelectionState(): PhotoSelectionState {
    return PhotoSelectionState(
        selectedMedia = createMockMedia(),
        selectionMode = SelectionMode.MANUAL_SELECTION,
        overlayVisible = true,
        currentCell = createMockHexCell(),
        cellNavigationIndex = 0
    )
}
```

This API documentation provides comprehensive coverage of all public interfaces and usage patterns for the Photo Selection Overlay system.