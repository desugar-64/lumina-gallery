# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LuminaGallery is a modern Android Compose application focused on advanced image gallery functionality with sophisticated touch gesture handling. The app uses a single-activity architecture with Jetpack Compose and implements matrix-based transformations for smooth pan, zoom, and scale operations. It features a unique hexagonal grid visualization system for displaying grouped media on a zoomable, pannable canvas.

## Key Technologies

- **Jetpack Compose** with Material 3 design system
- **Kotlin** 2.1.21 with Compose Compiler Plugin
- **Hilt** for dependency injection
- **Single Activity Architecture** using ComponentActivity
- **Matrix-based transformations** for gesture handling
- **Custom Canvas drawing** with performance optimizations
- **Clean Architecture** with separate domain, data, and UI layers

## Build Configuration

- **Target SDK:** 35 (Android 15)
- **Min SDK:** 29 (Android 10)
- **Java Version:** 11
- **Namespace:** `dev.serhiiyaremych.lumina`

## Essential Commands

### Building and Running
```bash
# Build debug APK (quiet output)
./gradlew -q assembleDebug

# Build and install debug on device (quiet output)
./gradlew -q installDebug

# Build release APK (quiet output)
./gradlew -q assembleRelease

# Clean build (quiet output)
./gradlew -q clean

# If build fails, re-run with stacktrace for debugging:
# ./gradlew assembleDebug --stacktrace
```

### Testing
```bash
# Run unit tests (quiet output)
./gradlew -q test

# Run instrumented tests on connected device (quiet output)
./gradlew -q connectedAndroidTest

# Run UI tests (quiet output)
./gradlew -q app:connectedDebugAndroidTest

# If tests fail, re-run with stacktrace for debugging:
# ./gradlew test --stacktrace
```

### Atlas Performance Benchmarking
```bash
# Initialize fresh baseline measurement
./gradlew :benchmark:initAtlasBaseline

# Track optimization improvements
./gradlew :benchmark:benchmarkAtlasOptimization -Poptimization.name="your_optimization_name"

# View performance metrics in CLI table
./gradlew :benchmark:showAtlasMetrics
./gradlew :benchmark:listAtlasTimeline

# Baseline management
./gradlew :benchmark:updateAtlasBaseline -Pbaseline.name="baseline_v2"
./gradlew :benchmark:cleanAtlasTimeline -Pforce

# Experimental development (allows uncommitted changes)
./gradlew :benchmark:benchmarkAtlasOptimization -Poptimization.name="experimental" -Pallow.dirty
./gradlew :benchmark:cleanAtlasExperimental
```

### Development
```bash
# Run lint checks
./gradlew -q lint

# Start app on connected device (assumes debug build installed)
adb shell am start -n dev.serhiiyaremych.lumina/.MainActivity

# Install and run in one command
./gradlew -q installDebug && adb shell am start -n dev.serhiiyaremych.lumina/.MainActivity
```

## Project Structure

### Key File Locations

**Main Application:**
- `app/src/main/java/dev/serhiiyaremych/lumina/MainActivity.kt` - Single activity entry point
- `app/src/main/java/dev/serhiiyaremych/lumina/LuminaApplication.kt` - Application class with Hilt
- `app/src/main/java/dev/serhiiyaremych/lumina/di/AppModule.kt` - Hilt dependency injection

**Data Classes (`data/`):**
- `PhotoScaler.kt` - Hardware-accelerated bitmap scaling
- `TexturePacker.kt` - Shelf packing algorithm (implements `ShelfTexturePacker` class)
- `datasource/MediaStoreDataSource.kt` - Real media data from Android MediaStore
- `datasource/FakeMediaDataSource.kt` - Test data source
- `repository/MediaRepositoryImpl.kt` - Repository implementation

