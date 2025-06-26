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
# Build debug APK
./gradlew assembleDebug

# Build and install debug on device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests on connected device
./gradlew connectedAndroidTest

# Run UI tests
./gradlew app:connectedDebugAndroidTest
```

### Development
```bash
# Run lint checks
./gradlew lint

# Start app on connected device (assumes debug build installed)
adb shell am start -n dev.serhiiyaremych.lumina/.MainActivity
```

## Architecture Details

### Clean Architecture Layers

The app follows Clean Architecture principles with clear separation of concerns:

1. **Domain Layer** (`domain/`):
   - `model/`: Core entities (Media, HexGrid, HexGridGenerator)
   - `repository/`: Abstract repository interfaces (MediaRepository)
   - `usecase/`: Business logic (GetMediaUseCase, GroupMediaUseCase, GenerateHexGridUseCase, GetHexGridParametersUseCase)

2. **Data Layer** (`data/`):
   - `datasource/`: Data source implementations (currently FakeMediaDataSource)
   - `repository/`: Repository implementations (MediaRepositoryImpl)

3. **UI Layer** (`ui/`):
   - Root composables (App.kt)
   - Custom UI components (TransformableContent, GridCanvas, HexGridRenderer, MediaHexVisualization)
   - Permission system (MediaPermissionManager, MediaPermissionFlow)
   - Feature-specific ViewModels (GalleryViewModel)
   - Theme system (theme/)

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

### Performance Optimizations

The app implements several performance optimizations:

- **Cached FloatArray** for matrix operations to avoid allocations
- **State-driven recomposition** using offset/zoom state variables  
- **Visible range calculations** in grid drawing to only render visible elements
- **Matrix clamping** with MIN_ZOOM (0.1f) and MAX_ZOOM (10f) constants

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

This project includes comprehensive development rules in `.aider-desk/rules/` that should be referenced when working on the codebase:

- **modern-android.mdc**: Core principles for modern Android development with Jetpack Compose, state management patterns, and architectural guidelines
- **kotlin-coding-conventions.md**: Official Kotlin coding standards including naming, formatting, and code organization rules
- **implement-task.mdc**: Systematic approach for task implementation with planning, evaluation, and best practices
- **five.mdc**: Five Whys root cause analysis technique for debugging and problem-solving
- **continuous-improvement.mdc**: Framework for improving development practices and identifying patterns
- **create-docs.mdc**: Template and process for creating comprehensive documentation
- **team-documentation.mdc**: Documented patterns and lessons learned from transformable content and media permissions
- **media/**: Android media permissions documentation including shared storage, permission patterns, and Android 14+ changes

### Code Style Notes

- Uses wildcard imports for cleaner code organization
- Follows Compose naming conventions per kotlin-coding-conventions.md
- Constants are defined at top-level (MIN_ZOOM, MAX_ZOOM)
- State management follows Compose best practices with remember and mutableStateOf
- Adheres to modern Android patterns outlined in modern-android.mdc