package com.standoutbylogic.sudoku.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import com.standoutbylogic.sudoku.R

/**
 * 数字 1-9 进度条：显示已固定的个数，填满时变为金色。点击某一项即填入选中的格子。
 */
class NumberProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onDigitClick: ((Int) -> Unit)? = null

    private var counts = IntArray(10)
    private var pressedIndex = -1

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x14000000
    }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = 16f * scaledDensity
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f * scaledDensity
    }

    private val cellBg = context.getColor(R.color.cell_bg)
    private val goldSoft = context.getColor(R.color.gold_soft)
    private val borderColor = context.getColor(R.color.progress_border)
    private val ink = context.getColor(R.color.ink)
    private val countColor = context.getColor(R.color.progress_text)
    private val filledText = context.getColor(R.color.hinted_text)

    init {
        isClickable = true
    }

    fun setCounts(values: IntArray) {
        if (values.contentEquals(counts)) {
            return
        }
        counts = values.copyOf()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) {
            return
        }
        val itemWidth = width / 9f
        val gap = 1.5f * density
        val top = 2f * density
        val bottom = height - 2f * density
        val radius = 7f * density
        val digitOffset = -(digitPaint.ascent() + digitPaint.descent()) / 2f - 5f * density

        for (index in 0 until 9) {
            val value = index + 1
            val left = index * itemWidth + gap
            val right = (index + 1) * itemWidth - gap
            val filled = counts[value] >= 9

            fillPaint.color = if (filled) goldSoft else cellBg
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, fillPaint)

            strokePaint.color = borderColor
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, strokePaint)

            if (pressedIndex == value) {
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, pressedPaint)
            }

            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f

            digitPaint.color = if (filled) filledText else ink
            canvas.drawText(value.toString(), centerX, centerY + digitOffset, digitPaint)

            countPaint.color = countColor
            val count = counts[value].coerceIn(0, 9)
            canvas.drawText("$count/9", centerX, centerY + 11f * density, countPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = digitAt(event.x)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = digitAt(event.x)
                if (index != pressedIndex) {
                    pressedIndex = index
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val index = pressedIndex
                pressedIndex = -1
                invalidate()
                if (index in 1..9 && digitAt(event.x) == index) {
                    onDigitClick?.invoke(index)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun digitAt(x: Float): Int {
        if (width <= 0 || x < 0f || x >= width) {
            return -1
        }
        val index = (x / (width / 9f)).toInt()
        return (index + 1).coerceIn(1, 9)
    }
}
