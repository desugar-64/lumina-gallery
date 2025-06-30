Metrics are the main type of information extracted from your benchmarks. They are passed to the `measureRepeated` function as a List, which lets you specify multiple measured metrics at once. At least one type of metric is required for the benchmark to run.

The following code snippet captures frame timing and custom trace section metrics:
benchmarkRule.measureRepeated(
packageName = TARGET_PACKAGE,
metrics = listOf(
FrameTimingMetric(),
TraceSectionMetric("RV CreateView"),
TraceSectionMetric("RV OnBindView"),
),
iterations = 5,
// ...
)

In this example, RV CreateView and RV OnBindView are the IDs of traceable blocks that are defined in RecyclerView. The source code for the createViewHolder() method is an example of how you can define traceable blocks within your own code.

StartupTimingMetric, TraceSectionMetric, FrameTimingMetric, and PowerMetric, are covered in detail later in this document. For a full list of metrics, check out subclasses of Metric.

In this example, RV CreateView and RV OnBindView are the IDs of traceable blocks that are defined in RecyclerView. The source code for the createViewHolder() method is an example of how you can define traceable blocks within your own code.

StartupTimingMetric, TraceSectionMetric, FrameTimingMetric, and PowerMetric, are covered in detail later in this document. For a full list of metrics, check out subclasses of Metric.

StartupTimingMetric

StartupTimingMetric captures app startup timing metrics with the following values:

    timeToInitialDisplayMs: The amount of time from when the system receives a launch intent to when it renders the first frame of the destination Activity.
    timeToFullDisplayMs: The amount of time from when the system receives a launch intent to when the app reports fully drawn using the reportFullyDrawn() method. The measurement stops at the completion of rendering the first frame after—or containing—the reportFullyDrawn() call. This measurement might not be available on Android 10 (API level 29) and earlier.

StartupTimingMetric outputs the min, median, and max values from the startup iterations. To assess startup improvement you should focus on median values, since they provide the best estimate of the typical startup time. For more information about what contributes to app startup time.

FrameTimingMetric

FrameTimingMetric captures timing information from frames produced by a benchmark, such as a scrolling or animation, and outputs the following values:

    frameOverrunMs: the amount of time a given frame misses its deadline by. Positive numbers indicate a dropped frame and visible jank or stutter. Negative numbers indicate how much faster a frame is than the deadline. Note: This is available only on Android 12 (API level 31) and higher.
    frameDurationCpuMs: the amount of time the frame takes to be produced on the CPU on both the UI thread and the RenderThread.

These measurements are collected in a distribution of 50th, 90th, 95th, and 99th percentile.

TraceSectionMetric
Experimental: This class is experimental.

TraceSectionMetric captures the number of times a trace section matching the provided sectionName occurs and the amount of time it takes. For the time, it outputs the minimum, median, and maximum times in milliseconds. The trace section is defined either by the function call trace(sectionName) or the code between Trace.beginSection(sectionName) and Trace.endSection() or their async variants. It always selects the first instance of a trace section captured during a measurement. It only outputs trace sections from your package by default; to include processes outside your package, set targetPackageOnly = false.

PowerMetric
Experimental: This class is experimental.

PowerMetric captures the change in power or energy over the duration of your test for the provided power categories. Each selected category is broken down into its measurable subcomponents, and unselected categories are added to the "unselected" metric.

These metrics measure system-wide consumption, not the consumption on a per-app basis, and are limited to Pixel 6, Pixel 6 Pro, and later devices:

    power<category>Uw: the amount of power consumed over the duration of your test in this category.
    energy<category>Uws: the amount of energy transferred per unit of time for the duration of your test in this category.

Categories include the following:

    CPU
    DISPLAY
    GPU
    GPS
    MEMORY
    MACHINE_LEARNING
    NETWORK
    UNCATEGORIZED

With some categories, like CPU, it might be difficult to separate work done by other processes from work done by your own app. To minimize the interference, remove or restrict unnecessary apps and accounts.


Function & class refs:
``` measureRepeated
fun measureRepeated(
packageName: String,
metrics: List<Metric>,
compilationMode: CompilationMode = CompilationMode.DEFAULT,
startupMode: StartupMode? = null,
iterations: @IntRange(from = 1) Int,
setupBlock: MacrobenchmarkScope.() -> Unit = {},
measureBlock: MacrobenchmarkScope.() -> Unit
): Unit

This performs a macrobenchmark with the below control flow:
    resetAppCompilation()
    compile(compilationMode)
    repeat(iterations) {
        setupBlock()
        captureTraceAndMetrics {
            measureBlock()
        }
    }
```
```Metric
sealed class Metric

Known direct subclasses:
ArtMetric: Captures metrics about ART method/class compilation and initialization.
FrameTimingGfxInfoMetric: Version of FrameTimingMetric based on 'dumpsys gfxinfo' instead of trace data.
FrameTimingMetric: Metric which captures timing information from frames produced by a benchmark, such as a scrolling or animation benchmark.
PowerMetric: Captures the change of power, energy or battery charge metrics over time for specified duration.
StartupTimingMetric: Captures app startup timing metrics.
TraceMetric: Metric which captures results from a Perfetto trace with custom TraceProcessor queries.
TraceSectionMetric: Captures the time taken by named trace section - a named begin / end pair matching the provided sectionName.
```