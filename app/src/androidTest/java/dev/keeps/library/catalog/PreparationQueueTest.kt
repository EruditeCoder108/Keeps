package dev.keeps.library.catalog

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import dev.keeps.library.inference.LocalEmbeddingProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreparationQueueTest {
    @Test fun missingModelCannotQueueMediaOrRepeatReadyText() = runBlocking {
        val actual = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "prep-model-guard-${UUID.randomUUID()}"
        val privateRoot = File(actual.cacheDir, prefix).apply { mkdirs() }
        val isolated = object : ContextWrapper(actual) {
            override fun getFilesDir() = File(privateRoot, "files").apply { mkdirs() }
            override fun getCacheDir() = File(privateRoot, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = actual.getSharedPreferences("$prefix-$name", mode)
        }
        val db = Room.inMemoryDatabaseBuilder(actual, LibraryDatabase::class.java).build()
        try {
            val dao = db.library()
            val image = AssetEntity("image", "content://fixture/image", "photo.png", "image/png", AssetKind.IMAGE, 1)
            val text = image.copy(id = "text", uri = "content://fixture/text", kind = AssetKind.TEXT, name = "note.txt", mimeType = "text/plain")
            val word = image.copy(id = "word", uri = "content://fixture/word", kind = AssetKind.DOCUMENT, name = "letter.doc", mimeType = "application/msword")
            listOf(image, text, word).forEach { dao.insertAsset(it) }
            dao.replaceChunks(text.id, listOf(ChunkEntity("text:1", text.id, "lexical-v1", "Ready text")), IndexStatus.TEXT_READY, "Text ready")
            val provider = LocalEmbeddingProvider(isolated)
            assertFalse(provider.state.value.installed)
            val result = LibraryRepository(isolated, dao, provider).queueAll()
            assertEquals(0, result.queued); assertEquals(1, result.complete)
            assertEquals(1, result.modelNeeded); assertEquals(1, result.unsupported)
            assertFalse(dao.hasPendingPreparation())
            assertEquals(IndexStatus.IMPORTED, dao.asset(image.id)!!.status)
            assertEquals("Ready text", dao.assetChunks(text.id).single().text)
        } finally { db.close(); privateRoot.deleteRecursively() }
    }

    /** Only UUID-owned synthetic files. Requires the actual pack and authorized shared storage. */
    @Test fun boundedQueuePauseResumeAndDuplicateSkipOnActualPack() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        check(graph.embeddings.state.value.installed) { "Import the actual model pack first" }
        check(Environment.isExternalStorageManager()) { "Allow device files first" }
        check(!graph.dao.hasPendingPreparation()) { "Pause user preparation before the scoped probe" }
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".KeepsPreparationProbe-${UUID.randomUUID()}").apply { mkdirs() }
        val added = mutableListOf<AssetEntity>()
        val began = SystemClock.elapsedRealtime()
        try {
            for ((name, color) in listOf("red.png" to Color.RED, "blue.png" to Color.BLUE)) {
                val file = File(root, name)
                val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            File(root, "note.txt").writeText("Synthetic Keeps probe: bicycle repair appointment on Saturday.")
            instrumentation.context.assets.open("video/fixture-moments.mp4").use { source -> File(root, "moments.mp4").outputStream().use { source.copyTo(it) } }
            for ((name, kind, mime) in listOf(Triple("red.png", AssetKind.IMAGE, "image/png"), Triple("blue.png", AssetKind.IMAGE, "image/png"), Triple("note.txt", AssetKind.TEXT, "text/plain"), Triple("moments.mp4", AssetKind.VIDEO, "video/mp4"))) {
                val file = File(root, name)
                val asset = AssetEntity(UUID.randomUUID().toString(), Uri.fromFile(file).toString(), "fixture-preparation-$name", mime, kind, file.length(),
                    treeUri = Uri.fromFile(root).toString(), modifiedAt = file.lastModified(), durationMs = if (kind == AssetKind.VIDEO) 18_000 else 0)
                graph.dao.insertAsset(asset); added += asset
            }
            val ids = added.map { it.id }.toSet()
            assertEquals(0, graph.repository.queueSelected(ids, emptySet()).queued)
            val imageIds = added.filter { it.kind == AssetKind.IMAGE }.map { it.id }.toSet()
            assertEquals(2, graph.repository.queueSelected(ids, setOf(AssetKind.IMAGE)).queued)
            assertEquals(IndexStatus.IMPORTED, graph.dao.asset(added.first { it.kind == AssetKind.TEXT }.id)!!.status)
            withTimeout(90_000) { while (imageIds.any { graph.dao.asset(it)!!.status != IndexStatus.AI_READY }) delay(200) }
            val imageVectors = imageIds.associateWith { graph.dao.assetChunks(it).single().vector!!.copyOf() }
            val initial = graph.repository.queueSelected(ids)
            assertEquals(2, initial.queued); assertEquals(2, initial.complete)
            val video = added.single { it.kind == AssetKind.VIDEO }
            withTimeout(150_000) { while (graph.dao.assetChunks(video.id).isEmpty()) delay(200) }
            val checkpoint = graph.dao.assetChunks(video.id).first()
            graph.repository.pause()
            withTimeout(90_000) { graph.preparationGate.withLock { } }
            assertTrue(graph.dao.hasPendingPreparation())
            assertTrue(graph.dao.assetChunks(video.id).any { it.id == checkpoint.id && it.vector!!.contentEquals(checkpoint.vector!!) })
            graph.repository.resumePreparation()
            assertTrue(graph.dao.hasPendingPreparation())
            withTimeout(180_000) { while (ids.any { graph.dao.asset(it)!!.status != IndexStatus.AI_READY }) {
                assertFalse(added.any { graph.dao.asset(it.id)!!.status == IndexStatus.FAILED }); delay(200)
            } }
            val windows = graph.dao.assetChunks(video.id).sortedBy { it.startMs }
            assertEquals(listOf(0L, 6_000L, 12_000L), windows.map { it.startMs })
            assertTrue(windows.all { it.vector?.size == 3072 && it.visualVector?.size == 3072 })
            val repeat = graph.repository.queueSelected(ids)
            assertEquals(0, repeat.queued); assertEquals(4, repeat.complete)
            imageVectors.forEach { (id, vector) -> assertTrue(vector.contentEquals(graph.dao.assetChunks(id).single().vector!!)) }
            val scoped = graph.repository.search("blue square", AssetKind.VIDEO, assetId = video.id)
            assertNull(scoped.notice); assertEquals(6_000L, scoped.hits.single().chunk!!.startMs)
            File(context.filesDir, "preparation-queue-probe.json").writeText(JSONObject()
                .put("files", 4).put("kindSelectionPassed", true).put("pauseResumePassed", true).put("completedVectorsRetained", true)
                .put("repeatQueued", repeat.queued).put("ready", repeat.complete).put("videoWindows", windows.size)
                .put("matchedStartMs", scoped.hits.single().chunk!!.startMs).put("elapsedMs", SystemClock.elapsedRealtime() - began)
                .put("modelSha256", graph.embeddings.state.value.digest).put("userOriginalMutations", 0).toString(2))
        } finally {
            graph.repository.pause()
            withTimeoutOrNull(90_000) { graph.preparationGate.withLock { } }
            added.forEach { graph.dao.remove(it.id) }
            root.deleteRecursively()
        }
    }
}
