package com.example.xl0801monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SHOW_DASHBOARD = "show_dashboard"
    }

    private val bleReq = 1001
    private val overlayReq = 1002
    private var dashboardMode = false
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDashboard()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dashboardMode = intent.getBooleanExtra(EXTRA_SHOW_DASHBOARD, false)
        proceed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_SHOW_DASHBOARD, false) == true) {
            dashboardMode = true
            showDashboard()
        }
    }

    private fun proceed() {
        if (!hasBlePermissions()) {
            requestBlePermissions()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                overlayReq
            )
            return
        }

        ContextCompat.startForegroundService(this, Intent(this, BleOverlayService::class.java))

        if (dashboardMode) showDashboard() else finish()
    }

    private fun showDashboard() {
        setContentView(R.layout.activity_main)
        refreshDashboard()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    private fun refreshDashboard() {
        if (!dashboardMode) return
        val points = SensorHistory.snapshot()
        val latest = points.lastOrNull()
        val chart = findViewById<TrendChartView>(R.id.trendChart) ?: return
        chart.setData(points)

        if (latest != null) {
            findViewById<TextView>(R.id.tvTemperature).text = String.format(Locale.getDefault(), "%.1f°C", latest.temperature)
            findViewById<TextView>(R.id.tvHumidity).text = "${latest.humidity}%"
            findViewById<TextView>(R.id.tvRssi).text = "${latest.rssi} dBm"
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(latest.time))
            findViewById<TextView>(R.id.tvLastUpdate).text = "最后更新：$time"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    private fun hasBlePermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), bleReq)
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), bleReq)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == bleReq && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) proceed()
        else finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayReq) {
            if (Settings.canDrawOverlays(this)) proceed() else finish()
        }
    }
}
