package dev.keeps.library.catalog

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreparationImageBatchTest {
    @Test fun boundedImageBatchesKeepCompletedVectorsAndOnlyQueueRemainingFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        check(graph.embeddings.state.value.installed) { "Import the actual model first" }
        check(Environment.isExternalStorageManager())
        check(!graph.dao.hasPendingPreparation()) { "Pause user preparation before the generated probe" }
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".KeepsHeatProbe-${UUID.randomUUID()}").apply { mkdirs() }
        val rows = mutableListOf<AssetEntity>()
        val start = SystemClock.elapsedRealtime()
        try {
            repeat(6) { i ->
                val file = File(root, "fixture-heat-$i.png")
                val image = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                image.eraseColor(if (i % 2 == 0) Color.RED else Color.BLUE)
                file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
                val row = AssetEntity(UUID.randomUUID().toString(), Uri.fromFile(file).toString(), file.name, "image/png", AssetKind.IMAGE, file.length(),
                    treeUri = Uri.fromFile(root).toString(), modifiedAt = file.lastModified())
                rows += row; graph.dao.insertAsset(row)
            }
            val ids = rows.map { it.id }.toSet()
            val first = graph.repository.queueSelected(ids, maxNewFiles = 2)
            assertEquals(2, first.queued); assertEquals(4, first.remaining)
            suspend fun ready() = rows.mapNotNull { graph.dao.asset(it.id) }.filter { it.status == IndexStatus.AI_READY }
            withTimeout(240_000) { while (ready().size < 2) delay(200) }
            val originalVectors = ready().associate { row -> row.id to graph.dao.assetChunks(row.id).single().vector!!.copyOf() }
            val second = graph.repository.queueSelected(ids, maxNewFiles = 2)
            assertEquals(2, second.queued); assertEquals(2, second.complete); assertEquals(2, second.remaining)
            withTimeout(240_000) { while (ready().size < 4) delay(200) }
            val repeat = graph.repository.queueSelected(ready().map { it.id }.toSet())
            assertEquals(0, repeat.queued); assertEquals(4, repeat.complete)
            originalVectors.forEach { (id, vector) -> assertTrue(vector.contentEquals(graph.dao.assetChunks(id).single().vector!!)) }
            assertEquals(2, rows.count { graph.dao.asset(it.id)!!.status == IndexStatus.IMPORTED })
            File(context.filesDir, "heat-image-batch-probe.json").writeText(JSONObject().put("modelSha256", graph.embeddings.state.value.digest)
                .put("generatedFiles", 6).put("prepared", 4).put("leftUnqueued", 2).put("repeatQueued", 0).put("completedVectorsUnchanged", true)
                .put("requestedCpuThreadsPerBackend", 2).put("elapsedMs", SystemClock.elapsedRealtime() - start).put("userOriginalMutations", 0).toString(2))
        } finally {
            graph.repository.pause()
            withTimeoutOrNull(90_000) { graph.preparationGate.withLock { } }
            rows.forEach { graph.dao.remove(it.id) }; root.deleteRecursively()
        }
    }
}
