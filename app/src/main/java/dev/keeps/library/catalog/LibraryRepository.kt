package dev.keeps.library.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.*
import dev.keeps.core.*
import dev.keeps.library.indexing.IndexWorker
import dev.keeps.library.inference.LocalEmbeddingProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchHit(val asset: AssetEntity, val chunk: ChunkEntity? = null, val reason: String = "Filename match")
data class SearchResponse(val hits: List<SearchHit>, val notice: String? = null)

class LibraryRepository(private val context: Context, val dao: LibraryDao, private val provider: LocalEmbeddingProvider) {
    val assets = dao.observeAssets()

    suspend fun search(query: String, kind: AssetKind? = null, favorites: Boolean = false): SearchResponse = withContext(Dispatchers.Default) {
        val allAssets = dao.assets()
        val grants = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        allAssets.filter { it.uri !in grants && it.status != IndexStatus.NO_ACCESS }.forEach {
            dao.replaceChunks(it.id, emptyList(), IndexStatus.NO_ACCESS, "Access revoked; select this file again")
        }
        val allowed = allAssets.filter { it.uri in grants && (kind == null || it.kind == kind) && (!favorites || it.favorite) }
        if (query.isBlank()) return@withContext SearchResponse(allowed.map { SearchHit(it, reason = it.detail) })
        val byId = allowed.associateBy { it.id }
        val namespace = provider.namespace
        val chunks = dao.chunks(namespace).filter { it.assetId in byId }
        val lexical = allowed.map { asset ->
            val text = chunks.filter { it.assetId == asset.id }.joinToString(" ") { it.text }
            asset.id to Ranking.lexicalScore(query, asset.name, text)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        var notice: String? = null
        val semantic = mutableMapOf<String, Pair<ChunkEntity, Float>>()
        if (provider.state.value.installed && chunks.any { it.vector != null }) {
            try {
                val vector = provider.embed(LocalEmbeddingProvider.query(query), namespace)
                chunks.filter { it.vector != null && it.namespace == namespace }.forEach { chunk ->
                    val score = Vectors.cosine(vector, Vectors.decode(chunk.vector!!))
                    val previous = semantic[chunk.assetId]
                    if (score > 0 && (previous == null || score > previous.second)) semantic[chunk.assetId] = chunk to score
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = "AI search could not run. Showing text matches." }
        }
        val semanticIds = semantic.entries.sortedByDescending { it.value.second }.take(30).map { it.key }
        val scores = Ranking.fuse(lexical, semanticIds)
        val hits = scores.entries.sortedByDescending { it.value }.take(50).mapNotNull { (id, _) ->
            val asset = byId[id] ?: return@mapNotNull null
            val chunk = semantic[id]?.first
            val reason = when {
                chunk?.pageNumber != null -> "Similar page ${chunk.pageNumber}"
                chunk?.startMs != null && asset.kind == AssetKind.VIDEO -> "Similar frame at ${formatTime(chunk.startMs)}"
                chunk?.startMs != null -> "Similar audio at ${formatTime(chunk.startMs)}"
                chunk != null -> "Similar content"
                else -> "Text or filename match"
            }
            SearchHit(asset, chunk, reason)
        }
        SearchResponse(hits, notice)
    }

    suspend fun queueAll(): Int {
        val assets = dao.assets().filter { it.status !in listOf(IndexStatus.INDEXING, IndexStatus.QUEUED, IndexStatus.NO_ACCESS) }
        assets.forEach { asset ->
            dao.status(asset.id, IndexStatus.QUEUED, "Waiting to index")
            val request = OneTimeWorkRequestBuilder<IndexWorker>()
                .setInputData(workDataOf("assetId" to asset.id))
                .addTag("indexing")
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("asset-${asset.id}", ExistingWorkPolicy.KEEP, request)
        }
        return assets.size
    }

    suspend fun pause() {
        WorkManager.getInstance(context).cancelAllWorkByTag("indexing").result.get()
        dao.assets().filter { it.status == IndexStatus.QUEUED || it.status == IndexStatus.INDEXING }
            .forEach { dao.status(it.id, IndexStatus.IMPORTED, "Paused; index again to resume") }
    }

    suspend fun remove(asset: AssetEntity) {
        WorkManager.getInstance(context).cancelUniqueWork("asset-${asset.id}").result.get()
        dao.remove(asset.id)
        try { context.contentResolver.releasePersistableUriPermission(Uri.parse(asset.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Provider may already have revoked it. */ }
    }
}
