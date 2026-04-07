package com.kanagawa.yamada.inaho

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContentUris
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.*
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ==========================================
// 1. MODEL & PAGING
// ==========================================
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val trackUri: Uri,
    val formattedDuration: String,
    val isVideo: Boolean = false
)

class MusicPagingSource(private val context: Context, private val settings: AppSettings) : PagingSource<Int, Song>() {
    private val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Files.getContentUri("external")
    }

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.TITLE,
        MediaStore.Files.FileColumns.ARTIST,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )

    override fun getRefreshKey(state: PagingState<Int, Song>): Int? = state.anchorPosition?.let { state.closestPageToPosition(it)?.prevKey?.plus(1) ?: state.closestPageToPosition(it)?.nextKey?.minus(1) }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val offset = page * pageSize
            val songs = ArrayList<Song>(pageSize)
            val sortOrder = when (settings.sortOption) {
                SortOption.TITLE_ASC -> "${MediaStore.Files.FileColumns.TITLE} ASC"
                SortOption.TITLE_DESC -> "${MediaStore.Files.FileColumns.TITLE} DESC"
                SortOption.ARTIST_ASC -> "${MediaStore.Files.FileColumns.ARTIST} ASC"
                SortOption.DATE_ADDED_DESC -> "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                SortOption.DURATION_ASC -> "${MediaStore.Files.FileColumns.DURATION} ASC"
                SortOption.DURATION_DESC -> "${MediaStore.Files.FileColumns.DURATION} DESC"
            }

            var selection = "(" +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO} OR " +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}" +
                    ") AND ${MediaStore.Files.FileColumns.DURATION} > 10000"

            if (settings.onlyMusicFolder) {
                selection += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    " AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE '%Music/%'"
                } else {
                    " AND ${MediaStore.Files.FileColumns.DATA} LIKE '%/Music/%'"
                }
            }

            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, pageSize)
                    putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                }
                context.contentResolver.query(collection, projection, queryArgs, null)
            } else {
                context.contentResolver.query(collection, projection, selection, null, "$sortOrder LIMIT $pageSize OFFSET $offset")
            }

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.ARTIST)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val mediaTypeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val dur = c.getLong(durationCol)
                    val isVideo = c.getInt(mediaTypeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                    val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val trackUri = ContentUris.withAppendedId(baseUri, id)

                    songs.add(Song(id, c.getString(titleCol) ?: "Unknown", c.getString(artistCol) ?: "Unknown", dur, trackUri, String.format("%02d:%02d", (dur / 1000) / 60, (dur / 1000) % 60), isVideo))
                }
            }
            LoadResult.Page(songs, if (page == 0) null else page - 1, if (songs.size < pageSize) null else page + 1)
        } catch (e: Exception) { LoadResult.Error(e) }
    }
}

@Composable
fun rememberPlayerService(): PlayerService? {
    val context = LocalContext.current
    var playerService by remember { mutableStateOf<PlayerService?>(null) }
    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { playerService = (binder as PlayerService.PlayerBinder).getService() }
            override fun onServiceDisconnected(name: ComponentName) { playerService = null }
        }
        val intent = Intent(context, PlayerService::class.java)
        context.startService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(connection) }
    }
    return playerService
}

