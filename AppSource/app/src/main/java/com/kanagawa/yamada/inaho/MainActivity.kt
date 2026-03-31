package com.kanagawa.yamada.inaho

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

// --- Screen Enum ---
enum class AppScreen {
    LIST, SETTINGS, PLAYER
}

// --- Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.show(WindowInsetsCompat.Type.statusBars())

        setContent {
            HaloMusicTheme {
                val musicViewModel: MusicViewModel = viewModel()
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.LIST) }

                // Track global player state across all screens
                val playerState by PlayerService.playerState.collectAsState()

                // THE FIX: Whenever the song changes (auto-play next, skipped, clicked), preload its art instantly!
                LaunchedEffect(playerState.currentIndex, playerState.activeQueue) {
                    if (playerState.activeQueue.isNotEmpty() && playerState.currentIndex >= 0) {
                        musicViewModel.preloadQueueWindow(playerState.activeQueue, playerState.currentIndex)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {

                        // ANIMATED SCREEN TRANSITIONS
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                when (targetState) {
                                    AppScreen.PLAYER -> {
                                        slideInVertically(
                                            animationSpec = tween(400),
                                            initialOffsetY = { it }
                                        ) + fadeIn() togetherWith slideOutVertically(
                                            animationSpec = tween(400),
                                            targetOffsetY = { -it / 4 }
                                        ) + fadeOut()
                                    }
                                    AppScreen.SETTINGS -> {
                                        slideInHorizontally(
                                            animationSpec = tween(300),
                                            initialOffsetX = { it }
                                        ) + fadeIn() togetherWith slideOutHorizontally(
                                            animationSpec = tween(300),
                                            targetOffsetX = { -it / 4 }
                                        ) + fadeOut()
                                    }
                                    AppScreen.LIST -> {
                                        if (initialState == AppScreen.PLAYER) {
                                            slideInVertically(
                                                animationSpec = tween(400),
                                                initialOffsetY = { -it / 4 }
                                            ) + fadeIn() togetherWith slideOutVertically(
                                                animationSpec = tween(400),
                                                targetOffsetY = { it }
                                            ) + fadeOut()
                                        } else {
                                            slideInHorizontally(
                                                animationSpec = tween(300),
                                                initialOffsetX = { -it / 4 }
                                            ) + fadeIn() togetherWith slideOutHorizontally(
                                                animationSpec = tween(300),
                                                targetOffsetX = { it }
                                            ) + fadeOut()
                                        }
                                    }
                                }
                            },
                            label = "Screen Transition"
                        ) { screen ->
                            when (screen) {
                                AppScreen.LIST -> {
                                    MusicListScreen(
                                        musicViewModel = musicViewModel,
                                        onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                                        onNavigateToPlayer = { currentScreen = AppScreen.PLAYER }
                                    )
                                }
                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        settingsManager = musicViewModel.settingsManager,
                                        onNavigateBack = { currentScreen = AppScreen.LIST }
                                    )
                                }
                                AppScreen.PLAYER -> {
                                    PlayerScreen(
                                        musicViewModel = musicViewModel,
                                        onNavigateBack = { currentScreen = AppScreen.LIST }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}