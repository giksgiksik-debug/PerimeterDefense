package com.giksik.perimeterdefense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GridEngineTest {
    @Test
    fun newGridHasSixBySixCellsAndSafeCenter() {
        val grid = GridEngine.newGrid(Random(1))
        assertEquals(6, grid.size)
        assertTrue(grid.all { it.size == 6 })
        for (row in 2..3) {
            for (col in 2..4) {
                assertEquals(CellState.REVEALED, grid[row][col].state)
                assertFalse(grid[row][col].hasMine)
            }
        }
    }

    @Test
    fun adjacentMineCountIncludesEightNeighbors() {
        val grid = List(6) { row ->
            List(6) { col ->
                GridCell(row, col, hasMine = (row == 1 && col == 1) || (row == 1 && col == 2))
            }
        }
        val counted = GridEngine.recalculateAdjacentMines(grid)
        assertEquals(2, counted[2][2].adjacentMines)
        assertEquals(1, counted[0][0].adjacentMines)
    }

    @Test
    fun matchesFindHorizontalThree() {
        val grid = List(6) { row ->
            List(6) { col ->
                if (row == 0 && col in 1..3) {
                    GridCell(row, col, state = CellState.REVEALED, ammoColor = AmmoColor.RED)
                } else GridCell(row, col)
            }
        }
        assertEquals(setOf(0 to 1, 0 to 2, 0 to 3), GridEngine.findMatches(grid))
    }
}