**Domain Models (`domain/model/`):**
- `Media.kt` - Core media entity (Image, Video)
- `TextureAtlas.kt` - Atlas container with bitmap and regions
- `AtlasRegion.kt` - Individual photo region within atlas
- `LODLevel.kt` - 6-level LOD system (LEVEL_0 to LEVEL_5)
- `HexGrid.kt`, `HexGridGenerator.kt` - Hexagonal grid system
- `HexCellWithMedia.kt` - Hex cell containing grouped media

**Use Cases (`domain/usecase/`):**
- `EnhancedAtlasGenerator.kt` - **Primary atlas generator** (current system)
- `DynamicAtlasPool.kt` - Multi-atlas management with 2K/4K/8K support
- `AtlasManager.kt` - High-level atlas lifecycle coordinator
- `PhotoLODProcessor.kt` - Photo processing for LOD levels
- `SmartMemoryManager.kt` - Memory pressure monitoring
- `DeviceCapabilities.kt` - Device performance detection
- `AtlasGenerator.kt` - Legacy single-atlas generator (**deprecated**)
- `GetMediaUseCase.kt`, `GroupMediaUseCase.kt` - Media business logic
- `GenerateHexGridUseCase.kt`, `GenerateHexGridLayoutUseCase.kt` - Grid generation

**UI Components (`ui/`):**
- `App.kt` - Main application composable
- `MediaHexVisualization.kt` - **Primary renderer** for media on hex grid
- `TransformableContent.kt` - Gesture handling and matrix transformations
- `GridCanvas.kt` - Custom canvas with optimized drawing
- `HexGridRenderer.kt`, `GridRenderer.kt` - Grid rendering utilities
- `GeometryReader.kt` - Hit testing and coordinate mapping
- `gallery/GalleryViewModel.kt` - Main screen state management
- `components/MediaPermissionManager.kt` - Permission system
- `debug/AtlasDebugOverlay.kt` - Atlas visualization for debugging

**Benchmarking:**
- `benchmark/src/main/java/dev/serhiiyaremych/lumina/benchmark/AtlasPerformanceBenchmark.kt` - Performance testing
- `common/src/main/java/dev/serhiiyaremych/lumina/common/BenchmarkLabels.kt` - Tracing labels

**Important:** When referencing files in code or documentation, always use the full path from the project root. The data classes are in `data/`, not `data/texture/` or similar subdirectories.

## Architecture Details

### Clean Architecture Layers

The app follows Clean Architecture principles with clear separation of concerns:

1. **Domain Layer** (`app/src/main/java/dev/serhiiyaremych/lumina/domain/`):
   - `model/`: Core entities (Media, HexGrid, HexGridGenerator, TextureAtlas, AtlasRegion, LODLevel, HexCellWithMedia)
   - `repository/`: Abstract repository interfaces (MediaRepository)
   - `usecase/`: Business logic and atlas system components:
     - **Core Use Cases**: GetMediaUseCase, GroupMediaUseCase, GenerateHexGridUseCase, GenerateHexGridLayoutUseCase, GetHexGridParametersUseCase
     - **Atlas System**: EnhancedAtlasGenerator, DynamicAtlasPool, AtlasManager, PhotoLODProcessor, SmartMemoryManager, DeviceCapabilities
     - **Legacy**: AtlasGenerator (deprecated), EnhancedAtlasAdapter (compatibility)

2. **Data Layer** (`app/src/main/java/dev/serhiiyaremych/lumina/data/`):
   - `datasource/`: Data source implementations (FakeMediaDataSource, MediaStoreDataSource)
   - `repository/`: Repository implementations (MediaRepositoryImpl)
   - **Atlas Support**: PhotoScaler (hardware-accelerated scaling), TexturePacker (shelf packing algorithm)

3. **UI Layer** (`app/src/main/java/dev/serhiiyaremych/lumina/ui/`):
   - **Root Composables**: App.kt (main application), MainActivity.kt
   - **Canvas System**: TransformableContent (gesture handling), GridCanvas (optimized drawing), GridRenderer, HexGridRenderer
   - **Visualization**: MediaHexVisualization (primary renderer), GeometryReader (hit testing), CoordinateTransformUtils
   - **Components**: MediaPermissionManager, MediaPermissionFlow, PermissionSelectionBottomSheet (permission system)
   - **Features**: gallery/GalleryViewModel (state management)
   - **Debug**: debug/AtlasDebugOverlay (atlas visualization)
   - **Theme**: theme/ (Color, Theme, Type)

