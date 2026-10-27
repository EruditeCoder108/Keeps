package dev.keeps.library.indexing

import android.app.NotificationManager
import android.content.Context
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import dev.keeps.library.catalog.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = KeepsApplication::class)
class PreparationWorkerTest {
    private lateinit var app: KeepsApplication
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as KeepsApplication
        app.getSharedPreferences("preparation", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build())
    }
    @After fun cleanup() { WorkManager.getInstance(app).cancelAllWork().result.get(); WorkManagerTestInitHelper.closeWorkDatabase() }
    private fun row(id: String) = AssetEntity(id, "content://fixture/$id", "$id.txt", "text/plain", AssetKind.TEXT, 20, status = IndexStatus.QUEUED)
    private fun worker(revision: Long, attempt: Int = 0) = TestListenableWorkerBuilder<IndexWorker>(app)
        .setInputData(workDataOf("revision" to revision)).setRunAttemptCount(attempt).build()

    @Test fun pauseDuringAnUninterruptibleStepSavesCheckpointAndStartsNoNextFile() = runBlocking {
        val graph = app.graph
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        graph.indexer = object : AssetPreparation {
            override suspend fun index(asset: AssetEntity, completed: List<ChunkEntity>, maxNewWindows: Int, checkpoint: suspend (ChunkEntity, String) -> Unit): IndexResult {
                calls.incrementAndGet(); entered.complete(Unit)
                withContext(NonCancellable) { release.await() } // Models a JNI call that cannot be cancelled mid-call.
                val chunk = ChunkEntity("saved-${asset.id}", asset.id, "lexical-v1", text = "Synthetic checkpoint")
                checkpoint(chunk, "One section saved")
                return IndexResult(listOf(chunk), IndexStatus.TEXT_READY, "Synthetic text ready")
            }
        }
        graph.dao.insertAsset(row("a")); graph.dao.insertAsset(row("b"))
        graph.preparation.beginSession(0)
        val revision = graph.preparation.commandResume()
        val task = async(Dispatchers.Default) { worker(revision).doWork() }
        try {
            withTimeout(10_000) { entered.await() }
            graph.repository.pause()
            assertEquals(PreparationPhase.PAUSING, graph.preparation.controls.value.status.phase)
            val pauseRevision = graph.preparation.controls.value.revision
            graph.repository.resumePreparation()
            assertEquals("Resume must wait for the native step", pauseRevision, graph.preparation.controls.value.revision)
            release.complete(Unit)
            withTimeout(10_000) { task.join() }
            assertTrue("Superseded worker must stop", task.isCancelled)
            assertEquals(1, calls.get())
            assertEquals(PreparationPhase.PAUSED, graph.preparation.controls.value.status.phase)
            assertEquals(2, graph.dao.assets().count { it.status == IndexStatus.QUEUED })
            assertEquals(1, graph.dao.assetChunks("a").size)
            assertTrue(graph.dao.assetChunks("b").isEmpty())
            val notification = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(PreparationNotifications.ID)
            assertEquals(listOf("Resume"), notification.actions.map { it.title.toString() })
        } finally {
            release.complete(Unit); task.cancelAndJoin(); graph.database.close()
        }
    }
    @Test fun repeatedResourceChecksScheduleAFixedDelayInsteadOfGrowingRetryBackoff() = runBlocking {
        val graph = app.graph
        graph.preparation.chargingOnly(true) // Robolectric's sticky battery state is unplugged.
        graph.dao.insertAsset(row("a"))
        val revision = graph.preparation.commandResume()
        try {
            val result = worker(revision, attempt = 20).doWork()
            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(PreparationPhase.WAITING, graph.preparation.controls.value.status.phase)
            assertTrue(graph.preparation.controls.value.waiting.contains("charging"))
            val requests = WorkManager.getInstance(app).getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get()
            assertEquals(1, requests.size)
            assertEquals(30_000L, requests.single().initialDelayMillis)
        } finally { WorkManager.getInstance(app).cancelAllWork().result.get(); graph.database.close() }
    }
    @Test fun aBadFileDoesNotPoisonTheRemainingQueueOrClaimSuccessfulCompletion() = runBlocking {
        val graph = app.graph
        val calls = AtomicInteger()
        graph.indexer = object : AssetPreparation {
            override suspend fun index(asset: AssetEntity, completed: List<ChunkEntity>, maxNewWindows: Int, checkpoint: suspend (ChunkEntity, String) -> Unit): IndexResult {
                calls.incrementAndGet()
                if (asset.id == "a") error("Synthetic decoder failure")
                return IndexResult(listOf(ChunkEntity("good", asset.id, "lexical-v1", text = "Good synthetic content")), IndexStatus.TEXT_READY, "Text ready")
            }
        }
        graph.dao.insertAsset(row("a")); graph.dao.insertAsset(row("b"))
        val revision = graph.preparation.commandResume()
        try {
            assertEquals(ListenableWorker.Result.success(), worker(revision).doWork())
            assertEquals(2, calls.get())
            assertEquals(IndexStatus.FAILED, graph.dao.asset("a")!!.status)
            assertEquals(IndexStatus.TEXT_READY, graph.dao.asset("b")!!.status)
            assertEquals(PreparationPhase.NEEDS_REVIEW, graph.preparation.controls.value.status.phase)
            assertFalse(graph.dao.hasPendingPreparation())
        } finally { graph.database.close() }
    }
}
