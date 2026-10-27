package com.gymtimer.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

class FloatingWidgetManager(
    private val context: Context,
    private val onPlayPauseClicked: () -> Unit,
    private val onStopClicked: () -> Unit
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAttached = false

    private var tvFloatingTime: TextView? = null
    private var btnPlayPause: ImageView? = null

    fun show() {
        if (!Settings.canDrawOverlays(context) || isViewAttached) {
            return
        }

        try {
            // ContextThemeWrapper garante que atributos de tema resolvam sem erro no Service
            val themedContext = ContextThemeWrapper(context, R.style.Theme_GymTimer)
            val inflater = LayoutInflater.from(themedContext)
            val view = inflater.inflate(R.layout.layout_floating_timer, null)
            floatingView = view

            tvFloatingTime = view.findViewById(R.id.tvFloatingTime)
            btnPlayPause = view.findViewById(R.id.btnFloatingPlayPause)
            val btnStop = view.findViewById<ImageView>(R.id.btnFloatingStop)
            val dragHandle = view.findViewById<ImageView>(R.id.ivFloatingDragHandle)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 80
                y = 250
            }
            layoutParams = params

            setupTouchAndDrag(view, params)

            btnPlayPause?.setOnClickListener {
                onPlayPauseClicked()
            }

            btnStop.setOnClickListener {
                onStopClicked()
                hide()
            }

            dragHandle.setOnClickListener {
                openMainActivity()
            }

            tvFloatingTime?.setOnClickListener {
                openMainActivity()
            }

            windowManager.addView(view, params)
            isViewAttached = true
            Log.d("FloatingWidgetManager", "Janela flutuante exibida com sucesso.")
        } catch (e: Exception) {
            Log.e("FloatingWidgetManager", "Falha ao exibir janela flutuante", e)
            isViewAttached = false
            floatingView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchAndDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > 8 || abs(deltaY) > 8) {
                        isDragging = true
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        if (isViewAttached && floatingView != null) {
                            try {
                                windowManager.updateViewLayout(view, params)
                            } catch (e: Exception) {
                                Log.e("FloatingWidgetManager", "Erro ao mover layout", e)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Toque simples na janela reabre o app principal
                        openMainActivity()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    fun updateTime(secondsRemaining: Long) {
        val minutes = secondsRemaining / 60
        val seconds = secondsRemaining % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)
        tvFloatingTime?.text = formatted
    }

    fun updateState(isRunning: Boolean) {
        if (isRunning) {
            btnPlayPause?.setImageResource(R.drawable.ic_pause)
        } else {
            btnPlayPause?.setImageResource(R.drawable.ic_play)
        }
    }

    fun hide() {
        if (isViewAttached && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                Log.e("FloatingWidgetManager", "Erro ao ocultar janela flutuante", e)
            }
            isViewAttached = false
            floatingView = null
        }
    }

    fun isShown(): Boolean = isViewAttached
}
