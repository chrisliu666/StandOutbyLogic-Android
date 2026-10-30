package com.standoutbylogic.sudoku.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.standoutbylogic.sudoku.R
import com.standoutbylogic.sudoku.game.CellStatus
import com.standoutbylogic.sudoku.game.GameState
import kotlin.math.abs
import kotlin.math.min

/**
 * 自绘 9x9 棋盘 + 手势状态机：
 * 单击选中、上下滑动 ±1、双击确认、长按清除、点棋盘外由 Activity 取消选中。
 */
class SudokuBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: GameState? = null
        set(value) {
            field = value
            invalidate()
        }

    var onSelectCell: ((Int) -> Unit)? = null
    var onConfirmCell: ((Int) -> Unit)? = null
    var onClearCell: ((Int) -> Unit)? = null
    var onAdjustCell: ((Int, Int) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val cellBg = context.getColor(R.color.cell_bg)
    private val givenBg = context.getColor(R.color.given_bg)
    private val givenText = context.getColor(R.color.given_text)
    private val hintedBg = context.getColor(R.color.hinted_bg)
    private val hintedText = context.getColor(R.color.hinted_text)
    private val relatedBg = context.getColor(R.color.related_bg)
    private val sameBg = context.getColor(R.color.same_bg)
    private val sameText = context.getColor(R.color.same_text)
    private val draftText = context.getColor(R.color.blue)
    private val lineColor = context.getColor(R.color.line)
    private val lineStrong = context.getColor(R.color.line_strong)
    private val sakura = context.getColor(R.color.sakura)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = sakura
    }

    private var boardLeft = 0f
    private var boardTop = 0f
    private var side = 0f
    private var cellSize = 0f
    private var swipeStepPx = 40f

    private var flashStart = -1L

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var downIndex = -1
    private var longFired = false
    private var dragging = false
    private var horizontalDrag = false
    private var swipeStep = 0
    private var lastTapTime = 0L
    private var lastTapIndex = -1

    private val longPressRunnable = Runnable {
        if (downIndex >= 0 && !dragging) {
            longFired = true
            onClearCell?.invoke(downIndex)
        }
    }

    init {
        isClickable = true
    }

    fun flashBorder() {
        flashStart = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val margin = 4f * density
        side = min(w, h).toFloat() - margin * 2f
        if (side < 0f) {
            side = 0f
        }
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f
        cellSize = side / 9f
        swipeStepPx = maxOf(cellSize * 0.55f, 24f * density)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = game ?: return
        if (side <= 0f) {
            return
        }

        val selected = state.selected
        val highlight = state.highlightedValue
        val radius = 5f * density

        // 1. 格子底色
        for (index in 0 until 81) {
            val row = index / 9
            val col = index % 9
            val left = boardLeft + col * cellSize
            val top = boardTop + row * cellSize
            val status = state.statuses[index]
            val value = state.values[index]
            val fixed = status == CellStatus.GIVEN || status == CellStatus.CONFIRMED
            val same = highlight != 0 && value == highlight
            val related = selected != null && isRelated(index, selected)
            fillPaint.color = when {
                status == CellStatus.HINTED -> hintedBg
                fixed -> givenBg
                same -> sameBg
                related -> relatedBg
                else -> cellBg
            }
            canvas.drawRect(left, top, left + cellSize, top + cellSize, fillPaint)
        }

        // 2. 网格线
        for (index in 0..9) {
            val strong = index % 3 == 0
            strokePaint.color = if (strong) lineStrong else lineColor
            strokePaint.strokeWidth = if (strong) 2.5f * density else 1f * density
            val offset = index * cellSize
            val x = boardLeft + offset
            val y = boardTop + offset
            canvas.drawLine(x, boardTop, x, boardTop + side, strokePaint)
            canvas.drawLine(boardLeft, y, boardLeft + side, y, strokePaint)
        }

        // 3. 数字
        digitPaint.textSize = cellSize * 0.62f
        val centerOffset = -(digitPaint.ascent() + digitPaint.descent()) / 2f
        for (index in 0 until 81) {
            val value = state.values[index]
            if (value == 0) {
                continue
            }
            val status = state.statuses[index]
            val same = highlight != 0 && value == highlight
            digitPaint.color = when {
                same -> sameText
                status == CellStatus.GIVEN || status == CellStatus.CONFIRMED -> givenText
                status == CellStatus.HINTED -> hintedText
                else -> draftText
            }
            val row = index / 9
            val col = index % 9
            val centerX = boardLeft + (col + 0.5f) * cellSize
            val centerY = boardTop + (row + 0.5f) * cellSize
            canvas.drawText(value.toString(), centerX, centerY + centerOffset, digitPaint)
        }

        // 4. 选中描边
        if (selected != null && selected in 0..80) {
            val row = selected / 9
            val col = selected % 9
            val inset = 2f * density
            strokePaint.color = sakura
            strokePaint.strokeWidth = 3f * density
            canvas.drawRoundRect(
                boardLeft + col * cellSize + inset,
                boardTop + row * cellSize + inset,
                boardLeft + (col + 1) * cellSize - inset,
                boardTop + (row + 1) * cellSize - inset,
                radius,
                radius,
                strokePaint
            )
        }

        // 5. 数字填满时边框闪光
        if (flashStart >= 0L) {
            val elapsed = SystemClock.uptimeMillis() - flashStart
            if (elapsed < FLASH_DURATION_MS) {
                val progress = elapsed.toFloat() / FLASH_DURATION_MS
                flashPaint.alpha = ((1f - progress) * 255f).toInt().coerceIn(0, 255)
                flashPaint.strokeWidth = 4f * density
                val inset = 2f * density
                canvas.drawRoundRect(
                    boardLeft + inset,
                    boardTop + inset,
                    boardLeft + side - inset,
                    boardTop + side - inset,
                    radius,
                    radius,
                    flashPaint
                )
                postInvalidateOnAnimation()
            } else {
                flashStart = -1L
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount > 1) {
                    return false
                }
                val index = cellAt(event.x, event.y)
                if (index < 0) {
                    return false
                }
                downIndex = index
                downX = event.x
                downY = event.y
                longFired = false
                dragging = false
                horizontalDrag = false
                swipeStep = 0
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, LONG_PRESS_MS)
                // 单击立即选中，不等待双击判定
                onSelectCell?.invoke(index)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (downIndex < 0) {
                    return true
                }
                if (event.pointerCount > 1) {
                    removeCallbacks(longPressRunnable)
                    dragging = true
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    horizontalDrag = abs(dx) > abs(dy)
                    removeCallbacks(longPressRunnable)
                }
                if (dragging && !horizontalDrag && !longFired) {
                    val step = ((downY - event.y) / swipeStepPx).toInt()
                    if (step != swipeStep) {
                        val diff = step - swipeStep
                        swipeStep = step
                        val direction = if (diff > 0) 1 else -1
                        repeat(minOf(abs(diff), 3)) {
                            onAdjustCell?.invoke(downIndex, direction)
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (longFired || dragging) {
                    // 长按/滑动后不参与双击判定，避免误确认
                    lastTapIndex = -1
                    lastTapTime = 0L
                    resetGesture()
                    return true
                }
                val index = downIndex
                if (index >= 0) {
                    val now = event.eventTime
                    if (index == lastTapIndex && now - lastTapTime in 0L..doubleTapTimeout) {
                        lastTapIndex = -1
                        lastTapTime = 0L
                        onConfirmCell?.invoke(index)
                    } else {
                        lastTapIndex = index
                        lastTapTime = now
                    }
                }
                resetGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                resetGesture()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    private fun resetGesture() {
        removeCallbacks(longPressRunnable)
        downIndex = -1
        longFired = false
        dragging = false
        horizontalDrag = false
        swipeStep = 0
    }

    private fun cellAt(x: Float, y: Float): Int {
        if (side <= 0f) {
            return -1
        }
        if (x < boardLeft || x >= boardLeft + side || y < boardTop || y >= boardTop + side) {
            return -1
        }
        val col = ((x - boardLeft) / cellSize).toInt().coerceIn(0, 8)
        val row = ((y - boardTop) / cellSize).toInt().coerceIn(0, 8)
        return row * 9 + col
    }

    private fun isRelated(a: Int, b: Int): Boolean {
        val rowA = a / 9
        val colA = a % 9
        val rowB = b / 9
        val colB = b % 9
        if (rowA == rowB || colA == colB) {
            return true
        }
        return rowA / 3 == rowB / 3 && colA / 3 == colB / 3
    }

    private companion object {
        const val LONG_PRESS_MS = 500L
        const val FLASH_DURATION_MS = 340L
    }
}
