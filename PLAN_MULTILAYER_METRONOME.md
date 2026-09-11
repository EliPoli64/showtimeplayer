# Multi-Layer Metronome with DAW Timeline Editor

## Goal

Transform the single-count-in metronome into a multi-layer system where users can add independent metronome tracks to any part of a song. Replace the step wizard with a DAW-like timeline editor. Implement the audio mixer in C++ via Oboe.

## Constraints

- Max 4 layers per preset
- Max 16 regions per layer (unenforced in UI, but sensible limit)
- Bottom sheet for region editing
- Discrete layer colors for visual distinction
- Destructive migration acceptable (existing presets will be recreated)

---

## Phase 1: Data Model

### New entities

**`data/db/entity/MetronomeLayer.kt`** (create)

```kotlin
@Entity(
    tableName = "metronome_layers",
    foreignKeys = [ForeignKey(PresetEntity::class, ["id"], ["presetId"], CASCADE)],
    indices = [Index("presetId")],
)
data class MetronomeLayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val name: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)
```

**`data/db/entity/MetronomeRegion.kt`** (create)

```kotlin
@Entity(
    tableName = "metronome_regions",
    foreignKeys = [ForeignKey(MetronomeLayer::class, ["id"], ["layerId"], CASCADE)],
    indices = [Index("layerId")],
)
data class MetronomeRegion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val layerId: Long,
    val startMs: Long,
    val endMs: Long? = null,
    val bpm: Int = 120,
    val timeSignatureNum: Int = 4,
    val timeSignatureDenom: Int = 4,
    val countInBars: Int = 0,
    val sortOrder: Int = 0,
)
```

### Modify existing

**`data/db/entity/PresetEntity.kt`** — Remove: `metronomeEnabled`, `metronomeBpm`, `metronomeTimeSignatureNum`, `metronomeTimeSignatureDenom`, `metronomeCountInBars`. Keep `loopStartMs` (song-level beat-1 marker).

**`data/db/PracticeDatabase.kt`** — Add `MetronomeLayer::class`, `MetronomeRegion::class` to entities array. Bump version to 3.

### New DAOs

**`data/db/dao/MetronomeLayerDao.kt`** (create)
- `observeForPreset(presetId: Long): Flow<List<MetronomeLayer>>`
- `getForPreset(presetId: Long): List<MetronomeLayer>`
- `insert(layer: MetronomeLayer): Long`
- `update(layer: MetronomeLayer)`
- `delete(layer: MetronomeLayer)`
- `deleteById(id: Long)`

**`data/db/dao/MetronomeRegionDao.kt`** (create)
- `observeForLayer(layerId: Long): Flow<List<MetronomeRegion>>`
- `getForLayer(layerId: Long): List<MetronomeRegion>`
- `observeForPreset(presetId: Long): Flow<List<MetronomeRegion>>` — joins through layers
- `insert(region: MetronomeRegion): Long`
- `update(region: MetronomeRegion)`
- `delete(region: MetronomeRegion)`
- `deleteById(id: Long)`

### Repository

**`data/repository/PresetRepositoryImpl.kt`** — Add:
- `observePresetWithLayers(presetId: Long): Flow<PresetWithLayers>`
- `insertLayer(layer: MetronomeLayer): Long`
- `updateLayer(layer: MetronomeLayer)`
- `deleteLayer(layer: MetronomeLayer)`
- `insertRegion(region: MetronomeRegion): Long`
- `updateRegion(region: MetronomeRegion)`
- `deleteRegion(region: MetronomeRegion)`

### New composite types

```kotlin
data class MetronomeLayerWithRegions(
    val layer: MetronomeLayer,
    val regions: List<MetronomeRegion>,
)

data class PresetWithLayers(
    val preset: PresetEntity,
    val layers: List<MetronomeLayerWithRegions>,
)
```

---

## Phase 2: DAW Timeline Editor UI

### Screen structure

The `PresetsScreen.kt` AnimatedContent wizard is replaced by two modes:

