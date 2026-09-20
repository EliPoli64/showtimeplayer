package com.showtimeplayer.player.metronome

import com.showtimeplayer.player.engine.NativeOboeEngine

data class MetronomeLayerConfig(
    val id: Int,
    val bpm: Float,
    val timeSigNum: Int,
    val timeSigDenom: Int,
    val volume: Float = 1.0f,
)

class MetronomeEngine {
    private val nativeEngine = NativeOboeEngine()
    private var isRunning = false
    private var initialized = false

    fun initialize(): Boolean {
        if (initialized) return true
        initialized = nativeEngine.initEngine()
        return initialized
    }

    fun start() {
        // Always (re)assert native start: it is cheap when already running, and it also
        // re-opens the stream if a device change stopped it. Track the result so a failed
        // open (e.g. transient route change) is retried on the next start().
        isRunning = nativeEngine.start()
    }

    fun stop() {
        if (isRunning) {
            nativeEngine.stop()
            isRunning = false
        }
    }

    fun destroy() {
        stop()
        nativeEngine.destroy()
    }

    fun addLayer(config: MetronomeLayerConfig) {
        nativeEngine.addStream(
            config.id, config.bpm, config.timeSigNum, config.timeSigDenom, config.volume,
        )
    }

    fun removeLayer(id: Int) {
        nativeEngine.removeStream(id)
    }

    fun updateLayer(config: MetronomeLayerConfig) {
        nativeEngine.updateStream(
            config.id, config.bpm, config.timeSigNum, config.timeSigDenom, config.volume,
        )
    }

    fun triggerAll() {
        nativeEngine.triggerAll()
    }

    // Output latency of the click stream in ms (0 when unknown). Lets callers start count-ins
    // early so the clicks are heard at the intended position.
    fun latencyMs(): Float = nativeEngine.getLatencyMs()

    fun addClip(id: Int, data: FloatArray, startSample: Long, volume: Float) {
        nativeEngine.addClip(id, data, startSample, volume)
    }

    fun removeClip(id: Int) {
        nativeEngine.removeClip(id)
    }

    fun sampleCount(): Long = nativeEngine.getSampleCount()

    fun sampleRate(): Int = nativeEngine.getSampleRate()

    // Pre-renders one count-in region's full click track into a mono PCM buffer at the given
    // output sample rate. Clicks are spaced so they land on media beats at the current
    // playback rate, matching the live synth exactly.
    fun renderCountInClip(
        sampleRate: Int,
        playbackRate: Float,
        bpm: Int,
        timeSigNum: Int,
        countInBars: Int,
        volume: Float,
    ): FloatArray {
        if (sampleRate <= 0 || bpm <= 0 || timeSigNum <= 0 || countInBars <= 0) {
            return FloatArray(0)
        }
        val numClicks = countInBars * timeSigNum
        val beatIntervalMs = 60_000.0 / bpm
        val spacing = (beatIntervalMs / playbackRate * sampleRate / 1000.0).toInt().coerceAtLeast(1)
        val tailSamples = sampleRate / 30
        val length = numClicks * spacing + tailSamples
        val out = FloatArray(length)
        for (k in 0 until numClicks) {
            val accent = k % timeSigNum == 0
            val freq = if (accent) 1000.0 else 800.0
            val amp = (if (accent) 0.8 else 0.4) * volume
            val durationMs = if (accent) 20 else 14
            val clickSamples = (durationMs * sampleRate / 1000.0).toInt()
            val start = k * spacing
            for (i in 0 until clickSamples) {
                val idx = start + i
                if (idx >= out.size) break
                val t = i.toDouble() / sampleRate
                val envelope = Math.exp(-t * 200.0)
                out[idx] += (Math.sin(2.0 * Math.PI * freq * t) * amp * envelope).toFloat()
            }
        }
        return out
    }
}
