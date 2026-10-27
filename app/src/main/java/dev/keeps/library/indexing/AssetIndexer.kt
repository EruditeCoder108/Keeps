package dev.keeps.library.indexing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import dev.keeps.core.*
import dev.keeps.library.catalog.AssetEntity
import dev.keeps.library.catalog.ChunkEntity
import dev.keeps.library.inference.EmbeddingInput
import dev.keeps.library.inference.LocalEmbeddingProvider
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class IndexResult(val chunks: List<ChunkEntity>, val status: IndexStatus, val detail: String, val resume: Boolean = false)

interface AssetPreparation {
    suspend fun index(asset: AssetEntity, completed: List<ChunkEntity> = emptyList(), maxNewWindows: Int = 8,
        checkpoint: suspend (ChunkEntity, String) -> Unit = { _, _ -> }): IndexResult
}

class AssetIndexer(private val context: Context, private val provider: LocalEmbeddingProvider) : AssetPreparation {
    override suspend fun index(asset: AssetEntity, completed: List<ChunkEntity>, maxNewWindows: Int,
        checkpoint: suspend (ChunkEntity, String) -> Unit): IndexResult {
        val uri = Uri.parse(asset.uri)
        val namespace = provider.namespace
        context.contentResolver.openFileDescriptor(uri, "r")?.use {} ?: error("File is no longer available")
        if (asset.kind != AssetKind.TEXT && !provider.state.value.installed) {
            return IndexResult(emptyList(), IndexStatus.IMPORTED, "Import a model pack to search this content")
        }
        return when (asset.kind) {
            AssetKind.TEXT -> text(asset, uri, namespace, completed, maxNewWindows, checkpoint)
            AssetKind.IMAGE -> {
                val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = 1024.0 / maxOf(info.size.width, info.size.height)
                    if (scale < 1) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val vector = try { provider.embed(EmbeddingInput.Image(image.png()), namespace) } finally { image.recycle() }
                IndexResult(listOf(chunk(asset, "image", namespace, vector)), IndexStatus.AI_READY, "Image indexed on this phone")
            }
            AssetKind.DOCUMENT -> pdf(asset, uri, namespace, completed, maxNewWindows, checkpoint)
            AssetKind.VIDEO -> video(asset, uri, namespace, completed, maxNewWindows, checkpoint)
            AssetKind.AUDIO -> audio(asset, uri, namespace, completed, maxNewWindows, checkpoint)
            AssetKind.OTHER -> IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "Filename search only; this format has no parser yet")
        }
    }

    private suspend fun text(asset: AssetEntity, uri: Uri, namespace: String, completed: List<ChunkEntity>, limit: Int, checkpoint: suspend (ChunkEntity, String) -> Unit): IndexResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(512_001) } ?: error("Cannot read text")
        val truncated = bytes.size > 512_000
        val content = bytes.take(512_000).toByteArray().toString(Charsets.UTF_8)
        val pieces = Chunking.text(content)
        val chunks = mutableListOf<ChunkEntity>()
        val saved = completed.associateBy { it.id }
        var fresh = 0
        var covered = 0
        for ((index, piece) in pieces.withIndex()) {
            currentCoroutineContext().ensureActive()
            val lexical = ChunkEntity("${asset.id}:text:$index", asset.id, "lexical-v1", text = piece)
            val oldVector = saved["${asset.id}:text-vector:$index"]?.takeIf { it.namespace == namespace && it.text == piece && it.vector != null }
            val needsWork = if (provider.state.value.installed) oldVector == null else saved[lexical.id]?.text != piece
            if (needsWork && fresh >= limit) break
            chunks += lexical
            if (saved[lexical.id]?.text != piece) checkpoint(lexical, "${index + 1}/${pieces.size} text sections saved")
            if (provider.state.value.installed) {
                val vector = oldVector ?: chunk(asset, "text-vector:$index", namespace,
                    provider.embed(LocalEmbeddingProvider.document(asset.name, piece), namespace)).copy(text = piece)
                chunks += vector
                if (oldVector == null) checkpoint(vector, "${index + 1}/${pieces.size} text sections prepared")
            }
            if (needsWork) fresh++
            covered++
        }
        val more = covered < pieces.size
        val status = when {
            truncated || more -> IndexStatus.PARTIAL
            provider.state.value.installed -> IndexStatus.AI_READY
            else -> IndexStatus.TEXT_READY
        }
        return IndexResult(chunks, status, if (more) "$covered/${pieces.size} text sections; continuing preparation" else if (truncated) "First 500 KB indexed" else if (status == IndexStatus.AI_READY) "Text and meaning indexed" else "Text indexed; import a model for semantic search", resume = more)
    }

    private suspend fun pdf(asset: AssetEntity, uri: Uri, namespace: String, completed: List<ChunkEntity>, limit: Int, checkpoint: suspend (ChunkEntity, String) -> Unit): IndexResult {
        val chunks = mutableListOf<ChunkEntity>()
        val saved = completed.filter { it.namespace == namespace && it.pageNumber != null && it.vector != null }.associateBy { it.pageNumber }
        var fresh = 0
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot read PDF")
        var pages = 0
        try { PdfRenderer(descriptor).use { renderer ->
            pages = renderer.pageCount
            for (index in 0 until minOf(pages, 24)) {
                currentCoroutineContext().ensureActive()
                val previous = saved[index + 1]
                if (previous != null) { chunks += previous; continue }
                if (fresh >= limit) break
                renderer.openPage(index).use { page ->
                    val scale = 1024.0 / maxOf(page.width, page.height)
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val vector = try {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        provider.embed(EmbeddingInput.Image(bitmap.png()), namespace)
                    } finally { bitmap.recycle() }
                    val prepared = chunk(asset, "page:$index", namespace, vector).copy(pageNumber = index + 1)
                    chunks += prepared
                    fresh++
                    checkpoint(prepared, "${chunks.size}/${minOf(pages, 24)} PDF pages prepared")
                }
            }
        } } finally { descriptor.close() }
        val more = chunks.size < minOf(pages, 24)
        return IndexResult(chunks, if (more || pages > 24) IndexStatus.PARTIAL else IndexStatus.AI_READY,
            if (more) "${chunks.size}/${minOf(pages, 24)} PDF pages; continuing preparation" else if (pages > 24) "First 24 of $pages pages indexed" else "$pages pages indexed visually", resume = more)
    }

    private suspend fun video(asset: AssetEntity, uri: Uri, namespace: String, completed: List<ChunkEntity>, maxNewWindows: Int,
        checkpoint: suspend (ChunkEntity, String) -> Unit): IndexResult {
        val chunks = mutableListOf<ChunkEntity>()
        var unavailableAudio = false
        var total = 0
        require(maxNewWindows in 1..32)
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Unknown video duration")
            require(duration > 0)
            val windows = Chunking.windows(duration)
            total = windows.size
            val candidates = completed.filter { it.namespace == namespace && it.recipe == VideoWindows.RECIPE && it.vector != null && it.visualVector != null }.associateBy { it.id }
            val allComplete = windows.all { "${asset.id}:${VideoWindows.RECIPE}:${it.startMs}:${it.endMs}" in candidates }
            val saved = candidates.filterValues { !allComplete || !it.audioUnavailable }
            val opened = VideoAudioExtractor.open(context, uri)
            val audioDeclared = opened.trackPresent || retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            opened.decoder.use { audio ->
                unavailableAudio = audioDeclared && audio == null
                var newWindows = 0
                for (window in windows) {
                    currentCoroutineContext().ensureActive()
                    val id = "${asset.id}:${VideoWindows.RECIPE}:${window.startMs}:${window.endMs}"
                    val existing = saved[id]
                    if (existing != null) { chunks += existing; unavailableAudio = unavailableAudio || existing.audioUnavailable; continue }
                    if (newWindows >= maxNewWindows) break
                    val times = VideoWindows.frameTimes(window)
                    val frames = times.map { timestamp ->
                        currentCoroutineContext().ensureActive()
                        val bitmap = retriever.getScaledFrameAtTime(timestamp * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 512, 512)
                            ?: error("Cannot decode video frame")
                        try { bitmap.png() } finally { bitmap.recycle() }
                    }
                    val pcm = audio?.readWindow(window.startMs, window.endMs)
                    if (audio?.failed == true) unavailableAudio = true
                    val slices = pcm?.let { splitWindowPcm(it, window.startMs, window.endMs, times.size) } ?: emptyList()
                    val vector = provider.embed(EmbeddingInput.Sequence(buildWindowParts(frames, times, slices)), namespace)
                    // The actual pack's combined sequence misranked a basic blue-scene query.
                    // Keep its temporal/audio representation plus a distinct visual anchor.
                    val visual = provider.embed(EmbeddingInput.Image(frames[frames.size / 2]), namespace)
                    val result = ChunkEntity(id, asset.id, namespace, startMs = window.startMs, endMs = window.endMs,
                        vector = Vectors.encode(vector), recipe = VideoWindows.RECIPE, frameCount = frames.size, hasAudio = slices.any { it != null && it.isNotEmpty() },
                        audioUnavailable = audioDeclared && (audio == null || audio.failed), visualVector = Vectors.encode(visual))
                    checkpoint(result, "${chunks.size + 1}/${windows.size} windows prepared · through ${formatTime(window.endMs)} of ${formatTime(duration)}")
                    chunks += result
                    newWindows++
                }
                chunks += saved.values.filter { cached -> chunks.none { it.id == cached.id } }
            }
        }
        val sound = chunks.count { it.hasAudio }
        val more = chunks.size < total
        return IndexResult(chunks.sortedBy { it.startMs }, if (more || unavailableAudio) IndexStatus.PARTIAL else IndexStatus.AI_READY,
            "${chunks.size}/$total ordered windows · $sound with soundtrack${if (more) "; continuing preparation" else if (unavailableAudio) "; audio decoding unavailable for some content" else ""}", resume = more)
    }

    private suspend fun audio(asset: AssetEntity, uri: Uri, namespace: String, completed: List<ChunkEntity>, maxNewWindows: Int,
        checkpoint: suspend (ChunkEntity, String) -> Unit): IndexResult {
        require(maxNewWindows in 1..32)
        val duration = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Unknown audio duration")
        }
        require(duration > 0)
        val windows = Chunking.windows(duration)
        val saved = completed.filter { it.namespace == namespace && it.recipe == AudioWindows.RECIPE && it.vector != null }.associateBy { it.id }
        val chunks = mutableListOf<ChunkEntity>()
        val opened = VideoAudioExtractor.open(context, uri)
        if (opened.decoder == null) {
            // Preserve the proven short PCM WAV path on providers/codecs with no decoder.
            return legacyWav(asset, uri, namespace)
        }
        var newWindows = 0
        var failed = false
        opened.decoder.use { decoder ->
            for (window in windows) {
                currentCoroutineContext().ensureActive()
                val id = "${asset.id}:${AudioWindows.RECIPE}:${window.startMs}:${window.endMs}"
                val existing = saved[id]
                if (existing != null) { chunks += existing; continue }
                if (newWindows >= maxNewWindows) break
                val decoded = decoder.readWindow(window.startMs, window.endMs)
                if (decoder.failed) { failed = true; break }
                // A complete timestamp gap represents silence, not time-compressed audio.
                val pcm = decoded ?: ShortArray(((window.endMs - window.startMs) * 16).toInt())
                val vector = provider.embed(EmbeddingInput.Pcm(pcm), namespace)
                val result = ChunkEntity(id, asset.id, namespace, startMs = window.startMs, endMs = window.endMs,
                    vector = Vectors.encode(vector), recipe = AudioWindows.RECIPE, hasAudio = true)
                checkpoint(result, "${chunks.size + 1}/${windows.size} audio windows prepared · through ${formatTime(window.endMs)} of ${formatTime(duration)}")
                chunks += result
                newWindows++
            }
            chunks += saved.values.filter { cached -> chunks.none { it.id == cached.id } }
        }
        val more = chunks.size < windows.size
        return IndexResult(chunks.sortedBy { it.startMs }, if (more || failed) IndexStatus.PARTIAL else IndexStatus.AI_READY,
            "${chunks.size}/${windows.size} audio windows${if (failed) "; decoder stopped; prepare again to resume" else if (more) "; continuing preparation" else "; no transcript"}", resume = more && !failed)
    }

    private suspend fun legacyWav(asset: AssetEntity, uri: Uri, namespace: String): IndexResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(1_100_001) } ?: error("Cannot read audio")
        if (bytes.size > 1_100_000) return IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "This phone cannot decode this recording; filename search and Open with remain available")
        val duration = try { Wav.durationMs(bytes) } catch (_: IllegalArgumentException) {
            return IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "This phone cannot decode this recording; filename search and Open with remain available")
        }
        if (duration > 30_000) return IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "Prototype audio supports WAV up to 30 seconds")
        val vector = provider.embed(EmbeddingInput.Audio(bytes), namespace)
        return IndexResult(listOf(chunk(asset, "audio", namespace, vector).copy(startMs = 0, endMs = duration)), IndexStatus.AI_READY, "Audio indexed locally; no transcript")
    }

    private fun chunk(asset: AssetEntity, key: String, namespace: String, vector: FloatArray) =
        ChunkEntity("${asset.id}:$key", asset.id, namespace, vector = Vectors.encode(vector))
    private fun Bitmap.png() = ByteArrayOutputStream().use { stream -> compress(Bitmap.CompressFormat.PNG, 100, stream); stream.toByteArray() }
}
