package com.kanagawa.yamada.inaho

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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

    // Animation Trigger State
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // --- Staggered Animations ---
    val alphaTitle by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "alphaTitle"
    )
    val offsetYTitle by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 30.dp,
        animationSpec = tween(durationMillis = 800, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "offsetTitle"
    )

    val alphaSubtitle by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "alphaSubtitle"
    )
    val offsetYSubtitle by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 30.dp,
        animationSpec = tween(durationMillis = 800, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "offsetSubtitle"
    )

    val alphaInput by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "alphaInput"
    )
    val offsetYInput by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 30.dp,
        animationSpec = tween(durationMillis = 800, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "offsetInput"
    )

    val alphaButton by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 550, easing = FastOutSlowInEasing),
        label = "alphaButton"
    )
    val offsetYButton by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 30.dp,
        animationSpec = tween(durationMillis = 800, delayMillis = 550, easing = FastOutSlowInEasing),
        label = "offsetButton"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 32.dp), // Increased padding for a sleeker, uncrowded layout
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Stylized Icon Header
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = "Welcome Sparkle",
            tint = accentColor,
            modifier = Modifier
                .size(48.dp)
                .alpha(alphaTitle)
                .offset(y = offsetYTitle)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = "Welcome to Inaho",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold, // Boosted emphasis
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(alphaTitle)
                .offset(y = offsetYTitle)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Subtitle
        Text(
            text = "How should we call you?",
            color = accentColor.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(alphaSubtitle)
                .offset(y = offsetYSubtitle)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Modernized Input Field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            shape = RoundedCornerShape(16.dp), // Softer, more modern corners
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
                unfocusedIndicatorColor = Color(0xFF333333), // Darker, subtler unfocused border
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
                .alpha(alphaInput)
                .offset(y = offsetYInput)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Enhanced Action Button
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
                .height(56.dp) // Slightly taller for better touch target & visual weight
                .alpha(alphaButton)
                .offset(y = offsetYButton),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                disabledContainerColor = Color(0xFF2A2A2A), // Blends cleanly into the dark background when disabled
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
                letterSpacing = 1.sp // Better button typography
            )
        }
    }
}