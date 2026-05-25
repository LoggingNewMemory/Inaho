/*
Inaho Music Player - Noise Masking Module
Copyright (C) 2026 Kanagawa Yamada 
*/

package com.kanagawa.yamada.inaho

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class YamadaNoiseManager(private val context: Context) {

    // 🧪 KOYORI'S MEMORY BANK: Initialize SharedPreferences
    private val prefs = context.getSharedPreferences("inaho_noise", Context.MODE_PRIVATE)

    // Load saved states, defaulting to false and 50% if nothing is saved yet!
    private val _isEnabled = MutableStateFlow(prefs.getBoolean("is_enabled", false))
    val isEnabled = _isEnabled.asStateFlow()

    private val _volume = MutableStateFlow(prefs.getFloat("volume", 0.5f))
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
        prefs.edit().putBoolean("is_enabled", enabled).apply() // 💾 Save state!
        evaluateEngine()
    }

    fun syncWithPlayer(isPlaying: Boolean) {
        isMusicPlaying = isPlaying
        evaluateEngine()
    }

    fun setVolume(value: Float) {
        _volume.value = value.coerceIn(0f, 1f)
        prefs.edit().putFloat("volume", _volume.value).apply() // 💾 Save volume!
        audioTrack?.setVolume(_volume.value * 0.6f) // 60% boost limit still active!
    }

    private fun evaluateEngine() {
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
            setVolume(_volume.value * 0.6f)
            play()
        }

        noiseJob = coroutineScope.launch(Dispatchers.Default) {
            val audioData = ShortArray(bufferSize)
            var brownState = 0f

            while (isActive) {
                for (i in audioData.indices) {
                    val white = Random.nextFloat() * 2f - 1f
                    brownState = (brownState + (0.02f * white)) / 1.02f
                    var brown = brownState * 3.5f
                    brown = brown.coerceIn(-1f, 1f)
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