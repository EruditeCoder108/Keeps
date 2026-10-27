package dev.keeps.library.inference

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import dev.keeps.core.Vectors
import dev.keeps.core.WindowPart
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.*

data class ModelState(val installed: Boolean = false, val importing: Boolean = false, val digest: String = "", val size: Long = 0, val loaded: Boolean = false)
sealed interface EmbeddingInput {
    data class Text(val value: String) : EmbeddingInput
    data class Image(val bytes: ByteArray) : EmbeddingInput
    data class Audio(val wav: ByteArray) : EmbeddingInput
    data class Pcm(val samples: ShortArray) : EmbeddingInput
    data class Sequence(val parts: List<WindowPart>) : EmbeddingInput
}

interface EmbeddingProvider {
    val namespace: String
    suspend fun embed(input: EmbeddingInput, expectedNamespace: String): FloatArray
}

/** All native inference and model replacement use one lock; vectors never mix model revisions. */
class LocalEmbeddingProvider(private val context: Context) : EmbeddingProvider {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
    private val inferenceDirectory = File(context.cacheDir, "inference").apply {
        mkdirs()
        // Older builds validated the temporary import in the permanent cache directory.
        listFiles()?.filter { it.isFile && it.name.startsWith("import.part_") }?.forEach { it.delete() }
    }
    private val preferences = context.getSharedPreferences("model", Context.MODE_PRIVATE)
    private val gate = Mutex()
    private var engine: EmbeddingEngine? = null
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleJob: Job? = null
    private val mutableState = MutableStateFlow(readState())
    val state = mutableState.asStateFlow()
    override val namespace get() = "embeddinggemma2:${state.value.digest}:768:model-card-v1:cpu:preprocess-v1"
    private fun modelFile() = File(modelDirectory, "${preferences.getString("digest", "missing")}.litertlm")
    private fun readState(): ModelState {
        val digest = preferences.getString("digest", "") ?: ""
        val file = File(modelDirectory, "$digest.litertlm")
        return ModelState(file.isFile && digest.isNotEmpty(), digest = digest, size = if (file.isFile) file.length() else 0)
    }

    suspend fun importModel(uri: Uri) = withContext(Dispatchers.IO) {
        gate.withLock {
            mutableState.value = state.value.copy(importing = true)
            val staging = File(modelDirectory, "import.part")
            val validationCache = File(context.cacheDir, "model-validation").apply { mkdirs() }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { source ->
                    FileOutputStream(staging).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 2_000_000_000L) { "Model exceeds the 2 GB import limit" }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                } ?: error("Cannot read the selected model")
                require(staging.length() > 1_024) { "The selected file is not a model pack" }
                // Loading and a real embedding validate the pack before replacing the active model.
                engine?.close()
                engine = null
                val candidate = createEngine(staging, validationCache)
                try {
                    candidate.initialize()
                    val output = candidate.computeEmbedding(listOf(InputData.Text("task: search result | query: test")), options).embedding
                    require(output.size == 768) { "Expected a 768-dimensional EmbeddingGemma 2 pack" }
                    Vectors.normalize(output)
                } finally { if (candidate.isInitialized()) candidate.close() }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                val destination = File(modelDirectory, "$hash.litertlm")
                if (destination.exists()) staging.delete() else check(staging.renameTo(destination)) { "Cannot install model" }
                engine?.close()
                engine = null
                check(preferences.edit().putString("digest", hash).commit()) { "Cannot save model selection" }
                mutableState.value = readState()
                modelDirectory.listFiles()?.filter { it.name != destination.name }?.forEach { it.delete() }
                inferenceDirectory.listFiles()?.filter {
                    it.isFile && !it.name.startsWith("${destination.name}_")
                }?.forEach { it.delete() }
            } finally {
                staging.delete()
                // Validation has its own disposable cache; the installed model uses one cache.
                validationCache.deleteRecursively()
                mutableState.value = readState()
            }
        }
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        idleJob?.cancel()
        gate.withLock { engine?.close(); engine = null; mutableState.value = state.value.copy(loaded = false) }
    }

    override suspend fun embed(input: EmbeddingInput, expectedNamespace: String): FloatArray = withContext(Dispatchers.Default) {
        gate.withLock {
            idleJob?.cancel()
            check(state.value.installed) { "Import an EmbeddingGemma 2 model pack in Settings" }
            check(namespace == expectedNamespace) { "Model changed; retry indexing" }
            val active = engine ?: createEngine(modelFile()).also { it.initialize(); engine = it }
            mutableState.value = state.value.copy(loaded = true)
            val data = when (input) {
                is EmbeddingInput.Text -> listOf(InputData.Text(input.value))
                is EmbeddingInput.Image -> listOf(InputData.Image(input.bytes))
                is EmbeddingInput.Audio -> listOf(InputData.Audio(input.wav))
                is EmbeddingInput.Pcm -> listOf(InputData.Audio(pcmWav(input.samples)))
                is EmbeddingInput.Sequence -> {
                    require(input.parts.isNotEmpty() && input.parts.size <= 32)
                    input.parts.map { part -> when (part) {
                        is WindowPart.Text -> InputData.Text(part.text)
                        is WindowPart.Image -> InputData.Image(part.imageData)
                        is WindowPart.Audio -> InputData.Audio(pcmWav(part.pcm))
                    } }
                }
            }
            try {
                val result = active.computeEmbedding(data, options).embedding
                check(result.size == 768) { "Unexpected embedding dimension" }
                Vectors.normalize(result)
            } finally {
                idleJob = idleScope.launch {
                    delay(60_000)
                    gate.withLock { engine?.close(); engine = null; mutableState.value = state.value.copy(loaded = false) }
                }
            }
        }
    }

    private fun createEngine(file: File, cache: File = inferenceDirectory) = EmbeddingEngine(EmbeddingEngineConfig(
        modelPath = file.absolutePath,
        backend = Backend.CPU(threadCount = 2), visionBackend = Backend.CPU(threadCount = 2), audioBackend = Backend.CPU(threadCount = 2),
        cacheDir = cache.absolutePath,
    ))

    companion object {
        private fun pcmWav(samples: ShortArray): ByteArray {
            require(samples.size in 1..480_000)
            val bytes = java.nio.ByteBuffer.allocate(44 + samples.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(16_000).putInt(32_000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(samples.size * 2)
            samples.forEach { bytes.putShort(it) }
            return bytes.array()
        }
        private val options = EmbeddingOptions(normalize = true, outputSize = 768)
        fun query(value: String) = EmbeddingInput.Text("task: search result | query: ${value.trim()}")
        fun document(title: String, value: String) = EmbeddingInput.Text("title: $title | text: $value")
    }
}
