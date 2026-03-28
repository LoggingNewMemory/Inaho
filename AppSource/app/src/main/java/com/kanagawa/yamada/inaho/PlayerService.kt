package com.kanagawa.yamada.inaho

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// ==========================================
// PLAYER STATE
// ==========================================
data class PlayerState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val nextSong: Song?
        get() = if (currentIndex + 1 < queue.size) queue[currentIndex + 1] else null
    val hasPrev: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex + 1 < queue.size
}

// ==========================================
// PLAYER SERVICE
// ==========================================
class PlayerService : Service() {

    companion object {
        private const val CHANNEL_ID = "inaho_player"
        private const val NOTIF_ID = 1

        // Actions for notification buttons
        const val ACTION_PLAY_PAUSE = "com.kanagawa.yamada.inaho.PLAY_PAUSE"
        const val ACTION_NEXT = "com.kanagawa.yamada.inaho.NEXT"
        const val ACTION_PREV = "com.kanagawa.yamada.inaho.PREV"
        const val ACTION_STOP = "com.kanagawa.yamada.inaho.STOP"

        // Singleton state — survives configuration changes
        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()
    }

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    private val binder = PlayerBinder()
    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> skipNext()
            ACTION_PREV -> skipPrev()
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ---- Public API ----

    fun playSong(song: Song, queue: List<Song>, index: Int) {
        val newState = PlayerState(
            currentSong = song,
            queue = queue,
            currentIndex = index,
            isPlaying = false,
            positionMs = 0L,
            durationMs = song.durationMs
        )
        _playerState.value = newState
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
        updateNotification()
    }

    fun skipNext() {
        val state = _playerState.value
        if (!state.hasNext) return
        val nextIndex = state.currentIndex + 1
        val nextSong = state.queue[nextIndex]
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
        // If more than 3s in, restart current; else go previous
        val position = mp?.currentPosition ?: 0
        if (position > 3000 || !state.hasPrev) {
            mp?.seekTo(0)
            _playerState.value = state.copy(positionMs = 0L)
            return
        }
        val prevIndex = state.currentIndex - 1
        val prevSong = state.queue[prevIndex]
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
    }

    // ---- Private helpers ----

    private fun prepareAndPlay(uri: Uri) {
        mediaPlayer?.apply {
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
                    updateNotification()
                    startForeground(NOTIF_ID, buildNotification())
                }
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                    if (_playerState.value.hasNext) skipNext() else updateNotification()
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
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song?.title ?: "Inaho")
            .setContentText(song?.artist ?: "")
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_media_previous, "Previous",
                actionIntent(ACTION_PREV, 1)
            )
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Pause" else "Play",
                actionIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(
                android.R.drawable.ic_media_next, "Next",
                actionIntent(ACTION_NEXT, 3)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
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
        mediaPlayer?.release()
        mediaPlayer = null
    }
}