### Dependency Injection with Hilt

- **AppModule**: Provides core dependencies (MediaRepository, UseCases)
- **@HiltViewModel**: Used for ViewModels with automatic dependency injection
- **@Singleton**: Applied to repository and use case providers for single instances

### Core Components

1. **MainActivity.kt** - Single activity with edge-to-edge display
2. **App.kt** - Root composable containing TransformableContent and GridCanvas
3. **TransformableContent** - Handles gesture detection and matrix transformations
4. **GridCanvas** - Custom canvas drawing with optimized visible range calculations
5. **MediaHexVisualization** - Renders media groups on hexagonal grid layout
6. **GalleryViewModel** - Manages media state and grouping logic using StateFlow

### Ultra Atlas System Components

The app implements a sophisticated multi-atlas texture system for efficient photo rendering with 6-level LOD support:

**Core Atlas Components:**
7. **PhotoScaler** (`data/PhotoScaler.kt`) - Hardware-accelerated bitmap scaling using bilinear filtering
8. **PhotoLODProcessor** (`domain/usecase/PhotoLODProcessor.kt`) - Processes photos for Level-of-Detail (LOD) atlas system
9. **TexturePacker** (`data/TexturePacker.kt`) - Shelf packing algorithm for efficient atlas generation (renamed from ShelfTexturePacker)
10. **TextureAtlas, AtlasRegion, LODLevel** (`domain/model/`) - Core data models for atlas system

**Multi-Atlas System:**
11. **EnhancedAtlasGenerator** (`domain/usecase/EnhancedAtlasGenerator.kt`) - Primary atlas generation coordinator using multi-atlas system
12. **DynamicAtlasPool** (`domain/usecase/DynamicAtlasPool.kt`) - Manages multiple atlas sizes (2K/4K/8K) based on device capabilities
13. **AtlasManager** (`domain/usecase/AtlasManager.kt`) - High-level atlas lifecycle management with LOD transitions
14. **SmartMemoryManager** (`domain/usecase/SmartMemoryManager.kt`) - Memory pressure monitoring and emergency cleanup
15. **DeviceCapabilities** (`domain/usecase/DeviceCapabilities.kt`) - Device-aware atlas size selection and performance tier detection

**Legacy Components (Deprecated):**
16. **AtlasGenerator** (`domain/usecase/AtlasGenerator.kt`) - Original single-atlas generator (deprecated, replaced by EnhancedAtlasGenerator)
17. **EnhancedAtlasAdapter** (`domain/usecase/EnhancedAtlasAdapter.kt`) - Compatibility adapter for migration (may be deprecated soon)

### Performance Optimizations & Instrumentation

The app implements several performance optimizations with comprehensive instrumentation:

- **Cached FloatArray** for matrix operations to avoid allocations
- **State-driven recomposition** using offset/zoom state variables  
- **Visible range calculations** in grid drawing to only render visible elements
- **Matrix clamping** with MIN_ZOOM (0.1f) and MAX_ZOOM (10f) constants
- **Hardware-accelerated scaling** using Android's bilinear filtering for photo processing
- **Memory-efficient bitmap handling** with explicit recycling and cleanup patterns
- **Multi-stage atlas pipeline** with comprehensive failure tracking and partial success handling
- **Index-based coordinate mapping** for reliable photo-to-atlas region tracking

### Comprehensive Performance Instrumentation

The app features a sophisticated benchmarking system with detailed performance tracking:

