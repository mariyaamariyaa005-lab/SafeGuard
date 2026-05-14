package com.safeguard.app.utils

import android.os.Handler
import android.os.Looper
import android.util.Log

class VolumeButtonDetector(
    private val onTriggered: () -> Unit
) {
    private val requiredPresses = 3
    private val timeWindowMs = 2000L

    private var pressCount = 0
    private var firstPressTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val resetRunnable = Runnable {
        pressCount = 0
        firstPressTime = 0L
    }

    fun onVolumeDown() {
        val now = System.currentTimeMillis()

        if (pressCount == 0) {
            firstPressTime = now
        }

        if (now - firstPressTime > timeWindowMs) {
            pressCount = 0
            firstPressTime = now
        }

        pressCount++
        Log.d("VolumeDetector", "Volume down press #$pressCount")

        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, timeWindowMs)

        if (pressCount >= requiredPresses) {
            pressCount = 0
            firstPressTime = 0L
            handler.removeCallbacks(resetRunnable)
            Log.d("VolumeDetector", "SOS triggered!")
            onTriggered()
        }
    }

    fun reset() {
        pressCount = 0
        firstPressTime = 0L
    }
}