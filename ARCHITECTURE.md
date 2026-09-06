# ShowtimePlayer — Android Architecture Specification

## 1. Product Overview

### Vision

A high-performance Android music player designed specifically for musicians. ShowtimePlayer provides low-latency time stretching, independent pitch shifting, section looping (A/B), and a sample-accurate count-in metronome — with persistent presets per song.

### Target Users

* Musicians learning songs by ear


* Music teachers demonstrating sections at different speeds


* Bands rehearsing with click tracks synced to audio


* Students transcribing complex solos



### Core Features

1. **Time Stretching** — Slow down or speed up songs (0.25x – 2.0x) while preserving pitch.


2. **Independent Pitch Shifting** — Transpose songs by semitones (-12 to +12) without altering speed.


3. **High-Precision Count-in Metronome** — Sample-accurate click engine playing prior to or alongside playback.


4. **Practice Presets** — Save and load speed, pitch, loop points, and metronome settings per track.


5. **MediaStore Scanner** — Fast background indexing of local audio assets with full metadata and artwork.


6. **A/B Region Looping** — Seamlessly repeat specific track sections.



### Non-Functional Requirements

| Metric | Target |
| --- | --- |
| **Audio Latency** | < 20ms for real-time DSP (Oboe / NDK)

 |
| **Metronome Accuracy** | Sample-accurate (< 1ms jitter)

 |
| **App Cold-Start** | < 600ms |
| **Memory Footprint** | < 80MB during playback |
| **Battery Drain** | Minimal during playback; near-zero idle background consumption |
| **Supported Formats** | MP3, M4A, FLAC, AAC, WAV, OGG

 |
| **Min Android SDK** | API level 24 (Android 7.0+)

 |
| **Target Android SDK** | API level 35 (Android 15+) |

---

## 2. Tech Stack & Architecture

### Tech Stack

* **Language:** Kotlin (100% Native)


* **UI Framework:** Jetpack Compose (Modern, declarative UI)


* **Asynchronous Execution:** Kotlin Coroutines + Reactive Flow


* **Database & Caching:** Room Database (SQLite abstraction)


* **Playback & Media Service:** Jetpack Media3 (ExoPlayer + `MediaSessionService`)


* **DSP & Metronome Engine:** C++20 via Android NDK using **Google Oboe** + **SoundTouch Library**

* **Media Indexing:** Android `MediaStore` ContentResolver


* **Image Loading:** Coil (Compose-first image caching)

### System Architecture

```
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                   │
│   Screens: Library | Player | Presets | Settings      │
├────────────────────────────────────────────────────────┤
│                 ViewModel / State Layer                │
│   AudioViewModel | MetronomeViewModel | PresetViewModel│
├────────────────────────────────────────────────────────┤
│                    Domain Services                     │
│   AudioEngineManager | MetronomeEngine | Scanner       │
├────────────────────────────────────────────────────────┤
│                   Data & Audio Layer                   │
│  Media3 Session │ Room Database │ Oboe DSP Audio Engine│
├────────────────────────────────────────────────────────┤
│                      Native Layer                      │
│      Android AudioServer (AAudio / OpenSL ES)          │
└────────────────────────────────────────────────────────┘

```

---

## 3. Directory Structure

