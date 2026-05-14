package com.safeguard.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.app.R
import com.safeguard.app.utils.PinManager

class StealthActivity : AppCompatActivity() {

    private lateinit var pinManager: PinManager
    private var display = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinManager = PinManager(this)

        // If stealth mode is off go straight to MainActivity
        if (!pinManager.isStealthEnabled) {
            openRealApp()
            return
        }

        // Always use calculator layout (only disguise for now)
        setContentView(R.layout.activity_stealth_calculator)

        setupCalculator()
    }

    private fun setupCalculator() {
        val tvDisplay = findViewById<TextView>(R.id.tvCalcDisplay)
        val buttons = listOf(
            R.id.calcBtn0, R.id.calcBtn1, R.id.calcBtn2,
            R.id.calcBtn3, R.id.calcBtn4, R.id.calcBtn5,
            R.id.calcBtn6, R.id.calcBtn7, R.id.calcBtn8,
            R.id.calcBtn9
        )

        buttons.forEachIndexed { index, id ->
            findViewById<Button>(id).setOnClickListener {
                display += index.toString()
                tvDisplay.text = display

                // Check if PIN entered
                if (display.length >= 4) {
                    if (pinManager.verifyPin(display.takeLast(4))) {
                        openRealApp()
                    } else if (display.length > 10) {
                        display = display.takeLast(4)
                    }
                }
            }
        }

        // Calculator operators — just for looks
        listOf(R.id.calcBtnPlus, R.id.calcBtnMinus,
            R.id.calcBtnMultiply, R.id.calcBtnDivide).forEach { id ->
            try {
                findViewById<Button>(id).setOnClickListener {
                    display += when(id) {
                        R.id.calcBtnPlus -> "+"
                        R.id.calcBtnMinus -> "-"
                        R.id.calcBtnMultiply -> "×"
                        else -> "÷"
                    }
                    tvDisplay.text = display
                }
            } catch (e: Exception) { }
        }

        // Equals button
        try {
            findViewById<Button>(R.id.calcBtnEquals).setOnClickListener {
                if (pinManager.verifyPin(display.filter { it.isDigit() }.takeLast(4))) {
                    openRealApp()
                } else {
                    display = "0"
                    tvDisplay.text = display
                }
            }
        } catch (e: Exception) { }

        // Clear button
        try {
            findViewById<Button>(R.id.calcBtnClear).setOnClickListener {
                display = ""
                tvDisplay.text = "0"
            }
        } catch (e: Exception) { }
    }

    private fun openRealApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}