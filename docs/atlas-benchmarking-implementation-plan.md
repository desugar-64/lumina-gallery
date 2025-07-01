# Atlas Texture System Benchmarking - Implementation Plan

> **📖 Reference Document**: `docs/atlas-implementation-plan.md`  
> This document provides a comprehensive implementation plan for establishing performance baselines and measuring optimizations for the atlas texture system. Use this as a detailed roadmap for implementing benchmarking infrastructure.

## Project Overview

This document outlines the implementation plan for creating a comprehensive benchmarking system to measure and track atlas texture system performance. The system will establish baselines, track optimizations over time, and provide automated reporting with timeline visualization.

## Current State Analysis

### Atlas System Performance Issues (From atlas-implementation-plan.md)

**Current Performance Baseline:**
- **Atlas Generation Time**: ~5 seconds for 7 images at LOD2 (128px) on Android Emulator
- **Target Performance**: < 1 second for 7 images, < 2 seconds for 50 images
- **Performance Gap**: 5x slower than target

**Critical Bottlenecks Identified:**
1. **PhotoLODProcessor.kt** - Bitmap memory pool needed (line 84)
2. **AtlasGenerator.kt** - Software canvas → Hardware canvas optimization (lines 147-183)
3. **TexturePacker.kt** - Linear shelf search optimization
4. **PhotoScaler.kt** - Hardware-accelerated scaling

### Benchmarking Requirements

**What We Need to Measure:**
- Atlas generation time breakdown by component
- Memory usage during atlas operations
- Frame timing during zoom/pan operations
- Optimization impact tracking over time

**Current Limitations:**
- No performance metrics collection
- No baseline measurements
- No automated optimization tracking
- Manual performance assessment only

## Benchmarking System Architecture

### Core Components Overview

```
Atlas Benchmarking System
├── Tracing Infrastructure (Android Tracing)
├── Atlas Idleness Tracking (Custom State Management)
├── Macrobenchmark Tests (UIAutomator + Metrics)
├── Results Collection Pipeline (Python Scripts)
├── Timeline Visualization (HTML + SVG Charts)
└── Gradle Automation (Task Integration)
```

### Performance Tracking Strategy

**Three-Layer Approach:**
1. **Trace Markers**: Fine-grained component timing
2. **Atlas State Tracking**: Background generation completion
3. **Macrobenchmark**: End-to-end user interaction simulation

### Timeline-Based Results Management

**Instead of simple before/after comparison:**
- Track all benchmark runs with timestamps
- Build performance timeline across optimizations
- Generate visual charts showing improvement trajectory
- Automated HTML reports with interactive SVG charts

## Implementation Tasks & Timeline

### Phase 1: Tracing Infrastructure Setup (Week 1)

#### ✅ Task 1.1: Dependency Setup (COMPLETED)
**Files modified:**
- `gradle/libs.versions.toml` - Add tracing library versions
- `app/build.gradle.kts` - Add tracing dependencies

**Dependencies Added:**
```toml
tracing = "1.3.0"
compose-tracing = "1.8.3"
androidx-tracing-ktx = { group = "androidx.tracing", name = "tracing-ktx", version.ref = "tracing" }
androidx-compose-runtime-tracing = { group = "androidx.compose.runtime", name = "runtime-tracing", version.ref = "compose-tracing" }
```

#### ✅ Task 1.2: Atlas State Tracking Implementation (COMPLETED)
**Goal**: Track when atlas generation is actually complete (not just UI idle)

**✅ Files modified:**
- `ui/gallery/GalleryViewModel.kt` - Add atlas generation state tracking
- `ui/MainActivity.kt` - Add atlas idleness tracking system
- `common/src/main/java/dev/serhiiyaremych/lumina/common/BenchmarkLabels.kt` - Centralized constants

**✅ Implementation completed with enhanced features:**
- Atlas generation state tracking via StateFlow
- Choreographer-based Compose idleness detection for UI performance
- Centralized benchmark labels in common module

**GalleryViewModel.kt Changes (IMPLEMENTED):**
```kotlin
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val atlasManager: AtlasManager
) : ViewModel() {
    
    private val _atlasState = MutableStateFlow<AtlasUpdateResult?>(null)
    val atlasState: StateFlow<AtlasUpdateResult?> = _atlasState.asStateFlow()
    
    // NEW: Atlas generation state for benchmarking
    private val _isAtlasGenerating = MutableStateFlow(false)
    val isAtlasGenerating: StateFlow<Boolean> = _isAtlasGenerating.asStateFlow()
    
    fun onVisibleCellsChanged(visibleCells: List<HexCellWithMedia>, zoom: Float) {
        viewModelScope.launch {
            _isAtlasGenerating.value = true // Mark as generating
            try {
                val result = atlasManager.updateVisibleCells(visibleCells, zoom)
                _atlasState.value = result
            } finally {
                _isAtlasGenerating.value = false // Mark as complete
            }
        }
    }
}
```

