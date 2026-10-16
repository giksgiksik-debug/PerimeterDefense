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

    fun findMatches(grid: List<List<GridCell>>): Set<Pair<Int, Int>> {
        val matched = mutableSetOf<Pair<Int, Int>>()
        fun valid(cell: GridCell) =
            cell.state == CellState.REVEALED && !cell.hasMine && cell.ammoColor != AmmoColor.NONE

        for (row in 0 until GRID_SIZE) {
            var start = 0
            while (start < GRID_SIZE) {
                var end = start + 1
                while (end < GRID_SIZE && valid(grid[row][start]) && valid(grid[row][end]) &&
                    grid[row][start].ammoColor == grid[row][end].ammoColor) end++
                if (end - start >= 3 && valid(grid[row][start])) {
                    for (col in start until end) matched.add(row to col)
                }
                start = end
            }
        }

        for (col in 0 until GRID_SIZE) {
            var start = 0
            while (start < GRID_SIZE) {
                var end = start + 1
                while (end < GRID_SIZE && valid(grid[start][col]) && valid(grid[end][col]) &&
                    grid[start][col].ammoColor == grid[end][col].ammoColor) end++
                if (end - start >= 3 && valid(grid[start][col])) {
                    for (row in start until end) matched.add(row to col)
                }
                start = end
            }
        }
        return matched
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
