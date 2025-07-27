package dev.serhiiyaremych.lumina.domain.usecase

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.unit.IntSize
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import java.util.Locale
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Smart Memory Manager for optimal memory allocation and pressure handling in the atlas system.
 *
 * This component manages:
 * - Dynamic memory budget allocation based on device capabilities
 * - Real-time memory pressure monitoring
 * - Atlas eviction strategies under memory pressure
 * - Graceful degradation of LOD levels
 * - Memory cleanup and garbage collection coordination
 */
@Singleton
class SmartMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceCapabilities: DeviceCapabilities
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "SmartMemoryManager"

        // Relaxed memory pressure thresholds (will be dynamically adjusted)
        private const val DEFAULT_PRESSURE_LOW = 0.7f      // Was 0.8f
        private const val DEFAULT_PRESSURE_MEDIUM = 0.85f  // Was 0.9f
        private const val DEFAULT_PRESSURE_HIGH = 0.92f    // Was 0.95f
        private const val DEFAULT_PRESSURE_CRITICAL = 0.96f // Was 0.98f

        // Relaxed memory allocation safety margins
        private const val SAFETY_MARGIN = 0.05f // 5% safety margin (was 10%)
        private const val EMERGENCY_THRESHOLD = 0.97f // 97% usage triggers emergency cleanup (was 99%)

        // System memory monitoring
        private const val MEMORY_CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
        private const val MEMORY_SNAPSHOT_LIMIT = 50 // Keep last 50 snapshots for leak detection
    }

    private val capabilities = deviceCapabilities.getCapabilities()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // Dynamic memory budget that can be adjusted at runtime
    private val baseBudgetBytes = (capabilities.memoryBudgetMB * 1024 * 1024).toLong()
    private var memoryBudgetBytes = AtomicLong((baseBudgetBytes * (1 - SAFETY_MARGIN)).toLong())

    private val currentMemoryUsage = AtomicInteger(0)
    private val atlasRegistry = ConcurrentHashMap<AtlasKey, AtlasEntry>()
    private val memorySnapshots = mutableListOf<MemorySnapshot>()
    private var lastSystemMemoryCheck = 0L
    private var lastTrimLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE

    private val _memoryPressure = MutableStateFlow(MemoryPressure.NORMAL)
    val memoryPressure: StateFlow<MemoryPressure> = _memoryPressure.asStateFlow()

    // Register for system memory callbacks
    init {
        context.registerComponentCallbacks(this)
        Log.d(TAG, "SmartMemoryManager initialized with budget: ${memoryBudgetBytes.get() / 1024 / 1024}MB")
    }

    /**
     * Memory pressure levels with enhanced detection
     */
    enum class MemoryPressure {
        NORMAL,     // < 70% usage, system healthy
        LOW,        // 70-80% usage, minor pressure
        MEDIUM,     // 80-90% usage, moderate pressure
        HIGH,       // 90-95% usage, high pressure
        CRITICAL    // > 95% usage, emergency cleanup needed
    }

    /**
     * System memory pressure information
     */
    data class SystemMemoryPressure(
        val systemUsagePercent: Float,
        val isLowMemory: Boolean,
        val isNearThreshold: Boolean,
        val backgroundAppCount: Int,
        val trimLevel: Int
    )

    /**
     * Enhanced memory pressure combining app and system metrics
     */
    data class EnhancedMemoryPressure(
        val appPressure: Float,
        val systemPressure: Float,
        val combinedPressure: Float,
        val isSystemUnderStress: Boolean
    )

    /**
     * Dynamic threshold configuration
     */
    data class DynamicThresholds(
        val low: Float,
        val medium: Float,
        val high: Float,
        val critical: Float
    )

    /**
     * Background app memory usage information
     */
    data class BackgroundMemoryUsage(
        val totalBackgroundMemoryMB: Int,
        val highMemoryAppCount: Int,
        val memoryPressureFactor: Float
    )

    /**
     * Memory snapshot for leak detection
     */
    data class MemorySnapshot(
        val timestamp: Long,
        val atlasCount: Int,
        val memoryUsage: Long
    )

    /**
     * Memory leak detection report
     */
    data class MemoryLeakReport(
        val detected: Boolean,
        val severity: Float,
        val recommendation: String
    )

    /**
     * Pre-allocation analysis result
     */
    data class PreAllocationResult(
        val predictedUsage: Long,
        val availableMemory: Long,
        val recommendedLOD: LODLevel,
        val willFit: Boolean,
        val confidence: Float
    )

    /**
     * Atlas registration key
     */
    data class AtlasKey(
        val lodLevel: LODLevel,
        val atlasSize: IntSize,
        val photosHash: Int // Hash of photo URIs for identification
    )

    /**
     * Atlas entry with metadata
     */
    data class AtlasEntry(
        val atlas: TextureAtlas,
        val memoryUsageBytes: Long,
        val lastAccessTime: Long,
        val priority: Float // Higher priority = less likely to be evicted
    )

    /**
     * Memory allocation result
     */
    data class AllocationResult(
        val success: Boolean,
        val memoryBudgetBytes: Long,
        val currentUsageBytes: Long,
        val availableBytes: Long,
        val pressureLevel: MemoryPressure,
        val recommendedLOD: LODLevel? = null
    )

    /**
     * Request memory allocation for an atlas
     */
    fun requestAtlasMemory(
        requiredMemoryBytes: Long,
        lodLevel: LODLevel,
        atlasSize: IntSize,
        priority: Float = 0.5f
    ): AllocationResult {
        updateMemoryPressureWithContext()

        val currentUsage = currentMemoryUsage.get()
        val currentBudget = memoryBudgetBytes.get()
        val availableBytes = currentBudget - currentUsage

        // Check if we have enough memory
        if (availableBytes >= requiredMemoryBytes) {
            return AllocationResult(
                success = true,
                memoryBudgetBytes = currentBudget,
                currentUsageBytes = currentUsage.toLong(),
                availableBytes = availableBytes,
                pressureLevel = _memoryPressure.value
            )
        }

        // Try to free memory by evicting lower priority atlases
        val freedMemory = attemptMemoryEviction(requiredMemoryBytes, priority)
        val newAvailable = availableBytes + freedMemory

        if (newAvailable >= requiredMemoryBytes) {
            return AllocationResult(
                success = true,
                memoryBudgetBytes = currentBudget,
                currentUsageBytes = (currentUsage - freedMemory).toLong(),
                availableBytes = newAvailable,
                pressureLevel = _memoryPressure.value
            )
        }

        // Suggest a lower LOD level that might fit
        val recommendedLOD = suggestLowerLOD(lodLevel, newAvailable)

        return AllocationResult(
            success = false,
            memoryBudgetBytes = currentBudget,
            currentUsageBytes = currentUsage.toLong(),
            availableBytes = newAvailable,
            pressureLevel = _memoryPressure.value,
            recommendedLOD = recommendedLOD
        )
    }

    /**
     * Register an atlas in the memory manager
     */
    fun registerAtlas(
        key: AtlasKey,
        atlas: TextureAtlas,
        priority: Float = 0.5f
    ) {
        val memoryUsage = calculateAtlasMemoryUsage(atlas)
        val entry = AtlasEntry(
            atlas = atlas,
            memoryUsageBytes = memoryUsage,
            lastAccessTime = System.currentTimeMillis(),
            priority = priority
        )

        atlasRegistry[key] = entry
        currentMemoryUsage.addAndGet(memoryUsage.toInt())

        Log.d(TAG, "Registered atlas: $key, memory: ${memoryUsage / 1024}KB, total: ${currentMemoryUsage.get() / 1024}KB")

        updateMemoryPressureWithContext()
        recordMemorySnapshot()
    }

    /**
     * Unregister an atlas and free its memory
     */
    fun unregisterAtlas(key: AtlasKey?) {
        atlasRegistry.remove(key)?.let { entry ->
            currentMemoryUsage.addAndGet(-entry.memoryUsageBytes.toInt())

            // Recycle the bitmap if it's not already recycled
            if (!entry.atlas.bitmap.isRecycled) {
                entry.atlas.bitmap.recycle()
            }

            Log.d(TAG, "Unregistered atlas: $key, freed: ${entry.memoryUsageBytes / 1024}KB, total: ${currentMemoryUsage.get() / 1024}KB")
        }

        updateMemoryPressureWithContext()
    }

    /**
     * Update access time for an atlas (for LRU eviction)
     */
    fun touchAtlas(key: AtlasKey) {
        atlasRegistry[key]?.let { entry ->
            atlasRegistry[key] = entry.copy(lastAccessTime = System.currentTimeMillis())
        }
    }

    /**
     * Get current memory status with enhanced system information
     */
    fun getMemoryStatus(): MemoryStatus {
        val currentUsage = currentMemoryUsage.get()
        val currentBudget = memoryBudgetBytes.get()
        val usagePercent = currentUsage.toFloat() / currentBudget.toFloat()

        return MemoryStatus(
            totalBudgetBytes = currentBudget,
            currentUsageBytes = currentUsage.toLong(),
            availableBytes = currentBudget - currentUsage,
            usagePercent = usagePercent,
            pressureLevel = _memoryPressure.value,
            registeredAtlases = atlasRegistry.size,
            deviceCapabilities = capabilities
        )
    }

    /**
     * Predictive memory usage calculation
     */
    fun preAllocateCheck(photoUris: List<Uri>, lodLevel: LODLevel): PreAllocationResult {
        val predictedUsage = predictMemoryUsage(photoUris.size, lodLevel)
        val currentBudget = calculateContextAwareBudget()
        val availableMemory = currentBudget - currentMemoryUsage.get()

        val recommendedLOD = if (predictedUsage > availableMemory) {
            suggestLowerLOD(lodLevel, availableMemory) ?: lodLevel
        } else {
            lodLevel
        }

        return PreAllocationResult(
            predictedUsage = predictedUsage,
            availableMemory = availableMemory,
            recommendedLOD = recommendedLOD,
            willFit = predictedUsage <= availableMemory,
            confidence = calculateConfidence(photoUris.size, lodLevel)
        )
    }

    /**
     * Detect potential memory leaks
     */
    fun detectMemoryLeak(): MemoryLeakReport? {
        if (memorySnapshots.size < 10) return null

        val recentSnapshots = memorySnapshots.takeLast(10)
        val atlasCountStable = recentSnapshots.map { it.atlasCount }.distinct().size <= 2
        val memoryIncreasing = recentSnapshots.zipWithNext().all { (a, b) -> b.memoryUsage > a.memoryUsage }

        return if (atlasCountStable && memoryIncreasing) {
            MemoryLeakReport(
                detected = true,
                severity = calculateLeakSeverity(recentSnapshots),
                recommendation = "Consider bitmap recycling or atlas cleanup"
            )
        } else {
            null
        }
    }

    /**
     * Recalculate memory budget based on current conditions
     */
    fun recalculateBudget() {
        val newBudget = calculateContextAwareBudget()
        val oldBudget = memoryBudgetBytes.get()

        if (newBudget != oldBudget) {
            memoryBudgetBytes.set(newBudget)

            // Trigger pressure update with new budget
            updateMemoryPressureWithContext()

            Log.i(TAG, "Memory budget updated: ${oldBudget / 1024 / 1024}MB -> ${newBudget / 1024 / 1024}MB")
        }
    }

    // Protected atlases that should not be cleaned up
    private val protectedAtlases = ConcurrentHashMap<AtlasKey, Boolean>()

    /**
     * Protect atlases from emergency cleanup
     */
    fun protectAtlases(atlasKeys: Set<AtlasKey>) {
        protectedAtlases.clear()
        for (key in atlasKeys) {
            protectedAtlases[key] = true
        }
        Log.d(TAG, "Protected ${atlasKeys.size} atlases from cleanup")
    }

    /**
     * Add additional atlases to protection (additive)
     */
    fun addProtectedAtlases(atlasKeys: Set<AtlasKey>) {
        for (key in atlasKeys) {
            protectedAtlases[key] = true
        }
        Log.d(TAG, "Added ${atlasKeys.size} atlases to protection (total: ${protectedAtlases.size})")
    }

    /**
     * Force emergency cleanup - only evicts unprotected atlases
     */
    fun emergencyCleanup() {
        Log.w(TAG, "Emergency cleanup triggered!")

        // Only evict atlases that are not protected
        val unprotectedEntries = atlasRegistry.entries.filter { (key, _) ->
            !protectedAtlases.containsKey(key)
        }

        if (unprotectedEntries.isEmpty()) {
            Log.w(TAG, "No unprotected atlases available for cleanup - all ${atlasRegistry.size} are protected")
            return
        }

        // Evict 1/3 of the unprotected atlases, prioritizing lower priority ones (was 1/2)
        val sortedEntries = unprotectedEntries.sortedBy { it.value.priority }
        val toEvict = sortedEntries.take(maxOf(1, sortedEntries.size / 3))

        Log.d(TAG, "Evicting ${toEvict.size} unprotected atlases out of ${atlasRegistry.size} total (${protectedAtlases.size} protected)")

        for ((key, _) in toEvict) {
            unregisterAtlas(key)
        }

        // Force garbage collection
        System.gc()

        updateMemoryPressureWithContext()
        Log.i(TAG, "Emergency cleanup completed. Memory usage: ${currentMemoryUsage.get() / 1024}KB")
    }

    /**
     * Calculate memory usage of an atlas
     */
    private fun calculateAtlasMemoryUsage(atlas: TextureAtlas): Long {
        val bitmap = atlas.bitmap
        return if (bitmap.isRecycled) {
            0L
        } else {
            bitmap.allocationByteCount.toLong()
        }
    }

    /**
     * Attempt to free memory by evicting lower priority atlases
     */
    private fun attemptMemoryEviction(requiredMemory: Long, newPriority: Float): Long {
        val candidates = atlasRegistry.entries
            .filter { it.value.priority < newPriority }
            .sortedWith(compareBy<Map.Entry<AtlasKey, AtlasEntry>> { it.value.priority }
                .thenBy { it.value.lastAccessTime })

        var freedMemory = 0L
        for ((key, entry) in candidates) {
            if (freedMemory >= requiredMemory) break

            unregisterAtlas(key)
            freedMemory += entry.memoryUsageBytes
        }

        return freedMemory
    }

    /**
     * Suggest a lower LOD level that might fit in available memory
     */
    private fun suggestLowerLOD(currentLOD: LODLevel, availableMemory: Long): LODLevel? {
        val lowerLevels = LODLevel.getAllLevels().filter { it.level < currentLOD.level }

        for (level in lowerLevels.reversed()) {
            val estimatedMemory = LODLevel.getMemoryUsageKB(level) * 1024L
            if (estimatedMemory <= availableMemory) {
                return level
            }
        }

        return null
    }

    /**
     * Enhanced memory pressure update with system context
     */
    private fun updateMemoryPressureWithContext() {
        val enhancedPressure = calculateEnhancedMemoryPressure()
        val dynamicThresholds = getDynamicThresholds()

        val newPressure = when {
            enhancedPressure.combinedPressure >= dynamicThresholds.critical -> MemoryPressure.CRITICAL
            enhancedPressure.combinedPressure >= dynamicThresholds.high -> MemoryPressure.HIGH
            enhancedPressure.combinedPressure >= dynamicThresholds.medium -> MemoryPressure.MEDIUM
            enhancedPressure.combinedPressure >= dynamicThresholds.low -> MemoryPressure.LOW
            else -> MemoryPressure.NORMAL
        }

        if (newPressure != _memoryPressure.value) {
            _memoryPressure.value = newPressure
            Log.d(TAG, "Memory pressure updated: $newPressure (combined: ${"%.1f".format(enhancedPressure.combinedPressure * 100)}%)")

            // TEMPORARILY DISABLED: Trigger emergency cleanup if critical
            // if (newPressure == MemoryPressure.CRITICAL) {
            //     emergencyCleanup()
            // }
        }
    }

    /**
     * Get system-wide memory pressure information
     */
    private fun getSystemMemoryPressure(): SystemMemoryPressure {
        val currentTime = System.currentTimeMillis()

        // Throttle system memory checks to avoid overhead
        if (currentTime - lastSystemMemoryCheck < MEMORY_CHECK_INTERVAL_MS) {
            // Use cached values for frequent calls
            return SystemMemoryPressure(
                systemUsagePercent = 0.5f, // Conservative estimate
                isLowMemory = false,
                isNearThreshold = false,
                backgroundAppCount = 5, // Conservative estimate
                trimLevel = lastTrimLevel
            )
        }

        lastSystemMemoryCheck = currentTime

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val backgroundAppCount = getBackgroundAppCount()

        return SystemMemoryPressure(
            systemUsagePercent = 1.0f - (memInfo.availMem.toFloat() / memInfo.totalMem),
            isLowMemory = memInfo.lowMemory,
            isNearThreshold = memInfo.availMem < memInfo.threshold * 1.5f,
            backgroundAppCount = backgroundAppCount,
            trimLevel = lastTrimLevel
        )
    }

    /**
     * Calculate enhanced memory pressure combining app and system metrics
     */
    private fun calculateEnhancedMemoryPressure(): EnhancedMemoryPressure {
        val appPressure = currentMemoryUsage.get().toFloat() / memoryBudgetBytes.get().toFloat()
        val systemPressure = getSystemMemoryPressure()

        // Weight: 60% app budget, 40% system pressure
        val combinedPressure = (appPressure * 0.6f) + (systemPressure.systemUsagePercent * 0.4f)

        return EnhancedMemoryPressure(
            appPressure = appPressure,
            systemPressure = systemPressure.systemUsagePercent,
            combinedPressure = combinedPressure,
            isSystemUnderStress = systemPressure.isLowMemory || systemPressure.isNearThreshold
        )
    }

    /**
     * Get dynamic thresholds based on system state
     */
    private fun getDynamicThresholds(): DynamicThresholds {
        val systemPressure = getSystemMemoryPressure()

        val isSystemUnderStress = systemPressure.isLowMemory || systemPressure.isNearThreshold

        return when {
            // High-end device with system stress - more relaxed
            capabilities.memoryTier == DeviceCapabilities.MemoryTier.HIGH && isSystemUnderStress ->
                DynamicThresholds(0.65f, 0.78f, 0.88f, 0.93f)  // Was: 0.7f, 0.8f, 0.9f, 0.95f

            // Low-end device under normal conditions - more permissive
            capabilities.memoryTier == DeviceCapabilities.MemoryTier.MINIMAL && !isSystemUnderStress ->
                DynamicThresholds(0.55f, 0.7f, 0.82f, 0.9f)   // Was: 0.6f, 0.75f, 0.85f, 0.92f

            // Background apps consuming memory - less aggressive
            systemPressure.backgroundAppCount > 10 ->
                DynamicThresholds(0.6f, 0.75f, 0.85f, 0.92f)  // Was: 0.65f, 0.78f, 0.88f, 0.94f

            else ->
                DynamicThresholds(DEFAULT_PRESSURE_LOW, DEFAULT_PRESSURE_MEDIUM, DEFAULT_PRESSURE_HIGH, DEFAULT_PRESSURE_CRITICAL)
        }
    }

    /**
     * Calculate context-aware memory budget
     */
    private fun calculateContextAwareBudget(): Long {
        val backgroundUsage = getBackgroundAppMemoryUsage()
        val systemPressure = getSystemMemoryPressure()

        val isSystemUnderStress = systemPressure.isLowMemory || systemPressure.isNearThreshold

        val contextFactor = when {
            backgroundUsage.totalBackgroundMemoryMB > 3000 -> 0.8f  // Heavy background usage (was 2000 -> 0.7f)
            backgroundUsage.highMemoryAppCount > 8 -> 0.85f         // Many memory-intensive apps (was 5 -> 0.8f)
            isSystemUnderStress -> 0.85f                            // System under stress (was 0.75f)
            else -> 1.0f
        }

        return (baseBudgetBytes * contextFactor * (1 - SAFETY_MARGIN)).toLong()
    }

    /**
     * Get background app memory usage information
     */
    private fun getBackgroundAppMemoryUsage(): BackgroundMemoryUsage {
        val runningApps = activityManager.runningAppProcesses ?: return BackgroundMemoryUsage(0, 0, 0.0f)

        var backgroundMemoryMB = 0
        var highMemoryApps = 0

        for (processInfo in runningApps) {
            if (processInfo.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                try {
                    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))[0]
                    val memoryMB = memoryInfo.totalPss / 1024

                    backgroundMemoryMB += memoryMB
                    if (memoryMB > 100) highMemoryApps++
                } catch (e: Exception) {
                    // Ignore errors accessing process memory info
                }
            }
        }

        return BackgroundMemoryUsage(
            totalBackgroundMemoryMB = backgroundMemoryMB,
            highMemoryAppCount = highMemoryApps,
            memoryPressureFactor = (backgroundMemoryMB / 1000.0f).coerceAtMost(1.0f)
        )
    }

    /**
     * Get count of background apps
     */
    private fun getBackgroundAppCount(): Int {
        val runningApps = activityManager.runningAppProcesses ?: return 0
        return runningApps.count { it.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND }
    }

    /**
     * Predict memory usage for given photos and LOD level
     */
    private fun predictMemoryUsage(photoCount: Int, lodLevel: LODLevel): Long {
        val avgPhotoSize = LODLevel.getMemoryUsageKB(lodLevel) * 1024L
        val estimatedAtlasCount = ceil(photoCount.toFloat() / LODLevel.getAtlasCapacity2K(lodLevel))
        val packingOverhead = 1.2f // 20% overhead for packing inefficiency

        return (photoCount * avgPhotoSize * packingOverhead).toLong()
    }

    /**
     * Calculate confidence level for memory prediction
     */
    private fun calculateConfidence(photoCount: Int, lodLevel: LODLevel): Float {
        return when {
            photoCount < 10 -> 0.9f  // High confidence for small sets
            photoCount < 100 -> 0.8f // Good confidence for medium sets
            photoCount < 1000 -> 0.7f // Moderate confidence for large sets
            else -> 0.6f  // Lower confidence for very large sets
        }
    }

    /**
     * Record memory snapshot for leak detection
     */
    private fun recordMemorySnapshot() {
        memorySnapshots.add(MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            atlasCount = atlasRegistry.size,
            memoryUsage = currentMemoryUsage.get().toLong()
        ))

        // Keep only last snapshots
        if (memorySnapshots.size > MEMORY_SNAPSHOT_LIMIT) {
            memorySnapshots.removeAt(0)
        }
    }

    /**
     * Calculate memory leak severity
     */
    private fun calculateLeakSeverity(snapshots: List<MemorySnapshot>): Float {
        if (snapshots.size < 2) return 0.0f

        val memoryIncrease = snapshots.last().memoryUsage - snapshots.first().memoryUsage
        val timeSpan = snapshots.last().timestamp - snapshots.first().timestamp

        // Calculate leak rate (bytes per minute)
        val leakRateBytesPerMin = (memoryIncrease * 60000) / timeSpan

        return when {
            leakRateBytesPerMin > 20 * 1024 * 1024 -> 1.0f  // > 20MB/min = critical (was 10MB/min)
            leakRateBytesPerMin > 10 * 1024 * 1024 -> 0.8f  // > 10MB/min = high (was 5MB/min)
            leakRateBytesPerMin > 3 * 1024 * 1024 -> 0.6f   // > 3MB/min = medium (was 1MB/min)
            leakRateBytesPerMin > 1 * 1024 * 1024 -> 0.4f   // > 1MB/min = low (was 512KB/min)
            else -> 0.2f  // < 1MB/min = minimal
        }
    }

    // ComponentCallbacks2 implementation
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // No-op
    }

    override fun onLowMemory() {
        Log.w(TAG, "System onLowMemory() callback received")
        lastTrimLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE

        // Trigger emergency cleanup
        emergencyCleanup()

        // Force garbage collection
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        lastTrimLevel = level

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.d(TAG, "System memory pressure: MODERATE")
                // Release some unneeded resources
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "System memory pressure: LOW")
                // Release some resources - less aggressive
                val toEvict = atlasRegistry.size / 6  // Evict ~17% of atlases (was 25%)
                evictOldestAtlases(toEvict)
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.e(TAG, "System memory pressure: CRITICAL")
                // Release as many resources as possible
                emergencyCleanup()
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "UI hidden, releasing some resources")
                // App moved to background, release UI-related resources - less aggressive
                val toEvict = atlasRegistry.size / 3  // Evict ~33% of atlases (was 50%)
                evictOldestAtlases(toEvict)
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "App in background with memory pressure level: $level")
                // Release most resources
                emergencyCleanup()
            }
        }

        // Recalculate budget based on new system state
        recalculateBudget()
    }

    /**
     * Evict oldest atlases based on last access time
     */
    private fun evictOldestAtlases(count: Int) {
        if (count <= 0) return

        val sortedEntries = atlasRegistry.entries
            .filter { !protectedAtlases.containsKey(it.key) }
            .sortedBy { it.value.lastAccessTime }
            .take(count)

        Log.d(TAG, "Evicting $count oldest atlases")

        for ((key, _) in sortedEntries) {
            unregisterAtlas(key)
        }
    }

    /**
     * Current memory status
     */
    data class MemoryStatus(
        val totalBudgetBytes: Long,
        val currentUsageBytes: Long,
        val availableBytes: Long,
        val usagePercent: Float,
        val pressureLevel: MemoryPressure,
        val registeredAtlases: Int,
        val deviceCapabilities: DeviceCapabilities.Capabilities
    ) {
        override fun toString(): String {
            return "MemoryStatus(" +
                    "totalBudget=${formatBytes(totalBudgetBytes)}, " +
                    "currentUsage=${formatBytes(currentUsageBytes)}, " +
                    "available=${formatBytes(availableBytes)}, " +
                    "usagePercent=${String.format(Locale.US, "%.1f", usagePercent * 100)}%, " +
                    "pressure=$pressureLevel, " +
                    "atlases=$registeredAtlases, " +
                    "capabilities=$deviceCapabilities" +
                    ")"
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }

    /**
     * Cleanup resources when manager is destroyed
     */
    fun cleanup() {
        try {
            context.unregisterComponentCallbacks(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering component callbacks", e)
        }

        // Clear all atlases
        atlasRegistry.clear()
        memorySnapshots.clear()
        protectedAtlases.clear()

        Log.d(TAG, "SmartMemoryManager cleanup completed")
    }
}
