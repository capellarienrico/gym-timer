package com.gymtimer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var vibrationHelper: VibrationHelper
    private lateinit var floatingWidgetManager: FloatingWidgetManager

    var totalSeconds: Long = 60
        private set
    var remainingSeconds: Long = 60
        private set
    var isRunning: Boolean = false
        private set
    var isFinished: Boolean = false
        private set

    var isAppInForeground: Boolean = true
        set(value) {
            field = value
            handleFloatingVisibility()
        }

    var onTickListener: ((remaining: Long, isRunning: Boolean, isFinished: Boolean) -> Unit)? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning && remainingSeconds > 0) {
                remainingSeconds--
                notifyStateChanged()
                if (remainingSeconds <= 0) {
                    onFinished()
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        vibrationHelper = VibrationHelper(this)
        floatingWidgetManager = FloatingWidgetManager(
            context = this,
            onPlayPauseClicked = { togglePlayPause() },
            onStopClicked = { resetTimer() }
        )
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun setTime(seconds: Long) {
        pauseTimer()
        totalSeconds = seconds.coerceAtLeast(1)
        remainingSeconds = totalSeconds
        isFinished = false
        vibrationHelper.cancel()
        notifyStateChanged()
    }

    fun addSeconds(seconds: Long) {
        remainingSeconds = (remainingSeconds + seconds).coerceAtLeast(1)
        if (remainingSeconds > totalSeconds) {
            totalSeconds = remainingSeconds
        }
        notifyStateChanged()
    }

    fun startTimer() {
        if (remainingSeconds <= 0) {
            remainingSeconds = totalSeconds
        }
        isRunning = true
        isFinished = false
        vibrationHelper.cancel()
        handler.removeCallbacks(timerRunnable)
        handler.postDelayed(timerRunnable, 1000)
        notifyStateChanged()
        handleFloatingVisibility()
    }

    fun pauseTimer() {
        isRunning = false
        handler.removeCallbacks(timerRunnable)
        notifyStateChanged()
    }

    fun togglePlayPause() {
        if (isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    fun resetTimer() {
        pauseTimer()
        remainingSeconds = totalSeconds
        isFinished = false
        vibrationHelper.cancel()
        notifyStateChanged()
        floatingWidgetManager.hide()
    }

    private fun onFinished() {
        isRunning = false
        isFinished = true
        handler.removeCallbacks(timerRunnable)
        // Vibração na intensidade máxima sem tocar som nenhum
        vibrationHelper.vibrateMax()
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        onTickListener?.invoke(remainingSeconds, isRunning, isFinished)
        floatingWidgetManager.updateTime(remainingSeconds)
        floatingWidgetManager.updateState(isRunning)
        updateNotification()
    }

    private fun handleFloatingVisibility() {
        if (!isAppInForeground && (isRunning || remainingSeconds > 0)) {
            floatingWidgetManager.show()
            floatingWidgetManager.updateTime(remainingSeconds)
            floatingWidgetManager.updateState(isRunning)
        } else {
            floatingWidgetManager.hide()
        }
    }

    fun forceShowFloating() {
        floatingWidgetManager.show()
        floatingWidgetManager.updateTime(remainingSeconds)
        floatingWidgetManager.updateState(isRunning)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeStr = String.format("%02d:%02d", minutes, seconds)
        val statusText = when {
            isFinished -> "Descanso finalizado!"
            isRunning -> "Descansando... $timeStr"
            else -> "Pausado em $timeStr"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        vibrationHelper.cancel()
        floatingWidgetManager.hide()
    }

    companion object {
        const val CHANNEL_ID = "gym_timer_foreground_channel"
        const val NOTIFICATION_ID = 1001
    }
}
