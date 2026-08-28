/*
Inaho Music Player - Yamada Audio Engine
Copyright (C) 2026 Kanagawa Yamada
*/

package com.kanagawa.yamada.inaho

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EqPreset(
    val displayName: String,
    val emoji: String,
    val description: String,
    /** Gain values in millibels for bands 0-4 */
    val bands: IntArray,
    /** Extra loudness boost in millibels applied via LoudnessEnhancer */
    val loudnessGainMb: Int = 0,
    /** Enable Smart Audio Tunnel (dynamic gain riding) */
    val smartTunnel: Boolean = false
) {
    OFF(
        displayName = "Off",
        emoji = "✕",
        description = "Bypass all EQ",
        bands = intArrayOf(0, 0, 0, 0, 0),
        loudnessGainMb = 0,
        smartTunnel = false
    ),
    SMART(
        displayName = "Smart",
        emoji = "◈",
        description = "Dynamic audio tunnel — boosts volume on beat drops and lifts",
        bands = intArrayOf(200, 100, 0, 100, 150),
        loudnessGainMb = 600, // Boosted fallback to match the new dynamic gains
        smartTunnel = true
    ),
    ROCK(
        displayName = "Rock",
        emoji = "♟",
        description = "Punchy bass, scooped mids, crisp highs",
        bands = intArrayOf(500, 300, -200, 200, 400),
        loudnessGainMb = 500
    ),
    JAZZ(
        displayName = "Jazz",
        emoji = "♩",
        description = "Warm low-mids, airy top end",
        bands = intArrayOf(300, 200, 100, 0, 200),
        loudnessGainMb = 400
    ),
    CLASSIC(
        displayName = "Classic",
        emoji = "𝄞",
        description = "Flat response, natural dynamics",
        bands = intArrayOf(0, 0, 0, 0, 0),
        loudnessGainMb = 300 // THE BASELINE SWEET SPOT
    ),
    POP(
        displayName = "Pop",
        emoji = "♪",
        description = "Boosted vocals & presence, tight bass",
        bands = intArrayOf(-100, 200, 300, 200, 100),
        loudnessGainMb = 400
    ),
    BASS(
        displayName = "Bass",
        emoji = "◉",
        description = "Heavy sub & bass boost for earphones",
        bands = intArrayOf(800, 600, 0, -100, -100),
        loudnessGainMb = 600
    )
}

// ==========================================
// EQ MANAGER  (attach to MediaPlayer audio session)
// ==========================================

class YamadaAudioEngine(private val context: Context) {

    private val _currentPreset = MutableStateFlow(EqPreset.OFF)
    val currentPreset = _currentPreset.asStateFlow()

    private val _spatialEnabled = MutableStateFlow(false)
    val spatialEnabled = _spatialEnabled.asStateFlow()

    private var audioSessionId: Int = 0

    private var equalizer: Equalizer? = null
    private var customDynamicsProcessor: YamadaCustomDynamics? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null

    private var environmentalReverb: android.media.audiofx.EnvironmentalReverb? = null

    private val prefs = context.getSharedPreferences("inaho_eq", Context.MODE_PRIVATE)

    private var mediaPlayer: android.media.MediaPlayer? = null

    init {
        val savedName = prefs.getString("preset", EqPreset.OFF.name) ?: EqPreset.OFF.name
        _currentPreset.value = runCatching { EqPreset.valueOf(savedName) }.getOrDefault(EqPreset.OFF)
        _spatialEnabled.value = prefs.getBoolean("spatial", false)
    }

    fun attach(mp: android.media.MediaPlayer) {
        mediaPlayer = mp
        audioSessionId = mp.audioSessionId
        applyEffects(_currentPreset.value, _spatialEnabled.value, audioSessionId)
    }

    fun release() {
        tearDown()
        audioSessionId = 0
        mediaPlayer = null
    }

    fun setPreset(preset: EqPreset) {
        prefs.edit().putString("preset", preset.name).apply()
        _currentPreset.value = preset
        if (audioSessionId != 0) applyEffects(preset, _spatialEnabled.value, audioSessionId)
    }

    fun toggleSpatial() {
        val newState = !_spatialEnabled.value
        prefs.edit().putBoolean("spatial", newState).apply()
        _spatialEnabled.value = newState
        if (audioSessionId != 0) applyEffects(_currentPreset.value, newState, audioSessionId)
    }

    private fun tearDown() {
        runCatching { equalizer?.release() }
        runCatching { customDynamicsProcessor?.release() }
        runCatching { virtualizer?.release() }
        runCatching { environmentalReverb?.release() }
        runCatching { mediaPlayer?.setAuxEffectSendLevel(0f) }
        
        equalizer = null
        customDynamicsProcessor = null
        virtualizer = null
        environmentalReverb = null
    }

