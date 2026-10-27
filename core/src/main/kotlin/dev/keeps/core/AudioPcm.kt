/*
 * Copyright 2026 Google LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.keeps.core

import kotlin.math.roundToInt

// Adapted from pinned Gallery VideoAudioExtractor.kt, 8 October 2026.
// Downmix/resampling retained; sparse PTS buffer added for Keeps. See docs/THIRD_PARTY.md.
fun downmixToMono(interleaved: ShortArray, channelCount: Int): ShortArray {
    require(channelCount in 1..8)
    if (channelCount == 1) return interleaved
    return ShortArray(interleaved.size / channelCount) { frame ->
        var sum = 0
        repeat(channelCount) { sum += interleaved[frame * channelCount + it] }
        (sum / channelCount).toShort()
    }
}

fun resampleLinear(input: ShortArray, inputRateHz: Int, outputRateHz: Int = 16_000): ShortArray {
    require(inputRateHz in 8_000..192_000 && outputRateHz in 8_000..192_000)
    if (input.isEmpty() || inputRateHz == outputRateHz) return input
    val count = (input.size.toLong() * outputRateHz / inputRateHz).toInt()
    return ShortArray(count) { i ->
        // Integer phase makes overlapping slices round identically at 44.1 kHz.
        // A floating i*step drifts across large offsets at half-sample ties.
        val phase = i.toLong() * inputRateHz
        val lower = (phase / outputRateHz).toInt()
        val fraction = (phase % outputRateHz).toDouble() / outputRateHz
        val a = input[lower].toDouble()
        val b = input.getOrElse(lower + 1) { input[lower] }.toDouble()
        (a + (b - a) * fraction).roundToInt().coerceIn(-32768, 32767).toShort()
    }
}

/** Sparse decoded PCM on the container timeline; gaps never allocate a whole missing track. */
class PcmTimelineBuffer {
    private data class Segment(val start: Long, val samples: ShortArray) { val end get() = start + samples.size }
    private val segments = ArrayDeque<Segment>()
    var decodedThrough: Long = 0
        private set

    fun append(startSample: Long, samples: ShortArray) {
        val skip = (maxOf(0, decodedThrough) - startSample).coerceIn(0, samples.size.toLong()).toInt()
        val start = startSample + skip
        if (skip < samples.size) segments.addLast(Segment(start, if (skip == 0) samples else samples.copyOfRange(skip, samples.size)))
        decodedThrough = maxOf(decodedThrough, startSample + samples.size)
    }

    fun dropBefore(sample: Long) {
        while (segments.isNotEmpty() && segments.first().end <= sample) segments.removeFirst()
        val first = segments.firstOrNull() ?: return
        if (first.start < sample) {
            segments.removeFirst()
            segments.addFirst(Segment(sample, first.samples.copyOfRange((sample - first.start).toInt(), first.samples.size)))
        }
    }

    /** Missing interior/leading samples are silence; an undecoded tail is never stretched. */
    fun slice(start: Long, end: Long): ShortArray? {
        require(start >= 0 && end >= start && end - start <= 1_536_000)
        val actualEnd = minOf(end, decodedThrough)
        if (actualEnd <= start || segments.none { it.start < actualEnd && it.end > start }) return null
        val result = ShortArray((actualEnd - start).toInt())
        for (segment in segments) {
            val from = maxOf(start, segment.start)
            val to = minOf(actualEnd, segment.end)
            if (to > from) segment.samples.copyInto(result, (from - start).toInt(), (from - segment.start).toInt(), (to - segment.start).toInt())
        }
        return result
    }
}
