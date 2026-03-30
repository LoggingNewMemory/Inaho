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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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

// ==========================================
// 1. MODEL
// ==========================================
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val trackUri: Uri,
    val formattedDuration: String
)

// ==========================================
// 2. PAGING SOURCE
// ==========================================
class MusicPagingSource(
    private val context: Context,
    private val settings: AppSettings
) : PagingSource<Int, Song>() {

    private val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )

    override fun getRefreshKey(state: PagingState<Int, Song>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val offset = page * pageSize
            val songs = ArrayList<Song>(pageSize)

            val sortOrder = when (settings.sortOption) {
                SortOption.TITLE_ASC -> "${MediaStore.Audio.Media.TITLE} ASC"
                SortOption.TITLE_DESC -> "${MediaStore.Audio.Media.TITLE} DESC"
                SortOption.ARTIST_ASC -> "${MediaStore.Audio.Media.ARTIST} ASC"
                SortOption.DATE_ADDED_DESC -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                SortOption.DURATION_ASC -> "${MediaStore.Audio.Media.DURATION} ASC"
                SortOption.DURATION_DESC -> "${MediaStore.Audio.Media.DURATION} DESC"
            }

            var selection =
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
            if (settings.onlyMusicFolder) {
                selection += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    " AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE '%Music/%'"
                else
                    " AND ${MediaStore.Audio.Media.DATA} LIKE '%/Music/%'"
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
                context.contentResolver.query(
                    collection, projection, selection, null,
                    "$sortOrder LIMIT $pageSize OFFSET $offset"
                )
            }

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val dur = c.getLong(durationCol)
                    val title = c.getString(titleCol) ?: "Unknown Title"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val trackUri =
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    songs.add(Song(id, title, artist, dur, trackUri, buildFormattedDuration(dur)))
                }
            }

            LoadResult.Page(
                songs,
                if (page == 0) null else page - 1,
                if (songs.size < pageSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private fun buildFormattedDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

// ==========================================
// 3. SERVICE CONNECTION HELPER
// ==========================================
@Composable
fun rememberPlayerService(): PlayerService? {
    val context = LocalContext.current
    var playerService by remember { mutableStateOf<PlayerService?>(null) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                playerService = (binder as PlayerService.PlayerBinder).getService()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                playerService = null
            }
        }
        val intent = Intent(context, PlayerService::class.java)
        context.startService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(connection) }
    }
    return playerService
}

