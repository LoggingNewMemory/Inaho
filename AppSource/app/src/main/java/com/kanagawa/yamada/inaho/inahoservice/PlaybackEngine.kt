package com.kanagawa.yamada.inaho.inahoservice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import com.kanagawa.yamada.inaho.Song
import com.kanagawa.yamada.inaho.YamadaAudioEngine
import kotlinx.coroutines.*

class PlaybackEngine(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val onSongCompletion: (Boolean) -> Unit,
    private val onStateUpdate: (PlayerState.() -> PlayerState) -> Unit
) {
    var mediaPlayer: MediaPlayer? = null
    var bgMediaPlayer: MediaPlayer? = null
    
    var currentSurface: Surface? = null
    var currentBgSurface: Surface? = null
    
    val eqEngine = YamadaAudioEngine(context).apply {
        onVolumeMultiplierChanged = { mult ->
            if (mediaPlayer != null && !fadingOutPlayers.contains(mediaPlayer)) {
                try { mediaPlayer?.setVolume(mult, mult) } catch (_: Exception) {}
            }
        }
    }
    
    private val fadingOutPlayers = mutableSetOf<MediaPlayer>()
    private var crossfadeJob: Job? = null
    
    private var playGeneration = 0
    private var isMainPrepared = false
    private var isBgPrepared = false
    var isMainPreparing = false
    private var isBgPreparing = false
    
    private var currentPlaybackSpeed = 1.0f
    private var currentPlaybackPitch = 1.0f

    fun setVideoSurface(surface: Surface?) {
        currentSurface = surface
        try { mediaPlayer?.setSurface(surface) } catch (_: Exception) {}
    }

    fun setBgVideoSurface(surface: Surface?) {
        currentBgSurface = surface
        try { bgMediaPlayer?.setSurface(surface) } catch (_: Exception) {}
    }
    
    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        currentPlaybackSpeed = speed
        currentPlaybackPitch = pitch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try { mediaPlayer?.let { mp -> mp.playbackParams = mp.playbackParams.setSpeed(speed).setPitch(pitch) } } catch (_: Exception) {}
            try { bgMediaPlayer?.let { bg -> bg.playbackParams = bg.playbackParams.setSpeed(speed).setPitch(pitch) } } catch (_: Exception) {}
        }
    }

    fun togglePlayPause(isPlaying: Boolean) {
        try {
            if (isPlaying) {
                mediaPlayer?.pause()
                bgMediaPlayer?.pause()
                onStateUpdate { copy(isPlaying = false) }
            } else {
                mediaPlayer?.start()
                bgMediaPlayer?.start()
                onStateUpdate { copy(isPlaying = true) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mediaPlayer?.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
                bgMediaPlayer?.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
            } else {
                mediaPlayer?.seekTo(positionMs.toInt())
                bgMediaPlayer?.seekTo(positionMs.toInt())
            }
            onStateUpdate { copy(positionMs = positionMs) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopPlayback() {
        serviceScope.coroutineContext.cancelChildren()
        fadingOutPlayers.forEach { safelyDestroyPlayer(it) }
        fadingOutPlayers.clear()
        
        eqEngine.release()

        safelyDestroyPlayer(bgMediaPlayer)
        bgMediaPlayer = null

        safelyDestroyPlayer(mediaPlayer)
        mediaPlayer = null
    }
    
    fun prepareAndPlay(song: Song, isCrossfading: Boolean = false, currentState: PlayerState) {
        val prefs = context.getSharedPreferences("inaho_settings", Context.MODE_PRIVATE)
        val crossfadeSec = try { prefs.getFloat("crossfade_duration", 0f) } catch (e: Exception) { prefs.getInt("crossfade_duration", 0).toFloat() }
        val doCrossfade = isCrossfading || (crossfadeSec > 0 && mediaPlayer?.isPlaying == true)
        
        if (isCrossfading) {
            crossfadeJob?.cancel()
        }

        eqEngine.release()
        val uri = song.trackUri

        val generation = ++playGeneration
        isMainPrepared = false
        isBgPrepared = false

        isMainPreparing = true
        isBgPreparing = false

        if (doCrossfade) {
            fadeOutAndRelease(mediaPlayer, crossfadeSec)
        } else {
            safelyDestroyPlayer(mediaPlayer)
        }
        mediaPlayer = null

        mediaPlayer = MediaPlayer().apply {
            if (doCrossfade) setVolume(0f, 0f) else setVolume(eqEngine.currentVolumeMultiplier, eqEngine.currentVolumeMultiplier)
            try {
                val attrBuilder = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                setAudioAttributes(attrBuilder.build())

                if (song.isVideo && currentSurface?.isValid == true) {
                    setSurface(currentSurface)
                }

                eqEngine.analyzeAndSetTrack(song.path)
                setDataSource(context, uri)
                attachMainListeners(this, song, generation, doCrossfade, crossfadeSec, currentState)
                prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
                isMainPreparing = false
                onStateUpdate { copy(isPlaying = false) }
            }
        }
    }

    private fun attachMainListeners(mp: MediaPlayer, song: Song, generation: Int, doCrossfade: Boolean, crossfadeSec: Float, currentState: PlayerState) {
        mp.setOnVideoSizeChangedListener { _, width, height ->
            if (width > 0 && height > 0) {
                onStateUpdate { copy(videoWidth = width, videoHeight = height) }
            }
        }

        mp.setOnPreparedListener { 
            if (generation != playGeneration) return@setOnPreparedListener
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (generation == playGeneration) {
                    eqEngine.attach(mp)
                }
            }
            onStateUpdate { copy(audioSessionId = mp.audioSessionId) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (currentPlaybackSpeed != 1.0f || currentPlaybackPitch != 1.0f)) {
                try { mp.playbackParams = mp.playbackParams.setSpeed(currentPlaybackSpeed).setPitch(currentPlaybackPitch) } catch (_: Exception) {}
            }

            isMainPreparing = false
            isMainPrepared = true

            if (song.isVideo && currentSurface?.isValid == true) {
                try { mp.setSurface(currentSurface) } catch(_: Exception) {}
            }

            if (song.isVideo) {
                prepareBgPlayer(song, generation, currentState)
            } else {
                isBgPrepared = true
                checkAndStartBoth(generation)
                if (doCrossfade) fadeIn(mp, crossfadeSec)
                startCrossfadePoller(currentState)
            }
        }

        mp.setOnCompletionListener {
            onSongCompletion(false)
        }

        mp.setOnErrorListener { _, _, _ ->
            if (generation == playGeneration) {
                isMainPreparing = false
                isMainPrepared = true
                onStateUpdate { copy(isPlaying = false) }
            }
            true
        }
    }

    private fun prepareBgPlayer(song: Song, generation: Int, currentState: PlayerState) {
        if (generation != playGeneration) return
        isBgPreparing = true

        bgMediaPlayer = MediaPlayer().apply {
            try {
                val attrBuilder = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                setAudioAttributes(attrBuilder.build())

                if (currentBgSurface?.isValid == true) {
                    setSurface(currentBgSurface)
                }

                setVolume(0f, 0f)
                setDataSource(context, song.trackUri)

                setOnPreparedListener { mp ->
                    if (generation != playGeneration) return@setOnPreparedListener
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (currentPlaybackSpeed != 1.0f || currentPlaybackPitch != 1.0f)) {
                        try { mp.playbackParams = mp.playbackParams.setSpeed(currentPlaybackSpeed).setPitch(currentPlaybackPitch) } catch (_: Exception) {}
                    }
                    isBgPreparing = false
                    isBgPrepared = true
                    checkAndStartBoth(generation)
                }
                setOnErrorListener { _, _, _ ->
                    if (generation == playGeneration) {
                        isBgPreparing = false
                        isBgPrepared = true
                        checkAndStartBoth(generation)
                    }
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
                isBgPreparing = false
                isBgPrepared = true
                checkAndStartBoth(generation)
            }
        }
    }

    private fun checkAndStartBoth(generation: Int) {
        if (generation != playGeneration) return
        val mp = mediaPlayer ?: return

        if (isMainPrepared && (bgMediaPlayer == null || isBgPrepared)) {
            try {
                bgMediaPlayer?.start()
                mp.start()
                onStateUpdate { copy(
                    isPlaying  = true,
                    durationMs = mp.duration.toLong(),
                    videoWidth = mp.videoWidth,
                    videoHeight = mp.videoHeight
                ) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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

    private fun fadeOutAndRelease(mp: MediaPlayer?, durationSec: Float) {
        if (mp == null || !mp.isPlaying) {
            safelyDestroyPlayer(mp)
            return
        }
        
        try { mp.setOnCompletionListener(null) } catch (_: Exception) {}
        try { mp.setOnErrorListener(null) } catch (_: Exception) {}
        
        fadingOutPlayers.add(mp)
        serviceScope.launch {
            val steps = 20
            val interval = (durationSec * 1000 / steps).toLong()
            for (i in steps downTo 1) {
                if (!isActive) break
                val vol = (i.toFloat() / steps) * eqEngine.currentVolumeMultiplier
                try { mp.setVolume(vol, vol) } catch (_: Exception) {}
                delay(interval)
            }
            safelyDestroyPlayer(mp)
            fadingOutPlayers.remove(mp)
        }
    }

    private fun fadeIn(mp: MediaPlayer, durationSec: Float) {
        serviceScope.launch {
            val steps = 20
            val interval = (durationSec * 1000 / steps).toLong()
            for (i in 1..steps) {
                if (!isActive) break
                val vol = (i.toFloat() / steps) * eqEngine.currentVolumeMultiplier
                try { mp.setVolume(vol, vol) } catch (_: Exception) {}
                delay(interval)
            }
        }
    }

    private fun startCrossfadePoller(currentState: PlayerState) {
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            while (isActive) {
                delay(500)
                val prefs = context.getSharedPreferences("inaho_settings", Context.MODE_PRIVATE)
                val crossfadeSec = try { prefs.getFloat("crossfade_duration", 0f) } catch (e: Exception) { prefs.getInt("crossfade_duration", 0).toFloat() }
                if (crossfadeSec > 0 && mediaPlayer?.isPlaying == true ) {
                    val duration = mediaPlayer?.duration ?: 0
                    val position = mediaPlayer?.currentPosition ?: 0
                    if (duration > 0 && (duration - position) <= (crossfadeSec * 1000).toInt() + 500) {
                        onSongCompletion(true)
                        break
                    }
                }
            }
        }
    }
}
