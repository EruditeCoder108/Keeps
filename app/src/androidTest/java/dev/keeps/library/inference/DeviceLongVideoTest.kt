package dev.keeps.library.inference

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import dev.keeps.library.catalog.AssetEntity
import dev.keeps.library.indexing.IndexWorker
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real production workers, nine new native windows, temporary owned test source/catalog row.
 * No activity/repository search during the test: the private file is not a SAF user source.
 */
class DeviceLongVideoTest {
    @Test fun ninthWindowContinuesAndFindsRepeatedBlueChapters(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsContinuationTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        assertEquals("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c", graph.embeddings.state.value.digest)
        val file = File(context.cacheDir, "fixture-long-worker-test.mp4")
        val asset = AssetEntity("native-worker-test-${UUID.randomUUID()}", Uri.fromFile(file).toString(), "fixture-long-worker.mp4", "video/mp4", AssetKind.VIDEO, 0)
        val work = WorkManager.getInstance(context)
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            instrumentation.context.assets.open("video/fixture-long-worker.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            graph.dao.insertAsset(asset.copy(size = file.length()))
            IndexWorker.setPaused(context, false)
            work.enqueueUniqueWork("asset-${asset.id}", ExistingWorkPolicy.KEEP, IndexWorker.request(asset.id)).result.get()
            var sawPartialEight = false
            var lastCount = -1
            var maxObservedPssKb = 0
            withTimeout(420_000) {
                while (true) {
                    delay(1_000)
                    val row = graph.dao.asset(asset.id)!!
                    val count = graph.dao.assetChunks(asset.id).size
                    if (count != lastCount) {
                        lastCount = count
                        maxObservedPssKb = maxOf(maxObservedPssKb, android.os.Debug.getPss().toInt())
                        android.util.Log.i("KeepsContinuationTest", "Generated video: $count/9 windows persisted")
                    }
                    val jobs = work.getWorkInfosForUniqueWork("asset-${asset.id}").get()
                    if (count == 9) File(context.cacheDir, "keeps-long-video-state.json").writeText(JSONObject()
                        .put("status", row.status.name).put("detail", row.detail).put("workRecords", jobs.size)
                        .put("attempts", jobs.sumOf { it.runAttemptCount }).put("states", JSONArray(jobs.map { it.state.name })).toString(2))
                    assertFalse("Video worker failed", jobs.any { it.state == WorkInfo.State.FAILED })
                    if (row.status == IndexStatus.PARTIAL && graph.dao.assetChunks(asset.id).size == 8) sawPartialEight = true
                    if (row.status == IndexStatus.AI_READY && jobs.isNotEmpty() && jobs.all { it.state == WorkInfo.State.SUCCEEDED }) break
                }
            }
            val chunks = graph.dao.assetChunks(asset.id).sortedBy { it.startMs }
            assertEquals((0L..48_000L step 6_000).toList(), chunks.map { it.startMs })
            val duration = android.media.MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, Uri.fromFile(file))
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            }
            assertTrue(duration in 53_900..54_100)
            assertEquals(duration, chunks.last().endMs)
            assertEquals(9, chunks.size)
            chunks.forEach { chunk ->
                assertEquals(VideoWindows.RECIPE, chunk.recipe); assertEquals(4, chunk.frameCount)
                assertTrue(chunk.hasAudio); assertFalse(chunk.audioUnavailable)
                assertEquals(768, Vectors.decode(chunk.vector!!).size)
                assertTrue(Vectors.decode(chunk.visualVector!!).all { it.isFinite() })
            }
            val query = graph.embeddings.embed(LocalEmbeddingProvider.query("blue square"), graph.embeddings.namespace)
            val scored = chunks.map { chunk ->
                ScoredMoment(chunk.id, TimeWindow(chunk.startMs!!, chunk.endMs!!),
                    maxOf(Vectors.cosine(query, Vectors.decode(chunk.vector!!)), Vectors.cosine(query, Vectors.decode(chunk.visualVector!!))))
            }
            val selected = MomentRanking.select(scored, limit = 3)
            val jobs = work.getWorkInfosForUniqueWork("asset-${asset.id}").get()
            File(context.cacheDir, "keeps-long-video-probe.json").writeText(JSONObject()
                .put("windows", chunks.size).put("successfulWorkRecords", jobs.size).put("workerAttempts", jobs.sumOf { it.runAttemptCount })
                .put("observedPartialEight", sawPartialEight).put("bestBlueStartsMs", JSONArray(selected.map { it.window.startMs }.sorted()))
                .put("maxCheckpointObservedPssKb", maxObservedPssKb)
                .put("scores", JSONArray(scored.map { JSONObject().put("startMs", it.window.startMs).put("cosine", it.score) }))
                .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started).toString(2))
            assertTrue("Nine bounded windows need more than one execution", jobs.size >= 2 || jobs.sumOf { it.runAttemptCount } >= 2)
            assertEquals(listOf(6_000L, 24_000L, 42_000L), selected.map { it.window.startMs }.sorted())
        } finally {
            work.cancelUniqueWork("asset-${asset.id}").result.get()
            graph.dao.remove(asset.id); file.delete(); graph.embeddings.unload()
        }
    }
}
