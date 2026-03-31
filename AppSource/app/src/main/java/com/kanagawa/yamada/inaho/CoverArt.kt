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
// LRU BITMAP CACHE
// ==========================================

/**
 * A thread-safe LRU map that evicts the least-recently-used entry once [maxSize] is exceeded.
 * Bitmaps are recycled on eviction to free native memory immediately.
 */
private class LruBitmapCache(private val maxSize: Int) :
    LinkedHashMap<Long, Bitmap?>(maxSize + 1, 0.75f, /* accessOrder = */ true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap?>?): Boolean {
        val shouldEvict = size > maxSize
        if (shouldEvict) {
            eldest?.value?.let { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        return shouldEvict
    }
}

// ==========================================
// VIEWMODEL
// ==========================================

/**
 * How many bitmaps to keep in the in-memory cache at most.
 *
 * 800 × 800 ARGB_8888 ≈ 2.5 MB each.
 * 50 entries ≈ 125 MB worst-case (in practice far less, since many songs share art or have none).
 * This is a safe ceiling even on 3 GB devices.
 */
private const val ART_CACHE_MAX = 50

/**
 * Window of songs to pre-load around the currently playing song.
 * Songs within [PRE_LOAD_RADIUS] positions of the current queue index will be loaded
 * proactively, so cover art is always ready before the user gets there.
 */
private const val PRE_LOAD_RADIUS = 15

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val settingsManager = SettingsManager(application)
    val favoritesManager = FavoritesManager(application)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs = settingsManager.settingsFlow.flatMapLatest { settings ->
        Pager(
            config = PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false),
            pagingSourceFactory = { MusicPagingSource(application.applicationContext, settings) }
        ).flow
    }.cachedIn(viewModelScope)

    // ── In-memory LRU cache (exposed as a plain Map snapshot for Compose) ──
    private val _artCache = MutableStateFlow<Map<Long, Bitmap?>>(emptyMap())
    val artCache = _artCache.asStateFlow()

    // The LRU store itself (only touched under artCacheMutex)
    private val lruStore = LruBitmapCache(ART_CACHE_MAX)

    private val _loadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val loadedSongs = _loadedSongs.asStateFlow()

    private val artCacheMutex = Mutex()
    private val inFlightIds = mutableSetOf<Long>()

    fun recordLoadedSongs(songs: List<Song>) {
        _loadedSongs.value = songs
    }

    // ── Called by list items for songs currently visible on screen ──
    fun loadArtIfNeeded(song: Song) {
        if (lruStore.containsKey(song.id)) return
        enqueueLoad(song)
    }

    /**
     * Pre-loads cover art for the [PRE_LOAD_RADIUS] songs before and after [currentIndex]
     * in the active queue. Called by the player whenever the current song changes so that
     * art is ready before those songs scroll into view or start playing.
     */
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
                // Double-check inside the lock
                if (lruStore.containsKey(song.id)) return@launch
                if (inFlightIds.contains(song.id)) return@launch
                inFlightIds.add(song.id)
            }

            val context: Context = getApplication()
            try {
                val bitmap: Bitmap? = if (isArtResolved(context, song.id)) {
                    loadBitmapFromDisk(context, song.id)
                } else {
                    val extracted = extractAndDownsample(context, song.trackUri, targetPx = 800)
                    saveBitmapToDisk(context, song.id, extracted)
                    extracted
                }

                artCacheMutex.withLock {
                    lruStore[song.id] = bitmap          // LRU put; may evict oldest entry
                    _artCache.value = lruStore.toMap()  // publish a snapshot to Compose
                    inFlightIds.remove(song.id)
                }
            } catch (e: Exception) {
                artCacheMutex.withLock { inFlightIds.remove(song.id) }
            }
        }
    }
}