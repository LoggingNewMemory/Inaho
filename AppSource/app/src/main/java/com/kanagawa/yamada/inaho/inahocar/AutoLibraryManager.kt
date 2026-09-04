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
        const val ROOT_ID = "inaho_root_id"
        const val CATEGORY_SONGS = "category_songs"
        const val CATEGORY_PLAYLISTS = "category_playlists"
        const val CATEGORY_EQ = "category_eq"
        const val PREFIX_PLAYLIST = "playlist_"
        const val PREFIX_EQ = "preset_"
    }
    
    private val playlistManager = PlaylistManager(context)

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
                items.add(createBrowsableItem(CATEGORY_EQ, "Yamada EQ", "Select Audio Preset"))
            }
            parentId == CATEGORY_SONGS -> {
                val songs = fetchSongs(null, null)
                lastBrowsedSongs = songs
                songs.forEach { items.add(createPlayableItem(it)) }
            }
            parentId == CATEGORY_PLAYLISTS -> {
                val playlists = playlistManager.customPlaylistsFlow.value
                playlists.forEach { pl ->
                    items.add(createBrowsableItem("$PREFIX_PLAYLIST${pl.id}", pl.name, "${pl.songIds.size} songs"))
                }
            }
            parentId.startsWith(PREFIX_PLAYLIST) -> {
                val plId = parentId.removePrefix(PREFIX_PLAYLIST).toLongOrNull()
                if (plId != null) {
                    val pl = playlistManager.customPlaylistsFlow.value.find { it.id == plId }
                    if (pl != null) {
                        val allSongs = fetchSongs(null, null)
                        val playlistSongs = pl.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
                        lastBrowsedSongs = playlistSongs
                        playlistSongs.forEach { items.add(createPlayableItem(it)) }
                    }
                }
            }
            parentId == CATEGORY_EQ -> {
                EqPreset.values().forEach { preset ->
                    items.add(
                        MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("$PREFIX_EQ${preset.name}")
                                .setTitle("${preset.emoji} ${preset.displayName}")
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

    fun fetchSongs(selectionExtra: String?, selectionArgs: Array<String>?): List<Song> {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, 
            MediaStore.Audio.Media.TITLE, 
            MediaStore.Audio.Media.ARTIST, 
            MediaStore.Audio.Media.DATA, 
            MediaStore.Audio.Media.DURATION
        )
        
        var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        if (selectionExtra != null) {
            selection += " AND $selectionExtra"
        }
        
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
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
        return songs
    }
}
