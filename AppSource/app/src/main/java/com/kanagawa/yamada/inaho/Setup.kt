/*
Inaho Music Player - Inaho Music Player
Copyright (C) 2026 Kanagawa Yamada
*/

package com.kanagawa.yamada.inaho

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    settingsManager: SettingsManager,
    onComplete: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val settings by settingsManager.settingsFlow.collectAsState()
    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val accentColor = Color(0xFFB8355B)
    val accentDim = Color(0xFF8A2844)
    val context = LocalContext.current

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
            photoUri = it
        }
    }

    // Animation trigger
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // ─── Avatar: smooth scale + fade ──────────────────────────────────────────
    val avatarScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.6f,
        animationSpec = tween(durationMillis = 700, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "avatarScale"
    )
    val avatarAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 100),
        label = "avatarAlpha"
    )

    // ─── Title: slide up + fade ───────────────────────────────────────────────
    val titleOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 32.dp,
        animationSpec = tween(durationMillis = 600, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "titleOffsetY"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 350),
        label = "titleAlpha"
    )

    // ─── Subtitle: slide up + fade ────────────────────────────────────────────
    val subtitleOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 550, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "subtitleOffsetY"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 500),
        label = "subtitleAlpha"
    )

    // ─── Input: slide up + fade ───────────────────────────────────────────────
    val inputOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 550, delayMillis = 650, easing = FastOutSlowInEasing),
        label = "inputOffsetY"
    )
    val inputAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 650),
        label = "inputAlpha"
    )

    // ─── Button: slide up + fade ──────────────────────────────────────────────
    val buttonOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 500, delayMillis = 800, easing = FastOutSlowInEasing),
        label = "buttonOffsetY"
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 800),
        label = "buttonAlpha"
    )

    // ─── Glow ring pulse ──────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .safeDrawingPadding()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ─── Avatar with glow ring ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .alpha(avatarAlpha)
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                },
            contentAlignment = Alignment.Center
        ) {
            // Glow ring behind avatar
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                accentColor.copy(alpha = glowAlpha),
                                accentDim.copy(alpha = glowAlpha * 0.5f),
                                accentColor.copy(alpha = glowAlpha)
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Avatar image
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A))
                    .clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "User Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_inaho),
                        contentDescription = "Inaho Logo",
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                    )
                }
            }

            // Camera badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .border(2.dp, bgColor, CircleShape)
                    .clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "Change Photo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tap to set your photo",
            color = Color(0xFF666666),
            fontSize = 12.sp,
            modifier = Modifier.alpha(avatarAlpha)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ─── Title ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clipToBounds()
        ) {
            Text(
                text = "Welcome to Inaho",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffsetY)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── Subtitle ────────────────────────────────────────────────────────
        Text(
            text = "How should we call you?",
            color = Color(0xFFAAAAAA),
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(subtitleAlpha)
                .offset(y = subtitleOffsetY)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ─── Name input ───────────────────────────────────────────────────────
        TextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "User Icon",
                    tint = if (name.isBlank()) Color(0xFF555555) else accentColor
                )
            },
            placeholder = {
                Text(
                    text = "Enter your name",
                    color = Color(0xFF555555)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = accentColor,
                focusedContainerColor = Color(0xFF1E1E1E),
                unfocusedContainerColor = Color(0xFF1A1A1A)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (name.isNotBlank()) {
                        settingsManager.updateUserName(name.trim())
                        settingsManager.updateUserPhotoUri(photoUri?.toString())
                        onComplete()
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .alpha(inputAlpha)
                .offset(y = inputOffsetY)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Continue button ──────────────────────────────────────────────────
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    settingsManager.updateUserName(name.trim())
                    settingsManager.updateUserPhotoUri(photoUri?.toString())
                    onComplete()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .alpha(buttonAlpha)
                .offset(y = buttonOffsetY),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                disabledContainerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                disabledContentColor = Color(0xFF555555)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp
            )
        ) {
            Text(
                text = "Let's Go",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}