package com.standoutbylogic.sudoku.game

/**
 * 位掩码 + MRV 回溯求解器。只关心“是否唯一解”，搜索数达到上限即停止。
 */
object SudokuSolver {

    fun countSolutions(puzzle: IntArray): Int {
        if (puzzle.size != 81) {
            return 0
        }
        val grid = puzzle.copyOf()
        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)
        for (index in 0 until 81) {
            val value = grid[index]
            if (value == 0) {
                continue
            }
            if (value < 1 || value > 9) {
                return 0
            }
            val row = index / 9
            val col = index % 9
            val box = (row / 3) * 3 + col / 3
            val bit = 1 shl (value - 1)
            if (rows[row] and bit != 0 || cols[col] and bit != 0 || boxes[box] and bit != 0) {
                return 0
            }
            rows[row] = rows[row] or bit
            cols[col] = cols[col] or bit
            boxes[box] = boxes[box] or bit
        }
        return search(grid, rows, cols, boxes, 2)
    }

    private fun search(grid: IntArray, rows: IntArray, cols: IntArray, boxes: IntArray, limit: Int): Int {
        if (limit <= 0) {
            return 0
        }
        var bestIndex = -1
        var bestMask = 0
        var bestCount = 10
        for (index in 0 until 81) {
            if (grid[index] != 0) {
                continue
            }
            val row = index / 9
            val col = index % 9
            val box = (row / 3) * 3 + col / 3
            val mask = (rows[row] or cols[col] or boxes[box]).inv() and 0x1FF
            if (mask == 0) {
                return 0
            }
            val count = Integer.bitCount(mask)
            if (count < bestCount) {
                bestIndex = index
                bestMask = mask
                bestCount = count
                if (count == 1) {
                    break
                }
            }
        }
        if (bestIndex == -1) {
            return 1
        }
        val row = bestIndex / 9
        val col = bestIndex % 9
        val box = (row / 3) * 3 + col / 3
        var total = 0
        var bits = bestMask
        while (bits != 0) {
            val bit = bits and -bits
            bits = bits xor bit
            grid[bestIndex] = 32 - Integer.numberOfLeadingZeros(bit)
            rows[row] = rows[row] or bit
            cols[col] = cols[col] or bit
            boxes[box] = boxes[box] or bit
            total += search(grid, rows, cols, boxes, limit - total)
            grid[bestIndex] = 0
            rows[row] = rows[row] xor bit
            cols[col] = cols[col] xor bit
            boxes[box] = boxes[box] xor bit
            if (total >= limit) {
                break
            }
        }
        return total
    }
}
