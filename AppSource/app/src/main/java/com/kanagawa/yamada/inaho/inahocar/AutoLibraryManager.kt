package com.kanagawa.yamada.inaho.inahocar

import android.content.Context
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import com.kanagawa.yamada.inaho.Song
import com.kanagawa.yamada.inaho.PlaylistManager
import com.kanagawa.yamada.inaho.YamadaAudioEngine
import com.kanagawa.yamada.inaho.EqPreset

class AutoLibraryManager(private val context: Context) {

    companion object {
        const val ROOT_ID = "inaho_root_v2"
        const val CATEGORY_SONGS = "category_1_songs"
        const val CATEGORY_PLAYLISTS = "category_2_playlists"
        const val CATEGORY_EQ = "category_3_eq"
        const val PREFIX_PLAYLIST = "playlist_"
        const val PREFIX_EQ = "preset_"
    }
    
    

    // Helper to create a browsable category item
    private fun createBrowsableItem(id: String, title: String, subtitle: String): MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaItem(description, MediaItem.FLAG_BROWSABLE)
    }

    // Helper to create a playable song item
    private fun createPlayableItem(song: Song): MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artist)
            .build()
        return MediaItem(description, MediaItem.FLAG_PLAYABLE)
    }

    var lastBrowsedSongs: List<Song> = emptyList()

    fun getChildren(parentId: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        
        when {
            parentId == ROOT_ID -> {
                items.add(createBrowsableItem(CATEGORY_SONGS, "Songs", "Browse all local music"))
                items.add(createBrowsableItem(CATEGORY_PLAYLISTS, "Playlists", "Browse custom playlists"))
                items.add(createBrowsableItem(CATEGORY_EQ, "Yamada AE", "Select Audio Preset"))
            }
            parentId == CATEGORY_SONGS -> {
                val songs = fetchSongs(null, null)
                lastBrowsedSongs = songs
                songs.forEach { items.add(createPlayableItem(it)) }
            }
            parentId == CATEGORY_PLAYLISTS -> {
                val freshPlaylistManager = PlaylistManager(context)
                val favorites = freshPlaylistManager.favoritesFlow.value
                if (favorites.isNotEmpty()) {
                    items.add(createBrowsableItem("${PREFIX_PLAYLIST}favorites", "Favorites", "${favorites.size} songs"))
                }
                
                val playlists = freshPlaylistManager.customPlaylistsFlow.value
                playlists.forEach { pl ->
                    items.add(createBrowsableItem("$PREFIX_PLAYLIST${pl.id}", pl.name, "${pl.songIds.size} songs"))
                }
            }
            parentId.startsWith(PREFIX_PLAYLIST) -> {
                val plIdStr = parentId.removePrefix(PREFIX_PLAYLIST)
                val freshPlaylistManager = PlaylistManager(context)
                
                val songIds = if (plIdStr == "favorites") {
                    freshPlaylistManager.favoritesFlow.value
                } else {
                    val plId = plIdStr.toLongOrNull()
                    if (plId != null) {
                        freshPlaylistManager.customPlaylistsFlow.value.find { it.id == plId }?.songIds
                    } else null
                }
                
                if (songIds != null) {
                    val allSongs = fetchSongs(null, null)
                    val playlistSongs = songIds.mapNotNull { id -> allSongs.find { it.id == id } }
                    lastBrowsedSongs = playlistSongs
                    playlistSongs.forEach { items.add(createPlayableItem(it)) }
                }
            }
            parentId == CATEGORY_EQ -> {
                val eqPrefs = context.getSharedPreferences("inaho_eq", Context.MODE_PRIVATE)
                val rgEnabled = eqPrefs.getBoolean("replaygain_enabled", false)
                val currentPresetName = eqPrefs.getString("preset", EqPreset.OFF.name) ?: EqPreset.OFF.name
                
                val rgStateStr = if (rgEnabled) "On" else "Off"
                items.add(
                    MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId("action_toggle_replaygain")
                            .setTitle("Toggle ReplayGain ($rgStateStr)")
                            .setSubtitle("Normalize volume across tracks")
                            .build(),
                        MediaItem.FLAG_PLAYABLE
                    )
                )

                EqPreset.values().forEach { preset ->
                    val isActive = preset.name == currentPresetName
                    val titleSuffix = if (isActive) " (Active)" else ""
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("$PREFIX_EQ${preset.name}")
                                .setTitle("${preset.emoji} ${preset.displayName}$titleSuffix")
                                .setSubtitle(preset.description)
                                .build(),
                            MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
            }
        }
        
        return items
    }

    private fun fetchUniqueArtists(): List<String> {
        val artists = mutableSetOf<String>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.ARTIST)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        try {
            context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.ARTIST} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val artist = cursor.getString(0) ?: "Unknown Artist"
                    artists.add(artist)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return artists.toList().sorted()
    }

    private fun fetchUniqueAlbums(): List<String> {
        val albums = mutableSetOf<String>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        try {
            context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.ALBUM} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val album = cursor.getString(0) ?: "Unknown Album"
                    albums.add(album)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return albums.toList().sorted()
    }

    private val settingsManager = com.kanagawa.yamada.inaho.SettingsManager(context)

    fun fetchSongs(selectionExtra: String?, selectionArgs: Array<String>?): List<Song> {
        val songs = mutableListOf<Song>()
        val settings = settingsManager.settingsFlow.value
        
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID, 
            MediaStore.Files.FileColumns.TITLE, 
            MediaStore.Files.FileColumns.ARTIST, 
            MediaStore.Files.FileColumns.DATA, 
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        
        var selection = "(" +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO} OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}" +
                ") AND ${MediaStore.Files.FileColumns.DURATION} > 10000"

        if (settings.onlyMusicFolder) {
            selection += if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                " AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE '%Music/%'"
            } else {
                " AND ${MediaStore.Files.FileColumns.DATA} LIKE '%/Music/%'"
            }
        }
        
        if (selectionExtra != null) {
            selection += " AND $selectionExtra"
        }
        
        try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.ARTIST)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown"
                    val path = cursor.getString(dataCol) ?: ""
                    val duration = cursor.getLong(durationCol)
                    val isVideo = cursor.getInt(mediaTypeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    
                    val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val trackUri = android.content.ContentUris.withAppendedId(baseUri, id)
                    
                    val m = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(duration)
                    val s = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(duration) - java.util.concurrent.TimeUnit.MINUTES.toSeconds(m)
                    val formatted = String.format("%02d:%02d", m, s)
                    
                    songs.add(Song(id, title, artist, duration, trackUri, formatted, isVideo, path))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs
    }
}