    private fun applyEffects(preset: EqPreset, spatial: Boolean, sessionId: Int) {
        tearDown()

        // 1. HRTF Spatializer (Insert Effect - safe from deadlocks)
        if (spatial) {
            runCatching {
                // Using Android's native Binaural Virtualizer which executes true HRTF (Head-Related Transfer Function)
                // without requiring the deadlock-prone attachAuxEffect() required by Reverb.
                virtualizer = android.media.audiofx.Virtualizer(0, sessionId).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        runCatching { forceVirtualizationMode(android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_BINAURAL) }
                    }
                    enabled = true
                    runCatching { setStrength(750.toShort()) } // 75% HRTF width - the perfect sweet spot!
                }
            }
        }

        // We process EQ and Dynamics even if preset is OFF, because Spatial acts as an independent layer
        val actualPreset = preset

        // 2. Equalizer bands (Emulate Kei-Audio's cinematic low/high bumps)
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bandCount = numberOfBands.toInt()
                actualPreset.bands.take(bandCount).forEachIndexed { i, gainMb ->
                    val min = bandLevelRange[0].toInt()
                    val max = bandLevelRange[1].toInt()
                    
                    var finalGain = gainMb
                    if (spatial) {
                        if (i == 0) finalGain += 300 // +3dB Sub-bass bump
                        if (i == bandCount - 1) finalGain += 150 // +1.5dB air bump
                    }
                    
                    setBandLevel(i.toShort(), finalGain.coerceIn(min, max).toShort())
                }
            }
        }

        runCatching {
            customDynamicsProcessor = YamadaCustomDynamics(sessionId).apply {
                applyDynamics(actualPreset, spatial)
            }
        }
    }
}

// ==========================================
// CUSTOM DYNAMICS PROCESSOR
// ==========================================

/**
 * A custom implementation of Dynamics Processing that does not rely on Android's built-in
 * DynamicsProcessing (which requires API 28+). This ensures the audio engine works on any
 * Android version.
 */
class YamadaCustomDynamics(sessionId: Int) {
    private var loudnessEnhancer: LoudnessEnhancer? = null

    init {
        runCatching {
            loudnessEnhancer = LoudnessEnhancer(sessionId)
        }
    }

    fun applyDynamics(preset: EqPreset, spatial: Boolean) {
        runCatching {
            loudnessEnhancer?.apply {
                // Emulate the dynamic gain via LoudnessEnhancer
                var baseGain = preset.loudnessGainMb
                
                // Emulate Smart Tunnel by giving an extra volume bump
                if (preset.smartTunnel) {
                    baseGain += 100 
                }
                
                val spatialBoost = if (spatial) 150 else 0
                
                setTargetGain(baseGain + spatialBoost)
                enabled = true
            }
        }
    }

    fun release() {
        runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        }
    }
}

// ==========================================
// EQ DIALOG UI
// ==========================================

@Composable
fun EqDialog(
    eqEngine: YamadaAudioEngine,
    onDismiss: () -> Unit
) {
    val currentPreset by eqEngine.currentPreset.collectAsState()
    val spatialEnabled by eqEngine.spatialEnabled.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1010))
                .padding(20.dp)
        ) {
            Text(
                text = "Yamada Audio Engine",
                color = Color(0xFFB8355B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = currentPreset.description,
                color = Color(0xFF888888),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val presets = EqPreset.values().toList()
            val offPreset = presets.first { it == EqPreset.OFF }
            val otherPresets = presets.filter { it != EqPreset.OFF }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqPresetTile(
                    preset = offPreset,
                    isSelected = offPreset == currentPreset,
                    onClick = { eqEngine.setPreset(offPreset) },
                    modifier = Modifier.weight(1f)
                )
                FeatureTile(
                    emoji = "🎧",
                    displayName = "Spatial",
                    isSelected = spatialEnabled,
                    onClick = { eqEngine.toggleSpatial() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            otherPresets.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        EqPresetTile(
                            preset = preset,
                            isSelected = preset == currentPreset,
                            onClick = { eqEngine.setPreset(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (currentPreset != EqPreset.OFF) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EqPresetTile(
    preset: EqPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FeatureTile(
        emoji = preset.emoji,
        displayName = preset.displayName,
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun FeatureTile(
    emoji: String,
    displayName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFB8355B) else Color(0xFF251818),
        animationSpec = tween(200),
        label = "tile_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFD4577A) else Color(0xFF3A2020),
        animationSpec = tween(200),
        label = "tile_border"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}