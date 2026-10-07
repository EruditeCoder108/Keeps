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

data class IndexResult(val chunks: List<ChunkEntity>, val status: IndexStatus, val detail: String)

class AssetIndexer(private val context: Context, private val provider: LocalEmbeddingProvider) {
    suspend fun index(asset: AssetEntity): IndexResult {
        val uri = Uri.parse(asset.uri)
        val namespace = provider.namespace
        context.contentResolver.openFileDescriptor(uri, "r")?.use {} ?: error("File is no longer available")
        if (asset.kind != AssetKind.TEXT && !provider.state.value.installed) {
            return IndexResult(emptyList(), IndexStatus.IMPORTED, "Import a model pack to search this content")
        }
        return when (asset.kind) {
            AssetKind.TEXT -> text(asset, uri, namespace)
            AssetKind.IMAGE -> {
                val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = 1024.0 / maxOf(info.size.width, info.size.height)
                    if (scale < 1) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val vector = try { provider.embed(EmbeddingInput.Image(image.png()), namespace) } finally { image.recycle() }
                IndexResult(listOf(chunk(asset, "image", namespace, vector)), IndexStatus.AI_READY, "Image indexed on this phone")
            }
            AssetKind.DOCUMENT -> pdf(asset, uri, namespace)
            AssetKind.VIDEO -> video(asset, uri, namespace)
            AssetKind.AUDIO -> audio(asset, uri, namespace)
            AssetKind.OTHER -> IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "Filename search only; this format has no parser yet")
        }
    }

    private suspend fun text(asset: AssetEntity, uri: Uri, namespace: String): IndexResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(512_001) } ?: error("Cannot read text")
        val truncated = bytes.size > 512_000
        val content = bytes.take(512_000).toByteArray().toString(Charsets.UTF_8)
        val pieces = Chunking.text(content)
        val chunks = mutableListOf<ChunkEntity>()
        for ((index, piece) in pieces.withIndex()) {
            currentCoroutineContext().ensureActive()
            val lexical = ChunkEntity("${asset.id}:text:$index", asset.id, "lexical-v1", text = piece)
            chunks += lexical
            if (provider.state.value.installed) {
                val vector = provider.embed(LocalEmbeddingProvider.document(asset.name, piece), namespace)
                chunks += chunk(asset, "text-vector:$index", namespace, vector).copy(text = piece)
            }
        }
        val status = when {
            truncated -> IndexStatus.PARTIAL
            provider.state.value.installed -> IndexStatus.AI_READY
            else -> IndexStatus.TEXT_READY
        }
        return IndexResult(chunks, status, if (truncated) "First 500 KB indexed" else if (status == IndexStatus.AI_READY) "Text and meaning indexed" else "Text indexed; import a model for semantic search")
    }

    private suspend fun pdf(asset: AssetEntity, uri: Uri, namespace: String): IndexResult {
        val chunks = mutableListOf<ChunkEntity>()
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot read PDF")
        var pages = 0
        try { PdfRenderer(descriptor).use { renderer ->
            pages = renderer.pageCount
            repeat(minOf(pages, 24)) { index ->
                currentCoroutineContext().ensureActive()
                renderer.openPage(index).use { page ->
                    val scale = 1024.0 / maxOf(page.width, page.height)
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val vector = try {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        provider.embed(EmbeddingInput.Image(bitmap.png()), namespace)
                    } finally { bitmap.recycle() }
                    chunks += chunk(asset, "page:$index", namespace, vector).copy(pageNumber = index + 1)
                }
            }
        } } finally { descriptor.close() }
        return IndexResult(chunks, if (pages > 24) IndexStatus.PARTIAL else IndexStatus.AI_READY,
            if (pages > 24) "First 24 of $pages pages indexed" else "$pages pages indexed visually")
    }

    private suspend fun video(asset: AssetEntity, uri: Uri, namespace: String): IndexResult {
        val chunks = mutableListOf<ChunkEntity>()
        var total = 0
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Unknown video duration")
            val windows = Chunking.windows(duration)
            total = windows.size
            for ((index, window) in windows.take(48).withIndex()) {
                currentCoroutineContext().ensureActive()
                val timestamp = window.startMs + (window.endMs - window.startMs) / 2
                val frame = retriever.getScaledFrameAtTime(timestamp * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 512, 512)
                    ?: error("Cannot decode video frame")
                val vector = try { provider.embed(EmbeddingInput.Image(frame.png()), namespace) } finally { frame.recycle() }
                chunks += chunk(asset, "frame:$index", namespace, vector).copy(startMs = timestamp, endMs = window.endMs)
            }
        }
        return IndexResult(chunks, IndexStatus.PARTIAL,
            "${minOf(total, 48)} sampled frames indexed; motion and soundtrack not indexed yet")
    }

    private suspend fun audio(asset: AssetEntity, uri: Uri, namespace: String): IndexResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(1_100_001) } ?: error("Cannot read audio")
        if (bytes.size > 1_100_000) return IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "Prototype audio supports PCM WAV up to 30 seconds")
        val duration = try { Wav.durationMs(bytes) } catch (_: IllegalArgumentException) {
            return IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "Use mono 16 kHz, 16-bit PCM WAV up to 30 seconds")
        }
        if (duration > 30_000) return IndexResult(emptyList(), IndexStatus.UNSUPPORTED, "Prototype audio supports WAV up to 30 seconds")
        val vector = provider.embed(EmbeddingInput.Audio(bytes), namespace)
        return IndexResult(listOf(chunk(asset, "audio", namespace, vector).copy(startMs = 0, endMs = duration)), IndexStatus.AI_READY, "Audio indexed locally; no transcript")
    }

    private fun chunk(asset: AssetEntity, key: String, namespace: String, vector: FloatArray) =
        ChunkEntity("${asset.id}:$key", asset.id, namespace, vector = Vectors.encode(vector))
    private fun Bitmap.png() = ByteArrayOutputStream().use { stream -> compress(Bitmap.CompressFormat.PNG, 100, stream); stream.toByteArray() }
}
