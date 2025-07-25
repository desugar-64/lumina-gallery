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
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AtlasPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun awaitAtlasIdle(timeout: Long = 15000) {
        device.wait(Until.findObject(By.desc(BenchmarkLabels.ATLAS_STATE_IDLE)), timeout)
    }

    private fun awaitComposeIdle(timeout: Long = 4000) {
        device.wait(Until.findObject(By.desc(BenchmarkLabels.COMPOSE_STATE_IDLE)), timeout)
    }

    private fun grantPermissionsIfNeeded() {
        // Grant media permissions via ADB command
        val permissions = listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE"
        )

        permissions.forEach { permission ->
            try {
                device.executeShellCommand("pm grant $TARGET_PACKAGE $permission")
            } catch (e: Exception) {
                // Permission might not exist on this API level, continue
            }
        }
    }

    private fun handlePermissionDialogIfPresent() {
        // Wait a bit for permission dialog to appear
        Thread.sleep(1000)

        // Look for various permission dialog buttons
        val allowButtons = listOf(
            "Allow", "ALLOW",
            "Allow all", "ALLOW ALL",
            "While using the app", "WHILE USING THE APP"
        )

        for (buttonText in allowButtons) {
            val allowButton = device.findObject(By.text(buttonText))
            if (allowButton != null && allowButton.isEnabled) {
                allowButton.click()
                Thread.sleep(1000)
                break
            }
        }

        // Also try to find by resource ID (common permission dialog IDs)
        val allowByIds = listOf(
            "com.android.permissioncontroller:id/permission_allow_button",
            "android:id/button1"
        )

        for (resourceId in allowByIds) {
            val allowButton = device.findObject(By.res(resourceId))
            if (allowButton != null && allowButton.isEnabled) {
                allowButton.click()
                Thread.sleep(1000)
                break
            }
        }
    }

    // Pinch gesture methods removed - using app-based automation instead

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun atlasGenerationThroughZoomInteractions() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            // Primary optimization targets
            TraceSectionMetric(BenchmarkLabels.PHOTO_LOD_SCALE_BITMAP, mode = TraceSectionMetric.Mode.Max), // Bitmap pool optimization
            TraceSectionMetric(BenchmarkLabels.ATLAS_GENERATOR_SOFTWARE_CANVAS, mode = TraceSectionMetric.Mode.Max), // Hardware canvas optimization

            // Supporting metrics
            TraceSectionMetric(BenchmarkLabels.PHOTO_LOD_LOAD_BITMAP, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.ATLAS_GENERATOR_CREATE_ATLAS_BITMAP, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.ATLAS_MANAGER_UPDATE_VISIBLE_CELLS, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.ATLAS_MANAGER_SELECT_LOD_LEVEL, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS, mode = TraceSectionMetric.Mode.Max),

            // Disk I/O Operations (Critical for identifying bottleneck)
            TraceSectionMetric(BenchmarkLabels.PHOTO_LOD_DISK_OPEN_INPUT_STREAM, mode = TraceSectionMetric.Mode.Max),

            // Memory I/O Operations (Bitmap processing breakdown)
            TraceSectionMetric(BenchmarkLabels.PHOTO_LOD_MEMORY_DECODE_BOUNDS, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.PHOTO_LOD_MEMORY_DECODE_BITMAP, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.PHOTO_LOD_MEMORY_SAMPLE_SIZE_CALC, mode = TraceSectionMetric.Mode.Max),

            // PhotoScaler operations (Hardware-accelerated scaling)
            TraceSectionMetric(BenchmarkLabels.PHOTO_SCALER_SCALE, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.PHOTO_SCALER_CREATE_SCALED_BITMAP, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.PHOTO_SCALER_CREATE_CROPPED_BITMAP, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.PHOTO_SCALER_CALCULATE_DIMENSIONS, mode = TraceSectionMetric.Mode.Max),

            // Memory Management (Allocation/deallocation tracking)
            TraceSectionMetric(BenchmarkLabels.ATLAS_MEMORY_BITMAP_ALLOCATE, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.ATLAS_MEMORY_BITMAP_RECYCLE, mode = TraceSectionMetric.Mode.Max),

            // AtlasGenerator pipeline operations
            TraceSectionMetric(BenchmarkLabels.ATLAS_GENERATOR_GENERATE_ATLAS, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.ATLAS_GENERATOR_PROCESS_PHOTOS, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.ATLAS_GENERATOR_PACK_TEXTURES, mode = TraceSectionMetric.Mode.Max),

            // TexturePacker algorithm operations
            TraceSectionMetric(BenchmarkLabels.TEXTURE_PACKER_PACK_ALGORITHM, mode = TraceSectionMetric.Mode.Max),
            TraceSectionMetric(BenchmarkLabels.TEXTURE_PACKER_SORT_IMAGES, mode = TraceSectionMetric.Mode.Max),

            // System metrics
