package com.kanagawa.yamada.inaho.inahoservice

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
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
    val audioSessionId: Int? = null
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
            onGaplessNext = { skipNext(isAutoCompletion = true) },
            onStateUpdate = { _playerState.value = _playerState.value.it() }
        )

        mediaSessionManager = MediaSessionManager(this)
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

    override fun onBind(intent: Intent?): IBinder = binder

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
        val q = if (_playerState.value.isShuffled) queue.shuffled() else queue
        val i = if (_playerState.value.isShuffled) q.indexOf(song) else index

        _playerState.value = _playerState.value.copy(
            originalQueue = queue,
            activeQueue = q,
            currentIndex = i,
            currentSong = song,
            positionMs = 0L,
            durationMs = song.durationMs,
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
        val newQueue = if (isNowShuffled) state.activeQueue.shuffled() else state.originalQueue
        val newIndex = state.currentSong?.let { newQueue.indexOf(it) } ?: 0
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

        if (state.repeatMode == RepeatMode.ONE && isAutoCompletion && !isCrossfading) {
            seekTo(0)
            if (!state.isPlaying) togglePlayPause()
            return
        }

        val nextSong = state.nextSong
        if (nextSong != null) {
            val prefs = getSharedPreferences("inaho_settings", Context.MODE_PRIVATE)
            val isGapless = prefs.getBoolean("gapless_playback", false)
            
            // If it's autocompletion and we have a next player ready via gapless
            if (isAutoCompletion && !isCrossfading && isGapless && playbackEngine.nextMediaPlayer != null) {
                playbackEngine.handleGaplessNext(nextSong, state)
                updateSessionAndNotification()
                return
            }

            _playerState.value = state.copy(
                currentSong = nextSong,
                currentIndex = state.activeQueue.indexOf(nextSong),
                positionMs = 0L,
                durationMs = nextSong.durationMs
            )
            
            if (requestAudioFocus()) {
                playbackEngine.prepareAndPlay(nextSong, isCrossfading, _playerState.value)
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
            durationMs = prevSong.durationMs
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

    fun getCurrentPosition(): Long = playbackEngine.mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun jumpToQueueIndex(index: Int) {
        val state = _playerState.value
        if (index in state.activeQueue.indices) {
            val song = state.activeQueue[index]
            _playerState.value = state.copy(
                currentSong = song,
                currentIndex = index,
                positionMs = 0L,
                durationMs = song.durationMs
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
        mediaSessionManager.updateState(_playerState.value, getCurrentPosition())
        if (isForeground) {
            notificationManager.updateNotification(_playerState.value, mediaSessionManager.mediaSession.sessionToken)
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