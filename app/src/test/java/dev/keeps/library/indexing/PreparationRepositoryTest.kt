package dev.keeps.library.indexing

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import dev.keeps.library.catalog.*
import dev.keeps.library.inference.LocalEmbeddingProvider
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = KeepsApplication::class, shadows = [ScopedStorageEnvironment::class])
class PreparationRepositoryTest {
    private lateinit var app: KeepsApplication
    private lateinit var db: LibraryDatabase
    private lateinit var dao: LibraryDao
    private lateinit var controller: PreparationController
    private lateinit var repository: LibraryRepository
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as KeepsApplication
        app.getSharedPreferences("preparation", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build())
        db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).allowMainThreadQueries().build()
        dao = db.library()
        controller = PreparationController(app)
        controller.chargingOnly(true) // TestScheduler leaves charging constraints unmet: no native/model execution.
        repository = LibraryRepository(app, dao, LocalEmbeddingProvider(app), controller)
    }
    @After fun cleanup() { db.close(); WorkManager.getInstance(app).cancelAllWork().result.get(); WorkManagerTestInitHelper.closeWorkDatabase() }
    private fun row(id: String, status: IndexStatus = IndexStatus.QUEUED, automatic: Boolean = false) =
        AssetEntity(id, "content://fixture/$id", "$id.txt", "text/plain", AssetKind.TEXT, 20, status = status, automaticPreparation = automatic)

    @Test fun pauseAndResumeKeepOnlyTheOriginalIdsModeAndCheckpoints() = runBlocking {
        val first = row("selected-a"); val second = row("selected-b")
        listOf(first, second, row("not-selected", IndexStatus.IMPORTED)).forEach { dao.insertAsset(it) }
        dao.insertChunks(listOf(ChunkEntity("saved", first.id, "lexical-v1", text = "Retained section")))
        repository.pause()
        assertEquals(PreparationPhase.PAUSED, controller.controls.value.status.phase)
        assertEquals(2, dao.assets().count { it.status == IndexStatus.QUEUED })
        assertEquals(listOf("Retained section"), dao.assetChunks(first.id).map { it.text })
        repository.resumePreparation()
        assertTrue(controller.controls.value.chargingOnly)
        assertFalse(controller.controls.value.paused)
        assertEquals(IndexStatus.IMPORTED, dao.asset("not-selected")!!.status)
        assertEquals(2, dao.assets().count { it.status == IndexStatus.QUEUED })
        val work = WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().filter { !it.state.isFinished }
        assertEquals(1, work.size)
        assertTrue(work.single().constraints.requiresCharging())
    }
    @Test fun repeatedResumeAndAppOpeningDoNotAppendJobsOrInvalidateRunningWork() = runBlocking {
        dao.insertAsset(row("a")); repository.pause(); repository.resumePreparation()
        val revision = controller.controls.value.revision
        repeat(4) { repository.resumePreparation(); repository.resumePending() }
        assertEquals(revision, controller.controls.value.revision)
        assertEquals(1, WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().count { !it.state.isFinished })
    }
    @Test fun changingToANewSelectionCannotSilentlyResumeTheOldPausedQueue() = runBlocking {
        dao.insertAsset(row("a")); repository.pause()
        try { repository.queueSelected(setOf("different")); fail("Must require an explicit resume/end") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("paused run")) }
        assertTrue(controller.controls.value.paused)
        assertEquals("a", dao.nextPreparation()!!.id)
    }
    @Test fun endingRunKeepsDocumentAndTextChunksInsteadOfTreatingThemAsUnprepared() = runBlocking {
        val text = row("text"); val pdf = row("pdf").copy(kind = AssetKind.DOCUMENT, mimeType = "application/pdf")
        listOf(text, pdf).forEach { dao.insertAsset(it) }
        dao.insertChunks(listOf(ChunkEntity("text-section", text.id, "lexical-v1", text = "Retain me"), ChunkEntity("page", pdf.id, "model", pageNumber = 1)))
        repository.pause(); repository.endManualRun()
        assertEquals(PreparationPhase.IDLE, controller.controls.value.status.phase)
        assertEquals(IndexStatus.PARTIAL, dao.asset(text.id)!!.status)
        assertEquals(IndexStatus.PARTIAL, dao.asset(pdf.id)!!.status)
        assertEquals(1, dao.assetChunks(text.id).size); assertEquals(1, dao.assetChunks(pdf.id).size)
        assertFalse(dao.hasPendingPreparation())
    }
    @Test fun legacyPauseMigrationRestoresItsSelectionWithoutExpandingIt() = runBlocking {
        dao.insertAsset(row("old", IndexStatus.IMPORTED).copy(detail = "Preparation paused; completed content retained"))
        dao.insertAsset(row("other", IndexStatus.IMPORTED))
        dao.insertAsset(row("excluded", IndexStatus.PARTIAL).copy(detail = "Preparation paused; completed content retained", searchExcluded = true))
        controller.requestPause(); repository.resumePending()
        assertEquals("old", dao.nextPreparation()!!.id)
        assertEquals(IndexStatus.IMPORTED, dao.asset("other")!!.status)
        assertEquals(IndexStatus.PARTIAL, dao.asset("excluded")!!.status)
        assertTrue(controller.controls.value.paused)
    }
    @Test fun automaticAndManualCommandsCannotAccidentallyChangeEachOthersScope() = runBlocking {
        repository.automatic(true, setOf(AssetKind.IMAGE), setOf("approved"))
        repository.pause()
        try { repository.queueSelected(setOf("unapproved")); fail("Manual mode must be explicit") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("automatic preparation")) }
        assertEquals(setOf("approved"), controller.controls.value.automaticIds)
        repository.resumePreparation()
        assertEquals(setOf("approved"), controller.controls.value.automaticIds)
        assertTrue(controller.controls.value.automaticEnabled)
        assertFalse(controller.controls.value.paused)
    }
    @Test fun staleNotificationCommandDoesNotCancelANewerResumedRun() = runBlocking {
        dao.insertAsset(row("a")); repository.pause()
        val oldRevision = controller.controls.value.revision
        repository.resumePreparation()
        val resumed = controller.controls.value.revision
        repository.pause(oldRevision)
        assertFalse(controller.controls.value.paused)
        assertEquals(resumed, controller.controls.value.revision)
        assertEquals(1, WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().count { !it.state.isFinished })
    }
    @Test fun notificationReceiverAndSettingsRepositoryUseTheSamePausePath() = runBlocking {
        // Receiver uses the real application graph; it still has only this test's disposable JVM catalog.
        val graph = app.graph
        graph.preparation.chargingOnly(true)
        graph.dao.insertAsset(row("receiver-owned"))
        val revision = graph.preparation.commandResume()
        // Robolectric doesn't auto-register manifest receivers without intent filters. Android's explicit
        // PendingIntent does; register the same receiver here to exercise real onReceive/goAsync behavior.
        val receiver = PausePreparationReceiver()
        app.registerReceiver(receiver, IntentFilter(PausePreparationReceiver.PAUSE), Context.RECEIVER_NOT_EXPORTED)
        try {
            app.sendBroadcast(Intent(PausePreparationReceiver.PAUSE).setPackage(app.packageName).putExtra("revision", revision))
            withTimeout(10_000) {
                while (!graph.preparation.controls.value.status.canResume || graph.dao.asset("receiver-owned")?.detail?.startsWith("Preparation paused; queue") != true) {
                    shadowOf(android.os.Looper.getMainLooper()).idle(); delay(10)
                }
            }
            assertEquals("receiver-owned", graph.dao.nextPreparation()!!.id)
            val notification = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(PreparationNotifications.ID)
            assertEquals("Preparation paused", notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(listOf("Resume"), notification.actions.map { it.title.toString() })
        } finally { app.unregisterReceiver(receiver); graph.database.close() }
    }
    @Test fun disablingAutomaticWorkKeepsPdfCheckpointsAndCancelsItsPlanner() = runBlocking {
        repository.automatic(true, setOf(AssetKind.DOCUMENT), setOf("pdf"))
        dao.insertAsset(row("pdf", automatic = true).copy(kind = AssetKind.DOCUMENT, mimeType = "application/pdf"))
        dao.insertChunks(listOf(ChunkEntity("page", "pdf", "model", pageNumber = 1)))
        repository.automatic(false)
        assertEquals(IndexStatus.PARTIAL, dao.asset("pdf")!!.status)
        assertEquals(1, dao.assetChunks("pdf").size)
        assertEquals(PreparationPhase.IDLE, controller.controls.value.status.phase)
        assertFalse(controller.controls.value.automaticEnabled)
        assertTrue(WorkManager.getInstance(app).getWorkInfosByTag("automatic-planning").get().all { it.state.isFinished })
    }
    @Test fun aLateWorkerCannotEnqueueAContinuationAfterPauseOrANewerResume() = runBlocking {
        val oldRevision = controller.commandResume()
        repository.pause()
        IndexWorker.enqueueQueue(app, continuation = true, expectedRevision = oldRevision)
        assertTrue(WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().isEmpty())
        controller.commandResume()
        IndexWorker.enqueueQueue(app, continuation = true, expectedRevision = oldRevision)
        assertTrue(WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().isEmpty())
    }
    @Test fun resumeRecoversAnInterruptedIndexingRowWithoutAddingOtherFiles() = runBlocking {
        dao.insertAsset(row("interrupted", IndexStatus.INDEXING))
        dao.insertAsset(row("other", IndexStatus.IMPORTED))
        controller.requestPause()
        repository.resumePreparation()
        assertEquals("interrupted", dao.nextPreparation()!!.id)
        assertEquals(IndexStatus.IMPORTED, dao.asset("other")!!.status)
        assertFalse(controller.controls.value.paused)
    }
    @Test fun automaticApprovalQueuesKnownFilesWhileScreenIsOnWithoutDoingInference() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        // Metadata-only pack marker for queuing; TestScheduler leaves charging unmet, so no engine is loaded.
        val marker = java.io.File(app.filesDir, "models/metadata-only.litertlm").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        app.getSharedPreferences("model", Context.MODE_PRIVATE).edit().putString("digest", "metadata-only").commit()
        val provider = LocalEmbeddingProvider(app)
        val awake = PreparationController(app, { PreparationSensors(0, 0.2, 36.0, charging = true, interactive = true) })
        val repo = LibraryRepository(app, dao, provider, awake)
        try {
            dao.insertAsset(row("approved", IndexStatus.IMPORTED).copy(uri = "content://media/external_primary/images/media/101", kind = AssetKind.IMAGE, mimeType = "image/jpeg"))
            dao.insertAsset(row("other", IndexStatus.IMPORTED).copy(uri = "content://media/external_primary/images/media/102", kind = AssetKind.IMAGE, mimeType = "image/jpeg"))
            repo.automatic(true, setOf(AssetKind.IMAGE), setOf("approved"))
            assertEquals(IndexStatus.QUEUED, dao.asset("approved")!!.status)
            assertEquals(IndexStatus.IMPORTED, dao.asset("other")!!.status)
            assertTrue(dao.asset("approved")!!.automaticPreparation)
            assertEquals(PreparationPhase.WAITING, awake.controls.value.status.phase)
            assertTrue(awake.controls.value.waiting.contains("lock the screen"))
            assertFalse(provider.state.value.loaded)
        } finally { marker.delete(); app.getSharedPreferences("model", Context.MODE_PRIVATE).edit().clear().commit() }
    }
    @Test fun screenOffOpportunityAdvancesADelayedAutomaticQueueButPreservesPause() = runBlocking {
        val idle = PreparationController(app, { PreparationSensors(0, 0.2, 36.0, charging = true, interactive = false) })
        val repo = LibraryRepository(app, dao, LocalEmbeddingProvider(app), idle)
        idle.automatic(true, setOf(AssetKind.TEXT)); val revision = idle.commandResume()
        dao.insertAsset(row("a", automatic = true))
        WorkManager.getInstance(app).enqueueUniqueWork(IndexWorker.AUTO_QUEUE_NAME, androidx.work.ExistingWorkPolicy.KEEP,
            IndexWorker.request(delayMs = 300_000, automatic = true, chargingOnly = true, revision = revision)).result.get()
        repo.wakeAutomatic()
        var active = WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.AUTO_QUEUE_NAME).get().filter { !it.state.isFinished }
        assertEquals(1, active.size); assertEquals(0L, active.single().initialDelayMillis)
        repo.pause(); val pausedRevision = idle.controls.value.revision
        repo.wakeAutomatic()
        AutomaticPreparation.observeOpportunities(app, repo)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        active = WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.AUTO_QUEUE_NAME).get().filter { !it.state.isFinished }
        assertTrue(active.isEmpty()); assertTrue(idle.controls.value.paused)
        assertEquals(pausedRevision, idle.controls.value.revision)
        assertEquals("a", dao.nextPreparation(true)!!.id)
    }
    @Test fun reimportingTheSameModelDoesNotEraseCompatiblePreparedContent() = runBlocking {
        val namespace = LocalEmbeddingProvider(app).namespace
        dao.insertAsset(row("ready", IndexStatus.AI_READY))
        dao.insertChunks(listOf(ChunkEntity("vector", "ready", namespace, vector = byteArrayOf(1, 2, 3))))
        repository.applyModelChange(namespace)
        assertEquals(IndexStatus.AI_READY, dao.asset("ready")!!.status)
        assertArrayEquals(byteArrayOf(1, 2, 3), dao.assetChunks("ready").single().vector)
    }
    @Test fun aDifferentModelInvalidatesVectorsButKeepsLexicalText() = runBlocking {
        dao.insertAsset(row("ready", IndexStatus.AI_READY))
        dao.insertChunks(listOf(ChunkEntity("vector", "ready", "old-model", vector = byteArrayOf(1)), ChunkEntity("text", "ready", "lexical-v1", text = "Keep lexical content")))
        repository.applyModelChange("old-model")
        assertEquals(IndexStatus.IMPORTED, dao.asset("ready")!!.status)
        assertEquals("Keep lexical content", dao.assetChunks("ready").single().text)
    }
}
