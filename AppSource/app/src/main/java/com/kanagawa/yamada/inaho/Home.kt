package com.kanagawa.yamada.inaho

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val appLaunchSeed = kotlin.random.Random.Default.nextLong()

@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val settings by musicViewModel.settingsManager.settingsFlow.collectAsState()
    val artCache by musicViewModel.artCache.collectAsState()
    val playerState by PlayerService.playerState.collectAsState()
    val playerService = rememberPlayerService()
    val fullLibrary by musicViewModel.loadedSongs.collectAsState()

    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val surfaceColor = if (settings.amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)
    val accentColor = if (settings.theme == AppTheme.YAMADA) Color(0xFF9E9EDB) else Color(0xFFB8355B)

    val isVip = remember(settings.userName) {
        listOf("Kanagawa Yamada", "Ochinai Inaho", "落乃いなほ").contains(settings.userName.trim())
    }
    val nameColor = if (isVip) accentColor else Color.White

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true || permissions[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        hasPermission = storageGranted
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)

        val neededPermissions = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (neededPermissions.isNotEmpty()) permissionLauncher.launch(neededPermissions.toTypedArray())
    }

    LaunchedEffect(hasPermission, settings.sortOption, settings.onlyMusicFolder) {
        if (hasPermission) {
            withContext(Dispatchers.IO) {
                try {
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }

                    val projection = arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.TITLE,
                        MediaStore.Files.FileColumns.ARTIST,
                        MediaStore.Files.FileColumns.DURATION,
                        MediaStore.Files.FileColumns.MEDIA_TYPE
                    )

                    val sortOrder = when (settings.sortOption) {
                        SortOption.TITLE_ASC -> "${MediaStore.Files.FileColumns.TITLE} ASC"
                        SortOption.TITLE_DESC -> "${MediaStore.Files.FileColumns.TITLE} DESC"
                        SortOption.ARTIST_ASC -> "${MediaStore.Files.FileColumns.ARTIST} ASC"
                        SortOption.DATE_ADDED_DESC -> "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                        SortOption.DURATION_ASC -> "${MediaStore.Files.FileColumns.DURATION} ASC"
                        SortOption.DURATION_DESC -> "${MediaStore.Files.FileColumns.DURATION} DESC"
                    }

                    var selection = "(" +
                            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO} OR " +
                            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}" +
                            ") AND ${MediaStore.Files.FileColumns.DURATION} > 10000"

                    if (settings.onlyMusicFolder) {
                        selection += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            " AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE '%Music/%'"
                        } else {
                            " AND ${MediaStore.Files.FileColumns.DATA} LIKE '%/Music/%'"
                        }
                    }

                    val tempList = mutableListOf<Song>()
                    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val titleCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.TITLE)
                        val artistCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.ARTIST)
                        val durationCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                        val mediaTypeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                        while (c.moveToNext()) {
                            val id = c.getLong(idCol)
                            val dur = c.getLong(durationCol)
                            val title = c.getString(titleCol) ?: "Unknown"
                            val artist = c.getString(artistCol) ?: "Unknown"
                            val isVideo = c.getInt(mediaTypeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                            val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            val trackUri = ContentUris.withAppendedId(baseUri, id)

                            tempList.add(
                                Song(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    durationMs = dur,
                                    trackUri = trackUri,
                                    formattedDuration = String.format("%02d:%02d", (dur / 1000) / 60, (dur / 1000) % 60),
                                    isVideo = isVideo
                                )
                            )
                        }
                    }
                    musicViewModel.recordLoadedSongs(tempList)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val dailySongs = remember(fullLibrary) {
        if (fullLibrary.isNotEmpty()) fullLibrary.shuffled(kotlin.random.Random(appLaunchSeed)).take(5) else emptyList()
    }
    val quickList = remember(fullLibrary) {
        if (fullLibrary.size > 5) fullLibrary.shuffled(kotlin.random.Random(appLaunchSeed + 1)).take(10) else emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            Text(text = "いらっしゃいませ,", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = settings.userName, color = nameColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Song of The Day", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))

            if (dailySongs.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val mainSong = dailySongs[0]
                    LaunchedEffect(mainSong.id) { musicViewModel.loadArtIfNeeded(mainSong) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(surfaceColor)
                            .clickable {
                                playerService?.playSong(mainSong, fullLibrary, fullLibrary.indexOf(mainSong))
                                onNavigateToPlayer()
                            }
                    ) {
                        val cover = artCache[mainSong.id]
                        if (cover != null) {
                            Image(bitmap = cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C2C)))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                        .background(surfaceColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!hasPermission) "Storage permission required." else "Not enough songs found in your library.",
                        color = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                Text("Let Inaho Make Your Playlist Today!", color = accentColor, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Suggested for You", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = if (playerState.currentSong != null) 100.dp else 16.dp)
                ) {
                    itemsIndexed(quickList) { index, song ->
                        LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                        SongListItem(
                            song = song,
                            coverBitmap = artCache[song.id],
                            isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                            accentColor = accentColor,
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
        }

        AnimatedVisibility(
            visible = playerState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            val durationMs = playerState.durationMs.coerceAtLeast(1L)
            var livePositionMs by remember { mutableLongStateOf(playerState.positionMs) }

            // Polling effect for live progress matching the PlayerScreen approach
            LaunchedEffect(playerState.isPlaying, playerService, playerState.currentSong?.id) {
                if (playerService != null) {
                    livePositionMs = playerService.getCurrentPosition()
                    while (playerState.isPlaying) {
                        delay(200) // update every 200ms
                        livePositionMs = playerService.getCurrentPosition()
                    }
                }
            }

            val targetProgress = (livePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                label = "MiniPlayerProgress"
            )

            Box(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .drawBehind {
                        // 1. Static surface background
                        drawRect(color = surfaceColor)

                        // 2. Dynamic progress bar on top
                        val progressWidth = size.width * animatedProgress
                        drawRect(
                            color = accentColor.copy(alpha = 0.25f),
                            size = Size(width = progressWidth, height = size.height)
                        )
                    }
            ) {
                MiniPlayerBar(
                    playerState = playerState,
                    playerService = playerService,
                    coverBitmap = playerState.currentSong?.let { artCache[it.id] },
                    accentColor = accentColor,
                    onPlayPause = { playerService?.togglePlayPause() },
                    onNext = { playerService?.skipNext() },
                    onExpand = onNavigateToPlayer,
                    surfaceColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
private fun RowScope.GridSmallItem(
    song: Song?,
    artCache: Map<Long, Bitmap?>,
    vm: MusicViewModel,
    ps: PlayerService?,
    lib: List<Song>,
    nav: () -> Unit
) {
    if (song != null) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C2C))
                .clickable {
                    ps?.playSong(song, lib, lib.indexOf(song))
                    nav()
                }
        ) {
            LaunchedEffect(song.id) { vm.loadArtIfNeeded(song) }
            val cover = artCache[song.id]
            if (cover != null) {
                Image(bitmap = cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
    }
}