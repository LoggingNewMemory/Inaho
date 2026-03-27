package com.kanagawa.yamada.halo.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MusicListScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { /* Sort */ }) { Text("[Sort]") }
            TextButton(onClick = { /* Refresh */ }) { Text("[Refresh]") }
            TextButton(onClick = { /* Search */ }) { Text("[Search]") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable List of Songs
        LazyColumn {
            items(10) { // Generates 10 placeholder items
                SongListItem()
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SongListItem() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini Cover
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color.LightGray, RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Title and Details
        Column(modifier = Modifier.weight(1f)) {
            Text("Song Title", style = MaterialTheme.typography.bodyLarge)
            Text("Song Artist | Song Album", style = MaterialTheme.typography.bodySmall)
        }

        // Duration
        Text("3:45", style = MaterialTheme.typography.bodySmall)
    }
}