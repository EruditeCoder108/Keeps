package dev.keeps.library.inference

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in native compatibility probe. Generates inputs in memory; never writes catalog/originals. */
class DeviceSequenceTest {
    @Test fun actualPackAcceptsOrderedFramesAndAlignedSound(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsSequenceTests") == "true")
        val provider = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KeepsApplication).graph.embeddings
        assertEquals("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c", provider.state.value.digest)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        com.google.ai.edge.litertlm.ModelInfo.from(java.io.File(context.filesDir, "models/${provider.state.value.digest}.litertlm").absolutePath).use { info ->
            Log.i("KeepsSequenceTest", "pack context=${info.maxContextTokens()}; visionBudget=${info.maxVisionTokenBudget()}; modalities=${info.inputModalities()}")
            assertTrue(info.inputModalities().vision)
            assertTrue(info.inputModalities().audio)
        }
        fun frame(x: Float): ByteArray {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawCircle(x, 128f, 35f, android.graphics.Paint().apply { color = Color.RED })
            return try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } } finally { bitmap.recycle() }
        }
        val images = listOf(frame(55f), frame(200f))
        val pcm = ShortArray(32_000) { (kotlin.math.sin(it * 2.0 * Math.PI * 440 / 16_000) * 5000).toInt().toShort() }
        val audio = splitWindowPcm(pcm, 0, 2000, 2)
        val start = SystemClock.elapsedRealtime()
        try {
            val silent = provider.embed(EmbeddingInput.Sequence(buildWindowParts(images, listOf(0, 1000))), provider.namespace)
            val reversed = provider.embed(EmbeddingInput.Sequence(buildWindowParts(images.reversed(), listOf(0, 1000))), provider.namespace)
            val sound = provider.embed(EmbeddingInput.Sequence(buildWindowParts(images, listOf(0, 1000), audio)), provider.namespace)
            listOf(silent, reversed, sound).forEach { vector -> assertEquals(768, vector.size); assertTrue(vector.all { it.isFinite() }) }
            Log.i("KeepsSequenceTest", "frames/reverse/audio accepted; reverse cosine=${Vectors.cosine(silent, reversed)}; audio cosine=${Vectors.cosine(silent, sound)}; durationMs=${SystemClock.elapsedRealtime() - start}")
        } finally { provider.unload() }
    }
}
