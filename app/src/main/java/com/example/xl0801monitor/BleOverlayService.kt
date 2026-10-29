package com.example.xl0801monitor

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.abs

class BleOverlayService : Service() {
    companion object {
        private const val TARGET_NAME = "XL0801"
        private const val MANUFACTURER_ID = 0x0901
        private const val CHANNEL_ID = "xl0801_monitor"
        private const val NOTIFICATION_ID = 8001
    }

    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var tempView: TextView? = null
    private var humView: TextView? = null
    private var signalView: SignalStrengthView? = null

    private val adapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SHOW_DASHBOARD, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("蓝牙温湿度计")
                .setContentText("正在监听 BLE 广播")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        )
        showOverlay()
        startScan()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = LayoutInflater.from(this).inflate(R.layout.overlay_temperature, null)
        tempView = overlay!!.findViewById(R.id.tvOverlayTemp)
        humView = overlay!!.findViewById(R.id.tvOverlayHumidity)
        signalView = overlay!!.findViewById(R.id.signalView)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 30
        p.y = 120

        var px = 0
        var py = 0
        var tx = 0f
        var ty = 0f
        var moved = false

        overlay!!.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    px = p.x; py = p.y; tx = e.rawX; ty = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - tx) > 8 || abs(e.rawY - ty) > 8) moved = true
                    p.x = px + (e.rawX - tx).toInt()
                    p.y = py + (e.rawY - ty).toInt()
                    wm.updateViewLayout(overlay, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openDashboard()
                    true
                }
                else -> false
            }
        }
        wm.addView(overlay, p)
    }

    private fun openDashboard() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SHOW_DASHBOARD, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val scanner = adapter.bluetoothLeScanner ?: return
        val filters = listOf(ScanFilter.Builder().setDeviceName(TARGET_NAME).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = parse(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { parse(it) }
    }

    private fun parse(result: ScanResult) {
        val payload = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
        if (payload.size < 3) return

        val rawUnsigned = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val rawSigned = if (rawUnsigned and 0x8000 != 0) rawUnsigned - 0x10000 else rawUnsigned
        val temperature = rawSigned / 10.0
        val humidity = payload[2].toInt() and 0xFF
        val rssi = result.rssi
        val mac = embeddedMac(payload)

        tempView?.text = String.format(Locale.getDefault(), "%.1f°C", temperature)
        humView?.text = "$humidity%"
        signalView?.setRssi(rssi)

        SensorHistory.update(
            SensorLatest(
                System.currentTimeMillis(),
                temperature,
                humidity,
                rssi,
                mac
            )
        )
    }

    private fun embeddedMac(payload: ByteArray): String {
        if (payload.size < 9) return "--:--:--:--:--:--"
        return payload.copyOfRange(3, 9).joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        try { adapter.bluetoothLeScanner?.stopScan(callback) } catch (_: Exception) {}
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "蓝牙温湿度计", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
