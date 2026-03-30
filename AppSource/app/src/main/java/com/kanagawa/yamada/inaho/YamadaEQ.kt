package com.kanagawa.yamada.inaho

import android.content.Context
import android.media.audiofx.DynamicsProcessing
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

// ==========================================
// EQ PRESET MODELS
// ==========================================

/**
 * Yamada EQ Presets
 *
 * Each preset carries:
 * - [bands]  : gain in millibels for the 5 standard Android EQ bands
 * (60 Hz | 230 Hz | 910 Hz | 3.6 kHz | 14 kHz)
 * - [loudnessGainMb] : extra loudness-enhancer boost in millibels (0 = off)
 * - [smartTunnel]    : enables the "Audio Tunnel" dynamic-gain logic (Smart only)
 *
 * Band indices (Android standard):
 * 0 → ~60 Hz   (Sub-bass)
 * 1 → ~230 Hz  (Bass)
 * 2 → ~910 Hz  (Low-mid)
 * 3 → ~3600 Hz (Presence)
 * 4 → ~14000 Hz(Air / Brilliance)
 */
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
        loudnessGainMb = 800, // Boosted fallback loudness
        smartTunnel = true
    ),
    ROCK(
        displayName = "Rock",
        emoji = "♟",
        description = "Punchy bass, scooped mids, crisp highs",
        bands = intArrayOf(500, 300, -200, 200, 400),
        loudnessGainMb = 800 // Boosted from 200
    ),
    JAZZ(
        displayName = "Jazz",
        emoji = "♩",
        description = "Warm low-mids, airy top end",
        bands = intArrayOf(300, 200, 100, 0, 200),
        loudnessGainMb = 500 // Boosted from 100
    ),
    CLASSIC(
        displayName = "Classic",
        emoji = "𝄞",
        description = "Flat response, natural dynamics",
        bands = intArrayOf(0, 0, 0, 0, 0),
        loudnessGainMb = 300 // Given a gentle boost from 0 so it's not too quiet
    ),
    POP(
        displayName = "Pop",
        emoji = "♪",
        description = "Boosted vocals & presence, tight bass",
        bands = intArrayOf(-100, 200, 300, 200, 100),
        loudnessGainMb = 600 // Boosted from 150
    ),
    BASS(
        displayName = "Bass",
        emoji = "◉",
        description = "Heavy sub & bass boost for earphones",
        bands = intArrayOf(800, 600, 0, -100, -100),
        loudnessGainMb = 1000 // Boosted from 400 (Max heavy punch)
    )
}

// ==========================================
// EQ MANAGER  (attach to MediaPlayer audio session)
// ==========================================

/**
 * YamadaEQManager
 *
 * Lifecycle: create once per [PlayerService], call [attach] when a new
 * MediaPlayer session starts, [release] when the player is torn down.
 *
 * Smart Audio Tunnel logic:
 * Android's [DynamicsProcessing] API (API 28+) is used to apply a
 * multi-band compressor that rides the gain: loud transients (beat hits)
 * are kept from clipping while quiet passages are gently lifted, giving
 * the perception that the music "breathes" with the beat.
 * On API < 28, a simple LoudnessEnhancer is used as a fallback.
 */
class YamadaEQManager(private val context: Context) {

    private val _currentPreset = MutableStateFlow(EqPreset.OFF)
    val currentPreset = _currentPreset.asStateFlow()

    private var audioSessionId: Int = 0

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessor: Any? = null   // DynamicsProcessing (API 28+), kept as Any to avoid hard API ref

    // ── Shared Preferences persistence ──
    private val prefs = context.getSharedPreferences("inaho_eq", Context.MODE_PRIVATE)

    init {
        val savedName = prefs.getString("preset", EqPreset.OFF.name) ?: EqPreset.OFF.name
        _currentPreset.value = runCatching { EqPreset.valueOf(savedName) }.getOrDefault(EqPreset.OFF)
    }

    /** Called by PlayerService every time a new MediaPlayer is created. */
    fun attach(sessionId: Int) {
        audioSessionId = sessionId
        applyPreset(_currentPreset.value, sessionId)
    }

    /** Call when MediaPlayer is released. */
    fun release() {
        tearDown()
        audioSessionId = 0
    }

    fun setPreset(preset: EqPreset) {
        prefs.edit().putString("preset", preset.name).apply()
        _currentPreset.value = preset
        if (audioSessionId != 0) applyPreset(preset, audioSessionId)
    }

    // ── Private helpers ──

    private fun tearDown() {
        runCatching { equalizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { (dynamicsProcessor as? DynamicsProcessing)?.release() }
        }
        equalizer = null
        loudnessEnhancer = null
        dynamicsProcessor = null
    }

