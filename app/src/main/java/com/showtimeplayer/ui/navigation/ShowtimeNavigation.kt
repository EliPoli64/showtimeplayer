package com.showtimeplayer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.showtimeplayer.data.db.entity.TrackEntity
import com.showtimeplayer.data.repository.PresetRepositoryImpl
import com.showtimeplayer.data.repository.PresetWithLayers
import com.showtimeplayer.ui.screens.library.LibraryScreen
import com.showtimeplayer.ui.screens.library.LibraryViewModel
import com.showtimeplayer.ui.screens.albums.AlbumsScreen
import com.showtimeplayer.ui.screens.albums.AlbumsViewModel
import com.showtimeplayer.ui.screens.player.PlayerScreen
import com.showtimeplayer.ui.screens.player.PlayerViewModel
import com.showtimeplayer.ui.screens.presets.PresetsScreen
import com.showtimeplayer.ui.screens.presets.PresetsViewModel
import com.showtimeplayer.ui.screens.settings.SettingsScreen
import com.showtimeplayer.ui.screens.settings.SettingsViewModel

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Library : Screen(
        route = "library",
        label = "Library",
        selectedIcon = Icons.AutoMirrored.Filled.QueueMusic,
        unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic,
    )

    data object Player : Screen(
        route = "player",
        label = "Player",
        selectedIcon = Icons.Filled.MusicNote,
        unselectedIcon = Icons.Outlined.MusicNote,
    )

    data object Albums : Screen(
        route = "albums",
        label = "Albums",
        selectedIcon = Icons.Filled.Album,
        unselectedIcon = Icons.Outlined.Album,
    )

    data object Presets : Screen(
        route = "presets",
        label = "Presets",
        selectedIcon = Icons.Filled.Equalizer,
        unselectedIcon = Icons.Outlined.Equalizer,
    )

    data object Settings : Screen(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    )
}

private val bottomNavItems = listOf(
    Screen.Library,
    Screen.Albums,
    Screen.Player,
    Screen.Presets,
    Screen.Settings,
)

@Composable
fun ShowtimeNavigation(
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    playerViewModel: PlayerViewModel,
    albumsViewModel: AlbumsViewModel,
    presetsViewModel: PresetsViewModel,
    presetRepository: PresetRepositoryImpl,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    fun navigateToPlayer() {
        navController.navigate(Screen.Player.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                            )
                        },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onTrackClick = { track ->
                        playerViewModel.playTrack(track)
                        navigateToPlayer()
                    },
                    onTrackAddToQueue = { track ->
                        playerViewModel.addToQueue(track)
                    },
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(viewModel = playerViewModel)
            }
            composable(Screen.Albums.route) {
                AlbumsScreen(
                    viewModel = albumsViewModel,
                    onTrackClick = { track ->
                        playerViewModel.playTrack(track)
                        navigateToPlayer()
                    },
                    onTrackAddToQueue = { track ->
                        playerViewModel.addToQueue(track)
                    },
                    onPlayAlbum = { tracks, startIndex ->
                        playerViewModel.playTrackAsQueue(tracks, startIndex)
                        navigateToPlayer()
                    },
                    onAddAlbumToQueue = { tracks ->
                        tracks.forEach { playerViewModel.addToQueue(it) }
                    },
                )
            }
            composable(Screen.Presets.route) {
                PresetsScreen(
                    viewModel = presetsViewModel,
                    onPresetPlay = { preset, track ->
                        scope.launch {
                            val layers = presetRepository.getLayersForPreset(preset.id)
                            val layersWithRegions = layers.map { layer ->
                                com.showtimeplayer.data.repository.MetronomeLayerWithRegions(
                                    layer = layer,
                                    regions = presetRepository.getRegionsForLayer(layer.id),
                                )
                            }
                            val presetWithLayers = PresetWithLayers(
                                preset = preset,
                                layers = layersWithRegions,
                            )
                            playerViewModel.playWithLayers(track, presetWithLayers)
                            navigateToPlayer()
                        }
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
