/*
Inaho Music Player - A Music Player that inspired with Ochinai Inaho
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
import android.widget.Toast
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
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import java.io.File

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

enum class AppTheme { INAHO, YAMADA }

data class AppSettings(
    val userName: String,
    val sortOption: SortOption,
    val onlyMusicFolder: Boolean,
    val amoledBlack: Boolean = false,
    val amvModeAlwaysOn: Boolean = false,
    val amvBlurAmount: Float = 40f,
    val amvDimAmount: Float = 0.6f,
    val showCoverBackground: Boolean = true,
    val enableBackgroundBlur: Boolean = true,
    val theme: AppTheme = AppTheme.INAHO
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
            amoledBlack = prefs.getBoolean("amoled_black", false),
            amvModeAlwaysOn = prefs.getBoolean("amv_mode_always_on", false),
            amvBlurAmount = prefs.getFloat("amv_blur_amount", 40f),
            amvDimAmount = prefs.getFloat("amv_dim_amount", 0.6f),
            showCoverBackground = prefs.getBoolean("show_cover_background", true),
            enableBackgroundBlur = prefs.getBoolean("enable_background_blur", true),
            theme = AppTheme.valueOf(prefs.getString("theme", AppTheme.INAHO.name) ?: AppTheme.INAHO.name)
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

    fun updateAmvModeAlwaysOn(enabled: Boolean) {
        prefs.edit().putBoolean("amv_mode_always_on", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(amvModeAlwaysOn = enabled)
    }

    fun updateAmvBlurAmount(amount: Float) {
        prefs.edit().putFloat("amv_blur_amount", amount).apply()
        _settingsFlow.value = _settingsFlow.value.copy(amvBlurAmount = amount)
    }

    fun updateAmvDimAmount(amount: Float) {
        prefs.edit().putFloat("amv_dim_amount", amount).apply()
        _settingsFlow.value = _settingsFlow.value.copy(amvDimAmount = amount)
    }

    fun updateShowCoverBackground(enabled: Boolean) {
        prefs.edit().putBoolean("show_cover_background", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(showCoverBackground = enabled)
    }

    fun updateEnableBackgroundBlur(enabled: Boolean) {
        prefs.edit().putBoolean("enable_background_blur", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(enableBackgroundBlur = enabled)
    }

    fun updateTheme(theme: AppTheme) {
        prefs.edit().putString("theme", theme.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(theme = theme)
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
    val context = LocalContext.current

    val accentColor = if (settings.theme == AppTheme.YAMADA) Color(0xFF9E9EDB) else Color(0xFFB8355B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (settings.amoledBlack) Color.Black else Color(0xFF120E0E))
            .displayCutoutPadding()
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
                    tint = accentColor
                )
            }
            Text(
                text = "Settings",
                color = accentColor,
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
            accentColor = accentColor,
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
            accentColor = accentColor,
            onToggle = { settingsManager.updateAmoledBlack(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleRow(
            icon = Icons.Default.Image,
            title = "Cover Background",
            subtitle = "Use song cover as a full-screen background",
            checked = settings.showCoverBackground,
            accentColor = accentColor,
            onToggle = { settingsManager.updateShowCoverBackground(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "THEME",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeSelectorChip(
                title = "Inaho",
                color = Color(0xFFB8355B),
                isSelected = settings.theme == AppTheme.INAHO,
                onClick = { settingsManager.updateTheme(AppTheme.INAHO) },
                modifier = Modifier.weight(1f)
            )
            ThemeSelectorChip(
                title = "Yamada",
                color = Color(0xFF9E9EDB),
                isSelected = settings.theme == AppTheme.YAMADA,
                onClick = { settingsManager.updateTheme(AppTheme.YAMADA) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "AMV MODE",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Text(
            text = "AMV features only support files in the .mp4 format.",
            color = accentColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        SettingsToggleRow(
            icon = Icons.Default.OndemandVideo,
            title = "AMV Mode Always On",
            subtitle = "Automatically play video instead of thumbnail if available",
            checked = settings.amvModeAlwaysOn,
            accentColor = accentColor,
            onToggle = { settingsManager.updateAmvModeAlwaysOn(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleRow(
            icon = Icons.Default.BlurOn,
            title = "Enable Background Blur",
            subtitle = "Apply blur effect to backgrounds",
            checked = settings.enableBackgroundBlur,
            accentColor = accentColor,
            onToggle = { settingsManager.updateEnableBackgroundBlur(it) }
        )

        SettingsSliderRow(
            title = "Background Blur",
            value = settings.amvBlurAmount,
            range = 0f..100f,
            enabled = settings.enableBackgroundBlur,
            accentColor = accentColor,
            onValueChange = { settingsManager.updateAmvBlurAmount(it) }
        )

        SettingsSliderRow(
            title = "Background Dim",
            value = settings.amvDimAmount,
            range = 0f..1f,
            enabled = true,
            accentColor = accentColor,
            onValueChange = { settingsManager.updateAmvDimAmount(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "STORAGE",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        SettingsActionRow(
            icon = Icons.Default.Delete,
            title = "Clear Cover Cache",
            subtitle = "Recreate cached song cover",
            accentColor = accentColor,
            onClick = {
                val artDir = File(context.cacheDir, "art")
                if (artDir.exists()) {
                    artDir.deleteRecursively()
                }
                Toast.makeText(context, "Restart The App Please", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                        color = if (isSelected) accentColor else Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor)
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
                withStyle(style = SpanStyle(color = Color(0xFF9E9EDB))) { append("THE ") }
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
                    SocialLink(
                        iconResId = R.drawable.github,
                        text = "GitHub",
                        url = "https://github.com/LoggingNewMemory",
                        accentColor = Color(0xFF9E9EDB)
                    )
                    SocialLink(
                        iconResId = R.drawable.youtube,
                        text = "YouTube",
                        url = "https://www.youtube.com/@KanagawaYamada",
                        accentColor = Color(0xFF9E9EDB)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        DeveloperProfile(
            role = "Inspired By",
            roleColor = accentColor,
            avatarResId = R.drawable.ic_inaho, // Ensure this exists in your res/drawable
            name = "Ochinai Inaho",
            description = "Japanese VTuber under the agency of Goraku",
            socials = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SocialLink(
                        iconResId = R.drawable.x,
                        text = "X",
                        url = "https://x.com/inaho_vt",
                        accentColor = Color(0xFFB8355B))
                    SocialLink(
                        iconResId = R.drawable.youtube,
                        text = "YouTube",
                        url = "https://www.youtube.com/@%E8%90%BD%E4%B9%83%E3%81%84%E3%81%AA%E3%81%BB",
                        accentColor = Color(0xFFB8355B))
                }
            }
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun ThemeSelectorChip(
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) color else Color(0xFF1E1414)
    val contentColor = if (isSelected) Color.White else color

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector, title: String, subtitle: String, checked: Boolean, accentColor: Color, onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1414)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF888888), fontSize = 13.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.LightGray, uncheckedTrackColor = Color(0xFF2C2C2C)
            )
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, color = Color.White.copy(alpha = alpha), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val displayValue = if (range.endInclusive > 1f) value.toInt().toString() else String.format("%.2f", value)
            Text(text = displayValue, color = accentColor.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) Color.White else Color.Gray,
                activeTrackColor = if (enabled) accentColor else Color(0xFF555555),
                inactiveTrackColor = Color(0xFF2C2C2C)
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector, title: String, subtitle: String, accentColor: Color, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF888888), fontSize = 13.sp)
        }
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
private fun SocialLink(iconResId: Int, text: String, url: String, accentColor: Color) {
    val uriHandler = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { uriHandler.openUri(url) }.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Icon(painter = painterResource(id = iconResId), contentDescription = text, tint = accentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}