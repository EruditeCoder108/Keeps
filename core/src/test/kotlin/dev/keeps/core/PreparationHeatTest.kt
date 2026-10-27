package dev.keeps.core

import org.junit.Assert.*
import org.junit.Test

class PreparationHeatTest {
    @Test fun anyAvailableHeatSignalCanRequestCooling() {
        assertTrue(PreparationHeat.needsCooling(2, null, null, false))
        assertTrue(PreparationHeat.needsCooling(0, 0.9, null, false))
        assertTrue(PreparationHeat.needsCooling(0, Double.NaN, 40.0, false))
        assertFalse(PreparationHeat.needsCooling(1, 0.5, 39.0, false))
    }
    @Test fun coolingHasHysteresisAndWaitsForAllKnownSignals() {
        assertTrue(PreparationHeat.needsCooling(0, 0.85, 37.0, true))
        assertTrue(PreparationHeat.needsCooling(0, 0.3, 39.0, true))
        assertTrue(PreparationHeat.needsCooling(2, 0.3, 37.0, true))
        assertFalse(PreparationHeat.needsCooling(1, 0.8, 38.0, true))
    }
    @Test fun missingOrInvalidSensorsDoNotPretendToBeHot() {
        assertFalse(PreparationHeat.needsCooling(null, Double.NaN, Double.NaN, false))
        assertFalse(PreparationHeat.needsCooling(0, -1.0, -100.0, false))
        assertFalse(PreparationHeat.needsCooling(0, Double.POSITIVE_INFINITY, 100.0, false))
    }
    @Test fun vendorHeadroomLevelsAvoidAFalsePermanentCooldown() {
        val (stop, resume) = PreparationHeat.headroomBands(0.8333333, 0.96666664)
        assertFalse(PreparationHeat.needsCooling(1, 0.8416, 37.0, false, stop, resume))
        assertTrue(PreparationHeat.needsCooling(1, 0.92, 37.0, false, stop, resume))
        assertTrue(PreparationHeat.needsCooling(1, 0.85, 37.0, true, stop, resume))
        assertFalse(PreparationHeat.needsCooling(1, 0.82, 37.0, true, stop, resume))
        assertEquals(0.9 to 0.8, PreparationHeat.headroomBands(Double.NaN, null))
    }
    @Test fun gentleAddsMoreRestWithoutChangingInferenceInputs() {
        assertEquals(1_250L, PreparationHeat.restMillis(5_000, PreparationPace.GENTLE))
        assertEquals(0L, PreparationHeat.restMillis(5_000, PreparationPace.BALANCED))
        assertEquals(2_500L, PreparationHeat.restMillis(5_000, PreparationPace.GENTLE, warm = true))
    }
    @Test fun shortRestsAreBoundedAndNoWorkDoesNotRequireARest() {
        assertEquals(10_000L, PreparationHeat.restMillis(Long.MAX_VALUE, PreparationPace.GENTLE, warm = true))
        assertEquals(5_000L, PreparationHeat.restMillis(Long.MAX_VALUE, PreparationPace.GENTLE))
        assertEquals(0L, PreparationHeat.restMillis(-1, PreparationPace.GENTLE))
        assertEquals(0L, PreparationHeat.restMillis(0, PreparationPace.BALANCED, warm = true))
    }
}
