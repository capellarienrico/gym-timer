package com.gymtimer.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gymtimer.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var timerService: TimerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as TimerService.LocalBinder
            val service = localBinder.getService()
            timerService = service
            isBound = true

            service.isAppInForeground = true
            service.onTickListener = { remaining, isRunning, isFinished ->
                runOnUiThread {
                    updateUI(remaining, isRunning, isFinished)
                }
            }

            updateUI(service.remainingSeconds, service.isRunning, service.isFinished)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permissão de notificação concedida ou negada
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ao pressionar "Voltar", minimiza em vez de destruir a Activity
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        startAndBindService()
        setupListeners()
        requestNotificationPermissionIfNeeded()
        checkOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
        timerService?.isAppInForeground = true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Acionado no instante em que o usuário aperta Home ou troca de app
        timerService?.isAppInForeground = false
    }

    override fun onStop() {
        super.onStop()
        timerService?.isAppInForeground = false
    }

    private fun startAndBindService() {
        val intent = Intent(this, TimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupListeners() {
        // Presets rápidos
        binding.btnPreset45s.setOnClickListener {
            timerService?.setTime(45)
            highlightPreset(binding.btnPreset45s)
        }
        binding.btnPreset1m.setOnClickListener {
            timerService?.setTime(60)
            highlightPreset(binding.btnPreset1m)
        }
        binding.btnPreset1m30s.setOnClickListener {
            timerService?.setTime(90)
            highlightPreset(binding.btnPreset1m30s)
        }

        // Ajustes finos
        binding.btnMinus15s.setOnClickListener {
            timerService?.addSeconds(-15)
        }
        binding.btnAdd15s.setOnClickListener {
            timerService?.addSeconds(15)
        }
        binding.btnAdd30s.setOnClickListener {
            timerService?.addSeconds(30)
        }

        // Botões de Ação
        binding.btnStartPause.setOnClickListener {
            val service = timerService ?: return@setOnClickListener

            if (!Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog()
            }

            if (service.isRunning) {
                service.pauseTimer()
            } else {
                service.startTimer()
            }
        }

        binding.btnReset.setOnClickListener {
            timerService?.resetTimer()
        }

        // Botão para minimizar direto para a janela flutuante
        binding.btnMinimize.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog()
            } else {
                moveTaskToBack(true)
            }
        }

        // Banner de permissão de overlay
        binding.btnGrantOverlay.setOnClickListener {
            requestOverlayPermission()
        }
    }

    private fun highlightPreset(activeButton: View) {
        val activeColor = ContextCompat.getColor(this, R.color.accent_green)
        val defaultColor = ContextCompat.getColor(this, R.color.card_bg)
        val textActive = ContextCompat.getColor(this, R.color.black_bg)
        val textDefault = ContextCompat.getColor(this, R.color.text_white)

        listOf(binding.btnPreset45s, binding.btnPreset1m, binding.btnPreset1m30s).forEach { btn ->
            if (btn == activeButton) {
                btn.setBackgroundColor(activeColor)
                btn.setTextColor(textActive)
            } else {
                btn.setBackgroundColor(defaultColor)
                btn.setTextColor(textDefault)
            }
        }
    }

    private fun updateUI(remainingSeconds: Long, isRunning: Boolean, isFinished: Boolean) {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        binding.tvMainTimer.text = String.format("%02d:%02d", minutes, seconds)

        when {
            isFinished -> {
                binding.tvStatus.text = "DESCANSO CONCLUÍDO!"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                binding.btnStartPause.text = getString(R.string.start)
                binding.btnStartPause.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_green))
            }
            isRunning -> {
                binding.tvStatus.text = "DESCANSO EM ANDAMENTO"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_orange))
                binding.btnStartPause.text = getString(R.string.pause)
                binding.btnStartPause.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_orange))
            }
            else -> {
                binding.tvStatus.text = "PRONTO PARA O DESCANSO"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnStartPause.text = getString(R.string.start)
                binding.btnStartPause.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_green))
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            binding.cardPermissionWarning.visibility = View.GONE
        } else {
            binding.cardPermissionWarning.visibility = View.VISIBLE
        }
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Janela Flutuante Necessária")
            .setMessage("Para exibir a contagem enquanto você usa outros apps (Spotify, WhatsApp), ative a permissão 'Sobrepor a outros apps'.")
            .setPositiveButton("Configurar") { _, _ ->
                requestOverlayPermission()
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir as configurações de sobreposição.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            timerService?.onTickListener = null
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
