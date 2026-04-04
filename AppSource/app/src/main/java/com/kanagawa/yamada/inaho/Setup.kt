package com.kanagawa.yamada.inaho

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    settingsManager: SettingsManager,
    onComplete: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val settings by settingsManager.settingsFlow.collectAsState()
    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF120E0E)
    val accentColor = Color(0xFFB8355B)

    // Animation trigger
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // ─── Logo: spring scale punch-in + rotation ───────────────────────────────
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
            visibilityThreshold = 0.001f
        ),
        label = "logoScale"
    )
    val logoRotate by animateFloatAsState(
        targetValue = if (startAnimation) 0f else -30f,
        animationSpec = tween(durationMillis = 700, delayMillis = 80, easing = FastOutSlowInEasing),
        label = "logoRotate"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 80),
        label = "logoAlpha"
    )

    // ─── Title: clip-reveal slide up from below ────────────────────────────────
    val titleOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 48.dp,
        animationSpec = tween(durationMillis = 650, delayMillis = 280, easing = FastOutSlowInEasing),
        label = "titleOffsetY"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 280),
        label = "titleAlpha"
    )

    // ─── Subtitle: spring bounce scale + slide ────────────────────────────────
    val subtitleScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "subtitleScale"
    )
    val subtitleOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 600, delayMillis = 440, easing = FastOutSlowInEasing),
        label = "subtitleOffsetY"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 440),
        label = "subtitleAlpha"
    )

    // ─── Input: slide in from left + spring scale ─────────────────────────────
    val inputOffsetX by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else (-36).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "inputOffsetX"
    )
    val inputScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.97f,
        animationSpec = tween(durationMillis = 600, delayMillis = 580),
        label = "inputScale"
    )
    val inputAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 580),
        label = "inputAlpha"
    )

    // ─── Button: scale spring pop + slide up ──────────────────────────────────
    val buttonScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )
    val buttonOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 550, delayMillis = 700, easing = FastOutSlowInEasing),
        label = "buttonOffsetY"
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 700),
        label = "buttonAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Logo — spring scale punch-in with rotation
        Image(
            painter = painterResource(id = R.drawable.ic_inaho),
            contentDescription = "Inaho Logo",
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White)
                .alpha(logoAlpha)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    rotationZ = logoRotate
                }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Title — clip reveal: text slides up from behind a clipping boundary
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clipToBounds()
        ) {
            Text(
                text = "Welcome to Inaho",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffsetY)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subtitle — spring bounce scale + slide
        Text(
            text = "How should we call you?",
            color = accentColor.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(subtitleAlpha)
                .offset(y = subtitleOffsetY)
                .graphicsLayer {
                    scaleX = subtitleScale
                    scaleY = subtitleScale
                }
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Input — slides in from the left with spring scale
        OutlinedTextField(
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
                focusedIndicatorColor = accentColor,
                unfocusedIndicatorColor = Color(0xFF333333),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = accentColor,
                focusedContainerColor = bgColor,
                unfocusedContainerColor = bgColor
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (name.isNotBlank()) {
                        settingsManager.updateUserName(name.trim())
                        onComplete()
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(inputAlpha)
                .offset(x = inputOffsetX)
                .graphicsLayer {
                    scaleX = inputScale
                    scaleY = inputScale
                }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Button — scale spring pop from below
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    settingsManager.updateUserName(name.trim())
                    onComplete()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(buttonAlpha)
                .offset(y = buttonOffsetY)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                disabledContainerColor = Color(0xFF2A2A2A),
                contentColor = Color.White,
                disabledContentColor = Color(0xFF666666)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (name.isNotBlank()) 8.dp else 0.dp
            )
        ) {
            Text(
                text = "Let's Go",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}