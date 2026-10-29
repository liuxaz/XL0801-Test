package com.example.xl0801monitor

data class SensorPoint(
    val time: Long,
    val temperature: Double,
    val humidity: Int
)

data class SensorLatest(
    val time: Long,
    val temperature: Double,
    val humidity: Int,
    val rssi: Int,
    val mac: String
)

object SensorHistory {
    private const val MAX_POINTS = 300
    private const val STORE_INTERVAL_MS = 2000L
    private val points = mutableListOf<SensorPoint>()
    private var lastStoredAt = 0L
    private var latestValue: SensorLatest? = null

    @Synchronized
    fun update(latest: SensorLatest) {
        latestValue = latest
        if (latest.time - lastStoredAt < STORE_INTERVAL_MS) return
        lastStoredAt = latest.time
        points.add(SensorPoint(latest.time, latest.temperature, latest.humidity))
        while (points.size > MAX_POINTS) points.removeAt(0)
    }

    @Synchronized
    fun snapshot(): List<SensorPoint> = points.toList()

    @Synchronized
    fun latest(): SensorLatest? = latestValue
}
