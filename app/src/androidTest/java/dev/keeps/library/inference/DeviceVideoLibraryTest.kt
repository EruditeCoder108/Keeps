package dev.keeps.library.inference

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.keeps.library.indexing.IndexWorker
import androidx.work.WorkManager
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in production WorkManager/retrieval test using only the dedicated generated SAF library.
 * Push fixture-moments.mp4 into the already granted KeepsBrowseTest/Media folder first.
 * Retains the prepared fixture for manual use; never clears the catalog or replaces originals.
 */
class DeviceVideoLibraryTest {
    @Test fun chosenFolderWorkerScopedMomentsAndPlayback(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsVideoLibraryTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        val names = setOf("fixture-01.png", "fixture-02.png", "fixture-note.txt", "fixture-pages.pdf", "fixture-tone.wav", "fixture-clip.mp4")
        val before = graph.dao.assets()
        assumeTrue("Requires generated fixtures only", before.map { it.name }.toSet() in setOf(names, names + "fixture-moments.mp4"))
        val location = graph.dao.locations().singleOrNull { Uri.decode(it.uri).endsWith("primary:Download/KeepsBrowseTest") }
        assumeTrue("Requires the explicit fixture folder grant", location != null)
        assertEquals("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c", graph.embeddings.state.value.digest)
        fun digest(uri: String): String = context.contentResolver.openInputStream(Uri.parse(uri))!!.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val originals = before.associate { it.id to digest(it.uri) }
        val oldChunks = before.filter { it.name in names }.associate { it.id to graph.dao.assetChunks(it.id) }
        val scan = graph.importer.scan(location!!.uri)
        assertFalse(scan.limited); assertEquals(0, scan.failedFolders)
        assertEquals(names + "fixture-moments.mp4", graph.dao.assets().map { it.name }.toSet())
        val video = graph.dao.assets().single { it.name == "fixture-moments.mp4" }
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            assertTrue(graph.repository.queue(video))
            val work = WorkManager.getInstance(context)
            withTimeout(240_000) {
                do {
                    delay(1_000)
                    val jobs = work.getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get()
                    assertFalse("Preparation worker failed", jobs.any { it.state == WorkInfo.State.FAILED })
                    val current = graph.dao.asset(video.id)!!
                    if (jobs.isNotEmpty() && jobs.all { it.state.isFinished } && current.status == IndexStatus.AI_READY) break
                } while (true)
            }
            val chunks = graph.dao.assetChunks(video.id).sortedBy { it.startMs }
            assertEquals(listOf(0L, 6_000L, 12_000L), chunks.map { it.startMs })
            chunks.forEach { chunk ->
                assertEquals(VideoWindows.RECIPE, chunk.recipe); assertEquals(4, chunk.frameCount)
                assertTrue(chunk.hasAudio); assertFalse(chunk.audioUnavailable)
                listOf(chunk.vector, chunk.visualVector).forEach {
                    val vector = Vectors.decode(it!!)
                    assertEquals(768, vector.size); assertTrue(vector.all { value -> value.isFinite() })
                }
            }
            val library = graph.repository.search("blue square", AssetKind.VIDEO)
            assertNull(library.notice)
            assertEquals(video.id, library.hits.first().asset.id)
            assertEquals(6_000L, library.hits.first().chunk!!.startMs)
            assertEquals(3, library.hits.first().moments.size)
            val scoped = graph.repository.search("blue square", AssetKind.VIDEO, assetId = video.id)
            assertNull(scoped.notice); assertEquals(listOf(video.id), scoped.hits.map { it.asset.id })
            assertEquals(6_000L, scoped.hits.single().chunk!!.startMs)
            assertEquals(listOf(video.id), graph.repository.search("fixture", content = false, assetId = video.id).hits.map { it.asset.id })
            assertTrue(graph.repository.search("note", content = false, assetId = video.id).hits.isEmpty())

            // The same Media3 engine and SAF URI used by the viewer must decode, seek and play.
            lateinit var player: ExoPlayer
            val ready = CountDownLatch(1)
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build()
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                })
                player.setMediaItem(MediaItem.fromUri(video.uri)); player.prepare(); player.seekTo(5_000)
            }
            try {
                assertTrue("Media3 did not become ready", ready.await(20, TimeUnit.SECONDS))
                instrumentation.runOnMainSync {
                    assertTrue(player.duration in 17_500..18_500)
                    assertEquals(5_000L, player.currentPosition)
                    player.seekTo(6_000); assertEquals(6_000L, player.currentPosition)
                    player.play()
                }
                delay(1_000)
                instrumentation.runOnMainSync {
                    assertNull(player.playerError)
                    assertTrue("Playback did not advance", player.currentPosition > 6_100)
                    player.pause(); player.seekTo(12_000); assertEquals(12_000L, player.currentPosition)
                }
            } finally { instrumentation.runOnMainSync { player.release() } }

            before.forEach { asset ->
                assertEquals(asset, graph.dao.asset(asset.id))
                assertEquals(originals[asset.id], digest(asset.uri))
            }
            oldChunks.forEach { (id, old) ->
                val retained = graph.dao.assetChunks(id).associateBy { it.id }
                assertEquals(old.size, retained.size)
                old.forEach { chunk ->
                    assertEquals(chunk.text, retained[chunk.id]!!.text)
                    assertArrayEquals(chunk.vector, retained[chunk.id]!!.vector)
                }
            }
            File(context.cacheDir, "keeps-video-library-probe.json").writeText(JSONObject()
                .put("catalogAssets", graph.dao.assets().size).put("videoWindows", chunks.size)
                .put("framesPerWindow", 4).put("soundtrackWindows", chunks.count { it.hasAudio })
                .put("libraryBestStartMs", library.hits.first().chunk!!.startMs)
                .put("scopedBestStartMs", scoped.hits.first().chunk!!.startMs)
                .put("candidateMoments", scoped.hits.single().moments.size)
                .put("originalsAndPriorChunksPreserved", true).put("media3SeekAndPlayback", true)
                .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started).toString(2))
        } finally { graph.embeddings.unload() }
    }
}
