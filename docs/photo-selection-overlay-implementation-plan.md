# Photo Selection Overlay Implementation Plan

## 1. **Core Architecture & State Management**

### 1.1 Enhanced Media Model
- **Extend `Media` class** with additional metadata fields:
  - EXIF data (camera model, aperture, ISO, focal length)
  - Location data (GPS coordinates if available)
  - File metadata (creation date, file size in human-readable format)
  - TODO: Video support for later (duration, codec, resolution)

### 1.2 Selection State Management
- **Create `PhotoSelectionState`** to track:
  - Currently selected photo
  - Selection mode (auto/manual)
  - Current cell context for navigation
  - Overlay visibility state
  - Navigation index within current cell

### 1.3 Viewport-Aware Selection Logic
- **Implement `SelectionModeController`** that:
  - Monitors zoom level and photo viewport occupation
  - Auto-enters selection mode when photo fits width/height (>70% viewport)
  - Auto-exits when photo becomes small (<30% viewport)
  - Tracks cell boundary intersection (2/3 off-screen rule)

## 2. **UI Components**

### 2.1 Main Selection Overlay (`PhotoSelectionOverlay.kt`)
- **Bottom panel with gradient background** (semi-transparent)
- **Two-section layout:**
  - Left: Photo metadata (filename, date, size, dimensions)
  - Right: Navigation controls and cell photo thumbnails
- **Responsive design** that adapts to different screen sizes
- **Gesture-friendly** - non-interactive areas allow swipe-through

### 2.2 Metadata Display (`PhotoMetadataPanel.kt`)
- **Primary info:** Filename, creation date, file size
- **Technical details:** Dimensions, camera model, aperture (if available)
- **Location info:** GPS coordinates/location name (if available)
- **Minimalist design** - clean typography, appropriate spacing

### 2.3 Cell Navigation (`CellNavigationPanel.kt`)
- **Horizontal thumbnail strip** showing all photos in current cell
- **Navigation indicators** (current position, total count)
- **Swipe gestures** for quick photo switching
- **Visual current photo highlight**

## 3. **Integration Points**

### 3.1 Existing Systems Integration
- **Hook into `MediaInputHandling.kt`** selection mechanism
- **Extend `MediaStateManagement.kt`** with selection state
- **Integrate with `TransformableState.focusOn()`** for zoom-to-fit
- **Leverage existing animation system** for smooth transitions

### 3.2 Overlay Visibility Logic
- **Automatic show/hide** based on viewport occupation
- **Smooth fade animations** using existing animation framework
- **Respect existing gesture handling** - overlay doesn't block pan/zoom
- **Cell boundary tracking** for auto-hide behavior

## 4. **Enhanced Metadata System**

### 4.1 Metadata Repository (`MediaMetadataRepository.kt`)
- **EXIF data extraction** using Android's ExifInterface
- **Caching layer** for performance optimization
- **Background loading** to avoid UI blocking
- **Fallback handling** for missing metadata

### 4.2 Data Sources Enhancement
- **Extend `MediaStoreDataSource`** to include additional fields
- **Add EXIF reading capabilities** for camera metadata
- **Location data extraction** from GPS tags
- **File system metadata** (creation time, file size)

## 5. **User Experience Enhancements**

### 5.1 Automatic Selection Mode
- **Zoom-based activation:** Photo fills >70% of viewport width/height
- **Manual trigger:** Tap on already-selected photo
- **Smart exit conditions:** Zoom out (<30% viewport) or navigate away

### 5.2 Cell Navigation UX
- **Smooth transitions** between photos in same cell
- **Thumbnail preview** for quick navigation
- **Gesture shortcuts** (swipe left/right on photo)
- **Visual feedback** for current position

### 5.3 Overlay Behavior
- **Non-intrusive design** - light, semi-transparent
- **Contextual visibility** - appears only when relevant
- **Gesture-friendly** - doesn't interfere with existing interactions
- **Responsive layout** - adapts to screen orientations

## 6. **Performance Considerations**

### 6.1 Metadata Loading
- **Lazy loading** of EXIF data when overlay is shown
- **Caching strategy** to avoid repeated file system access
- **Background processing** to maintain UI responsiveness

### 6.2 Overlay Rendering
- **Efficient composition** using existing animation framework
- **Minimal overdraw** with proper layer management
- **Memory-conscious** thumbnail generation for navigation

## 7. **Implementation Phases**

### Phase 1: Core Infrastructure
1. Extend Media model with metadata fields
2. Create PhotoSelectionState management
3. Implement SelectionModeController
4. Basic overlay UI shell

### Phase 2: Metadata System
1. Create MediaMetadataRepository
2. Implement EXIF data extraction
3. Add caching layer
4. Integrate with data sources

### Phase 3: UI Implementation
1. Build PhotoSelectionOverlay component
2. Implement PhotoMetadataPanel
3. Create CellNavigationPanel
4. Add smooth animations

### Phase 4: Integration & Polish
1. Integrate with existing gesture system
2. Implement auto-show/hide logic
3. Add cell boundary tracking
4. Performance optimization and testing

## 8. **Future Enhancements (TODOs)**
- **Video support** - duration, codec info, thumbnail generation
- **Batch operations** - select multiple photos
- **Export options** - share, edit, delete actions
- **Advanced EXIF** - full camera settings, lens information
- **Location services** - reverse geocoding for location names

## 9. **Technical Implementation Details**

### 9.1 Current System Analysis
Based on codebase analysis, the current implementation has:
- **Media selection** handled in `MediaInputHandling.kt` with `onMediaClicked` callbacks
- **Focus system** using `TransformableState.focusOn()` for zoom-to-fit functionality
- **State management** via `MediaStateManagement.kt` with selection tracking
- **Animation system** for reveal/hide animations of media items
- **Hex cell structure** with `HexCellWithMedia` containing grouped photos

### 9.2 Integration Strategy
- **Extend existing selection flow** without breaking current tap/selection behavior
- **Reuse viewport calculation** from `TransformableState` for overlay visibility
- **Leverage existing animation framework** for smooth overlay transitions
- **Hook into `GalleryViewModel`** for state management integration

### 9.3 File Structure
```
ui/
├── overlay/
│   ├── PhotoSelectionOverlay.kt
│   ├── PhotoMetadataPanel.kt
│   ├── CellNavigationPanel.kt
│   └── SelectionModeController.kt
├── selection/
│   ├── PhotoSelectionState.kt
│   └── SelectionStateManager.kt
data/
├── metadata/
│   ├── MediaMetadataRepository.kt
│   ├── ExifDataExtractor.kt
│   └── MetadataCache.kt
domain/
├── model/
│   ├── MediaMetadata.kt (extend existing Media)
│   └── SelectionMode.kt
```

This plan builds upon the existing robust architecture while adding sophisticated selection and metadata capabilities that enhance the user experience without disrupting the core hex grid visualization system.