1. **List mode** (`isEditing == false`): Existing preset list + FAB. Mostly unchanged.
2. **Editor mode** (`isEditing == true`): DAW timeline editor.

#### Editor layout (top to bottom)

```
┌─────────────────────────────────────┐
│  TopAppBar: "Edit Preset" + Back    │
├─────────────────────────────────────┤
│  Preset name field                  │
├─────────────────────────────────────┤
│  Waveform timeline (reuse           │
│  BeatWheel/BeatPositionPicker)      │
│  + playhead + zoom/scroll           │
├─────────────────────────────────────┤
│  Layer 1: [===region 1===] [region2]│  ← colored blocks
│  Layer 2: [====region 1====]        │
│  Layer 3: empty (+ Add Region)      │
│  + Add Layer button                 │
├─────────────────────────────────────┤
│  [Save Preset] button               │
└─────────────────────────────────────┘
```

#### Tap region → bottom sheet

Bottom sheet shows: BPM field, time signature picker, count-in bars slider (0-8), delete button.

### Files to create

| File | Purpose |
|---|---|
| `ui/screens/presets/MetronomeTrackRow.kt` | Single layer row: name, enabled toggle, region blocks, delete |
| `ui/screens/presets/RegionBlock.kt` | Colored rectangle for a region on the timeline |
| `ui/screens/presets/RegionEditSheet.kt` | Bottom sheet to edit region BPM/time sig/count-in |
| `ui/screens/presets/LayerHeader.kt` | Layer name + controls row |

### Files to rewrite

**`ui/screens/presets/PresetsScreen.kt`** — Replace wizard with list+editor toggle.

**`ui/screens/presets/PresetsViewModel.kt`** — New state model:

```kotlin
data class PresetsUiState(
    val presets: List<PresetWithTrack>,
    val allTracks: List<TrackEntity>,
    val isEditing: Boolean,
    val editingPreset: PresetEntity?,
    val presetName: String,
    val layers: List<MetronomeLayerWithRegions>,
    val selectedRegion: MetronomeRegion?,
    val isPreviewPlaying: Boolean,
    val isSongPlaying: Boolean,
    val visibleStartMs: Float,
    val visibleDurationMs: Float,
    val waveformAmplitudes: List<Float>,
)
```

New ViewModel methods:
- `addLayer()` — creates a new layer with default name "Layer N"
- `removeLayer(layerId)`
- `toggleLayerEnabled(layerId)`
- `addRegion(layerId)` — creates a region at position 0 with default settings
- `updateRegion(region)` — called from bottom sheet
- `removeRegion(regionId)`
- `selectRegion(region)` — opens bottom sheet
- `clearSelectedRegion()` — closes bottom sheet

### Files to delete

All 7 step-enum screens are replaced by the editor:
- `AddPresetTrackScreen.kt`
- `AddPresetTempoScreen.kt`
- `AddPresetTimeSignatureScreen.kt`
- `AddPresetBeatScreen.kt`
- `AddPresetReviewScreen.kt`
- `EditPresetScreen.kt`
- `EditPresetBeatScreen.kt`
- `EditPresetTempoScreen.kt`

### Layer colors

```kotlin
val LAYER_COLORS = listOf(
    Color(0xFF4CAF50),  // Green
    Color(0xFF2196F3),  // Blue
    Color(0xFFFF9800),  // Orange
    Color(0xFFE91E63),  // Pink
)
```

Assigned by index: layer 0 = green, layer 1 = blue, etc.

---

## Phase 3: NDK/Oboe Audio Mixer

### Dependencies

**`app/build.gradle.kts`** — Add:
```kotlin
implementation("com.google.oboe:oboe:1.9.2")
```

### CMake

**`app/src/main/cpp/CMakeLists.txt`** — Enable Oboe:
```cmake
find_package(oboe REQUIRED CONFIG)
target_link_libraries(showtimeplayer_native ${log-lib} android oboe::oboe)
```

Remove `OpenSLES` from link libraries (Oboe replaces it).

### C++ implementation

