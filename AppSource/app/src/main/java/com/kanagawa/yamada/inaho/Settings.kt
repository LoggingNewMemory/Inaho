package com.kanagawa.yamada.inaho

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List // Replaced LibraryMusic to fix the build error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    DATE_ADDED_DESC("Recently Added")
}

data class AppSettings(
    val sortOption: SortOption,
    val onlyMusicFolder: Boolean
)

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("inaho_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(
        AppSettings(
            sortOption = SortOption.valueOf(
                prefs.getString("sort_option", SortOption.TITLE_ASC.name) ?: SortOption.TITLE_ASC.name
            ),
            onlyMusicFolder = prefs.getBoolean("only_music_folder", false)
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
}

// ==========================================
// 2. SETTINGS UI
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
            .background(Color(0xFF120E0E))
            .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp) // Reduced app gap
    ) {
        // Top Bar with Back Button (Made smaller to match Inaho top bar)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp) // Drastically reduced bottom padding
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.offset(x = (-8).dp) // Shifted left to remove visual gap
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
                fontSize = 28.sp, // Matched size with "Inaho" text
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = (-8).dp) // Shifted to sit closer to the back button
            )
        }

        // Setting Item: Set Music Directory
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { settingsManager.updateOnlyMusicFolder(!settings.onlyMusicFolder) }
                .padding(vertical = 12.dp, horizontal = 4.dp), // Slight inner padding so it doesn't touch screen edge
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [Music Logo] - Reduced Box and Icon size
            Box(
                modifier = Modifier
                    .size(40.dp) // Reduced from 56.dp
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.List, // Standard built-in icon to prevent crashes
                    contentDescription = "Music Folder",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp) // Reduced from 32.dp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set Music Directory",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Only Shows From Music Folder",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }

            // On / Off Switch
            Switch(
                checked = settings.onlyMusicFolder,
                onCheckedChange = { settingsManager.updateOnlyMusicFolder(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFB8355B),
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color(0xFF2C2C2C)
                )
            )
        }
    }
}