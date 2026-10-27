package com.gymtimer.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var vibrationHelper: VibrationHelper
    private lateinit var floatingWidgetManager: FloatingWidgetManager
    private var wakeLock: PowerManager.WakeLock? = null

    var totalSeconds: Long = 60
        private set
    var remainingSeconds: Long = 60
        private set
    var isRunning: Boolean = false
        private set
    var isFinished: Boolean = false
        private set

    private var targetEndTime: Long = 0L

    var isAppInForeground: Boolean = true
        set(value) {
            field = value
            handler.post { handleFloatingVisibility() }
        }

    var onTickListener: ((remaining: Long, isRunning: Boolean, isFinished: Boolean) -> Unit)? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val now = System.currentTimeMillis()
                val diff = targetEndTime - now
                if (diff <= 0) {
                    remainingSeconds = 0
                    onFinished()
                } else {
                    remainingSeconds = (diff + 999L) / 1000L
                    notifyStateChanged()
                    handler.postDelayed(this, 500L)
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
        initWakeLock()
        createNotificationChannel()
        promoteToForeground()
    }

    private fun initWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymTimer::CountdownWakeLock")
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Erro ao adquirir WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Erro ao liberar WakeLock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()

        when (intent?.action) {
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_STOP -> resetTimer()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun promoteToForeground() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Erro ao iniciar foreground service", e)
        }
    }

    fun setTime(seconds: Long) {
        pauseTimer()
        totalSeconds = seconds.coerceAtLeast(1)
        remainingSeconds = totalSeconds
        isFinished = false
        vibrationHelper.cancel()
        notifyStateChanged()
    }

    fun addSeconds(seconds: Long) {
        if (isRunning) {
            targetEndTime += (seconds * 1000L)
            val now = System.currentTimeMillis()
            remainingSeconds = ((targetEndTime - now + 999L) / 1000L).coerceAtLeast(1)
        } else {
            remainingSeconds = (remainingSeconds + seconds).coerceAtLeast(1)
        }
        if (remainingSeconds > totalSeconds) {
            totalSeconds = remainingSeconds
        }
        notifyStateChanged()
    }

    fun startTimer() {
        if (remainingSeconds <= 0) {
            remainingSeconds = totalSeconds
        }
        targetEndTime = System.currentTimeMillis() + remainingSeconds * 1000L
        isRunning = true
        isFinished = false
        vibrationHelper.cancel()
        acquireWakeLock()
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
        notifyStateChanged()
        handler.post { handleFloatingVisibility() }
    }

    fun pauseTimer() {
        if (isRunning) {
            val now = System.currentTimeMillis()
            remainingSeconds = ((targetEndTime - now + 999L) / 1000L).coerceAtLeast(0)
            isRunning = false
            releaseWakeLock()
            handler.removeCallbacks(timerRunnable)
            notifyStateChanged()
            handler.post { handleFloatingVisibility() }
        }
    }

    fun togglePlayPause() {
        if (isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    fun resetTimer() {
        isRunning = false
        releaseWakeLock()
        handler.removeCallbacks(timerRunnable)
        remainingSeconds = totalSeconds
        isFinished = false
        vibrationHelper.cancel()
        notifyStateChanged()
        handler.post { floatingWidgetManager.hide() }
    }

    private fun onFinished() {
        isRunning = false
        isFinished = true
        releaseWakeLock()
        handler.removeCallbacks(timerRunnable)
        // Vibração máxima configurada como ALARM (toca mesmo no Não Perturbe)
        vibrationHelper.vibrateMax()
        notifyStateChanged()
        handler.post { handleFloatingVisibility() }
    }

    private fun notifyStateChanged() {
        onTickListener?.invoke(remainingSeconds, isRunning, isFinished)
        floatingWidgetManager.updateTime(remainingSeconds)
        floatingWidgetManager.updateState(isRunning)
        updateNotification()
    }

    private fun handleFloatingVisibility() {
        if (!isAppInForeground && (isRunning || (remainingSeconds > 0 && remainingSeconds < totalSeconds))) {
            floatingWidgetManager.show()
            floatingWidgetManager.updateTime(remainingSeconds)
            floatingWidgetManager.updateState(isRunning)
        } else {
            floatingWidgetManager.hide()
        }
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
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

        val toggleActionTitle = if (isRunning) "Pausar" else "Retomar"
        val toggleActionIcon = if (isRunning) R.drawable.ic_pause else R.drawable.ic_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(openPendingIntent)
            .addAction(toggleActionIcon, toggleActionTitle, togglePendingIntent)
            .addAction(R.drawable.ic_stop, "Zerar", stopPendingIntent)
            .setOngoing(isRunning)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e("TimerService", "Erro ao atualizar notificação", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        handler.removeCallbacks(timerRunnable)
        vibrationHelper.cancel()
        floatingWidgetManager.hide()
    }

    companion object {
        const val CHANNEL_ID = "gym_timer_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE = "com.gymtimer.app.ACTION_TOGGLE"
        const val ACTION_STOP = "com.gymtimer.app.ACTION_STOP"
    }
}
