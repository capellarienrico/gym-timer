package com.gymtimer.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Responsável pela vibração na intensidade máxima ao finalizar o timer.
 * Não toca nenhum som/áudio.
 */
class VibrationHelper(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun vibrateMax() {
        if (!vibrator.hasVibrator()) return

        // Padrão de 3 pulsos fortes: 600ms ligado, 200ms pausa, 600ms ligado, 200ms pausa, 1000ms ligado
        val timings = longArrayOf(0, 600, 200, 600, 200, 1000)
        // 255 é a intensidade máxima suportada no Android
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } catch (e: Exception) {
                // Fallback caso o hardware não suporte controle fino de amplitude
                val fallbackEffect = VibrationEffect.createWaveform(timings, -1)
                vibrator.vibrate(fallbackEffect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    fun cancel() {
        vibrator.cancel()
    }
}
