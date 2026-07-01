package com.changegps.overlay

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.changegps.mock.JoyVector
import com.changegps.mock.Mode
import com.changegps.mock.MockController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Floating joystick that hovers over other apps (e.g. Pikmin Bloom). It only
 * mutates [MockController]; the already-running [com.changegps.mock.MockLocationService]
 * consumes the joystick vector and moves the mock location.
 *
 * Window flags (see plan):
 *  - NOT_FOCUSABLE: don't steal keyboard focus from the underlying game.
 *  - NOT_TOUCH_MODAL: let taps outside the panel pass through to the game.
 *  - LAYOUT_NO_LIMITS: allow smooth dragging to screen edges.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var speedLabel: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        MockController.setMode(Mode.JOYSTICK)
        addPanel()
        observeSpeed()
    }

    /** Live-update the speed label from the shared state (Main dispatcher = safe UI). */
    private fun observeSpeed() = scope.launch {
        MockController.state
            .map { it.currentSpeedKmh }
            .distinctUntilChanged()
            .collect { kmh ->
                speedLabel?.text = "速度：%.1f km/h".format(kmh)
            }
    }

    private fun addPanel() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        // Drag handle row with a close button.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val handle = TextView(this).apply {
            text = "⠿ 拖曳"
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(2), dp(12), dp(2))
        }
        val close = Button(this).apply {
            text = "✕"
            setOnClickListener { stopSelf() }
        }
        header.addView(handle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close)

        val joystick = JoystickView(this).apply {
            listener = { v: JoyVector? -> MockController.setJoystick(v) }
        }

        val speed = TextView(this).apply {
            text = "速度：0.0 km/h"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(2))
        }
        speedLabel = speed

        container.addView(header)
        container.addView(speed)
        container.addView(joystick, LinearLayout.LayoutParams(dp(140), dp(140)))

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        attachDrag(handle)
        wm.addView(container, params)
        panel = container
    }

    /** Drag the whole panel by the header handle. */
    private fun attachDrag(handle: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    clampToScreen()
                    panel?.let { wm.updateViewLayout(it, params) }
                    true
                }
                else -> false
            }
        }
    }

    /** Keep the panel on-screen — also re-run after rotation. */
    private fun clampToScreen() {
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - dp(60)).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - dp(60)).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clampToScreen()
        panel?.let { wm.updateViewLayout(it, params) }
    }

    override fun onDestroy() {
        scope.cancel()
        MockController.setJoystick(null)
        speedLabel = null
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
