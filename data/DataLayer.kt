package com.safeguard.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val relation: String = "Contact",
    val isActive: Boolean = true
)

data class AlertLog(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null
)

class ContactRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safeguard_contacts", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<EmergencyContact> {
        val json = prefs.getString("contacts", "[]") ?: "[]"
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getActiveContacts(): List<EmergencyContact> = getAll().filter { it.isActive }

    fun save(contact: EmergencyContact) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == contact.id }
        list.add(contact)
        prefs.edit().putString("contacts", gson.toJson(list)).apply()
    }

    fun delete(id: String) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == id }
        prefs.edit().putString("contacts", gson.toJson(list)).apply()
    }

    fun toggle(id: String) {
        val list = getAll().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = list[index].copy(isActive = !list[index].isActive)
            prefs.edit().putString("contacts", gson.toJson(list)).apply()
        }
    }
}

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("safeguard_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var isMonitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoring_enabled", false)
        set(v) = prefs.edit().putBoolean("monitoring_enabled", v).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean("auto_start_boot", true)
        set(v) = prefs.edit().putBoolean("auto_start_boot", v).apply()

    var isFlashEnabled: Boolean
        get() = prefs.getBoolean("flash_enabled", true)
        set(v) = prefs.edit().putBoolean("flash_enabled", v).apply()

    var isAlarmSoundEnabled: Boolean
        get() = prefs.getBoolean("alarm_sound_enabled", true)
        set(v) = prefs.edit().putBoolean("alarm_sound_enabled", v).apply()

    var isRepeatSmsEnabled: Boolean
        get() = prefs.getBoolean("repeat_sms_enabled", true)
        set(v) = prefs.edit().putBoolean("repeat_sms_enabled", v).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(v) = prefs.edit().putBoolean("vibration_enabled", v).apply()

    fun logAlert(text: String, lat: Double?, lng: Double?) {
        val logs = getAlertLogs().toMutableList()
        logs.add(0, AlertLog(text = text, latitude = lat, longitude = lng))
        if (logs.size > 50) logs.removeAt(logs.size - 1)
        prefs.edit().putString("alert_logs", gson.toJson(logs)).apply()
    }

    fun getAlertLogs(): List<AlertLog> {
        val json = prefs.getString("alert_logs", "[]") ?: "[]"
        val type = object : TypeToken<List<AlertLog>>() {}.type
        return gson.fromJson(json, type)
    }

    fun clearLogs() = prefs.edit().remove("alert_logs").apply()
}