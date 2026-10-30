package com.standoutbylogic.sudoku.game

/**
 * 与 Web 版 app.js 完全一致的确定性生成逻辑：
 * FNV-1a 种子哈希 -> mulberry32 伪随机 -> 基底盘重映射 + 带内行列洗牌 -> 带唯一解校验的挖空。
 * 同一种子码在同一难度下稳定复现同一题面与答案。
 */
object SudokuGenerator {

    private const val RETRY_PASSES = 3

    private val BASE = intArrayOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9,
        4, 5, 6, 7, 8, 9, 1, 2, 3,
        7, 8, 9, 1, 2, 3, 4, 5, 6,
        2, 3, 4, 5, 6, 7, 8, 9, 1,
        5, 6, 7, 8, 9, 1, 2, 3, 4,
        8, 9, 1, 2, 3, 4, 5, 6, 7,
        3, 4, 5, 6, 7, 8, 9, 1, 2,
        6, 7, 8, 9, 1, 2, 3, 4, 5,
        9, 1, 2, 3, 4, 5, 6, 7, 8
    )

    fun create(seed: String, difficulty: Difficulty): SavedGame {
        val rng = Mulberry32(hashString(seed))
        val digitMap = shuffle(IntArray(9) { it + 1 }, rng)
        var solution = IntArray(81) { digitMap[BASE[it] - 1] }
        solution = shuffleRowsAndColumns(solution, rng)
        val puzzle = createPuzzleWithUniqueSolution(solution, difficulty.holes, rng)
        return SavedGame(puzzle, solution, difficulty, 0L, 0L)
    }

    fun hashString(input: String): Int {
        var hash = 2166136261L.toInt()
        for (char in input) {
            hash = hash xor char.code
            hash *= 16777619
        }
        return hash
    }

    private fun createPuzzleWithUniqueSolution(solution: IntArray, targetHoles: Int, rng: Mulberry32): IntArray {
        var best: IntArray? = null
        var bestRemoved = -1
        for (pass in 0 until RETRY_PASSES) {
            val puzzle = solution.copyOf()
            var removed = 0
            val positions = shuffle(IntArray(81) { it }, rng)
            for (index in positions) {
                if (removed >= targetHoles) {
                    break
                }
                val value = puzzle[index]
                puzzle[index] = 0
                if (SudokuSolver.countSolutions(puzzle) == 1) {
                    removed += 1
                } else {
                    puzzle[index] = value
                }
            }
            if (removed > bestRemoved) {
                best = puzzle
                bestRemoved = removed
            }
            if (removed >= targetHoles) {
                break
            }
        }
        return best ?: solution.copyOf()
    }

    private fun shuffleRowsAndColumns(grid: IntArray, rng: Mulberry32): IntArray {
        val rowBands = shuffle(intArrayOf(0, 1, 2), rng)
        val colBands = shuffle(intArrayOf(0, 1, 2), rng)
        val rows = ArrayList<Int>(9)
        val cols = ArrayList<Int>(9)
        for (band in rowBands) {
            for (inner in shuffle(intArrayOf(0, 1, 2), rng)) {
                rows.add(band * 3 + inner)
            }
        }
        for (band in colBands) {
            for (inner in shuffle(intArrayOf(0, 1, 2), rng)) {
                cols.add(band * 3 + inner)
            }
        }
        val output = IntArray(81)
        var cursor = 0
        for (row in rows) {
            for (col in cols) {
                output[cursor] = grid[row * 9 + col]
                cursor += 1
            }
        }
        return output
    }

    private fun shuffle(items: IntArray, rng: Mulberry32): IntArray {
        val output = items.copyOf()
        for (index in output.size - 1 downTo 1) {
            val swapIndex = (rng.next() * (index + 1)).toInt()
            val temp = output[index]
            output[index] = output[swapIndex]
            output[swapIndex] = temp
        }
        return output
    }

    private class Mulberry32(seed: Int) {
        private var state = seed

        fun next(): Double {
            state += 0x6D2B79F5
            var value = state
            value = (value xor (value ushr 15)) * (value or 1)
            value = value xor (value + (value xor (value ushr 7)) * (value or 61))
            return ((value xor (value ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
    }
}
