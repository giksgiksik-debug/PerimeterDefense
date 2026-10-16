package com.giksik.perimeterdefense.domain

import kotlin.random.Random

object GridEngine {
    const val GRID_SIZE = 6
    const val MAX_MINES = 7
    private const val MINE_CHANCE = 0.16

    private val directions = listOf(
        -1 to -1, -1 to 0, -1 to 1,
         0 to -1,           0 to 1,
         1 to -1,  1 to 0,  1 to 1
    )

    fun newGrid(random: Random = Random.Default): List<List<GridCell>> {
        val safe = (2..3).flatMap { row -> (2..4).map { col -> row to col } }.toSet()
        var minesPlaced = 0
        val grid = List(GRID_SIZE) { row ->
            List(GRID_SIZE) { col ->
                val isSafe = (row to col) in safe
                val mine = !isSafe && minesPlaced < MAX_MINES && random.nextDouble() < MINE_CHANCE
                if (mine) minesPlaced++
                if (isSafe) {
                    GridCell(
                        row = row,
                        col = col,
                        state = CellState.REVEALED,
                        ammoColor = randomAmmo(random)
                    )
                } else {
                    GridCell(row = row, col = col, hasMine = mine)
                }
            }
        }
        return recalculateAdjacentMines(grid)
    }

    fun recalculateAdjacentMines(grid: List<List<GridCell>>): List<List<GridCell>> =
        grid.mapIndexed { row, cells ->
            cells.mapIndexed { col, cell ->
                val count = directions.count { (dr, dc) ->
                    val nr = row + dr
                    val nc = col + dc
                    nr in 0 until GRID_SIZE && nc in 0 until GRID_SIZE && grid[nr][nc].hasMine
                }
                cell.copy(adjacentMines = count)
            }
        }

    fun reveal(grid: List<List<GridCell>>, row: Int, col: Int, random: Random = Random.Default): GridResult {
        val cell = grid.getOrNull(row)?.getOrNull(col)
            ?: return GridResult(grid, message = "Клетка вне поля")
        if (cell.state == CellState.REVEALED) return GridResult(grid, message = "Клетка уже открыта")

        if (cell.hasMine) {
            val replaced = grid.mapIndexed { r, cells ->
                cells.mapIndexed { c, current ->
                    if (r == row && c == col) current.copy(hasMine = false, ammoColor = randomAmmo(random)) else current
                }
            }
            return GridResult(
                grid = recalculateAdjacentMines(replaced),
                detonatedMines = 1,
                turnConsumed = true,
                message = "Перегрузка! Турели оглушены на 2 хода"
            )
        }

        val opened = grid.mapIndexed { r, cells ->
            cells.mapIndexed { c, current ->
                if (r == row && c == col) current.copy(state = CellState.REVEALED, ammoColor = randomAmmo(random)) else current
            }
        }
        return GridResult(
            grid = recalculateAdjacentMines(opened),
            revealedCells = 1,
            turnConsumed = true,
            message = "Клетка вскрыта"
        )
    }

    fun capture(grid: List<List<GridCell>>, row: Int, col: Int, random: Random = Random.Default): GridResult {
        val cell = grid.getOrNull(row)?.getOrNull(col)
            ?: return GridResult(grid, message = "Клетка вне поля")
        if (cell.state == CellState.REVEALED) return GridResult(grid, message = "Выбери закрытую клетку")

        if (!cell.hasMine) {
            val opened = grid.mapIndexed { r, cells ->
                cells.mapIndexed { c, current ->
                    if (r == row && c == col) current.copy(state = CellState.REVEALED, ammoColor = randomAmmo(random)) else current
                }
            }
            return GridResult(
                grid = recalculateAdjacentMines(opened),
                revealedCells = 1,
                turnConsumed = true,
                message = "Промах захвата: клетка вскрыта"
            )
        }

        val removed = grid.mapIndexed { r, cells ->
            cells.mapIndexed { c, current ->
                if (r == row && c == col) current.copy(hasMine = false, state = CellState.REVEALED, ammoColor = AmmoColor.NONE) else current
            }
        }
        return GridResult(
            grid = recalculateAdjacentMines(removed),
            capturedMines = 1,
            turnConsumed = true,
            message = "Мина перехвачена и отправлена во врагов!"
        )
    }