**`app/src/main/cpp/MetronomeSynthesizer.cpp`** — Full mixer:

```cpp
struct MetronomeStream {
    int id;
    bool active;
    float bpm;
    int timeSigNum;
    int timeSigDenom;
    float accentFreq;   // 1000 Hz
    float normalFreq;   // 800 Hz
    // Oscillator state
    double phase;
    double phaseIncrement;
    float amplitude;
    // Timing state
    int64_t currentSample;
    int64_t beatIntervalSamples;
    int beatCount;
    int clicksPerAccent;
    bool inCountIn;
    int64_t countInEndSample;
};

class MetronomeSynthesizer {
    std::vector<MetronomeStream> streams;
    double sampleRate;

    void renderClick(float* buffer, int numFrames);
    void addStream(int id, float bpm, int timeSigNum, int timeSigDenom);
    void removeStream(int id);
    void updateStream(int id, float bpm, int timeSigNum, int timeSigDenom);
    void triggerAll();  // Reset all streams for sync
};
```

Render logic per stream:
- Track `currentSample` (incremented each frame)
- At each beat boundary (`currentSample % beatIntervalSamples == 0`), trigger a click
- Click = sine wave with exponential decay (matching existing `playClick` envelope)
- Accent on beat 0 of each bar (`beatCount % timeSigNum == 0`)
- Sum all active streams into the output buffer

**`app/src/main/cpp/AudioEngineNative.cpp`** — Oboe stream:

```cpp
#include <oboe/Oboe.h>

class AudioEngine : public oboe::AudioStreamDataCallback {
    oboe::ManagedStream stream;
    MetronomeSynthesizer synth;

    void start();
    void stop();
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void*, int32_t) override;
};
```

- Opens low-latency output (44100 Hz, stereo float, PerformanceMode::LowLatency)
- `onAudioReady` calls `synth.renderClick()` to fill buffer
- JNI functions exposed to Kotlin

### Kotlin JNI bridge

**`player/engine/NativeOboeEngine.kt`** — Full implementation:

```kotlin
class NativeOboeEngine {
    init { System.loadLibrary("showtimeplayer_native") }

    external fun nativeInit(sampleRate: Int, channelCount: Int)
    external fun nativeStart()
    external fun nativeStop()
    external fun nativeDestroy()
    external fun nativeAddStream(id: Int, bpm: Int, timeSigNum: Int, timeSigDenom: Int)
    external fun nativeRemoveStream(id: Int)
    external fun nativeUpdateStream(id: Int, bpm: Int, timeSigNum: Int, timeSigDenom: Int)
    external fun nativeTriggerAll()
}
```

### MetronomeEngine wrapper

**`player/metronome/MetronomeEngine.kt`** — Kotlin facade:

```kotlin
class MetronomeEngine(private val nativeEngine: NativeOboeEngine) {
    fun start(layers: List<MetronomeLayerConfig>)
    fun stop()
    fun updateLayer(id: Int, config: MetronomeLayerConfig)
    fun addLayer(id: Int, config: MetronomeLayerConfig)
    fun removeLayer(id: Int)
}
```

---

## Phase 4: Player Integration

### PlayerViewModel changes

**`ui/screens/player/PlayerViewModel.kt`**:

- Rename `playWithCountIn(track, preset)` → `playWithLayers(track, presetWithLayers: PresetWithLayers)`
- For each enabled layer with regions:
  1. Create a `MetronomeLayerConfig` per region
  2. Add streams to `MetronomeEngine`
  3. Schedule coroutines that manage stream lifecycle (add at count-in start, remove at region end)
- Pause/resume: forward to `MetronomeEngine.stop()`/`start()`
- `PlayerUiState`: replace `isCountInActive`/`beat1Ms` with:
  ```kotlin
  val activeLayers: List<ActiveLayerState> = emptyList()
  ```
  Where `ActiveLayerState` = `(layerId, layerName, isInCountIn: Boolean, nextBeatMs: Long)`

### Navigation changes

**`ui/navigation/ShowtimeNavigation.kt`**:

