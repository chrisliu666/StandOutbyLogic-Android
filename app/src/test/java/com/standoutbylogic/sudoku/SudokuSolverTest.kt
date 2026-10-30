package com.standoutbylogic.sudoku

import com.standoutbylogic.sudoku.game.SudokuSolver
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuSolverTest {

    private val puzzle = parse(
        """
            530070000
            600195000
            098000060
            800060003
            400803001
            700020006
            060000280
            000419005
            000080079
        """
    )

    private fun parse(text: String): IntArray {
        val digits = text.filter { it in '0'..'9' }
        require(digits.length == 81) { "题面需要 81 个数字" }
        return IntArray(81) { digits[it] - '0' }
    }

    @Test
    fun knownPuzzleHasExpertSolution() {
        assertEquals(1, SudokuSolver.countSolutions(puzzle))
    }

    @Test
    fun duplicateRowReturnsZero() {
        val broken = puzzle.copyOf()
        broken[1] = 5 // 第一行原本已有 5
        assertEquals(0, SudokuSolver.countSolutions(broken))
    }

    @Test
    fun emptyGridIsCappedAtTwo() {
        assertEquals(2, SudokuSolver.countSolutions(IntArray(81)))
    }
}
