package com.kanagawa.yamada.inaho

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.*
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    private val selection =
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"

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
// 3. DISK CACHE HELPERS
// ==========================================

/**
 * Returns the on-disk PNG file for a given song ID.
 * Stored under:  <app cache dir>/art/<songId>.png
 */
private fun artCacheFile(context: Context, songId: Long): File {
    val dir = File(context.cacheDir, "art").also { it.mkdirs() }
    return File(dir, "$songId.png")
}

/**
 * Loads a Bitmap from disk if the cache file exists, otherwise returns null.
 * Runs on the caller's thread — always dispatch to IO before calling.
 */
private fun loadBitmapFromDisk(context: Context, songId: Long): Bitmap? {
    val file = artCacheFile(context, songId)
    if (!file.exists()) return null
    return try {
        BitmapFactory.decodeFile(file.absolutePath)
    } catch (e: Exception) {
        null
    }
}

/**
 * Saves [bitmap] to disk as a lossless PNG so it survives across app restarts.
 * A sentinel zero-byte file is written when there is no cover art, so we never
 * re-extract from MediaMetadataRetriever for songs that simply have no art.
 */
private fun saveBitmapToDisk(context: Context, songId: Long, bitmap: Bitmap?) {
    val file = artCacheFile(context, songId)
    try {
        if (bitmap == null) {
            // Write a sentinel so we know "no art" without re-scanning
            file.createNewFile()
        } else {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    } catch (_: Exception) { /* Disk full or other IO error — silent fail, will retry next launch */ }
}

/**
 * Returns true if we already have a cache entry on disk for [songId]
 * (whether that entry is a real bitmap or the "no art" sentinel).
 */
private fun isCachedOnDisk(context: Context, songId: Long): Boolean =
    artCacheFile(context, songId).exists()

// ==========================================
// 4. VIEWMODEL — with persistent disk art cache
// ==========================================
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val songs = Pager(
        config = PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false),
        pagingSourceFactory = { MusicPagingSource(application.applicationContext) }
    ).flow.cachedIn(viewModelScope)

    // In-memory cache for the current session:
    //   Key absent        → not yet loaded into memory (may already be on disk)
    //   Key present, null → confirmed no cover art
    //   Key present, Bitmap → ready to display
    private val _artCache = MutableStateFlow<Map<Long, Bitmap?>>(emptyMap())
    val artCache = _artCache.asStateFlow()

    fun loadArtIfNeeded(song: Song) {
        // Already in memory — nothing to do
        if (_artCache.value.containsKey(song.id)) return

        viewModelScope.launch(Dispatchers.IO) {
            val context: Context = getApplication()

            // 1. Try the disk cache first (instant — no MediaMetadataRetriever needed)
            if (isCachedOnDisk(context, song.id)) {
                val bitmap = loadBitmapFromDisk(context, song.id)  // null == sentinel "no art"
                _artCache.value = _artCache.value + (song.id to bitmap)
                return@launch
            }

            // 2. Cache miss — extract from the audio file, then persist to disk
            val bitmap = extractAndDownsample(context, song.trackUri, targetPx = 150)
            saveBitmapToDisk(context, song.id, bitmap)
            _artCache.value = _artCache.value + (song.id to bitmap)
        }
    }
}

/**
 * Robustly extracts cover art from ANY audio format (MP3, FLAC, AAC, OGG…)
 * and downsamples it to [targetPx]×[targetPx] before returning.
 *
 * Two-pass BitmapFactory decode ensures we never OOM on hi-res FLAC artwork:
 *   Pass 1 – inJustDecodeBounds=true  → reads dimensions only, no allocation
 *   Pass 2 – inSampleSize computed    → allocates only the downsampled bitmap
 */
private fun extractAndDownsample(context: Context, uri: Uri, targetPx: Int): Bitmap? {
    val rawBytes: ByteArray = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val pic = retriever.embeddedPicture
        retriever.release()
        pic ?: return null   // No embedded art → cache null immediately
    } catch (e: Exception) {
        return null
    }

    return try {
        // Pass 1 — measure dimensions without allocating the bitmap
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)

        // Compute the largest power-of-2 sample size that keeps the image ≥ targetPx
        var sampleSize = 1
        val (w, h) = opts.outWidth to opts.outHeight
        while ((w / sampleSize) > targetPx * 2 && (h / sampleSize) > targetPx * 2) {
            sampleSize *= 2
        }

        // Pass 2 — decode at reduced resolution
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return null

        // Center-crop to square, then scale to exactly targetPx × targetPx
        val side = minOf(sampled.width, sampled.height)
        val xOffset = (sampled.width - side) / 2
        val yOffset = (sampled.height - side) / 2

        val cropped = if (xOffset == 0 && yOffset == 0) {
            sampled  // already square — skip the crop allocation
        } else {
            val c = Bitmap.createBitmap(sampled, xOffset, yOffset, side, side)
            sampled.recycle()
            c
        }

        if (cropped.width == targetPx) {
            cropped  // already the right size after crop
        } else {
            val scaled = Bitmap.createScaledBitmap(cropped, targetPx, targetPx, true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        }
    } catch (e: Exception) {
        null   // Corrupt / unreadable art → treat as no cover
    }
}

// ==========================================
// 5. UI — MusicListScreen
// ==========================================
@Composable
fun MusicListScreen(musicViewModel: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val songs: LazyPagingItems<Song> = musicViewModel.songs.collectAsLazyPagingItems()
    val artCache by musicViewModel.artCache.collectAsState()

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

    val permissionToRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) songs.refresh()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(permissionToRequest)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF120E0E))
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)
    ) {
        // Top bar
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Inaho", color = Color(0xFFB8355B), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { songs.refresh() },
                enabled = hasPermission && songs.loadState.refresh !is LoadState.Loading
            ) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
            }
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.AutoMirrored.Filled.List, "Sort", tint = Color.White)
            }
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when {
            !hasPermission ->
                Text("Storage permission is required to find your music.", color = Color.White)

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
                Text("No music files found.", color = Color.White)

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(
                    count = songs.itemCount,
                    key = songs.itemKey { it.id },
                    contentType = { "song" }
                ) { index ->
                    val song = songs[index]
                    if (song != null) {
                        // Kick off art loading via ViewModel (no-op if already cached)
                        LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }

                        SongListItem(
                            song = song,
                            // Key present in map → use its value (Bitmap or null)
                            // Key absent → still loading, show placeholder
                            coverBitmap = artCache[song.id]
                        )
                    } else {
                        SongListItemPlaceholder()
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
}

// ==========================================
// 6. UI — SongListItem
// ==========================================
@Composable
fun SongListItem(song: Song, coverBitmap: Bitmap?) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverBitmap)
                .build(),
            contentDescription = "Song Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2C2C2C))
        )

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = Color.White,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                color = Color.LightGray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))
        Text(song.formattedDuration, color = Color.White, fontSize = 14.sp)
    }
}

// ==========================================
// 7. UI — Placeholder
// ==========================================
@Composable
private fun SongListItemPlaceholder() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2C2C2C))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2C2C2C))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2C2C2C))
            )
        }
    }
}