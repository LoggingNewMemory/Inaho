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

    // 0.0 to 1.0 (UI Volume)
    private val _volume = MutableStateFlow(0.5f)
    val volume = _volume.asStateFlow()

    private var isMusicPlaying = false
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
        evaluateEngine()
    }

    fun syncWithPlayer(isPlaying: Boolean) {
        isMusicPlaying = isPlaying
        evaluateEngine()
    }

    fun setVolume(value: Float) {
        _volume.value = value.coerceIn(0f, 1f)
        audioTrack?.setVolume(_volume.value * 0.2f)
    }

    private fun evaluateEngine() {
        // Only run the engine if it is BOTH enabled in the UI AND the music is actually playing!
        if (_isEnabled.value && isMusicPlaying) {
            startEngine()
        } else {
            stopEngine()
        }
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
            setVolume(_volume.value * 0.2f) // Apply the 20% limit here too!
            play()
        }

        noiseJob = coroutineScope.launch(Dispatchers.Default) {
            val audioData = ShortArray(bufferSize)
            var brownState = 0f // Pure Brown Noise state!

            while (isActive) {
                for (i in audioData.indices) {
                    val white = Random.nextFloat() * 2f - 1f

                    // Filter into deep, warm Brown Noise
                    brownState = (brownState + (0.02f * white)) / 1.02f
                    var brown = brownState * 3.5f

                    // Prevent hard clipping
                    brown = brown.coerceIn(-1f, 1f)

                    // Convert Float to 16-bit PCM Short
                    audioData[i] = (brown * Short.MAX_VALUE).toInt().toShort()
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