**MainActivity.kt Changes:**
```kotlin
// Add to MainActivity class
private val galleryViewModel: GalleryViewModel by androidx.activity.viewModels()

internal fun ComponentActivity.launchAtlasIdlenessTracking(viewModel: GalleryViewModel) {
    val contentView: View = findViewById(android.R.id.content)
    
    // Track atlas generation state
    lifecycleScope.launch {
        viewModel.isAtlasGenerating.collect { isGenerating ->
            contentView.contentDescription = if (isGenerating) {
                "ATLAS-GENERATING"
            } else {
                "ATLAS-IDLE"
            }
        }
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing setup
    launchAtlasIdlenessTracking(galleryViewModel)
    // ... rest of onCreate
}
```

#### ✅ Task 1.3: Trace Markers Implementation (COMPLETED)
**Goal**: Add detailed timing markers to atlas components

**✅ Files modified:**
- `domain/usecase/PhotoLODProcessor.kt` - Bitmap operation tracing
- `domain/usecase/AtlasGenerator.kt` - Canvas rendering tracing
- `domain/usecase/AtlasManager.kt` - Coordination tracing

**✅ Implementation completed:** All primary optimization targets instrumented with trace markers using centralized BenchmarkLabels constants.

**PhotoLODProcessor.kt Trace Markers:**
```kotlin
import androidx.tracing.trace

suspend fun processPhotoForLOD(
    uri: Uri,
    lodLevel: LODLevel,
    scaleStrategy: ScaleStrategy = ScaleStrategy.CENTER_CROP
): ProcessedPhoto? {
    return trace("PhotoLODProcessor.processPhoto") {
        trace("PhotoLODProcessor.loadBitmap") {
            // Current bitmap loading code
            val originalBitmap = loadBitmapFromUri(uri) ?: return@trace null
            originalBitmap
        }?.let { originalBitmap ->
            trace("PhotoLODProcessor.scaleBitmap") {
                // Current bitmap scaling code - OPTIMIZATION TARGET
                val scaledBitmap = photoScaler.scale(originalBitmap, targetSize, scaleStrategy)
                // ... rest of processing
                scaledBitmap
            }
        }
    }
}
```

**AtlasGenerator.kt Trace Markers:**
```kotlin
import androidx.tracing.trace

suspend fun generateAtlas(
    photoUris: List<Uri>,
    lodLevel: LODLevel
): AtlasGenerationResult = trace("AtlasGenerator.generateAtlas") {
    
    trace("AtlasGenerator.processPhotos") {
        // Photo processing loop
        photoUris.mapNotNull { uri ->
            photoLODProcessor.processPhotoForLOD(uri, lodLevel, scaleStrategy)
        }
    }.let { processedPhotos ->
        
        trace("AtlasGenerator.packTextures") {
            // Texture packing
            texturePacker.pack(processedPhotos.map { it.toImageToPack() })
        }.let { packResult ->
            
            trace("AtlasGenerator.createAtlasBitmap") {
                trace("AtlasGenerator.softwareCanvas") {
                    // Current software canvas implementation - OPTIMIZATION TARGET
                    createAtlasBitmap(processedPhotos, packResult, atlasSize)
                }
            }
        }
    }
}
```

**AtlasManager.kt Trace Markers:**
```kotlin
import androidx.tracing.trace

suspend fun updateVisibleCells(
    visibleCells: List<HexCellWithMedia>,
    currentZoom: Float
): AtlasUpdateResult = trace("AtlasManager.updateVisibleCells") {
    
    trace("AtlasManager.selectLODLevel") {
        // LOD level selection logic
        selectLODLevel(currentZoom)
    }.let { lodLevel ->
        
        trace("AtlasManager.generateAtlas") {
            // Atlas generation coordination
            atlasGenerator.generateAtlas(photoUris, lodLevel)
        }
    }
}
```

#### ✅ Task 1.4: Canvas Test Tags Implementation (COMPLETED)
**Goal**: Add UIAutomator-accessible test tags for benchmark interaction

**✅ Files modified:**
- `ui/App.kt` - Added test tags to GridCanvas component
- Proper semantics setup with testTagsAsResourceId

