package com.kanagawa.yamada.inaho

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// ==========================================
// PLAYLIST MANAGER
// ==========================================
class PlaylistManager(context: Context) {
    private val prefs = context.getSharedPreferences("inaho_playlists", Context.MODE_PRIVATE)

    data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

    private val _favoritesFlow = MutableStateFlow(loadFavorites())
    val favoritesFlow = _favoritesFlow.asStateFlow()

    private val _customPlaylistsFlow = MutableStateFlow(loadCustomPlaylists())
    val customPlaylistsFlow = _customPlaylistsFlow.asStateFlow()

    private fun loadFavorites(): Set<Long> {
        val raw = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleFavorite(songId: Long) {
        val current = _favoritesFlow.value.toMutableSet()
        if (current.contains(songId)) current.remove(songId) else current.add(songId)
        prefs.edit().putStringSet("favorites", current.map { it.toString() }.toSet()).apply()
        _favoritesFlow.value = current
    }

    private fun loadCustomPlaylists(): List<Playlist> {
        val ids = prefs.getStringSet("playlist_ids", emptySet()) ?: emptySet()
        return ids.mapNotNull { idStr ->
            val id = idStr.toLongOrNull() ?: return@mapNotNull null
            val name = prefs.getString("playlist_${id}_name", "Unknown") ?: "Unknown"
            val songStr = prefs.getString("playlist_${id}_songs", "") ?: ""
            val songs = songStr.split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
            Playlist(id, name, songs)
        }.sortedBy { it.name }
    }

    fun createPlaylist(name: String) {
        val id = System.currentTimeMillis()
        val currentIds = prefs.getStringSet("playlist_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentIds.add(id.toString())
        prefs.edit()
            .putStringSet("playlist_ids", currentIds)
            .putString("playlist_${id}_name", name)
            .putString("playlist_${id}_songs", "")
            .apply()
        _customPlaylistsFlow.value = loadCustomPlaylists()
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val playlist = _customPlaylistsFlow.value.find { it.id == playlistId } ?: return
        if (playlist.songIds.contains(songId)) return
        val newSongs = playlist.songIds + songId
        prefs.edit().putString("playlist_${playlistId}_songs", newSongs.joinToString(",")).apply()
        _customPlaylistsFlow.value = loadCustomPlaylists()
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        val playlist = _customPlaylistsFlow.value.find { it.id == playlistId } ?: return
        val newSongs = playlist.songIds - songId
        prefs.edit().putString("playlist_${playlistId}_songs", newSongs.joinToString(",")).apply()
        _customPlaylistsFlow.value = loadCustomPlaylists()
    }

    fun deletePlaylist(playlistId: Long) {
        val currentIds = prefs.getStringSet("playlist_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentIds.remove(playlistId.toString())
        prefs.edit()
            .putStringSet("playlist_ids", currentIds)
            .remove("playlist_${playlistId}_name")
            .remove("playlist_${playlistId}_songs")
            .apply()
        _customPlaylistsFlow.value = loadCustomPlaylists()
    }
}