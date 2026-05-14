package com.safeguard.app.alarm

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.*
import android.os.*
import android.util.Log
import com.safeguard.app.data.PrefsManager
import kotlinx.coroutines.*

class AlarmManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flashJob: Job? = null
    val isActive get() = isPlaying

    fun startAlarm() {
        if (isPlaying) return
        isPlaying = true
        val prefs = PrefsManager(context)
        try {
            if (prefs.isAlarmSoundEnabled) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
                )
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                    isLooping = true
                    prepare()
                    start()
                }
            }
            if (prefs.isVibrationEnabled) startSosVibration()
            if (prefs.isFlashEnabled) startFlashlightSos()
        } catch (e: Exception) {
            Log.e("AlarmManager", "Alarm error: ${e.message}")
        }
    }

    fun stopAlarm() {
        isPlaying = false
        try {
            mediaPlayer?.apply { if (isPlaying) stop(); release() }
            mediaPlayer = null
        } catch (e: Exception) { }
        try {
            context.getSystemService(Vibrator::class.java)?.cancel()
        } catch (e: Exception) { }
        flashJob?.cancel()
        turnOffFlash()
    }

    private fun startSosVibration() {
        val d = 200L; val D = 600L; val g = 150L; val G = 400L
        val pattern = longArrayOf(0, d, g, d, g, d, G, D, g, D, g, D, G, d, g, d, g, d, 1000L)
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, 0)
        }
    }

    private fun startFlashlightSos() {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = try { cm.cameraIdList.firstOrNull() } catch (e: Exception) { null } ?: return
        flashJob = scope.launch {
            repeat(15) {
                repeat(3) {
                    try { cm.setTorchMode(id, true); delay(150)
                        cm.setTorchMode(id, false); delay(150) } catch (e: Exception) { }
                }
                delay(400)
                repeat(3) {
                    try { cm.setTorchMode(id, true); delay(450)
                        cm.setTorchMode(id, false); delay(150) } catch (e: Exception) { }
                }
                delay(400)
                repeat(3) {
                    try { cm.setTorchMode(id, true); delay(150)
                        cm.setTorchMode(id, false); delay(150) } catch (e: Exception) { }
                }
                delay(1200)
            }
        }
    }

    private fun turnOffFlash() {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.firstOrNull()?.let { cm.setTorchMode(it, false) }
        } catch (e: Exception) { }
    }

    fun cleanup() { stopAlarm(); scope.cancel() }
}