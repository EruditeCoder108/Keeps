package dev.keeps.library.catalog

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.PreparationPace
import dev.keeps.library.indexing.PreparationController
import dev.keeps.library.indexing.PreparationSensors
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PreparationHeatTest {
    private fun isolated(block: (Context) -> Unit) {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "heat-probe-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(real) {
            override fun getSharedPreferences(name: String, mode: Int) = real.getSharedPreferences("$prefix-$name", mode)
        }
        try { block(isolated) } finally { real.deleteSharedPreferences("$prefix-preparation") }
    }
    @Test fun sensorDrivenCoolingResumesAndHeadroomPollingIsThrottled() = isolated { context ->
        var now = 0L
        var reads = 0
        var sensor = PreparationSensors(2, 0.95, 39.0, true)
        val controller = PreparationController(context, { refresh -> if (refresh) reads++; sensor }, { now }, { now })
        assertTrue(controller.blockedReason()!!.startsWith("Cooling down"))
        sensor = PreparationSensors(0, 0.3, 37.0, true)
        now = 9_999
        assertTrue(controller.blockedReason()!!.startsWith("Cooling down")); assertEquals(1, reads)
        now = 10_000
        assertNull(controller.blockedReason()); assertEquals(2, reads)
    }
    @Test fun automaticApprovalIsExplicitPersistsAndRequiresScreenOffCharging() = isolated { context ->
        var readings = PreparationSensors(0, 0.2, 35.0, charging = true, interactive = true)
        val controller = PreparationController(context, { readings })
        assertFalse(controller.controls.value.automaticEnabled)
        controller.automatic(true, setOf(dev.keeps.core.AssetKind.IMAGE), setOf("approved"))
        assertTrue(controller.blockedReason(true)!!.contains("idle"))
        readings = readings.copy(interactive = false)
        assertNull(controller.blockedReason(true))
        readings = readings.copy(charging = false)
        assertTrue(controller.blockedReason(true)!!.contains("charging"))
        val restored = PreparationController(context, { readings })
        assertEquals(setOf("approved"), restored.controls.value.automaticIds)
        assertEquals(setOf(dev.keeps.core.AssetKind.IMAGE), restored.controls.value.automaticKinds)
        readings = readings.copy(charging = true, lowMemory = true)
        assertTrue(restored.blockedReason(true)!!.contains("memory"))
        controller.automatic(false)
        assertFalse(controller.controls.value.automaticEnabled)
    }
    @Test fun sessionProgressSurvivesRestartsWithoutAFileIdSet() = isolated { context ->
        var now = 1_000_000L
        val controller = PreparationController(context, { PreparationSensors() }, { now }, { now })
        controller.beginSession(24)
        controller.recordWork(3_000)
        now += 10_000
        controller.progressSaved()
        val restored = PreparationController(context, { PreparationSensors() }, { now }, { now })
        assertEquals(24, restored.controls.value.sessionInitialReady)
        assertEquals(1_000_000L, restored.controls.value.sessionStartedAt)
        assertEquals(now, restored.controls.value.lastProgressAt)
        assertEquals(3_000L, restored.controls.value.sessionWorkMs)
    }
    @Test fun paceBatchChargingAndRestPersistWithoutClearingWork() = isolated { context ->
        var now = 100_000L
        var charging = false
        val sensor: (Boolean) -> PreparationSensors = { PreparationSensors(0, Double.NaN, 35.0, charging) }
        val controller = PreparationController(context, sensor, { now }, { now })
        assertEquals(PreparationPace.GENTLE, controller.controls.value.pace)
        assertEquals(25, controller.controls.value.batchSize)
        controller.recordWork(5_000)
        assertEquals(1_250L, controller.restRemainingMs())
        controller.batchSize(100); controller.chargingOnly(true); controller.pace(PreparationPace.BALANCED)
        assertTrue(controller.blockedReason()!!.startsWith("Waiting for charging"))
        charging = true
        assertNull(controller.blockedReason())
        val restored = PreparationController(context, sensor, { now }, { now })
        assertEquals(100, restored.controls.value.batchSize); assertTrue(restored.controls.value.chargingOnly)
        assertEquals(PreparationPace.BALANCED, restored.controls.value.pace)
        assertEquals(1_250L, restored.restRemainingMs())
        now -= 1_000_000
        assertEquals(0L, restored.restRemainingMs())
    }
}
