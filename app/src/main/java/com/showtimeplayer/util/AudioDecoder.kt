package com.showtimeplayer.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

object AudioDecoder {

    data class WaveformData(
        val amplitudes: List<Float>,
        val durationMs: Long,
        val sampleRate: Int,
    )

    private val waveformCache = LinkedHashMap<String, WaveformData>(
        8, 0.75f, true,
    )

    @Synchronized
    private fun getCached(uri: String): WaveformData? = waveformCache[uri]

    @Synchronized
    private fun putCache(uri: String, data: WaveformData) {
        if (waveformCache.size >= 8) {
            waveformCache.remove(waveformCache.keys.first())
        }
        waveformCache[uri] = data
    }

    suspend fun decodeFullWaveform(
        context: Context,
        uri: Uri,
        samplesPerSecond: Int = 50,
        maxBars: Int = 500,
    ): WaveformData {
        getCached(uri.toString())?.let { return it }
        val data = withContext(Dispatchers.IO) {
            decodeInternal(context, uri, maxDurationMs = Long.MAX_VALUE, samplesPerSecond, maxBars)
        }
        putCache(uri.toString(), data)
        return data
    }

    suspend fun decodeWaveform(
        context: Context,
        uri: Uri,
        maxDurationMs: Long = 30_000,
        samplesPerSecond: Int = 100,
    ): WaveformData = withContext(Dispatchers.IO) {
        decodeInternal(context, uri, maxDurationMs, samplesPerSecond, maxBars = Int.MAX_VALUE)
    }

    private fun decodeInternal(
        context: Context,
        uri: Uri,
        maxDurationMs: Long,
        samplesPerSecond: Int,
        maxBars: Int,
    ): WaveformData {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        try {
            val audioTrackIndex = (0 until extractor.trackCount).firstNotNullOf { i ->
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@firstNotNullOf null
                if (mime.startsWith("audio/")) i else null
            } ?: throw IllegalArgumentException("No audio track found")

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val durationMs = min(durationUs / 1000, maxDurationMs.toLong())

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val maxSamples = (durationMs / 1000) * sampleRate
            val amplitudes = mutableListOf<Float>()
            var totalSamplesRead = 0L

            var inputDone = false
            var outputDone = false

            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuf = codec.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0 || totalSamplesRead >= maxSamples) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                totalSamplesRead += sampleSize / (channelCount * 2)
                                extractor.advance()
                            }
                        }
                    }

                    val outputInfo = MediaCodec.BufferInfo()
                    val outputIndex = codec.dequeueOutputBuffer(outputInfo, 10_000)
                    if (outputIndex >= 0) {
                        if (outputInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }

                        val outputBuf = codec.getOutputBuffer(outputIndex)!!
                        val outputSize = outputInfo.size
                        val outputData = ByteArray(outputSize)
                        outputBuf.get(outputData)
                        codec.releaseOutputBuffer(outputIndex, false)

                        val bytesPerSample = 2
                        val groupSize = sampleRate / samplesPerSecond

                        var pos = 0
                        while (pos + groupSize * channelCount * bytesPerSample <= outputSize) {
                            var sum = 0f
                            for (j in 0 until groupSize) {
                                val idx = (pos + j * channelCount) * bytesPerSample
                                if (idx + 1 < outputSize) {
                                    val sample = (outputData[idx].toInt() and 0xFF) or
                                        (outputData[idx + 1].toInt() shl 8)
                                    sum += abs(sample.toFloat() / Short.MAX_VALUE)
                                }
                            }
                            amplitudes.add((sum / groupSize).coerceIn(0f, 1f))
                            pos += groupSize * channelCount * bytesPerSample
                        }
                    }
                }
            } finally {
                try { codec.stop(); codec.release() } catch (_: Exception) {}
            }

            val result = if (amplitudes.size > maxBars && maxBars > 0) {
                val step = amplitudes.size.toFloat() / maxBars
                (0 until maxBars).map { i ->
                    val start = (i * step).toInt()
                    val end = ((i + 1) * step).toInt().coerceAtMost(amplitudes.size)
                    if (start < end) amplitudes.subList(start, end).average().toFloat() else 0f
                }
            } else {
                amplitudes
            }

            return WaveformData(amplitudes = result, durationMs = durationUs / 1000, sampleRate = sampleRate)
        } finally {
            extractor.release()
        }
    }
}
