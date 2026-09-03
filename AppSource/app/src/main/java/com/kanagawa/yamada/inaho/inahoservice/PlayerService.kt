package com.kanagawa.yamada.inaho.inahoservice

import androidx.media.MediaBrowserServiceCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.view.Surface
import com.kanagawa.yamada.inaho.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val videoHeight: Int = 0,
    val audioSessionId: Int? = null,
    val hasRepeatedOnce: Boolean = false
) {
    val nextSong: Song?
        get() = if (currentIndex + 1 < activeQueue.size) activeQueue[currentIndex + 1]
        else null

    val hasPrev: Boolean
        get() = currentIndex > 0 || repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.ONE

    val hasNext: Boolean
        get() = repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.ONE || currentIndex + 1 < activeQueue.size
}

// ==========================================
// PLAYER SERVICE
// ==========================================
class PlayerService : MediaBrowserServiceCompat() {

    private val binder = PlayerBinder()

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    companion object {
        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var playbackEngine: PlaybackEngine
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var notificationManager: MediaNotificationManager

    private var positionJob: Job? = null
    private var isForeground = false

    private lateinit var audioManager: AudioManager
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (_playerState.value.isPlaying) togglePlayPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_playerState.value.isPlaying) togglePlayPause()
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (_playerState.value.isPlaying) togglePlayPause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        playbackEngine = PlaybackEngine(
            context = this,
            serviceScope = serviceScope,
            onSongCompletion = { isCrossfading -> skipNext(isAutoCompletion = true, isCrossfading = isCrossfading) },
            onStateUpdate = { _playerState.value = _playerState.value.it() }
        )

        mediaSessionManager = MediaSessionManager(this)
        sessionToken = mediaSessionManager.mediaSession.sessionToken
        notificationManager = MediaNotificationManager(this)

