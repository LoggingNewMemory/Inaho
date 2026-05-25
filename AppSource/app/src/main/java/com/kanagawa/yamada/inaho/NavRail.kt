/*
Inaho Music Player - A Music Player that inspired with Ochinai Inaho
Copyright (C) 2026 Kanagawa Yamada 
This program is free software: you can redistribute it and/or modify it under the terms of 
the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. 

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details. 
You should have received a copy of the GNU General Public License along with this program. 

If not, see https://www.gnu.org/licenses/.
*/

package com.kanagawa.yamada.inaho

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.QueueMusic
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
            icon = { Icon(Icons.Default.List, contentDescription = "Songs") },
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
            icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Playlists") },
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
