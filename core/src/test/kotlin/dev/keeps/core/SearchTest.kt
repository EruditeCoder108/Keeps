package dev.keeps.core

import org.junit.Assert.*
import org.junit.Test

class SearchTest {
    @Test fun windowsCoverTailWithoutDuplicateTail() {
        assertEquals(listOf(TimeWindow(0, 8000), TimeWindow(6000, 14000), TimeWindow(12000, 17000)), Chunking.windows(17000))
        assertEquals(listOf(TimeWindow(0, 8000)), Chunking.windows(8000))
        assertTrue(Chunking.windows(0).isEmpty())
    }
    @Test fun textChunksKeepBoundaryContent() {
        val chunks = Chunking.text("abcdefghijklmnop", 8, 2)
        assertEquals(listOf("abcdefgh", "ghijklmn", "mnop"), chunks)
    }
    @Test fun vectorsRoundTripAndCompareDirections() {
        val vector = floatArrayOf(3f, 4f)
        assertArrayEquals(vector, Vectors.decode(Vectors.encode(vector)), 0f)
        assertEquals(1f, Vectors.cosine(vector, floatArrayOf(6f, 8f)), 0.0001f)
        assertEquals(-1f, Vectors.cosine(vector, floatArrayOf(-3f, -4f)), 0.0001f)
    }
    @Test fun rejectsInvalidVectors() {
        for (bad in listOf(floatArrayOf(), floatArrayOf(0f), floatArrayOf(Float.NaN))) {
            assertThrows(IllegalArgumentException::class.java) { Vectors.normalize(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { Vectors.decode(byteArrayOf(1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { Vectors.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)) }
    }
    @Test fun lexicalMatchingUsesWholeWordsAndUnicode() {
        assertEquals(0.0, Ranking.lexicalScore("cat", "vacation.jpg", ""), 0.0)
        assertEquals(3.0, Ranking.lexicalScore("बिल", "बिल.pdf", ""), 0.0)
        assertEquals(1.0, Ranking.lexicalScore("invoice", "receipt.pdf", "invoice 123"), 0.0)
    }
    @Test fun fusionDoesNotCountDuplicateResultsTwice() {
        val fused = Ranking.fuse(listOf("a", "a", "b"), listOf("b"))
        assertTrue(fused.getValue("b") > fused.getValue("a"))
        assertEquals(1.0 / 61, fused.getValue("a"), 0.00001)
    }
}
