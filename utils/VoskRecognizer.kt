package com.safeguard.app.utils

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

class VoskRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isReady = false

    fun initialize(onReady: () -> Unit) {
        Thread {
            try {
                val modelPath = copyModelToCache()
                model = Model(modelPath)
                isReady = true
                Log.d("VoskRecognizer", "✅ Model loaded successfully")
                onReady()
            } catch (e: Exception) {
                Log.e("VoskRecognizer", "❌ Model load failed: ${e.message}")
                onError(e)
            }
        }.start()
    }

    private fun copyModelToCache(): String {
        val modelDir = File(context.cacheDir, "model-en")
        if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
            return modelDir.absolutePath
        }
        modelDir.mkdirs()
        copyAssetFolder(context, "model-en", modelDir.absolutePath)
        return modelDir.absolutePath
    }

    private fun copyAssetFolder(context: Context, assetPath: String, outPath: String) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            copyAssetFile(context, assetPath, outPath)
        } else {
            File(outPath).mkdirs()
            assets.forEach { asset ->
                copyAssetFolder(context, "$assetPath/$asset", "$outPath/$asset")
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, outPath: String) {
        try {
            context.assets.open(assetPath).use { input ->
                File(outPath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e("VoskRecognizer", "Failed to copy: $assetPath")
        }
    }

    fun startListening() {
        if (!isReady || model == null) {
            Log.w("VoskRecognizer", "Model not ready yet")
            return
        }
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let { parseAndSend(it) }
                }
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { parseAndSend(it) }
                }
                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let { parseAndSend(it) }
                    stopListening()
                    startListening()
                }
                override fun onError(e: Exception?) {
                    Log.e("VoskRecognizer", "Error: ${e?.message}")
                    stopListening()
                    startListening()
                }
                override fun onTimeout() {
                    stopListening()
                    startListening()
                }
            })
            Log.d("VoskRecognizer", "🎤 Vosk listening started")
        } catch (e: Exception) {
            Log.e("VoskRecognizer", "Start error: ${e.message}")
            onError(e)
        }
    }

    private fun parseAndSend(json: String) {
        val text = json
            .replace("{", "").replace("}", "")
            .replace("\"partial\"", "").replace("\"text\"", "")
            .replace(":", "").replace("\"", "")
            .trim()
        if (text.isNotEmpty() && text != "the") {
            Log.d("VoskRecognizer", "Heard: $text")
            onResult(text)
        }
    }

    fun stopListening() {
        try {
            speechService?.stop()
            speechService = null
        } catch (e: Exception) { }
    }

    fun cleanup() {
        stopListening()
        model?.close()
        model = null
        isReady = false
    }

    val isInitialized get() = isReady
}