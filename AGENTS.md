# AGENTS.md

Guidance for AI coding agents working in **ShowtimePlayer**.

- Product spec: [`README.md`](README.md)
- Design/architecture spec: [`ARCHITECTURE.md`](ARCHITECTURE.md)

---

## What this is

A native Android music player built for musicians. It indexes on-device audio,
plays it via Media3/ExoPlayer, and layers a **DAW-style practice editor** on each
song: multiple metronome layers with draggable count-in regions, a scrubbable
playhead, a movable song clip, per-click volume, and pitch/speed practice tools,
saved as per-track **presets**.

Single-activity, single-module, all Compose.

---

## Tech stack (keep versions in `gradle/libs.versions.toml`)

| Area | Choice |
|---|---|
| Language | Kotlin 2.0.21, JVM target 17 |
| UI | Jetpack Compose + Material 3 (Compose BOM `2025.06.01`) |
| Async | Coroutines / Flow |
| Persistence | Room 2.6.1 (`practice.db`, **schema version 6**), DataStore Preferences |
| Playback | Media3 (ExoPlayer + `MediaSessionService`) 1.5.1 |
| Images | Coil 2.6.0 |
| Navigation | Navigation Compose 2.8.5 |
| DI | Manual — wired in `app/PracticeApplication.kt` (no Hilt/Koin) |
| Native audio | C++20 + Google Oboe 1.10.0 (prefab) via CMake 3.22.1 |
| Build | AGP 8.5.2, KSP, single module `:app`, `minSdk 24`, `compileSdk`/`targetSdk 35` |

Use the **version catalog** (`libs.versions.toml`) for dependencies; don't hardcode
versions in `app/build.gradle.kts` unless there's no catalog entry (Oboe is the one
hardcoded dep).

---

## Build & run

```bash
./gradlew assembleDebug        # build debug APK
./gradlew installDebug         # install to a connected device/emulator
./gradlew clean                # clean
./start                        # convenience: install + launch
```

Requirements: Android SDK 35 + Build Tools, **NDK** (27.2.x) + **CMake 3.22.1**
for the native metronome, and `local.properties` with `sdk.dir=...` (or
`ANDROID_HOME`).

There is **no test suite** checked in yet. If you add tests, put unit tests under
`app/src/test/` and instrumented tests under `app/src/androidTest/`.

---

## Project layout

```
app/src/main/
├── java/com/showtimeplayer/
│   ├── app/                     # PracticeApplication (manual DI), MainActivity
│   ├── data/
│   │   ├── db/                  # PracticeDatabase (Room), entities/, dao/
│   │   ├── repository/          # TrackRepositoryImpl, PresetRepositoryImpl
│   │   ├── scanner/             # MediaStoreScanner, FolderPreferences (DataStore)
│   │   └── preferences/         # PlaybackStatePreferences
│   ├── player/
│   │   ├── service/             # PlaybackService (MediaSessionService)
│   │   ├── engine/              # AudioEngine (iface), ExoPlayerEngine, NativeOboeEngine (JNI)
│   │   └── metronome/           # MetronomeEngine (multi-stream click mixer facade)
│   ├── ui/
│   │   ├── navigation/          # ShowtimeNavigation (bottom nav + NavHost)
│   │   ├── screens/             # library, albums, player, presets, settings
│   │   ├── components/          # WaveformView, BeatWheel, BeatPositionPicker, ...
│   │   └── theme/               # Theme.kt (custom dark-grey / greyish-blue palette)
│   └── util/                    # AudioDecoder (waveform), Extensions.kt
└── cpp/                         # C++20 + Oboe native metronome (CMakeLists.txt)
    ├── AudioEngineNative.cpp    # Oboe stream + JNI entry points
    ├── MetronomeSynthesizer.*   # multi-stream click renderer (mutex-guarded)
    └── PitchSpeedProcessor.*    # SoundTouch time-stretch/pitch (STUB — see below)
```

---

## Conventions & patterns

**UI**
- Compose + Material 3 only. There are **no XML layouts** — `res/values/themes.xml`
  is just a base View theme for the manifest (`android:Theme.Material.NoActionBar`).
- **One `ViewModel` per screen**, created via `ViewModelProvider.Factory` (manual DI,
  pulling repositories off `PracticeApplication`). See `MainActivity.kt`.
- Read VM state with `collectAsStateWithLifecycle()`.
- Prefer `MaterialTheme.colorScheme.*` over hardcoded `Color(...)`. The theme is a
  custom dark-grey base with a greyish-blue primary and light-grey accents
  (`ui/theme/Theme.kt`); don't reintroduce the default purple or stray colors.
  - Exception: the DAW timeline intentionally draws white tick/labels and a red
    `error`-colored playhead on colored blocks (`DawTimeline.kt`, `WaveformView.kt`).
  - Per-layer metronome tints live in `LAYER_COLORS` (`presets/PresetsViewModel.kt`).

**Navigation / insets**
- `ShowtimeNavigation.kt` has the **outer** `Scaffold` (bottom `NavigationBar` +
  `NavHost`). Each screen has its own **inner** `Scaffold` (top app bar, snackbar).
- The inner scaffolds set `contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)`
  so they don't re-apply system insets on top of the outer scaffold (which already
  reserves the status bar and bottom nav bar). Keep this when adding screens, or the
  gap above the bottom tab bar comes back.

**Data**
- Room is the single source of truth, exposed as `Flow`. DAOs in `data/db/dao/`,
  entities in `data/db/entity/`.
- **Schema version is 6.** Known upgrades (2→3 … 5→6) are real data-preserving
  `Migration`s in `PracticeDatabase.kt`. If you change the schema, **bump the version
  and add a `Migration`**; `fallbackToDestructiveMigration()` is only a last resort
  for unknown gaps. Don't silently bump without a migration.
- MediaStore scanning is coroutine-based on `Dispatchers.IO`; re-scan preserves track
  row ids so per-track presets survive.

**Playback**
- Song playback is ExoPlayer (`ExoPlayerEngine`). Pitch/speed use ExoPlayer's built-in
  Sonic time-stretch. The native `PitchSpeedProcessor.cpp` (SoundTouch) is a **stub /
  not implemented** — don't rely on it.
- The metronome uses the native Oboe engine via `NativeOboeEngine` (JNI) →
  `MetronomeEngine`, with sample-accurate pre-rendered count-in clips. Native code is
  mutex-guarded; add/modify streams carefully w.r.t. the audio callback thread.

**DAW editor state**
- Keep the DAW editor's hot state (speed/pitch sliders) isolated from the timeline so
  dragging sliders doesn't recompose the timeline (see `PresetsViewModel` /
  `SongSettingsState`).

---

## Do / Don't

- ✅ Route colors through `MaterialTheme.colorScheme`; extend the theme in `Theme.kt`.
- ✅ Add dependencies through `gradle/libs.versions.toml`.
- ✅ Preserve data — add Room migrations, never rely on destructive migration.
- ✅ Match the existing file-per-screen / one-VM-per-screen layout.
- ❌ Don't hardcode Compose/Material versions in `build.gradle.kts`.
- ❌ Don't remove `contentWindowInsets = WindowInsets(0.dp, …)` from inner scaffolds.
- ❌ Don't wire up DI frameworks; extend `PracticeApplication` manually instead.
- ❌ Don't assume the native SoundTouch pitch/speed path works (it's a stub).
