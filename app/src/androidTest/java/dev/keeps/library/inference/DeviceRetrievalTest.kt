package dev.keeps.library.inference

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import dev.keeps.core.Vectors
import dev.keeps.library.KeepsApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

/** Opt-in integration test against only the synthetic tools/create_device_fixtures.py library.
 * Import the official portable model and six fixtures through SAF and index them first.
 * Run with: am instrument -w -e keepsDeviceFixtureTests true .../AndroidJUnitRunner
 * Never imports, removes, or reindexes user files. Native inference runs on the actual phone.
 */
class DeviceRetrievalTest {
    @Test fun officialModelRetrievesSyntheticFixtures(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsDeviceFixtureTests") == "true")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KeepsApplication
        val graph = application.graph
        val provider = graph.embeddings
        val names = setOf("fixture-01.png", "fixture-02.png", "fixture-note.txt", "fixture-pages.pdf", "fixture-tone.wav", "fixture-clip.mp4")
        val assets = graph.dao.assets()
        // Refuse to exercise this repository test if any personal file has been imported.
        assumeTrue("Use only the dedicated synthetic test library", assets.size == names.size && assets.map { it.name }.toSet() == names)
        assertEquals("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c", provider.state.value.digest)
        assets.forEach { asset ->
            assertEquals(asset.name, if (asset.kind == AssetKind.VIDEO) IndexStatus.PARTIAL else IndexStatus.AI_READY, asset.status)
        }
        val chunks = graph.dao.chunks(provider.namespace)
        assertEquals(7, chunks.count { it.vector != null })
        chunks.filter { it.vector != null }.forEach { chunk ->
            val vector = Vectors.decode(chunk.vector!!)
            assertEquals(768, vector.size)
            assertTrue(vector.all { it.isFinite() })
            assertTrue(abs(Vectors.cosine(vector, vector) - 1f) < 0.0001f)
        }
        try {
            suspend fun search(query: String, kind: AssetKind) = graph.repository.search(query, kind).also {
                assertNull(it.notice)
                assertTrue("No result for $query", it.hits.isNotEmpty())
            }
            val start = SystemClock.elapsedRealtime()
            assertEquals("fixture-01.png", search("red circle on a white background", AssetKind.IMAGE).hits.first().asset.name)
            assertEquals("fixture-02.png", search("blue square on a white background", AssetKind.IMAGE).hits.first().asset.name)
            assertEquals("fixture-note.txt", search("appointment to fix a bicycle tire", AssetKind.TEXT).hits.first().asset.name)
            assertEquals(2, search("blue square", AssetKind.DOCUMENT).hits.first().chunk!!.pageNumber)
            val video = search("red circle", AssetKind.VIDEO).hits.first()
            assertEquals("fixture-clip.mp4", video.asset.name)
            assertEquals(if (video.chunk!!.recipe == dev.keeps.core.VideoWindows.RECIPE) 0L else 2_000L, video.chunk!!.startMs)
            val audio = search("a steady electronic tone", AssetKind.AUDIO).hits.first()
            assertEquals("fixture-tone.wav", audio.asset.name)
            assertEquals(3_000L, audio.chunk!!.endMs)
            val allFiles = graph.repository.search("green helmet")
            assertNull(allFiles.notice)
            assertEquals("fixture-note.txt", allFiles.hits.first().asset.name)
            assertEquals("fixture-note.txt", graph.repository.search("KEEPS ORCHID 731").hits.first().asset.name)
            Log.i("KeepsDeviceTest", "Eight synthetic retrieval queries completed in ${SystemClock.elapsedRealtime() - start} ms")
        } finally {
            provider.unload()
        }
    }
}
