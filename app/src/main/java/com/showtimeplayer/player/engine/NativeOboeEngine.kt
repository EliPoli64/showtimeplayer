package com.showtimeplayer.player.engine

class NativeOboeEngine {
    init {
        System.loadLibrary("showtimeplayer_native")
    }

    external fun nativeInit(sampleRate: Int, channelCount: Int): Int
    external fun nativeStart(): Int
    external fun nativeStop()
    external fun nativeDestroy()
    external fun nativeAddStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int)
    external fun nativeRemoveStream(id: Int)
    external fun nativeUpdateStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int)
    external fun nativeTriggerAll()

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

    fun addStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int) {
        nativeAddStream(id, bpm, timeSigNum, timeSigDenom)
    }

    fun removeStream(id: Int) {
        nativeRemoveStream(id)
    }

    fun updateStream(id: Int, bpm: Float, timeSigNum: Int, timeSigDenom: Int) {
        nativeUpdateStream(id, bpm, timeSigNum, timeSigDenom)
    }

    fun triggerAll() {
        nativeTriggerAll()
    }
}
