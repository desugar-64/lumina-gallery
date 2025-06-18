package dev.serhiiyaremych.lumina.domain.usecase

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.sqrt

class GetHexGridParametersUseCase @Inject constructor() {
    operator fun invoke(groupCount: Int): Pair<Int, Dp> {
        val gridSize = if (groupCount == 0) {
            15
        } else {
            val minSize = ceil(sqrt(groupCount.toDouble())).toInt()
            (minSize + 2).coerceAtLeast(10).coerceAtMost(25)
        }

        val cellSizeDp = when {
            gridSize <= 10 -> 230.dp
            gridSize <= 15 -> 200.dp
            gridSize <= 20 -> 170.dp
            else -> 140.dp
        }

        return gridSize to cellSizeDp
    }
}
