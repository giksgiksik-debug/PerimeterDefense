package com.giksik.perimeterdefense.domain

enum class CellState { HIDDEN, REVEALED }
enum class AmmoColor { RED, BLUE, YELLOW, GREEN_HEAL, NONE }
enum class InteractionMode { DRILL_REVEAL, FLAG_CAPTURE }

data class GridCell(
    val row: Int,
    val col: Int,
    val state: CellState = CellState.HIDDEN,
    val hasMine: Boolean = false,
    val ammoColor: AmmoColor = AmmoColor.NONE,
    val adjacentMines: Int = 0,
    val isJammed: Boolean = false
)

data class GridResult(
    val grid: List<List<GridCell>>,
    val capturedMines: Int = 0,
    val detonatedMines: Int = 0,
    val revealedCells: Int = 0,
    val turnConsumed: Boolean = false,
    val message: String = ""
)