**I/O Separation Tracking:**
- **Disk I/O Operations**: `PhotoLODProcessor.diskOpenInputStream` (ContentResolver file access)
- **Memory I/O Operations**: `PhotoLODProcessor.memoryDecodeBounds`, `memoryDecodeBitmap`, `memorySampleSizeCalc`
- Clear separation between file system access and in-memory bitmap processing

**Software Canvas Instrumentation:**
- **AtlasGenerator.softwareCanvas**: Tracks actual Canvas.drawBitmap() operations where individual photos are composited onto atlas texture
- Hardware-accelerated bilinear filtering operations with Paint optimization
- Bitmap-to-bitmap drawing performance with destination rectangle mapping

**Comprehensive Metrics (25+ tracked operations):**
- **Primary Targets**: Bitmap scaling, software canvas rendering (300ms aggressive target)
- **Hardware Operations**: PhotoScaler bilinear filtering, createScaledBitmap acceleration
- **Memory Management**: Bitmap allocation/recycling, atlas cleanup, processed photo cleanup
- **Algorithm Performance**: Texture packing shelf algorithm, image sorting, shelf fitting
- **Atlas Pipeline**: Photo processing, texture packing, atlas bitmap creation

**Benchmarking System:**
- **Iterative Optimization Tracking**: Track progress within single optimization tasks
- **Professional Table Output**: Color-coded performance improvements with perfect alignment
- **Device Consistency Checking**: Warns when switching hardware mid-optimization
- **Git Integration**: Automatic commit tracking and dirty state detection
- **Simple Gradle Tasks**: Easy-to-use commands for optimization workflow

### Atlas System Optimizations

The app implements advanced LOD (Level-of-Detail) optimizations for atlas texture generation:

**LOD Boundary Detection**:
- **LOD_0**: 0.0f-0.5f zoom (32px thumbnails for overview)
- **LOD_2**: 0.5f-2.0f zoom (128px standard quality)  
- **LOD_4**: 2.0f-10.0f zoom (512px high quality)
- Atlas regeneration **only** occurs when crossing these boundaries (0.5f, 2.0f)
- Prevents frequent regeneration during smooth zoom operations

**Memory Management**:
- Proper bitmap recycling with safety checks to prevent crashes
- Old atlas bitmaps are recycled before replacement during regeneration
- UI rendering includes `isRecycled` checks to avoid drawing recycled bitmaps

**Debug Visualization** (`/ui/debug/AtlasDebugOverlay.kt`):
- **AtlasDebugOverlay**: Shows atlas bitmap, region count, and error states
- **AtlasGenerationStatusOverlay**: Displays current LOD level and generation status
- Real-time feedback during atlas transitions with color-coded states

### Gesture System

The touch gesture system uses:
- `detectTransformGestures` for pan, zoom, and scale
- Matrix transformations with postScale and postTranslate
- Zoom level clamping between 0.1x and 10x
- State updates to trigger recomposition only when needed

### Media Permissions System

The app implements a modern, Android version-aware permissions system:

- **Cross-Version Support**: Handles Android 10-15 with appropriate permission models
- **Android 14+ Limited Access**: Transparent support for "Allow limited access" via `READ_MEDIA_VISUAL_USER_SELECTED`
- **Material3 UX**: Clean permission flow with bottom sheet selection UI
- **Binary State Model**: Simplified "has access" vs "no access" approach
- **System-First Design**: Leverages Android's built-in permission behaviors

**Key Components:**
- `MediaPermissionManager`: Core permission detection and management logic
- `MediaPermissionFlow`: Material3 UI flow with permission rationale and selection
- `PermissionSelectionBottomSheet`: User-friendly permission choice interface

