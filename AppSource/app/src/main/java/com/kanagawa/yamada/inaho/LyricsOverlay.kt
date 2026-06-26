package com.kanagawa.yamada.inaho

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileInputStream
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOverlay(
    song: Song?,
    isVisible: Boolean,
    onClose: () -> Unit,
    accentColor: Color,
    currentPositionMs: Long = 0L
) {
    var lyrics by remember(song) { mutableStateOf("") }
    var lrcLines by remember(song) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var isEditing by remember(song) { mutableStateOf(false) }
    var isLoading by remember(song) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var saveStatus by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                isLoading = true
                saveStatus = ""
                try {
                    val intentSender = saveLyricsToDisk(context, song!!, lyrics)
                    if (intentSender == null) {
                        saveStatus = ""
                        isEditing = false
                        lrcLines = parseLrc(lyrics)
                    } else {
                        saveStatus = "Failed again!"
                    }
                } catch(e: Exception) {
                    saveStatus = "Failed: ${e.message}"
                }
                isLoading = false
            }
        } else {
            saveStatus = "Permission denied!"
        }
    }

    LaunchedEffect(song, isVisible) {
        if (isVisible && song != null && song.path.isNotEmpty() && lyrics.isEmpty() && !isEditing) {
            isLoading = true
            lyrics = withContext(Dispatchers.IO) {
                try {
                    val f = File(song.path)
                    if (f.exists()) {
                        val audioFile = AudioFileIO.read(f)
                        val text = audioFile.tag?.getFirst(FieldKey.LYRICS) ?: ""
                        lrcLines = parseLrc(text)
                        text
                    } else ""
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
            }
            isLoading = false
        }
    }
    
    val listState = rememberLazyListState()
    val currentIndex = remember(lrcLines, currentPositionMs) {
        if (lrcLines.isEmpty()) -1 else {
            val idx = lrcLines.indexOfLast { it.timeMs <= currentPositionMs }
            if (idx == -1) 0 else idx
        }
    }

    LaunchedEffect(currentIndex, isVisible) {
        if (isVisible && currentIndex >= 0 && !isEditing) {
            val target = (currentIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xD9000000)) // Semi-transparent black background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Lyrics", tint = Color.White)
                    }

                    Text(
                        text = if (isEditing) "Edit Lyrics" else "Lyrics",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    IconButton(onClick = {
                        if (isEditing) {
                            // Save
                            scope.launch {
                                isLoading = true
                                saveStatus = ""
                                try {
                                    if (song != null) {
                                        val intentSender = saveLyricsToDisk(context, song, lyrics)
                                        if (intentSender == null) {
                                            saveStatus = ""
                                            isEditing = false
                                        } else {
                                            writeLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    saveStatus = "Failed to save: ${e.message}"
                                }
                                isLoading = false
                            }
                        } else {
                            isEditing = true
                        }
                    }) {
                        if (isEditing) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Save Lyrics", tint = accentColor)
                        } else {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Lyrics", tint = Color.White)
                        }
                    }
                }

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = accentColor)
                    } else if (isEditing) {
                        OutlinedTextField(
                            value = lyrics,
                            onValueChange = { 
                                lyrics = it
                                lrcLines = parseLrc(it)
                            },
                            modifier = Modifier.fillMaxSize(),
                            placeholder = { Text("Paste lyrics here...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = accentColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                        )
                    } else {
                        if (lyrics.isBlank()) {
                            Text(
                                text = "No lyrics embedded.\nTap the edit button to add some.",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (lrcLines.isNotEmpty()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 120.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(lrcLines) { index, line ->
                                    val isCurrent = index == currentIndex
                                    val textColor by animateColorAsState(
                                        targetValue = if (isCurrent) accentColor else Color.White.copy(alpha = 0.5f),
                                        animationSpec = tween(600, easing = FastOutSlowInEasing),
                                        label = "textColor"
                                    )
                                    val textWeight = FontWeight.Bold

                                    Text(
                                        text = line.text,
                                        color = textColor,
                                        fontSize = 18.sp,
                                        fontWeight = textWeight,
                                        lineHeight = 28.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .clickable {
                                                // Optional: seek to line.timeMs? Left out for now as it needs playerService
                                            }
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = lyrics,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (saveStatus.isNotEmpty()) {
                    Text(
                        text = saveStatus,
                        color = Color.Red,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

data class LrcLine(
    val timeMs: Long,
    val text: String
)

fun parseLrc(lrcText: String): List<LrcLine> {
    val result = mutableListOf<LrcLine>()
    val lines = lrcText.split("\n")
    val timeTagRegex = Regex("""\[\d{2}:\d{2}\.\d{2,3}\]""")
    val innerRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")

    for (line in lines) {
        val matches = timeTagRegex.findAll(line).toList()
        if (matches.isNotEmpty()) {
            val text = line.replace(timeTagRegex, "").trim()
            if (text.isNotEmpty()) {
                for (match in matches) {
                    val tag = match.value
                    val innerMatch = innerRegex.find(tag)
                    if (innerMatch != null) {
                        val min = innerMatch.groupValues[1].toLong()
                        val sec = innerMatch.groupValues[2].toLong()
                        val millisStr = innerMatch.groupValues[3]
                        val millis = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()
                        result.add(LrcLine(min * 60000 + sec * 1000 + millis, text))
                    }
                }
            }
        }
    }
    return result.sortedBy { it.timeMs }
}

suspend fun saveLyricsToDisk(context: android.content.Context, song: Song, lyrics: String): android.content.IntentSender? = withContext(Dispatchers.IO) {
    val originalFile = File(song.path)
    if (!originalFile.exists()) throw Exception("File not found")
    
    val extension = originalFile.extension.takeIf { it.isNotEmpty() } ?: "mp3"
    val tempFile = File(context.cacheDir, "temp_audio_${song.id}.$extension")
    originalFile.copyTo(tempFile, overwrite = true)
    
    val audioFile = AudioFileIO.read(tempFile)
    val tag = audioFile.tag ?: audioFile.createDefaultTag()
    tag.setField(FieldKey.LYRICS, lyrics)
    audioFile.tag = tag
    audioFile.commit()
    
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
    
    try {
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            FileInputStream(tempFile).use { it.copyTo(out) }
        }
        tempFile.delete()
        return@withContext null
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rse = e as? RecoverableSecurityException ?: throw e
            return@withContext rse.userAction.actionIntent.intentSender
        }
        throw e
    } catch (e: Exception) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            return@withContext e.userAction.actionIntent.intentSender
        }
        throw e
    }
}
