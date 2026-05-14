package com.safeguard.app.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.safeguard.app.data.ContactRepository
import com.safeguard.app.data.PrefsManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class SmsManager(private val ctx: Context) {

    private val contactRepo = ContactRepository(ctx)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var repeatJob: Job? = null
    private var isRepeating = false
    private var repeatCount = 0
    val isCurrentlyRepeating get() = isRepeating

    fun sendEmergencyAlert(
        reason: String,
        latitude: Double? = null,
        longitude: Double? = null,
        address: String? = null,
        isOffline: Boolean = false
    ) {
        val contacts = contactRepo.getActiveContacts()
        Log.d("SmsManager", "📋 Total active contacts: ${contacts.size}")
        if (contacts.isEmpty()) {
            Log.e("SmsManager", "❌ No active contacts found!")
            return
        }
        val message = buildMessage(reason, latitude, longitude, address, isOffline)
        contacts.forEach { contact ->
            Log.d("SmsManager", "📱 Sending to: ${contact.name} — ${contact.phone}")
            sendSms(contact.phone, message)
        }
        if (PrefsManager(ctx).isRepeatSmsEnabled) {
            startRepeatAlerts(contacts.map { c -> c.phone }, latitude, longitude, isOffline)
        }
    }

    fun sendSms(phoneNumber: String, message: String) {
        try {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e("SmsManager", "❌ SEND_SMS permission not granted!")
                return
            }
            Log.d("SmsManager", "📤 Attempting SMS to $phoneNumber")
            val smsManager = getSmsManager()
            val parts = smsManager.divideMessage(message)
            val sentIntents = ArrayList<PendingIntent>()
            parts.forEach { _ ->
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        ctx,
                        System.currentTimeMillis().toInt(),
                        Intent("com.safeguard.SMS_SENT"),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                    )
                )
            }
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            Log.d("SmsManager", "✅ SMS sent to $phoneNumber (${parts.size} parts)")
        } catch (e: SecurityException) {
            Log.e("SmsManager", "❌ Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e("SmsManager", "❌ SMS error: ${e.message}")
        }
    }

    private fun getSmsManager(): android.telephony.SmsManager {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                ctx.getSystemService(android.telephony.SmsManager::class.java)
                    ?.createForSubscriptionId(subId)
                    ?: ctx.getSystemService(android.telephony.SmsManager::class.java)!!
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startRepeatAlerts(
        phones: List<String>,
        lat: Double?,
        lng: Double?,
        wasOffline: Boolean
    ) {
        if (isRepeating) return
        isRepeating = true
        repeatCount = 0
        repeatJob = scope.launch {
            while (isRepeating && repeatCount < 10) {
                delay(2 * 60 * 1000L)
                repeatCount++
                val offline = !isInternetAvailable()
                val msg = buildUpdateMessage(repeatCount, lat, lng, offline)
                phones.forEach { phone -> sendSms(phone, msg) }
                Log.d("SmsManager", "Repeat alert #$repeatCount sent")
            }
            isRepeating = false
        }
    }

    fun stopRepeating() {
        isRepeating = false
        repeatCount = 0
        repeatJob?.cancel()
    }

    private fun buildMessage(
        reason: String,
        lat: Double?,
        lng: Double?,
        address: String?,
        isOffline: Boolean
    ): String {
        val time = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
        val locLine = if (lat != null && lng != null) {
            if (!isOffline) "maps.google.com/?q=$lat,$lng"
            else "GPS: $lat,$lng (offline)"
        } else "Location unavailable"
        val addrLine = if (!address.isNullOrBlank() && !isOffline) "\nAddr: $address" else ""
        return "EMERGENCY ALERT!\n" +
                "She needs help NOW!\n" +
                "Location: $locLine$addrLine\n" +
                "Time: $time\n" +
                "$reason\n" +
                "Call 112!\n" +
                "-SafeGuard"
    }

    private fun buildUpdateMessage(
        count: Int,
        lat: Double?,
        lng: Double?,
        isOffline: Boolean
    ): String {
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val loc = if (lat != null && lng != null) {
            if (!isOffline) "maps.google.com/?q=$lat,$lng"
            else "GPS: $lat,$lng"
        } else "Unavailable"
        return "UPDATE #$count - Still needs help!\n" +
                "Location: $loc\n" +
                "Time: $time\n" +
                "Call 112!\n" +
                "-SafeGuard"
    }

    fun cleanup() {
        stopRepeating()
        scope.cancel()
    }
}