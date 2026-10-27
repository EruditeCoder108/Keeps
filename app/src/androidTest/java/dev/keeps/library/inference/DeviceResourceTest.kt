package dev.keeps.library.inference

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.*
import dev.keeps.core.Vectors
import dev.keeps.library.KeepsApplication
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Generated inputs only; actual installed pack; no catalog changes. Opt-in by explicit class. */
class DeviceResourceTest {
    @Test fun actualPackResourceAndGalleryBudgetCompatibility(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = (context.applicationContext as KeepsApplication).graph.embeddings
        check(provider.state.value.installed) { "Import the actual model pack first" }
        provider.unload()
        fun pss() = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
        fun image(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap); canvas.drawColor(Color.WHITE)
            canvas.drawRect(48f, 48f, 208f, 208f, android.graphics.Paint().apply { this.color = color })
            return try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } } finally { bitmap.recycle() }
        }
        val report = JSONObject().put("modelSha256", provider.state.value.digest).put("unloadedPssKiB", pss())
        val textStart = SystemClock.elapsedRealtime()
        val query = provider.embed(LocalEmbeddingProvider.query("blue square on a white background"), provider.namespace)
        report.put("productionColdQueryMs", SystemClock.elapsedRealtime() - textStart).put("productionTextPssKiB", pss())
        val images = listOf(image(Color.RED), image(Color.BLUE))
        val production = images.map { provider.embed(EmbeddingInput.Image(it), provider.namespace) }
        assertTrue(Vectors.cosine(query, production[1]) > Vectors.cosine(query, production[0]))
        report.put("productionImagePssKiB", pss())
        provider.unload(); report.put("afterProductionUnloadPssKiB", pss())
        val cache = File(context.cacheDir, "budget-probe").apply { mkdirs() }
        var engine: EmbeddingEngine? = null
        var peak = pss()
        val sampler = launch(Dispatchers.IO) { while (isActive) { peak = maxOf(peak, pss()); delay(250) } }
        try {
            val start = SystemClock.elapsedRealtime()
            engine = EmbeddingEngine(EmbeddingEngineConfig(modelPath = File(context.filesDir, "models/${provider.state.value.digest}.litertlm").absolutePath,
                backend = Backend.CPU(), visionBackend = Backend.CPU(), audioBackend = Backend.CPU(), cacheDir = cache.absolutePath,
                maxInputLength = 1024, visionTokensPerImage = 70))
            engine.initialize()
            val options = EmbeddingOptions(normalize = true, outputSize = 768, visionTokensPerImage = 70)
            val compactQuery = engine.computeEmbedding(listOf(InputData.Text("task: search result | query: blue square on a white background")), options).embedding
            val vectors = images.map { engine.computeEmbedding(listOf(InputData.Image(it)), options).embedding }
            vectors.forEach { assertEquals(768, it.size); assertTrue(it.all { value -> value.isFinite() }) }
            report.put("galleryBudgetAccepted", true).put("galleryBudgetImageOrderCorrect", Vectors.cosine(compactQuery, vectors[1]) > Vectors.cosine(compactQuery, vectors[0]))
                .put("galleryBudgetProbeMs", SystemClock.elapsedRealtime() - start).put("galleryBudgetPssKiB", pss())
            val sequence = engine.computeEmbedding(listOf(InputData.Image(images[0]), InputData.Image(images[1]), InputData.Image(images[0]), InputData.Image(images[1])), options).embedding
            assertEquals(768, sequence.size); assertTrue(sequence.all { it.isFinite() })
            report.put("galleryBudgetFourFramesAccepted", true)
        } catch (error: Exception) {
            report.put("galleryBudgetAccepted", false).put("galleryBudgetError", "${error.javaClass.simpleName}: ${error.message}")
        } finally {
            sampler.cancelAndJoin(); report.put("galleryBudgetSampledPeakPssKiB", peak)
            engine?.let { if (it.isInitialized()) it.close() }
            report.put("afterAllUnloadPssKiB", pss())
            File(context.filesDir, "resource-probe.json").writeText(report.toString(2))
            android.util.Log.i("KeepsResourceTest", report.toString())
        }
        // Production correctness is required. Candidate incompatibility is recorded, not hidden or adopted.
    }
}
