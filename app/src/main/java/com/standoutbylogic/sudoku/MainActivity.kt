package com.standoutbylogic.sudoku

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import com.standoutbylogic.sudoku.game.Difficulty
import com.standoutbylogic.sudoku.game.GameController
import com.standoutbylogic.sudoku.game.GameEvent
import com.standoutbylogic.sudoku.game.MAX_MISTAKES
import com.standoutbylogic.sudoku.game.PrefsStore
import com.standoutbylogic.sudoku.ui.DrawerController
import com.standoutbylogic.sudoku.ui.Haptics
import com.standoutbylogic.sudoku.ui.NumberProgressView
import com.standoutbylogic.sudoku.ui.SudokuBoardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var controller: GameController
    private lateinit var prefs: PrefsStore
    private lateinit var haptics: Haptics
    private lateinit var drawer: DrawerController

    private lateinit var titleText: TextView
    private lateinit var timerText: TextView
    private lateinit var mistakesText: TextView
    private lateinit var hintsText: TextView
    private lateinit var menuBtn: TextView
    private lateinit var adaLine: TextView
    private lateinit var toastCard: TextView
    private lateinit var board: SudokuBoardView
    private lateinit var numberProgress: NumberProgressView
    private lateinit var keypadRow: LinearLayout
    private lateinit var actionsRow: LinearLayout
    private lateinit var confirmBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var hintBtn: Button
    private lateinit var againBtn: Button

    private lateinit var difficultyGroup: RadioGroup
    private lateinit var radioEasy: android.widget.RadioButton
    private lateinit var radioMedium: android.widget.RadioButton
    private lateinit var radioHard: android.widget.RadioButton
    private lateinit var radioExpert: android.widget.RadioButton
    private lateinit var moreHeader: Button
    private lateinit var moreBody: LinearLayout
    private lateinit var currentSeedText: TextView
    private lateinit var seedInput: EditText
    private lateinit var loadSeedBtn: Button
    private lateinit var newGameBtn: Button
    private lateinit var undoBtn: Button
    private lateinit var redoBtn: Button
    private lateinit var resetBtn: Button
    private lateinit var undoResetBtn: Button
    private lateinit var completeBtn: Button
    private lateinit var copySeedBtn: Button
    private lateinit var clearRecordsBtn: Button
    private lateinit var recordsContainer: LinearLayout

    private val digitViews = ArrayList<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var generationToken = 0
    private var loading = false
    private var suppressingDifficulty = false
    private var lastToastText: String? = null
    private var lastToastAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 500L)
        }
    }

    private var toastGeneration = 0
    private val hideToastRunnable = Runnable { hideToast(toastGeneration) }

    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsStore(this)
        controller = GameController(
            initialSeeds = prefs.loadSeeds(),
            initialRecords = prefs.loadRecords(),
            clock = { SystemClock.elapsedRealtime() },
            persistSeeds = prefs::saveSeeds,
            persistRecords = prefs::saveRecords
        )
        haptics = Haptics(this)

        bindViews()
        buildKeypad()
        wireViews()

        drawer = DrawerController(
            findViewById(R.id.drawerOverlay),
            findViewById(R.id.drawerScrim),
            findViewById(R.id.drawerPanel)
        )

        controller.onChange = { render() }
        controller.onEvent = { handleEvent(it) }
        board.game = controller.state

        controller.state.difficulty = Difficulty.MEDIUM
        syncDifficultyRadio()
        render()

        launchGame(null, Difficulty.MEDIUM)
    }

    override fun onStart() {
        super.onStart()
        controller.onAppForeground()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        controller.onAppBackground()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (drawer.isOpen) {
            drawer.close()
            return
        }
        if (controller.state.selected != null) {
            controller.selectCell(null)
            return
        }
        super.onBackPressed()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            if (drawer.isOpen) {
                if (!drawer.containsPoint(ev.rawX, ev.rawY)) {
                    drawer.close()
                    return true
                }
            } else if (controller.state.selected != null && !isInsideInteractive(ev.rawX, ev.rawY)) {
                controller.selectCell(null)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun bindViews() {
        titleText = findViewById(R.id.titleText)
        timerText = findViewById(R.id.timerText)
        mistakesText = findViewById(R.id.mistakesText)
        hintsText = findViewById(R.id.hintsText)
        menuBtn = findViewById(R.id.menuBtn)
        adaLine = findViewById(R.id.adaLine)
        toastCard = findViewById(R.id.toastCard)
        board = findViewById(R.id.board)
        numberProgress = findViewById(R.id.numberProgress)
        keypadRow = findViewById(R.id.keypadRow)
        actionsRow = findViewById(R.id.actionsRow)
        confirmBtn = findViewById(R.id.confirmBtn)
        clearBtn = findViewById(R.id.clearBtn)
        hintBtn = findViewById(R.id.hintBtn)
        againBtn = findViewById(R.id.againBtn)

        difficultyGroup = findViewById(R.id.difficultyGroup)
        radioEasy = findViewById(R.id.radioEasy)
        radioMedium = findViewById(R.id.radioMedium)
        radioHard = findViewById(R.id.radioHard)
        radioExpert = findViewById(R.id.radioExpert)
        moreHeader = findViewById(R.id.moreHeader)
        moreBody = findViewById(R.id.moreBody)
        currentSeedText = findViewById(R.id.currentSeedText)
        seedInput = findViewById(R.id.seedInput)
        loadSeedBtn = findViewById(R.id.loadSeedBtn)
        newGameBtn = findViewById(R.id.newGameBtn)
        undoBtn = findViewById(R.id.undoBtn)
        redoBtn = findViewById(R.id.redoBtn)
        resetBtn = findViewById(R.id.resetBtn)
        undoResetBtn = findViewById(R.id.undoResetBtn)
        completeBtn = findViewById(R.id.completeBtn)
        copySeedBtn = findViewById(R.id.copySeedBtn)
        clearRecordsBtn = findViewById(R.id.clearRecordsBtn)
        recordsContainer = findViewById(R.id.recordsContainer)
    }

    private fun buildKeypad() {
        val margin = (2 * density).toInt()
        for (value in 1..9) {
            val button = TextView(this).apply {
                text = value.toString()
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.ink))
                setBackgroundResource(R.drawable.bg_button)
                isClickable = true
                isFocusable = true
                contentDescription = "数字 $value"
                setOnClickListener { controller.inputDigit(value) }
            }
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            params.marginStart = margin
            params.marginEnd = margin
            keypadRow.addView(button, params)
            digitViews.add(button)
        }
    }

    private fun wireViews() {
        menuBtn.setOnClickListener {
            seedInput.setText(controller.state.seed)
            renderRecords()
            drawer.open()
        }

        board.onSelectCell = { controller.selectCell(it) }
        board.onConfirmCell = { controller.confirmCell(it) }
        board.onClearCell = { controller.clearCellAt(it) }
        board.onAdjustCell = { index, delta -> controller.adjustCell(index, delta) }
        numberProgress.onDigitClick = { controller.inputDigit(it) }

        confirmBtn.setOnClickListener { controller.confirmSelected() }
        clearBtn.setOnClickListener { controller.clearSelectedCell() }
        hintBtn.setOnClickListener { controller.useHint() }
        againBtn.setOnClickListener { launchGame(null, controller.state.difficulty) }

        moreHeader.setOnClickListener {
            val expanded = moreBody.visibility == View.VISIBLE
            moreBody.visibility = if (expanded) View.GONE else View.VISIBLE
            moreHeader.text = if (expanded) "更多操作 ▸" else "更多操作 ▾"
        }

        difficultyGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressingDifficulty || loading) {
                return@setOnCheckedChangeListener
            }
            val target = difficultyForRadio(checkedId) ?: return@setOnCheckedChangeListener
            if (target == controller.state.difficulty) {
                return@setOnCheckedChangeListener
            }
            if (controller.hasProgress() && !controller.state.ended) {
                AlertDialog.Builder(this)
                    .setTitle("切换难度")
                    .setMessage("切换难度会开始一道新题目，当前进度将丢失。")
                    .setPositiveButton("确定") { _, _ -> launchGame(null, target) }
                    .setNegativeButton("取消") { _, _ -> syncDifficultyRadio() }
                    .show()
            } else {
                launchGame(null, target)
            }
        }

        newGameBtn.setOnClickListener {
            drawer.close()
            launchGame(null, controller.state.difficulty)
        }

        undoBtn.setOnClickListener { controller.undo() }
        redoBtn.setOnClickListener { controller.redo() }
        resetBtn.setOnClickListener { controller.resetGame() }
        undoResetBtn.setOnClickListener { controller.undoReset() }
        completeBtn.setOnClickListener { controller.completeGame() }

        loadSeedBtn.setOnClickListener {
            val seed = seedInput.text.toString().trim()
            if (seed.isEmpty()) {
                showToast("请输入种子码")
                return@setOnClickListener
            }
            drawer.close()
            launchGame(seed, controller.state.difficulty)
        }

        copySeedBtn.setOnClickListener { copySeed(controller.state.seed) }
        clearRecordsBtn.setOnClickListener { controller.clearRecords() }
    }

    private fun launchGame(seed: String?, difficulty: Difficulty) {
        val token = ++generationToken
        loading = true
        setAda(ADA_LOADING)
        render()
        executor.execute {
            controller.onChange = null
            var failure: Throwable? = null
            try {
                controller.startGame(seed, difficulty)
            } catch (error: Throwable) {
                failure = error
            }
            handler.post {
                if (isFinishing || isDestroyed) {
                    return@post
                }
                controller.onChange = { render() }
                if (token != generationToken) {
                    return@post
                }
                loading = false
                if (failure != null) {
                    showToast("题目生成失败，请重试")
                    setAda("题目没有准备好，请再试一次。")
                } else {
                    seedInput.setText(controller.state.seed)
                    syncDifficultyRadio()
                    setAda(ADA_READY)
                }
                render()
            }
        }
    }

    private fun render() {
        val state = controller.state
        updateTimer()
        mistakesText.text = "错误 ${state.mistakes}/$MAX_MISTAKES"
        hintsText.text = "提示 ${state.hints}"
        currentSeedText.text = "当前种子：${state.seed.ifEmpty { "-" }}"
        numberProgress.setCounts(buildCounts())
        board.invalidate()
        renderControls()
    }

    private fun renderControls() {
        val state = controller.state
        val idle = !loading
        board.isEnabled = idle

        for (view in digitViews) {
            view.isEnabled = idle && !state.ended
            view.alpha = if (view.isEnabled) 1f else 0.45f
        }

        val active = idle && !state.ended
        confirmBtn.isEnabled = active
        clearBtn.isEnabled = active
        hintBtn.isEnabled = active && state.hints > 0
        newGameBtn.isEnabled = idle
        loadSeedBtn.isEnabled = idle
        resetBtn.isEnabled = idle
        undoResetBtn.isEnabled = state.resetSnapshot != null
        completeBtn.isEnabled = active
        undoBtn.isEnabled = active && state.undoStack.isNotEmpty()
        redoBtn.isEnabled = active && state.redoStack.isNotEmpty()
        againBtn.visibility = if (idle && state.ended && state.result == "失败") View.VISIBLE else View.GONE
        copySeedBtn.isEnabled = state.seed.isNotEmpty()
    }

    private fun buildCounts(): IntArray {
        val counts = IntArray(10)
        for (value in 1..9) {
            counts[value] = controller.countFixed(value)
        }
        return counts
    }

    private fun updateTimer() {
        timerText.text = formatTime(controller.elapsedSeconds())
    }

    private fun handleEvent(event: GameEvent) {
        when (event) {
            is GameEvent.Selected -> {
                haptics.select(board)
                setAda(ADA_READY)
            }

            is GameEvent.Deselected -> setAda(ADA_READY)

            is GameEvent.ValueChanged -> {
                haptics.input(board)
                setAda("临时数字已写入，推导无误再确认。")
            }

            is GameEvent.ConfirmCorrect -> {
                haptics.confirmCorrect(board)
                setAda("正确，这个数字锁定了。")
            }

            is GameEvent.ConfirmWrong -> {
                haptics.confirmWrong(board)
                setAda("这个数字不对，再推导一次。")
            }

            is GameEvent.HintUsed -> {
                haptics.hint(board)
                setAda("提示已填入，黄格就是答案。")
            }

            is GameEvent.DigitCompleted -> {
                haptics.digitComplete(board)
                board.flashBorder()
                setAda("数字 ${event.value} 已全部就位。")
            }

            is GameEvent.GameWon -> setAda("全部完成，逻辑漂亮。")

            is GameEvent.GameLost -> setAda("错误已达上限，可以再来一局。")

            is GameEvent.ResetRestarted, is GameEvent.ResetKept -> setAda(ADA_READY)

            is GameEvent.Message -> showToast(event.text)

            is GameEvent.RecordsChanged -> renderRecords()
        }
    }

    private fun setAda(text: String) {
        if (adaLine.text.toString() == text) {
            return
        }
        adaLine.text = text
        adaLine.alpha = 0.55f
        adaLine.animate().alpha(1f).setDuration(160L).start()
    }

    private fun syncDifficultyRadio() {
        suppressingDifficulty = true
        val id = when (controller.state.difficulty) {
            Difficulty.EASY -> R.id.radioEasy
            Difficulty.MEDIUM -> R.id.radioMedium
            Difficulty.HARD -> R.id.radioHard
            Difficulty.EXPERT -> R.id.radioExpert
        }
        difficultyGroup.check(id)
        suppressingDifficulty = false
    }

    private fun difficultyForRadio(checkedId: Int): Difficulty? = when (checkedId) {
        R.id.radioEasy -> Difficulty.EASY
        R.id.radioMedium -> Difficulty.MEDIUM
        R.id.radioHard -> Difficulty.HARD
        R.id.radioExpert -> Difficulty.EXPERT
        else -> null
    }

    private fun renderRecords() {
        recordsContainer.removeAllViews()
        val records = controller.recordsSnapshot()
        if (records.isEmpty()) {
            recordsContainer.addView(TextView(this).apply {
                text = getString(R.string.no_records)
                setTextColor(getColor(R.color.muted))
                textSize = 12f
                setPadding(0, verticalPadding(), 0, verticalPadding())
            })
            return
        }
        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        for (record in records) {
            recordsContainer.addView(TextView(this).apply {
                text = buildString {
                    append(record.result)
                    append("｜")
                    append(record.difficulty.label)
                    append("｜")
                    append(formatTime(record.seconds))
                    append("｜")
                    append(dateFormat.format(Date(record.atMillis)))
                    append("｜")
                    append(record.seed)
                }
                textSize = 12f
                setTextColor(getColor(R.color.ink))
                setPadding(0, verticalPadding(), 0, verticalPadding())
                isClickable = true
                setOnClickListener {
                    seedInput.setText(record.seed)
                    copySeed(record.seed)
                }
            })
        }
    }

    private fun copySeed(seed: String) {
        if (seed.isEmpty()) {
            showToast("当前没有种子码")
            return
        }
        try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("种子码", seed))
            showToast("种子码已复制")
        } catch (_: Exception) {
            showToast("复制失败，请手动记录")
        }
    }

    /** 居中浮层提示，对应 Web 版居中 toast：淡入缩放，约 1.6s 后淡出。 */
    private fun showToast(message: String) {
        val now = SystemClock.uptimeMillis()
        if (message == lastToastText && now - lastToastAt < 1200L) {
            return
        }
        lastToastText = message
        lastToastAt = now

        toastGeneration += 1
        handler.removeCallbacks(hideToastRunnable)
        toastCard.animate().cancel()
        toastCard.text = message
        toastCard.visibility = View.VISIBLE
        toastCard.alpha = 0f
        toastCard.scaleX = 0.96f
        toastCard.scaleY = 0.96f
        toastCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160L)
            .start()
        handler.postDelayed(hideToastRunnable, 1600L)
    }

    private fun hideToast(generation: Int) {
        if (generation != toastGeneration || toastCard.visibility != View.VISIBLE) {
            return
        }
        toastCard.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction {
                if (generation == toastGeneration) {
                    toastCard.visibility = View.GONE
                }
            }
            .start()
    }

    private fun isInsideInteractive(rawX: Float, rawY: Float): Boolean {
        val targets = arrayOf<View>(board, numberProgress, keypadRow, actionsRow, againBtn, menuBtn)
        val rect = Rect()
        for (view in targets) {
            if (view.visibility != View.VISIBLE) {
                continue
            }
            if (view.getGlobalVisibleRect(rect) && rect.contains(rawX.toInt(), rawY.toInt())) {
                return true
            }
        }
        return false
    }

    private fun verticalPadding(): Int = (8 * density).toInt()

    private fun formatTime(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    private companion object {
        const val ADA_READY = "选一个格子，我会帮你盯住同样的数字。"
        const val ADA_LOADING = "正在摆盘，请稍等…"
    }
}