        startPositionPoller()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MediaNotificationManager.ACTION_PLAY -> togglePlayPause()
            MediaNotificationManager.ACTION_PREV -> skipPrev()
            MediaNotificationManager.ACTION_NEXT -> skipNext()
            MediaNotificationManager.ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == "android.media.browse.MediaBrowserService") {
            super.onBind(intent)
        } else {
            binder
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: android.os.Bundle?
    ): BrowserRoot? {
        return BrowserRoot("inaho_root_id", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<android.support.v4.media.MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId == "inaho_root_id") {
            result.detach()
            serviceScope.launch(Dispatchers.IO) {
                val mediaItems = mutableListOf<android.support.v4.media.MediaBrowserCompat.MediaItem>()
                var queue = _playerState.value.originalQueue
                
                if (queue.isEmpty()) {
                    // Fetch all songs if queue is empty
                    val songs = mutableListOf<Song>()
                    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION)
                    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                    
                    try {
                        contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val id = cursor.getLong(0)
                                val title = cursor.getString(1) ?: "Unknown"
                                val artist = cursor.getString(2) ?: "Unknown"
                                val path = cursor.getString(3) ?: ""
                                val duration = cursor.getLong(4)
                                val trackUri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                                val m = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(duration)
                                val s = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(duration) - java.util.concurrent.TimeUnit.MINUTES.toSeconds(m)
                                val formatted = String.format("%02d:%02d", m, s)
                                songs.add(Song(id, title, artist, duration, trackUri, formatted, false, path))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    queue = songs
                    
                    // Pre-fill the original queue in PlayerState so playFromMediaId works
                    _playerState.value = _playerState.value.copy(originalQueue = queue)
                }

                for (song in queue) {
                    val description = android.support.v4.media.MediaDescriptionCompat.Builder()
                        .setMediaId(song.id.toString())
                        .setTitle(song.title)
                        .setSubtitle(song.artist)
                        .build()
                    
                    mediaItems.add(
                        android.support.v4.media.MediaBrowserCompat.MediaItem(
                            description,
                            android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
                result.sendResult(mediaItems)
            }
        } else {
            result.sendResult(mutableListOf())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(noisyReceiver)
        serviceJob.cancel()
        playbackEngine.stopPlayback()
        mediaSessionManager.release()
    }

    // --- Media Control APIs ---
    
    val eqEngine get() = playbackEngine.eqEngine
    val currentSurface get() = playbackEngine.currentSurface
    val currentBgSurface get() = playbackEngine.currentBgSurface

    fun setVideoSurface(surface: Surface?) = playbackEngine.setVideoSurface(surface)

    fun setBgVideoSurface(surface: Surface?) = playbackEngine.setBgVideoSurface(surface)

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun playSong(song: Song, queue: List<Song>, index: Int) {
        val q: List<Song>
        val i: Int
        if (_playerState.value.isShuffled) {
            val remaining = queue.filter { it.id != song.id }.shuffled()
            q = listOf(song) + remaining
            i = 0
        } else {
            q = queue
            i = index
        }

        _playerState.value = _playerState.value.copy(
            originalQueue = queue,
            activeQueue = q,
            currentIndex = i,
            currentSong = song,
            positionMs = 0L,
            durationMs = song.durationMs,
                hasRepeatedOnce = false,
            videoWidth = 0,
            videoHeight = 0
        )

        if (requestAudioFocus()) {
            playbackEngine.prepareAndPlay(song, false, _playerState.value)
            startForegroundServiceNotification()
        }
    }

    fun togglePlayPause() {
        val state = _playerState.value
        if (state.currentSong == null) return

        if (!state.isPlaying && !requestAudioFocus()) return
        
        playbackEngine.togglePlayPause(state.isPlaying)
        updateSessionAndNotification()
    }

    fun toggleShuffle() {
        val state = _playerState.value
        val isNowShuffled = !state.isShuffled
        
        val newQueue: List<Song>
        val newIndex: Int
        
        if (isNowShuffled) {
            val currentSong = state.currentSong
            if (currentSong != null) {
                val remaining = state.activeQueue.filter { it.id != currentSong.id }.shuffled()
                newQueue = listOf(currentSong) + remaining
                newIndex = 0
            } else {
                newQueue = state.activeQueue.shuffled()
                newIndex = 0
            }
        } else {
            newQueue = state.originalQueue
            newIndex = state.currentSong?.let { newQueue.indexOf(it) } ?: 0
        }
        
        _playerState.value = state.copy(isShuffled = isNowShuffled, activeQueue = newQueue, currentIndex = newIndex)
    }

    fun toggleRepeat() {
        val state = _playerState.value
        val nextMode = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playerState.value = state.copy(repeatMode = nextMode)
    }

    fun skipNext(isAutoCompletion: Boolean = false, isCrossfading: Boolean = false) {
        val state = _playerState.value
        if (state.activeQueue.isEmpty()) return

        var nextSong = state.nextSong
        var newHasRepeatedOnce = false

        if (isAutoCompletion) {
            if (state.repeatMode == RepeatMode.ALL) {
                // User defined "Normal Repeat" as looping the current track indefinitely
                nextSong = state.currentSong
            } else if (state.repeatMode == RepeatMode.ONE) {
                // User defined "Repeat 1" as looping the track EXACTLY ONCE
                if (!state.hasRepeatedOnce) {
                    nextSong = state.currentSong
                    newHasRepeatedOnce = true
                } else {
                    newHasRepeatedOnce = false
                }
            }
        }
        
        if (nextSong != null) {
            val prefs = getSharedPreferences("inaho_settings", Context.MODE_PRIVATE)
            val crossfadeSec = try { prefs.getFloat("crossfade_duration", 0f) } catch (e: Exception) { prefs.getInt("crossfade_duration", 0).toFloat() }
            val actuallyCrossfading = isCrossfading && crossfadeSec > 0
            
            _playerState.value = state.copy(
                currentSong = nextSong,
                currentIndex = state.activeQueue.indexOf(nextSong),
                positionMs = 0L,
                durationMs = nextSong.durationMs,
                hasRepeatedOnce = newHasRepeatedOnce
            )
            
            if (requestAudioFocus()) {
                playbackEngine.prepareAndPlay(nextSong, actuallyCrossfading, _playerState.value)
                updateSessionAndNotification()
            }
        } else {
            stopPlayback()
        }
    }

    fun skipPrev() {
        val state = _playerState.value
        if (state.activeQueue.isEmpty()) return

        val currentPos = playbackEngine.mediaPlayer?.currentPosition ?: 0
        if (currentPos > 3000 || !state.hasPrev) {
            seekTo(0)
            return
        }

        val prevIndex = if (state.currentIndex > 0) state.currentIndex - 1 else state.activeQueue.size - 1
        val prevSong = state.activeQueue[prevIndex]

        _playerState.value = state.copy(
            currentSong = prevSong,
            currentIndex = prevIndex,
            positionMs = 0L,
            durationMs = prevSong.durationMs,
            hasRepeatedOnce = false
        )
        
        if (requestAudioFocus()) {
            playbackEngine.prepareAndPlay(prevSong, false, _playerState.value)
            updateSessionAndNotification()
        }
    }

    fun seekTo(positionMs: Long) {
        playbackEngine.seekTo(positionMs)
        updateSessionAndNotification()
    }

    fun playFromMediaId(id: Long) {
        val state = _playerState.value
        val queue = state.originalQueue
        val song = queue.find { it.id == id }
        if (song != null) {
            val index = queue.indexOf(song)
            playSong(song, queue, index)
        }
    }

    fun getCurrentPosition(): Long = playbackEngine.mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun jumpToQueueIndex(index: Int) {
        val state = _playerState.value
        if (index in state.activeQueue.indices) {
            val song = state.activeQueue[index]
            _playerState.value = state.copy(
                currentSong = song,
                currentIndex = index,
                positionMs = 0L,
                durationMs = song.durationMs,
                hasRepeatedOnce = false
            )
            if (requestAudioFocus()) {
                playbackEngine.prepareAndPlay(song, false, _playerState.value)
                updateSessionAndNotification()
            }
        }
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        playbackEngine.setPlaybackSpeedAndPitch(speed, pitch)
    }

    fun stopPlayback() {
        playbackEngine.stopPlayback()
        _playerState.value = PlayerState()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isForeground = false
        stopSelf()
    }

    private fun updateSessionAndNotification() {
        val state = _playerState.value
        val pos = getCurrentPosition()
        serviceScope.launch {
            mediaSessionManager.updateState(state, pos)
            if (isForeground) {
                notificationManager.updateNotification(state, mediaSessionManager.mediaSession.sessionToken)
            }
        }
    }

    private fun startForegroundServiceNotification() {
        mediaSessionManager.updateState(_playerState.value, getCurrentPosition())
        val notif = notificationManager.buildNotification(_playerState.value, mediaSessionManager.mediaSession.sessionToken)
        startForeground(MediaNotificationManager.NOTIF_ID, notif)
        isForeground = true
    }

    private fun startPositionPoller() {
        positionJob?.cancel()
        positionJob = serviceScope.launch {
            while (isActive) {
                if (_playerState.value.isPlaying && !playbackEngine.isMainPreparing) {
                    val pos = getCurrentPosition()
                    _playerState.value = _playerState.value.copy(positionMs = pos)
                }
                delay(1000)
            }
        }
    }
}