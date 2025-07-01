# LuminaGallery Benchmarking System - Complete Guide

> **📋 Document Purpose**: Comprehensive guide for collecting, processing, storing, and visualizing performance benchmark data across different optimization domains in the LuminaGallery Android application.

## Table of Contents

1. [System Overview](#system-overview)
2. [Benchmark Data Flow](#benchmark-data-flow)
3. [Collection Process](#collection-process)
4. [Data Processing & Storage](#data-processing--storage)
5. [Collector Scripts](#collector-scripts)
6. [Timeline Management](#timeline-management)
7. [Extending for New Optimization Areas](#extending-for-new-optimization-areas)
8. [Visualization System](#visualization-system)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

---

## System Overview

### Architecture
The benchmarking system consists of four main components:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Android App   │───▶│  Benchmark Test │───▶│  Data Collector │───▶│  Visualization  │
│                 │    │                 │    │                 │    │                 │
│ • Trace markers │    │ • UIAutomator   │    │ • Python script │    │ • HTML reports  │
│ • State tracking│    │ • Metrics       │    │ • Timeline DB   │    │ • SVG charts   │
│ • Test tags     │    │ • JSON output   │    │ • Git tracking  │    │ • Trend analysis│
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Key Technologies
- **Android Macrobenchmark**: Performance testing framework
- **AndroidX Tracing**: Fine-grained timing instrumentation
- **UIAutomator**: Automated user interaction simulation
- **Python**: Data processing and timeline management
- **JSON**: Structured data storage format
- **HTML/SVG**: Interactive visualization reports

### Supported Optimization Domains
- **Atlas Texture System**: Bitmap processing, canvas rendering, texture packing
- **Future domains**: UI rendering, database operations, network requests (extensible)

---

## Benchmark Data Flow

### 1. Code Instrumentation
Performance-critical code sections are instrumented with trace markers:

```kotlin
import androidx.tracing.trace

// Example: Atlas texture system
suspend fun processPhotoForLOD(uri: Uri, lodLevel: LODLevel): ProcessedPhoto? {
    return trace("PhotoLODProcessor.processPhoto") {
        trace("PhotoLODProcessor.loadBitmap") {
            loadBitmapFromUri(uri)
        }?.let { bitmap ->
            trace("PhotoLODProcessor.scaleBitmap") {
                photoScaler.scale(bitmap, targetSize, scaleStrategy)
            }
        }
    }
}
```

### 2. Test Execution
Benchmark tests trigger realistic user interactions:

```kotlin
@Test
fun atlasGenerationThroughZoomInteractions() = benchmarkRule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(
        TraceSectionMetric("PhotoLODProcessor.scaleBitmap"),
        TraceSectionMetric("AtlasGenerator.softwareCanvas"),
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max)
    )
) {
    // Automated zoom sequence that triggers atlas generation
}
```

### 3. Raw Data Generation
Tests produce JSON files with detailed metrics:

```json
{
  "context": {
    "build": { "model": "SM-A155M", "version": { "sdk": 34 } },
    "cpuCoreCount": 8,
    "memTotalBytes": 5918945280
  },
  "benchmarks": [
    {
      "name": "atlasGenerationThroughZoomInteractions",
      "metrics": {
        "PhotoLODProcessor.scaleBitmapSumMs": { "median": 25.63 },
        "AtlasGenerator.softwareCanvasSumMs": { "median": 3.16 }
      }
    }
  ]
}
```

### 4. Data Processing
Python collector extracts, validates, and stores structured timeline data:

```json
[
  {
    "timestamp": "2025-07-01T13:05:11",
    "optimization": "baseline",
    "profile": "atlas",
    "total_optimization_time": 808.3,
    "git_commit": "a60b62f",
    "device_context": { "device_model": "SM-A155M" },
    "zoom_test": { "profile_metrics": { "PhotoLODProcessor.scaleBitmapSumMs": 25.63 } }
  }
]
```

### 5. Visualization
HTML reports with interactive charts showing performance trends over time.

---

## Collection Process

### Prerequisites
1. **Physical Android device** (emulators have limitations with FrameTimingMetric)
2. **USB debugging enabled**
3. **GPU profiling enabled** in developer options
4. **Git repository** for commit tracking

### Step-by-Step Collection

#### 1. Code Changes & Commit
```bash
# Implement optimization
git add .
git commit -m "Implement bitmap pool optimization"
```

#### 2. Run Benchmark Tests
```bash
# Execute benchmark on connected device
./gradlew :benchmark:connectedAndroidTest
```

**Output location:**
```
benchmark/build/outputs/connected_android_test_additional_output/
└── debugAndroidTest/connected/[device]/
    └── AtlasPerformanceBenchmark_[test_name]-benchmarkData.json
```

#### 3. Collect Results
```bash
# Basic collection
python3 scripts/atlas_benchmark_collector.py \
    benchmark/build/outputs/.../benchmarkData.json \
    "bitmap_pool_optimization"

# With options
python3 scripts/atlas_benchmark_collector.py collect \
    benchmarkData.json \
    "optimization_name" \
    --profile atlas \
    --allow-dirty
```

#### 4. Verify Collection
```bash
# List timeline entries
python3 scripts/atlas_benchmark_collector.py list

# View specific profile
python3 scripts/atlas_benchmark_collector.py --profile atlas list
```

### Collection Timing Considerations

#### When to Collect
- ✅ **After committing code changes** (clean git state)
- ✅ **After implementing complete optimization** (not partial work)
- ✅ **When device is in consistent state** (cooled down, charged)

#### When to Use `--allow-dirty`
- 🧪 **Experimental testing** of uncommitted changes
- 🔄 **A/B testing** different approaches
- 🚀 **Quick validation** before committing

---

## Data Processing & Storage

### Timeline Database Structure

Each optimization profile maintains its own timeline:

```
benchmark_results/
├── atlas_timeline.json          # Atlas texture system optimizations
├── ui_timeline.json             # UI rendering optimizations (future)
├── database_timeline.json       # Database query optimizations (future)
└── atlas_timeline_backup_*.json # Automatic backups
```

### Timeline Entry Schema

```json
{
  "timestamp": "ISO 8601 timestamp",
  "optimization": "human-readable optimization name",
  "profile": "optimization domain (atlas, ui, etc.)",
  "total_optimization_time": "primary metric in milliseconds",
  "git_commit": "commit hash with -dirty flag if uncommitted",
  "benchmark_file": "source JSON filename",
  "device_context": {
    "device_model": "string",
    "device_brand": "string", 
    "android_version": "integer",
    "cpu_cores": "integer",
    "cpu_max_freq_ghz": "float",
    "memory_total_gb": "float",
    "cpu_locked": "boolean",
    "compilation_mode": "string"
  },
  "zoom_test": {
    "found": "boolean",
    "total_runtime_ns": "integer",
    "iterations": "integer",
    "profile_metrics": {
      "MetricName.operationSumMs": "float (median value)"
    },
    "frame_metrics": {
      "frameDurationCpuMs": { "P50": 10.5, "P90": 18.7, "P99": 28.7 }
    },
    "memory_metrics": {
      "memoryGpuMaxKb": "float",
      "memoryHeapSizeMaxKb": "float"
    }
  },
  "pan_test": {
    "found": "boolean",
    "profile_metrics": { "MetricName.operationSumMs": "float" },
    "frame_metrics": { "frameDurationCpuMs": { "P50": 10.5 } }
  }
}
```

### Data Validation & Safety

#### Git State Tracking
- **Clean commits**: `"git_commit": "a60b62f"`
- **Uncommitted changes**: `"git_commit": "a60b62f-dirty"`
- **Reproducibility**: Links performance to exact code state

#### Automatic Backups
```bash
# Created before any timeline modification
benchmark_results/atlas_timeline_backup_20250701_132226.json
```

#### Data Integrity Checks
- JSON syntax validation
- Index range validation for removals
- Profile existence validation
- Device context consistency

---

## Collector Scripts

### Main Collector: `atlas_benchmark_collector.py`

#### Core Functionality
- **Profile-based data extraction**: Supports multiple optimization domains
- **Timeline management**: Append-only data storage with backup protection
- **Git integration**: Automatic commit tracking with dirty state detection
- **Device context capture**: Hardware specifications and test environment
- **Improvement calculation**: Automatic percentage improvements vs baseline

#### Command Structure

```bash
# New subcommand format (recommended)
python3 scripts/atlas_benchmark_collector.py [GLOBAL_OPTIONS] COMMAND [COMMAND_OPTIONS]

# Legacy format (backward compatible)
python3 scripts/atlas_benchmark_collector.py BENCHMARK_FILE OPTIMIZATION_NAME [OPTIONS]
```

#### Global Options
```bash
--results-dir DIRECTORY    # Storage location (default: benchmark_results)
--profile PROFILE_NAME     # Optimization domain (default: atlas)
```

#### Available Commands

##### `collect` - Add New Benchmark Results
```bash
python3 scripts/atlas_benchmark_collector.py collect \
    benchmark_file.json \
    "optimization_name" \
    [--allow-dirty]
```

**Parameters:**
- `benchmark_file.json`: Path to Macrobenchmark JSON output
- `optimization_name`: Human-readable identifier for this optimization
- `--allow-dirty`: Allow collection with uncommitted git changes

**Example:**
```bash
python3 scripts/atlas_benchmark_collector.py collect \
    build/outputs/.../benchmarkData.json \
    "bitmap_pool_optimization"
```

##### `list` - View Timeline Entries
```bash
python3 scripts/atlas_benchmark_collector.py list
```

**Output Format:**
```
📊 Timeline entries (3 total):
================================================================================
 0. 2025-07-01T13:05:11 | baseline             | a60b62f      |  808.3ms | SM-A155M
 1. 2025-07-02T14:22:33 | bitmap_pool          | b71c93a      |  650.2ms | SM-A155M  
 2. 2025-07-03T16:45:12 | hardware_canvas      | c82d15e      |  312.8ms | SM-A155M
================================================================================
```

##### `remove` - Delete Timeline Entries
```bash
python3 scripts/atlas_benchmark_collector.py remove \
    INDEX [INDEX ...] \
    [--no-backup] \
    [--force]
```

**Parameters:**
- `INDEX`: Timeline entry indices to remove (from `list` command)
- `--no-backup`: Skip automatic backup creation
- `--force`: Skip interactive confirmation

**Examples:**
```bash
# Remove entry 2 with confirmation
python3 scripts/atlas_benchmark_collector.py remove 2

# Remove multiple entries without backup
python3 scripts/atlas_benchmark_collector.py remove 1 3 --no-backup --force
```

##### `clean` - Remove Experimental Entries
```bash
python3 scripts/atlas_benchmark_collector.py clean \
    [--no-backup] \
    [--force]
```

**Purpose:** Remove all entries with `-dirty` git commits (experimental/uncommitted changes)

**Example:**
```bash
# Clean experimental entries with confirmation
python3 scripts/atlas_benchmark_collector.py clean

# Force clean without prompts (for automation)
python3 scripts/atlas_benchmark_collector.py clean --force
```

### Profile System

#### Current Profiles

##### Atlas Profile (`--profile atlas`)
**Metrics tracked:**
- `PhotoLODProcessor.scaleBitmapSumMs`: Bitmap scaling operations
- `AtlasGenerator.softwareCanvasSumMs`: Software canvas rendering
- `PhotoLODProcessor.loadBitmapSumMs`: Bitmap loading I/O
- `AtlasGenerator.createAtlasBitmapSumMs`: Atlas bitmap creation
- `AtlasManager.updateVisibleCellsSumMs`: Atlas coordination
- `AtlasManager.generateAtlasSumMs`: Atlas generation total
- `AtlasManager.selectLODLevelSumMs`: LOD level selection

**Performance targets:**
- Target time: 1000ms
- Baseline time: 5000ms

**Timeline file:** `atlas_timeline.json`

#### Universal Metrics (All Profiles)

##### Frame Performance
- `frameDurationCpuMs`: CPU frame timing (P50, P90, P99 percentiles)
- `frameOverrunMs`: Frame deadline misses

##### Memory Usage
- `memoryGpuMaxKb`: GPU memory consumption
- `memoryHeapSizeMaxKb`: Java heap usage
- `memoryRssAnonMaxKb`: Anonymous memory
- `memoryRssFileMaxKb`: File-backed memory

---

## Timeline Management

### Safe Operations

#### Viewing Data
```bash
# List entries with indices
python3 scripts/atlas_benchmark_collector.py list

# List specific profile
python3 scripts/atlas_benchmark_collector.py --profile ui list
```

#### Backup System
- **Automatic backups**: Created before any destructive operation
- **Timestamped files**: `atlas_timeline_backup_YYYYMMDD_HHMMSS.json`
- **Manual restore**: Copy backup file over `atlas_timeline.json`

#### Interactive Safety
```bash
# Confirmation prompts for destructive operations
🗑️  Entries to be removed:
   1: experimental_change (a60b62f-dirty)

Remove 1 entries? (y/N): 
```

#### Non-Interactive Mode
```bash
# For automation scripts
python3 scripts/atlas_benchmark_collector.py remove 1 --force
python3 scripts/atlas_benchmark_collector.py clean --force
```

### Data Recovery

#### From Backups
```bash
# List available backups
ls benchmark_results/atlas_timeline_backup_*.json

# Restore from backup
cp benchmark_results/atlas_timeline_backup_20250701_132226.json \
   benchmark_results/atlas_timeline.json
```

#### Manual JSON Editing
```bash
# Direct file editing (advanced users)
vim benchmark_results/atlas_timeline.json
```

**⚠️ Risks of manual editing:**
- JSON syntax errors
- Data corruption
- No automatic backup

---

## Extending for New Optimization Areas

### When to Create New Profiles

#### Indicators for New Profile
- **Different performance domain**: UI rendering vs database queries vs network requests
- **Different metrics**: Completely different trace section names
- **Different targets**: Frame timing (16.67ms) vs query time (100ms) vs atlas generation (1000ms)
- **Separate optimization timeline**: Independent improvement tracking

#### Examples
- **Atlas texture system**: `PhotoLODProcessor.*`, `AtlasGenerator.*` traces
- **UI rendering**: `ComposeRenderer.*`, `ScrollPerformance.*` traces  
- **Database operations**: `DatabaseQuery.*`, `DatabaseCache.*` traces
- **Network requests**: `NetworkAPI.*`, `NetworkConnection.*` traces

### Step-by-Step Extension

#### 1. Add Trace Markers to Code
```kotlin
// New UI optimization domain
import androidx.tracing.trace

@Composable
fun MyScrollableList() {
    trace("ScrollPerformance.lazyColumn") {
        LazyColumn {
            trace("ComposeRenderer.drawCanvas") {
                items(data) { item ->
                    ItemCard(item)
                }
            }
        }
    }
}
```

#### 2. Extend Collector Profile Support

**File:** `scripts/atlas_benchmark_collector.py`

**Location:** `_load_profile()` method

```python
def _load_profile(self, profile_name: str):
    """Load benchmark profile configuration"""
    if profile_name == "atlas":
        # Existing atlas configuration
        self.metrics = { ... }
        self.target_time_ms = 1000.0
        self.baseline_time_ms = 5000.0
    
    elif profile_name == "ui":  # NEW PROFILE
        # UI rendering optimization profile
        self.metrics = {
            "ComposeRenderer.drawCanvasSumMs": "Canvas drawing operations",
            "ComposeRenderer.recompositionSumMs": "Recomposition overhead", 
            "ScrollPerformance.lazyColumnSumMs": "LazyColumn scrolling",
            "AnimationEngine.interpolateValuesSumMs": "Animation interpolation"
        }
        self.target_time_ms = 16.67   # 60 FPS target
        self.baseline_time_ms = 33.0  # 30 FPS baseline
    
    else:
        # Update available profiles list
        available_profiles = ["atlas", "ui"]  # ADD NEW PROFILE HERE
        raise ValueError(f"Unknown profile '{profile_name}'. Available: {available_profiles}")
```

#### 3. Create Benchmark Tests

**File:** `benchmark/src/main/java/.../UIPerformanceBenchmark.kt`

```kotlin
@Test
fun scrollPerformanceThroughGestures() = benchmarkRule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(
        TraceSectionMetric("ScrollPerformance.lazyColumn"),
        TraceSectionMetric("ComposeRenderer.drawCanvas"),
        FrameTimingMetric()
    )
) {
    // Automated scroll gestures that trigger UI rendering
}
```

#### 4. Start Using New Profile

```bash
# Establish UI optimization baseline
python3 scripts/atlas_benchmark_collector.py --profile ui collect \
    ui_benchmark_results.json \
    "ui_baseline"

# Track UI optimizations
python3 scripts/atlas_benchmark_collector.py --profile ui collect \
    ui_optimized_results.json \
    "scroll_virtualization"

# Manage UI timeline
python3 scripts/atlas_benchmark_collector.py --profile ui list
python3 scripts/atlas_benchmark_collector.py --profile ui clean --force
```

#### 5. File Organization

```
benchmark_results/
├── atlas_timeline.json          # Atlas texture optimizations
├── ui_timeline.json             # UI rendering optimizations
├── database_timeline.json       # Database query optimizations  
├── network_timeline.json        # Network request optimizations
└── *_timeline_backup_*.json     # Automatic backups for all profiles
```

### Profile Configuration Guidelines

#### Metric Naming Convention
```
[Component].[Operation]SumMs
```

**Examples:**
- `PhotoLODProcessor.scaleBitmapSumMs`
- `ComposeRenderer.drawCanvasSumMs`
- `DatabaseQuery.selectUserDataSumMs`
- `NetworkAPI.authenticateUserSumMs`

#### Performance Targets
```python
# Frame-based targets (UI rendering)
self.target_time_ms = 16.67    # 60 FPS
self.baseline_time_ms = 33.0   # 30 FPS

# Operation-based targets (database)  
self.target_time_ms = 100.0    # 100ms query target
self.baseline_time_ms = 500.0  # 500ms baseline

# Complex operation targets (atlas, network)
self.target_time_ms = 1000.0   # 1 second target
self.baseline_time_ms = 5000.0 # 5 second baseline
```

#### Primary Metric Selection
Choose the most important metric for `total_optimization_time`:

```python
# Atlas: overall generation time
"total_optimization_time": zoom_metrics["profile_metrics"].get("AtlasManager.generateAtlasSumMs", 0)

# UI: frame duration
"total_optimization_time": pan_metrics["frame_metrics"].get("frameDurationCpuMs", {}).get("P90", 0)

# Database: query time  
"total_optimization_time": zoom_metrics["profile_metrics"].get("DatabaseQuery.selectUserDataSumMs", 0)
```

---

## Visualization System

### Current State
**Status:** Chart generator script planned but not yet implemented

**Planned file:** `scripts/atlas_timeline_chart.py`

### Planned Features

#### HTML Report Generation
```bash
# Generate performance report
python3 scripts/atlas_timeline_chart.py --profile atlas

# Output: benchmark_results/atlas_performance_report.html
```

#### Interactive Timeline Charts
```html
<!-- SVG-based charts showing performance over time -->
<div class="timeline-chart">
  <svg>
    <!-- Performance trend line -->
    <!-- Target performance line -->
    <!-- Data points with optimization labels -->
    <!-- Improvement percentages -->
  </svg>
</div>
```

#### Chart Types

##### Primary Timeline Chart
- **X-axis**: Timeline (commit dates)
- **Y-axis**: Total optimization time (ms)
- **Data points**: Each optimization milestone
- **Target line**: Performance goal (1000ms for atlas)
- **Trend analysis**: Improvement trajectory

##### Component Breakdown Chart
- **Stacked bar chart**: Time breakdown by component
- **Before/after comparison**: Optimization impact per component
- **Bottleneck identification**: Largest time contributors

##### Device Performance Matrix
- **Cross-device comparison**: Performance across different devices
- **Android version impact**: Performance by API level
- **Memory correlation**: Performance vs available memory

#### Planned Report Structure

```html
<!DOCTYPE html>
<html>
<head>
    <title>Atlas Performance Report</title>
    <style>/* Interactive chart styles */</style>
</head>
<body>
    <h1>Atlas Texture System Performance Timeline</h1>
    
    <!-- Executive Summary -->
    <div class="summary">
        <h2>Key Metrics</h2>
        <p>Current: 312ms | Target: 1000ms | Improvement: 61% ✅</p>
    </div>
    
    <!-- Main Timeline Chart -->
    <div class="chart-container">
        <h2>Performance Over Time</h2>
        <svg class="timeline-chart"><!-- Interactive SVG --></svg>
    </div>
    
    <!-- Component Breakdown -->
    <div class="breakdown">
        <h2>Optimization Impact by Component</h2>
        <svg class="breakdown-chart"><!-- Stacked bars --></svg>
    </div>
    
    <!-- Detailed Data Table -->
    <div class="data-table">
        <h2>All Benchmark Results</h2>
        <table><!-- Sortable, filterable data --></table>
    </div>
</body>
</html>
```

### Implementation Guidelines for Chart Generator

#### Data Processing Pipeline
```python
class AtlasTimelineChart:
    def __init__(self, timeline_file, profile="atlas"):
        self.timeline = self._load_timeline(timeline_file)
        self.profile = profile
    
    def generate_html_report(self, output_file):
        # Load timeline data
        timeline_data = self._prepare_timeline_data()
        
        # Generate chart components
        main_chart = self._create_timeline_chart(timeline_data)
        breakdown_chart = self._create_breakdown_chart(timeline_data)
        summary_stats = self._calculate_summary_stats(timeline_data)
        
        # Combine into HTML report
        html_content = self._create_html_template(
            main_chart, breakdown_chart, summary_stats
        )
        
        # Write to file
        with open(output_file, 'w') as f:
            f.write(html_content)
```

#### SVG Chart Generation
```python
def _create_timeline_chart(self, data):
    """Generate SVG timeline chart"""
    svg_elements = []
    
    # Chart axes
    svg_elements.append(self._create_axes(data))
    
    # Performance trend line
    svg_elements.append(self._create_trend_line(data))
    
    # Target performance line
    svg_elements.append(self._create_target_line())
    
    # Data points with tooltips
    for point in data:
        svg_elements.append(self._create_data_point(point))
    
    return f"<svg>{' '.join(svg_elements)}</svg>"
```

---

## Best Practices

### Development Workflow

#### Optimization Cycle
```bash
# 1. Establish baseline
git commit -m "Baseline before optimization"
python3 scripts/atlas_benchmark_collector.py collect baseline.json "baseline"

# 2. Implement optimization
# ... code changes ...
git commit -m "Implement bitmap pool optimization"

# 3. Measure improvement
./gradlew :benchmark:connectedAndroidTest
python3 scripts/atlas_benchmark_collector.py collect results.json "bitmap_pool"

# 4. Verify improvement
python3 scripts/atlas_benchmark_collector.py list
```

#### Experimental Testing
```bash
# Test experimental changes without committing
python3 scripts/atlas_benchmark_collector.py collect \
    experimental.json "bitmap_pool_experiment" --allow-dirty

# Clean up experiments after committing final version
python3 scripts/atlas_benchmark_collector.py clean --force
```

#### Multi-Approach Comparison
```bash
# Test approach A
python3 scripts/atlas_benchmark_collector.py collect \
    results_a.json "bitmap_pool_approach_a" --allow-dirty

# Test approach B  
python3 scripts/atlas_benchmark_collector.py collect \
    results_b.json "bitmap_pool_approach_b" --allow-dirty

# Commit best approach and establish clean baseline
git commit -m "Implement bitmap pool optimization (approach B)"
python3 scripts/atlas_benchmark_collector.py collect \
    final.json "bitmap_pool_final"

# Clean up experimental data
python3 scripts/atlas_benchmark_collector.py clean --force
```

### Device Configuration

#### Optimal Test Environment
- **Physical device**: Consistent hardware performance
- **Stable power**: Charged device, avoid thermal throttling
- **Consistent state**: Close other apps, disable notifications
- **Developer options**: GPU profiling enabled for FrameTimingMetric
- **USB debugging**: Enabled for reliable ADB connection

#### Device-Specific Considerations
```bash
# Test on primary target device for main timeline
python3 scripts/atlas_benchmark_collector.py collect results.json "optimization_name"

# Cross-device validation (separate timelines)
python3 scripts/atlas_benchmark_collector.py --results-dir results_pixel collect \
    pixel_results.json "optimization_name"

python3 scripts/atlas_benchmark_collector.py --results-dir results_samsung collect \
    samsung_results.json "optimization_name"
```

### Git Integration

#### Commit Message Conventions
```bash
# Good commit messages for benchmarking
git commit -m "Implement bitmap pool optimization for PhotoLODProcessor"
git commit -m "Add hardware canvas rendering to AtlasGenerator"
git commit -m "Optimize texture packing algorithm in TexturePacker"

# Avoid vague messages
git commit -m "Performance improvements"  # ❌ Not specific
git commit -m "Fix bugs"                 # ❌ No performance context
```

#### Branch Strategy
```bash
# Feature branch for optimization work
git checkout -b atlas-bitmap-pool-optimization

# Establish baseline on feature branch
python3 scripts/atlas_benchmark_collector.py collect baseline.json "baseline"

# Implement and test optimization
python3 scripts/atlas_benchmark_collector.py collect optimized.json "bitmap_pool"

# Merge to main with clean timeline
git checkout main
git merge atlas-bitmap-pool-optimization
```

### Timeline Management

#### Naming Conventions
```bash
# Good optimization names
"baseline"                    # Initial performance measurement
"bitmap_pool_optimization"    # Specific optimization description
"hardware_canvas_rendering"   # Feature-based naming
"texture_packing_algorithm"   # Component-specific improvement

# Avoid generic names
"optimization_1"             # ❌ Not descriptive
"performance_fix"            # ❌ Too vague
"latest"                     # ❌ Not specific
```

#### Timeline Hygiene
```bash
# Regular cleanup of experimental data
python3 scripts/atlas_benchmark_collector.py clean --force

# Periodic backup of important timelines
cp benchmark_results/atlas_timeline.json \
   backups/atlas_timeline_$(date +%Y%m%d).json

# Remove outlier/invalid measurements
python3 scripts/atlas_benchmark_collector.py remove 3 --force
```

---

## Troubleshooting

### Common Issues

#### Benchmark Test Failures

**Issue:** Tests fail to run on emulator
```bash
java.lang.IllegalStateException: FrameTimingMetric requires a device with GPU profiling
```

**Solution:** Use physical device or comment out FrameTimingMetric
```kotlin
// Comment out for emulator testing
// FrameTimingMetric(),
MemoryUsageMetric(MemoryUsageMetric.Mode.Max)
```

**Issue:** Permission dialogs during tests
```bash
# Grant permissions before running tests
adb shell pm grant dev.serhiiyaremych.lumina android.permission.READ_MEDIA_IMAGES
adb shell pm grant dev.serhiiyaremych.lumina android.permission.READ_MEDIA_VIDEO
```

#### Data Collection Problems

**Issue:** No trace data in benchmark results
```bash
"PhotoLODProcessor.scaleBitmapSumMs": { "minimum": 0.0, "maximum": 0.0 }
```

**Solutions:**
1. **Verify trace imports**: `import androidx.tracing.trace`
2. **Check trace markers**: Ensure `trace("Name") { }` blocks are executed
3. **Test triggering**: Verify benchmark interactions trigger the traced code
4. **Tracing dependencies**: Confirm tracing libraries are properly added

**Issue:** Git commit shows as "unknown"
```bash
# Ensure git repository is initialized and has commits
git status
git log --oneline -5
```

**Issue:** Timeline corruption
```bash
# Restore from backup
ls benchmark_results/*_backup_*.json
cp benchmark_results/atlas_timeline_backup_latest.json \
   benchmark_results/atlas_timeline.json
```

#### Performance Issues

**Issue:** Inconsistent benchmark results
```bash
# Ensure consistent test environment
- Close other applications
- Ensure device is charged and cool
- Run tests multiple times and use median values
- Check for thermal throttling
```

**Issue:** Unexpectedly slow performance
```bash
# Check compilation mode
"compilationMode": "run-from-apk"  # Should be optimized

# Verify CPU locking
"cpuLocked": true  # Should be locked for consistent results
```

### Debugging Commands

#### Verify Benchmark Output Structure
```bash
# Pretty-print JSON for inspection
python3 -m json.tool benchmarkData.json

# Check for required metrics
grep -o '"[^"]*SumMs"' benchmarkData.json
```

#### Validate Timeline Data
```bash
# Check timeline JSON syntax
python3 -m json.tool benchmark_results/atlas_timeline.json > /dev/null

# View recent entries
python3 scripts/atlas_benchmark_collector.py list | tail -5
```

#### Monitor Collection Process
```bash
# Verbose collection with error details
python3 -v scripts/atlas_benchmark_collector.py collect \
    benchmarkData.json "test_optimization" 2>&1 | tee collection.log
```

### Recovery Procedures

#### Restore Timeline from Backup
```bash
# List available backups
ls -lt benchmark_results/*backup*.json

# Restore latest backup
cp benchmark_results/atlas_timeline_backup_20250701_132226.json \
   benchmark_results/atlas_timeline.json

# Verify restoration
python3 scripts/atlas_benchmark_collector.py list
```

#### Rebuild Timeline from Individual Results
```bash
# Start fresh timeline
rm benchmark_results/atlas_timeline.json

# Re-collect from archived benchmark files
for file in archive/benchmark_*.json; do
    name=$(basename "$file" .json)
    python3 scripts/atlas_benchmark_collector.py collect "$file" "$name"
done
```

#### Manual Timeline Repair
```bash
# Edit timeline directly (advanced)
cp benchmark_results/atlas_timeline.json atlas_timeline_backup_manual.json
vim benchmark_results/atlas_timeline.json

# Validate JSON syntax after editing
python3 -m json.tool benchmark_results/atlas_timeline.json > /dev/null
```

---

## Future Enhancements

### Planned Features

#### Automated Report Generation
- **HTML timeline charts**: Interactive SVG visualization
- **Trend analysis**: Performance improvement tracking
- **Cross-device comparison**: Multi-device performance matrix
- **Optimization impact**: Component-level improvement breakdown

#### CI/CD Integration
- **Automated baseline**: Establish performance baselines on main branch
- **Regression detection**: Alert on performance degradation
- **PR benchmarks**: Automated performance testing on pull requests
- **Performance gates**: Block merges that regress performance

#### Advanced Analytics
- **Statistical analysis**: Confidence intervals, significance testing
- **Outlier detection**: Identify and flag anomalous results
- **Correlation analysis**: Link code changes to performance impact
- **Predictive modeling**: Forecast optimization potential

### Extension Opportunities

#### Additional Profiles
- **UI rendering performance**: Frame timing, scroll smoothness
- **Database query optimization**: Query time, cache hit rates
- **Network request performance**: Latency, throughput, retry rates
- **Memory management**: Allocation patterns, GC pressure
- **Battery usage**: Power consumption per operation

#### Enhanced Metrics
- **Energy profiling**: Battery drain measurement
- **Thermal monitoring**: Temperature impact on performance
- **Network conditions**: Performance under different connectivity
- **Memory pressure**: Performance under low memory conditions

---

## Summary

This benchmarking system provides a comprehensive foundation for tracking performance optimizations across different domains in the LuminaGallery application. The modular design allows for easy extension to new optimization areas while maintaining data integrity and providing detailed insights into performance improvements over time.

Key strengths:
- **Profile-based architecture**: Supports multiple optimization domains
- **Git integration**: Links performance to exact code states
- **Safety features**: Automatic backups and confirmation prompts
- **Extensible design**: Easy addition of new profiles and metrics
- **Timeline management**: Complete audit trail of optimization progress

The system successfully bridges the gap between low-level performance measurements and high-level optimization tracking, enabling data-driven performance optimization decisions.