    private fun applyPreset(preset: EqPreset, sessionId: Int) {
        tearDown()

        if (preset == EqPreset.OFF) return   // bypass — no effects attached

        // 1. Equalizer bands
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bandCount = numberOfBands.toInt()
                preset.bands.take(bandCount).forEachIndexed { i, gainMb ->
                    // Android EQ band levels are in millibels; getBandLevelRange() gives [min, max]
                    val min = bandLevelRange[0].toInt()
                    val max = bandLevelRange[1].toInt()
                    setBandLevel(i.toShort(), gainMb.coerceIn(min, max).toShort())
                }
            }
        }

        // 2. Smart Audio Tunnel  — DynamicsProcessing (API 28+) or LoudnessEnhancer fallback
        if (preset.smartTunnel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    /* channelCount */ 2,
                    /* preEqInUse   */ false, 0,
                    /* mbcInUse     */ true,  1,   // 1-band MBC compressor
                    /* postEqInUse  */ false, 0,
                    /* limiterInUse */ true
                ).build()

                val dp = DynamicsProcessing(0, sessionId, config).apply {
                    // Apply Dynamics Processing to BOTH channels (0 = Left, 1 = Right)
                    for (ch in 0..1) {
                        // MBC band: attack fast on beats, slow release so gaps stay loud
                        val channel = getMbcBandByChannelIndex(ch, 0)
                        val tunedBand = DynamicsProcessing.MbcBand(channel).apply {
                            attackTime    = 5f    // ms — snap to beat transient
                            releaseTime   = 200f  // ms — gentle release
                            ratio         = 2.0f  // softer compression ratio to save volume
                            threshold     = -24f  // dB — compress lower signals too
                            kneeWidth     = 6f    // dB — soft knee
                            noiseGateThreshold = -80f
                            expanderRatio = 1.0f
                            preGain       = 8f    // CRANKED UP makeup gain
                            postGain      = 6f    // CRANKED UP post gain
                        }
                        setMbcBandByChannelIndex(ch, 0, tunedBand)

                        // Limiter: prevent clipping after makeup gain
                        val lim = getLimiterByChannelIndex(ch)
                        val tunedLim = DynamicsProcessing.Limiter(lim).apply {
                            attackTime  = 1f
                            releaseTime = 50f
                            ratio       = 10f
                            threshold   = -0.5f  // dB — hard ceiling just below 0 dBFS
                            postGain    = 4f     // CRANKED UP master boost
                        }
                        setLimiterByChannelIndex(ch, tunedLim)
                    }

                    enabled = true
                }
                dynamicsProcessor = dp
            }
        } else if (preset.loudnessGainMb > 0) {
            // Fallback / non-smart presets: plain loudness boost
            runCatching {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    setTargetGain(preset.loudnessGainMb)
                    enabled = true
                }
            }
        }

        // 3. For Smart on API < 28, still apply loudness boost as fallback
        if (preset.smartTunnel && Build.VERSION.SDK_INT < Build.VERSION_CODES.P && preset.loudnessGainMb > 0) {
            runCatching {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    setTargetGain(preset.loudnessGainMb)
                    enabled = true
                }
            }
        }
    }
}

// ==========================================
// EQ DIALOG UI
// ==========================================

/**
 * EQ picker dialog — matches the dark red Inaho design language.
 * Shows OFF at the top, then 6 preset tiles in a 3-column grid,
 * with a band-level visualizer bar for the selected preset.
 */
@Composable
fun EqDialog(
    eqManager: YamadaEQManager,
    onDismiss: () -> Unit
) {
    val currentPreset by eqManager.currentPreset.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1010))
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "Yamada EQ",
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

            // Split OFF preset from the rest
            val presets = EqPreset.values().toList()
            val offPreset = presets.first { it == EqPreset.OFF }
            val otherPresets = presets.filter { it != EqPreset.OFF }

            // 1. Full width OFF Preset
            Row(modifier = Modifier.fillMaxWidth()) {
                EqPresetTile(
                    preset = offPreset,
                    isSelected = offPreset == currentPreset,
                    onClick = { eqManager.setPreset(offPreset) },
                    modifier = Modifier.fillMaxWidth() // Takes up the whole row
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Preset grid for the rest (3 columns)
            otherPresets.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        EqPresetTile(
                            preset = preset,
                            isSelected = preset == currentPreset,
                            onClick = { eqManager.setPreset(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty cells in last row
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Band visualizer (hidden for OFF)
            if (currentPreset != EqPreset.OFF) {
                Spacer(modifier = Modifier.height(8.dp))
                EqBandVisualizer(preset = currentPreset)
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
            text = preset.emoji,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = preset.displayName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Minimal 5-band bar chart showing the selected preset's EQ curve.
 * Bars are normalized so the tallest band = full height.
 */
@Composable
private fun EqBandVisualizer(preset: EqPreset) {
    val bandLabels = listOf("60", "230", "910", "3.6k", "14k")
    val maxGain = 1000   // millibels — reference ceiling for bar height

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "FREQUENCY RESPONSE",
            color = Color(0xFF555555),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            preset.bands.forEachIndexed { i, gainMb ->
                val fraction = ((gainMb + maxGain).toFloat() / (2 * maxGain)).coerceIn(0.05f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (gainMb > 0) Color(0xFFB8355B)
                                else if (gainMb < 0) Color(0xFF553030)
                                else Color(0xFF3A2424)
                            )
                    )
                }
            }
        }
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bandLabels.forEach { label ->
                Text(
                    text = label,
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}