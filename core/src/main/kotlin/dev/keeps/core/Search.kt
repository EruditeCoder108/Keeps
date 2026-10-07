package dev.keeps.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

enum class AssetKind { IMAGE, VIDEO, AUDIO, DOCUMENT, TEXT, OTHER }
enum class IndexStatus { IMPORTED, QUEUED, INDEXING, TEXT_READY, AI_READY, PARTIAL, FAILED, NO_ACCESS, UNSUPPORTED }

data class TimeWindow(val startMs: Long, val endMs: Long)

object Chunking {
    fun windows(durationMs: Long, sizeMs: Long = 8_000, overlapMs: Long = 2_000): List<TimeWindow> {
        require(durationMs >= 0 && sizeMs > 0 && overlapMs in 0 until sizeMs)
        val result = mutableListOf<TimeWindow>()
        var start = 0L
        while (start < durationMs) {
            val end = start + minOf(sizeMs, durationMs - start)
            result += TimeWindow(start, end)
            if (end == durationMs) break
            start += sizeMs - overlapMs
        }
        return result
    }

    fun text(value: String, size: Int = 1_600, overlap: Int = 200): List<String> {
        require(size > 0 && overlap in 0 until size)
        val result = mutableListOf<String>()
        var start = 0
        while (start < value.length) {
            val end = minOf(start + size, value.length)
            val chunk = value.substring(start, end).trim()
            if (chunk.isNotEmpty()) result += chunk
            if (end == value.length) break
            start = end - overlap
        }
        return result
    }
}

object Vectors {
    fun normalize(values: FloatArray): FloatArray {
        require(values.isNotEmpty() && values.all { it.isFinite() }) { "Invalid embedding" }
        val norm = sqrt(values.sumOf { it.toDouble() * it })
        require(norm > 0) { "Zero embedding" }
        return FloatArray(values.size) { (values[it] / norm).toFloat() }
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        require(left.size == right.size) { "Embedding dimensions differ" }
        val a = normalize(left)
        val b = normalize(right)
        return a.indices.sumOf { a[it].toDouble() * b[it] }.toFloat().coerceIn(-1f, 1f)
    }

    fun encode(vector: FloatArray): ByteArray = ByteBuffer.allocate(vector.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply { vector.forEach(::putFloat) }.array()

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.isNotEmpty() && bytes.size % 4 == 0) { "Corrupt vector payload" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }
}

object Ranking {
    private val tokens = Regex("[\\p{L}\\p{M}\\p{N}]+")

    fun lexicalScore(query: String, title: String, text: String): Double {
        val terms = tokens.findAll(query.lowercase()).map { it.value }.distinct().toList()
        if (terms.isEmpty()) return 0.0
        val titleTerms = tokens.findAll(title.lowercase()).map { it.value }.toSet()
        val textTerms = tokens.findAll(text.lowercase()).map { it.value }.toSet()
        return terms.sumOf { if (it in titleTerms) 3.0 else if (it in textTerms) 1.0 else 0.0 } / terms.size
    }

    fun fuse(vararg lists: List<String>, constant: Int = 60): Map<String, Double> {
        require(constant > 0)
        val scores = mutableMapOf<String, Double>()
        lists.forEach { ids -> ids.distinct().forEachIndexed { rank, id ->
            scores[id] = (scores[id] ?: 0.0) + 1.0 / (constant + rank + 1)
        } }
        return scores
    }
}

fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
