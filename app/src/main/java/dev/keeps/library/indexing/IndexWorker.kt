package dev.keeps.library.indexing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.keeps.core.IndexStatus
import dev.keeps.library.KeepsApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class IndexWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val graph = (applicationContext as KeepsApplication).graph
        val id = inputData.getString("assetId") ?: return@withContext Result.failure()
        val asset = graph.dao.asset(id) ?: return@withContext Result.success()
        graph.dao.status(id, IndexStatus.INDEXING, "Indexing on this phone")
        try {
            val result = graph.indexer.index(asset)
            currentCoroutineContext().ensureActive()
            check(result.chunks.filter { it.vector != null }.all { it.namespace == graph.embeddings.namespace }) {
                "Model changed before indexing completed"
            }
            graph.dao.replaceChunks(id, result.chunks, result.status, result.detail)
            Result.success()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { graph.dao.status(id, IndexStatus.IMPORTED, "Paused; index again to resume") }
            throw cancelled
        } catch (_: SecurityException) {
            graph.dao.replaceChunks(id, emptyList(), IndexStatus.NO_ACCESS, "Access was revoked; select this file again")
            Result.failure()
        } catch (_: Exception) {
            // No file names, content, model prompts, or native exception text go into logs.
            graph.dao.status(id, IndexStatus.FAILED, "Could not index this file. Check its format or model and retry.")
            Result.failure()
        }
    }
}
