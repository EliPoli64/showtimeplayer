#include <jni.h>
#include <oboe/Oboe.h>
#include "MetronomeSynthesizer.h"

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    oboe::ManagedStream stream;
    MetronomeSynthesizer synth;
    bool isStarted = false;

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

    int start() {
        if (isStarted) return 0;

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Shared)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setSampleRate(MetronomeSynthesizer::SAMPLE_RATE)
               ->setDataCallback(this);

        oboe::Result result = builder.openManagedStream(stream);
        if (result != oboe::Result::OK) {
            return static_cast<int>(result);
        }

        stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2);
        result = stream->requestStart();
        if (result != oboe::Result::OK) {
            return static_cast<int>(result);
        }

        isStarted = true;
        return 0;
    }

    void stop() {
        if (stream) {
            stream->requestStop();
        }
        isStarted = false;
    }
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
    jint timeSigDenom
) {
    if (!g_engine) return;
    g_engine->synth.addStream(
        static_cast<int>(streamId),
        static_cast<float>(bpm),
        static_cast<int>(timeSigNum),
        static_cast<int>(timeSigDenom)
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
    jint timeSigDenom
) {
    if (!g_engine) return;
    g_engine->synth.updateStream(
        static_cast<int>(streamId),
        static_cast<float>(bpm),
        static_cast<int>(timeSigNum),
        static_cast<int>(timeSigDenom)
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

} // extern "C"
