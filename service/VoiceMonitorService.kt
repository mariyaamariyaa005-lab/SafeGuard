package com.safeguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeguard.app.alarm.AlarmManager
import com.safeguard.app.classifier.EmergencyClassifier
import com.safeguard.app.classifier.EmergencyResult
import com.safeguard.app.data.PrefsManager
import com.safeguard.app.location.LocationManager
import com.safeguard.app.sms.SmsManager
import com.safeguard.app.ui.MainActivity
import com.safeguard.app.utils.PinManager
import com.safeguard.app.utils.VoskRecognizer

class VoiceMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "safeguard_monitor"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.safeguard.STOP_MONITOR"
        const val ACTION_RESET = "com.safeguard.RESET_COOLDOWN"
        const val ACTION_SAFE = "com.safeguard.IM_SAFE"
        const val BROADCAST_TRIGGERED = "com.safeguard.EMERGENCY_TRIGGERED"
        const val BROADCAST_TRANSCRIPT = "com.safeguard.TRANSCRIPT"
        const val BROADCAST_EMOTION = "com.safeguard.EMOTION_DETECTED"
    }

    private val classifier = EmergencyClassifier()
    private lateinit var locationManager: LocationManager
    private lateinit var smsMgr: SmsManager
    private lateinit var alarmMgr: AlarmManager
    private lateinit var prefs: PrefsManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var voskRecognizer: VoskRecognizer? = null
    private var isVoskReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isListening = false
    private var lastAlertTime = 0L

    private val restartRunnable = Runnable { if (isListening) startListening() }

    override fun onCreate() {
        super.onCreate()
        locationManager = LocationManager(this)
        smsMgr = SmsManager(this)
        alarmMgr = AlarmManager(this)
        prefs = PrefsManager(this)
        acquireWakeLock()
        createNotificationChannel()

        // Initialize Vosk for offline recognition
        voskRecognizer = VoskRecognizer(
            context = this,
            onResult = { text -> processVoiceText(text) },
            onError = { e -> Log.e("VoiceService", "Vosk error: ${e.message}") }
        )
        voskRecognizer?.initialize {
            isVoskReady = true
            Log.d("VoiceService", "✅ Vosk ready!")
        }
        // Emotion detector
        val emotionDetector = com.safeguard.app.utils.EmotionDetector { result ->
            if (result.shouldAlert) {
                handleEmergency("Emotion detected: ${result.emotion}", null)
            }
            sendBroadcast(Intent(BROADCAST_EMOTION).apply {
                putExtra("emotion", result.emotion.name)
                putExtra("confidence", result.confidence)
            })
        }
        emotionDetector.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_RESET -> { lastAlertTime = 0L; return START_STICKY }
            ACTION_SAFE -> {
                alarmMgr.stopAlarm()
                smsMgr.stopRepeating()
                lastAlertTime = 0L
                updateNotification("Listening...")
                return START_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotification("Listening..."))
        isListening = true
        locationManager.start()
        startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        handler.removeCallbacks(restartRunnable)
        speechRecognizer?.destroy()
        voskRecognizer?.cleanup()
        locationManager.stop()
        wakeLock?.release()
        alarmMgr.cleanup()
        smsMgr.cleanup()
    }

    override fun onBind(intent: Intent?) = null

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeGuard::WakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L)
    }

    private fun startListening() {
        if (isInternetAvailable()) {
            Log.d("VoiceService", "🌐 Using Google (online)")
            voskRecognizer?.stopListening()
            startGoogleListening()
        } else {
            Log.d("VoiceService", "📴 No internet - using Vosk")
            speechRecognizer?.destroy()
            speechRecognizer = null
            if (isVoskReady) {
                voskRecognizer?.startListening()
            } else {
                Log.d("VoiceService", "⏳ Vosk loading, retry in 3s...")
                handler.postDelayed({ startListening() }, 3000)
            }
        }
    }

    private fun startGoogleListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotBlank()) processVoiceText(text)
                handler.postDelayed(restartRunnable, 300)
            }

            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                sendBroadcast(Intent(BROADCAST_TRANSCRIPT).putExtra("text", text))
                if (classifier.isUrgent(text)) handleEmergency(text, null)
            }

            override fun onError(error: Int) {
                Log.e("VoiceService", "SpeechRecognizer error code: $error")

                when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> Log.e("VoiceService", "ERROR_AUDIO: Audio recording error")
                    SpeechRecognizer.ERROR_CLIENT -> Log.e("VoiceService", "ERROR_CLIENT: Client side error, check permissions or setup")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Log.e("VoiceService", "ERROR_INSUFFICIENT_PERMISSIONS: Missing RECORD_AUDIO permission")
                    SpeechRecognizer.ERROR_NETWORK -> Log.e("VoiceService", "ERROR_NETWORK: Network error")
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Log.e("VoiceService", "ERROR_NETWORK_TIMEOUT: Network timeout")
                    SpeechRecognizer.ERROR_NO_MATCH -> Log.e("VoiceService", "ERROR_NO_MATCH: No recognition result matched")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Log.e("VoiceService", "ERROR_RECOGNIZER_BUSY: Recognizer is busy")
                    SpeechRecognizer.ERROR_SERVER -> Log.e("VoiceService", "ERROR_SERVER: Server error")
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Log.e("VoiceService", "ERROR_SPEECH_TIMEOUT: No speech input")
                    else -> Log.e("VoiceService", "Unknown error code: $error")
                }

                val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000L else 800L
                handler.postDelayed(restartRunnable, delay)
            }


            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processVoiceText(text: String) {
        if (!isListening) return
        Log.d("VoiceService", "🎤 Heard: $text")
        sendBroadcast(Intent(BROADCAST_TRANSCRIPT).putExtra("text", text))

        val pinManager = PinManager(this)
        val secretWord = pinManager.secretWord
        Log.d("VoiceService", "🔑 Secret word: '$secretWord'")

        // Check secret word first
        if (secretWord.isNotEmpty()) {
            val textLower = text.lowercase().trim()
            val wordLower = secretWord.lowercase().trim()
            Log.d("VoiceService", "🔍 Checking: '$textLower' contains '$wordLower'")
            if (textLower.contains(wordLower)) {
                Log.d("VoiceService", "✅ Secret word matched!")
                handleEmergency("Secret word: $secretWord", null)
                return
            }
        }

        val result = classifier.classify(text)
        Log.d("VoiceService", "📊 Classification: ${result.severity} score=${result.score}")
        if (result.shouldTrigger) handleEmergency(text, result)
        else if (classifier.isUrgent(text)) handleEmergency(text, null)
    }

    private fun handleEmergency(text: String, result: EmergencyResult? = null) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < 30_000L) return
        lastAlertTime = now
        val loc = locationManager.getLastLocation()
        smsMgr.sendEmergencyAlert(
            "Voice: $text",
            loc?.latitude,
            loc?.longitude,
            loc?.address,
            loc?.isOffline ?: !locationManager.isInternetAvailable()
        )
        alarmMgr.startAlarm()
        prefs.logAlert(text, loc?.latitude, loc?.longitude)
        updateNotification("ALERT SENT!")
        sendBroadcast(Intent(BROADCAST_TRIGGERED).apply {
            putExtra("text", text)
            putExtra("severity", result?.severity?.name ?: "HIGH")
        })
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SafeGuard Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeGuard Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }
}