# ShowtimePlayer

A native Android music player built for musicians. It does the usual library/album/player
thing, then adds a **DAW-style practice editor** on top of each song: multiple metronome
layers with draggable count-in blocks, a scrubbable playhead, a movable song clip, per-click
volume, and pitch/speed practice tools, saved as reusable **presets** per track.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the longer design spec.

---

## Features

**Library**
- Scans on-device audio via `MediaStore` (background, coroutine-based).
- Restrict scanning to chosen folders (stored with DataStore).
- Re-scan refreshes tracks in place (same row ids), so per-track presets survive a rescan.
- Play a track or add it to the queue.

**Albums**
- Groups scanned tracks by album; play an album or enqueue it.

**Player**
- Album art (Coil), title/artist, seekable progress bar, prev/play-pause/next.
- Queue bottom sheet: reorder, remove, clear, jump to item.
- Active-preset banner (name + summary) shown on the now-playing screen.
- "Count-in running" indicator while a preset's count-in is in progress; the song audio
  starts after any leading count-in region, and seeking re-syncs the metronome to the
  new playback position.

**Presets, DAW-style metronome editor**
- Up to **4 metronome layers** per preset.
- **Count-in regions** on each layer: BPM, time signature, count-in bars, volume.
  - Long-press + drag to move a region (including across layers).
  - Long-press empty lane space to add a region there.
  - Tap a region to edit it in a bottom sheet (with live **tap tempo** and volume).
- **Scrubbable red playhead**: drag it anywhere; tap anywhere on the timeline to seek.
- **Movable song clip**: drag the waveform to set a lead-in offset.
- **Song settings** (bottom sheet):
  - Pitch shift, −12…+12 semitones.
  - Speed, 25%–200%.
  - **Time-stretch** (pitch preserved) or **tape** (pitch follows speed).
- Everything is persisted per song and replayed from the main Player.

**Settings**
- Manage the folders that get scanned.

---

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.0.21, Java 17 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2024.12.01) |
| Async | Kotlin Coroutines / Flow |
| Persistence | Room 2.6.1 (`practice.db`, currently v6), DataStore Preferences |
| Playback | Media3 (ExoPlayer + `MediaSessionService`) 1.5.1 |
| Images | Coil 2.6.0 |
| Navigation | Navigation Compose 2.8.5 |
| DI | Manual (via `PracticeApplication`) |
| Native audio | C++20 + Google Oboe 1.10.0 (via prefab) |
| Build | Android Gradle Plugin 8.5.2, KSP, version catalog (`gradle/libs.versions.toml`) |

- `minSdk 24`, `compileSdk`/`targetSdk 35`
- Single Gradle module: `:app`

---

## Requirements

- Android Studio (or the Android command-line SDK) with **SDK 35** and **Build Tools**.
- **NDK** + **CMake 3.22.1** (the native metronome engine is built from `app/src/main/cpp`).
- A `local.properties` pointing at your SDK (`sdk.dir=...`), or `ANDROID_HOME` set.
- Oboe and all Kotlin dependencies resolve from Google Maven / Maven Central, no manual
  downloads.

---

## Build & run

```bash
# Debug APK
./gradlew assembleDebug

# Install on a connected device/emulator
./gradlew installDebug

# Clean
./gradlew clean
```

There is also a convenience script that installs and launches the app:

```bash
./start
```

### Runtime permissions
The app requests `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (≤ API 32) to
index local audio, plus foreground-service/media-playback and wake-lock permissions for
background playback.

---

## Project structure

```
app/src/main/
├── java/com/showtimeplayer/
│   ├── app/                     # Application (manual DI), MainActivity
│   ├── data/
│   │   ├── db/                  # Room: PracticeDatabase, entities, DAOs
│   │   ├── repository/          # TrackRepositoryImpl, PresetRepositoryImpl
│   │   └── scanner/             # MediaStoreScanner, FolderPreferences (DataStore)
│   ├── player/
│   │   ├── service/             # PlaybackService (MediaSessionService)
│   │   ├── engine/              # NativeOboeEngine (JNI bridge), engine stubs
│   │   └── metronome/           # MetronomeEngine (multi-stream click mixer facade)
│   ├── ui/
│   │   ├── navigation/          # Bottom-nav + NavHost
│   │   ├── screens/             # library, albums, player, presets, settings
│   │   ├── components/          # waveform / beat-picker widgets
│   │   └── theme/
│   └── util/                    # AudioDecoder (waveform), extensions
└── cpp/                         # C++20 + Oboe native metronome
    ├── CMakeLists.txt
    ├── AudioEngineNative.cpp    # Oboe stream + JNI entry points
    ├── MetronomeSynthesizer.h   # multi-stream click renderer (mutex-guarded)
    ├── MetronomeSynthesizer.cpp # live streams + pre-rendered count-in clips
    └── PitchSpeedProcessor.*    # SoundTouch time-stretch/pitch (stub)
```

---

## Data model

Room database `practice.db` (`PracticeDatabase`, `exportSchema = false`):

- **`tracks`**, indexed local audio: `uri`, title/artist/album, duration, album art, etc.
- **`presets`**, per-song practice config: name, `playbackRate`,
  `pitchOffsetSemitones`, `pitchFollowsSpeed`, `loopStartMs`/`loopEndMs`,
  `songOffsetMs`.
- **`metronome_layers`**, up to 4 layers per preset (`presetId` FK, name, enabled,
  sort order).
- **`metronome_regions`**, count-in blocks (`layerId` FK): `startMs`, bpm, time
  signature, count-in bars, volume.

Foreign keys cascade on delete. Schema upgrades since v2 are real `Migration`s (2→3 … 5→6)
that preserve data; `fallbackToDestructiveMigration()` is only a last-resort fallback for
unknown version gaps.

---

## Native audio engine

The metronome does not use per-click `AudioTrack`s. `AudioEngineNative.cpp` opens a
low-latency Oboe output stream (44.1 kHz, stereo float). `MetronomeSynthesizer` mixes
count-ins as **pre-rendered PCM clips** (`RenderedClip`) positioned sample-accurately by
the engine's audio sample counter, with live oscillator streams kept as a fallback. The
mix passes through a soft-clip limiter so per-click volume up to 200% gets louder without
hard clipping. Streams/clips can be added/updated/removed while the audio callback runs;
access is guarded by a mutex. Kotlin talks to it through `NativeOboeEngine` ->
`MetronomeEngine`.

Song playback uses ExoPlayer. Pitch shifting and speed use ExoPlayer's built-in
time-stretch/pitch (Sonic); `PitchSpeedProcessor.cpp` is a placeholder for an optional
higher-fidelity SoundTouch DSP path.

---

## Notes / limitations

- Known upgrades (v2→v6) run as data-preserving migrations; only unknown version gaps
  fall back to a destructive rebuild.
- The native SoundTouch time-stretch/pitch processor is **not** implemented; pitch/speed are
  handled by ExoPlayer.
- No unit or instrumentation test suite is checked in yet.

---

## Development conventions

- Compose + Material 3 only; the app uses the default Material 3 (purple) color scheme.
- One `ViewModel` per screen; Room is the single source of truth, exposed as `Flow`.
- Keep the DAW editor's hot state isolated (e.g. speed/pitch live in a separate flow so
  dragging sliders doesn't recompose the timeline).
