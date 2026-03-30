package com.kanagawa.yamada.inaho

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    val sortOption: SortOption,
    val onlyMusicFolder: Boolean,
    val amoledBlack: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("inaho_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(
        AppSettings(
            sortOption = SortOption.valueOf(
                prefs.getString("sort_option", SortOption.TITLE_ASC.name) ?: SortOption.TITLE_ASC.name
            ),
            onlyMusicFolder = prefs.getBoolean("only_music_folder", false),
            amoledBlack = prefs.getBoolean("amoled_black", false)
        )
    )
    val settingsFlow = _settingsFlow.asStateFlow()

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
// 2. FAVORITES MANAGER
// ==========================================
class FavoritesManager(context: Context) {
    private val prefs = context.getSharedPreferences("inaho_favorites", Context.MODE_PRIVATE)

    private val _favoritesFlow = MutableStateFlow(loadFavorites())
    val favoritesFlow = _favoritesFlow.asStateFlow()

    private fun loadFavorites(): Set<Long> {
        val raw = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggle(songId: Long) {
        val current = _favoritesFlow.value.toMutableSet()
        if (current.contains(songId)) current.remove(songId) else current.add(songId)
        prefs.edit().putStringSet("favorites", current.map { it.toString() }.toSet()).apply()
        _favoritesFlow.value = current
    }

    fun isFavorite(songId: Long): Boolean = _favoritesFlow.value.contains(songId)
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
    ) {
        // Top Bar
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

        // Divider label
        Text(
            text = "LIBRARY",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Setting: Music Directory
        SettingsToggleRow(
            icon = Icons.Default.List,
            title = "Music Folder Only",
            subtitle = "Only show files in /Music folder",
            checked = settings.onlyMusicFolder,
            onToggle = { settingsManager.updateOnlyMusicFolder(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Divider label
        Text(
            text = "APPEARANCE",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Setting: AMOLED Black
        SettingsToggleRow(
            icon = Icons.Default.Nightlight,
            title = "AMOLED Black",
            subtitle = "Pure black background to save battery",
            checked = settings.amoledBlack,
            onToggle = { settingsManager.updateAmoledBlack(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Divider label
        Text(
            text = "SORT ORDER",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Sort options
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
                                .clip(androidx.compose.foundation.shape.CircleShape)
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
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1414)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFB8355B),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color(0xFF888888),
                fontSize = 13.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFB8355B),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0xFF2C2C2C)
            )
        )
    }
}
