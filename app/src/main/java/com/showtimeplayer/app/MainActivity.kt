package com.showtimeplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.showtimeplayer.ui.navigation.ShowtimeNavigation
import com.showtimeplayer.ui.screens.albums.AlbumsViewModel
import com.showtimeplayer.ui.screens.library.LibraryViewModel
import com.showtimeplayer.ui.screens.player.PlayerViewModel
import com.showtimeplayer.ui.screens.settings.SettingsViewModel
import com.showtimeplayer.ui.theme.ShowtimePlayerTheme

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModel.Factory((application as PracticeApplication).trackRepository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as PracticeApplication
        SettingsViewModel.Factory(app, app.folderPreferences, app.trackRepository)
    }

    private val playerViewModel: PlayerViewModel by viewModels {
        PlayerViewModel.Factory(application)
    }

    private val albumsViewModel: AlbumsViewModel by viewModels {
        AlbumsViewModel.Factory((application as PracticeApplication).trackRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShowtimePlayerTheme {
                ShowtimeNavigation(
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel,
                    playerViewModel = playerViewModel,
                    albumsViewModel = albumsViewModel,
                )
            }
        }
    }
}