**✅ Implementation completed:** Canvas properly tagged for UIAutomator detection with centralized test tag constants.

**Canvas Test Tags (IMPLEMENTED):**
```kotlin
// In MediaHexVisualization.kt or wherever main canvas is
Canvas(
    modifier = Modifier
        .fillMaxSize()
        .semantics { 
            testTagsAsResourceId = true
            contentDescription = "Gallery canvas"
        }
        .testTag("gallery_canvas")
) {
    // Existing hex grid drawing code
}
```

### Phase 2: Macrobenchmark Test Implementation (Week 2)

#### ✅ Task 2.1: AtlasPerformanceBenchmark Creation (COMPLETED)
**Goal**: Create comprehensive benchmark test that triggers atlas generation through realistic interactions

**✅ File created:**
- `benchmark/src/main/java/dev/serhiiyaremych/lumina/benchmark/AtlasPerformanceBenchmark.kt`

**✅ Implementation completed with enhancements:**
- **Zoom test**: App-controlled automatic zoom sequence (works on physical devices)
- **Pan test**: UIAutomator device.swipe() gestures (works on physical devices)
- **Smart idleness detection**: Uses both atlas and Compose idleness detection
- **Efficient timing**: Replaced Thread.sleep with awaitable idleness detection
- **FrameTimingMetric**: Enabled for physical device testing
- **Permission handling**: Comprehensive ADB permission granting
- **HexGrid fix**: Fixed validation for hexagonal pattern support

**✅ Key improvements made:**
- Fixed UIAutomator gesture limitations on emulators vs physical devices
- Added Choreographer-based Compose idleness detection for pan performance
- Resolved HexGrid validation crash for larger photo datasets
- Optimized benchmark timing for faster execution

**Benchmark Test Implementation (IMPLEMENTED):**
```kotlin
package dev.serhiiyaremych.lumina.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AtlasPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun awaitAtlasIdle(timeout: Long = 8000) {
        device.wait(Until.findObject(By.desc("ATLAS-IDLE")), timeout)
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun atlasGenerationThroughZoomInteractions() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            // Primary optimization targets
            TraceSectionMetric("PhotoLODProcessor.scaleBitmap"), // Bitmap pool optimization
            TraceSectionMetric("AtlasGenerator.softwareCanvas"), // Hardware canvas optimization
            
            // Supporting metrics
            TraceSectionMetric("PhotoLODProcessor.loadBitmap"),
            TraceSectionMetric("AtlasGenerator.createAtlasBitmap"),
            TraceSectionMetric("AtlasManager.updateVisibleCells"),
            TraceSectionMetric("AtlasManager.selectLODLevel"),
            TraceSectionMetric("AtlasManager.generateAtlas"),
            
            // System metrics
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max)
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 3, // Small number for faster feedback
        setupBlock = {
            startActivityAndWait()
            
            // Wait for gallery canvas to be ready
            device.wait(Until.hasObject(By.res("gallery_canvas")), 10_000)
            
            // Wait for initial atlas generation to complete
            awaitAtlasIdle(15_000)
        }
    ) {
        val canvas = device.findObject(By.res("gallery_canvas"))
        require(canvas != null) { "Gallery canvas not found" }
        
        val canvasBounds = canvas.visibleBounds
        val centerX = canvasBounds.centerX()
        val centerY = canvasBounds.centerY()

        // Trigger atlas generation through zoom level changes
        // Based on LOD levels: 0.0-0.5 (LOD_0), 0.5-2.0 (LOD_2), 2.0-10.0 (LOD_4)
        
        // 1. Zoom out significantly to trigger LOD_0 (32px) generation
        canvas.pinchClose(200)
        awaitAtlasIdle(5_000)
        
        // 2. Zoom in moderately to trigger LOD_2 (128px) generation  
        canvas.pinchOpen(300)
        awaitAtlasIdle(5_000)
        
        // 3. Zoom in more to trigger LOD_4 (512px) generation
        canvas.pinchOpen(400)
        awaitAtlasIdle(8_000)
        
        // 4. Pan around to trigger atlas regeneration with different visible cells
        device.drag(centerX, centerY, centerX - 300, centerY, 10)
        awaitAtlasIdle(3_000)
        
        device.drag(centerX, centerY, centerX + 300, centerY - 300, 10)
        awaitAtlasIdle(3_000)
        
        // 5. Final zoom back to medium level to complete cycle
        canvas.pinchClose(150)
        awaitAtlasIdle(5_000)
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun atlasGenerationThroughPanInteractions() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric("AtlasManager.updateVisibleCells"),
            TraceSectionMetric("AtlasGenerator.createAtlasBitmap"),
            FrameTimingMetric()
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = {
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("gallery_canvas")), 10_000)
            awaitAtlasIdle(15_000)
        }
    ) {
        val canvas = device.findObject(By.res("gallery_canvas"))
        require(canvas != null) { "Gallery canvas not found" }
        
        // Trigger atlas regeneration through panning (changes visible cells)
        val centerX = canvas.visibleCenter.x
        val centerY = canvas.visibleCenter.y
        
        device.drag(centerX, centerY, centerX - 200, centerY, 10)
        awaitAtlasIdle(2_000)
        
        device.drag(centerX, centerY, centerX + 400, centerY, 10)
        awaitAtlasIdle(2_000)
        
        device.drag(centerX, centerY, centerX, centerY - 300, 10)
        awaitAtlasIdle(2_000)
    }

    companion object {
        private const val TARGET_PACKAGE = "dev.serhiiyaremych.lumina"
    }
}
```