//            FrameTimingMetric(), // TODO: Enable for physical devices - requires GPU profiling in developer settings
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max)
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 4, // Small number for faster feedback
        setupBlock = {
            // Grant permissions before starting the app
            grantPermissionsIfNeeded()

            // Start activity with benchmark mode flags
            startActivityAndWait {
                it.putExtra(BenchmarkLabels.BENCHMARK_MODE_EXTRA, true)
                it.putExtra(BenchmarkLabels.BENCHMARK_AUTO_ZOOM, true)
            }

            // Handle any permission dialogs that might still appear
            handlePermissionDialogIfPresent()

            // Wait for gallery canvas to be ready
            device.wait(Until.hasObject(By.res(BenchmarkLabels.GALLERY_CANVAS_TEST_TAG)), 10_000)

            // Wait for initial atlas generation to complete
            awaitAtlasIdle()
        }
    ) {
        // App will automatically perform zoom interactions via LaunchedEffect
        // Just wait for the sequence to complete
        println("Waiting for automatic zoom sequence to complete...")

        // Wait for the full auto-zoom sequence (about 17 seconds total based on delays in App.kt)
        awaitAtlasIdle()

        println("Automatic zoom benchmark sequence completed")
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    @org.junit.Ignore("Temporarily disabled to focus on zoom test")
    fun atlasGenerationThroughPanInteractions() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric(BenchmarkLabels.ATLAS_MANAGER_UPDATE_VISIBLE_CELLS),
            TraceSectionMetric(BenchmarkLabels.ATLAS_GENERATOR_CREATE_ATLAS_BITMAP),
            FrameTimingMetric()
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 2,
        setupBlock = {
            // Grant permissions before starting the app
            grantPermissionsIfNeeded()

            // Start activity with benchmark mode (no auto-pan flag)
            startActivityAndWait()

            // Handle any permission dialogs that might still appear
            handlePermissionDialogIfPresent()

            device.wait(Until.hasObject(By.res(BenchmarkLabels.GALLERY_CANVAS_TEST_TAG)), 10_000)
            awaitAtlasIdle()
        }
    ) {
        val canvas = device.findObject(By.res(BenchmarkLabels.GALLERY_CANVAS_TEST_TAG))
        val centerPoint = canvas.visibleCenter
        val centerX = centerPoint.x
        val centerY = centerPoint.y

        // Pan left
        device.swipe(centerX, centerY, centerX - 300, centerY, 50)
        awaitComposeIdle()

        // Pan right
        device.swipe(centerX - 300, centerY, centerX + 300, centerY, 50)
        awaitComposeIdle()

        // Pan up
        device.swipe(centerX + 300, centerY, centerX, centerY - 300, 50)
        awaitComposeIdle()

        // Pan back to center
        device.swipe(centerX, centerY - 300, centerX, centerY, 50)
        awaitComposeIdle()
    }

    companion object {
        private const val TARGET_PACKAGE = "dev.serhiiyaremych.lumina"
    }
}
