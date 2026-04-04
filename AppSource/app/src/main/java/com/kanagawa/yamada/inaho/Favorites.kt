package com.kanagawa.yamada.inaho

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FavoritesScreen(
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()
    val favorites by musicViewModel.favoritesManager.favoritesFlow.collectAsState()
    val loadedSongs by musicViewModel.loadedSongs.collectAsState()
    val artCache by musicViewModel.artCache.collectAsState()
    val playerState by PlayerService.playerState.collectAsState()
    val playerService = rememberPlayerService()

    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)

    val favoriteSongs = remember(loadedSongs, favorites) {
        loadedSongs.filter { favorites.contains(it.id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Your Favorites",
            color = Color(0xFFB8355B),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (favoriteSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No favorites yet.\nTap the heart on a song to add it!",
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = if (playerState.currentSong != null) 88.dp else 16.dp)
            ) {
                items(favoriteSongs.size, key = { favoriteSongs[it].id }) { index ->
                    val song = favoriteSongs[index]
                    LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                    SongListItem(
                        song = song,
                        coverBitmap = artCache[song.id],
                        isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                        onClick = {
                            playerService?.playSong(song, favoriteSongs, index)
                            musicViewModel.preloadQueueWindow(favoriteSongs, index)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }
    }
}