### Phase 3: Results Collection & Visualization (Week 3)

#### 🔄 Task 3.1: Benchmark Results Collector (IN PROGRESS)
**Goal**: Python script to collect benchmark results into timeline database

**File to create:**
- `scripts/atlas_benchmark_collector.py`

**Status**: Ready for implementation - benchmark infrastructure is complete and working

**Implementation:**
```python
#!/usr/bin/env python3
"""
Atlas Benchmark Results Collector

Collects benchmark JSON results and maintains a timeline database
for tracking atlas texture system performance over time.

Usage:
    python atlas_benchmark_collector.py benchmark.json "optimization_name"
"""

import json
import datetime
import os
import subprocess
from pathlib import Path
from typing import Dict, List, Any, Optional

class AtlasBenchmarkCollector:
    def __init__(self, results_dir: str = "benchmark_results"):
        self.results_dir = Path(results_dir)
        self.results_dir.mkdir(exist_ok=True)
        self.timeline_file = self.results_dir / "atlas_timeline.json"
        
        # Key metrics we're tracking (matches trace markers)
        self.atlas_metrics = [
            "PhotoLODProcessor.scaleBitmap",      # Bitmap pool optimization target
            "AtlasGenerator.softwareCanvas",      # Hardware canvas optimization target
            "PhotoLODProcessor.loadBitmap",       # I/O optimization
            "AtlasGenerator.createAtlasBitmap",   # Overall atlas generation
            "AtlasManager.updateVisibleCells",    # Atlas coordination
            "AtlasManager.selectLODLevel",        # LOD selection logic
            "AtlasManager.generateAtlas"          # Atlas generation trigger
        ]
    
    def collect_benchmark_result(self, benchmark_json_path: str, optimization_name: str) -> Dict[str, Any]:
        """Collect a new benchmark result and add to timeline"""
        if not Path(benchmark_json_path).exists():
            raise FileNotFoundError(f"Benchmark file not found: {benchmark_json_path}")
        
        with open(benchmark_json_path) as f:
            benchmark_data = json.load(f)
        
        # Extract atlas-specific metrics
        extracted_metrics = self._extract_atlas_metrics(benchmark_data)
        
        # Create timeline entry
        timeline_entry = {
            "timestamp": datetime.datetime.now().isoformat(),
            "optimization": optimization_name,
            "metrics": extracted_metrics,
            "total_atlas_time": sum(extracted_metrics.values()),
            "git_commit": self._get_git_commit(),
            "benchmark_file": str(Path(benchmark_json_path).name),
            "system_metrics": self._extract_system_metrics(benchmark_data)
        }
        
        # Load existing timeline
        timeline = self._load_timeline()
        timeline.append(timeline_entry)
        
        # Save updated timeline
        with open(self.timeline_file, 'w') as f:
            json.dump(timeline, f, indent=2)
        
        print(f"✅ Collected benchmark result for: {optimization_name}")
        print(f"📊 Total atlas time: {timeline_entry['total_atlas_time']:.1f}ms")
        print(f"🔄 Timeline entries: {len(timeline)}")
        
        return timeline_entry
    
    def _extract_atlas_metrics(self, benchmark_data: Dict) -> Dict[str, float]:
        """Extract atlas-specific metrics from benchmark JSON"""
        extracted = {}
        
        for metric in self.atlas_metrics:
            if metric in benchmark_data:
                # Use median time as representative value
                extracted[metric] = benchmark_data[metric].get("medianMs", 0.0)
            else:
                print(f"⚠️  Metric not found in benchmark data: {metric}")
                extracted[metric] = 0.0
        
        return extracted
    
    def _extract_system_metrics(self, benchmark_data: Dict) -> Dict[str, Any]:
        """Extract system-level metrics"""
        system_metrics = {}
        
        # Frame timing metrics
        if "frameDurationCpuMs" in benchmark_data:
            system_metrics["frame_timing"] = benchmark_data["frameDurationCpuMs"]
        
        # Memory usage metrics
        if "memoryUsageMaximum" in benchmark_data:
            system_metrics["memory_max"] = benchmark_data["memoryUsageMaximum"]
        
        return system_metrics
    
    def _load_timeline(self) -> List[Dict]:
        """Load existing timeline or create empty one"""
        if self.timeline_file.exists():
            with open(self.timeline_file) as f:
                return json.load(f)
        return []
    
    def _get_git_commit(self) -> str:
        """Get current git commit hash"""
        try:
            result = subprocess.run(
                ['git', 'rev-parse', '--short', 'HEAD'], 
                capture_output=True, 
                text=True,
                cwd=Path(__file__).parent.parent
            )
            return result.stdout.strip() if result.returncode == 0 else "unknown"
        except Exception:
            return "unknown"
    
    def get_latest_results(self, count: int = 5) -> List[Dict]:
        """Get most recent benchmark results"""
        timeline = self._load_timeline()
        return timeline[-count:] if timeline else []
    
    def get_optimization_comparison(self, baseline_name: str = "baseline") -> Dict[str, Any]:
        """Compare current state against baseline"""
        timeline = self._load_timeline()
        if len(timeline) < 2:
            return {"error": "Need at least 2 benchmark results for comparison"}
        
        # Find baseline
        baseline = next((entry for entry in timeline if baseline_name in entry["optimization"].lower()), None)
        if not baseline:
            baseline = timeline[0]  # Use first entry as baseline
        
        latest = timeline[-1]
        
        comparison = {
            "baseline": baseline,
            "latest": latest,
            "improvements": {}
        }
        
        for metric in self.atlas_metrics:
            baseline_time = baseline["metrics"].get(metric, 0)
            latest_time = latest["metrics"].get(metric, 0)
            
            if baseline_time > 0:
                improvement = (baseline_time - latest_time) / baseline_time * 100
                comparison["improvements"][metric] = {
                    "baseline_ms": baseline_time,
                    "latest_ms": latest_time,
                    "improvement_percent": improvement
                }
        
        return comparison

if __name__ == "__main__":
    import sys
    
    if len(sys.argv) != 3:
        print("Usage: python atlas_benchmark_collector.py benchmark.json 'optimization_name'")
        print("\nExamples:")
        print("  python atlas_benchmark_collector.py results.json 'baseline'")
        print("  python atlas_benchmark_collector.py results.json 'bitmap_pool_optimization'")
        print("  python atlas_benchmark_collector.py results.json 'hardware_canvas_optimization'")
        sys.exit(1)
    
    collector = AtlasBenchmarkCollector()
    collector.collect_benchmark_result(sys.argv[1], sys.argv[2])
```

