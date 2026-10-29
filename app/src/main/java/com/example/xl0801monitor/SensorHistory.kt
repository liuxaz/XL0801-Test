package com.example.xl0801monitor

data class SensorPoint(
    val time: Long,
    val temperature: Double,
    val humidity: Int,
    val rssi: Int
)

object SensorHistory {
    private const val MAX_POINTS = 300
    private val points = mutableListOf<SensorPoint>()
    private var lastStoredAt = 0L

    @Synchronized
    fun add(point: SensorPoint) {
        // BLE 广播可能很频繁，趋势数据最多每 2 秒记录一次。
        if (point.time - lastStoredAt < 2000) return
        lastStoredAt = point.time
        points.add(point)
        while (points.size > MAX_POINTS) points.removeAt(0)
    }

    @Synchronized
    fun snapshot(): List<SensorPoint> = points.toList()

    @Synchronized
    fun latest(): SensorPoint? = points.lastOrNull()
}
