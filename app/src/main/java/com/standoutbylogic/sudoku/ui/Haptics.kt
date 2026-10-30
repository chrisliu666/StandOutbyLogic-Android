package com.standoutbylogic.sudoku.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 触觉反馈。所有事件共用确认正确/错误的同一手感（两段急促脉冲），
 * 震动不可用时才回退到系统触感与单次短震。
 * 设置里的「振动」开关通过 [enabled] 统一关闭全部反馈。
 */
class Haptics(context: Context) {

    /** 设置里的振动开关；false 时所有反馈静默。 */
    var enabled: Boolean = true

    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)

    /** 选中：与确认相同的双脉冲。 */
    fun select(anchor: View) {
        play(anchor, HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 填入/修改数字：与确认相同的双脉冲。 */
    fun input(anchor: View) {
        play(anchor, HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** 确认正确：两段急促脉冲。 */
    fun confirmCorrect(anchor: View) {
        play(
            anchor,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        )
    }

    /** 确认错误：与确认正确相同类型。 */
    fun confirmWrong(anchor: View) {
        play(anchor, HapticFeedbackConstants.LONG_PRESS)
    }

    /** 提示填入正确答案：与确认相同的双脉冲。 */
    fun hint(anchor: View) {
        play(anchor, HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** 数字 1-9 全部填满：与确认相同的双脉冲（边框闪光仍由棋盘负责）。 */
    fun digitComplete(anchor: View) {
        play(anchor, HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** 统一手感：默认强度时序波形 → 系统触感 → 旧版单次震动。 */
    private fun play(anchor: View, fallback: Int) {
        if (!enabled) {
            return
        }
        if (vibrateTimings(DOUBLE_PULSE)) {
            return
        }
        if (anchor.performHapticFeedback(fallback)) {
            return
        }
        vibrate(80)
    }

    /** 单次震动：默认振幅 → 最大振幅 → 旧版接口，逐级回退。 */
    private fun vibrate(durationMs: Long): Boolean {
        val device = vibrator ?: return false
        try {
            device.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            return true
        } catch (error: Exception) {
            Log.w(TAG, "oneShot(default) failed", error)
        }
        try {
            device.vibrate(VibrationEffect.createOneShot(durationMs, 255))
            return true
        } catch (error: Exception) {
            Log.w(TAG, "oneShot(255) failed", error)
        }
        return try {
            @Suppress("DEPRECATION")
            device.vibrate(durationMs)
            true
        } catch (error: Exception) {
            Log.w(TAG, "legacy vibrate failed", error)
            false
        }
    }

    /** 默认强度的时序波形（无振幅数组），避免部分机型忽略振幅导致静默。 */
    private fun vibrateTimings(durations: LongArray): Boolean {
        val device = vibrator ?: return false
        try {
            device.cancel()
            device.vibrate(VibrationEffect.createWaveform(durations, -1))
            return true
        } catch (error: Exception) {
            Log.w(TAG, "timings waveform failed", error)
            return false
        }
    }

    private companion object {
        const val TAG = "SudokuHaptics"

        /** 两段急促脉冲：所有震动反馈共用这一手感。 */
        val DOUBLE_PULSE = longArrayOf(0, 40, 65, 40)

        fun resolveVibrator(context: Context): Vibrator? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator?.let { return it }
                } catch (error: Exception) {
                    Log.w(TAG, "VibratorManager unavailable", error)
                }
            }
            return try {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } catch (error: Exception) {
                Log.w(TAG, "Vibrator unavailable", error)
                null
            }
        }
    }
}
