package com.kanagawa.yamada.inaho

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

// ==========================================
// DISK CACHE HELPERS
// ==========================================

private fun artCacheFile(context: Context, songId: Long): File {
    val dir = File(context.cacheDir, "art").also { it.mkdirs() }
    return File(dir, "$songId.png")
}

// A separate sentinel file marks that we've confirmed a song has NO embedded art.
// This prevents re-extracting on every launch for art-less songs.
private fun artAbsentFile(context: Context, songId: Long): File {
    val dir = File(context.cacheDir, "art").also { it.mkdirs() }
    return File(dir, "${songId}.noart")
}

internal fun loadBitmapFromDisk(context: Context, songId: Long): Bitmap? {
    val file = artCacheFile(context, songId)
    if (!file.exists()) return null
    return try { BitmapFactory.decodeFile(file.absolutePath) } catch (e: Exception) { null }
}

internal fun saveBitmapToDisk(context: Context, songId: Long, bitmap: Bitmap?) {
    if (bitmap == null) {
        // Write a sentinel so we know the song genuinely has no art (don't re-extract next time)
        try { artAbsentFile(context, songId).createNewFile() } catch (_: Exception) {}
        return
    }
    val file = artCacheFile(context, songId)
    try {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (_: Exception) { }
}

/**
 * Returns true only when we already have a definitive answer for this song:
 * either a bitmap is saved on disk, or we've confirmed the song has no art.
 */
internal fun isArtResolved(context: Context, songId: Long): Boolean =
    artCacheFile(context, songId).exists() || artAbsentFile(context, songId).exists()

// ==========================================
// BITMAP EXTRACTION
// ==========================================

internal fun extractAndDownsample(context: Context, uri: Uri, targetPx: Int): Bitmap? {
    val rawBytes: ByteArray = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val pic = retriever.embeddedPicture
        retriever.release()
        pic ?: return null
    } catch (e: Exception) { return null }

    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
        var sampleSize = 1
        val (w, h) = opts.outWidth to opts.outHeight
        while ((w / sampleSize) > targetPx * 2 && (h / sampleSize) > targetPx * 2) sampleSize *= 2
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return null
        val side = minOf(sampled.width, sampled.height)
        val xOffset = (sampled.width - side) / 2
        val yOffset = (sampled.height - side) / 2
        val cropped = if (xOffset == 0 && yOffset == 0) sampled else {
            val c = Bitmap.createBitmap(sampled, xOffset, yOffset, side, side)
            sampled.recycle()
            c
        }
        if (cropped.width == targetPx) cropped else {
            val scaled = Bitmap.createScaledBitmap(cropped, targetPx, targetPx, true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        }
    } catch (e: Exception) { null }
}

// ==========================================
// VIEWMODEL
// ==========================================

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val settingsManager = SettingsManager(application)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs = settingsManager.settingsFlow.flatMapLatest { settings ->
        Pager(
            config = PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false),
            pagingSourceFactory = { MusicPagingSource(application.applicationContext, settings) }
        ).flow
    }.cachedIn(viewModelScope)

    private val _artCache = MutableStateFlow<Map<Long, Bitmap?>>(emptyMap())
    val artCache = _artCache.asStateFlow()

    private val _loadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val loadedSongs = _loadedSongs.asStateFlow()

    // A mutex to serialize all writes to _artCache, preventing the
    // concurrent read-modify-write race that caused covers to disappear
    // when rapidly skipping tracks.
    private val artCacheMutex = Mutex()

    // Tracks in-flight loads so we never launch two coroutines for the
    // same song ID simultaneously (another source of the rapid-skip bug).
    private val inFlightIds = mutableSetOf<Long>()

    fun recordLoadedSongs(songs: List<Song>) {
        _loadedSongs.value = songs
    }

    fun loadArtIfNeeded(song: Song) {
        // Fast-path: already in memory cache — nothing to do.
        if (_artCache.value.containsKey(song.id)) return

        viewModelScope.launch(Dispatchers.IO) {
            // Double-checked locking under the mutex to prevent duplicate launches.
            artCacheMutex.withLock {
                if (_artCache.value.containsKey(song.id)) return@launch
                if (inFlightIds.contains(song.id)) return@launch
                inFlightIds.add(song.id)
            }

            val context: Context = getApplication()
            try {
                val bitmap: Bitmap? = if (isArtResolved(context, song.id)) {
                    // Disk hit: load the bitmap (or null for art-absent songs).
                    loadBitmapFromDisk(context, song.id)
                } else {
                    // Cache miss: extract from the audio file and persist.
                    val extracted = extractAndDownsample(context, song.trackUri, targetPx = 800)
                    saveBitmapToDisk(context, song.id, extracted)
                    extracted
                }

                // Atomic update — read the latest map inside the lock so no
                // concurrent write from another song's coroutine can be lost.
                artCacheMutex.withLock {
                    _artCache.value = _artCache.value + (song.id to bitmap)
                    inFlightIds.remove(song.id)
                }
            } catch (e: Exception) {
                artCacheMutex.withLock { inFlightIds.remove(song.id) }
            }
        }
    }
}
