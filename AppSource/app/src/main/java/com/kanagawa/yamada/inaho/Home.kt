/*
Inaho Music Player - Inaho Music Player
Copyright (C) 2026 Kanagawa Yamada
*/

package com.kanagawa.yamada.inaho

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val accentColor = getAppAccentColor(settings.theme)

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

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            musicViewModel.settingsManager.updateUserPhotoUri(it.toString())
        }
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
        if (fullLibrary.isNotEmpty()) fullLibrary.shuffled(kotlin.random.Random(appLaunchSeed)).take(21) else emptyList()
    }
    val quickList = remember(fullLibrary) {
        if (fullLibrary.size > 7) fullLibrary.shuffled(kotlin.random.Random(appLaunchSeed + 1)).take(10) else emptyList()
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Entrance animation trigger
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    // Section 1: Greeting row — delay 0ms
    val greetingAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 0, easing = FastOutSlowInEasing),
        label = "greetingAlpha"
    )
    val greetingOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 500, delayMillis = 0, easing = FastOutSlowInEasing),
        label = "greetingOffsetY"
    )

    // Section 2: Song of The Day — delay 150ms
    val songOfDayAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "songOfDayAlpha"
    )
    val songOfDayOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 500, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "songOfDayOffsetY"
    )

    // Section 3: Shuffle button — delay 300ms
    val shuffleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "shuffleAlpha"
    )
    val shuffleOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 500, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "shuffleOffsetY"
    )

    // Section 4: Suggested for You — delay 450ms
    val suggestedAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 450, easing = FastOutSlowInEasing),
        label = "suggestedAlpha"
    )
    val suggestedOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 500, delayMillis = 450, easing = FastOutSlowInEasing),
        label = "suggestedOffsetY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(greetingAlpha)
                    .offset(y = greetingOffsetY),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(text = "いらっしゃいませ,", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    val nameFontSize = if (settings.userName.length > 12) 22.sp else 28.sp
                    Text(text = settings.userName, color = nameColor, fontSize = nameFontSize, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                }
                
                settings.userPhotoUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "User Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, accentColor, CircleShape)
                            .background(surfaceColor)
                            .clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                    )
                } ?: run {
                    Image(
                        painter = painterResource(id = R.drawable.ic_inaho),
                        contentDescription = "Default Photo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, accentColor, CircleShape)
                            .background(Color.White)
                            .clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Song of The Day",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .alpha(songOfDayAlpha)
                    .offset(y = songOfDayOffsetY)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (dailySongs.isNotEmpty()) {
                // Song of The Day grid with entrance animation
                val rowModifier = if (isTablet) Modifier.fillMaxWidth().height(240.dp) else Modifier.fillMaxWidth()
                Row(
                    modifier = rowModifier
                        .alpha(songOfDayAlpha)
                        .offset(y = songOfDayOffsetY),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mainSong = dailySongs[0]
                    LaunchedEffect(mainSong.id) { musicViewModel.loadArtIfNeeded(mainSong) }
                    
                    val mainBoxModifier = if (isTablet) Modifier.fillMaxHeight().aspectRatio(1f) else Modifier.weight(1f).aspectRatio(1f)
                    
                    Box(
                        modifier = mainBoxModifier
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

                    if (isTablet) {
                        androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
                            rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(dailySongs.drop(1)) { song ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2C2C2C))
                                        .clickable {
                                            playerService?.playSong(song, fullLibrary, fullLibrary.indexOf(song))
                                            onNavigateToPlayer()
                                        }
                                ) {
                                    LaunchedEffect(song.id) { musicViewModel.loadArtIfNeeded(song) }
                                    val cover = artCache[song.id]
                                    if (cover != null) {
                                        Image(bitmap = cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    } else {
                        val rightColModifier = Modifier.weight(1f).aspectRatio(1f)

                        Column(
                            modifier = rightColModifier,
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
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTablet) 240.dp else 180.dp)
                        .alpha(songOfDayAlpha)
                        .offset(y = songOfDayOffsetY)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .alpha(shuffleAlpha)
                    .offset(y = shuffleOffsetY),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2020))
            ) {
                Text("Let Inaho Make Your Playlist Today!", color = accentColor, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Suggested for You",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .alpha(suggestedAlpha)
                    .offset(y = suggestedOffsetY)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .alpha(suggestedAlpha)
                    .offset(y = suggestedOffsetY)
            ) {
                if (isTablet) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                } else {
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