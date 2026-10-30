package com.standoutbylogic.sudoku

import com.standoutbylogic.sudoku.game.CellStatus
import com.standoutbylogic.sudoku.game.Difficulty
import com.standoutbylogic.sudoku.game.GameController
import com.standoutbylogic.sudoku.game.MAX_MISTAKES
import com.standoutbylogic.sudoku.game.Record
import com.standoutbylogic.sudoku.game.SavedGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameControllerTest {

    // 合法基底盘：每行、每列、每宫都是 1-9
    private val solution = IntArray(81) { index ->
        val row = index / 9
        val col = index % 9
        ((row * 3 + row / 3 + col) % 9) + 1
    }

    // 第一行全部挖空，方便制造错误、提示与通关
    private val emptyIndices = (0..8).toList()

    private fun fakeGame(seed: String, difficulty: Difficulty): SavedGame {
        val puzzle = solution.copyOf()
        for (index in emptyIndices) {
            puzzle[index] = 0
        }
        return SavedGame(puzzle, solution, difficulty, 0L, 0L)
    }

    private fun newController(
        clock: () -> Long = { 0L },
        persistedRecords: MutableList<List<Record>> = mutableListOf()
    ): GameController = GameController(
        initialSeeds = emptyMap(),
        initialRecords = emptyList(),
        clock = clock,
        generator = { seed, difficulty -> fakeGame(seed, difficulty) },
        persistSeeds = { true },
        persistRecords = { persistedRecords.add(it.toList()) }
    )

    private fun wrongValue(index: Int): Int = if (solution[index] == 9) 1 else solution[index] + 1

    @Test
    fun threeWrongConfirmsEndGameInFailure() {
        val controller = newController()
        controller.startGame("seed-wrong", Difficulty.EASY)
        repeat(MAX_MISTAKES) {
            controller.selectCell(0)
            controller.inputDigit(wrongValue(0))
            controller.confirmSelected()
        }
        assertTrue(controller.state.ended)
        assertEquals("失败", controller.state.result)
        assertEquals(MAX_MISTAKES, controller.state.mistakes)
    }

    @Test
    fun hintsAreLimitedToThree() {
        val controller = newController()
        controller.startGame("seed-hint", Difficulty.EASY)

        repeat(3) { controller.useHint() }

        assertEquals(0, controller.state.hints)
        assertEquals(CellStatus.HINTED, controller.state.statuses[0])
        val filledBefore = controller.state.values.count { it != 0 }

        controller.useHint()

        assertEquals(0, controller.state.hints)
        assertEquals(filledBefore, controller.state.values.count { it != 0 })
    }

    @Test
    fun draftCanBeUndoneAndRedone() {
        val controller = newController()
        controller.startGame("seed-history", Difficulty.EASY)

        controller.selectCell(0)
        controller.inputDigit(solution[0])
        assertEquals(solution[0], controller.state.values[0])
        assertEquals(CellStatus.DRAFT, controller.state.statuses[0])

        controller.undo()
        assertEquals(0, controller.state.values[0])

        controller.redo()
        assertEquals(solution[0], controller.state.values[0])
    }

    @Test
    fun confirmedCellCannotBeEditedAgain() {
        val controller = newController()
        controller.startGame("seed-lock", Difficulty.EASY)

        controller.selectCell(0)
        controller.inputDigit(solution[0])
        controller.confirmSelected()
        assertEquals(CellStatus.CONFIRMED, controller.state.statuses[0])

        controller.inputDigit(wrongValue(0))
        assertEquals(solution[0], controller.state.values[0])
    }

    @Test
    fun fillingEveryCellWinsAndWritesRecord() {
        val persisted = mutableListOf<List<Record>>()
        val controller = newController(persistedRecords = persisted)
        controller.startGame("seed-win", Difficulty.EASY)

        for (index in emptyIndices) {
            controller.selectCell(index)
            controller.inputDigit(solution[index])
            controller.confirmSelected()
        }

        assertTrue(controller.state.ended)
        assertEquals("成功", controller.state.result)
        assertEquals(1, controller.recordsSnapshot().size)
        assertTrue(persisted.isNotEmpty())
        assertEquals(1, persisted.last().size)
    }

    @Test
    fun resetKeepsMistakesAndUndoResetRestoresBoard() {
        val controller = newController()
        controller.startGame("seed-reset", Difficulty.EASY)

        controller.selectCell(0)
        controller.inputDigit(solution[0])
        controller.confirmSelected()
        controller.selectCell(1)
        controller.inputDigit(wrongValue(1))
        controller.selectCell(2)
        controller.inputDigit(wrongValue(2))
        controller.confirmSelected() // 记一次错误

        val mistakesBefore = controller.state.mistakes
        controller.resetGame()

        assertFalse(controller.state.ended)
        assertEquals(0, controller.state.values[1])
        assertEquals(0, controller.state.values[2])
        assertEquals(CellStatus.EMPTY, controller.state.statuses[1])
        assertEquals(mistakesBefore, controller.state.mistakes)
        assertNotNull(controller.state.resetSnapshot)

        controller.undoReset()

        assertEquals(solution[0], controller.state.values[0])
        assertEquals(CellStatus.CONFIRMED, controller.state.statuses[0])
        assertEquals(wrongValue(1), controller.state.values[1])
        assertEquals(mistakesBefore, controller.state.mistakes)
    }

    @Test
    fun swipeStepsCycleValueThroughZero() {
        val controller = newController()
        controller.startGame("seed-swipe", Difficulty.EASY)

        controller.adjustCell(0, 1)
        assertEquals(1, controller.state.values[0])
        assertEquals(CellStatus.DRAFT, controller.state.statuses[0])

        controller.adjustCell(0, -1)
        assertEquals(0, controller.state.values[0])
        assertEquals(CellStatus.EMPTY, controller.state.statuses[0])

        controller.adjustCell(0, -1)
        assertEquals(9, controller.state.values[0])
    }

    @Test
    fun pauseAndResumeSkipHiddenTime() {
        var now = 1000L
        val controller = newController(clock = { now })
        controller.startGame("seed-timer", Difficulty.EASY)

        now = 6000L
        assertEquals(5L, controller.elapsedSeconds())

        controller.onAppBackground()
        now = 20000L
        controller.onAppForeground()
        assertEquals(5L, controller.elapsedSeconds())

        now = 23000L
        assertEquals(8L, controller.elapsedSeconds())
    }

    @Test
    fun storedSeedKeepsOriginalDifficulty() {
        val controller = newController()
        controller.startGame("seed-difficulty", Difficulty.EASY)
        controller.startGame("seed-difficulty", Difficulty.EXPERT)
        assertEquals(Difficulty.EASY, controller.state.difficulty)
    }
}
