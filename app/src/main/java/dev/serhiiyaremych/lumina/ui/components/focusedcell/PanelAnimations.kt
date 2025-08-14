package dev.serhiiyaremych.lumina.ui.components.focusedcell

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Data class holding panel animation state and transitions.
 */
internal data class PanelAnimations(
    val isVisible: Boolean,
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition
)

/**
 * Animation state and transitions for the FocusedCellPanel.
 * Encapsulates Material 3 Expressive entrance/exit animations.
 */
@Composable
internal fun rememberPanelAnimations(): PanelAnimations {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    return remember(isVisible) {
        PanelAnimations(
            isVisible = isVisible,
            enterTransition = fadeIn(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                )
            ) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                ),
                initialOffsetY = { it / 3 }
            ) + scaleIn(
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = 500f
                ),
                initialScale = 0.92f
            ),
            exitTransition = fadeOut(
                animationSpec = tween(200)
            ) + slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { it / 4 }
            ) + scaleOut(
                animationSpec = tween(200),
                targetScale = 0.96f
            )
        )
    }
}