```
com/ShowtimePlayer/
├── app/
│   ├── MainActivity.kt                  # Single-activity Compose entry point
│   └── PracticeApplication.kt           # App initialization (Hilt/Koin, Timber)
├── data/
│   ├── db/
│   │   ├── PracticeDatabase.kt          # Room Database instance
│   │   ├── dao/
│   │   │   ├── TrackDao.kt             # Track CRUD operations
│   │   │   └── PresetDao.kt            # Preset CRUD operations
│   │   └── entity/
│   │       ├── TrackEntity.kt           # Local track table
│   │       └── PresetEntity.kt          # Practice preset table
│   ├── repository/
│   │   ├── TrackRepositoryImpl.kt      # MediaStore & Room sync
│   │   └── PresetRepositoryImpl.kt     # Preset storage manager
│   └── scanner/
│       └── MediaStoreScanner.kt         # ContentResolver MediaStore indexer
├── player/
│   ├── service/
│   │   └── PlaybackService.kt           # Media3 MediaSessionService implementation
│   ├── engine/
│   │   ├── AudioEngine.kt               # Interface for audio operations
│   │   ├── ExoPlayerEngine.kt           # Jetpack Media3 implementation (Standard)
│   │   └── NativeOboeEngine.kt          # JNI Bridge to C++ DSP Engine
│   └── metronome/
│       └── MetronomeEngine.kt           # Native/High-precision metronome logic
├── native/                              # C++ Source (Android NDK)
│   ├── cpp/
│   │   ├── AudioEngineNative.cpp        # Oboe stream setup & callbacks
│   │   ├── PitchSpeedProcessor.cpp      # SoundTouch time stretch & pitch shift
│   │   └── MetronomeSynthesizer.cpp     # Sample-accurate click generator
│   └── CMakeLists.txt                   # CMake build script
├── ui/
│   ├── theme/                           # Compose Material3 theme definitions
│   ├── screens/
│   │   ├── library/                     # Track list & search
│   │   ├── player/                      # Controls, sliders, A/B looping
│   │   ├── presets/                     # Preset list & creation
│   │   └── settings/                    # App configurations
│   └── components/                      # Reusable Compose widgets
└── util/
    └── Extensions.kt                    # Kotlin helper extensions

```

---

## 4. Data Models & Database Schemas

### Room Entities

```kotlin
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,                        // content:// URI string
    val title: String?,                     // MediaStore / ID3 title
    val artist: String?,                    // Artist name
    val album: String?,                     // Album name
    val durationMs: Long,                   // Duration in milliseconds
    val format: String?,                    // mp3, flac, wav, etc.
    val albumArtUri: String?,               // Cached or MediaStore artwork URI
    val dateAdded: Long
)

@Entity(
    tableName = "presets",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"])]
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,                      // Target track
    val name: String,                       // User defined preset name
    val playbackRate: Float = 1.0f,         // 0.25f - 2.0f
    val pitchOffsetSemitones: Int = 0,      // -12 to +12 semitones
    val loopStartMs: Long? = null,          // A point (null = no loop)
    val loopEndMs: Long? = null,            // B point (null = no loop)
    val metronomeEnabled: Boolean = false,
    val metronomeBpm: Int = 120,
    val metronomeTimeSignatureNum: Int = 4,
    val metronomeTimeSignatureDenom: Int = 4,
    val metronomeCountInBars: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

```

---

## 5. Dual Audio Engine & NDK Integration

ShowtimePlayer supports two execution layers:

1. **Media3 ExoPlayer Engine (Standard Mode):** Handles background playback, Media3 notification session, Bluetooth media controls, and standard time-stretching.


2. **Oboe C++ NDK Engine (DSP/High-Performance Mode):** Bypasses high-level Java frameworks to offer sample-accurate metronome synchronization and zero-latency independent pitch shifting.



### C++ Native Engine (SoundTouch + Oboe Bridge)

```cpp
// Native AudioEngine setup via Google Oboe
#include <oboe/Oboe.h>
#include "SoundTouch.h"

class NativeAudioEngine : public oboe::AudioStreamDataCallback {
public:
    oboe::ManagedStream stream;
    soundtouch::SoundTouch soundTouch;
    
    void setupStream() {
        oboe::AudioStreamBuilder builder;
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setDataCallback(this)
               ->openManagedStream(stream);
               
        soundTouch.setSampleRate(44100);
        soundTouch.setChannels(2);
    }

    void setPitchOffset(int semitones) {
        soundTouch.setPitchSemiTones(semitones);
    }

    void setSpeedRate(float rate) {
        soundTouch.setTempo(rate);
    }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream, 
        void *audioData, 
        int32_t numFrames
    ) override {
        // Read uncompressed PCM, process via SoundTouch, write to audio output buffer
        return oboe::DataCallbackResult::Continue;
    }
};

```

### Kotlin JNI Bridge

```kotlin
class NativeOboeEngine {
    init {
        System.loadLibrary("ShowtimePlayer_native")
    }

    external fun initNativeEngine(sampleRate: Int, channelCount: Int)
    external fun setPitchShift(semitones: Int)
    external fun setPlaybackTempo(tempo: Float)
    external fun triggerMetronomeClick(accent: Boolean)
    external fun destroyNativeEngine()
}

```

---

## 6. MediaStore Music Scanner

The scanner runs via Kotlin Coroutines on `Dispatchers.IO` using Android's native `ContentResolver`.

