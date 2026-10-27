package dev.keeps.core

import org.junit.Assert.*
import org.junit.Test

class PreparationSchedulingTest {
    @Test fun pausedAndPausingNeverOfferAnotherPause() {
        for (phase in listOf(PreparationPhase.PAUSING, PreparationPhase.PAUSED, PreparationPhase.COMPLETE, PreparationPhase.NEEDS_REVIEW))
            assertFalse(PreparationStatus(phase).canPause)
        assertFalse(PreparationStatus(PreparationPhase.PAUSING).canResume)
        assertTrue(PreparationStatus(PreparationPhase.PAUSED).canResume)
        assertFalse(PreparationStatus(PreparationPhase.PAUSED).ongoing)
    }
    @Test fun processingAndResourceWaitsHaveDifferentTitlesButBothCanPause() {
        val running = PreparationStatus(PreparationPhase.PROCESSING)
        val waiting = PreparationStatus(PreparationPhase.WAITING)
        assertNotEquals(running.title, waiting.title)
        assertTrue(running.canPause); assertTrue(waiting.canPause)
    }
    @Test fun aPassHasBothATimeAndFileBoundary() {
        assertTrue(PreparationScheduling.withinPass(47, 359_999))
        assertFalse(PreparationScheduling.withinPass(48, 0))
        assertFalse(PreparationScheduling.withinPass(0, 360_000))
    }
    @Test fun twoHundredPhotosDoNotAccumulateMinuteLongBurstRests() {
        val workPerPhoto = 7_800L // User's 26 active minutes / 200; simulation only, never a device ETA.
        for (warm in listOf(false, true)) {
            var work = 0L; var rest = 0L; var passElapsed = 0L; var files = 0; var passes = 1
            repeat(200) {
                if (!PreparationScheduling.withinPass(files, passElapsed)) { passes++; passElapsed = 0; files = 0 }
                val pause = PreparationHeat.restMillis(workPerPhoto, PreparationPace.GENTLE, warm)
                work += workPerPhoto; rest += pause; passElapsed += workPerPhoto + pause; files++
            }
            assertEquals(26 * 60_000L, work)
            assertEquals(if (warm) 13 * 60_000L else 390_000L, rest)
            assertTrue("Scheduler handoffs should not happen every four files", passes <= 7)
        }
    }
    @Test fun warmPacingDoesNotReplaceCoolingProtection() {
        assertTrue(PreparationHeat.warm(1, 0.85, 37.0, 0.9))
        assertFalse(PreparationHeat.warm(null, Double.NaN, null, 0.9))
        assertTrue(PreparationHeat.needsCooling(2, 0.2, 37.0, false))
        assertTrue(PreparationHeat.needsCooling(0, 0.2, 40.0, false))
        assertFalse(PreparationHeat.needsCooling(0, 0.2, 37.0, true))
    }
    @Test fun historicalFixedGentlePolicyAloneAddedSeventyMinutesToTheReportedWorkload() {
        var accumulated = 0L; var rest = 0L
        repeat(200) {
            accumulated += 7_800
            rest += maxOf(15_600L, if (accumulated >= 60_000) 60_000L else 0L)
            if (accumulated >= 60_000) accumulated = 0
        }
        assertEquals(70 * 60_000L + 30_000, rest)
    }
}
