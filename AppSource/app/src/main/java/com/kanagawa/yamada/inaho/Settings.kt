package com.kanagawa.yamada.inaho

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// ==========================================
// 1. SETTINGS MODELS & MANAGER
// ==========================================
enum class SortOption(val displayName: String) {
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST_ASC("Artist (A-Z)"),
    DATE_ADDED_DESC("Recently Added"),
    DURATION_ASC("Shortest First"),
    DURATION_DESC("Longest First")
}

data class AppSettings(
    val userName: String,
    val sortOption: SortOption,
    val onlyMusicFolder: Boolean,
    val amoledBlack: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("inaho_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(
        AppSettings(
            userName = prefs.getString("user_name", "") ?: "",
            sortOption = SortOption.valueOf(
                prefs.getString("sort_option", SortOption.TITLE_ASC.name) ?: SortOption.TITLE_ASC.name
            ),
            onlyMusicFolder = prefs.getBoolean("only_music_folder", true),
            amoledBlack = prefs.getBoolean("amoled_black", false)
        )
    )
    val settingsFlow = _settingsFlow.asStateFlow()

    fun updateUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(userName = name)
    }

    fun updateSortOption(option: SortOption) {
        prefs.edit().putString("sort_option", option.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(sortOption = option)
    }

    fun updateOnlyMusicFolder(only: Boolean) {
        prefs.edit().putBoolean("only_music_folder", only).apply()
        _settingsFlow.value = _settingsFlow.value.copy(onlyMusicFolder = only)
    }

    fun updateAmoledBlack(enabled: Boolean) {
        prefs.edit().putBoolean("amoled_black", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(amoledBlack = enabled)
    }
}

// ==========================================
// 2. PLAYLIST MANAGER
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

// ==========================================
// 3. SETTINGS UI
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onNavigateBack: () -> Unit
) {
    val settings by settingsManager.settingsFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (settings.amoledBlack) Color.Black else Color(0xFF120E0E))
            .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.offset(x = (-8).dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFB8355B)
                )
            }
            Text(
                text = "Settings",
                color = Color(0xFFB8355B),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = (-8).dp)
            )
        }

        Text(
            text = "LIBRARY",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        SettingsToggleRow(
            icon = Icons.Default.List,
            title = "Music Folder Only",
            subtitle = "Only show files in /Music folder",
            checked = settings.onlyMusicFolder,
            onToggle = { settingsManager.updateOnlyMusicFolder(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "APPEARANCE",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        SettingsToggleRow(
            icon = Icons.Default.Nightlight,
            title = "AMOLED Black",
            subtitle = "Pure black background to save battery",
            checked = settings.amoledBlack,
            onToggle = { settingsManager.updateAmoledBlack(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "SORT ORDER",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1010))
        ) {
            SortOption.values().forEachIndexed { index, option ->
                val isSelected = settings.sortOption == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { settingsManager.updateSortOption(option) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.displayName,
                        color = if (isSelected) Color(0xFFB8355B) else Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB8355B))
                        )
                    }
                }
                if (index < SortOption.values().size - 1) {
                    HorizontalDivider(
                        color = Color(0xFF2C2020),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.White)) { append("THE ") }
                withStyle(style = SpanStyle(color = Color(0xFFB8355B))) { append("DEVELOPERS") }
            },
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)
        )

        DeveloperProfile(
            role = "Developer",
            roleColor = Color.White,
            avatarResId = R.drawable.ic_yamada,
            name = "Kanagawa Yamada",
            description = "VTuber / VTeacher of Indonesia. Founder and Leader of Kanagawa Lab Community",
            socials = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SocialLink(iconResId = R.drawable.github, text = "GitHub", url = "https://github.com/LoggingNewMemory")
                    SocialLink(iconResId = R.drawable.youtube, text = "YouTube", url = "https://www.youtube.com/@KanagawaYamada")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        DeveloperProfile(
            role = "Inspired By",
            roleColor = Color(0xFFB8355B),
            avatarResId = R.drawable.ic_inaho,
            name = "Ochinai Inaho",
            description = "Japanese VTuber under the agency of Goraku",
            socials = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SocialLink(iconResId = R.drawable.x, text = "X", url = "https://x.com/inaho_vt")
                    SocialLink(iconResId = R.drawable.youtube, text = "YouTube", url = "https://www.youtube.com/@%E8%90%BD%E4%B9%83%E3%81%84%E3%81%AA%E3%81%BB")
                }
            }
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1414)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFB8355B), modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF888888), fontSize = 13.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFB8355B),
                uncheckedThumbColor = Color.LightGray, uncheckedTrackColor = Color(0xFF2C2C2C)
            )
        )
    }
}

@Composable
private fun DeveloperProfile(
    role: String, roleColor: Color = Color.White, avatarResId: Int, name: String, description: String, socials: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(text = role, color = roleColor, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1010)).padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = avatarResId), contentDescription = name,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(text = name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, color = Color(0xFFCCCCCC), fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                socials()
            }
        }
    }
}

@Composable
private fun SocialLink(iconResId: Int, text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { uriHandler.openUri(url) }.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Icon(painter = painterResource(id = iconResId), contentDescription = text, tint = Color(0xFFB8355B), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = Color(0xFFB8355B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}