// ==========================================
// 4. UI — MusicListScreen
// ==========================================
@Composable
fun MusicListScreen(
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val songs: LazyPagingItems<Song> = musicViewModel.songs.collectAsLazyPagingItems()
    val artCache by musicViewModel.artCache.collectAsState()
    val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()
    val favorites by musicViewModel.favoritesManager.favoritesFlow.collectAsState()
    val playerState by PlayerService.playerState.collectAsState()
    val playerService = rememberPlayerService()

    // Background color driven by AMOLED setting
    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val surfaceColor = if (settings.amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)

    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_AUDIO
                else
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        hasPermission = storageGranted
        if (storageGranted) songs.refresh()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    LaunchedEffect(songs.itemCount) {
        val list = (0 until songs.itemCount).mapNotNull { songs[it] }
        if (list.isNotEmpty()) musicViewModel.recordLoadedSongs(list)
    }

    // Build filtered list for search / favorites
    val allLoadedSongs = remember(songs.itemCount) {
        (0 until songs.itemCount).mapNotNull { songs[it] }
    }

    val filteredSongs = remember(searchQuery, allLoadedSongs, showFavoritesOnly, favorites) {
        val base = if (showFavoritesOnly) allLoadedSongs.filter { favorites.contains(it.id) }
        else null

        if (searchQuery.isBlank() && !showFavoritesOnly) null // show paged list normally
        else {
            val source = base ?: allLoadedSongs
            if (searchQuery.isBlank()) source
            else source.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp)
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(surfaceColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFB8355B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFFB8355B)),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search songs, artists…", color = Color(0xFF888888), fontSize = 16.sp)
                            }
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Cancel",
                    color = Color(0xFFB8355B),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        isSearchActive = false
                        searchQuery = ""
                    }
                )
            } else {
                // Title + song count
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inaho",
                        color = Color(0xFFB8355B),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (songs.itemCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${songs.itemCount}",
                            color = Color(0xFF555555),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(surfaceColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                // Search icon
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { isSearchActive = true }
                )

                Spacer(Modifier.width(20.dp))

                // 3-dot overflow menu
                Box {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { showOverflowMenu = true }
                    )
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(Color(0xFF2C2020))
                    ) {
                        // Favorites toggle
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (showFavoritesOnly) Color(0xFFB8355B) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = if (showFavoritesOnly) "Show All" else "Favorites",
                                    color = if (showFavoritesOnly) Color(0xFFB8355B) else Color.White
                                )
                            },
                            onClick = {
                                showFavoritesOnly = !showFavoritesOnly
                                showOverflowMenu = false
                            }
                        )
                        HorizontalDivider(color = Color(0xFF3C2828), thickness = 0.5.dp)
                        // Reload
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = if (hasPermission && songs.loadState.refresh !is LoadState.Loading)
                                        Color.White else Color.White.copy(alpha = 0.38f),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = "Reload",
                                    color = if (hasPermission && songs.loadState.refresh !is LoadState.Loading)
                                        Color.White else Color.White.copy(alpha = 0.38f)
                                )
                            },
                            enabled = hasPermission && songs.loadState.refresh !is LoadState.Loading,
                            onClick = {
                                songs.refresh()
                                showOverflowMenu = false
                            }
                        )
                        HorizontalDivider(color = Color(0xFF3C2828), thickness = 0.5.dp)
                        // Settings
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                Text(text = "Settings", color = Color.White)
                            },
                            onClick = {
                                showOverflowMenu = false
                                onNavigateToSettings()
                            }
                        )
                    }
                }
            }
        }

        // Favorites header label
        if (showFavoritesOnly) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFB8355B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Favorites${if (filteredSongs != null) " · ${filteredSongs.size}" else ""}",
                    color = Color(0xFFB8355B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasPermission ->
                    Text(
                        "Storage permission is required to find your music.",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )

                songs.loadState.refresh is LoadState.Loading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFB8355B))
                    }

                songs.loadState.refresh is LoadState.Error ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Failed to load music.", color = Color.White)
                            Button(onClick = { songs.refresh() }) { Text("Retry") }
                        }
                    }

                songs.itemCount == 0 ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No music files found.", color = Color.LightGray)
                    }

                // Filtered / Favorites / Search results mode
                filteredSongs != null -> {
                    if (filteredSongs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (showFavoritesOnly && searchQuery.isBlank()) {
                                    Icon(
                                        imageVector = Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = Color(0xFF555555),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No favorites yet",
                                        color = Color.LightGray,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "Tap the heart on a song to add it",
                                        color = Color(0xFF555555),
                                        fontSize = 13.sp
                                    )
                                } else {
                                    Text(
                                        "No results for \"$searchQuery\"",
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                bottom = if (playerState.currentSong != null) 88.dp else 8.dp,
                                start = 8.dp, end = 8.dp
                            )
                        ) {
                            items(filteredSongs.size, key = { filteredSongs[it].id }) { index ->
                                val song = filteredSongs[index]
                                LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                                SongListItem(
                                    song = song,
                                    coverBitmap = artCache[song.id],
                                    isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                                    isFavorite = favorites.contains(song.id),
                                    onFavoriteToggle = { musicViewModel.favoritesManager.toggle(song.id) },
                                    onClick = {
                                        playerService?.playSong(song, filteredSongs, index)
                                        onNavigateToPlayer()
                                    }
                                )
                            }
                        }
                    }
                }

                // Normal paged mode
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        bottom = if (playerState.currentSong != null) 88.dp else 8.dp,
                        start = 8.dp, end = 8.dp
                    )
                ) {
                    items(
                        count = songs.itemCount,
                        key = songs.itemKey { it.id },
                        contentType = { "song" }
                    ) { index ->
                        val song = songs[index]
                        if (song != null) {
                            LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                            SongListItem(
                                song = song,
                                coverBitmap = artCache[song.id],
                                isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                                isFavorite = favorites.contains(song.id),
                                onFavoriteToggle = { musicViewModel.favoritesManager.toggle(song.id) },
                                onClick = {
                                    val queue = (0 until songs.itemCount).mapNotNull { songs[it] }
                                    playerService?.playSong(song, queue, index)
                                    onNavigateToPlayer()
                                }
                            )
                        }
                    }

                    if (songs.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp), Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFB8355B))
                            }
                        }
                    }
                }
            }
        }

        if (playerState.currentSong != null) {
            MiniPlayerBar(
                playerState = playerState,
                coverBitmap = playerState.currentSong?.let { artCache[it.id] },
                onPlayPause = { playerService?.togglePlayPause() },
                onNext = { playerService?.skipNext() },
                onExpand = onNavigateToPlayer,
                surfaceColor = surfaceColor
            )
        }
    }
}

// ==========================================
// 5. UI — MiniPlayerBar (with progress line)
// ==========================================
@Composable
fun MiniPlayerBar(
    playerState: PlayerState,
    coverBitmap: Bitmap?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    surfaceColor: Color = Color(0xFF1E1414)
) {
    val song = playerState.currentSong ?: return
    val progress = if (playerState.durationMs > 0)
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpand() },
        color = surfaceColor,
        tonalElevation = 4.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0xFF2C2C2C))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(Color(0xFFB8355B))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2C2C2C))
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onNext, enabled = playerState.hasNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = if (playerState.hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. UI — SongListItem
// ==========================================
@Composable
fun SongListItem(
    song: Song,
    coverBitmap: Bitmap?,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = "Song Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2C2C2C))
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isPlaying) Color(0xFFB8355B) else Color.White,
                fontSize = 16.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                color = Color.LightGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(song.formattedDuration, color = Color(0xFF888888), fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        // Favorite heart
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (isFavorite) Color(0xFFB8355B) else Color(0xFF444444),
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onFavoriteToggle)
        )
    }
}