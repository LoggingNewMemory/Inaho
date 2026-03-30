package com.kanagawa.yamada.inaho

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// --- Audio Details Data Class ---
data class AudioDetails(
    val format: String,
    val sampleRate: String,
    val bitDepth: String,
    val bitRate: String
)

@Composable
fun PlayerScreen(
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val playerState by PlayerService.playerState.collectAsState()
    val artCache   by musicViewModel.artCache.collectAsState()
    val favorites  by musicViewModel.favoritesManager.favoritesFlow.collectAsState()
    val playerService = rememberPlayerService()
    val settings   by musicViewModel.settingsManager.settingsFlow.collectAsState()

    val song        = playerState.currentSong
    val coverBitmap: Bitmap? = song?.let { artCache[it.id] }

    val isShuffled  = playerState.isShuffled
    val repeatMode  = playerState.repeatMode

    val durationMs  = playerState.durationMs.coerceAtLeast(1L)

    var isSeeking       by remember { mutableStateOf(false) }
    var seekValue       by remember { mutableFloatStateOf(0f) }
    var livePositionMs  by remember { mutableLongStateOf(0L) }

    var audioDetails by remember { mutableStateOf<AudioDetails?>(null) }

    var showSpeedDialog     by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet      by remember { mutableStateOf(false) }
    // ── NEW: Yamada EQ dialog ──────────────────────────────────────────────
    var showEqDialog        by remember { mutableStateOf(false) }
    // ──────────────────────────────────────────────────────────────────────
    var sleepTimerRemainingMs by remember { mutableLongStateOf(-1L) }

    // currentSpeedLabel resets to "1.0×" whenever the song changes
    val currentSongId = song?.id
    var currentSpeedLabel by remember { mutableStateOf("1.0×") }
    LaunchedEffect(currentSongId) { currentSpeedLabel = "1.0×" }

    // ── Volume control ──
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume    = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    var volumeValue by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                    if (newVolume != volumeValue) volumeValue = newVolume
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        onDispose { context.unregisterReceiver(receiver) }
    }

    val bgColor      = if (settings.amoledBlack) Color.Black else Color(0xFF0D0A0A)
    val surfaceColor = if (settings.amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)

    BackHandler { onNavigateBack() }

    LaunchedEffect(playerState.isPlaying, playerService, playerState.currentSong?.id) {
        if (playerService != null) {
            val currentPos = playerService.getCurrentPosition()
            if (!isSeeking) {
                livePositionMs = currentPos
                seekValue = (currentPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
            while (playerState.isPlaying) {
                delay(200)
                val pos = playerService.getCurrentPosition()
                if (!isSeeking) {
                    livePositionMs = pos
                    seekValue = (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            }
        }
    }

    LaunchedEffect(song?.id) {
        if (song != null) {
            withContext(Dispatchers.IO) {
                audioDetails = extractAudioDetails(context, song.id)
            }
        } else {
            audioDetails = null
        }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepTimerRemainingMs) {
        if (sleepTimerRemainingMs > 0) {
            delay(1000)
            val next = sleepTimerRemainingMs - 1000
            if (next <= 0) {
                sleepTimerRemainingMs = -1L
                playerService?.togglePlayPause()
                if (playerState.isPlaying) playerService?.togglePlayPause()
            } else {
                sleepTimerRemainingMs = next
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        // ── Top Bar ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-12).dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFB8355B)
                )
            }

            audioDetails?.let { details ->
                Text(
                    text = "${details.format} • ${details.sampleRate} • ${details.bitDepth} • ${details.bitRate}",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(surfaceColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = { showQueueSheet = !showQueueSheet },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = if (showQueueSheet) Color(0xFFB8355B) else Color.White
                )
            }
        }

        // ── Queue Sheet ──
        AnimatedVisibility(
            visible = showQueueSheet,
            enter = expandVertically(),
            exit  = shrinkVertically()
        ) {
            QueuePanel(
                playerState = playerState,
                artCache    = artCache,
                onSongClick = { _, index ->
                    playerService?.jumpToQueueIndex(index)
                    showQueueSheet = false
                }
            )
        }

        if (!showQueueSheet) {
            // ── Cover Art ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceColor),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF3D2020),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Title + Favorite ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "No song selected",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = song?.artist ?: "",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (song != null) {
                val isFav = favorites.contains(song.id)
                IconButton(onClick = { musicViewModel.favoritesManager.toggle(song.id) }) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) Color(0xFFB8355B) else Color(0xFF555555),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Seek Bar ──
        Slider(
            value = seekValue,
            onValueChange = { value ->
                isSeeking  = true
                seekValue  = value
                livePositionMs = (value * durationMs).toLong()
            },
            onValueChangeFinished = {
                isSeeking = false
                playerService?.seekTo((seekValue * durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor         = Color.White,
                activeTrackColor   = Color.White,
                inactiveTrackColor = Color(0xFF3D3030)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(livePositionMs), color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Text(formatMs(durationMs),     color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Volume Slider ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VolumeDown,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(18.dp)
            )
            Slider(
                value = volumeValue,
                onValueChange = { v ->
                    val newStep = (v * maxVolume).toInt()
                    val oldStep = (volumeValue * maxVolume).toInt()
                    volumeValue = v
                    if (newStep != oldStep) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newStep, 0)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(
                    thumbColor         = Color(0xFFB8355B),
                    activeTrackColor   = Color(0xFFB8355B),
                    inactiveTrackColor = Color(0xFF3D3030)
                )
            )
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Extra Controls Row: Speed · EQ · Sleep Timer ──
        // Derive EQ active state for chip highlight
        val eqPreset by playerService?.eqManager?.currentPreset?.collectAsState()
            ?: run { remember { mutableStateOf(EqPreset.OFF) } }.let { remember { it } }
            .let { mutableStateOf(EqPreset.OFF) }.let { remember { it } }
            // Simpler pattern below — safe even before service binds:
        val eqActiveLabel = remember(eqPreset) {
            if (eqPreset == EqPreset.OFF) "EQ" else eqPreset.displayName
        }
        val eqIsActive = eqPreset != EqPreset.OFF

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed chip
            ExtraControlChip(
                icon    = Icons.Default.Speed,
                label   = currentSpeedLabel,
                active  = currentSpeedLabel != "1.0×",
                onClick = { showSpeedDialog = true }
            )

            // ── Yamada EQ chip ────────────────────────────────────────────
            ExtraControlChip(
                icon    = Icons.Default.Equalizer,
                label   = eqActiveLabel,
                active  = eqIsActive,
                onClick = { showEqDialog = true }
            )
            // ─────────────────────────────────────────────────────────────

            // Sleep timer chip
            ExtraControlChip(
                icon    = Icons.Default.Bedtime,
                label   = if (sleepTimerRemainingMs > 0) formatMs(sleepTimerRemainingMs) else "Sleep",
                active  = sleepTimerRemainingMs > 0,
                onClick = { showSleepTimerDialog = true }
            )
        }

        // ── Main Controls Row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick  = { playerService?.toggleShuffle() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffled) Color(0xFFB8355B) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick  = { playerService?.skipPrev() },
                enabled  = song != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (song != null) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(34.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick  = { playerService?.togglePlayPause() },
                    enabled  = song != null,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0D0A0A),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            IconButton(
                onClick  = { playerService?.skipNext(isAutoCompletion = false) },
                enabled  = playerState.hasNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = if (playerState.hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(
                onClick  = { playerService?.toggleRepeat() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode == RepeatMode.ONE) Color(0xFFB8355B) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    // ── Dialogs ──

    if (showSpeedDialog) {
        SpeedDialog(
            currentLabel = currentSpeedLabel,
            onSelect = { speed, label ->
                currentSpeedLabel = label
                playerService?.setPlaybackSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // ── Yamada EQ Dialog ──────────────────────────────────────────────────
    if (showEqDialog) {
        val service = playerService
        if (service != null) {
            EqDialog(
                eqManager = service.eqManager,
                onDismiss = { showEqDialog = false }
            )
        } else {
            // Service not yet bound — show a simple "unavailable" toast-style dialog
            Dialog(onDismissRequest = { showEqDialog = false }) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1A1010))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("EQ unavailable — start playback first", color = Color(0xFF888888), fontSize = 14.sp)
                }
            }
        }
    }
    // ─────────────────────────────────────────────────────────────────────

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isActive = sleepTimerRemainingMs > 0,
            onSelect = { minutes ->
                sleepTimerRemainingMs = minutes * 60 * 1000L
                showSleepTimerDialog  = false
            },
            onCancel = {
                sleepTimerRemainingMs = -1L
                showSleepTimerDialog  = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}

// ==========================================
// EXTRA CONTROL CHIP
// ==========================================
@Composable
private fun ExtraControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF2A1020) else Color(0xFF1E1414))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFFB8355B) else Color(0xFF888888),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) Color(0xFFB8355B) else Color.White,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

// ==========================================
// QUEUE PANEL
// ==========================================
@Composable
fun QueuePanel(
    playerState: PlayerState,
    artCache: Map<Long, Bitmap?>,
    onSongClick: (Song, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val queue     = playerState.activeQueue

    LaunchedEffect(playerState.currentIndex) {
        if (playerState.currentIndex >= 0 && playerState.currentIndex < queue.size) {
            listState.animateScrollToItem(playerState.currentIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1010))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Up Next",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${queue.size} songs",
                color = Color(0xFF666666),
                fontSize = 12.sp
            )
        }
        HorizontalDivider(color = Color(0xFF2C2020), thickness = 0.5.dp)

        LazyColumn(state = listState) {
            itemsIndexed(queue) { index, qSong ->
                val isCurrentSong = index == playerState.currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(qSong, index) }
                        .background(if (isCurrentSong) Color(0xFF2A1515) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val art = artCache[qSong.id]
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2C2C2C))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = qSong.title,
                            color = if (isCurrentSong) Color(0xFFB8355B) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = qSong.artist,
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isCurrentSong) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Now playing",
                            tint = Color(0xFFB8355B),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = qSong.formattedDuration,
                            color = Color(0xFF666666),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ==========================================
// SPEED DIALOG
// ==========================================
@Composable
fun SpeedDialog(
    currentLabel: String,
    onSelect: (Float, String) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(
        0.5f  to "0.5×",
        0.75f to "0.75×",
        1.0f  to "1.0×",
        1.25f to "1.25×",
        1.5f  to "1.5×",
        2.0f  to "2.0×"
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1414))
                .padding(20.dp)
        ) {
            Text(
                "Playback Speed",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            speeds.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (speed, label) ->
                        val isSelected = label == currentLabel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFB8355B) else Color(0xFF2C2020))
                                .clickable { onSelect(speed, label) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ==========================================
// SLEEP TIMER DIALOG
// ==========================================
@Composable
fun SleepTimerDialog(
    isActive: Boolean,
    onSelect: (Long) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5L to "5 min", 10L to "10 min", 15L to "15 min", 20L to "20 min", 30L to "30 min", 60L to "1 hour")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1414))
                .padding(20.dp)
        ) {
            Text("Sleep Timer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp))
            Text("Pause music after…", color = Color(0xFF888888), fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp))

            options.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (minutes, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2C2020))
                                .clickable { onSelect(minutes) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = label, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (isActive) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3D1515))
                        .clickable { onCancel() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel Timer", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ==========================================
// HELPERS
// ==========================================
private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun extractAudioDetails(context: Context, songId: Any): AudioDetails? {
    val uri = try {
        val idAsLong = songId.toString().toLong()
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, idAsLong)
    } catch (e: Exception) {
        Uri.parse(songId.toString())
    }

    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
        val formatStr = when {
            mime.contains("flac", true) -> "FLAC"
            mime.contains("mpeg", true) -> "MP3"
            mime.contains("mp4",  true) -> "M4A"
            mime.contains("wav",  true) -> "WAV"
            mime.contains("ogg",  true) -> "OGG"
            mime.contains("aac",  true) -> "AAC"
            mime.isNotEmpty()           -> mime.substringAfterLast("/").uppercase()
            else                        -> "UNKNOWN"
        }

        val bitrateStr  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val bitrateKbps = bitrateStr?.toLongOrNull()?.div(1000)?.toString() ?: "Unknown"

        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var sampleRate = "Unknown"
        var bitDepth   = "16"

        if (extractor.trackCount > 0) {
            val format = extractor.getTrackFormat(0)
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                sampleRate = if (sr % 1000 == 0) "${sr / 1000}" else "${sr / 1000f}"
            }
            if (format.containsKey("bits-per-sample")) {
                bitDepth = format.getInteger("bits-per-sample").toString()
            } else if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                val pcm = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                bitDepth = when (pcm) {
                    AudioFormat.ENCODING_PCM_8BIT              -> "8"
                    AudioFormat.ENCODING_PCM_16BIT             -> "16"
                    AudioFormat.ENCODING_PCM_24BIT_PACKED      -> "24"
                    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> "32"
                    else -> "16"
                }
            }
        }

        extractor.release()
        retriever.release()

        AudioDetails(formatStr, "${sampleRate} kHz", "$bitDepth Bit", "$bitrateKbps kbps")
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
