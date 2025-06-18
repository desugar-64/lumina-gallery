package dev.serhiiyaremych.lumina.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

data class HexCell(
    val q: Int, // Column coordinate
    val r: Int, // Row coordinate
    val center: Offset,
    val vertices: List<Offset>
)

data class HexGrid(
    val size: Int, // NxN grid size
    val cellSizeDp: Dp,
    val cells: List<HexCell>,
    val cellSizePx: Float
)
