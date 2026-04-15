/*
Copyright (C) 2026 Kanagawa Yamada 
This program is free software: you can redistribute it and/or modify it under the terms of 
the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. 

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details. 
You should have received a copy of the GNU General Public License along with this program. 

If not, see https://www.gnu.org/licenses/.
*/

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
        try { artAbsentFile(context, songId).createNewFile() } catch (_: Exception) {}
        return
    }
    val file = artCacheFile(context, songId)
    try {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (_: Exception) { }
}

internal fun isArtResolved(context: Context, songId: Long): Boolean =
    artCacheFile(context, songId).exists() || artAbsentFile(context, songId).exists()

// ==========================================
// BITMAP EXTRACTION (AUDIO)
// ==========================================

internal fun extractAndDownsample(context: Context, uri: Uri, targetPx: Int): Bitmap? {
    val rawBytes: ByteArray = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    } catch (e: Exception) { null } ?: return null

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
// BITMAP EXTRACTION (NATIVE VIDEO)
// ==========================================
internal fun loadVideoThumbnailNative(context: Context, uri: Uri, targetPx: Int): Bitmap? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Native Android Q+ Way
            context.contentResolver.loadThumbnail(uri, android.util.Size(targetPx, targetPx), null)
        } else {
            // Legacy Way
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(-1)
            retriever.release()
            frame
        }
    } catch (e: Exception) { null }
}

// ==========================================
// LRU BITMAP CACHE
// ==========================================

private class LruBitmapCache(private val maxSize: Int) :
    LinkedHashMap<Long, Bitmap?>(maxSize + 1, 0.75f, true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap?>?): Boolean {
        return size > maxSize
    }
}

// ==========================================
// VIEWMODEL
// ==========================================

private const val ART_CACHE_MAX = 50
private const val PRE_LOAD_RADIUS = 15

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val settingsManager = SettingsManager(application)
    val playlistManager = PlaylistManager(application)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs = settingsManager.settingsFlow.flatMapLatest { settings ->
        Pager(
            config = PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false),
            pagingSourceFactory = { MusicPagingSource(application.applicationContext, settings) }
        ).flow
    }.cachedIn(viewModelScope)

    private val _artCache = MutableStateFlow<Map<Long, Bitmap?>>(emptyMap())
    val artCache = _artCache.asStateFlow()

    private val lruStore = LruBitmapCache(ART_CACHE_MAX)

    private val _loadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val loadedSongs = _loadedSongs.asStateFlow()

    private val artCacheMutex = Mutex()
    private val inFlightIds = mutableSetOf<Long>()

    fun recordLoadedSongs(songs: List<Song>) {
        _loadedSongs.value = songs
    }

    fun loadArtIfNeeded(song: Song) {
        if (lruStore.containsKey(song.id)) return
        enqueueLoad(song)
    }

    fun preloadQueueWindow(queue: List<Song>, currentIndex: Int) {
        if (queue.isEmpty() || currentIndex < 0) return
        val start = (currentIndex - PRE_LOAD_RADIUS).coerceAtLeast(0)
        val end   = (currentIndex + PRE_LOAD_RADIUS).coerceAtMost(queue.size - 1)
        for (i in start..end) {
            val song = queue[i]
            if (!lruStore.containsKey(song.id)) {
                enqueueLoad(song)
            }
        }
    }

    private fun enqueueLoad(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            artCacheMutex.withLock {
                if (lruStore.containsKey(song.id)) return@launch
                if (inFlightIds.contains(song.id)) return@launch
                inFlightIds.add(song.id)
            }

            val context: Context = getApplication()
            try {
                val bitmap: Bitmap? = if (isArtResolved(context, song.id)) {
                    loadBitmapFromDisk(context, song.id)
                } else {
                    // Separate the logic cleanly based on the new flag
                    val extracted = if (song.isVideo) {
                        loadVideoThumbnailNative(context, song.trackUri, 800)
                    } else {
                        extractAndDownsample(context, song.trackUri, targetPx = 800)
                    }
                    saveBitmapToDisk(context, song.id, extracted)
                    extracted
                }

                artCacheMutex.withLock {
                    lruStore[song.id] = bitmap
                    _artCache.value = lruStore.toMap()
                    inFlightIds.remove(song.id)
                }
            } catch (e: Exception) {
                artCacheMutex.withLock { inFlightIds.remove(song.id) }
            }
        }
    }
}