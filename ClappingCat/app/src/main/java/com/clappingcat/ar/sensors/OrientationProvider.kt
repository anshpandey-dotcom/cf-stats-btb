package com.clappingcat.ar.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class OrientationProvider(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = sensor != null

    private val lock = Any()
    private val times = LongArray(CAPACITY)
    private val matrices = Array(CAPACITY) { DoubleArray(9) }
    private val raw = FloatArray(9)
    private var head = -1
    private var size = 0

    fun start() {
        sensor?.let { manager.registerListener(this, it, SAMPLING_PERIOD_US) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    fun latest(): DoubleArray? = synchronized(lock) {
        if (size == 0) null else matrices[head].copyOf()
    }

    fun at(timestampNs: Long): DoubleArray? = synchronized(lock) {
        if (size == 0) return null
        var bestIndex = head
        var bestGap = Long.MAX_VALUE
        for (n in 0 until size) {
            val index = (head - n + CAPACITY) % CAPACITY
            val gap = Math.abs(times[index] - timestampNs)
            if (gap < bestGap) {
                bestGap = gap
                bestIndex = index
            }
            if (times[index] < timestampNs) break
        }
        matrices[bestIndex].copyOf()
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(raw, event.values)
        synchronized(lock) {
            head = (head + 1) % CAPACITY
            if (size < CAPACITY) size++
            times[head] = event.timestamp
            val m = matrices[head]
            for (i in 0 until 3) m[i] = raw[i].toDouble()
            for (i in 0 until 3) m[3 + i] = raw[6 + i].toDouble()
            for (i in 0 until 3) m[6 + i] = -raw[3 + i].toDouble()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val CAPACITY = 256
        const val SAMPLING_PERIOD_US = 5000
    }
}
