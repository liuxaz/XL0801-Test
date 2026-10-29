package com.example.xl0801monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SignalStrengthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rssi: Int = -100
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setRssi(value: Int) {
        if (rssi != value) {
            rssi = value
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension((22 * density).toInt(), (18 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val activeBars = when {
            rssi >= -60 -> 4
            rssi >= -70 -> 3
            rssi >= -80 -> 2
            else -> 1
        }

        val gap = width * 0.08f
        val barWidth = (width - gap * 3) / 4f
        val bottom = height.toFloat()
        val heights = floatArrayOf(0.32f, 0.52f, 0.72f, 0.94f)

        for (i in 0..3) {
            paint.color = if (i < activeBars) Color.WHITE else Color.argb(70, 255, 255, 255)
            val left = i * (barWidth + gap)
            val top = bottom - height * heights[i]
            canvas.drawRoundRect(left, top, left + barWidth, bottom, barWidth * 0.32f, barWidth * 0.32f, paint)
        }
    }
}
