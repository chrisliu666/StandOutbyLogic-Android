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
 * 触觉反馈。系统 HapticFeedback 与 Vibrator 双通道：
 * 关键手感（确认正确/错误、提示、数字填满）必须走 Vibrator，
 * 且依次尝试 默认振幅 → 最大振幅 → 旧版接口，
 * 避免部分机型“调用成功但无震动”。
 * 设置里的「振动」开关通过 [enabled] 统一关闭全部反馈。
 */
class Haptics(context: Context) {

    /** 设置里的振动开关；false 时所有反馈静默。 */
    var enabled: Boolean = true

    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)

    /** 选中：极轻微短促的 Tick。 */
    fun select(anchor: View) {
        if (!enabled) {
            return
        }
        if (!anchor.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
            vibrate(15)
        }
    }

    /** 填入/修改数字：齿轮般的普通 Click。 */
    fun input(anchor: View) {
        if (!enabled) {
            return
        }
        if (!anchor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) {
            vibrate(20)
        }
    }

    /** 确认正确：与确认错误相同类型的两段脉冲（默认强度时序波形）。 */
    fun confirmCorrect(anchor: View) {
        if (!enabled) {
            return
        }
        if (!vibrateTimings(DOUBLE_PULSE)) {
            anchor.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            )
            vibrate(80)
        }
    }

    /** 确认错误：两次急促脉冲，与确认正确相同类型。 */
    fun confirmWrong(anchor: View) {
        if (!enabled) {
            return
        }
        if (!vibrateTimings(DOUBLE_PULSE)) {
            anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** 提示填入正确答案：单次中等脉冲。 */
    fun hint(anchor: View) {
        if (!enabled) {
            return
        }
        if (!vibrate(40)) {
            anchor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** 数字 1-9 全部填满：一次平滑绵长的渐强渐弱震动。 */
    fun digitComplete(anchor: View) {
        if (!enabled) {
            return
        }
        val durations = longArrayOf(0, 30, 30, 30, 30, 30, 30, 30, 30, 30)
        val amplitudes = intArrayOf(0, 40, 70, 100, 130, 160, 190, 160, 120, 80)
        if (!vibrateAmplitudes(durations, amplitudes) && !vibrateTimings(durations)) {
            anchor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
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

    /** 默认强度的时序波形（无振幅数组），用于双脉冲等节奏型手感。 */
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

    /** 带振幅数组的波形，仅用于渐强渐弱这类无法用默认强度表达的节奏。 */
    private fun vibrateAmplitudes(durations: LongArray, amplitudes: IntArray): Boolean {
        val device = vibrator ?: return false
        try {
            device.cancel()
            device.vibrate(VibrationEffect.createWaveform(durations, amplitudes, -1))
            return true
        } catch (error: Exception) {
            Log.w(TAG, "amplitude waveform failed", error)
            return false
        }
    }

    private companion object {
        const val TAG = "SudokuHaptics"

        /** 两段急促脉冲：确认正确与确认错误共用。 */
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