**Permissions Used:**
- `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` (Android 13+)
- `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+ limited access)
- `READ_EXTERNAL_STORAGE` (Android 10-12 legacy)
- `ACCESS_MEDIA_LOCATION` (optional, for photo/video metadata)

## Dependencies

Uses Gradle version catalog (libs.versions.toml) for dependency management. Key dependencies include:
- Compose BOM 2025.06.00
- AndroidX Core KTX, Lifecycle, Activity Compose
- Hilt 2.56.2 for dependency injection
- Material 3 for theming
- JUnit and Espresso for testing

## Development Rules and Guidelines

This project includes comprehensive documentation in `docs/` that should be referenced when working on the codebase:

### Development Guidelines & Best Practices
- **modern-android.md**: Core principles for modern Android development with Jetpack Compose, state management patterns, and architectural guidelines
- **kotlin-coding-conventions.md**: Official Kotlin coding standards including naming, formatting, and code organization rules
- **implement-task.md**: Systematic approach for task implementation with planning, evaluation, and best practices
- **five.md**: Five Whys root cause analysis technique for debugging and problem-solving
- **continuous-improvement.md**: Framework for improving development practices and identifying patterns
- **create-docs.md**: Template and process for creating comprehensive documentation
- **team-documentation.md**: Patterns and Lessons Learned During Project Development
- **gemini.md**: Guide for using Gemini CLI for large codebase analysis

### Technical Documentation
- **atlas-implementation-plan.md**: Implementation plan for the texture atlas system
- **atlas-texture-patterns.md**: Documentation of atlas texture system patterns and Phase 1 completion
- **atlas/atlas-system-design.md**: Detailed system design for the atlas rendering system

### Compose & UI Documentation
- **compose-side-effects.md**: Documentation for Compose side-effects patterns
- **compose-side-effects-samples.md**: Sample code for Compose side-effects usage
- **compose-animation-doc.md**: Guide for Compose animations
- **compose-animations-samples.md**: Sample code for Compose animations

### Android-Specific Documentation
- **media/**: Android media permissions documentation
  - **android14-changes-partial-photo-video-access.md**: Android 14+ changes for photo/video access
  - **permissions-requesting.md**: Permission patterns and requesting strategies
  - **shared-media.md**: Shared storage and media handling documentation

### Performance & Testing Documentation
- **benchmarking.md**: Simple benchmarking guide for iterative optimization tracking
  - Track progress within single optimization tasks with professional table output
  - Color-coded performance improvements with device consistency checking
  - Easy Gradle task commands for optimization workflow
- **macrobenchmark/**: Android Macrobenchmark testing documentation
  - **macrobenchmark-sample.md**: Sample macrobenchmark implementation
  - **macrobenchmark-capture-metrics.md**: Guide for capturing performance metrics
  - **macrobenchmark-control-app.md**: Setup and control app configuration

### Navigation Documentation
- **nav3/**: Navigation 3 documentation and guides
  - **navigation-3-get-started.md**: Getting started with Navigation 3
  - **navigation-3-basics.md**: Basic concepts and patterns
  - **navigatino-3-code-basics.md**: Code examples and implementation basics

Think hard to understand the true nature of a problem, not just to find a solution. Present at least three distinct solutions with their trade-offs before you act.

### Code Style Notes

- Uses wildcard imports for cleaner code organization
- Follows Compose naming conventions per docs/kotlin-coding-conventions.md
- Constants are defined at top-level (MIN_ZOOM, MAX_ZOOM)
- State management follows Compose best practices with remember and mutableStateOf
- Adheres to modern Android patterns outlined in docs/modern-android.md
- Troubleshooting problems following docs/five.md
- When documenting code read docs/create-docs.md

## Benchmarking Workflow

**Simple iterative optimization tracking. See `docs/benchmarking.md` for complete guide.**

### Quick Commands

```bash
# Start optimization tracking
./gradlew trackOptimization -Poptimization.name="bitmap_pooling"

# Continue tracking after code changes  
./gradlew trackOptimization -Poptimization.name="bitmap_pooling"

# View detailed comparison
./gradlew compareOptimization -Poptimization.name="bitmap_pooling"
```

### Features

- **Iterative tracking** within single optimization tasks
- **Perfect table alignment** with color-coded improvements
- **Device consistency warnings** when switching hardware
- **Git integration** with automatic commit tracking
- **Professional output** suitable for performance reports
