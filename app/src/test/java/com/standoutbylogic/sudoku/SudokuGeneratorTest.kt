package com.standoutbylogic.sudoku

import com.standoutbylogic.sudoku.game.Difficulty
import com.standoutbylogic.sudoku.game.SudokuGenerator
import com.standoutbylogic.sudoku.game.SudokuSolver
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGeneratorTest {

    @Test
    fun sameSeedProducesSamePuzzleAndSolution() {
        val first = SudokuGenerator.create("STANDOUT-20261030-AAAAAA", Difficulty.HARD)
        val second = SudokuGenerator.create("STANDOUT-20261030-AAAAAA", Difficulty.HARD)
        assertArrayEquals(first.puzzle, second.puzzle)
        assertArrayEquals(first.solution, second.solution)
        assertEquals(Difficulty.HARD, first.difficulty)
    }

    @Test
    fun everyDifficultyHasUniqueSolution() {
        for (difficulty in Difficulty.values()) {
            val game = SudokuGenerator.create("UNIQUE-${difficulty.name}-42", difficulty)
            assertEquals(
                "难度 ${difficulty.label} 的题面应只有唯一解",
                1,
                SudokuSolver.countSolutions(game.puzzle)
            )
            assertValidGrid(game.solution)
            for (index in 0 until 81) {
                if (game.puzzle[index] != 0) {
                    assertEquals(game.solution[index], game.puzzle[index])
                }
            }
            val holes = game.puzzle.count { it == 0 }
            assertTrue("挖空数不应超过目标", holes <= difficulty.holes)
            assertTrue("挖空数过少: $holes", holes >= difficulty.holes - 6)
        }
    }

    @Test
    fun differentSeedsProduceDifferentPuzzles() {
        val first = SudokuGenerator.create("STANDOUT-DIFF-1", Difficulty.EASY)
        val second = SudokuGenerator.create("STANDOUT-DIFF-2", Difficulty.EASY)
        assertTrue(!first.puzzle.contentEquals(second.puzzle))
    }

    private fun assertValidGrid(grid: IntArray) {
        assertEquals(81, grid.size)
        for (row in 0 until 9) {
            val values = (0 until 9).map { grid[row * 9 + it] }.toSet()
            assertEquals((1..9).toSet(), values)
        }
        for (col in 0 until 9) {
            val values = (0 until 9).map { grid[it * 9 + col] }.toSet()
            assertEquals((1..9).toSet(), values)
        }
        for (boxRow in 0..2) {
            for (boxCol in 0..2) {
                val values = HashSet<Int>()
                for (row in 0..2) {
                    for (col in 0..2) {
                        values.add(grid[(boxRow * 3 + row) * 9 + boxCol * 3 + col])
                    }
                }
                assertEquals((1..9).toSet(), values)
            }
        }
    }
}
