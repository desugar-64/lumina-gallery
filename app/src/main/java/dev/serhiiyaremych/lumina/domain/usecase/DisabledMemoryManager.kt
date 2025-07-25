package dev.serhiiyaremych.lumina.domain.usecase

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.IntSize
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disabled Memory Manager - Temporarily Disabled for Testing
 *
 * This is a stub implementation that disables all memory management and purging
 * to allow the streaming atlas system to be tested without memory constraints.
 * 
 * IMPORTANT: This should only be used for development/testing. In production,
 * proper memory management should be enabled.
 *
 * Features disabled:
 * - Memory pressure monitoring
 * - Atlas eviction strategies  
 * - Emergency cleanup
 * - Memory budget enforcement
 * - Automatic purging
 */
@Singleton
class DisabledMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "DisabledMemoryManager"
    }

    // Mock memory status - always report healthy state
    private val _memoryPressure = MutableStateFlow(MemoryPressure.NORMAL)
    val memoryPressure: StateFlow<MemoryPressure> = _memoryPressure.asStateFlow()

    init {
        Log.w(TAG, "=== MEMORY MANAGEMENT DISABLED ===")
        Log.w(TAG, "This is for testing only - memory constraints are ignored!")
        Log.w(TAG, "Application may crash due to OOM - enable proper memory management for production")
    }

    /**
     * Memory pressure levels (all return NORMAL when disabled)
     */
    enum class MemoryPressure {
        NORMAL,     // Always return this when disabled
        LOW,        // Disabled
        MEDIUM,     // Disabled  
        HIGH,       // Disabled
        CRITICAL    // Disabled
    }

    /**
     * Mock memory status - always healthy
     */
    data class MemoryStatus(
        val usedBytes: Long = 0L,
        val totalBudgetBytes: Long = Long.MAX_VALUE, // Unlimited
        val availableBytes: Long = Long.MAX_VALUE,   // Unlimited
        val utilizationPercent: Float = 0.1f,        // Always low
        val pressureLevel: MemoryPressure = MemoryPressure.NORMAL, // Always normal
        val isHealthy: Boolean = true                // Always healthy
    )

    /**
     * Atlas key for registration (ignored when disabled)
     */
    data class AtlasKey(
        val lodLevel: Int,
        val atlasSize: IntSize,
        val photosHash: Int
    )

    /**
     * Get memory status - always returns healthy state
     */
    fun getMemoryStatus(): MemoryStatus {
        return MemoryStatus() // Always healthy
    }

    /**
     * Register atlas - no-op when disabled
     */
    fun registerAtlas(key: AtlasKey?, atlas: TextureAtlas) {
        Log.d(TAG, "Atlas registration ignored (memory management disabled)")
        // Do nothing - no tracking when disabled
    }

    /**
     * Unregister atlas - no-op when disabled  
     */
    fun unregisterAtlas(key: AtlasKey?) {
        Log.d(TAG, "Atlas unregistration ignored (memory management disabled)")
        // Do nothing - no tracking when disabled
    }

    /**
     * Protect atlases - no-op when disabled
     */
    fun protectAtlases(keys: Set<AtlasKey>) {
        Log.d(TAG, "Atlas protection ignored (memory management disabled)")
        // Do nothing - no protection needed when disabled
    }

    /**
     * Add protected atlases - no-op when disabled
     */
    fun addProtectedAtlases(keys: Set<AtlasKey>) {
        Log.d(TAG, "Atlas protection ignored (memory management disabled)")
        // Do nothing - no protection needed when disabled
    }

    /**
     * Emergency cleanup - no-op when disabled (may cause OOM!)
     */
    fun emergencyCleanup() {
        Log.w(TAG, "Emergency cleanup ignored - MEMORY MANAGEMENT DISABLED!")
        Log.w(TAG, "This may cause OutOfMemoryError - enable memory management for safety")
        // Do nothing - let it crash for testing
    }

    /**
     * Trim memory callback - ignored when disabled
     */
    fun onTrimMemory(level: Int) {
        Log.w(TAG, "Memory trim ignored (level=$level) - MEMORY MANAGEMENT DISABLED!")
        // Do nothing - ignore system memory pressure
    }

    /**
     * Configuration change callback - ignored when disabled
     */
    fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // Do nothing when disabled
    }

    /**
     * Low memory callback - ignored when disabled  
     */
    fun onLowMemory() {
        Log.w(TAG, "Low memory warning ignored - MEMORY MANAGEMENT DISABLED!")
        Log.w(TAG, "Application may crash due to insufficient memory")
        // Do nothing - ignore low memory warnings
    }
}

/**
 * Compatibility wrapper to maintain interface compatibility
 * TODO: Remove when all code is migrated to streaming system
 */
interface SmartMemoryManagerCompat {
    fun getMemoryStatus(): DisabledMemoryManager.MemoryStatus
    fun registerAtlas(key: DisabledMemoryManager.AtlasKey?, atlas: TextureAtlas)
    fun unregisterAtlas(key: DisabledMemoryManager.AtlasKey?)
    fun protectAtlases(keys: Set<DisabledMemoryManager.AtlasKey>)
    fun addProtectedAtlases(keys: Set<DisabledMemoryManager.AtlasKey>)
    fun emergencyCleanup()
    val memoryPressure: StateFlow<DisabledMemoryManager.MemoryPressure>
}

// COMMENTED OUT: typealias causes redeclaration conflicts
// /**
//  * Alias for backward compatibility with existing code
//  * TODO: Remove when all code is migrated to streaming system
//  */
// typealias SmartMemoryManager = DisabledMemoryManager