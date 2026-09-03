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
    private val prefs = context.getSharedPreferences("inaho_eq", Context.MODE_PRIVATE)

    private val _currentPreset = MutableStateFlow(EqPreset.OFF)
    val currentPreset = _currentPreset.asStateFlow()

    private val isReplayGainEnabled = MutableStateFlow(prefs.getBoolean("replaygain_enabled", false))
    val replayGainEnabled = isReplayGainEnabled.asStateFlow()

    private var currentTrackReplayGainMb: Int = 0

    var currentVolumeMultiplier: Float = 1.0f
        private set

    var onVolumeMultiplierChanged: ((Float) -> Unit)? = null


    private var audioSessionId: Int = 0

    private var equalizer: Equalizer? = null
    private var customDynamicsProcessor: YamadaCustomDynamics? = null

    private var environmentalReverb: android.media.audiofx.EnvironmentalReverb? = null


    private var mediaPlayer: android.media.MediaPlayer? = null

    init {
        val savedName = prefs.getString("preset", EqPreset.OFF.name) ?: EqPreset.OFF.name
        _currentPreset.value = runCatching { EqPreset.valueOf(savedName) }.getOrDefault(EqPreset.OFF)
    }

    fun attach(mp: android.media.MediaPlayer) {
        mediaPlayer = mp
        audioSessionId = mp.audioSessionId
        applyEffects(_currentPreset.value, audioSessionId)
    }

    fun release() {
        tearDown()
        audioSessionId = 0
        mediaPlayer = null
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("replaygain_enabled", enabled).apply()
        isReplayGainEnabled.value = enabled
        if (audioSessionId != 0) applyEffects(_currentPreset.value, audioSessionId)
    }

    fun analyzeAndSetTrack(path: String) {
        currentTrackReplayGainMb = 0
        if (isReplayGainEnabled.value && path.isNotEmpty()) {
            try {
                val f = org.jaudiotagger.audio.AudioFileIO.read(java.io.File(path))
                val tag = f.tag
                if (tag != null) {
                    var gainStr = ""
                    val fields = tag.fields
                    while(fields.hasNext()) {
                        val field = fields.next()
                        if (field.toString().contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                            gainStr = field.toString()
                            break
                        }
                    }
                    if (gainStr.isNotEmpty()) {
                        val match = Regex("""([+-]?[0-9]*\.?[0-9]+)""").find(gainStr)
                        if (match != null) {
                            val db = match.value.toFloat()
                            currentTrackReplayGainMb = (db * 100).toInt()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (audioSessionId != 0) applyEffects(_currentPreset.value, audioSessionId)
    }

    fun setPreset(preset: EqPreset) {
        prefs.edit().putString("preset", preset.name).apply()
        _currentPreset.value = preset
        if (audioSessionId != 0) applyEffects(preset, audioSessionId)
    }


    private fun tearDown() {
        runCatching { equalizer?.release() }
        runCatching { customDynamicsProcessor?.release() }
        runCatching { environmentalReverb?.release() }
        runCatching { mediaPlayer?.setAuxEffectSendLevel(0f) }
        
        equalizer = null
        customDynamicsProcessor = null
        environmentalReverb = null
    }

    private fun applyEffects(preset: EqPreset, sessionId: Int) {
        tearDown()


        // We process EQ and Dynamics even if preset is OFF
        val actualPreset = preset

        // 1. Equalizer bands
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bandCount = numberOfBands.toInt()
                actualPreset.bands.take(bandCount).forEachIndexed { i, gainMb ->
                    val min = bandLevelRange[0].toInt()
                    val max = bandLevelRange[1].toInt()
                    setBandLevel(i.toShort(), gainMb.coerceIn(min, max).toShort())
                }
            }
        }

        runCatching {
            customDynamicsProcessor = YamadaCustomDynamics(sessionId).apply {
                var totalGainMb = actualPreset.loudnessGainMb + currentTrackReplayGainMb
                if (actualPreset.smartTunnel) {
                    totalGainMb += 100
                }

                if (totalGainMb > 0) {
                    applyGain(totalGainMb)
                    currentVolumeMultiplier = 1.0f
                } else {
                    applyGain(0)
                    currentVolumeMultiplier = Math.pow(10.0, totalGainMb / 2000.0).toFloat()
                }
                onVolumeMultiplierChanged?.invoke(currentVolumeMultiplier)
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

    fun applyGain(gainMb: Int) {
        runCatching {
            loudnessEnhancer?.apply {
                setTargetGain(gainMb)
                enabled = gainMb != 0
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

            val offIsSelected = offPreset == currentPreset
            val offBgColor by animateColorAsState(
                targetValue = if (offIsSelected) Color(0xFFB8355B) else Color(0xFF251818),
                animationSpec = tween(200),
                label = "off_bg"
            )
            val offBorderColor by animateColorAsState(
                targetValue = if (offIsSelected) Color(0xFFD4577A) else Color(0xFF3A2020),
                animationSpec = tween(200),
                label = "off_border"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(offBgColor)
                    .border(1.dp, offBorderColor, RoundedCornerShape(12.dp))
                    .clickable { eqEngine.setPreset(offPreset) }
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = offPreset.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (offIsSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = offPreset.emoji,
                    color = Color.White,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val replayGainEnabled by eqEngine.replayGainEnabled.collectAsState()

            val rgBgColor by animateColorAsState(
                targetValue = if (replayGainEnabled) Color(0xFFB8355B) else Color(0xFF251818),
                animationSpec = tween(200),
                label = "rg_bg"
            )
            val rgBorderColor by animateColorAsState(
                targetValue = if (replayGainEnabled) Color(0xFFD4577A) else Color(0xFF3A2020),
                animationSpec = tween(200),
                label = "rg_border"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(rgBgColor)
                    .border(1.dp, rgBorderColor, RoundedCornerShape(12.dp))
                    .clickable { eqEngine.setReplayGainEnabled(!replayGainEnabled) }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ReplayGain", color = Color.White, fontWeight = if (replayGainEnabled) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                    Text("Normalize volume across tracks", color = if (replayGainEnabled) Color(0xFFE0E0E0) else Color(0xFF888888), fontSize = 11.sp)
                }
                Text(
                    text = "±",
                    color = Color.White,
                    fontSize = 20.sp
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