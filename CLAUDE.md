# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LuminaGallery is a modern Android Compose application focused on advanced image gallery functionality with sophisticated touch gesture handling. The app uses a single-activity architecture with Jetpack Compose and implements matrix-based transformations for smooth pan, zoom, and scale operations. It features a unique hexagonal grid visualization system for displaying grouped media on a zoomable, pannable canvas.

**Design Philosophy**: The photo layout simulates the natural behavior of physical photos being scattered onto a table surface. Photos are positioned using organic shape patterns within hexagonal cells with realistic rotation angles, creating a controlled chaos that mimics turning a bag of printed photos upside down. This design balances randomness with organization through cell-based grouping, while ensuring all photos maintain partial visibility through "breathing room" constraints.

**Future Visualization Ideas**: Alternative physical metaphors to explore include corkboard/pin board with photos pinned at slight angles, polaroid stack effects with realistic shadows, magnetic fridge with rounded corners and curling effects, or artist's table with mixed media scattered organically.

## Key Technologies

- **Jetpack Compose** with Material 3 design system
- **Kotlin** 2.1.21 with Compose Compiler Plugin
- **Hilt** for dependency injection
- **Single Activity Architecture** using ComponentActivity
- **Matrix-based transformations** for gesture handling
- **Custom Canvas drawing** with performance optimizations
- **Clean Architecture** with separate domain, data, and UI layers

## Build Configuration

- **Target SDK:** 36 (Android 16)
- **Min SDK:** 29 (Android 10)
- **Java Version:** 11
- **Namespace:** `dev.serhiiyaremych.lumina`

# File Ops System Prompt (Kotlin/Android, Bash-first)

You work on Kotlin/Android repos. Conserve tokens when reading/modifying files.

## Always check size first
```bash
wc -l <file>

Policy:
<100 lines: safe to read fully
100–699 lines: read targeted slices only
≥700 lines: NEVER read fully. Use structure/search + partial reads

## Repository & structure overview (no big reads)
# Fast counts & hotspots
tokei .                         # LOC per language/dirs
scc --by-file --no-complexity . # LOC per file (find big Kotlin files)

# Kotlin-only file lists
```bash
fd -e kt -e kts                 # list Kotlin sources
find . -name "*.kt" -o -name "*.kts" | head -50  # fallback if no fd

# ast-grep: structural search (CORRECTED SYNTAX)
ast-grep -p 'class $NAME { $$$ }' -l kotlin <dir>
ast-grep -p 'fun $NAME($$$)' -l kotlin <dir>
ast-grep -p 'suspend fun $NAME($$$)' -l kotlin <dir>
ast-grep -p '@Composable fun $NAME($$$)' -l kotlin <dir>
ast-grep -p 'data class $NAME($$$)' -l kotlin <dir>
ast-grep -p 'sealed class $NAME' -l kotlin <dir>

# Get stats without dumping matches
ast-grep -p 'fun $NAME($$$)' -l kotlin <dir> --stats

# File tree (Kotlin only) if needed
tree -I 'build|.git|.gradle|.idea' -P '*.kt' -P '*.kts' -L 3
```

## Targeted searching (before reading)
# Ripgrep: Kotlin-only, with line numbers and context
```bash
rg -n -t kotlin 'pattern' <dir>
rg -n -t kotlin -C5 'class\s+\w+ViewModel' <dir>
rg -n -t kotlin -g '!**/build/**' -g '!**/generated/**' 'suspend fun'

# List files only (no content)
rg -l -t kotlin 'ViewModel' <dir>

# Jump points inside a file
rg -n '^package ' <file.kt>
rg -n '^import ' <file.kt> | head -20
rg -n '^(class|interface|object|enum) ' <file.kt>
rg -n '^\s*(fun|val|var) ' <file.kt>

# Android specific patterns
rg -n '@(Inject|Provides|Module|Component)' <file.kt>  # Dagger/Hilt
rg -n 'findViewById|viewBinding|dataBinding' <file.kt>  # View binding
```

## Partial reading (slices only)
```bash
# With bat (if available - shows syntax highlighting)
bat -n -r 1:80 <file.kt>              # head slice
bat -n -r 400:460 <file.kt>           # specific window

# POSIX fallbacks
head -n 80 <file.kt>
tail -n 120 <file.kt>
sed -n '100,200p' <file.kt>
awk 'NR>=145 && NR<=165' <file.kt>

# Read around line N with context
awk -v n=150 'NR >= n-20 && NR <= n+20' <file.kt>
sed -n "$((N-20)),$((N+20))p" <file.kt>  # if N is set

# Read specific function/class (between patterns)
sed -n '/^class UserViewModel/,/^class\|^interface\|^object\|^enum\|^$/p' <file.kt>
sed -n '/fun onCreate/,/^    }/p' <file.kt>
```

## Edit with minimal churn
```bash
# Single-line or range substitutions
sed -i '42s/.*/val limit = 100/' <file.kt>
sed -i '100,140s/MutableLiveData/StateFlow/g' <file.kt>

