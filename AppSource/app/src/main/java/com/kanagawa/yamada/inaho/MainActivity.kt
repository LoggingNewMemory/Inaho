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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.kanagawa.yamada.inaho.ui.theme.HaloMusicTheme

// --- Application ---
class InahoApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context).build()
}

// --- Screen Enum for simple Navigation ---
enum class AppScreen {
    LIST, SETTINGS
}

// --- Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloMusicTheme {
                val musicViewModel: MusicViewModel = viewModel()
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.LIST) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Crossfade(targetState = currentScreen, label = "Screen Transition") { screen ->
                            when (screen) {
                                AppScreen.LIST -> {
                                    MusicListScreen(
                                        musicViewModel = musicViewModel,
                                        onNavigateToSettings = { currentScreen = AppScreen.SETTINGS }
                                    )
                                }
                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        settingsManager = musicViewModel.settingsManager,
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