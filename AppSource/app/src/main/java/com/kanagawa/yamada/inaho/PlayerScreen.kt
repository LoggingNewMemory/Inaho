package com.kanagawa.yamada.inaho

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val artCache by musicViewModel.artCache.collectAsState()
    val playerService = rememberPlayerService()

    val song = playerState.currentSong
    val coverBitmap: Bitmap? = song?.let { artCache[it.id] }

    val isShuffled = playerState.isShuffled
    val repeatMode = playerState.repeatMode

    val durationMs = playerState.durationMs.coerceAtLeast(1L)

    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var livePositionMs by remember { mutableLongStateOf(0L) }

    // State for our Format Detector
    var audioDetails by remember { mutableStateOf<AudioDetails?>(null) }

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

    // Effect to extract actual audio metadata when the song changes
    LaunchedEffect(song?.id) {
        if (song != null) {
            withContext(Dispatchers.IO) {
                audioDetails = extractAudioDetails(context, song.id)
            }
        } else {
            audioDetails = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0A0A))
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        // Updated Top Bar using a Box for perfect centering
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-8).dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFB8355B)
                )
            }

            // FLAC / Audio Detector Badge
            audioDetails?.let { details ->
                Text(
                    text = "${details.format} • ${details.sampleRate} • ${details.bitDepth} • ${details.bitRate}",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xFF1E1414), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = { /* More options placeholder */ },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color(0xFFB8355B)
                )
            }
        }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(livePositionMs), color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Text(formatMs(durationMs), color = Color(0xFFAAAAAA), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { playerService?.toggleShuffle() },
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

            IconButton(
                onClick = { playerService?.skipNext(isAutoCompletion = false) },
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

            IconButton(
                onClick = { playerService?.toggleRepeat() },
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
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

// --- Audio Metadata Extractor ---
private fun extractAudioDetails(context: Context, songId: Any): AudioDetails? {
    // Generate URI safely depending on whether songId is an ID or a string path
    val uri = try {
        val idAsLong = songId.toString().toLong()
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, idAsLong)
    } catch (e: Exception) {
        Uri.parse(songId.toString())
    }

    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        // 1. Format (MIME)
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
        val formatStr = when {
            mime.contains("flac", true) -> "FLAC"
            mime.contains("mpeg", true) -> "MP3"
            mime.contains("mp4", true) -> "M4A"
            mime.contains("wav", true) -> "WAV"
            mime.contains("ogg", true) -> "OGG"
            mime.contains("aac", true) -> "AAC"
            mime.isNotEmpty() -> mime.substringAfterLast("/").uppercase()
            else -> "UNKNOWN"
        }

        // 2. Bitrate
        val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val bitrateKbps = bitrateStr?.toLongOrNull()?.div(1000)?.toString() ?: "Unknown"

        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var sampleRate = "Unknown"
        var bitDepth = "16" // Common Default

        if (extractor.trackCount > 0) {
            val format = extractor.getTrackFormat(0)

            // 3. Sample Rate
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                sampleRate = if (sr % 1000 == 0) "${sr / 1000}" else "${sr / 1000f}"
            }

            // 4. Bit Depth
            if (format.containsKey("bits-per-sample")) {
                bitDepth = format.getInteger("bits-per-sample").toString()
            } else if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                val pcm = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                bitDepth = when (pcm) {
                    AudioFormat.ENCODING_PCM_8BIT -> "8"
                    AudioFormat.ENCODING_PCM_16BIT -> "16"
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24"
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