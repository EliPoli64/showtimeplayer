package com.showtimeplayer.app

import android.app.Application
import com.showtimeplayer.data.db.PracticeDatabase
import com.showtimeplayer.data.repository.TrackRepositoryImpl
import com.showtimeplayer.data.scanner.FolderPreferences
import com.showtimeplayer.data.scanner.MediaStoreScanner

class PracticeApplication : Application() {
    val database: PracticeDatabase by lazy { PracticeDatabase.getInstance(this) }

    val folderPreferences: FolderPreferences by lazy { FolderPreferences(this) }

    val trackRepository: TrackRepositoryImpl by lazy {
        TrackRepositoryImpl(
            trackDao = database.trackDao(),
            scanner = MediaStoreScanner(this),
            folderPreferences = folderPreferences,
        )
    }
}