#### ⏳ Task 3.2: HTML Timeline Chart Generator (PENDING)
**Goal**: Generate interactive HTML reports with SVG timeline charts

**File to create:**
- `scripts/atlas_timeline_chart.py`

**Status**: Waiting for Task 3.1 completion

**Implementation:** *(Truncated for brevity, full implementation would include)*
```python
#!/usr/bin/env python3
"""
Atlas Performance Timeline Chart Generator

Generates HTML reports with SVG charts showing atlas performance over time.

Usage:
    python atlas_timeline_chart.py
"""

import json
import colorsys
from pathlib import Path
from typing import List, Dict, Tuple

class AtlasTimelineChart:
    def __init__(self, timeline_file: str = "benchmark_results/atlas_timeline.json"):
        self.timeline_file = Path(timeline_file)
        
        # Chart dimensions
        self.chart_width = 800
        self.chart_height = 300
        self.margin = 60
        
        # Target performance (from atlas implementation plan)
        self.target_time_ms = 1000  # 1 second target
        self.baseline_time_ms = 5000  # 5 second baseline
    
    def generate_html_report(self, output_file: str = "benchmark_results/atlas_performance_report.html"):
        """Generate comprehensive HTML report with timeline charts"""
        timeline = self._load_timeline()
        if not timeline:
            self._generate_empty_report(output_file)
            return
        
        # Generate chart components
        total_times_chart = self._create_total_times_chart(timeline)
        breakdown_chart = self._create_breakdown_chart(timeline)
        optimization_summary = self._create_optimization_summary(timeline)
        target_progress = self._create_target_progress(timeline)
        
        # Create complete HTML
        html_content = self._create_html_template(
            total_times_chart, 
            breakdown_chart, 
            optimization_summary, 
            target_progress
        )
        
        with open(output_file, 'w') as f:
            f.write(html_content)
        
        print(f"📊 Generated performance report: {output_file}")
        print(f"🌐 Open in browser: file://{Path(output_file).absolute()}")
    
    def _create_total_times_chart(self, timeline: List[Dict]) -> str:
        """Create SVG line chart for total atlas generation times"""
        if not timeline:
            return "<p>No timeline data available</p>"
        
        times = [entry["total_atlas_time"] for entry in timeline]
        max_time = max(max(times), self.target_time_ms * 1.2) if times else self.target_time_ms
        
        # Generate SVG with timeline data, target line, and data points
        # ... detailed SVG generation code ...
        
        return f"<svg width='{self.chart_width}' height='{self.chart_height}'><!-- chart content --></svg>"
    
    # ... Additional chart generation methods ...

if __name__ == "__main__":
    chart_generator = AtlasTimelineChart()
    chart_generator.generate_html_report()
```

