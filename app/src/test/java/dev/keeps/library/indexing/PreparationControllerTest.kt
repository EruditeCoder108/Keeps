package dev.keeps.library.indexing

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import dev.keeps.core.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreparationControllerTest {
    private lateinit var app: Application
    private lateinit var controller: PreparationController
    private var now = 1_000_000L
    private var sensors = PreparationSensors(0, 0.2, 36.0, charging = true, interactive = false)
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("preparation", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSystemService(NotificationManager::class.java).cancelAll()
        controller = create()
    }
    private fun create() = PreparationController(app, { sensors }, { now }, { now })
    private fun notification(): Notification = requireNotNull(shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(PreparationNotifications.ID))
    private fun actions() = notification().actions?.map { it.title.toString() }.orEmpty()

    @Test fun settingsPauseImmediatelyRemovesPauseActionAndOnlyOffersResumeAfterWorkerStops() {
        val revision = controller.commandResume()
        assertTrue(controller.workerStarted("worker", revision))
        controller.publish(revision, PreparationPhase.PROCESSING, "Processing")
        assertEquals(listOf("Pause"), actions())
        controller.requestPause()
        assertEquals(PreparationPhase.PAUSING, controller.controls.value.status.phase)
        assertTrue(actions().isEmpty())
        assertFalse(controller.publish(revision, PreparationPhase.RESTING, "Late worker update"))
        assertEquals("Pausing preparation", notification().extras.getString(Notification.EXTRA_TITLE))
        controller.workerFinished("worker")
        assertEquals(PreparationPhase.PAUSED, controller.controls.value.status.phase)
        assertEquals(listOf("Resume"), actions())
        assertEquals(0, notification().flags and Notification.FLAG_ONGOING_EVENT)
    }
    @Test fun staleActionsCannotPauseOrResumeANewerRun() {
        val oldRevision = controller.commandResume()
        val oldAction = notification().actions.single().actionIntent
        controller.requestPause(oldRevision)
        val pausedRevision = controller.controls.value.revision
        val newRevision = controller.commandResume(pausedRevision)
        assertTrue(newRevision > oldRevision)
        assertFalse(controller.requestPause(oldRevision))
        assertEquals(-1L, controller.commandResume(pausedRevision))
        assertFalse(controller.controls.value.paused)
        val newAction = notification().actions.single().actionIntent
        assertNotEquals(shadowOf(oldAction).savedIntent.data, shadowOf(newAction).savedIntent.data)
    }
    @Test fun completedNotificationIsDismissibleAndHasNoStaleAction() {
        val revision = controller.commandResume()
        controller.publish(revision, PreparationPhase.COMPLETE, "Two ready")
        assertTrue(actions().isEmpty())
        assertEquals(0, notification().flags and Notification.FLAG_ONGOING_EVENT)
        controller.endSession()
        assertNull(shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(PreparationNotifications.ID))
    }
    @Test fun migrationClearsOldLongRestsAndPreservesApprovedScopeProgressAndPause() {
        val prefs = app.getSharedPreferences("preparation", Context.MODE_PRIVATE)
        prefs.edit().putInt("pacingPolicyVersion", 2).putLong("restUntil", now + 300_000).putBoolean("paused", true)
            .putBoolean("automaticEnabled", true).putStringSet("automaticKinds", setOf("IMAGE"))
            .putStringSet("automaticIds", setOf("chosen" )).putLong("sessionWorkMs", 26 * 60_000).commit()
        val migrated = create()
        assertEquals(0L, migrated.restRemainingMs())
        assertEquals(setOf("chosen"), migrated.controls.value.automaticIds)
        assertTrue(migrated.controls.value.automaticEnabled)
        assertTrue(migrated.controls.value.paused)
        assertEquals(26 * 60_000L, migrated.controls.value.sessionWorkMs)
    }
    @Test fun interruptedProcessDoesNotLeaveAPermanentPausingState() {
        app.getSharedPreferences("preparation", Context.MODE_PRIVATE).edit().putBoolean("paused", true)
            .putString("workerOwner", "old").putString("workerProcess", "different-process").commit()
        val restored = create()
        assertEquals(PreparationPhase.PAUSED, restored.controls.value.status.phase)
        assertTrue(restored.controls.value.status.canResume)
        assertFalse(restored.controls.value.status.detail.contains("Waiting for Android"))
    }
    @Test fun interruptedStopCommandFinishesWithoutRestartingWork() {
        app.getSharedPreferences("preparation", Context.MODE_PRIVATE).edit().putBoolean("paused", true)
            .putString("workerOwner", "old").putString("workerProcess", "different-process").putBoolean("endRequested", true).commit()
        val restored = create()
        assertEquals(PreparationPhase.IDLE, restored.controls.value.status.phase)
        assertFalse(restored.controls.value.paused)
    }
    @Test fun elapsedProcessingBreakAndOtherTimeAreDistinctAndSurviveRecreation() {
        controller.beginSession(25)
        val revision = controller.commandResume()
        now += 7_800
        controller.recordWork(7_800)
        controller.recordRest(1_950, controller.controls.value.sessionStartedAt)
        now += 22_200
        controller.publish(revision, PreparationPhase.COMPLETE)
        now += 3_600_000
        val restored = create().controls.value
        assertEquals(30_000L, restored.elapsedMs(now))
        assertEquals(7_800L, restored.sessionWorkMs)
        assertEquals(1_950L, restored.sessionRestMs)
        assertEquals(20_250L, restored.otherMs(now))
    }
    @Test fun staleFinalizersAndMetricsDoNotBelongToANewerSession() {
        controller.beginSession(1)
        val session = controller.controls.value.sessionStartedAt
        val revision = controller.commandResume()
        controller.workerStarted("old", revision)
        controller.requestPause(); controller.workerFinished("old")
        now += 10_000; controller.beginSession(2)
        val nextRevision = controller.commandResume()
        controller.workerStarted("new", nextRevision)
        controller.workerFinished("old")
        controller.recordWork(30_000, session, revision)
        assertEquals(0L, controller.controls.value.sessionWorkMs)
        controller.requestPause()
        assertEquals(PreparationPhase.PAUSING, controller.controls.value.status.phase)
        controller.workerFinished("new")
    }
    @Test fun sensorWaitsAndShortWarmRestsDoNotDisableThermalProtection() {
        var revision = controller.commandResume()
        sensors = sensors.copy(interactive = true)
        assertTrue(controller.blockedReason(true)!!.contains("idle"))
        sensors = sensors.copy(interactive = false, status = 1)
        assertNull(controller.blockedReason(true))
        controller.recordWork(7_800)
        assertEquals(3_900L, controller.restRemainingMs())
        now += 10_000; sensors = sensors.copy(status = 2)
        assertTrue(controller.blockedReason(true)!!.contains("Cooling"))
        controller.publish(revision, PreparationPhase.WAITING, "Cooling")
        assertEquals(listOf("Pause"), actions())
        controller.requestPause(); revision = controller.commandResume()
        assertEquals(0L, controller.restRemainingMs())
        assertTrue(controller.current(revision))
    }
}
