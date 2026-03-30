package com.kanagawa.yamada.inaho

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// ==========================================
// PLAYER STATE
// ==========================================
enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val currentSong: Song? = null,
    val originalQueue: List<Song> = emptyList(),
    val activeQueue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val nextSong: Song?
        get() = if (currentIndex + 1 < activeQueue.size) activeQueue[currentIndex + 1]
        else if (repeatMode == RepeatMode.ALL && activeQueue.isNotEmpty()) activeQueue[0]
        else null

    val hasPrev: Boolean
        get() = currentIndex > 0 || repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.ONE

    val hasNext: Boolean
        get() = repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.ONE || currentIndex + 1 < activeQueue.size
}

// ==========================================
// PLAYER SERVICE
// ==========================================
class PlayerService : Service() {

    companion object {
        private const val CHANNEL_ID = "inaho_player"
        private const val NOTIF_ID = 1

        const val ACTION_PLAY_PAUSE = "com.kanagawa.yamada.inaho.PLAY_PAUSE"
        const val ACTION_NEXT = "com.kanagawa.yamada.inaho.NEXT"
        const val ACTION_PREV = "com.kanagawa.yamada.inaho.PREV"
        const val ACTION_STOP = "com.kanagawa.yamada.inaho.STOP"

        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()
    }

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    private val binder = PlayerBinder()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "Inaho_Media_Session").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (!_playerState.value.isPlaying) togglePlayPause()
                }

                override fun onPause() {
                    if (_playerState.value.isPlaying) togglePlayPause()
                }

                override fun onSkipToNext() {
                    skipNext(isAutoCompletion = false)
                }

                override fun onSkipToPrevious() {
                    skipPrev()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                    updateMediaSessionState() // Refresh the UI immediately
                    updateNotification()
                }

                override fun onStop() {
                    stopPlayback()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> skipNext(isAutoCompletion = false)
            ACTION_PREV -> skipPrev()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    // --- MediaSession Sync Methods ---

    private fun updateMediaSessionState() {
        val state = _playerState.value
        val song = state.currentSong ?: return

        // 1. Sync Playback State
        val playbackState = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val playbackSpeed = if (state.isPlaying) 1f else 0f

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(playbackState, position, playbackSpeed)

        mediaSession.setPlaybackState(stateBuilder.build())

        // 2. Sync Metadata (Cover, Title, Artist, Duration)
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)

        // Try to fetch album art for the left-side cover
        val bitmap = getAlbumArtBitmap(applicationContext, song.trackUri)
        if (bitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    // Helper function to extract album cover from the audio file
    private fun getAlbumArtBitmap(context: Context, uri: Uri): Bitmap? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
        } catch (e: Exception) {
            null
        } finally {
            retriever?.release()
        }
    }

    // --- Playback Controls ---

    fun playSong(song: Song, queue: List<Song>, index: Int) {
        val isShuffled = _playerState.value.isShuffled
        val activeQueue = if (isShuffled) {
            val shuffled = queue.shuffled().toMutableList()
            shuffled.remove(song)
            shuffled.add(0, song)
            shuffled
        } else {
            queue
        }
        val currentIndex = if (isShuffled) 0 else index

        _playerState.value = _playerState.value.copy(
            currentSong = song,
            originalQueue = queue,
            activeQueue = activeQueue,
            currentIndex = currentIndex,
            isPlaying = false,
            positionMs = 0L,
            durationMs = song.durationMs
        )
        prepareAndPlay(song.trackUri)
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
        } else {
            mp.start()
            _playerState.value = _playerState.value.copy(isPlaying = true)
        }
        updateMediaSessionState()
        updateNotification()
    }

    fun toggleShuffle() {
        val state = _playerState.value
        val newShuffle = !state.isShuffled
        if (newShuffle) {
            val current = state.currentSong
            val shuffled = state.originalQueue.shuffled().toMutableList()
            if (current != null) {
                shuffled.remove(current)
                shuffled.add(0, current)
            }
            _playerState.value = state.copy(
                isShuffled = true,
                activeQueue = shuffled,
                currentIndex = 0
            )
        } else {
            val current = state.currentSong
            val originalIdx = state.originalQueue.indexOf(current).takeIf { it >= 0 } ?: 0
            _playerState.value = state.copy(
                isShuffled = false,
                activeQueue = state.originalQueue,
                currentIndex = originalIdx
            )
        }
    }

    fun toggleRepeat() {
        val state = _playerState.value
        val newMode = if (state.repeatMode == RepeatMode.OFF) RepeatMode.ONE else RepeatMode.OFF
        _playerState.value = state.copy(repeatMode = newMode)
    }

    fun skipNext(isAutoCompletion: Boolean = false) {
        val state = _playerState.value
        if (state.activeQueue.isEmpty()) return

        val nextIndex: Int = when {
            state.repeatMode == RepeatMode.ONE -> state.currentIndex
            state.currentIndex + 1 < state.activeQueue.size -> state.currentIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> {
                if (isAutoCompletion) {
                    _playerState.value = state.copy(isPlaying = false, positionMs = 0)
                    updateMediaSessionState()
                    updateNotification()
                }
                return
            }
        }

        val nextSong = state.activeQueue[nextIndex]
        _playerState.value = state.copy(
            currentSong = nextSong,
            currentIndex = nextIndex,
            positionMs = 0L,
            durationMs = nextSong.durationMs
        )
        prepareAndPlay(nextSong.trackUri)
    }

    fun skipPrev() {
        val state = _playerState.value
        val mp = mediaPlayer
        val position = mp?.currentPosition ?: 0

        // If played more than 3 seconds, restart the current song
        if (position > 3000) {
            mp?.seekTo(0)
            _playerState.value = state.copy(positionMs = 0L)
            updateMediaSessionState()
            return
        }

        if (state.activeQueue.isEmpty()) return

        val prevIndex = when {
            state.currentIndex > 0 -> state.currentIndex - 1
            state.repeatMode == RepeatMode.ALL -> state.activeQueue.size - 1
            else -> {
                mp?.seekTo(0)
                _playerState.value = state.copy(positionMs = 0L)
                updateMediaSessionState()
                return
            }
        }

        val prevSong = state.activeQueue[prevIndex]
        _playerState.value = state.copy(
            currentSong = prevSong,
            currentIndex = prevIndex,
            positionMs = 0L,
            durationMs = prevSong.durationMs
        )
        prepareAndPlay(prevSong.trackUri)
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _playerState.value = _playerState.value.copy(positionMs = positionMs)
        updateMediaSessionState()
    }

    fun getCurrentPosition(): Long = mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        _playerState.value = PlayerState()

        // Remove the notification and stop the service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun prepareAndPlay(uri: Uri) {
        val oldPlayer = mediaPlayer
        mediaPlayer = null
        oldPlayer?.apply {
            setOnCompletionListener(null)
            setOnPreparedListener(null)
            setOnErrorListener(null)
            if (isPlaying) stop()
            reset()
            release()
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                setDataSource(applicationContext, uri)
                setOnPreparedListener { mp ->
                    mp.start()
                    _playerState.value = _playerState.value.copy(
                        isPlaying = true,
                        durationMs = mp.duration.toLong()
                    )
                    updateMediaSessionState()
                    startForeground(NOTIF_ID, buildNotification())
                }
                setOnCompletionListener {
                    skipNext(isAutoCompletion = true)
                }
                setOnErrorListener { _, _, _ ->
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                _playerState.value = _playerState.value.copy(isPlaying = false)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Inaho Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Music playback controls" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val state = _playerState.value
        val song = state.currentSong

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionIntent(action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                this, requestCode,
                Intent(this, PlayerService::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Uses your app's actual icon, fixing the weird play button on top
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song?.title ?: "Inaho")
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 1. Prev
            .addAction(
                android.R.drawable.ic_media_previous, "Previous",
                actionIntent(ACTION_PREV, 1)
            )
            // 2. Play/Pause
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Pause" else "Play",
                actionIntent(ACTION_PLAY_PAUSE, 2)
            )
            // 3. Next
            .addAction(
                android.R.drawable.ic_media_next, "Next",
                actionIntent(ACTION_NEXT, 3)
            )
            // 4. Kill/Stop (Now correctly handled by MediaSession)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "Close",
                actionIntent(ACTION_STOP, 4)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    // Shows Prev, Play, Next when notification is collapsed
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .setOngoing(state.isPlaying)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.apply {
            setOnCompletionListener(null)
            setOnPreparedListener(null)
            setOnErrorListener(null)
            release()
        }
        mediaPlayer = null
        mediaSession.release()
    }
}