#### ⏳ Task 3.3: Gradle Task Automation (PENDING)
**Goal**: Automate benchmark collection and report generation

**File to modify:**
- `benchmark/build.gradle.kts`

**Status**: Waiting for Python scripts completion

**Gradle Task Implementation:**
```kotlin
// Add to macrobenchmark/build.gradle.kts

tasks.register("collectAtlasBenchmarkResults") {
    group = "atlas-benchmarking"
    description = "Collect atlas benchmark results and generate performance report"
    dependsOn("connectedCheck")
    
    doLast {
        val resultsDir = File(project.rootProject.projectDir, "benchmark_results")
        resultsDir.mkdirs()
        
        // Find latest benchmark JSON file
        val outputDir = File(buildDir, "outputs/connected_android_test_additional_output/debugAndroidTest/connected")
        if (!outputDir.exists()) {
            throw GradleException("Benchmark output directory not found: ${outputDir.absolutePath}")
        }
        
        val benchmarkFiles = outputDir.walkTopDown()
            .filter { 
                it.name.contains("AtlasPerformanceBenchmark") && 
                it.extension == "json" &&
                it.name.contains("atlasGenerationThroughZoomInteractions")
            }
            .sortedByDescending { it.lastModified() }
        
        if (benchmarkFiles.isEmpty()) {
            throw GradleException("No atlas benchmark results found in ${outputDir.absolutePath}")
        }
        
        val latestResult = benchmarkFiles.first()
        val optimizationName = project.findProperty("optimization.name")?.toString() 
            ?: "benchmark_${System.currentTimeMillis()}"
        
        println("📊 Found benchmark result: ${latestResult.name}")
        println("🏷️  Optimization name: $optimizationName")
        
        // Execute Python collector script
        val scriptsDir = File(project.rootProject.projectDir, "scripts")
        val collectorScript = File(scriptsDir, "atlas_benchmark_collector.py")
        
        if (!collectorScript.exists()) {
            throw GradleException("Collector script not found: ${collectorScript.absolutePath}")
        }
        
        exec {
            workingDir = project.rootProject.projectDir
            commandLine("python3", collectorScript.absolutePath, latestResult.absolutePath, optimizationName)
        }
        
        // Generate HTML report
        val chartScript = File(scriptsDir, "atlas_timeline_chart.py")
        if (chartScript.exists()) {
            exec {
                workingDir = project.rootProject.projectDir
                commandLine("python3", chartScript.absolutePath)
            }
        }
        
        val reportFile = File(resultsDir, "atlas_performance_report.html")
        if (reportFile.exists()) {
            println("📈 Performance report generated!")
            println("🌐 View report: file://${reportFile.absolutePath}")
        }
    }
}

// Helper task for running specific optimization benchmarks
tasks.register("benchmarkAtlasOptimization") {
    group = "atlas-benchmarking"
    description = "Run atlas benchmark for specific optimization"
    
    doLast {
        val optimizationName = project.findProperty("optimization.name")?.toString()
        if (optimizationName.isNullOrBlank()) {
            throw GradleException("Please specify optimization name: -Poptimization.name='your_optimization'")
        }
        
        // Run benchmark
        tasks.getByName("connectedCheck").actions.forEach { it.execute(tasks.getByName("connectedCheck")) }
        
        // Collect results automatically
        tasks.getByName("collectAtlasBenchmarkResults").actions.forEach { 
            it.execute(tasks.getByName("collectAtlasBenchmarkResults")) 
        }
    }
}
```

