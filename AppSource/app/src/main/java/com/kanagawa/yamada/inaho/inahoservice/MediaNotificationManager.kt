package com.kanagawa.yamada.inaho.inahoservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.kanagawa.yamada.inaho.MainActivity
import com.kanagawa.yamada.inaho.R
import com.kanagawa.yamada.inaho.Song

class MediaNotificationManager(private val service: PlayerService) {

    companion object {
        const val CHANNEL_ID = "inaho_player"
        const val NOTIF_ID = 1
        
        const val ACTION_PREV = "com.kanagawa.yamada.inaho.PREV"
        const val ACTION_PLAY = "com.kanagawa.yamada.inaho.PLAY"
        const val ACTION_NEXT = "com.kanagawa.yamada.inaho.NEXT"
        const val ACTION_STOP = "com.kanagawa.yamada.inaho.STOP"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = service.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        state: PlayerState, 
        sessionToken: MediaSessionCompat.Token
    ): Notification {
        val song = state.currentSong
        
        val contentIntent = Intent(service, MainActivity::class.java)
        val pendingContentIntent = PendingIntent.getActivity(
            service, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseAction = NotificationCompat.Action(
            playPauseIcon, "Play/Pause",
            PendingIntent.getService(
                service, 1, 
                Intent(service, PlayerService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous",
            PendingIntent.getService(
                service, 2, 
                Intent(service, PlayerService::class.java).apply { action = ACTION_PREV },
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            PendingIntent.getService(
                service, 3, 
                Intent(service, PlayerService::class.java).apply { action = ACTION_NEXT },
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop",
            PendingIntent.getService(
                service, 4,
                Intent(service, PlayerService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(song?.title ?: "No Song")
            .setContentText(song?.artist ?: "")
            .setContentIntent(pendingContentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isPlaying)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(sessionToken)
            )

        song?.let {
            val bitmap = getAlbumArtBitmap(service, it)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }

        return builder.build()
    }

    fun updateNotification(state: PlayerState, sessionToken: MediaSessionCompat.Token) {
        val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, buildNotification(state, sessionToken))
    }

    private fun getAlbumArtBitmap(context: Context, song: Song): Bitmap? {
        return try {
            if (song.isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(song.trackUri, android.util.Size(800, 800), null)
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, song.trackUri)
                    val frame = retriever.getFrameAtTime(-1)
                    retriever.release()
                    frame
                }
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, song.trackUri)
                val art = retriever.embeddedPicture
                retriever.release()
                if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
