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
    @Test
    fun matchGroupsKeepSeparateRunsOfTheSameColorSeparate() {
        val grid = List(6) { row ->
            List(6) { col ->
                val inFirstRun = row == 0 && col in 0..2
                val inSecondRun = row == 5 && col in 3..5
                if (inFirstRun || inSecondRun) {
                    GridCell(row, col, state = CellState.REVEALED, ammoColor = AmmoColor.BLUE)
                } else {
                    GridCell(row, col)
                }
            }
        }

        val groups = GridEngine.findMatchGroups(grid)
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.ammoColor == AmmoColor.BLUE && it.size == 3 })
        assertTrue(groups.all { it.multiplier == 1.0 })
    }

    @Test
    fun crossingRunsOfTheSameColorBecomeOneFiveCellGroup() {
        val grid = List(6) { row ->
            List(6) { col ->
                val inHorizontal = row == 2 && col in 1..3
                val inVertical = col == 2 && row in 1..3
                if (inHorizontal || inVertical) {
                    GridCell(row, col, state = CellState.REVEALED, ammoColor = AmmoColor.RED)
                } else {
                    GridCell(row, col)
                }
            }
        }

        val groups = GridEngine.findMatchGroups(grid)
        assertEquals(1, groups.size)
        assertEquals(5, groups.single().size)
        assertEquals(2.5, groups.single().multiplier, 0.0)
        assertEquals(5, GridEngine.findMatches(grid).size)
    }

    @Test
    fun fourCellRunGetsOnePointFiveMultiplier() {
        val grid = List(6) { row ->
            List(6) { col ->
                if (row == 4 && col in 1..4) {
                    GridCell(row, col, state = CellState.REVEALED, ammoColor = AmmoColor.YELLOW)
                } else {
                    GridCell(row, col)
                }
            }
        }

        val group = GridEngine.findMatchGroups(grid).single()
        assertEquals(4, group.size)
        assertEquals(1.5, group.multiplier, 0.0)
    }

    @Test
    fun hiddenCellsAndMinesDoNotParticipateInMatches() {
        val grid = List(6) { row ->
            List(6) { col ->
                when {
                    row == 0 && col == 0 -> GridCell(row, col, state = CellState.REVEALED, ammoColor = AmmoColor.GREEN_HEAL)
                    row == 0 && col == 1 -> GridCell(row, col, state = CellState.HIDDEN, ammoColor = AmmoColor.GREEN_HEAL)
                    row == 0 && col == 2 -> GridCell(row, col, state = CellState.REVEALED, ammoColor = AmmoColor.GREEN_HEAL)
                    row == 1 && col in 0..2 -> GridCell(row, col, state = CellState.REVEALED, hasMine = col == 1, ammoColor = AmmoColor.BLUE)
                    else -> GridCell(row, col)
                }
            }
        }

        assertTrue(GridEngine.findMatchGroups(grid).isEmpty())
    }

}
