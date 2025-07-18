
## ️🔄 2025-07-18 Update: Active-Cell Metadata Prefetching

The metadata loading strategy has been refined:

1. `prefetchVisibleCells()` is now **deprecated** and returns immediately.
2. New method `prefetchActiveCell(cell, selectedMediaId, loadLevel)` in `MediaMetadataRepository` preloads metadata **only for the current cell**.
3. ViewModel callback signature changed:

```kotlin
// OLD
fun onVisibleCellsChanged(visibleCells: List<HexCellWithMedia>)

// NEW
fun onActiveCellChanged(currentCell: HexCellWithMedia) {
    metadataRepository.prefetchActiveCell(
        cell = currentCell,
        selectedMediaId = selectionState.selectedMedia?.id,
        loadLevel = MetadataLoadLevel.ESSENTIAL
    )
}
```

This prevents unnecessary EXIF extraction and focuses I/O on the photos the user is actually viewing.

# Photo Selection Overlay Implementation Guide

## Overview

This guide provides step-by-step implementation instructions for integrating the Photo Selection Overlay system into the LuminaGallery application. It covers dependency injection setup, state management integration, and UI component implementation.

## Phase 1: Foundation Setup (✅ Complete)

### 1.1 Media Model Extension

The `Media` class has been extended to include metadata state tracking:

```kotlin
sealed class Media(
    // ... existing properties
    open val metadataState: MetadataLoadingState = MetadataLoadingState.NotLoaded
)
```

### 1.2 Metadata Models

Core metadata models have been implemented:
- `MediaMetadata`: Progressive loading structure
- `MetadataLoadingState`: Loading state tracking
- `SerializableLocation`: Cacheable GPS coordinates

### 1.3 EXIF Management System

Complete EXIF management system with:
- `ExifDataExtractor`: Progressive metadata extraction
- `MetadataCache`: Multi-layer caching
- `MediaMetadataRepository`: Hybrid fetching strategy

### 1.4 Selection State Management

State management system with:
- `PhotoSelectionState`: Comprehensive state tracking
- `SelectionStateManager`: Event-driven state updates
- `PhotoSelectionConfig`: Configurable behavior

## Phase 2: Dependency Injection Integration

### 2.1 Update AppModule

