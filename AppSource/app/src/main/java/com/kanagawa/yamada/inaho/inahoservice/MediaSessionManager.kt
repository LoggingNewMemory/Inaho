package com.kanagawa.yamada.inaho.inahoservice

import android.content.ComponentName
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.kanagawa.yamada.inaho.EqPreset
import com.kanagawa.yamada.inaho.R
import kotlinx.coroutines.launch

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
                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                }
                override fun onPlayFromMediaId(mediaId: String?, extras: android.os.Bundle?) {
                    if (mediaId != null) {
                        if (mediaId == "action_toggle_replaygain") {
                            val isEnabled = service.eqEngine.replayGainEnabled.value
                            service.eqEngine.setReplayGainEnabled(!isEnabled)
                            service.updateSessionAndNotification()
                            service.notifyChildrenChanged(com.kanagawa.yamada.inaho.inahocar.AutoLibraryManager.CATEGORY_EQ)
                            return
                        }
                        if (mediaId.startsWith("preset_")) {
                            val presetName = mediaId.removePrefix("preset_")
                            val preset = EqPreset.values().find { it.name == presetName }
                            if (preset != null) {
                                service.eqEngine.setPreset(preset)
                                service.updateSessionAndNotification()
                                service.notifyChildrenChanged(com.kanagawa.yamada.inaho.inahocar.AutoLibraryManager.CATEGORY_EQ)
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
            
            // Set metadata immediately without art
            mediaSession.setMetadata(builder.build())

            // Load art asynchronously to prevent ANR on track change
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    var bitmap = com.kanagawa.yamada.inaho.loadBitmapFromDisk(service, song.id)
                    if (bitmap == null && !com.kanagawa.yamada.inaho.isArtResolved(service, song.id)) {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(service, song.trackUri)
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            bitmap = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                        } else if (song.isVideo) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                try {
                                    bitmap = service.contentResolver.loadThumbnail(song.trackUri, android.util.Size(512, 512), null)
                                } catch (e: Exception) {}
                            }
                            if (bitmap == null) {
                                val frame = retriever.getFrameAtTime(-1)
                                if (frame != null) {
                                    val maxSide = 512
                                    val scale = maxSide.toFloat() / maxOf(frame.width, frame.height)
                                    if (scale < 1.0f) {
                                        bitmap = android.graphics.Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                                        frame.recycle()
                                    } else {
                                        bitmap = frame
                                    }
                                }
                            }
                        }
                        retriever.release()
                    }
                    if (bitmap != null) {
                        // Ensure we haven't changed song while loading
                        if (lastMetadataSongId == song.id) {
                            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                            mediaSession.setMetadata(builder.build())
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }
}
