package com.kanagawa.yamada.inaho

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    val playerState by PlayerService.playerState.collectAsState()
    val artCache by musicViewModel.artCache.collectAsState()
    val playerService = rememberPlayerService()

    val song = playerState.currentSong
    val coverBitmap: Bitmap? = song?.let { artCache[it.id] }

    // Shuffle & Repeat UI-only toggle state (Now collected from ViewModel)
    val isShuffled by musicViewModel.isShuffled.collectAsState()
    val isRepeating by musicViewModel.isRepeating.collectAsState()

    val durationMs = playerState.durationMs.coerceAtLeast(1L)

    // Scrubbing state
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var livePositionMs by remember { mutableLongStateOf(0L) }

    // Continuous Sync Loop for Slider while playing - Now observing playerService connection status
    LaunchedEffect(playerState.isPlaying, playerService, playerState.currentSong?.id) {
        if (playerService != null) {
            // Initial sync (especially for when paused/resumed)
            val currentPos = playerService.getCurrentPosition()
            if (!isSeeking) {
                livePositionMs = currentPos
                seekValue = (currentPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }

            // Sync continuously while playing
            while (playerState.isPlaying) {
                delay(200) // 200ms delay to make slider updates smooth
                val pos = playerService.getCurrentPosition()
                if (!isSeeking) {
                    livePositionMs = pos
                    seekValue = (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0A0A))
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {

        // ── Top Bar ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.offset(x = (-8).dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFB8355B)
                )
            }
            IconButton(onClick = { /* More options placeholder */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color(0xFFB8355B)
                )
            }
        }

        // ── Album Art ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1414)),
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

        Spacer(modifier = Modifier.height(20.dp))

        // ── Song Title & Artist ───────────────────────────────────
        Text(
            text = song?.title ?: "No song selected",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song?.artist ?: "",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Seek Bar ──────────────────────────────────────────────
        Slider(
            value = seekValue,
            onValueChange = { value ->
                isSeeking = true
                seekValue = value
                livePositionMs = (value * durationMs).toLong()
            },
            onValueChangeFinished = {
                isSeeking = false
                playerService?.seekTo((seekValue * durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF3D3030)
            )
        )

        // Timestamps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(livePositionMs), color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Text(formatMs(durationMs), color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Controls Row ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(
                onClick = { musicViewModel.toggleShuffle() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffled) Color(0xFFB8355B) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Previous
            IconButton(
                onClick = { playerService?.skipPrev() },
                enabled = song != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (song != null) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(34.dp)
                )
            }

            // Play / Pause
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { playerService?.togglePlayPause() },
                    enabled = song != null,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0D0A0A),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Next
            IconButton(
                onClick = { playerService?.skipNext() },
                enabled = playerState.hasNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = if (playerState.hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(34.dp)
                )
            }

            // Repeat
            IconButton(
                onClick = { musicViewModel.toggleRepeat() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isRepeating) Icons.Default.RepeatOne
                    else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeating) Color(0xFFB8355B) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}