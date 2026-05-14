package com.safeguard.app.utils

import android.content.Context

class PinManager(context: Context) {

    private val prefs = context.getSharedPreferences("safeguard_pin", Context.MODE_PRIVATE)

    var secretWord: String
        get() = prefs.getString("secret_word", "") ?: ""
        set(v) = prefs.edit().putString("secret_word", v.lowercase().trim()).apply()

    val hasSecretWord: Boolean
        get() = secretWord.isNotEmpty()

    var appPin: String
        get() = prefs.getString("app_pin", "") ?: ""
        set(v) = prefs.edit().putString("app_pin", v).apply()

    val hasAppPin: Boolean
        get() = appPin.isNotEmpty()

    var isPinEnabled: Boolean
        get() = prefs.getBoolean("pin_enabled", false)
        set(v) = prefs.edit().putBoolean("pin_enabled", v).apply()

    var isStealthEnabled: Boolean
        get() = prefs.getBoolean("stealth_enabled", false)
        set(v) = prefs.edit().putBoolean("stealth_enabled", v).apply()

    var stealthDisguise: String
        get() = prefs.getString("stealth_disguise", "Calculator") ?: "Calculator"
        set(v) = prefs.edit().putString("stealth_disguise", v).apply()

    fun verifyPin(input: String): Boolean = input == appPin

    fun verifySecretWord(input: String): Boolean =
        secretWord.isNotEmpty() && input.lowercase().trim().contains(secretWord)

    fun clearPin() {
        prefs.edit().remove("app_pin").remove("pin_enabled").apply()
    }

    fun clearSecretWord() {
        prefs.edit().remove("secret_word").apply()
    }
}