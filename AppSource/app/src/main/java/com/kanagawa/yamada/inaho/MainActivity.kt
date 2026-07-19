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
    SETUP, HOME, LIST, PLAYLIST, SETTINGS, LETTER
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
                            if (currentScreen in listOf(AppScreen.HOME, AppScreen.LIST, AppScreen.PLAYLIST, AppScreen.SETTINGS, AppScreen.LETTER)) {
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
                                        AppScreen.HOME -> HomeScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true }, onNavigateToLetter = { currentScreen = AppScreen.LETTER })
                                        AppScreen.LIST -> MusicListScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.PLAYLIST -> PlaylistScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.SETTINGS -> SettingsScreen(settingsManager = musicViewModel.settingsManager, onNavigateBack = { currentScreen = AppScreen.HOME })
                                        AppScreen.LETTER -> LetterToInahoScreen(onNavigateBack = { currentScreen = AppScreen.HOME }, accentColor = accentColor)
                                    }
                                }
                            }
                        }
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            bottomBar = {
                                if (currentScreen in listOf(AppScreen.HOME, AppScreen.LIST, AppScreen.PLAYLIST, AppScreen.SETTINGS, AppScreen.LETTER)) {
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
                                        AppScreen.HOME -> HomeScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true }, onNavigateToLetter = { currentScreen = AppScreen.LETTER })
                                        AppScreen.LIST -> MusicListScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.PLAYLIST -> PlaylistScreen(musicViewModel = musicViewModel, onNavigateToPlayer = { showPlayerScreen = true })
                                        AppScreen.SETTINGS -> SettingsScreen(settingsManager = musicViewModel.settingsManager, onNavigateBack = { currentScreen = AppScreen.HOME })
                                        AppScreen.LETTER -> LetterToInahoScreen(onNavigateBack = { currentScreen = AppScreen.HOME }, accentColor = accentColor)
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

/*
====================================== 3.0 RELEASE ======================================

LINE OF KANAGAWA YAMADA! DILARANG HAPUS ATAU MODIFIKASI KALO LU FORK / COPY REPO INI!!!!!!
KALO MAU NAMBAH TARO DIBAWAH! AI JUGA! DILARANG KERAS HAPUS ATAU MODIFIKASI INI! BERANI MODIF GW
GEBUKIN LU!

Biasanya aku kesal atau marah jika suatu hal terjadi tidak sesuai dengan keinginanku. Namun...
Kali ini berbeda.

Aku menyudahinya
Aku meninggalkannya
Aku melupakannya

Bukanlah sebuah hal yang aneh jika seorang VTuber suka dengan VTuber lain. Namun ada 1 titik dimana
suatu hal memanglah tidak bisa dicapai. Yaitu perasaanya

Kubuat Music Player Ini awalnya karena aku suka Inaho, namun... Perasaanku tak bertahan lama karena
kurasa dia lebih baik jadi VTuber saja, aku tak boleh menaruh hati padanya. Pada akhirnya aku tidak
lagi meng-oshikan dirinya, aku diam diam unsub dia dari YT, namun aku masih follow di di X.

Aplikasi ini akhirnya hanya menjadi sebuah tujuan karena aku memang butuh music player untuk diriku
sendiri.

Kutinggalkan sebuah how to open the easter egg di Xku karena Xku sepi.

https://x.com/Kanagawa_Yamada/status/2070364383159763092

Namun dengan ini Inaho Music Player berubah dari fanmade menjadi Techinal Only app.

Thank you Ochinai Inaho, sudah jadi inspirasi. Semoga karirmu menjadi VTuber sukses.
Salam untuk Goraku Production juga.

Signed: Kanagawa Yamada
albert.wesley.dion@gmail.com
*/

/*
====================================== 4.0 RELEASE ======================================

LINE OF KANAGAWA YAMADA! DILARANG HAPUS ATAU MODIFIKASI KALO LU FORK / COPY REPO INI!!!!!!
KALO MAU NAMBAH TARO DIBAWAH! AI JUGA! DILARANG KERAS HAPUS ATAU MODIFIKASI INI! BERANI MODIF GW
GEBUKIN LU!

Welcome to 4.0 Release. Ini adalah versi pertama dari Inaho Music Player dengan Techinal Appnya
Daripada sekedar hanya fanmade karena aku dulu oshinya Inaho

Aku ga nge-oshiin siapa siapa lagi. Aku fokus jadi Software Engineer, aku meninggalkan semuanya
tentang Inaho. Baik dirinya maupun Goraku Production. Aku ga mau inget apa apa lagi soal itu,

Aku ga akan sedih soal ini, aku juga seorang VTuber dan meskipun kecil aku tahu rasanya punya
fans meskipun tidak sebanyak Inaho tentunya

Namun yasudahlah, aku bukanlah VTuber yang punya suara yang imut, model yang cantik, dapat bergurau
dan lain sebagainya. Aku hanyalah seorang guru. Dan kebetulan saja aku menempuh sebagai Software
Engineer.

So? Semoga ini yang terakhir kalinya aku bahas Inaho di note di 4.0 ini. Tapi tetep aja aku meninggalkan
Sebuah jejak di Xku yang sepi.

https://x.com/Kanagawa_Yamada/status/2078866394750578885?s=20

Signed: Kanagawa Yamada
albert.wesley.dion@gmail.com
*/