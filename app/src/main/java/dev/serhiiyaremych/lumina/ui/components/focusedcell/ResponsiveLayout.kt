package dev.serhiiyaremych.lumina.ui.components.focusedcell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Extension function to apply panel transformation modifiers with responsive width.
 */
@Composable
internal fun Modifier.panelTransformation(
    provideTranslationOffset: (panelSize: Size) -> Offset
): Modifier {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    val widthModifier = when {
        !windowSizeClass.isWidthAtLeastBreakpoint(ResponsiveLayoutConstants.SMALL_TO_MEDIUM_BREAKPOINT_DP) -> {
            Modifier.fillMaxWidth()
        }
        !windowSizeClass.isWidthAtLeastBreakpoint(ResponsiveLayoutConstants.MEDIUM_TO_LARGE_BREAKPOINT_DP) -> {
            Modifier.fillMaxWidth(ResponsiveLayoutConstants.SMALL_TABLET_WIDTH_FRACTION)
        }
        else -> {
            Modifier.fillMaxWidth(ResponsiveLayoutConstants.LARGE_TABLET_WIDTH_FRACTION)
        }
    }

    return this
        .then(widthModifier)
        .graphicsLayer {
            val offset = provideTranslationOffset(size)
            translationX = offset.x
            translationY = offset.y
        }
        .padding(16.dp)
}