// ==========================================
// UI — MusicListScreen (Search & List Only)
// ==========================================
@Composable
fun MusicListScreen(
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val songs: LazyPagingItems<Song> = musicViewModel.songs.collectAsLazyPagingItems()
    val artCache by musicViewModel.artCache.collectAsState()
    val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()
    val playerState by PlayerService.playerState.collectAsState()
    val playerService = rememberPlayerService()
    val fullLibrary by musicViewModel.loadedSongs.collectAsState()

    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val surfaceColor = if (settings.amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)

    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true || permissions[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        hasPermission = storageGranted
        if (storageGranted) songs.refresh()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)

        val neededPermissions = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (neededPermissions.isNotEmpty()) permissionLauncher.launch(neededPermissions.toTypedArray())
    }

    val filteredSongs = remember(searchQuery, fullLibrary) {
        if (searchQuery.isBlank()) null
        else fullLibrary.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .displayCutoutPadding()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp)
    ) {
        // --- Top Bar (Animated Search) ---
        AnimatedContent(targetState = isSearchActive, label = "SearchBarAnimation") { active ->
            if (active) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f).background(surfaceColor, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFFB8355B), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color(0xFFB8355B)), modifier = Modifier.weight(1f),
                            decorationBox = { inner -> if (searchQuery.isEmpty()) Text("Search songs, artists…", color = Color(0xFF888888), fontSize = 16.sp); inner() }
                        )
                        if (searchQuery.isNotEmpty()) Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp).clickable { searchQuery = "" })
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Cancel", color = Color(0xFFB8355B), fontSize = 14.sp, modifier = Modifier.clickable { isSearchActive = false; searchQuery = "" })
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(text = "All Songs", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (songs.itemCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(text = "${songs.itemCount}", color = Color(0xFF555555), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.background(surfaceColor, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(26.dp).clickable { isSearchActive = true })
                    Spacer(Modifier.width(16.dp))
                    Box {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(26.dp).clickable { showOverflowMenu = true })
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }, modifier = Modifier.background(Color(0xFF2C2020))) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                                text = { Text("Reload Library", color = Color.White) },
                                onClick = { songs.refresh(); showOverflowMenu = false }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            when {
                !hasPermission -> Text("Storage permission is required.", color = Color.White)
                songs.loadState.refresh is LoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFB8355B)) }
                songs.itemCount == 0 -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No music files found.", color = Color.LightGray) }
                filteredSongs != null -> {
                    if (filteredSongs.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No results for \"$searchQuery\"", color = Color.LightGray) }
                    else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = if (playerState.currentSong != null) 88.dp else 16.dp)) {
                            items(filteredSongs.size, key = { filteredSongs[it].id }) { index ->
                                val song = filteredSongs[index]
                                LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                                SongListItem(
                                    song = song, coverBitmap = artCache[song.id],
                                    isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                                    onClick = {
                                        playerService?.playSong(song, filteredSongs, index)
                                        musicViewModel.preloadQueueWindow(filteredSongs, index)
                                        onNavigateToPlayer()
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = if (playerState.currentSong != null) 88.dp else 16.dp)) {
                        items(count = songs.itemCount, key = songs.itemKey { it.id }) { index ->
                            val song = songs[index]
                            if (song != null) {
                                LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                                SongListItem(
                                    song = song, coverBitmap = artCache[song.id],
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
                }
            }
        }

        AnimatedVisibility(visible = playerState.currentSong != null, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()) {
            MiniPlayerBar(playerState = playerState, playerService = playerService, coverBitmap = playerState.currentSong?.let { artCache[it.id] }, onPlayPause = { playerService?.togglePlayPause() }, onNext = { playerService?.skipNext() }, onExpand = onNavigateToPlayer, surfaceColor = surfaceColor)
        }
    }
}

// Keep the Shared UI Components here for use across screens
@Composable
fun MiniPlayerBar(playerState: PlayerState, playerService: PlayerService?, coverBitmap: Bitmap?, onPlayPause: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit, surfaceColor: Color) {
    val song = playerState.currentSong ?: return
    var livePositionMs by remember(song.id) { mutableLongStateOf(playerState.positionMs) }
    LaunchedEffect(playerState.isPlaying, playerService, song.id) {
        if (playerService != null) {
            livePositionMs = playerService.getCurrentPosition()
            while (playerState.isPlaying) { delay(500); livePositionMs = playerService.getCurrentPosition() }
        }
    }
    val progress = if (playerState.durationMs > 0) (livePositionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Surface(modifier = Modifier.fillMaxWidth().clickable { onExpand() }, color = surfaceColor, tonalElevation = 4.dp) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF2C2C2C))) { Box(modifier = Modifier.fillMaxWidth(progress).height(2.dp).background(Color(0xFFB8355B))) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (coverBitmap != null) Image(bitmap = coverBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                else Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2C2C2C)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = song.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onPlayPause) {
                    AnimatedContent(targetState = playerState.isPlaying, label = "miniPlayPause") { isPlaying ->
                        Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                IconButton(onClick = onNext, enabled = playerState.hasNext) { Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = if (playerState.hasNext) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
fun SongListItem(song: Song, coverBitmap: Bitmap?, isPlaying: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        if (coverBitmap != null) Image(bitmap = coverBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)))
        else Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2C2C2C)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = song.title, color = if (isPlaying) Color(0xFFB8355B) else Color.White, fontSize = 16.sp, fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(4.dp))
        Text(text = song.formattedDuration, color = Color(0xFF888888), fontSize = 13.sp)
    }
}