### Phase 4: Integration & Validation (Week 4)

#### Task 4.1: End-to-End Testing
**Goal**: Validate complete benchmarking pipeline works correctly

**Test Scenarios:**
1. **Baseline Establishment**: Run initial benchmark, verify metrics collection
2. **Optimization Tracking**: Simulate optimization, verify improvement detection
3. **Report Generation**: Validate HTML report generation and chart accuracy
4. **Gradle Integration**: Test automated pipeline execution

#### Task 4.2: Documentation & Usage Guide
**Goal**: Create comprehensive usage documentation

**File to create:**
- `docs/atlas-benchmarking-usage.md`

**Documentation Content:**
```markdown
# Atlas Benchmarking System - Usage Guide

## Quick Start

### 1. Establish Baseline
```bash
./gradlew :macrobenchmark:collectAtlasBenchmarkResults -Poptimization.name="baseline"
```

### 2. Run Optimization Benchmarks
```bash
# After implementing bitmap pool optimization
./gradlew :macrobenchmark:collectAtlasBenchmarkResults -Poptimization.name="bitmap_pool"

# After implementing hardware canvas optimization  
./gradlew :macrobenchmark:collectAtlasBenchmarkResults -Poptimization.name="hardware_canvas"
```

### 3. View Results
```bash
open benchmark_results/atlas_performance_report.html
```

## Detailed Workflow...
```

## Technical Specifications

### Performance Targets
- **Primary Goal**: 5 seconds → 1 second (80% improvement)
- **Component Targets**:
  - `PhotoLODProcessor.scaleBitmap`: 30-50% improvement (bitmap pool)
  - `AtlasGenerator.softwareCanvas`: 5-10x improvement (hardware canvas)

### Metrics Collection
- **Trace Granularity**: Component-level timing (ms precision)
- **System Metrics**: Memory usage, frame timing
- **Timeline Tracking**: All benchmark runs with timestamps
- **Visualization**: Interactive SVG charts with target lines

### File Structure
```
benchmark_results/
├── atlas_timeline.json           # Timeline database
├── atlas_performance_report.html # Latest report
└── archived/                     # Historical reports
    ├── 2025-06-30_baseline.html
    ├── 2025-07-01_bitmap_pool.html
    └── 2025-07-02_hardware_canvas.html

scripts/
├── atlas_benchmark_collector.py  # Results collector
└── atlas_timeline_chart.py       # Chart generator

docs/
├── atlas-benchmarking-implementation-plan.md  # This document
└── atlas-benchmarking-usage.md               # Usage guide
```

## Integration Points

### Key Files to Modify
1. **GalleryViewModel.kt**: Atlas state tracking for benchmark sync
2. **MainActivity.kt**: Idleness tracking system integration
3. **PhotoLODProcessor.kt**: Trace markers for bitmap operations
4. **AtlasGenerator.kt**: Trace markers for canvas operations
5. **AtlasManager.kt**: Trace markers for coordination logic
6. **macrobenchmark/build.gradle.kts**: Gradle task automation

### Dependencies Required
```toml
# gradle/libs.versions.toml
tracing = "1.3.0"
compose-tracing = "1.8.3"
androidx-tracing-ktx = { group = "androidx.tracing", name = "tracing-ktx" }
androidx-compose-runtime-tracing = { group = "androidx.compose.runtime", name = "runtime-tracing" }
```

## Success Metrics

### Benchmark Quality Indicators
- **Trace Coverage**: All key atlas components instrumented
- **Measurement Accuracy**: Consistent results across runs (< 10% variance)
- **Atlas Sync**: Proper idle detection (no premature measurements)
- **Timeline Continuity**: All optimization phases tracked

### Optimization Validation
- **Baseline Established**: Initial performance documented
- **Improvement Detection**: >= 10% improvements clearly visible
- **Target Progress**: Tracking toward 80% overall improvement
- **Component Attribution**: Identify which optimizations contribute most

## Risk Mitigation

### Potential Issues & Solutions
1. **Trace Overhead**: Minimal impact on measurements (< 1ms per trace)
2. **Idle Detection**: Robust atlas state tracking prevents false measurements
3. **Platform Variations**: Run on consistent device for reliable comparisons
4. **Timeline Corruption**: JSON validation and backup mechanisms

### Troubleshooting Guide
- **No Benchmark Data**: Check trace markers are imported and called
- **Idle Timeout**: Increase atlas idle timeout for complex datasets
- **Missing Metrics**: Verify trace section names match exactly
- **Report Generation**: Check Python dependencies and file permissions

