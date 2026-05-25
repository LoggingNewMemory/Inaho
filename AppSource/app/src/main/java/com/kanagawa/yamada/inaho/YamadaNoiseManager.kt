/*
Inaho Music Player - Noise Masking Module
Copyright (C) 2026 Kanagawa Yamada 
*/

package com.kanagawa.yamada.inaho

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class YamadaNoiseManager {

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled = _isEnabled.asStateFlow()

    // 0.0 = Pure Pink, 1.0 = Pure Brown
    private val _warmth = MutableStateFlow(0.5f)
    val warmth = _warmth.asStateFlow()

    // 0.0 to 1.0 (Master volume of the noise)
    private val _volume = MutableStateFlow(0.2f)
    val volume = _volume.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var noiseJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    fun toggleNoise(enabled: Boolean) {
        _isEnabled.value = enabled
        if (enabled) startEngine() else stopEngine()
    }

    fun setWarmth(value: Float) {
        _warmth.value = value.coerceIn(0f, 1f)
    }

    fun setVolume(value: Float) {
        _volume.value = value.coerceIn(0f, 1f)
        audioTrack?.setVolume(_volume.value)
    }

    private fun startEngine() {
        if (noiseJob?.isActive == true) return

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        ).apply {
            setVolume(_volume.value)
            play()
        }

        noiseJob = coroutineScope.launch(Dispatchers.Default) {
            val audioData = ShortArray(bufferSize)

            // Pink Noise Filter State (Paul Kellet's method)
            var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f
            var b4 = 0f; var b5 = 0f; var b6 = 0f

            // Brown Noise Filter State (Leaky Integrator)
            var brownState = 0f

            while (isActive) {
                val currentWarmth = _warmth.value

                for (i in audioData.indices) {
                    // 1. Generate Raw White Noise (-1.0 to 1.0)
                    val white = Random.nextFloat() * 2f - 1f

                    // 2. Filter into Pink Noise
                    b0 = 0.99886f * b0 + white * 0.0555179f
                    b1 = 0.99332f * b1 + white * 0.0750759f
                    b2 = 0.96900f * b2 + white * 0.1538520f
                    b3 = 0.86650f * b3 + white * 0.3104856f
                    b4 = 0.55000f * b4 + white * 0.5329522f
                    b5 = -0.7616f * b5 - white * 0.0168980f
                    val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f) * 0.11f
                    b6 = white * 0.115926f

                    // 3. Filter into Brown Noise
                    brownState = (brownState + (0.02f * white)) / 1.02f
                    val brown = brownState * 3.5f

                    // 4. Mix them using Linear Interpolation (Lerp)
                    var mixed = pink * (1f - currentWarmth) + brown * currentWarmth

                    // 5. Prevent hard clipping
                    mixed = mixed.coerceIn(-1f, 1f)

                    // 6. Convert Float to 16-bit PCM Short
                    audioData[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
                }

                audioTrack?.write(audioData, 0, bufferSize)
            }
        }
    }

    private fun stopEngine() {
        noiseJob?.cancel()
        noiseJob = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun release() {
        stopEngine()
        coroutineScope.cancel()
    }
}