/*
Inaho Music Player - Inaho Music Player
Copyright (C) 2026 Kanagawa Yamada
*/

package com.kanagawa.yamada.inaho

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.kanagawa.yamada.inaho.ui.theme.HaloMusicTheme

// --- Application ---
class InahoApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context).build()
}

// --- Screen Enum Updated ---
enum class AppScreen {
    SETUP, HOME, LIST, PLAYLIST, SETTINGS
}

// --- Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set the ENTIRE APP to Full Immersive Mode
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            HaloMusicTheme {
                val musicViewModel: MusicViewModel = viewModel()
                val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()

                val accentColor = getAppAccentColor(settings)

                // Route to SETUP if name is blank, otherwise go to HOME
                var currentScreen by rememberSaveable {
                    mutableStateOf(if (settings.userName.isBlank()) AppScreen.SETUP else AppScreen.HOME)
                }

                // Overlay state for the PlayerScreen
                var showPlayerScreen by rememberSaveable { mutableStateOf(false) }

                val playerState by PlayerService.playerState.collectAsState()

                LaunchedEffect(playerState.currentIndex, playerState.activeQueue) {
                    if (playerState.activeQueue.isNotEmpty() && playerState.currentIndex >= 0) {
                        musicViewModel.preloadQueueWindow(playerState.activeQueue, playerState.currentIndex)
                    }
                }

                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isTablet = configuration.screenWidthDp >= 600

                val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF121212)

                Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
                    if (isTablet) {
                        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                            if (currentScreen in listOf(AppScreen.HOME, AppScreen.LIST, AppScreen.PLAYLIST, AppScreen.SETTINGS)) {
                                NavRail(
                                    currentScreen = currentScreen,
                                    onNavigate = { currentScreen = it },
                                    amoledBlack = settings.amoledBlack,
                                    accentColor = accentColor
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                                    },
                                    label = "Screen Transition"
                                ) { screen ->
                                    when (screen) {
                                        AppScreen.SETUP -> SetupScreen(settingsManager = musicViewModel.settingsManager, onComplete = { currentScreen = AppScreen.HOME })
                                        AppScreen.HOME -> HomeScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.LIST -> MusicListScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.PLAYLIST -> PlaylistScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.SETTINGS -> SettingsScreen(settingsManager = musicViewModel.settingsManager, onNavigateBack = { currentScreen = AppScreen.HOME })
                                    }
                                }
                            }
                        }
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            bottomBar = {
                                if (currentScreen in listOf(AppScreen.HOME, AppScreen.LIST, AppScreen.PLAYLIST, AppScreen.SETTINGS)) {
                                    NavBar(
                                        currentScreen = currentScreen,
                                        onNavigate = { currentScreen = it },
                                        amoledBlack = settings.amoledBlack,
                                        accentColor = accentColor
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                                    },
                                    label = "Screen Transition"
                                ) { screen ->
                                    when (screen) {
                                        AppScreen.SETUP -> SetupScreen(settingsManager = musicViewModel.settingsManager, onComplete = { currentScreen = AppScreen.HOME })
                                        AppScreen.HOME -> HomeScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.LIST -> MusicListScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.PLAYLIST -> PlaylistScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.SETTINGS -> SettingsScreen(settingsManager = musicViewModel.settingsManager, onNavigateBack = { currentScreen = AppScreen.HOME })
                                    }
                                }
                            }
                        }
                    }

                    // --- Persistent Player Overlay ---
                    val playerOffsetY by animateFloatAsState(
                        targetValue = if (showPlayerScreen) 0f else 1f,
                        animationSpec = tween(400),
                        label = "PlayerSlide"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = size.height * playerOffsetY
                                clip = true
                            }
                    ) {
                        PlayerScreen(
                            musicViewModel = musicViewModel,
                            isVisible = showPlayerScreen,
                            onNavigateBack = { showPlayerScreen = false }
                        )
                    }
                }
            }
        }
    }
}

