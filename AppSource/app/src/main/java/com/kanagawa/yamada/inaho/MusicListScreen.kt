package com.kanagawa.yamada.inaho

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// --- 1. Data Model ---
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

// --- 2. Media Store Fetcher ---
fun getAudioFiles(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    // Use the correct URI based on Android version
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )

    // Only fetch actual music files
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(
        collection,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn) ?: "Unknown Title"
            val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
            val duration = cursor.getLong(durationColumn)

            // Filter out super short files (e.g., notification sounds < 10 seconds)
            if (duration > 10000) {
                songs.add(Song(id, title, artist, duration))
            }
        }
    }
    return songs
}

// --- 3. The UI Screens ---
@Composable
fun MusicListScreen() {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    // Determine which permission to ask for based on Android version
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Launcher to ask the user for permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            songs = getAudioFiles(context)
        }
    }

    // Check permission on screen load
    LaunchedEffect(Unit) {
        val status = ContextCompat.checkSelfPermission(context, permissionToRequest)
        if (status == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            songs = getAudioFiles(context)
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    // Main UI Background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Dark background from your image
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inaho",
                color = Color(0xFFD81B60), // The pink/red color from your design
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            // Actual Sort Icon
            IconButton(onClick = { /* TODO: Sort actions */ }) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Sort Music",
                    tint = Color.White
                )
            }

            // Actual Settings Icon
            IconButton(onClick = { /* TODO: Open settings */ }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (!hasPermission) {
            Text("Storage permission is required to find your music.", color = Color.White)
        } else if (songs.isEmpty()) {
            Text("No music files found on this device.", color = Color.White)
        } else {
            // Scrollable List of Songs
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(songs) { song ->
                    SongListItem(song)
                }
            }
        }
    }
}

@Composable
fun SongListItem(song: Song) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Square Cover
        Box(
            modifier = Modifier
                .size(65.dp)
                .background(Color.White, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[Song\nCover]",
                color = Color.Black,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Title and Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.LightGray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        Text(
            text = song.formattedDuration,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}