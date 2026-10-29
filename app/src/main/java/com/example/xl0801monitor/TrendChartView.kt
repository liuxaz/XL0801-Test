package com.example.xl0801monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<SensorPoint> = emptyList()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 120, 120, 120)
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 30f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun setData(points: List<SensorPoint>) {
        data = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 82f
        val right = width - 22f
        if (right <= left) return

        val sectionH = height / 3f
        drawSection(canvas, 0f, sectionH, "温度 °C", data.map { it.temperature.toFloat() }, 0)
        drawSection(canvas, sectionH, sectionH * 2f, "湿度 %", data.map { it.humidity.toFloat() }, 1)
        drawSection(canvas, sectionH * 2f, height.toFloat(), "RSSI dBm", data.map { it.rssi.toFloat() }, 2)
    }

    private fun drawSection(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        title: String,
        values: List<Float>,
        series: Int
    ) {
        val left = 82f
        val right = width - 22f
        val plotTop = top + 38f
        val plotBottom = bottom - 28f

        textPaint.textSize = 28f
        canvas.drawText(title, 8f, top + 30f, textPaint)

        for (i in 0..3) {
            val y = plotTop + (plotBottom - plotTop) * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (values.isEmpty()) {
            textPaint.textSize = 24f
            canvas.drawText("等待数据...", left + 12f, (plotTop + plotBottom) / 2f, textPaint)
            return
        }

        var minV = values.minOrNull() ?: 0f
        var maxV = values.maxOrNull() ?: 1f
        if (series == 1) {
            minV = max(0f, minV - 5f)
            maxV = min(100f, maxV + 5f)
        } else {
            val pad = max(1f, (maxV - minV) * 0.15f)
            minV -= pad
            maxV += pad
        }
        if (maxV - minV < 0.01f) {
            maxV += 1f
            minV -= 1f
        }

        textPaint.textSize = 21f
        canvas.drawText(String.format("%.1f", maxV), 8f, plotTop + 8f, textPaint)
        canvas.drawText(String.format("%.1f", minV), 8f, plotBottom, textPaint)

        linePaint.color = when (series) {
            0 -> Color.rgb(230, 90, 70)
            1 -> Color.rgb(50, 130, 220)
            else -> Color.rgb(70, 150, 90)
        }

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1).toFloat()
            val ratio = (value - minV) / (maxV - minV)
            val y = plotBottom - ratio * (plotBottom - plotTop)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}
