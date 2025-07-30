package dev.serhiiyaremych.lumina.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.serhiiyaremych.lumina.domain.model.HexCell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages springy bounce animations for hex cell selections.
 * Implements Material 3 expressive design with relative scaling that accounts for base cell state.
 */
class HexCellBounceAnimationManager(private val scope: CoroutineScope) {
    
    // Cache of bounce animations for each hex cell
    private val cellAnimations = mutableMapOf<HexCell, Animatable<Float, *>>()
    
    // Track each cell's base state to ensure proper scaling
    private val cellBaseStates = mutableMapOf<HexCell, HexCellBaseState>()
    
    companion object {
        // Material 3 expressive bounce parameters (relative to base state)
        private const val BOUNCE_SCALE_MULTIPLIER = 1.2f // 20% increase from base state
        
        // Spring animation parameters for bouncy feel
        private val BOUNCE_SPRING_SPEC = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy, // Bouncy but controlled
            stiffness = Spring.StiffnessMedium // Medium responsiveness
        )
    }
    
    /**
     * Represents the base state of a hex cell for proper bounce animation scaling.
     */
    private data class HexCellBaseState(
        val baseScale: Float, // The scale the cell should return to after bounce
        val state: dev.serhiiyaremych.lumina.ui.HexCellState // The cell's current state
    )
    
    /**
     * Updates the base state for a hex cell to ensure proper bounce animation scaling.
     * This should be called whenever a cell's state changes.
     */
    fun updateCellBaseState(hexCell: HexCell, cellState: dev.serhiiyaremych.lumina.ui.HexCellState) {
        // Base scale is always 1.0f because path-based expansion handles Material 3 sizing
        cellBaseStates[hexCell] = HexCellBaseState(baseScale = 1.0f, state = cellState)
    }
    
    /**
     * Triggers a springy bounce animation for the specified hex cell.
     * Animation pattern: 1.0 → 1.2 → 1.0 (relative to the cell's current visual state).
     * 
     * The bounce animation is purely transform-based and works on top of the path-based
     * Material 3 expansion. This ensures consistent behavior regardless of cell state.
     */
    fun triggerBounce(hexCell: HexCell, cellState: dev.serhiiyaremych.lumina.ui.HexCellState) {
        // Update base state for tracking (but animation always uses 1.0f as baseline)
        updateCellBaseState(hexCell, cellState)
        
        scope.launch {
            val animation = getOrCreateAnimation(hexCell)
            
            // Always animate from 1.0f to 1.2f to 1.0f
            // This works correctly because:
            // - Path-based expansion (Material 3) creates the visual size
            // - Transform-based bounce scales whatever is already rendered
            val bounceTarget = 1.0f * BOUNCE_SCALE_MULTIPLIER
            val restingScale = 1.0f
            
            // Bounce up to 1.2x scale
            animation.animateTo(
                targetValue = bounceTarget,
                animationSpec = BOUNCE_SPRING_SPEC
            )
            
            // Bounce back down to 1.0x scale
            animation.animateTo(
                targetValue = restingScale,
                animationSpec = BOUNCE_SPRING_SPEC
            )
        }
    }
    
    /**
     * Gets the current scale value for a hex cell (for rendering).
     * Returns base scale if no animation is active for this cell.
     */
    fun getCellScale(hexCell: HexCell): Float {
        val baseState = cellBaseStates[hexCell]
        return cellAnimations[hexCell]?.value ?: (baseState?.baseScale ?: 1.0f)
    }
    
    /**
     * Gets or creates an animation instance for a hex cell.
     */
    private fun getOrCreateAnimation(hexCell: HexCell): Animatable<Float, *> {
        return cellAnimations.getOrPut(hexCell) {
            val baseState = cellBaseStates[hexCell]
            Animatable(baseState?.baseScale ?: 1.0f)
        }
    }
    
    /**
     * Cleans up animations for cells that are no longer visible or needed.
     */
    fun cleanup(activeCells: Set<HexCell>) {
        val cellsToRemove = cellAnimations.keys - activeCells
        cellsToRemove.forEach { cell ->
            cellAnimations.remove(cell)
            cellBaseStates.remove(cell)
        }
    }
    
    /**
     * Resets all animations to their base state.
     */
    fun resetAll() {
        scope.launch {
            cellAnimations.forEach { (hexCell, animation) ->
                val baseState = cellBaseStates[hexCell]
                animation.snapTo(baseState?.baseScale ?: 1.0f)
            }
        }
    }
}

/**
 * Composable function to remember a HexCellBounceAnimationManager instance.
 */
@Composable
fun rememberHexCellBounceAnimationManager(): HexCellBounceAnimationManager {
    val scope = rememberCoroutineScope()
    return remember { HexCellBounceAnimationManager(scope) }
}