/*
Inaho Music Player - Inaho Music Player
Copyright (C) 2026 Kanagawa Yamada
*/

package com.kanagawa.yamada.inaho

import android.content.Context
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

enum class AppTheme { INAHO, YAMADA, SYSTEM, CUSTOM }

enum class VisualizerType(val displayName: String) { 
    NONE("None"), 
    BARS("Bars"), 
    WAVEFORM("Waveform"), 
    LINE("Smooth Line"), 
    CIRCLE("Circle Pulse"),
    PEAKS("Peaks")
}

@Composable
fun getAppAccentColor(settings: AppSettings): Color {
    val context = LocalContext.current
    return when (settings.theme) {
        AppTheme.YAMADA -> Color(0xFF9E9EDB)
        AppTheme.INAHO -> Color(0xFFB8355B)
        AppTheme.SYSTEM -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context).primary
            } else {
                Color(0xFFB8355B)
            }
        }
        AppTheme.CUSTOM -> Color(settings.customThemeColor)
    }
}

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
    val theme: AppTheme = AppTheme.INAHO,
    val userPhotoUri: String? = null,
    val keepScreenOn: Boolean = false,
    val immersiveMode: Boolean = true,
    val customThemeColor: Int = 0xFFB8355B.toInt(),
    val visualizerType: VisualizerType = VisualizerType.NONE,
    val crossfadeDuration: Float = 0f
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
            theme = AppTheme.valueOf(prefs.getString("theme", AppTheme.INAHO.name) ?: AppTheme.INAHO.name),
            userPhotoUri = prefs.getString("user_photo_uri", null),
            keepScreenOn = prefs.getBoolean("keep_screen_on", false),
            immersiveMode = prefs.getBoolean("immersive_mode", true),
            customThemeColor = prefs.getInt("custom_theme_color", 0xFFB8355B.toInt()),
            visualizerType = VisualizerType.valueOf(prefs.getString("visualizer_type", VisualizerType.NONE.name) ?: VisualizerType.NONE.name),
            crossfadeDuration = try { prefs.getFloat("crossfade_duration", 0f) } catch (e: Exception) { prefs.getInt("crossfade_duration", 0).toFloat() }
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

    fun updateUserPhotoUri(uri: String?) {
        prefs.edit().putString("user_photo_uri", uri).apply()
        _settingsFlow.value = _settingsFlow.value.copy(userPhotoUri = uri)
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(keepScreenOn = enabled)
    }

    fun updateImmersiveMode(enabled: Boolean) {
        prefs.edit().putBoolean("immersive_mode", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(immersiveMode = enabled)
    }

    fun updateCustomThemeColor(color: Int) {
        prefs.edit().putInt("custom_theme_color", color).apply()
        _settingsFlow.value = _settingsFlow.value.copy(customThemeColor = color)
    }

    fun updateVisualizerType(type: VisualizerType) {
        prefs.edit().putString("visualizer_type", type.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(visualizerType = type)
    }

    fun updateCrossfadeDuration(seconds: Float) {
        prefs.edit().putFloat("crossfade_duration", seconds).apply()
        _settingsFlow.value = _settingsFlow.value.copy(crossfadeDuration = seconds)
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

    val accentColor = getAppAccentColor(settings)

    var showColorPicker by remember { mutableStateOf(settings.theme == AppTheme.CUSTOM) }

    // Entrance fade-in animation
    var startAnimation by remember { mutableStateOf(ScreenAnimationState.settingsAnimated) }
    LaunchedEffect(Unit) {
        startAnimation = true
        ScreenAnimationState.settingsAnimated = true
    }
    val settingsAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "settingsAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (settings.amoledBlack) Color.Black else Color(0xFF120E0E))
            .safeDrawingPadding()
            .alpha(settingsAlpha)
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
            icon = Icons.AutoMirrored.Filled.List,
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

        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleRow(
            icon = Icons.Default.Lightbulb,
            title = "Always On Display",
            subtitle = "Keep screen awake while in Player Screen",
            checked = settings.keepScreenOn,
            accentColor = accentColor,
            onToggle = { settingsManager.updateKeepScreenOn(it) }
        )
        
        SettingsToggleRow(
            icon = Icons.Default.Fullscreen,
            title = "Immersive Mode",
            subtitle = "Hide status bar and navigation bar",
            checked = settings.immersiveMode,
            accentColor = accentColor,
            onToggle = { settingsManager.updateImmersiveMode(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "VISUALIZER",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        var showVisualizerDropdown by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF222222))
                .clickable { showVisualizerDropdown = !showVisualizerDropdown }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Style", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(settings.visualizerType.displayName, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    }
                }
                Icon(
                    imageVector = if (showVisualizerDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            
            androidx.compose.animation.AnimatedVisibility(visible = showVisualizerDropdown) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    VisualizerType.values().forEach { type ->
                        val isSelected = settings.visualizerType == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.updateVisualizerType(type)
                                    showVisualizerDropdown = false
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = type.displayName,
                                color = if (isSelected) accentColor else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "THEME",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeSelectorChip(
                    title = "System",
                    color = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(context).primary else Color(0xFF555555),
                    isSelected = settings.theme == AppTheme.SYSTEM,
                    onClick = { settingsManager.updateTheme(AppTheme.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                ThemeSelectorChip(
                    title = "Custom",
                    color = Color(settings.customThemeColor),
                    isSelected = settings.theme == AppTheme.CUSTOM,
                    onClick = { 
                        if (settings.theme == AppTheme.CUSTOM) {
                            showColorPicker = !showColorPicker
                        } else {
                            settingsManager.updateTheme(AppTheme.CUSTOM)
                            showColorPicker = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(visible = settings.theme == AppTheme.CUSTOM && showColorPicker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF222222))
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val initialHsv = remember { FloatArray(3).apply { android.graphics.Color.colorToHSV(settings.customThemeColor, this) } }
                var hue by remember { mutableFloatStateOf(initialHsv[0]) }
                var sat by remember { mutableFloatStateOf(initialHsv[1]) }
                var value by remember { mutableFloatStateOf(initialHsv[2]) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .pointerInput("drag") {
                                detectDragGestures { change, _ ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val offset = change.position
                                    val dx = offset.x - center.x
                                    val dy = offset.y - center.y
                                    
                                    var degree = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (degree < 0) degree += 360f
                                    
                                    val radius = size.width / 2f
                                    val distance = (kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat() / radius).coerceIn(0f, 1f)
                                    
                                    hue = degree
                                    sat = distance
                                    settingsManager.updateCustomThemeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                                }
                            }
                            .pointerInput("tap") {
                                detectTapGestures { offset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val dx = offset.x - center.x
                                    val dy = offset.y - center.y
                                    
                                    var degree = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (degree < 0) degree += 360f
                                    
                                    val radius = size.width / 2f
                                    val distance = (kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat() / radius).coerceIn(0f, 1f)
                                    
                                    hue = degree
                                    sat = distance
                                    settingsManager.updateCustomThemeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.width / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)
    
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White, Color.Transparent),
                                    center = center,
                                    radius = radius
                                )
                            )
                            if (value < 1f) {
                                drawCircle(
                                    color = Color.Black.copy(alpha = 1f - value)
                                )
                            }
                            
                            val angleRad = Math.toRadians(hue.toDouble())
                            val thumbDistance = sat * radius
                            val thumbX = center.x + thumbDistance * kotlin.math.cos(angleRad).toFloat()
                            val thumbY = center.y + thumbDistance * kotlin.math.sin(angleRad).toFloat()
                            
                            drawCircle(
                                color = Color.White,
                                radius = 12.dp.toPx(),
                                center = Offset(thumbX, thumbY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = 12.dp.toPx(),
                                center = Offset(thumbX, thumbY),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(32.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .pointerInput("drag") {
                                detectDragGestures { change, _ ->
                                    val y = change.position.y
                                    value = 1f - (y / size.height).coerceIn(0f, 1f)
                                    settingsManager.updateCustomThemeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                                }
                            }
                            .pointerInput("tap") {
                                detectTapGestures { offset ->
                                    val y = offset.y
                                    value = 1f - (y / size.height).coerceIn(0f, 1f)
                                    settingsManager.updateCustomThemeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f))), Color.Black)
                                ),
                                size = size,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                            )
                            
                            val thumbY = (1f - value) * size.height
                            drawCircle(
                                color = Color.White,
                                radius = 10.dp.toPx(),
                                center = Offset(size.width / 2f, thumbY.coerceIn(10.dp.toPx(), size.height - 10.dp.toPx())),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = 10.dp.toPx(),
                                center = Offset(size.width / 2f, thumbY.coerceIn(10.dp.toPx(), size.height - 10.dp.toPx())),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                }
            }
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
            text = "PLAYBACK",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        SettingsSliderRow(
            title = "Crossfade Duration (${String.format("%.1f", settings.crossfadeDuration)}s)",
            value = settings.crossfadeDuration,
            range = 0f..1f,
            steps = 9,
            enabled = true, // Always enabled
            accentColor = accentColor,
            onValueChange = { settingsManager.updateCrossfadeDuration(it) }
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

        Spacer(modifier = Modifier.height(200.dp))
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
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, color = Color.White.copy(alpha = alpha), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val displayValue = if (range.endInclusive > 1f) value.toInt().toString() else String.format("%.1f", value)
            Text(text = displayValue, color = accentColor.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
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