package com.example.xl0801monitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TARGET_NAME = "XL0801"
        private const val MANUFACTURER_ID = 0x0901
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvRssi: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etRaw: EditText
    private lateinit var detailsContainer: LinearLayout
    private lateinit var btnDetails: Button
    private var scanning = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) startScan() else toast("缺少蓝牙扫描权限")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTemperature = findViewById(R.id.tvTemperature)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvRssi = findViewById(R.id.tvRssi)
        tvMeta = findViewById(R.id.tvMeta)
        tvStatus = findViewById(R.id.tvStatus)
        etRaw = findViewById(R.id.etRaw)
        detailsContainer = findViewById(R.id.detailsContainer)
        btnDetails = findViewById(R.id.btnDetails)

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        findViewById<Button>(R.id.btnScan).setOnClickListener { ensurePermissionsAndScan() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopScan() }
        btnDetails.setOnClickListener {
            val visible = detailsContainer.visibility == View.VISIBLE
            detailsContainer.visibility = if (visible) View.GONE else View.VISIBLE
            btnDetails.text = if (visible) "显示广播信息" else "隐藏广播信息"
        }

        ensurePermissionsAndScan()
    }

    private fun ensurePermissionsAndScan() {
        if (!bluetoothAdapter.isEnabled) {
            toast("请先打开蓝牙")
            return
        }
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                permissions += Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray()) else startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanning) {
            setStatus("正在扫描")
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            toast("BLE 扫描器不可用")
            return
        }
        val filters = listOf(ScanFilter.Builder().setDeviceName(TARGET_NAME).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, scanCallback)
        scanning = true
        setStatus("正在扫描")
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) {
            setStatus("已停止")
            return
        }
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
        setStatus("已停止")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handleResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handleResult(it) }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            setStatus("扫描失败：$errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val name = record.deviceName ?: result.device.name ?: return
        if (name != TARGET_NAME) return

        val payload = record.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
        if (payload.size < 3) return

        val rawTempUnsigned =
            ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)

        // XL0801 temperature uses signed 16-bit big-endian, unit = 0.1 °C.
        // Example: FF 9C = -100 -> -10.0 °C
        val rawTempSigned =
            if (rawTempUnsigned and 0x8000 != 0) rawTempUnsigned - 0x10000
            else rawTempUnsigned

        val temperature = rawTempSigned / 10.0
        val humidity = payload[2].toInt() and 0xFF

        val embeddedMac = if (payload.size >= 9) {
            payload.copyOfRange(3, 9).joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        } else result.device.address

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        tvTemperature.text = String.format(Locale.getDefault(), "%.1f °C", temperature)
        tvHumidity.text = "$humidity %"
        tvRssi.text = "${result.rssi} dBm"
        tvMeta.text = "广播 MAC：$embeddedMac    最后广播：$now"
        setStatus("已发现设备，持续监听中")

        val hex = payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        etRaw.append("[$now]\nAddress: ${result.device.address}\nRSSI: ${result.rssi} dBm\n0x0901: $hex\nTemperature: ${String.format(Locale.getDefault(), "%.1f", temperature)} °C\nHumidity: $humidity %\nEmbedded MAC/ID: $embeddedMac\n-----------------------------------------------\n")
        etRaw.setSelection(etRaw.text.length)
    }

    private fun setStatus(text: String) { tvStatus.text = "状态：$text" }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }
}
