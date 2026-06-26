/*
Inaho Music Player - Inaho Music Player
Copyright (C) 2026 Kanagawa Yamada
*/

package com.kanagawa.yamada.inaho

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NavRail(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    amoledBlack: Boolean,
    accentColor: Color
) {
    val containerColor = if (amoledBlack) Color(0xFF0A0A0A) else Color(0xFF1E1414)
    val unselectedColor = Color(0xFF888888)

    NavigationRail(
        containerColor = containerColor,
        contentColor = Color.White,
        modifier = Modifier.width(80.dp)
    ) {
        NavigationRailItem(
            selected = currentScreen == AppScreen.HOME,
            onClick = { onNavigate(AppScreen.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = accentColor,
                selectedTextColor = accentColor,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = Color.Transparent
            )
        )

        NavigationRailItem(
            selected = currentScreen == AppScreen.LIST,
            onClick = { onNavigate(AppScreen.LIST) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Songs") },
            label = { Text("Songs") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = accentColor,
                selectedTextColor = accentColor,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = Color.Transparent
            )
        )

        NavigationRailItem(
            selected = currentScreen == AppScreen.PLAYLIST,
            onClick = { onNavigate(AppScreen.PLAYLIST) },
            icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlists") },
            label = { Text("Playlists") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = accentColor,
                selectedTextColor = accentColor,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = Color.Transparent
            )
        )

        NavigationRailItem(
            selected = currentScreen == AppScreen.SETTINGS,
            onClick = { onNavigate(AppScreen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = accentColor,
                selectedTextColor = accentColor,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = Color.Transparent
            )
        )
    }
}
