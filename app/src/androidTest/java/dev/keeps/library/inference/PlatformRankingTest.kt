package dev.keeps.library.inference

import dev.keeps.core.Ranking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Android's regex implementation must preserve the JVM lexical-search behavior. */
class PlatformRankingTest {
    @Test fun wordsMatchAcrossScriptsWithoutUnrelatedFilenameMatches() {
        assertEquals(0.0, Ranking.lexicalScore("green helmet", "fixture-tone.wav", ""), 0.0)
        assertEquals(1.0, Ranking.lexicalScore("green helmet", "fixture-note.txt", "Bring the green helmet to the workshop."), 0.0)
        assertEquals(3.0, Ranking.lexicalScore("बिल", "बिल.pdf", ""), 0.0)
    }
}
