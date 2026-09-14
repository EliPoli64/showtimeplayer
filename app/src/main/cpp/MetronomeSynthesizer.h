#pragma once

#include <cmath>
#include <cstdint>
#include <mutex>
#include <vector>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

struct MetronomeStream {
    int id = 0;
    bool active = false;
    float bpm = 120.0f;
    int timeSigNum = 4;
    int timeSigDenom = 4;
    float volume = 1.0f;

    // Oscillator state
    double phase = 0.0;
    double phaseIncrement = 0.0;
    float amplitude = 0.0;
    int clickSamplesRemaining = 0;
    int clickTotalSamples = 0;

    // Timing state
    int64_t currentSample = 0;
    int64_t beatIntervalSamples = 0;
    int beatCount = 0;
    int64_t totalSamples = 0;
};

// A fully pre-rendered click track (one count-in) played back sample-accurately.
struct RenderedClip {
    int id = 0;
    std::vector<float> samples;   // mono PCM
    int64_t startSample = 0;      // engine sample counter at which to start
    int64_t playPosition = 0;     // next sample index to output
    float volume = 1.0f;
    bool done = false;
};

class MetronomeSynthesizer {
public:
    static constexpr int DEFAULT_SAMPLE_RATE = 44100;
    static constexpr float MAX_VOLUME = 2.0f;

    MetronomeSynthesizer() = default;

    // Called when the output stream opens (or reopens after a device change), so all
    // timing/pitch math matches the device's actual sample rate.
    void setSampleRate(int rate) {
        if (rate <= 0) return;
        std::lock_guard<std::mutex> lock(mtx);
        if (rate == sampleRate) return;
        sampleRate = rate;
        for (auto& s : streams) {
            if (s.bpm > 0.0f) {
                s.beatIntervalSamples = static_cast<int64_t>((60.0 / s.bpm) * sampleRate);
            }
        }
    }

    void addStream(int id, float bpm, int timeSigNum, int timeSigDenom, float volume) {
        std::lock_guard<std::mutex> lock(mtx);
        streams.erase(
            std::remove_if(streams.begin(), streams.end(),
                [id](const MetronomeStream& s) { return s.id == id; }),
            streams.end()
        );
        MetronomeStream stream;
        stream.id = id;
        stream.active = true;
        stream.bpm = bpm;
        stream.timeSigNum = timeSigNum;
        stream.timeSigDenom = timeSigDenom;
        stream.volume = std::max(0.0f, std::min(MAX_VOLUME, volume));
        stream.beatIntervalSamples = static_cast<int64_t>(
            (60.0 / bpm) * sampleRate
        );
        stream.currentSample = 0;
        stream.beatCount = 0;
        stream.totalSamples = 0;
        streams.push_back(stream);
    }

    void removeStream(int id) {
        std::lock_guard<std::mutex> lock(mtx);
        streams.erase(
            std::remove_if(streams.begin(), streams.end(),
                [id](const MetronomeStream& s) { return s.id == id; }),
            streams.end()
        );
    }

    // Pre-rendered clip (one whole count-in). data is copied out of the caller's buffer.
    void addClip(int id, const float* data, int numFrames, int64_t startSample, float volume) {
        std::lock_guard<std::mutex> lock(mtx);
        removeClipLocked(id);
        RenderedClip clip;
        clip.id = id;
        clip.samples.assign(data, data + numFrames);
        clip.startSample = startSample;
        clip.volume = std::max(0.0f, std::min(MAX_VOLUME, volume));
        clips.push_back(std::move(clip));
    }

    void removeClip(int id) {
        std::lock_guard<std::mutex> lock(mtx);
        removeClipLocked(id);
    }

    // Current output sample counter (advances in real time once the stream is running).
    int64_t getSampleCount() {
        std::lock_guard<std::mutex> lock(mtx);
        return renderSample;
    }

    int getSampleRate() {
        std::lock_guard<std::mutex> lock(mtx);
        return sampleRate;
    }

    void updateStream(int id, float bpm, int timeSigNum, int timeSigDenom, float volume) {
        std::lock_guard<std::mutex> lock(mtx);
        for (auto& s : streams) {
            if (s.id == id) {
                s.bpm = bpm;
                s.timeSigNum = timeSigNum;
                s.timeSigDenom = timeSigDenom;
                s.volume = std::max(0.0f, std::min(MAX_VOLUME, volume));
                s.beatIntervalSamples = static_cast<int64_t>(
                    (60.0 / bpm) * sampleRate
                );
                return;
            }
        }
    }

