package com.example.xl0801monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<SensorPoint> = emptyList()
    private val density = resources.displayMetrics.density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 228, 232)
        strokeWidth = 1f * density
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(115, 120, 128)
        textSize = 11f * resources.displayMetrics.scaledDensity
    }
    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(231, 93, 76)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val humidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(48, 132, 221)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.scaledDensity
    }

    fun setData(points: List<SensorPoint>) {
        data = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = 50f * density
        val right = width - 50f * density
        val top = 42f * density
        val bottom = height - 42f * density
        if (right <= left || bottom <= top) return

        drawLegend(canvas, left, 21f * density)

        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (data.isEmpty()) {
            axisPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("等待 BLE 数据...", width / 2f, (top + bottom) / 2f, axisPaint)
            return
        }

        val temps = data.map { it.temperature.toFloat() }
        val hums = data.map { it.humidity.toFloat() }

        var tempMin = floor((temps.minOrNull() ?: 0f) - 1f)
        var tempMax = ceil((temps.maxOrNull() ?: 1f) + 1f)
        if (tempMax - tempMin < 4f) {
            val mid = (tempMax + tempMin) / 2f
            tempMin = floor(mid - 2f)
            tempMax = ceil(mid + 2f)
        }

        var humMin = max(0f, floor((hums.minOrNull() ?: 0f) - 5f))
        var humMax = min(100f, ceil((hums.maxOrNull() ?: 100f) + 5f))
        if (humMax - humMin < 10f) {
            val mid = (humMax + humMin) / 2f
            humMin = max(0f, floor(mid - 5f))
            humMax = min(100f, ceil(mid + 5f))
        }

        axisPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.getDefault(), "%.0f°", tempMax), left - 7f * density, top + 4f * density, axisPaint)
        canvas.drawText(String.format(Locale.getDefault(), "%.0f°", tempMin), left - 7f * density, bottom + 4f * density, axisPaint)

        axisPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(String.format(Locale.getDefault(), "%.0f%%", humMax), right + 7f * density, top + 4f * density, axisPaint)
        canvas.drawText(String.format(Locale.getDefault(), "%.0f%%", humMin), right + 7f * density, bottom + 4f * density, axisPaint)

        drawSeries(canvas, data.map { it.temperature.toFloat() }, tempMin, tempMax, left, right, top, bottom, tempPaint)
        drawSeries(canvas, data.map { it.humidity.toFloat() }, humMin, humMax, left, right, top, bottom, humidPaint)
        drawTimeAxis(canvas, left, right, bottom)
    }

    private fun drawLegend(canvas: Canvas, x: Float, y: Float) {
        val dot = 4f * density
        tempPaint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, dot, tempPaint)
        tempPaint.style = Paint.Style.STROKE
        legendPaint.color = Color.rgb(70, 72, 76)
        canvas.drawText("温度", x + 9f * density, y + 4f * density, legendPaint)

        val x2 = x + 68f * density
        humidPaint.style = Paint.Style.FILL
        canvas.drawCircle(x2, y, dot, humidPaint)
        humidPaint.style = Paint.Style.STROKE
        canvas.drawText("湿度", x2 + 9f * density, y + 4f * density, legendPaint)
    }

    private fun drawSeries(
        canvas: Canvas,
        values: List<Float>,
        minV: Float,
        maxV: Float,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        paint: Paint
    ) {
        if (values.isEmpty()) return
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) (left + right) / 2f else left + (right - left) * index / (values.size - 1f)
            val ratio = ((value - minV) / (maxV - minV)).coerceIn(0f, 1f)
            val y = bottom - ratio * (bottom - top)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawTimeAxis(canvas: Canvas, left: Float, right: Float, bottom: Float) {
        if (data.isEmpty()) return
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val indices = if (data.size < 3) {
            data.indices.toList()
        } else {
            listOf(0, data.size / 2, data.size - 1).distinct()
        }

        axisPaint.textAlign = Paint.Align.CENTER
        indices.forEach { idx ->
            val x = if (data.size == 1) (left + right) / 2f else left + (right - left) * idx / (data.size - 1f)
            val label = formatter.format(Date(data[idx].time))
            canvas.drawText(label, x, bottom + 24f * density, axisPaint)
        }
    }
}
