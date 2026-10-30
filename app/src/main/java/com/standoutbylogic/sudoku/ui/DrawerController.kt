package com.standoutbylogic.sudoku.ui

import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 无 AndroidX 的右侧抽屉：遮罩 + panel 的 translationX 动画。
 * 打开后点遮罩关闭；MainActivity 负责点面板外与返回键关闭。
 */
class DrawerController(
    private val overlay: View,
    private val scrim: View,
    private val panel: View
) {

    var isOpen = false
        private set

    private val duration = 240L
    private val interpolator = DecelerateInterpolator()
    private val panelWidth = 300f * panel.context.resources.displayMetrics.density
    private val rect = Rect()

    init {
        panel.translationX = panelWidth
        scrim.alpha = 0f
        scrim.setOnClickListener { close() }
    }

    fun open() {
        if (isOpen) {
            return
        }
        isOpen = true
        overlay.visibility = View.VISIBLE
        panel.translationX = panelWidth
        panel.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .start()
        scrim.animate()
            .alpha(1f)
            .setDuration(duration)
            .start()
    }

    fun close(afterClose: (() -> Unit)? = null) {
        if (!isOpen) {
            afterClose?.invoke()
            return
        }
        isOpen = false
        panel.animate()
            .translationX(panelWidth)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .withEndAction {
                overlay.visibility = View.GONE
                afterClose?.invoke()
            }
            .start()
        scrim.animate()
            .alpha(0f)
            .setDuration(duration)
            .start()
    }

    /** screenX/screenY 使用 MotionEvent.rawX / rawY。 */
    fun containsPoint(screenX: Float, screenY: Float): Boolean {
        if (!isOpen) {
            return false
        }
        if (!panel.getGlobalVisibleRect(rect)) {
            return false
        }
        return rect.contains(screenX.toInt(), screenY.toInt())
    }
}