The `onPresetPlay` callback must load layers+regions before playing:

```kotlin
onPresetPlay = { preset, track ->
    viewModelScope.launch {
        val layers = presetRepository.observePresetWithLayers(preset.id).first()
        if (layers.layers.any { it.layer.enabled && it.regions.isNotEmpty() }) {
            playerViewModel.playWithLayers(track, layers)
        } else {
            playerViewModel.playTrack(track)
        }
        navigateToPlayer()
    }
}
```

---

## Phase 5: Cleanup

### Dead code to remove

| What | Where |
|---|---|
| `playClick(accent: Boolean)` | `PlayerViewModel.kt` — throwaway AudioTrack method |
| Warmup AudioTrack block | `PlayerViewModel.kt:127-144` |
| Flat metronome fields | `PresetsUiState`, `PresetsViewModel` |
| `countInJob`, `songStartTimeNs`, `countInEndMs`, `isCountInActive`, `isPaused`, `pauseAccumulatedNs`, `lastResumeNs` | `PlayerViewModel.kt` — replaced by MetronomeEngine state |

### Build verification

- `./gradlew assembleDebug` must pass
- NDK build must compile (Oboe + MetronomeSynthesizer)
- Room schema generates without errors

---

## File Change Summary

| File | Action | Phase |
|---|---|---|
| `data/db/entity/MetronomeLayer.kt` | Create | 1 |
| `data/db/entity/MetronomeRegion.kt` | Create | 1 |
| `data/db/entity/PresetEntity.kt` | Edit (remove metronome* fields) | 1 |
| `data/db/PracticeDatabase.kt` | Edit (add entities, bump version) | 1 |
| `data/db/dao/MetronomeLayerDao.kt` | Create | 1 |
| `data/db/dao/MetronomeRegionDao.kt` | Create | 1 |
| `data/repository/PresetRepositoryImpl.kt` | Edit (add layer/region CRUD) | 1 |
| `ui/screens/presets/PresetsScreen.kt` | Rewrite (DAW editor) | 2 |
| `ui/screens/presets/PresetsViewModel.kt` | Rewrite (new state model) | 2 |
| `ui/screens/presets/MetronomeTrackRow.kt` | Create | 2 |
| `ui/screens/presets/RegionBlock.kt` | Create | 2 |
| `ui/screens/presets/RegionEditSheet.kt` | Create | 2 |
| `ui/screens/presets/LayerHeader.kt` | Create | 2 |
| `app/build.gradle.kts` | Edit (add Oboe dep) | 3 |
| `app/src/main/cpp/CMakeLists.txt` | Edit (enable Oboe) | 3 |
| `app/src/main/cpp/MetronomeSynthesizer.cpp` | Rewrite (full mixer) | 3 |
| `app/src/main/cpp/AudioEngineNative.cpp` | Rewrite (Oboe stream) | 3 |
| `player/engine/NativeOboeEngine.kt` | Rewrite (JNI bridge) | 3 |
| `player/metronome/MetronomeEngine.kt` | Rewrite (Kotlin wrapper) | 3 |
| `ui/screens/player/PlayerViewModel.kt` | Edit (multi-layer playback) | 4 |
| `ui/navigation/ShowtimeNavigation.kt` | Edit (pass layers to player) | 4 |
| `ui/screens/presets/AddPresetTrackScreen.kt` | Delete | 2 |
| `ui/screens/presets/AddPresetTempoScreen.kt` | Delete | 2 |
| `ui/screens/presets/AddPresetTimeSignatureScreen.kt` | Delete | 2 |
| `ui/screens/presets/AddPresetBeatScreen.kt` | Delete | 2 |
| `ui/screens/presets/AddPresetReviewScreen.kt` | Delete | 2 |
| `ui/screens/presets/EditPresetScreen.kt` | Delete | 2 |
| `ui/screens/presets/EditPresetBeatScreen.kt` | Delete | 2 |
| `ui/screens/presets/EditPresetTempoScreen.kt` | Delete | 2 |
