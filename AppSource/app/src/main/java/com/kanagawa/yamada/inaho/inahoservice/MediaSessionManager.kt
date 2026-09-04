package com.kanagawa.yamada.inaho.inahoservice

import android.content.ComponentName
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.kanagawa.yamada.inaho.EqPreset
import com.kanagawa.yamada.inaho.R

class MediaSessionManager(
    private val service: PlayerService
) {
    val mediaSession: MediaSessionCompat

    init {
        val receiver = ComponentName(service.packageName, androidx.media.session.MediaButtonReceiver::class.java.name)
        mediaSession = MediaSessionCompat(service, "InahoMediaSession", receiver, null).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { service.togglePlayPause() }
                override fun onPause() { service.togglePlayPause() }
                override fun onSkipToNext() { service.skipNext() }
                override fun onSkipToPrevious() { service.skipPrev() }
                override fun onSeekTo(pos: Long) { service.seekTo(pos) }
                override fun onStop() { service.stopPlayback() }
                override fun onPlayFromMediaId(mediaId: String?, extras: android.os.Bundle?) {
                    if (mediaId != null) {
                        if (mediaId.startsWith("preset_")) {
                            val presetName = mediaId.removePrefix("preset_")
                            val preset = EqPreset.values().find { it.name == presetName }
                            if (preset != null) {
                                service.eqEngine.setPreset(preset)
                                service.updateSessionAndNotification()
                            }
                            return
                        }
                        
                        val id = mediaId.toLongOrNull()
                        if (id != null) {
                            service.playFromMediaId(id)
                        }
                    }
                }
            })
            isActive = true
        }
    }

    private var lastMetadataSongId: Long? = null

    fun updateState(state: PlayerState, currentPosition: Long) {
        val playbackState = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or 
                      PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                      PlaybackStateCompat.ACTION_SEEK_TO

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(playbackState, currentPosition, 1.0f)
                .build()
        )

        val song = state.currentSong
        if (song != null && song.id != lastMetadataSongId) {
            lastMetadataSongId = song.id
            val builder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)
            
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(service, song.trackUri)
                val art = retriever.embeddedPicture
                retriever.release()
                if (art != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                }
            } catch (e: Exception) {}

            mediaSession.setMetadata(builder.build())
        }
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }
}
