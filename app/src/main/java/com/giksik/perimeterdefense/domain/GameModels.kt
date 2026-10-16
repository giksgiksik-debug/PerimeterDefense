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


/**
 * A single connected Match-3 group. Crossing horizontal and vertical runs
 * of the same color are represented as one group, with intersection cells counted once.
 */
data class MatchGroup(
    val ammoColor: AmmoColor,
    val cells: Set<Pair<Int, Int>>
) {
    val size: Int get() = cells.size

    /** Damage/reward multiplier from the supplied prototype's combo rules. */
    val multiplier: Double
        get() = when {
            size >= 5 -> 2.5
            size == 4 -> 1.5
            else -> 1.0
        }
}
