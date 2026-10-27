package dev.keeps.library.inference

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.keeps.library.indexing.IndexWorker
import androidx.work.WorkManager
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import dev.keeps.library.indexing.VideoAudioExtractor
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real generated-only SAF AAC/MP3 preparation, ninth-window continuation and retrieval. */
class DeviceAudioLibraryTest {
    @Test fun compressedStereoRecordingsPrepareAllWindowsAndPreserveLibrary(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsAudioLibraryTests") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        val oldNames = setOf("fixture-01.png", "fixture-02.png", "fixture-note.txt", "fixture-pages.pdf", "fixture-tone.wav", "fixture-clip.mp4", "fixture-moments.mp4")
        val added = setOf("fixture-audio-long.m4a", "fixture-audio-scenes.mp3")
        val before = graph.dao.assets()
        assumeTrue("Generated fixture library only", before.size >= 6 && before.all { it.name in oldNames + added })
        val location = graph.dao.locations().singleOrNull { Uri.decode(it.uri).endsWith("primary:Download/KeepsBrowseTest") }
        assumeTrue("Explicit fixture-folder grant required", location != null)
        assertEquals("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c", graph.embeddings.state.value.digest)
        fun digest(uri: String): String = context.contentResolver.openInputStream(Uri.parse(uri))!!.use { input ->
            val sha = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; sha.update(buffer, 0, count) }
            sha.digest().joinToString("") { "%02x".format(it) }
        }
        val originals = before.associate { it.id to digest(it.uri) }
        val oldChunks = before.filter { it.name in oldNames }.associate { it.id to graph.dao.assetChunks(it.id) }
        val scan = graph.importer.scan(location!!.uri)
        assertFalse(scan.limited); assertEquals(0, scan.failedFolders)
        val recordings = graph.dao.assets().filter { it.name in added }
        assertEquals(2, recordings.size)
        val reports = JSONArray()
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            val mp3 = recordings.single { it.name.endsWith(".mp3") }
            VideoAudioExtractor.open(context, Uri.parse(mp3.uri)).decoder!!.use { decoder ->
                val first = decoder.readWindow(0, 8_000)!!
                val second = decoder.readWindow(6_000, 14_000)!!
                assertEquals(128_000, first.size); assertEquals(128_000, second.size)
                assertArrayEquals(first.copyOfRange(96_000, 128_000), second.copyOfRange(0, 32_000))
                assertFalse(decoder.failed)
            }
            val work = WorkManager.getInstance(context)
            for (recording in recordings) {
                val previousJobs = work.getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().map { it.id }.toSet()
                assertTrue(graph.repository.queue(recording))
                withTimeout(240_000) {
                    while (true) {
                        delay(1_000)
                        val jobs = work.getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().filter { it.id !in previousJobs }
                        assertFalse("Audio worker failed", jobs.any { it.state == WorkInfo.State.FAILED })
                        if (graph.dao.asset(recording.id)!!.status == IndexStatus.AI_READY && jobs.isNotEmpty() && jobs.all { it.state.isFinished }) break
                    }
                }
                val chunks = graph.dao.assetChunks(recording.id).sortedBy { it.startMs }
                val count = if (recording.name.endsWith(".m4a")) 9 else 3
                assertEquals(count, chunks.size)
                assertEquals(List(count) { it * 6_000L }, chunks.map { it.startMs })
                chunks.forEach {
                    assertEquals(AudioWindows.RECIPE, it.recipe); assertEquals(0, it.frameCount)
                    assertTrue(it.hasAudio); assertNull(it.visualVector)
                    val vector = Vectors.decode(it.vector!!)
                    assertEquals(768, vector.size); assertTrue(vector.all { v -> v.isFinite() })
                }
                val jobs = work.getWorkInfosForUniqueWork(IndexWorker.QUEUE_NAME).get().filter { it.id !in previousJobs }
                assertTrue(jobs.all { it.state == WorkInfo.State.SUCCEEDED })
                if (count == 9) assertTrue("Ninth audio window needs continuation", jobs.size >= 2 || jobs.sumOf { it.runAttemptCount } >= 2)
                reports.put(JSONObject().put("name", recording.name).put("windows", chunks.size).put("workRecords", jobs.size).put("workerAttempts", jobs.sumOf { it.runAttemptCount }))
            }
            val response = graph.repository.search("a steady electronic tone", AssetKind.AUDIO)
            assertNull(response.notice)
            assertTrue(response.hits.any { it.asset.id == mp3.id && it.moments.size == 3 })
            val scoped = graph.repository.search("white noise", AssetKind.AUDIO, assetId = mp3.id)
            assertNull(scoped.notice); assertEquals(listOf(mp3.id), scoped.hits.map { it.asset.id })
            assertEquals(3, scoped.hits.single().moments.size)
            before.filter { it.name in oldNames }.forEach {
                assertEquals(it, graph.dao.asset(it.id)); assertEquals(originals[it.id], digest(it.uri))
            }
            oldChunks.forEach { (id, chunks) ->
                val now = graph.dao.assetChunks(id).associateBy { it.id }
                assertEquals(chunks.size, now.size)
                chunks.forEach {
                    assertArrayEquals(it.vector, now[it.id]!!.vector)
                    assertArrayEquals(it.visualVector, now[it.id]!!.visualVector)
                }
            }
            File(context.cacheDir, "keeps-audio-library-probe.json").writeText(JSONObject()
                .put("recordings", reports).put("stereoMp3OverlapExact", true).put("priorOriginalsAndVectorsPreserved", true)
                .put("audioCandidates", scoped.hits.single().moments.size).put("elapsedMs", android.os.SystemClock.elapsedRealtime() - start).toString(2))
        } finally { graph.embeddings.unload() }
    }
}