```kotlin
class MediaStoreScanner(private val context: Context) {

    fun scanLocalAudio(): Flow<List<TrackEntity>> = flow {
        val trackList = mutableListOf<TrackEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()

                trackList.add(
                    TrackEntity(
                        uri = contentUri,
                        title = cursor.getString(titleCol),
                        artist = cursor.getString(artistCol),
                        album = cursor.getString(albumCol),
                        durationMs = cursor.getLong(durationCol),
                        format = "Audio",
                        albumArtUri = null,
                        dateAdded = System.currentTimeMillis()
                    )
                )

                if (trackList.size % 50 == 0) {
                    emit(trackList.toList())
                }
            }
        }
        emit(trackList)
    }.flowOn(Dispatchers.IO)
}

```

---

## 7. Sample-Accurate Metronome Engine

To support zero-jitter timing (< 1ms), the metronome generates synthetic sine-wave click pulses directly within the native C++ buffer rendering process.

```cpp
class MetronomeSynthesizer {
private:
    double phase = 0.0;
    const double sampleRate = 44100.0;

public:
    void renderClick(float* buffer, int numFrames, bool isAccent) {
        double frequency = isAccent ? 1200.0 : 800.0; // Hz
        double phaseIncrement = (2.0 * M_PI * frequency) / sampleRate;

        for (int i = 0; i < numFrames; ++i) {
            if (phase < M_PI * 10) { // Short 5ms pulse
                float sample = std::sin(phase) * std::exp(-phase * 0.1);
                buffer[i * 2] += sample;     // Left Channel
                buffer[i * 2 + 1] += sample; // Right Channel
                phase += phaseIncrement;
            } else {
                break;
            }
        }
    }

    void trigger() {
        phase = 0.0; // Reset oscillator phase
    }
};

```

---

## 8. Practice Presets & Auto-Save Lifecycle

```
User Adjusts UI Slider (Speed / Pitch / Loop / Metronome)
         │
         ▼
State updated in Playback ViewModel
         │
         ▼
Debounced State Flow (2000ms delay)
         │
         ▼
Room DB PresetDAO Update Operation (Async)

```

Presets allow instant restoration of complex practice configurations:

* If a track has an existing preset, loading the track immediately queries the `PresetDao`.


* The player restored state applies `playbackRate` to Media3/SoundTouch, sets up A/B looping markers in the player timeline, and initializes the metronome BPM and count-in parameters.



---

## 9. Jetpack Compose UI Screens

### Player Screen Structure

```kotlin
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AlbumArtDisplay(artworkUri = uiState.track?.albumArtUri)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = uiState.track?.title ?: "No Track", style = MaterialTheme.typography.headlineMedium)
        Text(text = uiState.track?.artist ?: "Unknown Artist", style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(24.dp))

        // A/B Progress Bar
        WaveformProgressBar(
            positionMs = uiState.currentPositionMs,
            durationMs = uiState.durationMs,
            loopStartMs = uiState.loopStartMs,
            loopEndMs = uiState.loopEndMs,
            onSeek = { viewModel.seekTo(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Time Stretch & Pitch Controls
        SpeedControlSlider(
            speed = uiState.playbackSpeed,
            onSpeedChange = { viewModel.setSpeed(it) }
        )

        PitchControlSlider(
            pitchSemitones = uiState.pitchOffsetSemitones,
            onPitchChange = { viewModel.setPitch(it) }
        )

        // Metronome Panel
        MetronomeControlPanel(
            metronomeState = uiState.metronomeState,
            onToggle = { viewModel.toggleMetronome() }
        )
    }
}

```

---

## 10. Performance Optimization Checklist

1. **JNI Crossing Minimization:** UI thread slider movements communicate with native code using simple primitive parameters, avoiding complex object allocations across the C++/Java boundary during continuous drag gestures.
2. **FlashList / LazyColumn Cell Reuse:** Use Compose `LazyColumn` keying (`key = { it.id }`) to avoid re-composing elements during list scrolling.
3. **Lockless Audio Processing:** The C++ audio callback utilizes atomic primitive pointers for state updates (e.g., speed, pitch, loop boundaries), guaranteeing zero locks or dynamic allocations on the native audio rendering thread.
4. **Media3 Session Management:** Service lifecycle is bound to `MediaSessionService`, ensuring optimal background operation while completely releasing hardware audio resources when paused or idle.