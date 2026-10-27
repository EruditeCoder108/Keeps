package dev.keeps.library.indexing

import android.content.Context
import androidx.work.*
import dev.keeps.core.*
import dev.keeps.library.AppGraph
import dev.keeps.library.KeepsApplication
import dev.keeps.library.catalog.contentSearchExcluded
import dev.keeps.library.catalog.PreparationProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class IndexWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val graph = (applicationContext as KeepsApplication).graph
        graph.preparationGate.withLock {
            val controller = graph.preparation
            val automatic = inputData.getBoolean("automatic", false)
            val revision = inputData.getLong("revision", controller.controls.value.revision)
            if (!controller.current(revision) || automatic != controller.controls.value.automaticEnabled) return@withLock Result.success()
            val owner = id.toString()
            if (!controller.workerStarted(owner, revision)) return@withLock Result.success()
            try {
                graph.dao.recoverPreparation()
                val started = android.os.SystemClock.elapsedRealtime()
                val legacyId = inputData.getString("assetId")
                var files = 0
                while (controller.current(revision) && PreparationScheduling.withinPass(files, android.os.SystemClock.elapsedRealtime() - started)) {
                    currentCoroutineContext().ensureActive()
                    val next = if (legacyId != null) graph.dao.asset(legacyId)?.takeIf { it.status == IndexStatus.QUEUED } else graph.dao.nextPreparation(automatic)
                    if (next == null) break
                    controller.blockedReason(automatic)?.let { return@withLock defer(graph, revision, automatic, it) }
                    val rest = controller.restRemainingMs()
                    if (rest > 0) {
                        controller.publish(revision, PreparationPhase.RESTING, "A short pacing break; resumes automatically. Completed work is saved.")
                        val restBegan = android.os.SystemClock.elapsedRealtime()
                        val session = controller.controls.value.sessionStartedAt
                        try { delay(rest) } finally { controller.recordRest(android.os.SystemClock.elapsedRealtime() - restBegan, session) }
                        if (!controller.current(revision)) break
                        controller.blockedReason(automatic)?.let { return@withLock defer(graph, revision, automatic, it) }
                    }
                    controller.publish(revision, PreparationPhase.PROCESSING, "Processing one file at a time. Originals stay in place.")
                    try { prepare(graph, next.id, automatic, revision) }
                    catch (deferred: ResourceDeferred) { return@withLock defer(graph, revision, automatic, deferred.message.orEmpty()) }
                    files++
                    if (legacyId != null) break
                }
                currentCoroutineContext().ensureActive()
                if (controller.current(revision)) {
                    if (graph.dao.nextPreparation(automatic) != null) {
                        controller.publish(revision, PreparationPhase.SCHEDULED, "Waiting for Android to continue the next saved batch.")
                        enqueueQueue(applicationContext, automatic = automatic, continuation = true, expectedRevision = revision)
                    } else {
                        val summary = PreparationProgress.from(graph.dao.assets(), graph.embeddings.state.value.installed, automatic)
                        controller.publish(revision, if (summary.failed + summary.partial > 0) PreparationPhase.NEEDS_REVIEW else PreparationPhase.COMPLETE, summary.summary)
                    }
                }
                Result.success()
            } catch (cancelled: CancellationException) {
                controller.publish(revision, PreparationPhase.SCHEDULED, "Android interrupted this pass. The saved queue will continue when allowed.")
                throw cancelled
            } catch (_: Exception) {
                controller.requestPause(revision, "Preparation stopped unexpectedly. Your saved queue is kept; review access and storage, then resume.")
                Result.failure()
            } finally { withContext(NonCancellable) {
                try { if (isPaused(applicationContext)) graph.embeddings.unload() }
                finally { controller.workerFinished(owner) }
            } }
        }
    }

    private fun defer(graph: AppGraph, revision: Long, automatic: Boolean, reason: String): Result {
        if (graph.preparation.publish(revision, PreparationPhase.WAITING, reason)) {
            // A resource wait is not a failed attempt: do not grow retry backoff after every heat/idle check.
            val delay = if (reason.contains("phone to be idle")) PreparationScheduling.INTERACTIVE_RECHECK_MILLIS else PreparationScheduling.RESOURCE_RECHECK_MILLIS
            enqueueQueue(applicationContext, delay, automatic, continuation = true, expectedRevision = revision)
        }
        return Result.success()
    }

    private suspend fun prepare(graph: AppGraph, assetId: String, automatic: Boolean, revision: Long) {
        val asset = graph.dao.asset(assetId) ?: return
        if (asset.contentSearchExcluded || asset.trashed || asset.status != IndexStatus.QUEUED || !graph.preparation.current(revision)) return
        if (!graph.dao.indexState(asset, IndexStatus.INDEXING, "Preparing this file on your phone")) return
        val began = android.os.SystemClock.elapsedRealtime()
        val session = graph.preparation.controls.value.sessionStartedAt
        try {
            val result = graph.indexer.index(asset, graph.dao.assetChunks(assetId), maxNewWindows = 2) { chunk, detail ->
                check(chunk.namespace == "lexical-v1" || chunk.namespace == graph.embeddings.namespace) { "Model changed during preparation" }
                val saved = if (asset.kind in listOf(AssetKind.TEXT, AssetKind.DOCUMENT)) graph.dao.checkpointDocument(asset, chunk, detail) else graph.dao.checkpointMedia(asset, chunk, detail)
                check(saved) { "Source or search scope changed" }
                graph.preparation.progressSaved()
                currentCoroutineContext().ensureActive()
                if (!graph.preparation.current(revision)) throw CancellationException("Preparation command changed")
                graph.preparation.blockedReason(automatic)?.let { throw ResourceDeferred(it) }
            }
            currentCoroutineContext().ensureActive()
            if (!graph.preparation.current(revision)) throw CancellationException("Preparation command changed")
            check(result.chunks.filter { it.vector != null }.all { it.namespace == graph.embeddings.namespace }) { "Model changed before indexing completed" }
            val committed = graph.dao.commitIndex(asset, result.chunks, result.status, result.detail)
            if (committed) graph.preparation.progressSaved()
            if (committed && result.resume) graph.dao.indexState(asset, IndexStatus.QUEUED, result.detail)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val disabled = automatic && !graph.preparation.controls.value.automaticEnabled
                val retained = graph.dao.assetChunks(assetId).isNotEmpty()
                graph.dao.indexState(asset, if (!disabled) IndexStatus.QUEUED else if (retained) IndexStatus.PARTIAL else IndexStatus.IMPORTED,
                    if (isPaused(applicationContext)) "Preparation paused; queue and completed content retained" else "Continuing interrupted preparation")
            }
            throw cancelled
        } catch (deferred: ResourceDeferred) {
            graph.dao.indexState(asset, IndexStatus.QUEUED, deferred.message.orEmpty())
            throw deferred
        } catch (_: SecurityException) {
            graph.dao.commitIndex(asset, emptyList(), IndexStatus.NO_ACCESS, "Access was revoked; select this file again")
        } catch (_: Exception) {
            val retained = graph.dao.assetChunks(assetId).size
            graph.dao.indexState(asset, if (retained > 0) IndexStatus.PARTIAL else IndexStatus.FAILED,
                if (retained > 0) "Stopped after $retained sections; prepare again to resume" else "Could not index this file. Check its format or model and retry.")
        } finally { graph.preparation.recordWork(android.os.SystemClock.elapsedRealtime() - began, session, revision) }
    }

    companion object {
        const val QUEUE_NAME = "keeps-content-preparation-v1"
        const val AUTO_QUEUE_NAME = "keeps-content-preparation-auto-v1"
        fun isPaused(context: Context) = context.getSharedPreferences("preparation", Context.MODE_PRIVATE).getBoolean("paused", false)
        fun setPaused(context: Context, paused: Boolean) {
            val controller = PreparationController(context)
            if (paused) controller.requestPause() else controller.commandResume()
        }
        fun enqueueQueue(context: Context, delayMs: Long = 0, automatic: Boolean = false, continuation: Boolean = false, expectedRevision: Long? = null) {
            synchronized(PreparationController.stateLock) {
                val controls = PreparationController.snapshot(context)
                if (controls.paused || automatic != controls.automaticEnabled || expectedRevision != null && expectedRevision != controls.revision) return
                WorkManager.getInstance(context).enqueueUniqueWork(if (automatic) AUTO_QUEUE_NAME else QUEUE_NAME,
                    if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                    request(delayMs = delayMs, chargingOnly = automatic || controls.chargingOnly, automatic = automatic, revision = controls.revision)).result.get()
            }
        }
        fun request(id: String? = null, delayMs: Long = 0, chargingOnly: Boolean = false, automatic: Boolean = false, revision: Long? = null): OneTimeWorkRequest {
            val input = Data.Builder().putBoolean("automatic", automatic)
            id?.let { input.putString("assetId", it) }; revision?.let { input.putLong("revision", it) }
            return OneTimeWorkRequestBuilder<IndexWorker>().setInputData(input.build()).addTag("indexing").addTag(if (automatic) "indexing-auto" else "indexing-manual")
                .setInitialDelay(delayMs.coerceIn(0, 300_000), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).setRequiresCharging(chargingOnly).build()).build()
        }
    }
    private class ResourceDeferred(message: String) : Exception(message)
}
