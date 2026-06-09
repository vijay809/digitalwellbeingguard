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
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
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
        onContinueListener: (Boolean) -> Unit
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

            val cbExcludeApp = overlayView?.findViewById<CheckBox>(R.id.cbExcludeApp)

            btnContinue?.setOnClickListener {
                onContinueListener(cbExcludeApp?.isChecked == true)
                removeOverlay()
            }

            val btnUnlock = overlayView?.findViewById<ImageButton>(R.id.btnUnlock)
            val layoutNumpad = overlayView?.findViewById<LinearLayout>(R.id.layoutNumpad)
            val tvPinEntry = overlayView?.findViewById<TextView>(R.id.tvPinEntry)

            val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val savedPin = prefs.getString("app_pin", null)
            val hasPin = !savedPin.isNullOrEmpty()

            if (hasPin && delaySeconds > 0) {
                btnUnlock?.visibility = View.VISIBLE
            } else {
                btnUnlock?.visibility = View.GONE
            }

            var currentPinEntry = ""

            btnUnlock?.setOnClickListener {
                layoutNumpad?.visibility = View.VISIBLE
            }

            val numClick = View.OnClickListener { v ->
                if (currentPinEntry.length < 4) {
                    val num = (v as Button).text.toString()
                    currentPinEntry += num
                    tvPinEntry?.text = "* ".repeat(currentPinEntry.length).trimEnd()

                    if (currentPinEntry.length == 4) {
                        if (currentPinEntry == savedPin) {
                            // Correct PIN
                            layoutNumpad?.visibility = View.GONE
                            btnUnlock?.visibility = View.GONE
                            countDownTimer?.cancel()
                            btnContinue?.isEnabled = true
                            btnContinue?.text = "Continue"
                            cbExcludeApp?.visibility = View.VISIBLE
                        } else {
                            // Incorrect PIN
                            layoutNumpad?.visibility = View.GONE
                            btnUnlock?.isEnabled = false
                            btnUnlock?.backgroundTintList = ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.accent_peach))
                        }
                    }
                }
            }

            val delClick = View.OnClickListener {
                if (currentPinEntry.isNotEmpty()) {
                    currentPinEntry = currentPinEntry.dropLast(1)
                    tvPinEntry?.text = "* ".repeat(currentPinEntry.length).trimEnd()
                }
            }

            overlayView?.findViewById<Button>(R.id.btnNum1)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum2)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum3)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum4)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum5)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum6)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum7)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum8)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum9)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNum0)?.setOnClickListener(numClick)
            overlayView?.findViewById<Button>(R.id.btnNumDel)?.setOnClickListener(delClick)

            
            if (delaySeconds > 0) {
                btnContinue?.isEnabled = false
                btnContinue?.text = "Continue (${delaySeconds}s)"
                countDownTimer = object : CountDownTimer((delaySeconds * 1000).toLong(), 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000).toInt()
                        btnContinue?.text = "Continue (${seconds}s)"
                    }

                    override fun onFinish() {
                        btnContinue?.isEnabled = true
                        btnContinue?.text = "Continue"
                    }
                }.start()
            } else {
                btnContinue?.isEnabled = true
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
