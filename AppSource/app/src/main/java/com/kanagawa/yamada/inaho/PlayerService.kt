package com.kanagawa.yamada.inaho

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Surface
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
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
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
        const val ACTION_NEXT       = "com.kanagawa.yamada.inaho.NEXT"
        const val ACTION_PREV       = "com.kanagawa.yamada.inaho.PREV"
        const val ACTION_STOP       = "com.kanagawa.yamada.inaho.STOP"

        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()
    }

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    private val binder = PlayerBinder()

    // --- Dual Decoder ---
    private var mediaPlayer: MediaPlayer? = null
    private var bgMediaPlayer: MediaPlayer? = null

    private lateinit var mediaSession: MediaSessionCompat

    private var currentPlaybackSpeed: Float = 1.0f
    private var currentPlaybackPitch: Float = 1.0f

    lateinit var eqManager: YamadaEQManager
        private set

    // ── Video Surface Handling ─────────────────────────────────────────────────
    private var currentSurface: Surface? = null
    private var currentBgSurface: Surface? = null

    fun setVideoSurface(surface: Surface?) {
        if (currentSurface === surface) return
        currentSurface = surface
        try { mediaPlayer?.setSurface(surface) } catch (_: Exception) {}
    }

    fun setBgVideoSurface(surface: Surface?) {
        if (currentBgSurface === surface) return
        currentBgSurface = surface
        try { bgMediaPlayer?.setSurface(surface) } catch (_: Exception) {}
    }
    // ──────────────────────────────────────────────────────────────────────────

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (_playerState.value.isPlaying) togglePlayPause()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        eqManager = YamadaEQManager(applicationContext)

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyAudioReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyAudioReceiver, filter)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "Inaho_Media_Session").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()            { if (!_playerState.value.isPlaying) togglePlayPause() }
                override fun onPause()           { if (_playerState.value.isPlaying)  togglePlayPause() }
                override fun onSkipToNext()      { skipNext(isAutoCompletion = false) }
                override fun onSkipToPrevious()  { skipPrev() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
                override fun onStop()            { stopPlayback() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT       -> skipNext(isAutoCompletion = false)
            ACTION_PREV       -> skipPrev()
            ACTION_STOP       -> stopPlayback()
        }
        return START_STICKY
    }

    private fun updateMediaSessionState() {
        val state = _playerState.value
        val song = state.currentSong ?: return

        val playbackState = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position      = try { mediaPlayer?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }
        val playbackSpeed = if (state.isPlaying) currentPlaybackSpeed else 0f

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_STOP
                )
                .setState(playbackState, position, playbackSpeed)
                .build()
        )

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)

        getAlbumArtBitmap(applicationContext, song.trackUri)?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }
        mediaSession.setMetadata(metadataBuilder.build())
    }

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
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

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

        currentPlaybackSpeed = 1.0f
        currentPlaybackPitch = 1.0f

        _playerState.value = _playerState.value.copy(
            currentSong  = song,
            originalQueue = queue,
            activeQueue  = activeQueue,
            currentIndex = currentIndex,
            isPlaying    = false,
            positionMs   = 0L,
            durationMs   = song.durationMs,
            videoWidth   = 0,
            videoHeight  = 0
        )
        prepareAndPlay(song)
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                try { bgMediaPlayer?.pause() } catch (_: Exception) {}
                _playerState.value = _playerState.value.copy(isPlaying = false)
            } else {
                mp.start()
                try {
                    bgMediaPlayer?.seekTo(mp.currentPosition)
                    bgMediaPlayer?.start()
                } catch (_: Exception) {}
                _playerState.value = _playerState.value.copy(isPlaying = true)
            }
            updateMediaSessionState()
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleShuffle() {
        val state = _playerState.value
        val newShuffle = !state.isShuffled
        if (newShuffle) {
            val current = state.currentSong
            val shuffled = state.originalQueue.shuffled().toMutableList()
            if (current != null) { shuffled.remove(current); shuffled.add(0, current) }
            _playerState.value = state.copy(isShuffled = true, activeQueue = shuffled, currentIndex = 0)
        } else {
            val current   = state.currentSong
            val originalIdx = state.originalQueue.indexOf(current).takeIf { it >= 0 } ?: 0
            _playerState.value = state.copy(isShuffled = false, activeQueue = state.originalQueue, currentIndex = originalIdx)
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

        currentPlaybackSpeed = 1.0f
        currentPlaybackPitch = 1.0f

        val nextSong = state.activeQueue[nextIndex]
        _playerState.value = state.copy(
            currentSong  = nextSong,
            currentIndex = nextIndex,
            positionMs   = 0L,
            durationMs   = nextSong.durationMs,
            videoWidth   = 0,
            videoHeight  = 0
        )
        prepareAndPlay(nextSong)
    }

    fun skipPrev() {
        val state = _playerState.value
        val position = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

        if (position > 3000) {
            try { mediaPlayer?.seekTo(0) } catch (_: Exception) {}
            try { bgMediaPlayer?.seekTo(0) } catch (_: Exception) {}
            _playerState.value = state.copy(positionMs = 0L)
            updateMediaSessionState()
            return
        }

        if (state.activeQueue.isEmpty()) return

        val prevIndex = when {
            state.currentIndex > 0 -> state.currentIndex - 1
            state.repeatMode == RepeatMode.ALL -> state.activeQueue.size - 1
            else -> {
                try { mediaPlayer?.seekTo(0) } catch (_: Exception) {}
                try { bgMediaPlayer?.seekTo(0) } catch (_: Exception) {}
                _playerState.value = state.copy(positionMs = 0L)
                updateMediaSessionState()
                return
            }
        }

        currentPlaybackSpeed = 1.0f
        currentPlaybackPitch = 1.0f

        val prevSong = state.activeQueue[prevIndex]
        _playerState.value = state.copy(
            currentSong  = prevSong,
            currentIndex = prevIndex,
            positionMs   = 0L,
            durationMs   = prevSong.durationMs,
            videoWidth   = 0,
            videoHeight  = 0
        )
        prepareAndPlay(prevSong)
    }

    fun seekTo(positionMs: Long) {
        try { mediaPlayer?.seekTo(positionMs.toInt()) } catch (_: Exception) {}
        try { bgMediaPlayer?.seekTo(positionMs.toInt()) } catch (_: Exception) {}
        _playerState.value = _playerState.value.copy(positionMs = positionMs)
        updateMediaSessionState()
        updateNotification()
    }

    fun getCurrentPosition(): Long = try { mediaPlayer?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }

    fun jumpToQueueIndex(index: Int) {
        val state = _playerState.value
        if (index < 0 || index >= state.activeQueue.size) return
        val song = state.activeQueue[index]

        currentPlaybackSpeed = 1.0f
        currentPlaybackPitch = 1.0f

        _playerState.value = state.copy(
            currentSong  = song,
            currentIndex = index,
            positionMs   = 0L,
            durationMs   = song.durationMs,
            videoWidth   = 0,
            videoHeight  = 0
        )
        prepareAndPlay(song)
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        currentPlaybackSpeed = speed
        currentPlaybackPitch = pitch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try { mediaPlayer?.let { mp -> mp.playbackParams = mp.playbackParams.setSpeed(speed).setPitch(pitch) } } catch (_: Exception) {}
            try { bgMediaPlayer?.let { bg -> bg.playbackParams = bg.playbackParams.setSpeed(speed).setPitch(pitch) } } catch (_: Exception) {}
        }
    }

    // ── NATIVE HARDWARE DECODER CLEANUP ──────────────────────────────────────────
    private fun safelyDestroyPlayer(mp: MediaPlayer?) {
        if (mp == null) return
        try { mp.setOnPreparedListener(null) } catch (_: Exception) {}
        try { mp.setOnCompletionListener(null) } catch (_: Exception) {}
        try { mp.setOnErrorListener(null) } catch (_: Exception) {}
        try { mp.setOnVideoSizeChangedListener(null) } catch (_: Exception) {}
        try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
        try { mp.reset() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
    }
    // ──────────────────────────────────────────────────────────────────────────

    fun stopPlayback() {
        eqManager.release()

        safelyDestroyPlayer(bgMediaPlayer)
        bgMediaPlayer = null

        safelyDestroyPlayer(mediaPlayer)
        mediaPlayer = null

        _playerState.value = PlayerState()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun prepareAndPlay(song: Song) {
        eqManager.release()
        val uri = song.trackUri

        // FORCEFULLY AND SAFELY PURGE OLD PLAYERS TO PREVENT DECODER LEAKS
        safelyDestroyPlayer(bgMediaPlayer)
        bgMediaPlayer = null
        safelyDestroyPlayer(mediaPlayer)
        mediaPlayer = null

        // --- Prepare Background Player (Muted) ---
        if (song.isVideo) {
            bgMediaPlayer = MediaPlayer().apply {
                try {
                    setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
                    setSurface(currentBgSurface)
                    setVolume(0f, 0f) // Muted!
                    setDataSource(applicationContext, uri)
                    setOnPreparedListener { mp ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (currentPlaybackSpeed != 1.0f || currentPlaybackPitch != 1.0f)) {
                            try { mp.playbackParams = mp.playbackParams.setSpeed(currentPlaybackSpeed).setPitch(currentPlaybackPitch) } catch (_: Exception) {}
                        }
                        try { mp.start() } catch (_: Exception) {}
                    }
                    setOnErrorListener { _, _, _ -> true } // Ignore background player crash safely
                    prepareAsync()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // --- Prepare Main Player ---
        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
                setSurface(currentSurface)

                setOnVideoSizeChangedListener { _, width, height ->
                    if (width > 0 && height > 0) {
                        _playerState.value = _playerState.value.copy(
                            videoWidth = width,
                            videoHeight = height
                        )
                    }
                }

                setDataSource(applicationContext, uri)
                setOnPreparedListener { mp ->
                    eqManager.attach(mp.audioSessionId)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (currentPlaybackSpeed != 1.0f || currentPlaybackPitch != 1.0f)) {
                        try { mp.playbackParams = mp.playbackParams.setSpeed(currentPlaybackSpeed).setPitch(currentPlaybackPitch) } catch (_: Exception) {}
                    }

                    try {
                        mp.start()
                        _playerState.value = _playerState.value.copy(
                            isPlaying  = true,
                            durationMs = mp.duration.toLong(),
                            videoWidth = mp.videoWidth,
                            videoHeight = mp.videoHeight
                        )
                        updateMediaSessionState()
                        startForeground(NOTIF_ID, buildNotification())
                    } catch (_: Exception) {}
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
                e.printStackTrace()
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
        val song  = state.currentSong

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
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song?.title ?: "Inaho")
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", actionIntent(ACTION_PREV, 1))
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Pause" else "Play",
                actionIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", actionIntent(ACTION_NEXT, 3))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", actionIntent(ACTION_STOP, 4))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .setOngoing(state.isPlaying)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(noisyAudioReceiver) }
        eqManager.release()

        safelyDestroyPlayer(bgMediaPlayer)
        bgMediaPlayer = null

        safelyDestroyPlayer(mediaPlayer)
        mediaPlayer = null

        mediaSession.release()
    }
}