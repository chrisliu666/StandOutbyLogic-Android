package com.standoutbylogic.sudoku.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 触觉反馈。优先使用系统 HapticFeedback（尊重用户设置），
 * 关键手感（确认正确/错误、提示、数字填满）额外走 Vibrator，
 * 且尽量使用默认强度（DEFAULT_AMPLITUDE）：部分机型会忽略振幅数组，
 * 导致“调用成功但无震动”。
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private val canVibrate: Boolean = try {
        vibrator?.hasVibrator() == true
    } catch (_: Exception) {
        false
    }

    /** 选中：极轻微短促的 Tick。 */
    fun select(anchor: View) {
        if (!anchor.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
            playOneShot(12)
        }
    }

    /** 填入/修改数字：齿轮般的普通 Click。 */
    fun input(anchor: View) {
        if (!anchor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) {
            playOneShot(18)
        }
    }

    /**
     * 确认正确：系统触感 + 60ms 默认强度震动双保险。
     * 两者叠加，确保所有机型都有明确手感。
     */
    fun confirmCorrect(anchor: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        playOneShot(60)
        anchor.performHapticFeedback(constant)
    }

    /** 确认错误：两次急促脉冲。 */
    fun confirmWrong(anchor: View) {
        if (!playTimings(longArrayOf(0, 40, 65, 40))) {
            anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** 提示填入正确答案：单次中等脉冲。 */
    fun hint(anchor: View) {
        if (!playOneShot(40)) {
            anchor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** 数字 1-9 全部填满：一次平滑绵长的渐强渐弱震动。 */
    fun digitComplete(anchor: View) {
        val durations = longArrayOf(0, 30, 30, 30, 30, 30, 30, 30, 30, 30)
        val amplitudes = intArrayOf(0, 40, 70, 100, 130, 160, 190, 160, 120, 80)
        if (!playWaveform(durations, amplitudes) && !playTimings(durations)) {
            anchor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** 默认强度单次震动：不指定振幅，兼容性最好。 */
    private fun playOneShot(durationMs: Long): Boolean {
        val device = vibrator ?: return false
        if (!canVibrate) {
            return false
        }
        return try {
            device.cancel()
            device.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 默认强度的时序波形（无振幅数组），用于双脉冲等节奏型手感。 */
    private fun playTimings(durations: LongArray): Boolean {
        val device = vibrator ?: return false
        if (!canVibrate) {
            return false
        }
        return try {
            device.cancel()
            device.vibrate(VibrationEffect.createWaveform(durations, -1))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun playWaveform(durations: LongArray, amplitudes: IntArray): Boolean {
        val device = vibrator ?: return false
        if (!canVibrate) {
            return false
        }
        return try {
            device.cancel()
            device.vibrate(VibrationEffect.createWaveform(durations, amplitudes, -1))
            true
        } catch (_: Exception) {
            false
        }
    }
}
