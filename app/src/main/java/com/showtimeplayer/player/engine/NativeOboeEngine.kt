package com.showtimeplayer.player.engine

class NativeOboeEngine {
    init {
        System.loadLibrary("showtimeplayer_native")
    }

    external fun nativeInit(sampleRate: Int, channelCount: Int): Int
    external fun nativeStart(): Int
    external fun nativeStop()
    external fun nativeDestroy()
    external fun nativeAddStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int, volume: Float)
    external fun nativeRemoveStream(id: Int)
    external fun nativeUpdateStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int, volume: Float)
    external fun nativeTriggerAll()
    external fun nativeGetLatencyMs(): Float
    external fun nativeAddClip(id: Int, data: FloatArray, startSample: Long, volume: Float)
    external fun nativeRemoveClip(id: Int)
    external fun nativeGetSampleCount(): Long
    external fun nativeGetSampleRate(): Int

    fun initEngine(sampleRate: Int = 44100, channelCount: Int = 2): Boolean {
        return nativeInit(sampleRate, channelCount) == 0
    }

    fun start(): Boolean {
        return nativeStart() == 0
    }

    fun stop() {
        nativeStop()
    }

    fun destroy() {
        nativeDestroy()
    }

    fun addStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int, volume: Float) {
        nativeAddStream(id, bpm, timeSigNum, timeSigDenom, volume)
    }

    fun removeStream(id: Int) {
        nativeRemoveStream(id)
    }

    fun updateStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int, volume: Float) {
        nativeUpdateStream(id, bpm, timeSigNum, timeSigDenom, volume)
    }

    fun triggerAll() {
        nativeTriggerAll()
    }

    fun getLatencyMs(): Float = nativeGetLatencyMs()

    fun addClip(id: Int, data: FloatArray, startSample: Long, volume: Float) {
        nativeAddClip(id, data, startSample, volume)
    }

    fun removeClip(id: Int) {
        nativeRemoveClip(id)
    }

    fun getSampleCount(): Long = nativeGetSampleCount()

    fun getSampleRate(): Int = nativeGetSampleRate()
}
