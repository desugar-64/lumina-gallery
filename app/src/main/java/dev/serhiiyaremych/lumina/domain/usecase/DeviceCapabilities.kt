package dev.serhiiyaremych.lumina.domain.usecase

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.unit.IntSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects device hardware capabilities for optimal atlas sizing and memory management.
 *
 * This component analyzes device specifications to determine:
 * - Maximum supported atlas sizes (2K/4K/8K)
 * - Memory capacity and allocation budgets
 * - Performance tier classification
 * - GPU texture size limits
 */
@Singleton
class DeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Atlas size constants
        private const val ATLAS_2K = 2048
        private const val ATLAS_4K = 4096
        private const val ATLAS_8K = 8192

        // Memory tier thresholds (in MB)
        private const val MEMORY_TIER_HIGH = 8 * 1024 // 8GB
        private const val MEMORY_TIER_MEDIUM = 6 * 1024 // 6GB
        private const val MEMORY_TIER_LOW = 4 * 1024 // 4GB
        private const val MEMORY_TIER_MINIMAL = 3 * 1024 // 3GB

        // Atlas memory budgets (in MB)
        private const val BUDGET_HIGH = 400
        private const val BUDGET_MEDIUM = 300
        private const val BUDGET_LOW = 200
        private const val BUDGET_MINIMAL = 100
    }

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Device capability information
     */
    data class Capabilities(
        val memoryTier: MemoryTier,
        val maxAtlasSize: IntSize,
        val memoryBudgetMB: Int,
        val isLowRamDevice: Boolean,
        val totalMemoryMB: Int,
        val androidVersion: Int,
        val performanceTier: PerformanceTier
    )

    /**
     * Memory tier classification based on available RAM
     */
    enum class MemoryTier(val budgetMB: Int) {
        HIGH(BUDGET_HIGH), // 8GB+ devices
        MEDIUM(BUDGET_MEDIUM), // 6GB+ devices
        LOW(BUDGET_LOW), // 4GB+ devices
        MINIMAL(BUDGET_MINIMAL) // 3GB devices
    }

    /**
     * Performance tier classification combining memory and Android version
     */
    enum class PerformanceTier {
        HIGH, // High-end devices with latest Android
        MEDIUM, // Mid-range devices
        LOW // Budget devices or older Android versions
    }

    /**
     * Get comprehensive device capabilities for atlas system optimization
     */
    fun getCapabilities(): Capabilities {
        val memoryInfo = getMemoryInfo()
        val memoryTier = classifyMemoryTier(memoryInfo.totalMemoryMB)
        val maxAtlasSize = getMaxAtlasSize(memoryTier)
        val performanceTier = classifyPerformanceTier(memoryTier, Build.VERSION.SDK_INT)

        return Capabilities(
            memoryTier = memoryTier,
            maxAtlasSize = maxAtlasSize,
            memoryBudgetMB = memoryTier.budgetMB,
            isLowRamDevice = activityManager.isLowRamDevice,
            totalMemoryMB = memoryInfo.totalMemoryMB,
            androidVersion = Build.VERSION.SDK_INT,
            performanceTier = performanceTier
        )
    }

    /**
     * Get current memory information
     */
    fun getMemoryInfo(): MemoryInfo {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMemoryMB = (memInfo.totalMem / (1024 * 1024)).toInt()
        val availableMemoryMB = (memInfo.availMem / (1024 * 1024)).toInt()
        val threshold = (memInfo.threshold / (1024 * 1024)).toInt()

        return MemoryInfo(
            totalMemoryMB = totalMemoryMB,
            availableMemoryMB = availableMemoryMB,
            lowMemoryThresholdMB = threshold,
            isLowMemory = memInfo.lowMemory,
            isLowRamDevice = activityManager.isLowRamDevice
        )
    }

    /**
     * Get maximum supported atlas size based on device capabilities
     */
    private fun getMaxAtlasSize(memoryTier: MemoryTier): IntSize {
        // Get maximum bitmap size based on Android version capabilities
        // Since minSdk is 29 (Android 10), all devices support at least 4K textures
        val maxCanvasSize = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> ATLAS_8K // Android 11+
            else -> ATLAS_4K // Android 10+ (guaranteed since minSdk = 29)
        }

        // Determine optimal atlas size based on memory tier and hardware limits
        val optimalSize = DeviceCapabilityComposer.selectOptimalAtlasSize(memoryTier)

        // Ensure we don't exceed hardware limits
        val finalSize = optimalSize.coerceAtMost(maxCanvasSize)

        return IntSize(finalSize, finalSize)
    }

    /**
     * Classify device memory tier based on total RAM
     */
    private fun classifyMemoryTier(totalMemoryMB: Int): MemoryTier = when {
        totalMemoryMB >= MEMORY_TIER_HIGH -> MemoryTier.HIGH
        totalMemoryMB >= MEMORY_TIER_MEDIUM -> MemoryTier.MEDIUM
        totalMemoryMB >= MEMORY_TIER_LOW -> MemoryTier.LOW
        else -> MemoryTier.MINIMAL
    }

    /**
     * Classify device performance tier based on memory and Android version
     */
    private fun classifyPerformanceTier(memoryTier: MemoryTier, androidVersion: Int): PerformanceTier = when {
        memoryTier == MemoryTier.HIGH && androidVersion >= Build.VERSION_CODES.TIRAMISU -> PerformanceTier.HIGH
        memoryTier in listOf(MemoryTier.HIGH, MemoryTier.MEDIUM) && androidVersion >= Build.VERSION_CODES.Q -> PerformanceTier.MEDIUM
        else -> PerformanceTier.LOW
    }

    /**
     * Check if device supports a specific atlas size
     */
    fun supportsAtlasSize(atlasSize: IntSize): Boolean {
        val capabilities = getCapabilities()
        return atlasSize.width <= capabilities.maxAtlasSize.width &&
            atlasSize.height <= capabilities.maxAtlasSize.height
    }

    /**
     * Get recommended atlas sizes for this device in order of preference
     */
    fun getRecommendedAtlasSizes(): List<IntSize> {
        val maxSize = getCapabilities().maxAtlasSize
        return DeviceCapabilityComposer.buildRecommendedAtlasSizes(maxSize)
    }

    /**
     * Current memory information
     */
    data class MemoryInfo(
        val totalMemoryMB: Int,
        val availableMemoryMB: Int,
        val lowMemoryThresholdMB: Int,
        val isLowMemory: Boolean,
        val isLowRamDevice: Boolean
    ) {
        /**
         * Calculate memory pressure as percentage (0.0 = no pressure, 1.0 = critical)
         */
        val memoryPressure: Float
            get() = DeviceCapabilityComposer.calculateMemoryPressure(availableMemoryMB, totalMemoryMB)
    }
}