    void triggerAll() {
        std::lock_guard<std::mutex> lock(mtx);
        for (auto& s : streams) {
            s.currentSample = 0;
            s.beatCount = 0;
            s.totalSamples = 0;
            s.clickSamplesRemaining = 0;
        }
    }

    void render(float* output, int numFrames, int channelCount) {
        std::lock_guard<std::mutex> lock(mtx);
        // Zero output buffer
        for (int i = 0; i < numFrames * channelCount; ++i) {
            output[i] = 0.0f;
        }

        // Mix pre-rendered clips sample-accurately.
        int64_t frameStart = renderSample;
        for (int frame = 0; frame < numFrames; ++frame) {
            int64_t idx = frameStart + frame;
            for (auto& clip : clips) {
                if (clip.done) continue;
                if (idx < clip.startSample) continue;
                if (clip.playPosition < static_cast<int64_t>(clip.samples.size())) {
                    float s = clip.samples[clip.playPosition] * clip.volume;
                    for (int ch = 0; ch < channelCount; ++ch) {
                        output[frame * channelCount + ch] += s;
                    }
                    clip.playPosition++;
                } else {
                    clip.done = true;
                }
            }
        }
        renderSample += numFrames;

        // Live-generated metronome streams (used by the fallback path).
        for (auto& stream : streams) {
            if (!stream.active) continue;
            renderStream(stream, output, numFrames, channelCount);
        }

        // Soft-limit so click volume up to 200% gets louder without hard clipping.
        for (int i = 0; i < numFrames * channelCount; ++i) {
            output[i] = softClip(output[i]);
        }
    }

private:
    std::vector<MetronomeStream> streams;
    std::vector<RenderedClip> clips;
    std::mutex mtx;
    int sampleRate = DEFAULT_SAMPLE_RATE;
    int64_t renderSample = 0;

    void removeClipLocked(int id) {
        clips.erase(
            std::remove_if(clips.begin(), clips.end(),
                [id](const RenderedClip& c) { return c.id == id; }),
            clips.end()
        );
    }

    // Linear below the threshold, then a smooth tanh knee approaching full scale.
    static float softClip(float x) {
        constexpr float threshold = 0.8f;
        constexpr float range = 1.0f - threshold;
        if (x > threshold) {
            return threshold + range * std::tanh((x - threshold) / range);
        }
        if (x < -threshold) {
            return -(threshold + range * std::tanh((-x - threshold) / range));
        }
        return x;
    }

    void renderStream(MetronomeStream& s, float* output, int numFrames, int channelCount) {
        for (int frame = 0; frame < numFrames; ++frame) {
            // Check if we need to trigger a new click
            if (s.clickSamplesRemaining <= 0) {
                int64_t beatBoundary = s.beatIntervalSamples;
                if (beatBoundary > 0 && s.currentSample % beatBoundary == 0) {
                    triggerClick(s);
                }
            }

            // Render click sample if active
            float sample = 0.0f;
            if (s.clickSamplesRemaining > 0) {
                double t = static_cast<double>(s.clickTotalSamples - s.clickSamplesRemaining)
                           / sampleRate;
                double envelope = std::exp(-t * 200.0);
                sample = static_cast<float>(
                    std::sin(s.phase) * s.amplitude * envelope
                );
                s.phase += s.phaseIncrement;
                s.clickSamplesRemaining--;
            }

            // Mix into output (mono to all channels)
            for (int ch = 0; ch < channelCount; ++ch) {
                output[frame * channelCount + ch] += sample;
            }

            s.currentSample++;
            s.totalSamples++;
        }
    }

    void triggerClick(MetronomeStream& s) {
        bool isAccent = (s.beatCount % s.timeSigNum == 0);
        float frequency = isAccent ? 1000.0f : 800.0f;
        s.amplitude = (isAccent ? 0.8f : 0.4f) * s.volume;
        s.phase = 0.0;
        s.phaseIncrement = (2.0 * M_PI * frequency) / sampleRate;

        int durationMs = isAccent ? 15 : 10;
        s.clickTotalSamples = sampleRate * durationMs / 1000;
        s.clickSamplesRemaining = s.clickTotalSamples;

        s.beatCount++;
        if (s.beatCount >= s.timeSigNum) {
            s.beatCount = 0;
        }
    }
};
