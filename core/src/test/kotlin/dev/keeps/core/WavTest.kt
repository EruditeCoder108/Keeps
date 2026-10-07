package dev.keeps.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WavTest {
    private fun fixture(rate: Int = 16000): ByteArray = ByteBuffer.allocate(32044).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(32036); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(rate); putInt(32000); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(32000)
    }.array()
    @Test fun acceptsCanonicalMonoRecording() { assertEquals(1000L, Wav.durationMs(fixture())) }
    @Test fun rejectsUnsupportedSampleRate() {
        assertThrows(IllegalArgumentException::class.java) { Wav.durationMs(fixture(44100)) }
    }
    @Test fun rejectsTruncatedRecording() {
        assertThrows(IllegalArgumentException::class.java) { Wav.durationMs(fixture().copyOf(50)) }
    }
}
