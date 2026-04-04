package com.kanagawa.yamada.inaho

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()
    val artCache by musicViewModel.artCache.collectAsState()
    val playerState by PlayerService.playerState.collectAsState()
    val playerService = rememberPlayerService()
    val fullLibrary by musicViewModel.loadedSongs.collectAsState()

    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val surfaceColor = if (settings.amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)

    val greetings = remember { listOf("Welcome", "いらっしゃいませ", "Selamat Datang", "Sugeng Rawuh") }
    val randomGreeting = remember { greetings.random() }

    // --- PERMISSION & LIBRARY LOADING LOGIC ADDED HERE ---
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions[Manifest.permission.READ_MEDIA_AUDIO] == true else permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        hasPermission = storageGranted
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)

        val neededPermissions = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (neededPermissions.isNotEmpty()) permissionLauncher.launch(neededPermissions.toTypedArray())
    }

    LaunchedEffect(hasPermission, settings.sortOption, settings.onlyMusicFolder) {
        if (hasPermission) {
            withContext(Dispatchers.IO) {
                try {
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
                    val sortOrder = when (settings.sortOption) {
                        SortOption.TITLE_ASC -> "${MediaStore.Audio.Media.TITLE} ASC"
                        SortOption.TITLE_DESC -> "${MediaStore.Audio.Media.TITLE} DESC"
                        SortOption.ARTIST_ASC -> "${MediaStore.Audio.Media.ARTIST} ASC"
                        SortOption.DATE_ADDED_DESC -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                        SortOption.DURATION_ASC -> "${MediaStore.Audio.Media.DURATION} ASC"
                        SortOption.DURATION_DESC -> "${MediaStore.Audio.Media.DURATION} DESC"
                    }
                    var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
                    if (settings.onlyMusicFolder) selection += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) " AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE '%Music/%'" else " AND ${MediaStore.Audio.Media.DATA} LIKE '%/Music/%'"

                    val tempList = mutableListOf<Song>()
                    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        while (c.moveToNext()) {
                            val id = c.getLong(idCol)
                            val dur = c.getLong(durationCol)
                            val title = c.getString(titleCol) ?: "Unknown"
                            val artist = c.getString(artistCol) ?: "Unknown"
                            tempList.add(Song(id, title, artist, dur, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), String.format("%02d:%02d", (dur / 1000) / 60, (dur / 1000) % 60)))
                        }
                    }
                    musicViewModel.recordLoadedSongs(tempList)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
    // -----------------------------------------------------

    // Pick up to 5 random songs for the grid
    val dailySongs = remember(fullLibrary) {
        if (fullLibrary.isNotEmpty()) fullLibrary.shuffled().take(5) else emptyList()
    }
    // Pick 10 random songs for the "Jump Back In" quick list
    val quickList = remember(fullLibrary) {
        if (fullLibrary.size > 5) fullLibrary.shuffled().take(10) else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        // --- Multi-line Greetings ---
        Text(text = "$randomGreeting,", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = settings.userName, color = Color(0xFFB8355B), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Song of The Day", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // --- Grid ---
        if (dailySongs.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Main Item
                val mainSong = dailySongs[0]
                LaunchedEffect(mainSong.id) { musicViewModel.loadArtIfNeeded(mainSong) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceColor)
                        .clickable {
                            playerService?.playSong(mainSong, fullLibrary, fullLibrary.indexOf(mainSong))
                            onNavigateToPlayer()
                        }
                ) {
                    val cover = artCache[mainSong.id]
                    if (cover != null) {
                        Image(bitmap = cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C2C)))
                    }
                }

                // 4 Small Items
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GridSmallItem(song = dailySongs.getOrNull(1), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                        GridSmallItem(song = dailySongs.getOrNull(2), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GridSmallItem(song = dailySongs.getOrNull(3), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                        GridSmallItem(song = dailySongs.getOrNull(4), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(surfaceColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(
                    text = if (!hasPermission) "Storage permission required." else "Not enough songs found in your library.",
                    color = Color.LightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Playlist Button ---
        Button(
            onClick = {
                if (fullLibrary.isNotEmpty()) {
                    val shuffled = fullLibrary.shuffled()
                    playerService?.playSong(shuffled[0], shuffled, 0)
                    musicViewModel.preloadQueueWindow(shuffled, 0)
                    onNavigateToPlayer()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2020))
        ) {
            Text("Let Inaho Make Your Playlist Today!", color = Color(0xFFB8355B), fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Suggested for You", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // --- Quick List ---
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = if (playerState.currentSong != null) 88.dp else 16.dp)
            ) {
                itemsIndexed(quickList) { index, song ->
                    LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                    SongListItem(
                        song = song,
                        coverBitmap = artCache[song.id],
                        isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                        onClick = {
                            val safeQueue = if (fullLibrary.isNotEmpty()) fullLibrary else listOf(song)
                            val queueIndex = safeQueue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
                            playerService?.playSong(song, safeQueue, queueIndex)
                            musicViewModel.preloadQueueWindow(safeQueue, queueIndex)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }

        // --- Mini Player ---
        AnimatedVisibility(
            visible = playerState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            MiniPlayerBar(
                playerState = playerState, playerService = playerService,
                coverBitmap = playerState.currentSong?.let { artCache[it.id] },
                onPlayPause = { playerService?.togglePlayPause() },
                onNext = { playerService?.skipNext() },
                onExpand = onNavigateToPlayer,
                surfaceColor = surfaceColor
            )
        }
    }
}

@Composable
private fun RowScope.GridSmallItem(
    song: Song?,
    artCache: Map<Long, Bitmap?>,
    vm: MusicViewModel,
    ps: PlayerService?,
    lib: List<Song>,
    nav: () -> Unit
) {
    if (song != null) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C2C))
                .clickable {
                    ps?.playSong(song, lib, lib.indexOf(song))
                    nav()
                }
        ) {
            LaunchedEffect(song.id) { vm.loadArtIfNeeded(song) }
            val cover = artCache[song.id]
            if (cover != null) {
                Image(bitmap = cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
    }
}