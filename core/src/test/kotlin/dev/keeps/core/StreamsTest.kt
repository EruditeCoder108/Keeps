package dev.keeps.core

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class StreamsTest {
    @Test fun boundedReadLeavesTailUnread() {
        val stream = ByteArrayInputStream("abcdef".toByteArray())
        assertEquals("abc", stream.readBounded(3).toString(Charsets.UTF_8))
        assertEquals('d'.code, stream.read())
    }
    @Test fun shortStreamReturnsAvailableBytes() {
        assertEquals("abc", ByteArrayInputStream("abc".toByteArray()).readBounded(100).toString(Charsets.UTF_8))
    }
}
