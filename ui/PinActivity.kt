package com.safeguard.app.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.app.R
import com.safeguard.app.utils.PinManager

class PinActivity : AppCompatActivity() {

    private lateinit var pinManager: PinManager
    private var enteredPin = ""
    private var mode = MODE_VERIFY

    companion object {
        const val MODE_VERIFY = "verify"
        const val MODE_SETUP = "setup"
        const val EXTRA_MODE = "mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)
        pinManager = PinManager(this)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VERIFY
        setupUI()
    }

    private fun setupUI() {
        val tvTitle = findViewById<TextView>(R.id.tvPinTitle)
        val tvDots = findViewById<TextView>(R.id.tvPinDots)
        val tvError = findViewById<TextView>(R.id.tvPinError)
        tvTitle.text = if (mode == MODE_SETUP) "Set Your PIN" else "Enter PIN"

        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        buttonIds.forEachIndexed { index, id ->
            findViewById<Button>(id).setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin += index.toString()
                    tvDots.text = "●".repeat(enteredPin.length) +
                            "○".repeat(4 - enteredPin.length)
                    tvError.text = ""
                    if (enteredPin.length == 4) handlePinComplete(tvError, tvDots)
                }
            }
        }

        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
                tvDots.text = "●".repeat(enteredPin.length) +
                        "○".repeat(4 - enteredPin.length)
            }
        }
    }

    private fun handlePinComplete(tvError: TextView, tvDots: TextView) {
        if (mode == MODE_SETUP) {
            pinManager.appPin = enteredPin
            pinManager.isPinEnabled = true
            Toast.makeText(this, "PIN set successfully!", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } else {
            if (pinManager.verifyPin(enteredPin)) {
                setResult(RESULT_OK)
                finish()
            } else {
                tvError.text = "Wrong PIN. Try again."
                enteredPin = ""
                tvDots.text = "○○○○"
            }
        }
    }
}