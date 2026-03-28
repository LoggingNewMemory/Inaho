package com.kanagawa.yamada.inaho

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // Live position ticker — updates every 500ms while playing
    var livePositionMs by remember { mutableLongStateOf(playerState.positionMs) }
    LaunchedEffect(playerState.isPlaying, playerState.currentSong) {
        while (playerState.isPlaying) {
            livePositionMs = playerService?.getCurrentPosition() ?: livePositionMs
            delay(500)
        }
    }
    LaunchedEffect(playerState.positionMs) {
        livePositionMs = playerState.positionMs
    }

    val durationMs = playerState.durationMs.coerceAtLeast(1L)
    val sliderProgress = (livePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A0A0E), Color(0xFF120E0E))
                )
            )
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
    ) {
        // ---- Top Bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
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
            Text(
                text = "Now Playing",
                color = Color(0xFFB8355B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = (-8).dp)
            )
        }

        // ---- Album Art ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2C2C2C)),
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
                // Tasteful empty state instead of "[SONG COVER]" text
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3D2020)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("♪", color = Color(0xFFB8355B), fontSize = 36.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ---- Song Title & Artist ----
        Text(
            text = song?.title ?: "No song selected",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song?.artist ?: "",
            color = Color(0xFFB8B8B8),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Seek Bar ----
        var isSeeking by remember { mutableStateOf(false) }
        var seekValue by remember { mutableFloatStateOf(sliderProgress) }

        // Keep seekValue in sync when not scrubbing
        LaunchedEffect(sliderProgress) {
            if (!isSeeking) seekValue = sliderProgress
        }

        Slider(
            value = seekValue,
            onValueChange = { value ->
                isSeeking = true
                seekValue = value
                livePositionMs = (value * durationMs).toLong()
            },
            onValueChangeFinished = {
                playerService?.seekTo((seekValue * durationMs).toLong())
                isSeeking = false
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFB8355B),
                activeTrackColor = Color(0xFFB8355B),
                inactiveTrackColor = Color(0xFF3D2020)
            )
        )

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(livePositionMs), color = Color(0xFFB8B8B8), fontSize = 12.sp)
            Text(formatMs(durationMs), color = Color(0xFFB8B8B8), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Playback Controls ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous
            IconButton(
                onClick = { playerService?.skipPrev() },
                enabled = playerState.currentSong != null,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (playerState.currentSong != null) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play / Pause — large center button
            IconButton(
                onClick = { playerService?.togglePlayPause() },
                enabled = playerState.currentSong != null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB8355B))
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Next
            IconButton(
                onClick = { playerService?.skipNext() },
                enabled = playerState.hasNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = if (playerState.hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // ---- Up Next — pushed to bottom with Spacer, never overlaps controls ----
        Spacer(modifier = Modifier.weight(1f))

        playerState.nextSong?.let { next ->
            HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Up Next", color = Color(0xFFB8B8B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        next.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        next.artist,
                        color = Color(0xFFB8B8B8),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    next.formattedDuration,
                    color = Color(0xFFB8B8B8),
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}