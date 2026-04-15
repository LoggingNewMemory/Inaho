/*
Copyright (C) 2026 Kanagawa Yamada 
This program is free software: you can redistribute it and/or modify it under the terms of 
the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. 

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details. 
You should have received a copy of the GNU General Public License along with this program. 

If not, see https://www.gnu.org/licenses/.
*/

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

    private fun loadFavorites(): List<Long> {
        val raw = prefs.getString("favorites_ordered", null)
        if (raw != null) {
            // Load from the ordered list format
            return raw.split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
        }
        // Migrate from old Set format
        val oldSet = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        val migrated = oldSet.mapNotNull { it.toLongOrNull() }
        if (migrated.isNotEmpty()) {
            prefs.edit()
                .putString("favorites_ordered", migrated.joinToString(","))
                .remove("favorites")
                .apply()
        }
        return migrated
    }

    fun toggleFavorite(songId: Long) {
        val current = _favoritesFlow.value.toMutableList()
        if (current.contains(songId)) current.remove(songId) else current.add(songId)
        prefs.edit().putString("favorites_ordered", current.joinToString(",")).apply()
        _favoritesFlow.value = current
    }

    fun reorderFavorites(newOrder: List<Long>) {
        prefs.edit().putString("favorites_ordered", newOrder.joinToString(",")).apply()
        _favoritesFlow.value = newOrder
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

    fun renamePlaylist(playlistId: Long, newName: String) {
        prefs.edit().putString("playlist_${playlistId}_name", newName).apply()
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

    fun reorderPlaylistSongs(playlistId: Long, newOrder: List<Long>) {
        prefs.edit().putString("playlist_${playlistId}_songs", newOrder.joinToString(",")).apply()
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