    /**
     * Finds contiguous Match-3 runs and merges same-color runs that intersect
     * (for example, a T or cross). Separate runs of the same color stay separate.
     */
    fun findMatchGroups(grid: List<List<GridCell>>): List<MatchGroup> {
        val groups = mutableListOf<MatchGroup>()

        fun valid(cell: GridCell): Boolean =
            cell.state == CellState.REVEALED &&
                !cell.hasMine &&
                cell.ammoColor != AmmoColor.NONE

        fun addRun(color: AmmoColor, runCells: Set<Pair<Int, Int>>) {
            val mergedCells = runCells.toMutableSet()
            var merged: Boolean
            do {
                merged = false
                val iterator = groups.iterator()
                while (iterator.hasNext()) {
                    val existing = iterator.next()
                    if (existing.ammoColor == color && existing.cells.any { it in mergedCells }) {
                        mergedCells.addAll(existing.cells)
                        iterator.remove()
                        merged = true
                    }
                }
            } while (merged)
            groups.add(MatchGroup(color, mergedCells))
        }

        // Horizontal runs.
        for (row in 0 until GRID_SIZE) {
            var start = 0
            while (start < GRID_SIZE) {
                val first = grid[row][start]
                var end = start + 1
                while (
                    end < GRID_SIZE &&
                    valid(first) &&
                    valid(grid[row][end]) &&
                    grid[row][end].ammoColor == first.ammoColor
                ) {
                    end++
                }
                if (valid(first) && end - start >= 3) {
                    addRun(first.ammoColor, (start until end).mapTo(linkedSetOf()) { row to it })
                }
                start = end
            }
        }

        // Vertical runs.
        for (col in 0 until GRID_SIZE) {
            var start = 0
            while (start < GRID_SIZE) {
                val first = grid[start][col]
                var end = start + 1
                while (
                    end < GRID_SIZE &&
                    valid(first) &&
                    valid(grid[end][col]) &&
                    grid[end][col].ammoColor == first.ammoColor
                ) {
                    end++
                }
                if (valid(first) && end - start >= 3) {
                    addRun(first.ammoColor, (start until end).mapTo(linkedSetOf()) { it to col })
                }
                start = end
            }
        }

        return groups.sortedWith(
            compareBy<MatchGroup> { group -> group.cells.minOf { it.first } }
                .thenBy { group -> group.cells.minOf { it.second } }
        )
    }

    /** Backward-compatible flat set used by existing callers. */
    fun findMatches(grid: List<List<GridCell>>): Set<Pair<Int, Int>> =
        findMatchGroups(grid).flatMapTo(mutableSetOf()) { it.cells }

    /**
     * Resolves all current matches in batches. Cells are re-rolled only after the
     * complete batch is identified, so coordinates from one batch never go stale
     * while another group is being processed.
     */
    fun resolveMatches(
        grid: List<List<GridCell>>,
        random: Random = Random.Default
    ): GridMatchResolution {
        var currentGrid = grid
        val allGroups = mutableListOf<MatchGroup>()
        var cascades = 0
        val maxCascades = 12

        while (cascades < maxCascades) {
            val groups = findMatchGroups(currentGrid)
            if (groups.isEmpty()) break

            allGroups.addAll(groups)
            cascades++
            val matchedCells = groups.flatMapTo(mutableSetOf()) { it.cells }

            currentGrid = currentGrid.mapIndexed { row, cells ->
                cells.mapIndexed { col, cell ->
                    if ((row to col) in matchedCells) {
                        cell.copy(ammoColor = randomAmmo(random))
                    } else {
                        cell
                    }
                }
            }
        }

        // Keep resolution bounded even with an unlucky random sequence.
        // The remaining pieces are spent instead of re-firing on every turn.
        val leftovers = findMatchGroups(currentGrid)
        if (leftovers.isNotEmpty()) {
            val leftoverCells = leftovers.flatMapTo(mutableSetOf()) { it.cells }
            currentGrid = currentGrid.mapIndexed { row, cells ->
                cells.mapIndexed { col, cell ->
                    if ((row to col) in leftoverCells) cell.copy(ammoColor = AmmoColor.NONE) else cell
                }
            }
        }

        return GridMatchResolution(currentGrid, allGroups, cascades)
    }

    private fun randomAmmo(random: Random): AmmoColor {
        val roll = random.nextInt(100)
        return when {
            roll < 30 -> AmmoColor.RED
            roll < 60 -> AmmoColor.BLUE
            roll < 90 -> AmmoColor.YELLOW
            else -> AmmoColor.GREEN_HEAL
        }
    }
}
