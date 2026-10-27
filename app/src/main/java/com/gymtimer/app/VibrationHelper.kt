package com.gymtimer.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Responsável pela vibração na intensidade máxima ao finalizar o timer.
 * Configurado com AudioAttributes.USAGE_ALARM para vibrar mesmo em modo silencioso / Não Perturbe.
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

    private val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()

    fun vibrateMax() {
        if (!vibrator.hasVibrator()) return

        // Padrão de pulsos fortes: 600ms ligado, 200ms pausa, 600ms ligado, 200ms pausa, 1200ms ligado
        val timings = longArrayOf(0, 600, 200, 600, 200, 1200)
        // 255 é a amplitude máxima suportada pela API do Android
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect, alarmAttributes)
            } catch (e: Exception) {
                // Fallback caso o hardware não suporte controle fino de amplitude
                val fallbackEffect = VibrationEffect.createWaveform(timings, -1)
                vibrator.vibrate(fallbackEffect, alarmAttributes)
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
