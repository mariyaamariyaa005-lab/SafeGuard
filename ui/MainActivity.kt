package com.safeguard.app.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.safeguard.app.R
import com.safeguard.app.alarm.AlarmManager
import com.safeguard.app.classifier.EmergencyClassifier
import com.safeguard.app.data.ContactRepository
import com.safeguard.app.data.EmergencyContact
import com.safeguard.app.data.PrefsManager
import com.safeguard.app.location.LocationManager
import com.safeguard.app.service.VoiceMonitorService
import com.safeguard.app.sms.SmsManager
import com.safeguard.app.utils.PinManager
import com.safeguard.app.utils.ShakeDetector
import com.safeguard.app.utils.VolumeButtonDetector

class MainActivity : AppCompatActivity() {

    private val classifier = EmergencyClassifier()
    private lateinit var locationMgr: LocationManager
    private lateinit var smsMgr: SmsManager
    private lateinit var alarmMgr: AlarmManager
    private lateinit var contactRepo: ContactRepository
    private lateinit var prefs: PrefsManager
    private lateinit var pinManager: PinManager
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var volumeDetector: VolumeButtonDetector
    private var isMonitoring = false
    private val PIN_REQUEST = 101
    private val PIN_SETUP_REQUEST = 102

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.VIBRATE,
        Manifest.permission.CAMERA
    )

    private val emergencyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoiceMonitorService.BROADCAST_TRIGGERED -> {
                    val text = intent.getStringExtra("text") ?: ""
                    val severity = intent.getStringExtra("severity") ?: ""
                    showEmergencyBanner("$severity: ${text.take(50)}")
                }
                VoiceMonitorService.BROADCAST_TRANSCRIPT -> {
                    val text = intent.getStringExtra("text") ?: ""
                    updateTranscript(text)
                }
                VoiceMonitorService.BROADCAST_EMOTION -> {
                    val emotion = intent.getStringExtra("emotion") ?: ""
                    val confidence = intent.getFloatExtra("confidence", 0f)
                    updateEmotionDisplay(emotion, confidence)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        locationMgr = LocationManager(this)
        smsMgr = SmsManager(this)
        alarmMgr = AlarmManager(this)
        contactRepo = ContactRepository(this)
        prefs = PrefsManager(this)
        pinManager = PinManager(this)

        // Volume button detector
        volumeDetector = VolumeButtonDetector {
            runOnUiThread {
                showToast("🚨 Volume SOS triggered!")
                triggerPanic("VOLUME BUTTON SOS")
            }
        }

        setupUI()
        checkPermissions()

        Handler(Looper.getMainLooper()).postDelayed({
            locationMgr.start()
            shakeDetector = ShakeDetector(this) {
                runOnUiThread {
                    showToast("Shake detected! Sending alert...")
                    triggerPanic("SHAKE DETECTED")
                }
            }
            shakeDetector.start()
            if (pinManager.isPinEnabled) {
                val intent = Intent(this, PinActivity::class.java).apply {
                    putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY)
                }
                startActivityForResult(intent, PIN_REQUEST)
            }
        }, 500)
    }

    // Volume button override for SOS
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDetector.onVolumeDown()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(VoiceMonitorService.BROADCAST_TRIGGERED)
            addAction(VoiceMonitorService.BROADCAST_TRANSCRIPT)
            addAction(VoiceMonitorService.BROADCAST_EMOTION)
        }
        ContextCompat.registerReceiver(
            this, emergencyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshContactList()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(emergencyReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationMgr.stop()
        smsMgr.cleanup()
        alarmMgr.cleanup()
        if (::shakeDetector.isInitialized) shakeDetector.stop()
        volumeDetector.reset()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PIN_REQUEST && resultCode != RESULT_OK) finish()
    }

    private fun setupUI() {
        findViewById<Button>(R.id.btnPanic).setOnClickListener { triggerPanic() }
        findViewById<Button>(R.id.btnImSafe).setOnClickListener { markSafe() }
        findViewById<Button>(R.id.btnToggleMonitor).setOnClickListener { toggleMonitor() }
        findViewById<Button>(R.id.btnAnalyze).setOnClickListener { analyzeManualInput() }
        findViewById<Button>(R.id.btnCall112).setOnClickListener {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:112")))
        }
        findViewById<Button>(R.id.btnManualAlert).setOnClickListener { sendManualAlert() }
        findViewById<Button>(R.id.btnAddContact).setOnClickListener { showAddContactDialog() }

        // Settings switches
        val switchAlarm = findViewById<Switch>(R.id.switchAlarmSound)
        val switchFlash = findViewById<Switch>(R.id.switchFlash)
        val switchVibration = findViewById<Switch>(R.id.switchVibration)
        val switchRepeat = findViewById<Switch>(R.id.switchRepeatSms)

        switchAlarm.isChecked = prefs.isAlarmSoundEnabled
        switchFlash.isChecked = prefs.isFlashEnabled
        switchVibration.isChecked = prefs.isVibrationEnabled
        switchRepeat.isChecked = prefs.isRepeatSmsEnabled

        switchAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.isAlarmSoundEnabled = checked
            showToast(if (checked) "Alarm sound ON" else "Alarm sound OFF")
        }
        switchFlash.setOnCheckedChangeListener { _, checked ->
            prefs.isFlashEnabled = checked
            showToast(if (checked) "Flashlight SOS ON" else "Flashlight SOS OFF")
        }
        switchVibration.setOnCheckedChangeListener { _, checked ->
            prefs.isVibrationEnabled = checked
            showToast(if (checked) "Vibration ON" else "Vibration OFF")
        }
        switchRepeat.setOnCheckedChangeListener { _, checked ->
            prefs.isRepeatSmsEnabled = checked
            showToast(if (checked) "Repeat SMS ON" else "Repeat SMS OFF")
        }

        // Stealth mode
        val switchStealth = findViewById<Switch>(R.id.switchStealth)
        switchStealth.isChecked = pinManager.isStealthEnabled
        switchStealth.setOnCheckedChangeListener { _, checked ->
            pinManager.isStealthEnabled = checked
            showToast(if (checked) "Stealth mode ON" else "Stealth mode OFF")
        }

        val spinnerDisguise = findViewById<Spinner>(R.id.spinnerDisguise)
        val disguises = arrayOf("Calculator", "Notes", "Weather")
        spinnerDisguise.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, disguises)
        spinnerDisguise.setSelection(disguises.indexOf(pinManager.stealthDisguise))
        spinnerDisguise.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                pinManager.stealthDisguise = disguises[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Secret word
        val etSecretWord = findViewById<EditText>(R.id.etSecretWord)
        etSecretWord.setText(pinManager.secretWord)
        findViewById<Button>(R.id.btnSaveSecretWord).setOnClickListener {
            val word = etSecretWord.text.toString().trim()
            if (word.isNotEmpty()) {
                pinManager.secretWord = word
                showToast("Secret word saved: $word")
            } else showToast("Enter a secret word first")
        }

        // PIN
        findViewById<Button>(R.id.btnSetPin).setOnClickListener {
            val intent = Intent(this, PinActivity::class.java).apply {
                putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP)
            }
            startActivityForResult(intent, PIN_SETUP_REQUEST)
        }
        findViewById<Button>(R.id.btnClearPin).setOnClickListener {
            pinManager.clearPin()
            showToast("PIN removed")
        }
    }

    private fun triggerPanic(reason: String = "PANIC BUTTON PRESSED") {
        Log.d("SafeGuard", "triggerPanic called: $reason")
        val loc = locationMgr.getLastLocation()
        val offline = loc?.isOffline ?: !locationMgr.isInternetAvailable()
        smsMgr.sendEmergencyAlert(reason, loc?.latitude, loc?.longitude, loc?.address, offline)
        alarmMgr.startAlarm()
        showEmergencyBanner("ALERT SENT!")
        showToast("Emergency alert sent!")
    }

    private fun markSafe() {
        alarmMgr.stopAlarm()
        smsMgr.stopRepeating()
        hideEmergencyBanner()
        startService(Intent(this, VoiceMonitorService::class.java).apply {
            action = VoiceMonitorService.ACTION_SAFE
        })
        startService(Intent(this, VoiceMonitorService::class.java).apply {
            action = VoiceMonitorService.ACTION_RESET
        })
        runOnUiThread {
            findViewById<TextView>(R.id.tvMonitorStatus).apply {
                text = "LISTENING..."
                visibility = if (isMonitoring) View.VISIBLE else View.GONE
            }
            findViewById<TextView>(R.id.tvTranscript).text = ""
            findViewById<TextView>(R.id.tvEmotionStatus).visibility = View.GONE
        }
        showToast("You are safe. Still monitoring.")
    }

    private fun sendManualAlert() {
        val loc = locationMgr.getLastLocation()
        val offline = loc?.isOffline ?: !locationMgr.isInternetAvailable()
        smsMgr.sendEmergencyAlert("Manual alert", loc?.latitude, loc?.longitude, loc?.address, offline)
        showToast("Alert SMS sent!")
    }

    private fun toggleMonitor() {
        if (isMonitoring) {
            isMonitoring = false
            stopService(Intent(this, VoiceMonitorService::class.java))
            prefs.isMonitoringEnabled = false
            findViewById<Button>(R.id.btnToggleMonitor).text = "Start Voice Monitoring"
            findViewById<TextView>(R.id.tvMonitorStatus).visibility = View.GONE
            findViewById<TextView>(R.id.tvEmotionStatus).visibility = View.GONE
        } else {
            isMonitoring = true
            ContextCompat.startForegroundService(this, Intent(this, VoiceMonitorService::class.java))
            prefs.isMonitoringEnabled = true
            findViewById<Button>(R.id.btnToggleMonitor).text = "Stop Monitoring"
            findViewById<TextView>(R.id.tvMonitorStatus).apply {
                text = "LISTENING..."
                visibility = View.VISIBLE
            }
        }
    }

    private fun analyzeManualInput() {
        val input = findViewById<EditText>(R.id.etManualInput).text.toString().trim()
        if (input.isEmpty()) { showToast("Enter text to analyze"); return }
        val result = classifier.classify(input)
        val tv = findViewById<TextView>(R.id.tvNlpResult)
        tv.text = "Severity: ${result.severity}\n" +
                "Score: ${result.score}\n" +
                "Category: ${result.category}\n" +
                "Emotion: ${result.emotion}\n" +
                "Matched: ${result.matchedPhrases.joinToString(", ")}"
        tv.visibility = View.VISIBLE
        if (result.shouldTrigger) triggerPanic("Analyzed: $input")
    }

    private fun refreshContactList() {
        val container = findViewById<LinearLayout>(R.id.contactsContainer)
        container.removeAllViews()
        contactRepo.getAll().forEach { contact ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val info = TextView(this).apply {
                text = "${contact.name} | ${contact.phone} | ${contact.relation}"
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val toggleBtn = Button(this).apply {
                text = if (contact.isActive) "ON" else "OFF"
                setOnClickListener { contactRepo.toggle(contact.id); refreshContactList() }
            }
            val delBtn = Button(this).apply {
                text = "X"
                setOnClickListener { contactRepo.delete(contact.id); refreshContactList() }
            }
            row.addView(info); row.addView(toggleBtn); row.addView(delBtn)
            container.addView(row)
        }
        val count = contactRepo.getActiveContacts().size
        findViewById<TextView>(R.id.tvContactCount).text = "$count active contact(s)"
    }

    private fun showAddContactDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16)
        }
        val nameInput = EditText(this).apply { hint = "Name (e.g. Mom)" }
        val phoneInput = EditText(this).apply {
            hint = "Phone (+919876543210)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val relInput = EditText(this).apply { hint = "Relation (e.g. Mother)" }
        layout.addView(nameInput); layout.addView(phoneInput); layout.addView(relInput)
        AlertDialog.Builder(this)
            .setTitle("Add Emergency Contact")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val rel = relInput.text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    contactRepo.save(EmergencyContact(
                        name = name, phone = phone,
                        relation = rel.ifEmpty { "Contact" }
                    ))
                    refreshContactList()
                    showToast("Contact saved!")
                } else showToast("Name and phone required")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun updateEmotionDisplay(emotion: String, confidence: Float) {
        runOnUiThread {
            val tv = findViewById<TextView>(R.id.tvEmotionStatus)
            val percent = (confidence * 100).toInt()
            val emoji = when (emotion) {
                "PANIC" -> "😱"
                "FEAR" -> "😨"
                "ANGER" -> "😠"
                "CRYING" -> "😢"
                else -> "😊"
            }
            tv.text = "$emoji Emotion: $emotion ($percent%)"
            tv.visibility = View.VISIBLE
        }
    }

    private fun showEmergencyBanner(text: String) {
        runOnUiThread {
            findViewById<LinearLayout>(R.id.emergencyBanner).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvEmergencyText).text = text
        }
    }

    private fun hideEmergencyBanner() {
        runOnUiThread {
            findViewById<LinearLayout>(R.id.emergencyBanner).visibility = View.GONE
        }
    }

    private fun updateTranscript(text: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.tvTranscript).apply {
                this.text = "\"$text\""
                visibility = View.VISIBLE
            }
        }
    }

    private fun showToast(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }
}