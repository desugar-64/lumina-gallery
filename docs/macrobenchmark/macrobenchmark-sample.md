Create a macrobenchmark class

Benchmark testing is provided through the MacrobenchmarkRule JUnit4 rule API in the Macrobenchmark library. It contains a measureRepeated method that lets you specify various conditions on how to run and benchmark the target app.

You need to at least specify the packageName of the target app, what metrics you want to measure and how many iterations the benchmark must run.
Kotlin
Java

@LargeTest
@RunWith(AndroidJUnit4::class)
class SampleStartupBenchmark {
@get:Rule
val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = DEFAULT_ITERATIONS,
        setupBlock = {
            // Press home button before each run to ensure the starting activity isn't visible.
            pressHome()
        }
    ) {
        // starts default launch activity
        startActivityAndWait()
    }
}


For all the options on customizing your benchmark, see Customize the benchmarks section.
Run the benchmark
Note: Use Android 14 (API level 34) or later to persist state when benchmarking. The Macrobenchmark library fully resets the compilation state for each compile, which on Android versions earlier than 14 require reinstalling the APK. As a workaround, control app compilation separately and skip compilation using CompilationMode.Ignore.

Run the test from within Android Studio to measure the performance of your app on your device. You can run the benchmarks the same way you run any other @Test using the gutter action next to your test class or method, as shown in the figure 5.

You can also run all benchmarks in a Gradle module from the command line by executing the connectedCheck command:

./gradlew :macrobenchmark:connectedCheck

You can run a single test by executing the following:

./gradlew :macrobenchmark:connectedCheck -P android.testInstrumentationRunnerArguments.class=com.example.macrobenchmark.startup.SampleStartupBenchmark#startup

Note: We discourage running the benchmarks on an emulator, as they don't produce performance numbers representative of the end-user experience.

See Benchmark in Continuous Integration for information on how to run and monitor benchmarks in continuous integration.
Benchmark results

After a successful benchmark run, metrics are displayed directly in Android Studio and are output for CI usage in a JSON file. Each measured iteration captures a separate system trace. You can open these trace results by clicking on the links in the Test Results pane, as shown in the figure 6:
If the app is misconfigured—debuggable or non-profileable—Macrobenchmark returns an error rather than reporting an incorrect or incomplete measurement. You can suppress these errors with the androidx.benchmark.suppressErrors argument.

Macrobenchmark also returns errors when attempting to measure an emulator or on a low-battery device, which might compromise core availability and clock speed.
Customize the benchmarks

The measureRepeated function accepts various parameters that influence which metrics the library collects, how your app is started and compiled, or how many iterations the benchmark runs.
Capture the metrics

Benchmark results
After a successful benchmark run, metrics are displayed directly in Android Studio and are output for CI usage in a JSON file. Each measured iteration captures a separate system trace.
JSON reports and any profiling traces are also automatically copied from the device to the host. These are written on the host machine in the following location:
project_root/module/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/device_id/


Metrics are the main type of information extracted from your benchmarks. The following metrics are available:

    StartupTimingMetric
    FrameTimingMetric
    TraceSectionMetric

For more information about metrics, see [macrobenchmark-capture-metrics.md](macrobenchmark-capture-metrics.md).
Improve trace data with custom events

It can be useful to instrument your app with custom trace events, which are seen with the rest of the trace report and can help point out problems specific to your app. To learn more about creating custom trace events, see Define custom events.
CompilationMode

Macrobenchmarks can specify a CompilationMode, which defines how much of the app must be pre-compiled from DEX bytecode (the bytecode format within an APK) to machine code (similar to pre-compiled C++).

By default, Macrobenchmarks are run with CompilationMode.DEFAULT, which installs a Baseline Profile—if available—on Android 7 (API level 24) and later. If you are using Android 6 (API level 23) or earlier, the compilation mode fully compiles the APK as default system behavior.

You can install a Baseline Profile if the target app contains both a Baseline Profile and the ProfileInstaller library.

On Android 7 and later, you can customize the CompilationMode to affect the amount of on-device pre-compilation to mimic different levels of ahead-of-time (AOT) compilation or JIT caching. See CompilationMode.Full, CompilationMode.Partial, CompilationMode.None, and CompilationMode.Ignore.

This feature is built on ART compilation commands. Each benchmark clears profile data before it starts, to help ensure non-interference between benchmarks.
StartupMode

To perform an activity start, you can pass a predefined startup mode: COLD, WARM, or HOT. This parameter changes how the activity launches and the process state at the start of the test.
Warning: If StartupMode.COLD is used, the app process is killed between the execution of setupBlock and measureBlock to allow for app preparation without starting the process. If you need the process to remain active, use StartupMode.WARM, which restarts activities without restarting the process, or set startupMode to null and call killProcess() within the setupBlock.