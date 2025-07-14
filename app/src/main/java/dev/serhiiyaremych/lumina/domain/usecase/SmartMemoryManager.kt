package dev.serhiiyaremych.lumina.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.unit.IntSize
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import java.util.Locale
import javax.inject.Singleton

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
    private val deviceCapabilities: DeviceCapabilities
) {

    companion object {
        private const val TAG = "SmartMemoryManager"

        // Memory pressure thresholds
        private const val PRESSURE_LOW = 0.8f
        private const val PRESSURE_MEDIUM = 0.9f
        private const val PRESSURE_HIGH = 0.98f

        // Memory allocation safety margins
        private const val SAFETY_MARGIN = 0.1f // 10% safety margin
        private const val EMERGENCY_THRESHOLD = 0.99f // 99% usage triggers emergency cleanup
    }

    private val capabilities = deviceCapabilities.getCapabilities()
    private val memoryBudgetBytes = (capabilities.memoryBudgetMB * 1024 * 1024 * (1 - SAFETY_MARGIN)).toLong()
    private val currentMemoryUsage = AtomicInteger(0)
    private val atlasRegistry = ConcurrentHashMap<AtlasKey, AtlasEntry>()

    private val _memoryPressure = MutableStateFlow(MemoryPressure.NORMAL)
    val memoryPressure: StateFlow<MemoryPressure> = _memoryPressure.asStateFlow()

    /**
     * Memory pressure levels
     */
    enum class MemoryPressure {
        NORMAL,     // < 70% usage
        LOW,        // 70-80% usage
        MEDIUM,     // 80-90% usage
        HIGH,       // 90-95% usage
        CRITICAL    // > 95% usage
    }

    /**
     * Atlas registration key
     */
    data class AtlasKey(
        val lodLevel: Int,
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
        updateMemoryPressure()

        val currentUsage = currentMemoryUsage.get()
        val availableBytes = memoryBudgetBytes - currentUsage

        // Check if we have enough memory
        if (availableBytes >= requiredMemoryBytes) {
            return AllocationResult(
                success = true,
                memoryBudgetBytes = memoryBudgetBytes,
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
                memoryBudgetBytes = memoryBudgetBytes,
                currentUsageBytes = (currentUsage - freedMemory).toLong(),
                availableBytes = newAvailable,
                pressureLevel = _memoryPressure.value
            )
        }

        // Suggest a lower LOD level that might fit
        val recommendedLOD = suggestLowerLOD(lodLevel, newAvailable)

        return AllocationResult(
            success = false,
            memoryBudgetBytes = memoryBudgetBytes,
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

        updateMemoryPressure()
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

        updateMemoryPressure()
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
     * Get current memory status
     */
    fun getMemoryStatus(): MemoryStatus {
        val currentUsage = currentMemoryUsage.get()
        val usagePercent = currentUsage.toFloat() / memoryBudgetBytes.toFloat()

        return MemoryStatus(
            totalBudgetBytes = memoryBudgetBytes,
            currentUsageBytes = currentUsage.toLong(),
            availableBytes = memoryBudgetBytes - currentUsage,
            usagePercent = usagePercent,
            pressureLevel = _memoryPressure.value,
            registeredAtlases = atlasRegistry.size,
            deviceCapabilities = capabilities
        )
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

        // Evict half of the unprotected atlases, prioritizing lower priority ones
        val sortedEntries = unprotectedEntries.sortedBy { it.value.priority }
        val toEvict = sortedEntries.take(maxOf(1, sortedEntries.size / 2))

        Log.d(TAG, "Evicting ${toEvict.size} unprotected atlases out of ${atlasRegistry.size} total (${protectedAtlases.size} protected)")

        for ((key, _) in toEvict) {
            unregisterAtlas(key)
        }

        // Force garbage collection
        System.gc()

        updateMemoryPressure()
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
     * Update memory pressure based on current usage
     */
    private fun updateMemoryPressure() {
        val currentUsage = currentMemoryUsage.get()
        val usagePercent = currentUsage.toFloat() / memoryBudgetBytes.toFloat()

        val newPressure = when {
            usagePercent >= EMERGENCY_THRESHOLD -> MemoryPressure.CRITICAL
            usagePercent >= PRESSURE_HIGH -> MemoryPressure.HIGH
            usagePercent >= PRESSURE_MEDIUM -> MemoryPressure.MEDIUM
            usagePercent >= PRESSURE_LOW -> MemoryPressure.LOW
            else -> MemoryPressure.NORMAL
        }

        if (newPressure != _memoryPressure.value) {
            _memoryPressure.value = newPressure
            Log.d(TAG, "Memory pressure changed to: $newPressure (${(usagePercent * 100).toInt()}%)")

            // Trigger emergency cleanup if critical
            if (newPressure == MemoryPressure.CRITICAL) {
                emergencyCleanup()
            }
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
}
