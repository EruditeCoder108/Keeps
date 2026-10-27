package dev.keeps.core

import org.junit.Assert.*
import org.junit.Test

class MomentRankingTest {
    @Test fun separateMomentsSurviveWhileOverlappingDuplicatesAreRemoved() {
        val candidates = listOf(
            ScoredMoment("first", TimeWindow(0, 8000), .7f), ScoredMoment("best", TimeWindow(6000, 14000), .9f),
            ScoredMoment("duplicate", TimeWindow(6500, 14500), .8f), ScoredMoment("tail", TimeWindow(12000, 18000), .6f))
        assertEquals(listOf("best", "first", "tail"), MomentRanking.select(candidates).map { it.id })
    }
    @Test fun invalidScoresAndIntervalsCannotBecomeMatches() {
        assertTrue(MomentRanking.select(listOf(ScoredMoment("bad", TimeWindow(8, 8), .9f), ScoredMoment("nan", TimeWindow(0, 8), Float.NaN), ScoredMoment("negative", TimeWindow(0, 8), -.1f))).isEmpty())
    }
    @Test fun resultLimitIsAppliedAfterDeduplication() {
        assertEquals(listOf("a", "c"), MomentRanking.select(listOf(ScoredMoment("a", TimeWindow(0, 8000), .9f), ScoredMoment("b", TimeWindow(1, 8001), .8f), ScoredMoment("c", TimeWindow(20000, 28000), .7f)), 2).map { it.id })
    }
}
