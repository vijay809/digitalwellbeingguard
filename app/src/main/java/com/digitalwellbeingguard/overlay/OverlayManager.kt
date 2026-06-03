package com.digitalwellbeingguard.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.digitalwellbeingguard.R

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tvTimer: TextView? = null
    private var countDownTimer: CountDownTimer? = null

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun showOverlay(
        time: String,
        message: String,
        delaySeconds: Int = 0,
        onContinueListener: () -> Unit
    ) {
        if (isOverlayShowing()) return

        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            layoutParams.gravity = Gravity.CENTER

            val inflater = LayoutInflater.from(context)
            overlayView = inflater.inflate(R.layout.overlay_warning, null)

            tvTimer = overlayView?.findViewById(R.id.tvTimer)
            val tvMessage = overlayView?.findViewById<TextView>(R.id.tvMessage)
            val btnContinue = overlayView?.findViewById<Button>(R.id.btnContinue)

            tvTimer?.text = time
            tvMessage?.text = message

            btnContinue?.setOnClickListener {
                onContinueListener()
                removeOverlay()
            }
            
            if (delaySeconds > 0) {
                btnContinue?.isEnabled = false
                btnContinue?.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
                btnContinue?.text = "Continue (${delaySeconds}s)"
                countDownTimer = object : CountDownTimer((delaySeconds * 1000).toLong(), 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000).toInt()
                        btnContinue?.text = "Continue (${seconds}s)"
                    }

                    override fun onFinish() {
                        btnContinue?.isEnabled = true
                        btnContinue?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
                        btnContinue?.text = "Continue"
                    }
                }.start()
            } else {
                btnContinue?.isEnabled = true
                btnContinue?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
                btnContinue?.text = "Continue"
            }

            windowManager?.addView(overlayView, layoutParams)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateTimer(time: String) {
        if (isOverlayShowing()) {
            tvTimer?.post {
                tvTimer?.text = time
            }
        }
    }

    fun removeOverlay() {
        countDownTimer?.cancel()
        countDownTimer = null
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                overlayView = null
                tvTimer = null
            }
        }
    }

    fun isOverlayShowing(): Boolean {
        return overlayView != null
    }
}
