package com.kanagawa.yamada.inaho

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
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

        // 1. Enable drawing behind system bars
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2. Hide ONLY the Navigation Bar
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Allow the navigation bar to temporarily appear if the user swipes up from the bottom
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Hide the navigation bar, but explicitly ensure the status bar remains visible
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.show(WindowInsetsCompat.Type.statusBars())

        setContent {
            HaloMusicTheme {
                val musicViewModel: MusicViewModel = viewModel()
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.LIST) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // innerPadding will now automatically protect your content from overlapping
                    // with the visible status bar at the top!
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Crossfade(targetState = currentScreen, label = "Screen Transition") { screen ->
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