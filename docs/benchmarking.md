# Benchmarking Guide

Simple iterative optimization tracking for Android performance benchmarks.

## Quick Start

```bash
# Start working on an optimization
./gradlew trackOptimization -Poptimization.name="bitmap_pooling"

# Continue tracking progress (after making changes)
./gradlew trackOptimization -Poptimization.name="bitmap_pooling"

# View detailed comparison
./gradlew compareOptimization -Poptimization.name="bitmap_pooling"
```

## Commands

### `trackOptimization`
Runs benchmark and automatically shows progress comparison.

```bash
./gradlew trackOptimization -Poptimization.name="bitmap_pooling"
```

**What it does:**
1. Runs `connectedBenchmarkAndroidTest`
2. Collects results into `benchmark_results/bitmap_pooling/`
3. Creates new run file: `bitmap_pooling_run01_timestamp_commit.json`
4. Shows comparison with previous runs (if any)

### `compareOptimization`
Shows detailed comparison table of all runs within an optimization.

```bash
./gradlew compareOptimization -Poptimization.name="bitmap_pooling"
./gradlew compareOptimization -Poptimization.name="bitmap_pooling" -Pall.metrics
```

**Output:** Perfectly aligned table showing progress over time with color-coded changes:
- 🟢 **Green**: Meaningful improvement (≥15% faster)
- 🟡 **Yellow**: Minor slowdown (<15% slower)
- 🔴 **Red**: Significant slowdown (≥15% slower)
- ⚪ **Gray**: No change or insignificant improvement

### `listOptimizationRuns`
Lists all runs for an optimization.

```bash
./gradlew listOptimizationRuns -Poptimization.name="bitmap_pooling"
```

## File Organization

```
benchmark_results/
├── bitmap_pooling/
│   ├── bitmap_pooling_run01_20250702_194128_cb0669f-.json
│   ├── bitmap_pooling_run02_20250702_194133_cb0669f-.json
│   └── bitmap_pooling_run03_20250702_194838_cb0669f-.json
├── hardware_acceleration/
│   └── hardware_acceleration_run01_...json
└── memory_optimization/
    └── memory_optimization_run01_...json
```

## Workflow Example

```bash
# Phase 1: Establish baseline
./gradlew trackOptimization -Poptimization.name="bitmap_pooling"
# Output: Run #1 baseline metrics

# Phase 2: Implement bitmap pooling in code
# (edit your Kotlin files)

# Phase 3: Track improvement
./gradlew trackOptimization -Poptimization.name="bitmap_pooling" 
# Output: Run #2 with comparison to Run #1

# Phase 4: Refine implementation
# (more code changes)

# Phase 5: Track again
./gradlew trackOptimization -Poptimization.name="bitmap_pooling"
# Output: Run #3 with full progress view

# Phase 6: View detailed analysis anytime
./gradlew compareOptimization -Poptimization.name="bitmap_pooling"
```

## Features

- **Device consistency checking** - Warns if you switch devices mid-optimization
- **Git integration** - Tracks commit hashes automatically  
- **Perfect table alignment** - Professional formatting for reports
- **Automatic run numbering** - No manual file management needed
- **Time-focused metrics** - Shows performance metrics by default

## Tips

- **One optimization = One folder** - Keep related attempts together
- **Meaningful names** - Use descriptive optimization names like `bitmap_pooling`, `hardware_acceleration`
- **Consistent device** - Run all tests on the same device for accurate comparison
- **Commit changes** - Best practice to commit code before benchmarking (warns about dirty state)