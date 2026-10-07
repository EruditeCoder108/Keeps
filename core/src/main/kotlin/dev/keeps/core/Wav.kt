package dev.keeps.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Strict bounded WAV support for the initial audio pipeline; compressed files need a decoder. */
object Wav {
    fun durationMs(bytes: ByteArray): Long {
        require(bytes.size >= 44) { "Incomplete WAV" }
        fun tag(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        require(tag(0) == "RIFF" && tag(8) == "WAVE") { "Select a PCM WAV recording" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val declared = buffer.getInt(4).toLong() and 0xffffffffL
        require(declared + 8 == bytes.size.toLong()) { "WAV length does not match its header" }
        var offset = 12
        var formatValid = false
        var dataSize = 0L
        while (offset + 8 <= bytes.size) {
            val length = buffer.getInt(offset + 4).toLong() and 0xffffffffL
            val start = offset + 8
            require(length <= bytes.size - start) { "Incomplete WAV chunk" }
            when (tag(offset)) {
                "fmt " -> {
                    require(length >= 16) { "Incomplete WAV format" }
                    require(buffer.getShort(start).toInt() == 1 && buffer.getShort(start + 2).toInt() == 1 &&
                        buffer.getInt(start + 4) == 16_000 && buffer.getInt(start + 8) == 32_000 &&
                        buffer.getShort(start + 12).toInt() == 2 && buffer.getShort(start + 14).toInt() == 16) {
                        "Use mono, 16 kHz, 16-bit PCM WAV for this prototype"
                    }
                    formatValid = true
                }
                "data" -> dataSize += length
            }
            offset = (start.toLong() + length + length % 2).toInt()
        }
        require(formatValid && dataSize > 0 && dataSize % 2 == 0L) { "Missing WAV samples" }
        return dataSize * 1_000 / 32_000
    }
}
