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

class YamadaEQManager(private val context: Context) {

    private val _currentPreset = MutableStateFlow(EqPreset.OFF)
    val currentPreset = _currentPreset.asStateFlow()

    private var audioSessionId: Int = 0

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessor: Any? = null

    private val prefs = context.getSharedPreferences("inaho_eq", Context.MODE_PRIVATE)

    init {
        val savedName = prefs.getString("preset", EqPreset.OFF.name) ?: EqPreset.OFF.name
        _currentPreset.value = runCatching { EqPreset.valueOf(savedName) }.getOrDefault(EqPreset.OFF)
    }

    fun attach(sessionId: Int) {
        audioSessionId = sessionId
        applyPreset(_currentPreset.value, sessionId)
    }

    fun release() {
        tearDown()
        audioSessionId = 0
    }

    fun setPreset(preset: EqPreset) {
        prefs.edit().putString("preset", preset.name).apply()
        _currentPreset.value = preset
        if (audioSessionId != 0) applyPreset(preset, audioSessionId)
    }

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

        if (preset == EqPreset.OFF) return

        // 1. Equalizer bands
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bandCount = numberOfBands.toInt()
                preset.bands.take(bandCount).forEachIndexed { i, gainMb ->
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
                    /* mbcInUse     */ true,  1,
                    /* postEqInUse  */ false, 0,
                    /* limiterInUse */ true
                ).build()

                val dp = DynamicsProcessing(0, sessionId, config).apply {
                    for (ch in 0..1) {
                        val channel = getMbcBandByChannelIndex(ch, 0)
                        val tunedBand = DynamicsProcessing.MbcBand(channel).apply {
                            attackTime    = 5f
                            releaseTime   = 200f
                            ratio         = 2.2f
                            threshold     = -20f
                            kneeWidth     = 6f
                            noiseGateThreshold = -80f
                            expanderRatio = 1.0f
                            preGain       = 5f    // BOOSTED: Drive more signal into the compressor
                            postGain      = 8.5f    // BOOSTED: Makeup gain for what the compressor eats
                        }
                        setMbcBandByChannelIndex(ch, 0, tunedBand)

                        val lim = getLimiterByChannelIndex(ch)
                        val tunedLim = DynamicsProcessing.Limiter(lim).apply {
                            attackTime  = 1f
                            releaseTime = 50f
                            ratio       = 10f
                            threshold   = -0.5f  // Slightly higher ceiling before brickwalling
                            postGain    = 5f   // BOOSTED: Extra master lift to stand shoulder-to-shoulder with Classic
                        }
                        setLimiterByChannelIndex(ch, tunedLim)
                    }
                    enabled = true
                }
                dynamicsProcessor = dp
            }
        } else if (preset.loudnessGainMb > 0) {
            runCatching {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    setTargetGain(preset.loudnessGainMb)
                    enabled = true
                }
            }
        }

        // 3. For Smart on API < 28, apply fallback
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

            val presets = EqPreset.values().toList()
            val offPreset = presets.first { it == EqPreset.OFF }
            val otherPresets = presets.filter { it != EqPreset.OFF }

            Row(modifier = Modifier.fillMaxWidth()) {
                EqPresetTile(
                    preset = offPreset,
                    isSelected = offPreset == currentPreset,
                    onClick = { eqManager.setPreset(offPreset) },
                    modifier = Modifier.fillMaxWidth()
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
                            onClick = { eqManager.setPreset(preset) },
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

@Composable
private fun EqBandVisualizer(preset: EqPreset) {
    val bandLabels = listOf("60", "230", "910", "3.6k", "14k")
    val maxGain = 1000

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