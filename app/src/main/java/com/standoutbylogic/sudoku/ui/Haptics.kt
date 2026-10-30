package com.standoutbylogic.sudoku.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 5 类触觉反馈。优先使用系统 HapticFeedback（尊重用户设置），
 * 需要波形时使用 Vibrator（VIBRATE 为普通权限，无需运行时申请）。
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.applicationContext
                .getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
        anchor.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 填入/修改数字：齿轮般的普通 Click。 */
    fun input(anchor: View) {
        anchor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** 确认正确：干脆的重震。 */
    fun confirmCorrect(anchor: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            anchor.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else if (!playWaveform(longArrayOf(0, 45), intArrayOf(0, 235))) {
            anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** 确认错误：两次急促低频脉冲。 */
    fun confirmWrong(anchor: View) {
        if (!playWaveform(longArrayOf(0, 40, 65, 40), intArrayOf(0, 220, 0, 220))) {
            anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** 数字 1-9 全部填满：一次平滑绵长的渐强渐弱震动。 */
    fun digitComplete(anchor: View) {
        val durations = longArrayOf(0, 30, 30, 30, 30, 30, 30, 30, 30, 30)
        val amplitudes = intArrayOf(0, 40, 70, 100, 130, 160, 190, 160, 120, 80)
        if (!playWaveform(durations, amplitudes)) {
            anchor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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
