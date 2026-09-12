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

class MetronomeSynthesizer {
public:
    static constexpr int SAMPLE_RATE = 44100;

    MetronomeSynthesizer() = default;

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
        stream.volume = std::max(0.0f, std::min(1.0f, volume));
        stream.beatIntervalSamples = static_cast<int64_t>(
            (60.0 / bpm) * SAMPLE_RATE
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

    void updateStream(int id, float bpm, int timeSigNum, int timeSigDenom, float volume) {
        std::lock_guard<std::mutex> lock(mtx);
        for (auto& s : streams) {
            if (s.id == id) {
                s.bpm = bpm;
                s.timeSigNum = timeSigNum;
                s.timeSigDenom = timeSigDenom;
                s.volume = std::max(0.0f, std::min(1.0f, volume));
                s.beatIntervalSamples = static_cast<int64_t>(
                    (60.0 / bpm) * SAMPLE_RATE
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

        for (auto& stream : streams) {
            if (!stream.active) continue;
            renderStream(stream, output, numFrames, channelCount);
        }

        // Soft clamp to [-1, 1]
        for (int i = 0; i < numFrames * channelCount; ++i) {
            output[i] = std::max(-1.0f, std::min(1.0f, output[i]));
        }
    }

private:
    std::vector<MetronomeStream> streams;
    std::mutex mtx;

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
                           / SAMPLE_RATE;
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
        s.phaseIncrement = (2.0 * M_PI * frequency) / SAMPLE_RATE;

        int durationMs = isAccent ? 15 : 10;
        s.clickTotalSamples = SAMPLE_RATE * durationMs / 1000;
        s.clickSamplesRemaining = s.clickTotalSamples;

        s.beatCount++;
        if (s.beatCount >= s.timeSigNum) {
            s.beatCount = 0;
        }
    }
};
