package com.safeguard.app.utils

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

enum class EmotionState {
    NORMAL, FEAR, PANIC, ANGER, CRYING, UNKNOWN
}

data class EmotionResult(
    val emotion: EmotionState,
    val confidence: Float,
    val shouldAlert: Boolean,
    val pitch: Float,
    val energy: Float,
    val speechRate: Float
)

class EmotionDetector(
    private val onEmotionDetected: (EmotionResult) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var audioRecord: AudioRecord? = null

    private val SAMPLE_RATE = 16000
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 4

    // Emotion thresholds
    private val FEAR_PITCH_MIN = 200f
    private val FEAR_ENERGY_MIN = 0.3f
    private val PANIC_PITCH_MIN = 280f
    private val PANIC_ENERGY_MIN = 0.6f
    private val ANGER_ENERGY_MIN = 0.7f
    private val ANGER_PITCH_MAX = 200f
    private val CRYING_ENERGY_MAX = 0.2f
    private val CRYING_PITCH_MIN = 150f

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE
                )
                audioRecord?.startRecording()
                Log.d("EmotionDetector", "✅ Started emotion detection")
                analyzeLoop()
            } catch (e: Exception) {
                Log.e("EmotionDetector", "Error: ${e.message}")
            }
        }
    }

    private suspend fun analyzeLoop() {
        val buffer = ShortArray(BUFFER_SIZE / 2)
        var analysisBuffer = mutableListOf<Short>()
        val analysisWindowSize = SAMPLE_RATE * 2 // 2 seconds of audio

        while (isRunning) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (read > 0) {
                analysisBuffer.addAll(buffer.take(read).toList())
                if (analysisBuffer.size >= analysisWindowSize) {
                    val samples = analysisBuffer.take(analysisWindowSize).toShortArray()
                    val result = analyzeEmotion(samples)
                    if (result.emotion != EmotionState.NORMAL) {
                        withContext(Dispatchers.Main) {
                            onEmotionDetected(result)
                        }
                    }
                    analysisBuffer = analysisBuffer.drop(SAMPLE_RATE).toMutableList()
                }
            }
            delay(100)
        }
    }

    private fun analyzeEmotion(samples: ShortArray): EmotionResult {
        val energy = calculateEnergy(samples)
        val pitch = estimatePitch(samples)
        val speechRate = estimateSpeechRate(samples)
        val zeroCrossingRate = calculateZeroCrossingRate(samples)

        Log.d("EmotionDetector", "Energy: $energy, Pitch: $pitch, Rate: $speechRate, ZCR: $zeroCrossingRate")

        val emotion = when {
            // PANIC — very high pitch + high energy + fast speech
            pitch > PANIC_PITCH_MIN && energy > PANIC_ENERGY_MIN && speechRate > 1.5f ->
                EmotionState.PANIC

            // FEAR — high pitch + moderate energy + irregular speech
            pitch > FEAR_PITCH_MIN && energy > FEAR_ENERGY_MIN && energy < PANIC_ENERGY_MIN ->
                EmotionState.FEAR

            // ANGER — high energy + low-medium pitch + fast speech
            energy > ANGER_ENERGY_MIN && pitch < ANGER_PITCH_MAX ->
                EmotionState.ANGER

            // CRYING — low energy + mid pitch + high zero crossing rate
            energy < CRYING_ENERGY_MAX && pitch > CRYING_PITCH_MIN && zeroCrossingRate > 0.1f ->
                EmotionState.CRYING

            else -> EmotionState.NORMAL
        }

        val confidence = when (emotion) {
            EmotionState.PANIC -> minOf((energy / PANIC_ENERGY_MIN) * 0.5f +
                    (pitch / PANIC_PITCH_MIN) * 0.5f, 1.0f)
            EmotionState.FEAR -> minOf((pitch / FEAR_PITCH_MIN) * 0.6f +
                    (energy / FEAR_ENERGY_MIN) * 0.4f, 1.0f)
            EmotionState.ANGER -> minOf(energy / ANGER_ENERGY_MIN, 1.0f)
            EmotionState.CRYING -> minOf(zeroCrossingRate * 5f, 1.0f)
            else -> 0f
        }

        val shouldAlert = (emotion == EmotionState.PANIC && confidence > 0.7f) ||
                (emotion == EmotionState.FEAR && confidence > 0.8f)

        return EmotionResult(
            emotion = emotion,
            confidence = confidence,
            shouldAlert = shouldAlert,
            pitch = pitch,
            energy = energy,
            speechRate = speechRate
        )
    }

    private fun calculateEnergy(samples: ShortArray): Float {
        var sum = 0.0
        samples.forEach { sum += (it.toDouble() / 32768.0).pow(2) }
        return sqrt(sum / samples.size).toFloat()
    }

    private fun estimatePitch(samples: ShortArray): Float {
        val normalized = samples.map { it.toFloat() / 32768f }
        val minPeriod = (SAMPLE_RATE / 500f).toInt()
        val maxPeriod = (SAMPLE_RATE / 80f).toInt()
        var bestPeriod = minPeriod
        var bestCorrelation = Float.MIN_VALUE

        for (period in minPeriod..maxPeriod) {
            var correlation = 0f
            var count = 0
            for (i in 0 until minOf(normalized.size - period, 1000)) {
                correlation += normalized[i] * normalized[i + period]
                count++
            }
            if (count > 0) {
                correlation /= count
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation
                    bestPeriod = period
                }
            }
        }
        return if (bestCorrelation > 0.1f) SAMPLE_RATE.toFloat() / bestPeriod else 0f
    }

    private fun estimateSpeechRate(samples: ShortArray): Float {
        val energy = calculateEnergy(samples)
        if (energy < 0.01f) return 0f
        val frameSize = SAMPLE_RATE / 100
        var transitions = 0
        var prevActive = false
        for (i in samples.indices step frameSize) {
            val frameEnd = minOf(i + frameSize, samples.size)
            val frame = samples.copyOfRange(i, frameEnd)
            val frameEnergy = calculateEnergy(frame)
            val isActive = frameEnergy > energy * 0.3f
            if (isActive != prevActive) transitions++
            prevActive = isActive
        }
        return transitions.toFloat() / 2f
    }

    private fun calculateZeroCrossingRate(samples: ShortArray): Float {
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0) != (samples[i-1] >= 0)) crossings++
        }
        return crossings.toFloat() / samples.size
    }

    fun stop() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) { }
        scope.cancel()
    }
}