## Future Enhancements

### Phase 2 Improvements
- **Multiple Device Testing**: Cross-device performance comparison
- **CI Integration**: Automated performance regression detection
- **Advanced Visualization**: Interactive charts with drill-down capability
- **Performance Alerts**: Threshold-based improvement/regression notifications

### Advanced Metrics
- **GPU Utilization**: Hardware acceleration effectiveness measurement
- **Memory Pressure**: Atlas generation under low-memory conditions
- **Battery Impact**: Power consumption tracking for optimization validation
- **Thermal Throttling**: Performance impact under sustained load

---

## Checkpoint: Ready for Implementation

### 📋 **Implementation Checklist**
- [ ] **Dependencies**: Tracing libraries added to project
- [ ] **Atlas Tracking**: State management for generation completion
- [ ] **Trace Markers**: Component-level timing instrumentation
- [ ] **Benchmark Tests**: UIAutomator interactions with zoom/pan
- [ ] **Results Collection**: Python scripts for timeline management
- [ ] **Chart Generation**: HTML reports with SVG visualization
- [ ] **Gradle Integration**: Automated collection and reporting
- [ ] **Documentation**: Usage guide and troubleshooting

### 🎯 **Next Steps**
1. **Week 1**: Implement tracing infrastructure and atlas state tracking
2. **Week 2**: Create macrobenchmark tests with proper interaction patterns
3. **Week 3**: Build results collection pipeline and visualization system
4. **Week 4**: Integrate with Gradle automation and validate end-to-end

### 📈 **Expected Outcomes**
- **Baseline Metrics**: Accurate measurement of current atlas performance
- **Optimization Tracking**: Clear before/after improvement visualization
- **Timeline Reports**: Historical performance trend analysis
- **Automated Pipeline**: One-command benchmark execution and reporting

This comprehensive benchmarking system will provide the foundation for systematically optimizing the atlas texture system and achieving the target 5x performance improvement.

---

## 🏁 **Current Implementation Status** (Updated)

### ✅ **Phase 1 & 2: COMPLETED** (Tracing Infrastructure + Benchmark Tests)
- **Dependencies**: Tracing libraries integrated
- **Atlas State Tracking**: StateFlow-based generation monitoring
- **Compose Idleness**: Choreographer-based UI performance detection  
- **Trace Markers**: All critical components instrumented
- **Canvas Test Tags**: UIAutomator-accessible elements
- **Benchmark Tests**: Working zoom and pan performance tests
- **Device Support**: Physical device testing enabled
- **HexGrid Fix**: Hexagonal pattern validation resolved

### 🔄 **Phase 3: IN PROGRESS** (Results Collection & Visualization)
- **atlas_benchmark_collector.py**: Ready for implementation
- **atlas_timeline_chart.py**: Ready for implementation  
- **Gradle automation**: Ready for implementation

### 📊 **Working Benchmark System Features**
1. **Dual Idleness Detection**:
   - `awaitAtlasIdle()`: For zoom tests (triggers atlas regeneration)
   - `awaitComposeIdle()`: For pan tests (UI recomposition only)

2. **Optimized Test Execution**:
   - App-controlled zoom automation (reliable across devices)
   - UIAutomator pan gestures (works on physical devices)
   - Smart waiting (no fixed Thread.sleep delays)

3. **Comprehensive Metrics Collection**:
   - Primary targets: `PhotoLODProcessor.scaleBitmap`, `AtlasGenerator.softwareCanvas`
   - Supporting metrics: Atlas coordination, LOD selection, bitmap operations
   - System metrics: FrameTimingMetric (physical devices), Memory usage

4. **Robust Error Handling**:
   - Permission management via ADB
   - Canvas detection and bounds calculation
   - HexGrid validation for variable photo datasets

### 🎯 **Next Steps** (Remaining Work)
1. **Implement Python collector script** (Task 3.1)
2. **Create HTML chart generator** (Task 3.2)  
3. **Add Gradle automation tasks** (Task 3.3)
4. **Test end-to-end pipeline** (Task 4.1)

### 🚀 **Ready for Optimization Tracking**
The benchmark infrastructure is **production-ready** for tracking atlas optimizations:
- Run baseline: Benchmark tests work on physical devices
- Collect results: Manual JSON analysis possible, automation pending
- Track improvements: Trace markers capture all optimization targets
- Validate changes: Reliable performance measurement system in place

**Total Progress: ~75% Complete** - Core benchmarking system functional, automation scripts remaining.