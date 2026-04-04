package com.kanagawa.yamada.inaho

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()
    val artCache by musicViewModel.artCache.collectAsState()
    val playerState by PlayerService.playerState.collectAsState()
    val playerService = rememberPlayerService()
    val fullLibrary by musicViewModel.loadedSongs.collectAsState()

    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val surfaceColor = if (settings.amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)

    val greetings = remember { listOf("Welcome", "いらっしゃいませ", "Selamat Datang", "Sugeng Rawuh") }
    val randomGreeting = remember { greetings.random() }

    // Pick 5 random songs for the grid
    val dailySongs = remember(fullLibrary) { if (fullLibrary.isNotEmpty()) fullLibrary.shuffled().take(5) else emptyList() }
    // Pick 10 random songs for the "Jump Back In" quick list
    val quickList = remember(fullLibrary) { if (fullLibrary.size > 5) fullLibrary.shuffled().take(10) else emptyList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 16.dp) // <-- FIX: Separated horizontal padding
            .padding(top = 16.dp)        // <-- FIX: Separated top padding
    ) {
        Text(text = "$randomGreeting, ${settings.userName}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = "Inaho", color = Color(0xFFB8355B), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Song of The Day", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // --- Grid ---
        if (dailySongs.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Main Item
                val mainSong = dailySongs[0]
                LaunchedEffect(mainSong.id) { musicViewModel.loadArtIfNeeded(mainSong) }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(surfaceColor).clickable {
                        playerService?.playSong(mainSong, fullLibrary, fullLibrary.indexOf(mainSong))
                        onNavigateToPlayer()
                    }
                ) {
                    val cover = artCache[mainSong.id]
                    if (cover != null) Image(bitmap = cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C2C)))
                }

                // 4 Small Items
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GridSmallItem(song = dailySongs.getOrNull(1), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                        GridSmallItem(song = dailySongs.getOrNull(2), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GridSmallItem(song = dailySongs.getOrNull(3), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                        GridSmallItem(song = dailySongs.getOrNull(4), artCache = artCache, vm = musicViewModel, ps = playerService, lib = fullLibrary, nav = onNavigateToPlayer)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(surfaceColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("Not enough songs", color = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Playlist Button ---
        Button(
            onClick = {
                if (fullLibrary.isNotEmpty()) {
                    val shuffled = fullLibrary.shuffled()
                    playerService?.playSong(shuffled[0], shuffled, 0)
                    musicViewModel.preloadQueueWindow(shuffled, 0)
                    onNavigateToPlayer()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2020))
        ) {
            Text("Let Inaho Make Your Playlist Today!", color = Color(0xFFB8355B), fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Suggested for You", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        // --- Quick List ---
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = if (playerState.currentSong != null) 88.dp else 16.dp)
            ) {
                itemsIndexed(quickList) { index, song ->
                    LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                    SongListItem(
                        song = song,
                        coverBitmap = artCache[song.id],
                        isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                        onClick = {
                            val safeQueue = if (fullLibrary.isNotEmpty()) fullLibrary else listOf(song)
                            val queueIndex = safeQueue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
                            playerService?.playSong(song, safeQueue, queueIndex)
                            musicViewModel.preloadQueueWindow(safeQueue, queueIndex)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }

        // --- Mini Player ---
        AnimatedVisibility(
            visible = playerState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            MiniPlayerBar(
                playerState = playerState, playerService = playerService,
                coverBitmap = playerState.currentSong?.let { artCache[it.id] },
                onPlayPause = { playerService?.togglePlayPause() },
                onNext = { playerService?.skipNext() },
                onExpand = onNavigateToPlayer,
                surfaceColor = surfaceColor
            )
        }
    }
}

@Composable
private fun RowScope.GridSmallItem(song: Song?, artCache: Map<Long, Bitmap?>, vm: MusicViewModel, ps: PlayerService?, lib: List<Song>, nav: () -> Unit) {
    Box(
        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2C2C2C)).clickable {
            if (song != null) {
                ps?.playSong(song, lib, lib.indexOf(song))
                nav()
            }
        }
    ) {
        if (song != null) {
            LaunchedEffect(song.id) { vm.loadArtIfNeeded(song) }
            val cover = artCache[song.id]
            if (cover != null) Image(bitmap = cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}