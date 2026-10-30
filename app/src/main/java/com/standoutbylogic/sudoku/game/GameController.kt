package com.standoutbylogic.sudoku.game

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

const val MAX_MISTAKES = 3
const val HINT_LIMIT = 3
const val MAX_SAVED_SEEDS = 60
const val MAX_RECORDS = 20

sealed class GameEvent {
    object Selected : GameEvent()
    object Deselected : GameEvent()
    object ValueChanged : GameEvent()
    object ConfirmCorrect : GameEvent()
    object ConfirmWrong : GameEvent()
    object HintUsed : GameEvent()
    data class DigitCompleted(val value: Int) : GameEvent()
    object GameWon : GameEvent()
    object GameLost : GameEvent()
    object ResetRestarted : GameEvent()
    object ResetKept : GameEvent()
    data class Message(val text: String) : GameEvent()
    object RecordsChanged : GameEvent()
}

class GameState {
    var puzzle: IntArray = IntArray(81)
    var solution: IntArray = IntArray(81)
    var values: IntArray = IntArray(81)
    var statuses: Array<CellStatus> = Array(81) { CellStatus.EMPTY }
    var selected: Int? = null
    var highlightedValue: Int = 0
    var mistakes: Int = 0
    var hints: Int = HINT_LIMIT
    var difficulty: Difficulty = Difficulty.MEDIUM
    var seed: String = ""
    var startedAt: Long = 0L
    var endedAt: Long = 0L
    var hiddenAt: Long? = null
    var ended: Boolean = false
    var result: String? = null
    val undoStack = ArrayDeque<HistoryAction>()
    val redoStack = ArrayDeque<HistoryAction>()
    var resetSnapshot: GameSnapshot? = null
    val completedDigits = HashSet<Int>()

    fun canEdit(index: Int): Boolean {
        if (ended || index !in 0..80) {
            return false
        }
        val status = statuses[index]
        return status != CellStatus.GIVEN && status != CellStatus.CONFIRMED && status != CellStatus.HINTED
    }
}

/**
 * 全部游戏规则，与 Web 版 app.js 一一对应。此类不依赖 Android API，可直接单元测试。
 */
