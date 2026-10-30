package com.standoutbylogic.sudoku.game

enum class Difficulty(val label: String, val holes: Int) {
    EASY("简单", 36),
    MEDIUM("普通", 44),
    HARD("困难", 52),
    EXPERT("专家", 58);

    companion object {
        fun fromName(name: String?): Difficulty = values().firstOrNull { it.name == name } ?: MEDIUM
    }
}

enum class CellStatus {
    EMPTY,
    GIVEN,
    DRAFT,
    CONFIRMED,
    HINTED
}

data class SavedGame(
    val puzzle: IntArray,
    val solution: IntArray,
    val difficulty: Difficulty,
    val savedAt: Long,
    val lastUsedAt: Long
) {
    fun withTimestamps(savedAt: Long = this.savedAt, lastUsedAt: Long = this.lastUsedAt): SavedGame =
        SavedGame(puzzle, solution, difficulty, savedAt, lastUsedAt)
}

data class CellSnapshot(val value: Int, val status: CellStatus)

data class GameSnapshot(
    val values: IntArray,
    val statuses: Array<CellStatus>,
    val selected: Int?,
    val highlightedValue: Int,
    val hints: Int,
    val mistakes: Int,
    val ended: Boolean,
    val result: String?,
    val startedAt: Long,
    val endedAt: Long
)

sealed class HistoryAction {
    data class CellAction(
        val index: Int,
        val before: CellSnapshot,
        val after: CellSnapshot
    ) : HistoryAction()

    data class HintAction(
        val index: Int,
        val before: CellSnapshot,
        val after: CellSnapshot,
        val hintsBefore: Int,
        val hintsAfter: Int
    ) : HistoryAction()

    data class GameAction(
        val before: GameSnapshot,
        val after: GameSnapshot
    ) : HistoryAction()
}

data class Record(
    val result: String,
    val seconds: Long,
    val seed: String,
    val difficulty: Difficulty,
    val atMillis: Long
)
