/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.keeps.library.indexing

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dev.keeps.core.PcmTimelineBuffer
import dev.keeps.core.downmixToMono
import dev.keeps.core.resampleLinear
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// Adapted from pinned Gallery VideoAudioExtractor.kt, 8 October 2026.
// SAF URIs, actual PCM presentation times, sparse gaps, bounded stalls and no content logs.
// Pinned source: 3210720d3f808f51bae52798435d0c590ddc05bf; docs/THIRD_PARTY.md.
class VideoAudioExtractor private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    private var sampleRate: Int,
    private var channels: Int,
) : AutoCloseable {
    private val pcm = PcmTimelineBuffer()
    private var inputDone = false
    private var outputDone = false
    private var formatKnown = false
    private var stalls = 0
    private var lastStart = -1L
    private var closed = false
    var failed = false
        private set

    /** One forward pass; overlapping windows release only samples before their next start. */
    suspend fun readWindow(startMs: Long, endMs: Long): ShortArray? {
        require(startMs >= 0 && endMs > startMs && endMs - startMs <= 8_000 && startMs >= lastStart)
        if (closed || failed) return null
        try {
            // A resumed job starts near its first unfinished window, avoiding a full-track decode.
            if (lastStart < 0 && startMs > 0) extractor.seekTo((startMs - 1_000).coerceAtLeast(0) * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            lastStart = startMs
            while (!formatKnown && !outputDone && !failed) { currentCoroutineContext().ensureActive(); pump() }
            val start = startMs * sampleRate / 1000
            val end = endMs * sampleRate / 1000
            pcm.dropBefore(start)
            while (pcm.decodedThrough < end && !outputDone && !failed) {
                currentCoroutineContext().ensureActive()
                pump()
                pcm.dropBefore(start)
            }
            if (failed) return null
            return pcm.slice(start, end)?.let { resampleLinear(it, sampleRate) }?.takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; return null }
    }

    private fun pump() {
        var progressed = false
        if (!inputDone) {
            val input = codec.dequeueInputBuffer(0)
            if (input >= 0) {
                val buffer = codec.getInputBuffer(input)
                val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
                progressed = true
            }
        }
        val info = MediaCodec.BufferInfo()
        val output = codec.dequeueOutputBuffer(info, 10_000)
        when {
            output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val format = codec.outputFormat
                val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val count = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                check(rate in 8_000..192_000 && count in 1..8 && encoding == AudioFormat.ENCODING_PCM_16BIT)
                check(!formatKnown || rate == sampleRate) { "Audio sample rate changed mid-track" }
                sampleRate = rate; channels = count; formatKnown = true; progressed = true
            }
            output >= 0 -> {
                try {
                    val buffer = codec.getOutputBuffer(output)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val values = ShortArray(shorts.remaining() / channels * channels)
                        shorts.get(values)
                        pcm.append(info.presentationTimeUs * sampleRate / 1_000_000, downmixToMono(values, channels))
                        formatKnown = true
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    progressed = true
                } finally { codec.releaseOutputBuffer(output, false) }
            }
        }
        stalls = if (progressed) 0 else stalls + 1
        check(stalls < 200) { "Audio decoder stalled" }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { extractor.release() }
    }

    data class Opened(val decoder: VideoAudioExtractor?, val trackPresent: Boolean)
    companion object {
        fun open(context: Context, uri: Uri): Opened {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            var present = false
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                if (track == null) { extractor.release(); return Opened(null, false) }
                present = true
                val format = extractor.getTrackFormat(track)
                val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                check(rate in 8_000..192_000 && channels in 1..8)
                extractor.selectTrack(track)
                val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                codec = decoder
                decoder.configure(format, null, null, 0); decoder.start()
                return Opened(VideoAudioExtractor(extractor, decoder, rate, channels), true)
            } catch (_: Exception) {
                runCatching { codec?.release() }; runCatching { extractor.release() }
                return Opened(null, present)
            }
        }
    }
}
