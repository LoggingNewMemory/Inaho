package com.kanagawa.yamada.halo.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "[Song Name]", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        // Song Cover Placeholder
        Box(
            modifier = Modifier
                .size(250.dp)
                .background(Color.LightGray, shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("[SONG COVER]")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Slider Placeholder
        var sliderPosition by remember { mutableStateOf(0f) }
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            modifier = Modifier.fillMaxWidth()
        )

        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { /* Previous */ }) { Text("P") }
            Button(onClick = { /* Play */ }) { Text("Play") }
            Button(onClick = { /* Next */ }) { Text("N") }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Up Next Section
        Divider()
        Text(
            text = "Up Next: [Song Name]",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
    }
}