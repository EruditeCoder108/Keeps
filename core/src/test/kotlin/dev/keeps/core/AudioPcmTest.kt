package dev.keeps.core

import org.junit.Assert.*
import org.junit.Test

class AudioPcmTest {
    @Test fun stereoMixPreservesBothChannelsWithoutOverflow() {
        assertArrayEquals(shortArrayOf(32767, 0, -32768), downmixToMono(shortArrayOf(32767, 32767, 3000, -3000, -32768, -32768), 2))
    }
    @Test fun resamplingKeepsDurationAndToneRatherThanChangingPlaybackSpeed() {
        val source = ShortArray(48_000) { (kotlin.math.sin(2 * Math.PI * 440 * it / 48_000) * 5000).toInt().toShort() }
        val result = resampleLinear(source, 48_000)
        assertEquals(16_000, result.size)
        assertTrue((1 until result.size).count { result[it - 1] <= 0 && result[it] > 0 } in 439..441)
    }
    @Test fun overlapping44100HzWindowsHaveExactlyTheSameResampledSamples() {
        val source = ShortArray(44_100 * 14) { ((it * 5) % 30_000).toShort() }
        val first = resampleLinear(source.copyOfRange(0, 44_100 * 8), 44_100)
        val second = resampleLinear(source.copyOfRange(44_100 * 6, 44_100 * 14), 44_100)
        assertArrayEquals(first.copyOfRange(96_000, 128_000), second.copyOfRange(0, 32_000))
    }
    @Test fun delayedAudioAndSparseGapsKeepTheirPresentationTimes() {
        val buffer = PcmTimelineBuffer()
        buffer.append(3, shortArrayOf(10, 11))
        buffer.append(10, shortArrayOf(20, 21))
        assertArrayEquals(shortArrayOf(0, 0, 0, 10, 11, 0), buffer.slice(0, 6))
        assertNull(buffer.slice(5, 10))
        assertArrayEquals(shortArrayOf(20, 21), buffer.slice(10, 20))
        buffer.dropBefore(10)
        assertNull(buffer.slice(3, 5))
    }
    @Test fun prerollAndRepeatedDecodedSamplesAreTrimmed() {
        val buffer = PcmTimelineBuffer()
        buffer.append(-2, shortArrayOf(1, 2, 3, 4))
        buffer.append(1, shortArrayOf(4, 5, 6))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), buffer.slice(0, 4))
    }
    @Test fun framePlanAndWindowsCoverLongVideosAndFinalTail() {
        val windows = Chunking.windows(361_001)
        assertTrue(windows.size > 48)
        assertEquals(361_001L, windows.last().endMs)
        assertEquals(listOf(6_000L, 8_000L, 10_000L, 12_000L), VideoWindows.frameTimes(TimeWindow(6_000, 14_000)))
        assertEquals(listOf(0L), VideoWindows.frameTimes(TimeWindow(0, 1)))
    }
}
