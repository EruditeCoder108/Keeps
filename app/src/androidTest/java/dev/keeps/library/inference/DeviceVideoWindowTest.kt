package dev.keeps.library.inference

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import dev.keeps.library.catalog.*
import dev.keeps.library.indexing.VideoAudioExtractor
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Own packaged generated fixture + temporary in-memory catalog. No user catalog/original writes. */
class DeviceVideoWindowTest {
    @Test fun aacTimelineAndInterruptedOrderedWindowsResumeOnActualPack(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsVideoTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        assertEquals("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c", graph.embeddings.state.value.digest)
        val file = File(context.cacheDir, "keeps-window-test.mp4")
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            instrumentation.context.assets.open("video/fixture-moments.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            val uri = Uri.fromFile(file)
            val opened = VideoAudioExtractor.open(context, uri)
            assertTrue(opened.trackPresent)
            assertNotNull(opened.decoder)
            opened.decoder!!.use { audio ->
                val first = audio.readWindow(0, 8_000)!!
                val second = audio.readWindow(6_000, 14_000)!!
                assertEquals(128_000, first.size)
                assertEquals(128_000, second.size)
                assertTrue(first.count { it.toInt() != 0 } > 100_000)
                assertArrayEquals(first.copyOfRange(96_000, 128_000), second.copyOfRange(0, 32_000))
                assertFalse(audio.failed)
            }
            val asset = AssetEntity("window-test", uri.toString(), "fixture-moments.mp4", "video/mp4", AssetKind.VIDEO, file.length())
            val dao = db.library()
            dao.insertAsset(asset)
            try {
                graph.indexer.index(asset) { chunk, detail ->
                    assertTrue(dao.checkpointMedia(asset, chunk, detail))
                    throw CancellationException("Fixture interruption after one committed window")
                }
                fail("Expected interruption")
            } catch (_: CancellationException) { /* Resume must retain this committed window. */ }
            val saved = dao.assetChunks(asset.id)
            assertEquals(1, saved.size)
            var newWindows = 0
            val batch = graph.indexer.index(asset, saved, maxNewWindows = 1) { chunk, detail ->
                assertTrue(dao.checkpointMedia(asset, chunk, detail)); newWindows++
            }
            assertTrue(batch.resume)
            assertEquals(2, batch.chunks.size)
            val result = graph.indexer.index(asset, dao.assetChunks(asset.id), maxNewWindows = 1) { chunk, detail ->
                assertTrue(dao.checkpointMedia(asset, chunk, detail)); newWindows++
            }
            assertFalse(result.resume)
            assertEquals(2, newWindows)
            assertEquals(IndexStatus.AI_READY, result.status)
            assertEquals(listOf(0L, 6_000L, 12_000L), result.chunks.map { it.startMs })
            assertEquals(listOf(8_000L, 14_000L, 18_000L), result.chunks.map { it.endMs })
            assertArrayEquals(saved.single().vector, result.chunks.first().vector)
            result.chunks.forEach {
                assertEquals(VideoWindows.RECIPE, it.recipe); assertEquals(4, it.frameCount)
                assertTrue(it.hasAudio); assertFalse(it.audioUnavailable)
                assertTrue(Vectors.decode(it.vector!!).all { value -> value.isFinite() })
                assertTrue(Vectors.decode(it.visualVector!!).all { value -> value.isFinite() })
            }
            val query = graph.embeddings.embed(LocalEmbeddingProvider.query("blue square"), graph.embeddings.namespace)
            val metrics = JSONArray()
            val scored = result.chunks.map { chunk ->
                val sequence = Vectors.cosine(query, Vectors.decode(chunk.vector!!))
                val visual = Vectors.cosine(query, Vectors.decode(chunk.visualVector!!))
                metrics.put(JSONObject().put("startMs", chunk.startMs).put("endMs", chunk.endMs).put("sequenceCosine", sequence).put("visualCosine", visual))
                chunk to maxOf(sequence, visual)
            }
            File(context.cacheDir, "keeps-video-probe.json").writeText(JSONObject().put("fixture", "18-second generated chapters with 48 kHz stereo AAC")
                .put("query", "blue square").put("windows", metrics).put("resumeNewWindows", newWindows).toString(2))
            val best = scored.maxBy { it.second }.first
            assertEquals(6_000L, best.startMs)
            val moments = MomentRanking.select(scored.map { (chunk, score) -> ScoredMoment(chunk.id, TimeWindow(chunk.startMs!!, chunk.endMs!!), score) })
            assertEquals(3, moments.size)
        } finally { db.close(); file.delete(); graph.embeddings.unload() }
    }
}