class GameController(
    initialSeeds: Map<String, SavedGame> = emptyMap(),
    initialRecords: List<Record> = emptyList(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val generator: (String, Difficulty) -> SavedGame = { seed, difficulty ->
        SudokuGenerator.create(seed, difficulty)
    },
    private val persistSeeds: (Map<String, SavedGame>) -> Boolean = { true },
    private val persistRecords: (List<Record>) -> Unit = {}
) {
    val state = GameState()
    val seedStore: MutableMap<String, SavedGame> = LinkedHashMap(initialSeeds)
    private val records = ArrayList<Record>(initialRecords)

    var onChange: (() -> Unit)? = null
    var onEvent: ((GameEvent) -> Unit)? = null

    fun recordsSnapshot(): List<Record> = records.toList()

    fun startGame(requestedSeed: String?, difficulty: Difficulty = state.difficulty) {
        val seed = requestedSeed?.takeIf { it.isNotBlank() } ?: createSeed(difficulty)
        val stored = seedStore[seed]
        val game: SavedGame
        var usedDifficulty = difficulty
        if (stored != null) {
            usedDifficulty = stored.difficulty
            game = stored.withTimestamps(lastUsedAt = clock())
        } else {
            val generated = generator(seed, difficulty)
            game = generated.withTimestamps(savedAt = clock(), lastUsedAt = clock())
        }
        seedStore[seed] = game
        saveSeedStore()

        state.puzzle = game.puzzle.copyOf()
        state.solution = game.solution.copyOf()
        state.values = game.puzzle.copyOf()
        state.statuses = Array(81) {
            if (state.puzzle[it] != 0) CellStatus.GIVEN else CellStatus.EMPTY
        }
        state.selected = null
        state.highlightedValue = 0
        state.mistakes = 0
        state.hints = HINT_LIMIT
        state.difficulty = usedDifficulty
        state.seed = seed
        state.startedAt = clock()
        state.endedAt = 0L
        state.hiddenAt = null
        state.ended = false
        state.result = null
        state.undoStack.clear()
        state.redoStack.clear()
        state.resetSnapshot = null
        state.completedDigits.clear()
        changed()
    }

    fun loadSeed(seed: String) {
        val trimmed = seed.trim()
        if (trimmed.isEmpty()) {
            emit(GameEvent.Message("请输入种子码"))
            return
        }
        startGame(trimmed, state.difficulty)
    }

    fun selectCell(index: Int?) {
        if (index == null) {
            if (state.selected == null) {
                return
            }
            state.selected = null
            state.highlightedValue = 0
            emit(GameEvent.Deselected)
            changed()
            return
        }
        if (index !in 0..80) {
            return
        }
        state.selected = index
        state.highlightedValue = if (state.values[index] != 0) state.values[index] else 0
        emit(GameEvent.Selected)
        changed()
    }

    fun inputDigit(value: Int) {
        if (state.ended || value !in 1..9) {
            return
        }
        val index = state.selected
        if (index == null) {
            emit(GameEvent.Message("请先选择一个格子"))
            return
        }
        if (!state.canEdit(index)) {
            emit(GameEvent.Message("该格不可修改"))
            return
        }
        setCellValue(index, value)
    }

    fun adjustCell(index: Int, delta: Int) {
        if (state.ended || !state.canEdit(index)) {
            return
        }
        state.selected = index
        setCellValue(index, cycleValue(state.values[index] + delta))
    }

    fun confirmSelected() {
        val index = state.selected
        if (index == null) {
            emit(GameEvent.Message("请先选择一个格子"))
            return
        }
        confirmCell(index)
    }

    fun confirmCell(index: Int) {
        if (state.ended || index !in 0..80) {
            return
        }
        val status = state.statuses[index]
        if (status == CellStatus.GIVEN || status == CellStatus.HINTED) {
            return
        }
        val value = state.values[index]
        if (value == 0) {
            emit(GameEvent.Message("空格不能确认"))
            return
        }
        if (value == state.solution[index]) {
            val before = snapshotCell(index)
            state.statuses[index] = CellStatus.CONFIRMED
            val after = snapshotCell(index)
            pushHistory(HistoryAction.CellAction(index, before, after))
            emit(GameEvent.ConfirmCorrect)
            checkWin()
        } else {
            state.mistakes += 1
            emit(GameEvent.ConfirmWrong)
            emit(GameEvent.Message("错误，还剩 ${maxOf(0, MAX_MISTAKES - state.mistakes)} 次机会"))
            if (state.mistakes >= MAX_MISTAKES) {
                finishGame("失败")
            }
        }
        checkDigitCompletion()
        changed()
    }

    fun clearSelectedCell() {
        val index = state.selected
        if (index == null) {
            emit(GameEvent.Message("请先选择一个格子"))
            return
        }
        clearCellAt(index)
    }

    fun clearCellAt(index: Int) {
        if (state.ended || index !in 0..80) {
            return
        }
        if (state.selected != index) {
            selectCell(index)
        }
        if (!state.canEdit(index)) {
            emit(GameEvent.Message("该格不可修改"))
            return
        }
        val before = snapshotCell(index)
        state.values[index] = 0
        state.statuses[index] = CellStatus.EMPTY
        state.highlightedValue = 0
        val after = snapshotCell(index)
        pushHistory(HistoryAction.CellAction(index, before, after))
        state.redoStack.clear()
        emit(GameEvent.Message("已清除"))
        changed()
    }

    fun useHint() {
        if (state.ended) {
            return
        }
        if (state.hints <= 0) {
            emit(GameEvent.Message("提示次数已用完"))
            return
        }
        val index = pickHintIndex()
        if (index == null) {
            emit(GameEvent.Message("没有可提示的格子"))
            return
        }
        val before = snapshotCell(index)
        state.values[index] = state.solution[index]
        state.statuses[index] = CellStatus.HINTED
        state.hints -= 1
        state.selected = index
        state.highlightedValue = state.values[index]
        val after = snapshotCell(index)
        pushHistory(HistoryAction.HintAction(index, before, after, state.hints + 1, state.hints))
        emit(GameEvent.HintUsed)
        emit(GameEvent.Message("提示已填入"))
        checkDigitCompletion()
        checkWin()
        changed()
    }

    fun completeGame() {
        if (state.ended) {
            return
        }
        val before = snapshotGame()
        state.values = state.solution.copyOf()
        state.statuses = Array(81) {
            if (state.puzzle[it] != 0) CellStatus.GIVEN else CellStatus.CONFIRMED
        }
        state.selected = null
        state.highlightedValue = 0
        pushHistory(HistoryAction.GameAction(before, snapshotGame()))
        emit(GameEvent.Message("已一键完成"))
        finishGame("成功")
        changed()
    }

    fun undo() {
        if (state.ended) {
            return
        }
        val action = state.undoStack.removeLastOrNull() ?: return
        applyHistory(action, useBefore = true)
        state.redoStack.addLast(action)
        changed()
    }

    fun redo() {
        if (state.ended) {
            return
        }
        val action = state.redoStack.removeLastOrNull() ?: return
        applyHistory(action, useBefore = false)
        state.undoStack.addLast(action)
        changed()
    }

    fun resetGame() {
        val wasEnded = state.ended
        state.resetSnapshot = snapshotGame()
        state.values = state.puzzle.copyOf()
        state.statuses = Array(81) {
            if (state.puzzle[it] != 0) CellStatus.GIVEN else CellStatus.EMPTY
        }
        state.selected = null
        state.highlightedValue = 0
        state.undoStack.clear()
        state.redoStack.clear()
        if (wasEnded) {
            state.hints = HINT_LIMIT
            state.mistakes = 0
            state.ended = false
            state.result = null
            state.startedAt = clock()
            state.hiddenAt = null
            state.completedDigits.clear()
            emit(GameEvent.ResetRestarted)
            emit(GameEvent.Message("已重置并重新计时，可撤销重置"))
        } else {
            emit(GameEvent.ResetKept)
            emit(GameEvent.Message("已清空填数，错误与提示次数保留"))
        }
        changed()
    }

    fun undoReset() {
        val snapshot = state.resetSnapshot
        if (snapshot == null) {
            emit(GameEvent.Message("没有可撤销的重置"))
            return
        }
        restoreSnapshot(snapshot)
        state.resetSnapshot = null
        emit(GameEvent.Message("已撤销重置"))
        changed()
    }

    fun hasProgress(): Boolean {
        for (index in 0 until 81) {
            if (state.values[index] != state.puzzle[index]) {
                return true
            }
        }
        return false
    }

    fun elapsedSeconds(): Long {
        if (state.ended) {
            return (state.endedAt - state.startedAt).coerceAtLeast(0) / 1000
        }
        val end = state.hiddenAt ?: clock()
        return (end - state.startedAt).coerceAtLeast(0) / 1000
    }

    fun onAppBackground() {
        if (state.ended || state.hiddenAt != null) {
            return
        }
        state.hiddenAt = clock()
    }

    fun onAppForeground() {
        val hiddenAt = state.hiddenAt ?: return
        state.startedAt += clock() - hiddenAt
        state.hiddenAt = null
        changed()
    }

    fun clearRecords() {
        records.clear()
        persistRecords(records)
        emit(GameEvent.RecordsChanged)
    }

    private fun setCellValue(index: Int, value: Int) {
        if (!state.canEdit(index)) {
            return
        }
        val before = snapshotCell(index)
        state.values[index] = value
        state.statuses[index] = if (value != 0) CellStatus.DRAFT else CellStatus.EMPTY
        state.highlightedValue = value
        val after = snapshotCell(index)
        pushHistory(HistoryAction.CellAction(index, before, after))
        state.redoStack.clear()
        emit(GameEvent.ValueChanged)
        checkDigitCompletion()
        changed()
    }

    private fun pickHintIndex(): Int? {
        val selected = state.selected
        if (selected != null && state.canEdit(selected)) {
            return selected
        }
        for (index in 0 until 81) {
            if (state.values[index] != state.solution[index] && state.statuses[index] != CellStatus.GIVEN) {
                return index
            }
        }
        return null
    }

    private fun checkWin() {
        if (state.values.contentEquals(state.solution)) {
            finishGame("成功")
        }
    }

    private fun finishGame(result: String) {
        if (state.ended) {
            return
        }
        state.ended = true
        state.result = result
        state.endedAt = clock()
        state.hiddenAt = null
        val seconds = elapsedSeconds()
        records.add(0, Record(result, seconds, state.seed, state.difficulty, clock()))
        while (records.size > MAX_RECORDS) {
            records.removeAt(records.size - 1)
        }
        runCatching { persistRecords(records) }
        emit(GameEvent.RecordsChanged)
        emit(if (result == "成功") GameEvent.GameWon else GameEvent.GameLost)
    }

    private fun applyHistory(action: HistoryAction, useBefore: Boolean) {
        when (action) {
            is HistoryAction.GameAction -> restoreSnapshot(if (useBefore) action.before else action.after)
            is HistoryAction.HintAction -> {
                state.hints = if (useBefore) action.hintsBefore else action.hintsAfter
                applyCell(action.index, if (useBefore) action.before else action.after)
            }
            is HistoryAction.CellAction -> applyCell(action.index, if (useBefore) action.before else action.after)
        }
    }

    private fun applyCell(index: Int, snapshot: CellSnapshot) {
        state.values[index] = snapshot.value
        state.statuses[index] = snapshot.status
        state.selected = index
        state.highlightedValue = snapshot.value
        checkDigitCompletion()
    }

    private fun snapshotCell(index: Int): CellSnapshot =
        CellSnapshot(state.values[index], state.statuses[index])

    private fun snapshotGame(): GameSnapshot = GameSnapshot(
        values = state.values.copyOf(),
        statuses = state.statuses.copyOf(),
        selected = state.selected,
        highlightedValue = state.highlightedValue,
        hints = state.hints,
        mistakes = state.mistakes,
        ended = state.ended,
        result = state.result,
        startedAt = state.startedAt,
        endedAt = state.endedAt
    )

    private fun restoreSnapshot(snapshot: GameSnapshot) {
        state.values = snapshot.values.copyOf()
        state.statuses = snapshot.statuses.copyOf()
        state.selected = snapshot.selected
        state.highlightedValue = snapshot.highlightedValue
        state.hints = snapshot.hints
        state.mistakes = snapshot.mistakes
        state.ended = snapshot.ended
        state.result = snapshot.result
        state.startedAt = snapshot.startedAt
        state.endedAt = snapshot.endedAt
        state.hiddenAt = null
        recomputeCompletedDigits()
    }

    private fun pushHistory(action: HistoryAction) {
        state.undoStack.addLast(action)
        state.redoStack.clear()
    }

    private fun checkDigitCompletion() {
        for (value in 1..9) {
            if (state.completedDigits.contains(value)) {
                continue
            }
            if (countFixed(value) >= 9) {
                state.completedDigits.add(value)
                emit(GameEvent.DigitCompleted(value))
            }
        }
    }

    private fun recomputeCompletedDigits() {
        state.completedDigits.clear()
        for (value in 1..9) {
            if (countFixed(value) >= 9) {
                state.completedDigits.add(value)
            }
        }
    }

    fun countFixed(value: Int): Int {
        var count = 0
        for (index in 0 until 81) {
            if (state.values[index] != value) {
                continue
            }
            val status = state.statuses[index]
            if (status == CellStatus.GIVEN || status == CellStatus.CONFIRMED || status == CellStatus.HINTED) {
                count += 1
            }
        }
        return count
    }

    private fun saveSeedStore() {
        pruneSeedStore()
        if (persistSeeds(seedStore)) {
            return
        }
        val oldest = seedStore.entries.sortedBy { seedRecency(it.value) }
        val dropCount = (oldest.size + 1) / 2
        for (entry in oldest.take(dropCount)) {
            seedStore.remove(entry.key)
        }
        if (persistSeeds(seedStore)) {
            return
        }
        seedStore.clear()
        persistSeeds(seedStore)
    }

    private fun pruneSeedStore() {
        if (seedStore.size <= MAX_SAVED_SEEDS) {
            return
        }
        val seeds = seedStore.keys.sortedBy { seedRecency(seedStore.getValue(it)) }
        val extra = seedStore.size - MAX_SAVED_SEEDS
        for (seed in seeds.take(extra)) {
            seedStore.remove(seed)
        }
    }

    private fun seedRecency(game: SavedGame): Long =
        if (game.lastUsedAt > 0) game.lastUsedAt else game.savedAt

    private fun createSeed(difficulty: Difficulty): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val suffix = buildString {
            repeat(6) { append(chars[Random.nextInt(chars.length)]) }
        }
        return "${difficulty.name}-$date-$suffix"
    }

    private fun cycleValue(value: Int): Int = when {
        value > 9 -> 0
        value < 0 -> 9
        else -> value
    }

    private fun emit(event: GameEvent) {
        onEvent?.invoke(event)
    }

    private fun changed() {
        onChange?.invoke()
    }
}