Add the metadata system to your Hilt configuration:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideMediaMetadataRepository(
        exifExtractor: ExifDataExtractor,
        cache: MetadataCache
    ): MediaMetadataRepository {
        return MediaMetadataRepository(exifExtractor, cache)
    }
    
    @Provides
    @Singleton
    fun provideSelectionStateManager(): SelectionStateManager {
        return SelectionStateManager()
    }
}
```

### 2.2 Update Data Source Integration

Modify your `MediaStoreDataSource` to work with the extended Media model:

```kotlin
@Singleton
class MediaStoreDataSource @Inject constructor(
    private val context: Context
) {
    
    fun getMediaItems(): List<Media> {
        // ... existing media loading logic
        
        return mediaList.map { mediaData ->
            Media.Image(
                // ... existing properties
                metadataState = MetadataLoadingState.NotLoaded
            )
        }
    }
}
```

## Phase 3: ViewModel Integration

### 3.1 Update GalleryViewModel

Integrate the selection state manager into your existing ViewModel:

```kotlin
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val groupMediaUseCase: GroupMediaUseCase,
    private val generateHexGridLayoutUseCase: GenerateHexGridLayoutUseCase,
    private val selectionStateManager: SelectionStateManager,
    private val metadataRepository: MediaMetadataRepository
) : ViewModel() {
    
    // Expose selection state
    val selectionState = selectionStateManager.state
    
    // Existing state flows
    private val _mediaItems = MutableStateFlow<List<Media>>(emptyList())
    val mediaItems: StateFlow<List<Media>> = _mediaItems.asStateFlow()
    
    private val _hexGridLayout = MutableStateFlow<HexGridLayout?>(null)
    val hexGridLayout: StateFlow<HexGridLayout?> = _hexGridLayout.asStateFlow()
    
    // Add metadata loading functionality
    fun onMediaSelected(media: Media) {
        val currentLayout = _hexGridLayout.value
        if (currentLayout != null) {
            selectionStateManager.selectPhoto(
                media = media,
                cells = currentLayout.hexCellsWithMedia,
                mode = SelectionMode.MANUAL_SELECTION
            )
            
            // Trigger metadata loading
            viewModelScope.launch {
                metadataRepository.preloadHighPriorityMetadata(
                    media = media,
                    loadLevel = MetadataLoadLevel.ADVANCED
                )
            }
        }
    }
    
    fun onViewportOccupationChanged(occupation: Float) {
        selectionStateManager.handleEvent(
            PhotoSelectionEvent.ViewportOccupationChanged(occupation)
        )
    }
    
    fun onNavigateNext() {
        selectionStateManager.handleEvent(PhotoSelectionEvent.NavigateNext)
    }
    
    fun onNavigatePrevious() {
        selectionStateManager.handleEvent(PhotoSelectionEvent.NavigatePrevious)
    }
    
    fun onOverlayDismissed() {
        selectionStateManager.handleEvent(PhotoSelectionEvent.PhotoDeselected)
    }
    
    // Prefetch metadata for visible cells
    fun onVisibleCellsChanged(visibleCells: List<HexCellWithMedia>) {
        metadataRepository.prefetchVisibleCells(
            visibleCells = visibleCells,
            loadLevel = MetadataLoadLevel.ESSENTIAL
        )
    }
}
```

## Phase 4: UI Component Implementation (Next Phase)

### 4.1 Create SelectionModeController

Create the viewport-aware selection logic:

```kotlin
@Composable
fun SelectionModeController(
    transformableState: TransformableState,
    selectedMedia: Media?,
    hexGridLayout: HexGridLayout?,
    onViewportOccupationChanged: (Float) -> Unit,
    config: PhotoSelectionConfig
) {
    val viewport by remember {
        derivedStateOf {
            transformableState.calculateViewport()
        }
    }
    
    LaunchedEffect(viewport, selectedMedia) {
        if (selectedMedia != null && hexGridLayout != null) {
            val occupation = calculateMediaViewportOccupation(
                media = selectedMedia,
                viewport = viewport,
                hexGridLayout = hexGridLayout
            )
            onViewportOccupationChanged(occupation)
        }
    }
}

