package com.kanagawa.yamada.inaho

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistScreen(
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()
    val favorites by musicViewModel.playlistManager.favoritesFlow.collectAsState()
    val customPlaylists by musicViewModel.playlistManager.customPlaylistsFlow.collectAsState()
    val loadedSongs by musicViewModel.loadedSongs.collectAsState()
    val artCache by musicViewModel.artCache.collectAsState()
    val playerState by PlayerService.playerState.collectAsState()
    val playerService = rememberPlayerService()

    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val surfaceColor = if (settings.amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)

    // Navigation state inside the screen
    var currentView by remember { mutableStateOf("LIST") } // LIST, FAV, CUSTOM
    var selectedPlaylist by remember { mutableStateOf<PlaylistManager.Playlist?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Rename dialog state
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistManager.Playlist?>(null) }

    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaylistManager.Playlist?>(null) }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF1E1414),
            title = { Text("New Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name", color = Color(0xFF666666)) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFB8355B),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFFB8355B),
                        unfocusedIndicatorColor = Color(0xFF333333)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) musicViewModel.playlistManager.createPlaylist(newName.trim())
                    showCreateDialog = false
                }) { Text("Create", color = Color(0xFFB8355B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = Color.LightGray) }
            }
        )
    }

    if (showRenameDialog && renameTarget != null) {
        var newName by remember { mutableStateOf(renameTarget!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; renameTarget = null },
            containerColor = Color(0xFF1E1414),
            title = { Text("Rename Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name", color = Color(0xFF666666)) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFB8355B),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFFB8355B),
                        unfocusedIndicatorColor = Color(0xFF333333)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        musicViewModel.playlistManager.renamePlaylist(renameTarget!!.id, newName.trim())
                        // If currently viewing this playlist, refresh the title
                        if (selectedPlaylist?.id == renameTarget!!.id) {
                            selectedPlaylist = selectedPlaylist!!.copy(name = newName.trim())
                        }
                    }
                    showRenameDialog = false
                    renameTarget = null
                }) { Text("Rename", color = Color(0xFFB8355B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; renameTarget = null }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    if (showDeleteDialog && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteTarget = null },
            containerColor = Color(0xFF1E1414),
            title = { Text("Delete Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Delete \"${deleteTarget!!.name}\"? This can't be undone.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    musicViewModel.playlistManager.deletePlaylist(deleteTarget!!.id)
                    showDeleteDialog = false
                    deleteTarget = null
                }) { Text("Delete", color = Color(0xFFB8355B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteTarget = null }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (currentView == "LIST") {
            // --- MAIN PLAYLIST OVERVIEW ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Library",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.background(Color(0xFF2C2020), RoundedCornerShape(12.dp))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Playlist", tint = Color(0xFFB8355B))
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (playerState.currentSong != null) 100.dp else 24.dp)
            ) {
                // 1. Favorites Playlist Card
                item {
                    val firstFavSong = remember(favorites, loadedSongs) { loadedSongs.find { it.id == favorites.firstOrNull() } }
                    LaunchedEffect(firstFavSong?.id) { firstFavSong?.let { musicViewModel.loadArtIfNeeded(it) } }
                    val favCover = firstFavSong?.let { artCache[it.id] }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF4A1525), Color(0xFF2A0D15))))
                            .clickable { currentView = "FAV" }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (favCover != null) {
                            Image(
                                bitmap = favCover.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Favorites", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "${favorites.size} saved songs", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                        }
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    }
                }

                // 2. Custom Playlists
                items(customPlaylists) { playlist ->
                    val firstSong = remember(playlist.songIds, loadedSongs) { loadedSongs.find { it.id == playlist.songIds.firstOrNull() } }
                    LaunchedEffect(firstSong?.id) { firstSong?.let { musicViewModel.loadArtIfNeeded(it) } }
                    val cover = firstSong?.let { artCache[it.id] }

                    // 3-dot menu state per card
                    var showMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(surfaceColor)
                            .clickable {
                                selectedPlaylist = playlist
                                currentView = "CUSTOM"
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cover != null) {
                            Image(
                                bitmap = cover.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2C2C2C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = playlist.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "${playlist.songIds.size} songs", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                        }

                        // 3-dot menu button (replaces the old inline Delete icon)
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Playlist options",
                                    tint = Color(0xFF888888)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = Color(0xFF2A1A1A)
                            ) {
                                // Reorder songs
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Reorder, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(20.dp))
                                    },
                                    text = { Text("Reorder songs", color = Color(0xFFCCCCCC), fontSize = 14.sp) },
                                    onClick = {
                                        showMenu = false
                                        selectedPlaylist = playlist
                                        currentView = "CUSTOM"
                                    }
                                )
                                // Rename
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(20.dp))
                                    },
                                    text = { Text("Rename", color = Color(0xFFCCCCCC), fontSize = 14.sp) },
                                    onClick = {
                                        showMenu = false
                                        renameTarget = playlist
                                        showRenameDialog = true
                                    }
                                )
                                HorizontalDivider(color = Color(0xFF3A2020), thickness = 1.dp)
                                // Delete
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFB8355B), modifier = Modifier.size(20.dp))
                                    },
                                    text = { Text("Delete playlist", color = Color(0xFFB8355B), fontSize = 14.sp) },
                                    onClick = {
                                        showMenu = false
                                        deleteTarget = playlist
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // --- DETAILED VIEW (Favorites or Custom Playlist) ---
            val isFavView = currentView == "FAV"
            val title = if (isFavView) "Favorites" else selectedPlaylist?.name ?: "Playlist"
            val songIds = if (isFavView) favorites else selectedPlaylist?.songIds ?: emptyList()

            // Maintain local reorderable list so drags feel instant before persistence
            var reorderableSongIds by remember(songIds) { mutableStateOf(songIds) }
            val songsToDisplay = remember(loadedSongs, reorderableSongIds) {
                reorderableSongIds.mapNotNull { id -> loadedSongs.find { it.id == id } }
            }

            val heroCover = songsToDisplay.firstOrNull()?.let { artCache[it.id] }

            // Lazy list + reorderable state
            val lazyListState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                reorderableSongIds = reorderableSongIds.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
                // Persist the new order
                if (isFavView) {
                    musicViewModel.playlistManager.reorderFavorites(reorderableSongIds)
                } else {
                    selectedPlaylist?.let { pl ->
                        musicViewModel.playlistManager.reorderPlaylistSongs(pl.id, reorderableSongIds)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentView = "LIST" }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFB8355B))
                    }
                }

                // Hero Header Section
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .shadow(12.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFavView) Color(0xFFB8355B) else Color(0xFF2C2C2C)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (heroCover != null) {
                            Image(bitmap = heroCover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(
                                imageVector = if (isFavView) Icons.Default.Favorite else Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${songsToDisplay.size} songs", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Play All Button
                            Button(
                                onClick = {
                                    if (songsToDisplay.isNotEmpty()) {
                                        playerService?.playSong(songsToDisplay[0], songsToDisplay, 0)
                                        musicViewModel.preloadQueueWindow(songsToDisplay, 0)
                                        onNavigateToPlayer()
                                    }
                                },
                                enabled = songsToDisplay.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFFB8355B),
                                    disabledContainerColor = Color.White.copy(alpha = 0.5f),
                                    disabledContentColor = Color(0xFFB8355B).copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            // Shuffle Button
                            Button(
                                onClick = {
                                    if (songsToDisplay.isNotEmpty()) {
                                        val shuffled = songsToDisplay.shuffled()
                                        playerService?.playSong(shuffled[0], shuffled, 0)
                                        musicViewModel.preloadQueueWindow(shuffled, 0)
                                        onNavigateToPlayer()
                                    }
                                },
                                enabled = songsToDisplay.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB8355B),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFB8355B).copy(alpha = 0.5f),
                                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = surfaceColor, thickness = 2.dp)

                // Songs List (reorderable)
                if (songsToDisplay.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No songs yet.\nAdd some songs to this playlist!",
                            color = Color(0xFF666666),
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = if (playerState.currentSong != null) 100.dp else 24.dp, top = 8.dp)
                    ) {
                        itemsIndexed(songsToDisplay, key = { _, s -> s.id }) { index, song ->
                            LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }

                            ReorderableItem(reorderableState, key = song.id) { isDragging ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isDragging) Color(0xFF2C1A1A) else Color.Transparent)
                                        .clickable {
                                            playerService?.playSong(song, songsToDisplay, index)
                                            musicViewModel.preloadQueueWindow(songsToDisplay, index)
                                            onNavigateToPlayer()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Drag handle — must be inside ReorderableItem lambda
                                    Icon(
                                        imageVector = Icons.Default.Reorder,
                                        contentDescription = "Drag to reorder",
                                        tint = Color(0xFF555555),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .draggableHandle()
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PlaylistSongRow(
                                        song = song,
                                        coverBitmap = artCache[song.id],
                                        isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                                        showRemove = !isFavView && selectedPlaylist != null,
                                        modifier = Modifier.weight(1f),
                                        onRemove = {
                                            if (selectedPlaylist != null) {
                                                musicViewModel.playlistManager.removeSongFromPlaylist(selectedPlaylist!!.id, song.id)
                                            }
                                        }
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

// Song content row — rendered inside the ReorderableItem lambda.
// The outer Row (with drag handle) lives in the caller; this composable
// fills the remaining space with its own Row, giving weight(1f) a valid RowScope.
@Composable
private fun PlaylistSongRow(
    song: Song,
    coverBitmap: Bitmap?,
    isPlaying: Boolean,
    showRemove: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Album art ────────────────────────────────────────────────────
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2C2C2C))
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ── Song info ────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isPlaying) Color(0xFFB8355B) else Color.White,
                fontSize = 16.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Right side: Remove or duration ──────────────────────────────
        if (showRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = Color(0xFF666666))
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = song.formattedDuration, color = Color(0xFF666666), fontSize = 13.sp)
        }
    }
}