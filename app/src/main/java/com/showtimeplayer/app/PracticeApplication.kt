package com.showtimeplayer.app

import android.app.Application
import com.showtimeplayer.data.db.PracticeDatabase
import com.showtimeplayer.data.repository.PresetRepositoryImpl
import com.showtimeplayer.data.repository.TrackRepositoryImpl
import com.showtimeplayer.data.preferences.PlaybackStatePreferences
import com.showtimeplayer.data.scanner.FolderPreferences
import com.showtimeplayer.data.scanner.MediaStoreScanner
import com.showtimeplayer.player.metronome.MetronomeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PracticeApplication : Application() {

    /**
     * Outlives any ViewModel, so work started from `onCleared` still completes — by then
     * `viewModelScope` is already cancelled.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: PracticeDatabase by lazy { PracticeDatabase.getInstance(this) }

    val folderPreferences: FolderPreferences by lazy { FolderPreferences(this) }

    val playbackStatePreferences: PlaybackStatePreferences by lazy {
        PlaybackStatePreferences(this)
    }

    val metronomeEngine: MetronomeEngine by lazy { MetronomeEngine() }

    val trackRepository: TrackRepositoryImpl by lazy {
        TrackRepositoryImpl(
            trackDao = database.trackDao(),
            scanner = MediaStoreScanner(this),
            folderPreferences = folderPreferences,
        )
    }

    val presetRepository: PresetRepositoryImpl by lazy {
        PresetRepositoryImpl(
            presetDao = database.presetDao(),
            metronomeLayerDao = database.metronomeLayerDao(),
            metronomeRegionDao = database.metronomeRegionDao(),
        )
    }
}