# Append (cheapest)
printf '\n@Deprecated("Use Foo")\nclass Bar\n' >> <file.kt>

# Insert at specific line
sed -i '100i\    private val logger = LoggerFactory.getLogger()' <file.kt>

# Safe patch workflow
cp <file.kt> <file.kt.bak>
# make edits to file.kt
diff -u <file.kt.bak> <file.kt> > changes.patch
# review patch, then apply if needed

# Git-based (if in repo)
git diff --no-index <old.kt> <new.kt>
```

## Android & Gradle specifics
```bash
# Gradle deps block only
sed -n '/dependencies\s*{/,/^}/p' build.gradle.kts
grep "implementation\|api\|kapt\|ksp" build.gradle.kts

# Android manifest
xmllint --xpath "//activity/@android:name" AndroidManifest.xml 2>/dev/null
grep "android:name=" AndroidManifest.xml

# Resources
grep -o '@string/[^"]*' res/layout/*.xml | sort -u
grep "name=" res/values/strings.xml | head -20
```

## Decision flow (MUST follow)
wc -l <file>
1. <100 lines → may read fully (but prefer slices if possible)
2. 100–699 → use rg/ast-grep to locate, then bat/sed exact ranges
3. ≥700 → use ast-grep/rg to pinpoint; only read windows around matches
4. Edits → limit to smallest line ranges; prefer sed/patch

## Common pitfalls to avoid
1. NEVER use cat on files >100 line
2. NEVER use grep -r without file type filters
3. IGNORE build/, .gradle/, generated/ directories
4. CHECK file existence before operations: [[ -f <file> ]] && wc -l <file>
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

# Code formatting with ktlint
./gradlew ktlintCheck          # Check code style
./gradlew ktlintFormat         # Auto-fix formatting issues
ktlint "**/*.kt"              # System-wide ktlint check
ktlint -F "**/*.kt"           # System-wide auto-fix

# Start app on connected device (assumes debug build installed)
adb shell am start -n dev.serhiiyaremych.lumina/.MainActivity

# Install and run in one command
./gradlew -q installDebug && adb shell am start -n dev.serhiiyaremych.lumina/.MainActivity
```

### Debugging with ADB Logcat
```bash
# Debug UI rendering and media drawing
adb logcat -d -s MediaDrawing:D

# Combine multiple tags for comprehensive debugging
adb logcat -d -s StreamingAtlasManager:D -s MediaDrawing:D -s App:D

# Alternative: Real-time monitoring (use Ctrl+C to stop)
adb logcat -s StreamingAtlasManager:D

# Clear logcat buffer before testing
adb logcat -c

# Note: The -d flag dumps current logcat and exits immediately
# Without -d, logcat runs continuously until Ctrl+C
```

### Codebase Analysis with Gemini CLI
```bash
# Use Gemini CLI for large codebase analysis when Claude's context is insufficient
# Install from: https://github.com/google/gemini-cli#installation

# Check if feature is implemented across codebase
gemini -p "@app/src/ Has touch gesture handling been implemented? Show me the relevant files and functions"

# Analyze specific implementation patterns
gemini -p "@app/src/main/java/dev/serhiiyaremych/lumina/domain/ @app/src/main/java/dev/serhiiyaremych/lumina/ui/ How is the atlas system integrated with the UI rendering?"

# Verify architecture patterns
gemini -p "@app/src/ Is clean architecture properly implemented? Show repository, use case, and UI layer separation"

# Find usage of specific components
gemini -p "@app/src/ Where is the HexGridGenerator used? List all files and functions that reference it"

# Check for performance optimizations
gemini -p "@app/src/main/java/dev/serhiiyaremych/lumina/ui/ Are there any performance optimizations for canvas drawing? Show the implementation details"

# Security and permissions analysis
gemini -p "@app/src/ How are media permissions handled? Show the complete permission flow implementation"
```

### Advanced Code Quality Analysis
```bash
# Find deprecated APIs and code issues with Kotlin compiler warnings
./gradlew clean compileDebugKotlin 2>&1 | grep "^w:" | head -20

# Count total compiler warnings
./gradlew clean compileDebugKotlin 2>&1 | grep "^w:" | wc -l

# Run comprehensive lint analysis with detailed reports
./gradlew lintDebug --stacktrace

# Auto-fix safe lint issues
./gradlew lintFix

# Check for dependency issues and conflicts
./gradlew analyzeDebugDependencies

# Find duplicate classes in classpath
./gradlew checkDebugDuplicateClasses

# View detailed lint reports (generated after lintDebug)
cat app/build/reports/lint-results-debug.txt
open app/build/reports/lint-results-debug.html

