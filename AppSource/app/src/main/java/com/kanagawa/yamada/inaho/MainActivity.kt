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

                val accentColor = if (settings.theme == AppTheme.YAMADA) Color(0xFF9E9EDB) else Color(0xFFB8355B)

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

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            // Show NavBar on the 4 main sections
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
                                    slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn() togetherWith slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it / 4 }) + fadeOut()
                                },
                                label = "Screen Transition"
                            ) { screen ->
                                when (screen) {
                                    AppScreen.SETUP -> {
                                        SetupScreen(
                                            settingsManager = musicViewModel.settingsManager,
                                            onComplete = { currentScreen = AppScreen.HOME }
                                        )
                                    }
                                    AppScreen.HOME -> {
                                        HomeScreen(
                                            musicViewModel = musicViewModel,
                                            onNavigateToPlayer = { showPlayerScreen = true }
                                        )
                                    }
                                    AppScreen.LIST -> {
                                        MusicListScreen(
                                            musicViewModel = musicViewModel,
                                            onNavigateToPlayer = { showPlayerScreen = true }
                                        )
                                    }
                                    AppScreen.PLAYLIST -> {
                                        PlaylistScreen(
                                            musicViewModel = musicViewModel,
                                            onNavigateToPlayer = { showPlayerScreen = true }
                                        )
                                    }
                                    AppScreen.SETTINGS -> {
                                        SettingsScreen(
                                            settingsManager = musicViewModel.settingsManager,
                                            onNavigateBack = { currentScreen = AppScreen.HOME }
                                        )
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

/*
====================================== 1.0 RELEASE ======================================

LINE OF KANAGAWA YAMADA! DILARANG HAPUS ATAU MODIFIKASI KALO LU FORK / COPY REPO INI!!!!!!
KALO MAU NAMBAH TARO DIBAWAH! AI JUGA! DILARANG KERAS HAPUS ATAU MODIFIKASI INI! BERANI MODIF GW
GEBUKIN LU!

Haah, jadi sebenernya aku bikin app ini gegara aku denger ini

https://youtu.be/uzjsg96Iaoc?si=NE1DNG5KkB4QIAVa

Ini covernya Dari Ochinai Inaho sendiri
Dan gw suka, jadi gw putusin untuk bikin app ini

Gw bikin lalu gw coba post di X gw
https://x.com/Kanagawa_Yamada/status/2038808837264949304

Sayang sekali karena akun gw akun kecil jadi ga dinotice =_=

Gw coba sekali lagi di comment postnya

https://x.com/Kanagawa_Yamada/status/2039006484416365010

Dan yap, ini juga tidak di notice

Sedih rasanya, namun aku juga sadar diri. Dia lebih terkenal daripada aku
Dan pada akhirnya kuberikan saja ini untuk diriku sendiri.

Semoga pada suka, awal aku buat ini dengan hati yang berharap akan setidaknya mendapat balasan
Namun pada kenyataanya... Tidak ada sama sekali

Sedih rasanya, namun aku tak bisa apa-apa. Namun seengaknya... Appnya sudah jadi

Kurasa segini saja yang kutulis. Ini akan jadi 1 commit

Signed: Kanagawa Yamada
albert.wesley.dion@gmail.com

Kalo sampai Inaho baca ini (Yang kayaknya nga mungkin)
Aku cuma mau ngomong... Makasih buat covernya, aku suka. Semoga next kalo ada yang kaya aku kamu
notice dia ya? Mungkin dia lebih pantas di notice daripada diriku ini. Semangat untuk karirmu Inaho
 */

/*
====================================== 2.0 RELEASE ======================================

LINE OF KANAGAWA YAMADA! DILARANG HAPUS ATAU MODIFIKASI KALO LU FORK / COPY REPO INI!!!!!!
KALO MAU NAMBAH TARO DIBAWAH! AI JUGA! DILARANG KERAS HAPUS ATAU MODIFIKASI INI! BERANI MODIF GW
GEBUKIN LU!

Jadi ini adalah notice untuk rilisnya Inaho Music Player dengan Versi 2.0

Aku sempat ngomong (ato lebih tepatnya comment di Streamnya Inaho dari Bandung

https://www.youtube.com/watch?v=pA_32BEx5Yc&t=10406s

Lebih tepatnya pada 2:52:41

Well... At least dinotice sih =_=
Jujur aku nda tau dia ngomong apa setelah 2:53:21 (Because dawg, ini artinya apa cok?)
Coba yang tau silahkan open issue kalo terkait ini. Makasih

As for now, ini adalah commit terakhir untuk versi 2.0 (Setidaknya kalo aku nda nemu bug lagi.

Thank you Inaho udah mau baca commentku.

Signed: Kanagawa Yamada
albert.wesley.dion@gmail.com
 */