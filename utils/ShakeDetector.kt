package com.safeguard.app.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private val onShakeTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val SHAKE_THRESHOLD = 12f
    private val SHAKE_DURATION_MS = 2000L
    private var shakeStartTime = 0L
    private var isShaking = false

    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isShaking = false
        shakeStartTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        if (acceleration > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (!isShaking) {
                isShaking = true
                shakeStartTime = now
            } else if (now - shakeStartTime >= SHAKE_DURATION_MS) {
                isShaking = false
                shakeStartTime = 0L
                onShakeTriggered()
            }
        } else {
            isShaking = false
            shakeStartTime = 0L
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}