package com.changegps.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.changegps.mock.JoyVector
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Round analog joystick. Reports a [JoyVector] whose angle is a compass bearing
 * (up = north = 0°, right = east = 90°) and strength in 0..1, or null on release.
 */
class JoystickView(context: Context) : View(context) {

    var listener: ((JoyVector?) -> Unit)? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 60, 60, 60)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.argb(200, 255, 255, 255)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 33, 150, 243)
    }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var knobX = 0f
    private var knobY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f - 8f
        knobX = cx
        knobY = cy
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius, basePaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(knobX, knobY, radius * 0.4f, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                val dist = hypot(dx, dy)
                val clamped = min(dist, radius)
                val ratio = if (dist == 0f) 0f else clamped / dist
                knobX = cx + dx * ratio
                knobY = cy + dy * ratio
                // Compass bearing: up (dy<0) = north(0), right (dx>0) = east(90).
                val bearing = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
                val strength = (clamped / radius).coerceIn(0f, 1f).toDouble()
                listener?.invoke(if (strength < 0.05) null else JoyVector(bearing, strength))
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = cx
                knobY = cy
                listener?.invoke(null)
                invalidate()
            }
        }
        return true
    }
}
