package com.kanagawa.yamada.inaho

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.*
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

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
// 2. PAGING SOURCE (For Performance)
// ==========================================
class MusicPagingSource(private val context: Context) : PagingSource<Int, Song>() {
    private val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )

    private val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"

    override fun getRefreshKey(state: PagingState<Int, Song>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1) ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val offset = page * pageSize
            val songs = ArrayList<Song>(pageSize)
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

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
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val dur = c.getLong(durationCol)
                    val title = c.getString(titleCol) ?: "Unknown Title"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    songs.add(Song(id, title, artist, dur, trackUri, buildFormattedDuration(dur)))
                }
            }
            LoadResult.Page(songs, if (page == 0) null else page - 1, if (songs.size < pageSize) null else page + 1)
        } catch (e: Exception) { LoadResult.Error(e) }
    }

    private fun buildFormattedDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

// ==========================================
// 3. VIEWMODEL
// ==========================================
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val songs = Pager(
        config = PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false),
        pagingSourceFactory = { MusicPagingSource(application.applicationContext) }
    ).flow.cachedIn(ioScope)
}

// ==========================================
// 4. UI SCREENS
// ==========================================
@Composable
fun MusicListScreen(musicViewModel: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val songs: LazyPagingItems<Song> = musicViewModel.songs.collectAsLazyPagingItems()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (isGranted) songs.refresh()
    }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(permissionToRequest) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF120E0E)).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Inaho", color = Color(0xFFB8355B), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { songs.refresh() }, enabled = hasPermission && songs.loadState.refresh !is LoadState.Loading) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
            }
            IconButton(onClick = { /* TODO */ }) { Icon(Icons.AutoMirrored.Filled.List, "Sort", tint = Color.White) }
            IconButton(onClick = { /* TODO */ }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when {
            !hasPermission -> Text("Storage permission is required to find your music.", color = Color.White)
            songs.loadState.refresh is LoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFB8355B)) }
            songs.loadState.refresh is LoadState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load music.", color = Color.White)
                    Button(onClick = { songs.refresh() }) { Text("Retry") }
                }
            }
            songs.itemCount == 0 -> Text("No music files found.", color = Color.White)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(count = songs.itemCount, key = songs.itemKey { it.id }, contentType = { "song" }) { index ->
                    songs[index]?.let { SongListItem(it) } ?: SongListItemPlaceholder()
                }
                if (songs.loadState.append is LoadState.Loading) {
                    item { Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFB8355B)) } }
                }
            }
        }
    }
}

@Composable
fun SongListItem(song: Song) {
    val context = LocalContext.current
    // Using your proven extraction method!
    var coverBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(song.trackUri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, song.trackUri)
                coverBytes = retriever.embeddedPicture
            } catch (e: Exception) {
                // Ignore if corrupt or unreadable
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = coverBytes,
            contentDescription = "Song Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2C2C2C)) // Dark grey placeholder
        )

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.LightGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.width(8.dp))
        Text(song.formattedDuration, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun SongListItemPlaceholder() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2C2C2C)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2C2C2C)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2C2C2C)))
        }
    }
}