/**
 * Pure functions for device capability computation - extracted from DeviceCapabilities
 */
object DeviceCapabilityComposer {
    private const val ATLAS_2K = 2048
    private const val ATLAS_4K = 4096
    private const val ATLAS_8K = 8192

    /**
     * Pure function to select optimal atlas size based on memory tier
     */
    fun selectOptimalAtlasSize(memoryTier: DeviceCapabilities.MemoryTier): Int = when (memoryTier) {
        DeviceCapabilities.MemoryTier.HIGH -> ATLAS_8K
        DeviceCapabilities.MemoryTier.MEDIUM -> ATLAS_4K
        DeviceCapabilities.MemoryTier.LOW -> ATLAS_4K
        DeviceCapabilities.MemoryTier.MINIMAL -> ATLAS_2K
    }

    /**
     * Pure function to calculate memory pressure percentage
     */
    fun calculateMemoryPressure(availableMemoryMB: Int, totalMemoryMB: Int): Float = if (availableMemoryMB > 0) {
        (1.0f - (availableMemoryMB.toFloat() / totalMemoryMB.toFloat())).coerceIn(0.0f, 1.0f)
    } else {
        1.0f
    }

    /**
     * Pure function to build recommended atlas sizes without mutable state
     */
    fun buildRecommendedAtlasSizes(maxSize: IntSize): List<IntSize> = listOfNotNull(
        if (maxSize.width >= ATLAS_8K) IntSize(ATLAS_8K, ATLAS_8K) else null,
        if (maxSize.width >= ATLAS_4K) IntSize(ATLAS_4K, ATLAS_4K) else null,
        IntSize(ATLAS_2K, ATLAS_2K) // Always supported
    )
}
