Control your app from Macrobenchmark

Unlike most Android UI tests, Macrobenchmark tests run in a separate process from the app itself. This is necessary to enable things like stopping the app process and compiling from DEX bytecode to machine code.

You can drive your app's state using the UIAutomator library or other mechanisms that can control the target app from the test process. You can't use Espresso or ActivityScenario for Macrobenchmark because they expect to run in a shared process with the app.

The following example finds a RecyclerView using its resource ID and scrolls down several times:

@Test
fun scrollList() {
benchmarkRule.measureRepeated(
// ...
setupBlock = {
// Before starting to measure, navigate to the UI to be measured
val intent = Intent("$packageName.RECYCLER_VIEW_ACTIVITY")
startActivityAndWait(intent)
}
) {
val recycler = device.findObject(By.res(packageName, "recycler"))
// Set gesture margin to avoid triggering gesture navigation
// with input events from automation.
recycler.setGestureMargin(device.displayWidth / 5)

        // Scroll down several times
        repeat(3) { recycler.fling(Direction.DOWN) }
    }
}


Your benchmark doesn't have to scroll the UI. Instead, it can run an animation, for example. It also doesn't need to use UI Automator specifically. It collects performance metrics as long as frames are being produced by the view system, including frames produced by Jetpack Compose.
Note: When accessing UI objects, specify the packageName, because the tests run in a separate process.
Navigate to internal parts of the app

Sometimes you want to benchmark parts of your app that aren't directly accessible from outside. This might be, for example, accessing inner Activities that are marked with exported=false, navigating to a Fragment, or swiping some part of your UI away. The benchmarks need to manually navigate to these parts of the app like a user.

To manually navigate, change the code inside setupBlock{} to contain the effect you want, such as button tap or swipe. Your measureBlock{} contains only the UI manipulation you want to actually benchmark:

@Test
fun nonExportedActivityScrollList() {
benchmarkRule.measureRepeated(
// ...
setupBlock = setupBenchmark()
) {
// ...
}
}

private fun setupBenchmark(): MacrobenchmarkScope.() -> Unit = {
// Before starting to measure, navigate to the UI to be measured
startActivityAndWait()

    // click a button to launch the target activity.
    // While we use button text  here to find the button, you could also use
    // accessibility info or resourceId.
    val selector = By.text("RecyclerView")
    if (!device.wait(Until.hasObject(selector), 5_500)) {
        fail("Could not find resource in time")
    }
    val launchRecyclerActivity = device.findObject(selector)
    launchRecyclerActivity.click()

    // wait until the activity is shown
    device.wait(
        Until.hasObject(By.clazz("$packageName.NonExportedRecyclerActivity")),
        TimeUnit.SECONDS.toMillis(10)
    )
}

Sample:
import android.content.Intent
import android.graphics.Point
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import com.example.macrobenchmark.benchmark.util.DEFAULT_ITERATIONS
import com.example.macrobenchmark.benchmark.util.TARGET_PACKAGE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
@get:Rule
val benchmarkRule = MacrobenchmarkRule()

    // [START macrobenchmark_control_your_app]
    @Test
    fun scrollList() {
        benchmarkRule.measureRepeated(
            // [START_EXCLUDE]
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            // Try switching to different compilation modes to see the effect
            // it has on frame timing metrics.
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM, // restarts activity each iteration
            iterations = DEFAULT_ITERATIONS,
            // [END_EXCLUDE]
            setupBlock = {
                // Before starting to measure, navigate to the UI to be measured
                val intent = Intent("$packageName.RECYCLER_VIEW_ACTIVITY")
                startActivityAndWait(intent)
            }
        ) {
            val recycler = device.findObject(By.res(packageName, "recycler"))
            // Set gesture margin to avoid triggering gesture navigation
            // with input events from automation.
            recycler.setGestureMargin(device.displayWidth / 5)

            // Scroll down several times
            repeat(3) { recycler.fling(Direction.DOWN) }
        }
    }
    // [END macrobenchmark_control_your_app]

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun scrollComposeList() {
        benchmarkRule.measureRepeated(
            // [START_EXCLUDE]
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                // Measure custom trace sections by name EntryRow (which is added to the EntryRow composable).
                // Mode.Sum measure combined duration and also how many times it occurred in the trace.
                // This way, you can estimate whether a composable recomposes more than it should.
                TraceSectionMetric("EntryRowCustomTrace", TraceSectionMetric.Mode.Sum),
                // This trace section takes into account the SQL wildcard character %,
                // which can find trace sections without knowing the full name.
                // This way, you can measure composables produced by the composition tracing
                // and measure how long they took and how many times they recomposed.
                // WARNING: This metric only shows results when running with composition tracing, otherwise it won't be visible in the outputs.
                TraceSectionMetric("%EntryRow (%", TraceSectionMetric.Mode.Sum),
            ),
            // Try switching to different compilation modes to see the effect
            // it has on frame timing metrics.
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM, // restarts activity each iteration
            iterations = DEFAULT_ITERATIONS,
            // [END_EXCLUDE]
            setupBlock = {
                // Before starting to measure, navigate to the UI to be measured
                val intent = Intent("$packageName.COMPOSE_ACTIVITY")
                startActivityAndWait(intent)
            }
        ) {
            /**
             * Compose does not have view IDs so we cannot directly access composables from UiAutomator.
             * To access a composable we need to set:
             * 1) Modifier.semantics { testTagsAsResourceId = true } once, high in the compose hierarchy
             * 2) Add Modifier.testTag("someIdentifier") to all of the composables you want to access
             *
             * Once done that, we can access the composable using By.res("someIdentifier")
             */
            val column = device.findObject(By.res("myLazyColumn"))

            // Set gesture margin to avoid triggering gesture navigation
            // with input events from automation.
            column.setGestureMargin(device.displayWidth / 5)

            // Scroll down several times
            repeat(1) { column.drag(Point(column.visibleCenter.x, column.visibleBounds.top)) }
        }
    }
}
