#include <jni.h>
#include <atomic>
#include <mutex>
#include <oboe/Oboe.h>
#include "MetronomeSynthesizer.h"

class AudioEngine : public oboe::AudioStreamCallback {
public:
    oboe::ManagedStream stream;
    MetronomeSynthesizer synth;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* oboeStream,
        void* audioData,
        int32_t numFrames
    ) override {
        auto* output = static_cast<float*>(audioData);
        int channelCount = oboeStream->getChannelCount();
        synth.render(output, numFrames, channelCount);
        return oboe::DataCallbackResult::Continue;
    }

    // Called by Oboe after the stream is closed due to an error/device change (e.g. a
    // Bluetooth route change). Reopen so the click keeps following the current output
    // route instead of going silent or glitching.
    void onErrorAfterClose(oboe::AudioStream* /* audioStream */, oboe::Result /* error */) override {
        if (!shouldRun.load()) return;
        isStarted = false;
        reopen();
    }

    // Public entry point (from JNI). Marks the engine as wanting to run, then (re)opens.
    int start() {
        shouldRun = true;
        return reopen();
    }

    void stop() {
        shouldRun = false;
        isStarted = false;
        if (stream) {
            stream->requestStop();
        }
    }

    // Round-trip output latency of the metronome stream, in milliseconds. Used by the app to
    // start count-ins early so the clicks are heard at the intended timeline position.
    float latencyMs() {
        if (!stream) return 0.0f;
        oboe::ResultWithValue<double> result = stream->calculateLatencyMillis();
        if (result.error() == oboe::Result::OK) {
            return static_cast<float>(result.value());
        }
        return 0.0f;
    }

private:
    int reopen() {
        std::lock_guard<std::mutex> lock(restartMutex);
        // A stop() may have raced with a device-change callback; don't resurrect it.
        if (!shouldRun.load() || isStarted) return 0;

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               // Normal mixer (not LowLatency): the low-latency/fast MMAP path does not mix
               // reliably alongside ExoPlayer's track, whereas the shared normal mixer does.
               // A metronome click does not need low latency.
               ->setPerformanceMode(oboe::PerformanceMode::None)
               ->setSharingMode(oboe::SharingMode::Shared)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               // No fixed sample rate: let the device pick (Bluetooth usually runs at
               // 48 kHz, not 44.1 kHz) and keep the synth in sync with the actual rate.
               ->setSampleRate(oboe::Unspecified)
               ->setCallback(this);

        oboe::Result result = builder.openManagedStream(stream);
        if (result != oboe::Result::OK) {
            return static_cast<int>(result);
        }

        // Match the synth to the actual output rate so tempo/pitch stay correct.
        synth.setSampleRate(stream->getSampleRate());

        // A deeper buffer rides out Bluetooth scheduling jitter, which otherwise shows up as
        // chopped/uneven clicks on the normal (non-low-latency) mixer path.
        int32_t capacity = stream->getBufferCapacityInFrames();
        int32_t desired = stream->getFramesPerBurst() * 2;
        int32_t minMs = stream->getSampleRate() / 50; // ~20 ms
        if (desired < minMs) desired = minMs;
        if (capacity > 0 && desired > capacity) desired = capacity;
        stream->setBufferSizeInFrames(desired);

        result = stream->requestStart();
        if (result != oboe::Result::OK) {
            return static_cast<int>(result);
        }

        isStarted = true;
        return 0;
    }

    std::atomic<bool> isStarted{false};
    std::atomic<bool> shouldRun{false};
    std::mutex restartMutex;
};

static AudioEngine* g_engine = nullptr;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeInit(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint sampleRate,
    jint /* channelCount */
) {
    if (g_engine) {
        g_engine->stop();
        delete g_engine;
    }
    g_engine = new AudioEngine();
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeStart(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!g_engine) return -1;
    return g_engine->start();
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeStop(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (g_engine) {
        g_engine->stop();
    }
}

JNIEXPORT jfloat JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeGetLatencyMs(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!g_engine) return 0.0f;
    return g_engine->latencyMs();
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeDestroy(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (g_engine) {
        g_engine->stop();
        delete g_engine;
        g_engine = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeAddStream(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint streamId,
    jfloat bpm,
    jint timeSigNum,
    jint timeSigDenom,
    jfloat volume
) {
    if (!g_engine) return;
    g_engine->synth.addStream(
        static_cast<int>(streamId),
        static_cast<float>(bpm),
        static_cast<int>(timeSigNum),
        static_cast<int>(timeSigDenom),
        static_cast<float>(volume)
    );
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeRemoveStream(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint streamId
) {
    if (!g_engine) return;
    g_engine->synth.removeStream(static_cast<int>(streamId));
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeUpdateStream(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint streamId,
    jfloat bpm,
    jint timeSigNum,
    jint timeSigDenom,
    jfloat volume
) {
    if (!g_engine) return;
    g_engine->synth.updateStream(
        static_cast<int>(streamId),
        static_cast<float>(bpm),
        static_cast<int>(timeSigNum),
        static_cast<int>(timeSigDenom),
        static_cast<float>(volume)
    );
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeTriggerAll(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!g_engine) return;
    g_engine->synth.triggerAll();
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeAddClip(
    JNIEnv* env,
    jobject /* thiz */,
    jint id,
    jfloatArray data,
    jlong startSample,
    jfloat volume
) {
    if (!g_engine) return;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return;
    jfloat* ptr = env->GetFloatArrayElements(data, nullptr);
    if (!ptr) return;
    g_engine->synth.addClip(
        static_cast<int>(id), ptr, static_cast<int>(len),
        static_cast<int64_t>(startSample), static_cast<float>(volume)
    );
    env->ReleaseFloatArrayElements(data, ptr, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeRemoveClip(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint id
) {
    if (!g_engine) return;
    g_engine->synth.removeClip(static_cast<int>(id));
}

JNIEXPORT jlong JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeGetSampleCount(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!g_engine) return 0;
    return static_cast<jlong>(g_engine->synth.getSampleCount());
}

JNIEXPORT jint JNICALL
Java_com_showtimeplayer_player_engine_NativeOboeEngine_nativeGetSampleRate(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!g_engine) return 0;
    return static_cast<jint>(g_engine->synth.getSampleRate());
}

} // extern "C"
