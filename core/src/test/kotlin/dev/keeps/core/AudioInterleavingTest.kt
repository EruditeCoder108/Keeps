package dev.keeps.core

import org.junit.Assert.*
import org.junit.Test

class AudioInterleavingTest {
    @Test fun silentWindowsKeepOrderedFramesWithoutUnvalidatedTimeMarkers() {
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        val parts = buildWindowParts(listOf(first, second), listOf(500, 1500))
        assertEquals(2, parts.size)
        assertArrayEquals(first, (parts[0] as WindowPart.Image).imageData)
        assertArrayEquals(second, (parts[1] as WindowPart.Image).imageData)
    }
    @Test fun truncatedSoundtrackDoesNotStretchAcrossMissingTail() {
        val slices = splitWindowPcm(ShortArray(16_000) { 7 }, 1000, 3000, 2)
        assertEquals(16_000, slices[0]!!.size)
        assertNull(slices[1])
        val parts = buildWindowParts(listOf(byteArrayOf(1), byteArrayOf(2)), listOf(1000, 2000), slices)
        assertTrue(parts[0] is WindowPart.Text)
        assertEquals(16_000, (parts[1] as WindowPart.Audio).pcm.size)
        assertEquals(1, parts.filterIsInstance<WindowPart.Audio>().size)
        assertEquals(2, parts.filterIsInstance<WindowPart.Image>().size)
    }
    @Test fun sliceBoundariesCoverWindowWithoutGaps() {
        val ranges = audioSliceBoundaries(123, 2124, 3)
        assertEquals(123L, ranges.first().first)
        assertEquals(2124L, ranges.last().second)
        assertTrue(ranges.zipWithNext().all { (a, b) -> a.second == b.first })
    }
    @Test(expected = IllegalArgumentException::class) fun reversedFramesAreRejected() {
        buildWindowParts(listOf(byteArrayOf(1), byteArrayOf(2)), listOf(1500, 500))
    }
}