private fun calculateMediaViewportOccupation(
    media: Media,
    viewport: Rect,
    hexGridLayout: HexGridLayout
): Float {
    // Find the media's position in the grid
    val mediaWithPosition = hexGridLayout.hexCellsWithMedia
        .flatMap { it.mediaItems }
        .find { it.media.id == media.id }
        ?: return 0f
    
    // Calculate intersection with viewport
    val intersection = mediaWithPosition.absoluteBounds.intersect(viewport)
    val mediaArea = mediaWithPosition.absoluteBounds.width * mediaWithPosition.absoluteBounds.height
    val viewportArea = viewport.width * viewport.height
    
    return if (mediaArea > 0) {
        (intersection.width * intersection.height) / minOf(mediaArea, viewportArea)
    } else {
        0f
    }
}
```

### 4.2 Create PhotoSelectionOverlay

Create the main overlay component:

```kotlin
@Composable
fun PhotoSelectionOverlay(
    state: PhotoSelectionState,
    metadataState: MetadataLoadingState,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateToIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.shouldShowOverlay,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Metadata Panel
                PhotoMetadataPanel(
                    media = state.selectedMedia,
                    metadataState = metadataState,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (state.shouldShowNavigation) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Navigation Panel
                    CellNavigationPanel(
                        cellMediaItems = state.cellMediaItems,
                        currentIndex = state.cellNavigationIndex,
                        onNavigateToIndex = onNavigateToIndex,
                        onNavigateNext = onNavigateNext,
                        onNavigatePrevious = onNavigatePrevious,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
```

### 4.3 Create PhotoMetadataPanel

Create the metadata display component:

```kotlin
@Composable
fun PhotoMetadataPanel(
    media: Media?,
    metadataState: MetadataLoadingState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // File name
        media?.displayName?.let { fileName ->
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        when (metadataState) {
            is MetadataLoadingState.NotLoaded -> {
                Text(
                    text = "Tap to load details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            is MetadataLoadingState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading details...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            is MetadataLoadingState.Loaded -> {
                MetadataContent(
                    metadata = metadataState.metadata,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            is MetadataLoadingState.Error -> {
                Text(
                    text = "Unable to load photo details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MetadataContent(
    metadata: MediaMetadata,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Essential metadata
        metadata.essential.let { essential ->
            if (essential.dateTime != null) {
                MetadataRow(
                    label = "Date",
                    value = essential.dateTime
                )
            }
            
            MetadataRow(
                label = "Size",
                value = essential.fileSizeFormatted
            )
            
            if (essential.cameraMake != null && essential.cameraModel != null) {
                MetadataRow(
                    label = "Camera",
                    value = "${essential.cameraMake} ${essential.cameraModel}"
                )
            }
            
            essential.location?.let { location ->
                MetadataRow(
                    label = "Location",
                    value = "${location.latitude}, ${location.longitude}"
                )
            }
        }
        
        // Technical metadata
        metadata.technical?.let { technical ->
            if (technical.aperture != null || technical.iso != null || technical.focalLength != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Camera Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            technical.aperture?.let { aperture ->
                MetadataRow(label = "Aperture", value = aperture)
            }
            
            technical.iso?.let { iso ->
                MetadataRow(label = "ISO", value = iso)
            }
            
            technical.focalLength?.let { focalLength ->
                MetadataRow(label = "Focal Length", value = focalLength)
            }
            
            technical.exposureTime?.let { exposureTime ->
                MetadataRow(label = "Exposure", value = exposureTime)
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

### 4.4 Create CellNavigationPanel

Create the cell navigation component:

```kotlin
@Composable
fun CellNavigationPanel(
    cellMediaItems: List<MediaWithPosition>,
    currentIndex: Int,
    onNavigateToIndex: (Int) -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Navigation info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentIndex + 1} of ${cellMediaItems.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Row {
                IconButton(
                    onClick = onNavigatePrevious,
                    enabled = currentIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous photo"
                    )
                }
                
                IconButton(
                    onClick = onNavigateNext,
                    enabled = currentIndex < cellMediaItems.size - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next photo"
                    )
                }
            }
        }
        
        // Thumbnail strip
        if (cellMediaItems.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(cellMediaItems) { index, mediaWithPosition ->
                    ThumbnailItem(
                        media = mediaWithPosition.media,
                        isSelected = index == currentIndex,
                        onClick = { onNavigateToIndex(index) },
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    media: Media,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
```

## Phase 5: Main UI Integration

### 5.1 Update App.kt

Integrate the overlay system into your main app composable:

```kotlin
@Composable
fun App(
    modifier: Modifier = Modifier
) {
    val galleryViewModel: GalleryViewModel = hiltViewModel()
    val selectionState by galleryViewModel.selectionState.collectAsState()
    val hexGridLayout by galleryViewModel.hexGridLayout.collectAsState()
    val mediaItems by galleryViewModel.mediaItems.collectAsState()
    
    // Get metadata state for selected media
    val metadataState by remember(selectionState.selectedMedia) {
        if (selectionState.selectedMedia != null) {
            galleryViewModel.getMetadataStateFlow(selectionState.selectedMedia.id.toString())
        } else {
            flowOf(MetadataLoadingState.NotLoaded)
        }
    }.collectAsState()
    
    Box(modifier = modifier.fillMaxSize()) {
        TransformableContent(
            onTransform = { /* existing transform logic */ },
            modifier = Modifier.fillMaxSize()
        ) {
            GridCanvas(
                hexGridLayout = hexGridLayout,
                selectedMedia = selectionState.selectedMedia,
                onMediaClicked = galleryViewModel::onMediaSelected,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Selection mode controller
        if (hexGridLayout != null) {
            SelectionModeController(
                transformableState = rememberTransformableState(),
                selectedMedia = selectionState.selectedMedia,
                hexGridLayout = hexGridLayout,
                onViewportOccupationChanged = galleryViewModel::onViewportOccupationChanged,
                config = PhotoSelectionConfig()
            )
        }
        
        // Photo selection overlay
        PhotoSelectionOverlay(
            state = selectionState,
            metadataState = metadataState,
            onNavigateNext = galleryViewModel::onNavigateNext,
            onNavigatePrevious = galleryViewModel::onNavigatePrevious,
            onNavigateToIndex = { index ->
                galleryViewModel.handleEvent(
                    PhotoSelectionEvent.NavigateToIndex(index)
                )
            },
            onDismiss = galleryViewModel::onOverlayDismissed,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
```

## Phase 6: Testing and Validation

### 6.1 Unit Tests

Create comprehensive unit tests for the system:

```kotlin
@Test
fun `selection state manager handles photo selection correctly`() {
    val stateManager = SelectionStateManager()
    val mockMedia = createMockMedia()
    val mockCell = createMockHexCell()
    
    stateManager.handleEvent(
        PhotoSelectionEvent.PhotoSelected(
            media = mockMedia,
            mediaWithPosition = mockCell.mediaItems.first(),
            cell = mockCell,
            cellIndex = 0,
            mode = SelectionMode.MANUAL_SELECTION
        )
    )
    
    val state = stateManager.getCurrentState()
    
    assert(state.selectedMedia == mockMedia)
    assert(state.selectionMode == SelectionMode.MANUAL_SELECTION)
    assert(state.overlayVisible)
}

@Test
fun `metadata repository caches results correctly`() = runTest {
    val mockCache = mockk<MetadataCache>()
    val mockExtractor = mockk<ExifDataExtractor>()
    val repository = MediaMetadataRepository(mockExtractor, mockCache)
    
    val mockMedia = createMockMedia()
    val mockMetadata = createMockMetadata()
    
    coEvery { mockCache.getMetadata(any()) } returns mockMetadata
    
    val result = repository.getMetadata(mockMedia)
    
    assert(result == mockMetadata)
    coVerify { mockCache.getMetadata(mockMedia.id.toString()) }
}
```

### 6.2 Integration Tests

Test the complete flow from UI interaction to metadata display:

```kotlin
@Test
fun `complete photo selection flow works correctly`() = runTest {
    // Setup
    val viewModel = createTestViewModel()
    val mockMedia = createMockMedia()
    
    // Action
    viewModel.onMediaSelected(mockMedia)
    
    // Verify state changes
    val selectionState = viewModel.selectionState.value
    assert(selectionState.selectedMedia == mockMedia)
    assert(selectionState.overlayVisible)
    
    // Verify metadata loading triggered
    delay(100)
    val metadataState = viewModel.getMetadataState(mockMedia.id.toString())
    assert(metadataState is MetadataLoadingState.Loading)
}
```

## Phase 7: Performance Optimization

### 7.1 Memory Management

Monitor and optimize memory usage:

```kotlin
class PhotoSelectionMemoryManager @Inject constructor(
    private val metadataRepository: MediaMetadataRepository
) {
    
    fun onMemoryPressure() {
        // Clear memory cache during pressure
        metadataRepository.clearMemoryCache()
    }
    
    fun onCellNavigatedAway(cell: HexCellWithMedia) {
        // Optionally evict metadata for cells no longer visible
        viewModelScope.launch {
            cell.mediaItems.forEach { mediaWithPosition ->
                metadataRepository.removeMetadata(mediaWithPosition.media.id.toString())
            }
        }
    }
}
```

### 7.2 Performance Monitoring

Add performance monitoring:

```kotlin
class PhotoSelectionPerformanceMonitor {
    
    fun trackMetadataLoadTime(media: Media, duration: Long) {
        // Track metadata loading performance
        if (duration > 1000) { // > 1 second
            Log.w("PhotoSelection", "Slow metadata load: ${duration}ms for ${media.displayName}")
        }
    }
    
    fun trackCacheHitRate(stats: CacheStats) {
        // Monitor cache performance
        if (stats.memoryHitRate < 0.7f) { // < 70% hit rate
            Log.i("PhotoSelection", "Cache hit rate: ${stats.memoryHitRate}")
        }
    }
}
```

## Troubleshooting Guide

### Common Issues

1. **Metadata not loading**: Check ExifInterface permissions and file access
2. **Memory leaks**: Ensure proper cleanup of coroutines and cache
3. **Performance issues**: Monitor cache hit rates and loading times
4. **State not updating**: Verify StateFlow subscriptions and event handling

### Debug Tools

1. **Cache Statistics**: Use `getCacheStats()` to monitor cache performance
2. **State Inspection**: Log state changes during development
3. **Memory Profiling**: Use Android Studio's memory profiler
4. **Performance Testing**: Measure metadata loading times

This implementation guide provides a complete roadmap for integrating the Photo Selection Overlay system into your existing LuminaGallery application.