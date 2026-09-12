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
        if (!isRunning) {
            nativeEngine.start()
            isRunning = true
        }
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
}