# Enable all Gradle warnings during build
./gradlew --info assembleDebug 2>&1 | grep -E "(warning|deprecated|unused)"
```

**Important:** When referencing files in code or documentation, always use the full path from the project root. The data classes are in `data/`, not `data/texture/` or similar subdirectories.

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
6. **StreamingGalleryViewModel** - Manages media state and grouping logic using StateFlow

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

### Functional Programming Guidelines
- **Prefer pure functions for decision logic**: Extract complex conditionals into pure function objects (e.g., AtlasStrategySelector, AtlasRegenerationDecider)
- **Use Result types for explicit error handling**: Replace nullable returns with sealed Result classes (Success/Failed variants)
- **Extract strategy patterns into composer objects**: Use functional composition over complex nested conditionals
- **Test pure functions in isolation**: Pure functions enable easier unit testing without mocking side effects
- **Maintain behavioral compatibility when refactoring**: Preserve existing functionality while improving code structure
- **Separate pure logic from side effects**: Keep decision logic separate from logging, state updates, and I/O operations

### Compose & UI Documentation
- **compose-side-effects.md**: Documentation for Compose side-effects patterns
- **compose-side-effects-samples.md**: Sample code for Compose side-effects usage
- **compose-animation-doc.md**: Guide for Compose animations
- **compose-animations-samples.md**: Sample code for Compose animations
- **compose-ui-graphics-layer.md**: Complete GraphicsLayer API documentation for offscreen rendering and layered drawing
- **compose-ui-graphics-layer-samples.md**: GraphicsLayer code samples demonstrating layered drawing, blend modes, and visual effects

Think hard to understand the true nature of a problem, not just to find a solution. Present at least three distinct solutions with their trade-offs before you act.

### Code Style Notes

- Uses wildcard imports for cleaner code organization
- Follows Compose naming conventions per docs/kotlin-coding-conventions.md
- Constants are defined at top-level (MIN_ZOOM, MAX_ZOOM)
- State management follows Compose best practices with remember and mutableStateOf
- Adheres to modern Android patterns outlined in docs/modern-android.md
- Troubleshooting problems following docs/five.md
- When documenting code read docs/create-docs.md

### Legacy Code Policy

**IMPORTANT**: This codebase is in active development and should not maintain deprecated/legacy code paths.

- **Remove legacy code immediately** when identified - do not keep deprecated functions, classes, or code paths
- **No @Deprecated annotations** - delete the code entirely rather than marking as deprecated
- **Clean up unused imports** after removing legacy code
- **Single implementation principle** - maintain only one implementation of each feature (the current/modern one)
- **Active development mindset** - assume users want the latest, most optimized implementation

### Development Workflow

**MANDATORY**: After implementing any feature or making code changes, always:
1. **Build the project first** to verify it compiles: `./gradlew -q assembleDebug`
2. **If build fails**: **STOP and ASK THE USER** if they want you to fix the build errors before proceeding
3. **If build succeeds**: Format code to ensure consistent style: `./gradlew ktlintFormat`
4. **Check code style** for any remaining issues: `./gradlew ktlintCheck`
5. **Check for lint issues** using: `./gradlew -q lint`
6. **If ktlint or lint issues are found**: **ASK THE USER** if they want you to fix the issues before proceeding
7. **Ask the user** if they want to launch the application to test the changes: `./gradlew -q installDebug && adb shell am start -n dev.serhiiyaremych.lumina/.MainActivity`

**CRITICAL**: Never proceed if build fails. Build success is mandatory before any formatting or style checks. This ensures syntax correctness first, then code standards, and gives the user control over issue resolution.

### Code Formatting Standards

**ktlint Configuration**: This project uses ktlint v1.7.1 with comprehensive Android/Compose rules configured via:
- **`.editorconfig`**: Main configuration with 40+ rules for consistent formatting
- **Gradle integration**: System-wide ktlint applied to all modules
- **Compose support**: PascalCase allowed for `@Composable` functions
- **Android Studio style**: Uses `android_studio` code style by default

**Quick ktlint Commands**:
```bash
# Check all Kotlin files
ktlint

# Check specific paths
ktlint "app/src/main/java/"
ktlint "**/*.kt"

# Auto-fix formatting
ktlint -F "**/*.kt"

# Gradle integration
./gradlew ktlintCheck          # Check all modules
./gradlew ktlintFormat         # Auto-fix all modules
./gradlew :app:ktlintCheck     # Check specific module
```

**IDE Integration**:
- **Android Studio**: ktlint plugin available in marketplace
- **VS Code**: ktlint extension with auto-format on save
- **Configuration**: Uses `.editorconfig` for consistent rules across editors

**Key Rules Enabled**:
- **180 character line length** (generous for modern wide screens)
- **PascalCase for @Composable functions** via `ktlint_function_naming_ignore_when_annotated_with = Composable`
- **Trailing commas** for cleaner git diffs
- **Wildcard imports allowed** (disabled `no-wildcard-imports` for cleaner code organization)
- **Property naming relaxed** (disabled for Compose patterns like `object Colors`)
- **Import ordering disabled** (flexible import organization)
- **Android Studio code style** compliance with experimental rules enabled

**Common ktlint Issue - Parameter Comments**:
- **NEVER place comments on the same line after function parameters**
- ❌ `val param: String = "value" // This is wrong`
- ✅ `// This is correct`<br>&nbsp;&nbsp;&nbsp;&nbsp;`val param: String = "value"`
- This is the **most frequent ktlint violation** in the project
- Always move inline parameter comments to the line above

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
