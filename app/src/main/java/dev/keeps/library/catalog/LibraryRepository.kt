package dev.keeps.library.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.*
import dev.keeps.core.*
import dev.keeps.library.indexing.IndexWorker
import dev.keeps.library.indexing.PreparationController
import dev.keeps.library.indexing.AutomaticPreparation
import dev.keeps.library.inference.LocalEmbeddingProvider
import dev.keeps.library.sources.hasReadAccess
import dev.keeps.library.sources.ReadAccessSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SearchHit(val asset: AssetEntity, val chunk: ChunkEntity? = null, val reason: String = "Filename match", val moments: List<ChunkEntity> = emptyList())
data class SearchResponse(val hits: List<SearchHit>, val notice: String? = null)
data class PreparationResult(val queued: Int, val complete: Int, val modelNeeded: Int, val unsupported: Int, val pending: Int, val remaining: Int = 0) {
    val message get() = "$queued queued · $complete already prepared · $pending already waiting · $remaining left for another batch. " +
        if (modelNeeded > 0) "$modelNeeded files need a model; import the local AI pack for photos, videos and audio." else "$unsupported files use filename search only."
}

class LibraryRepository(private val context: Context, val dao: LibraryDao, private val provider: LocalEmbeddingProvider,
    private val preparation: dev.keeps.library.indexing.PreparationController = dev.keeps.library.indexing.PreparationController(context),
    private val executionGate: Mutex = Mutex()) {
    val assets = dao.observeAssets()
    private val queueGate = Mutex()

    suspend fun search(query: String, kind: AssetKind? = null, favorites: Boolean = false, content: Boolean = true,
        parentUri: String? = null, assetId: String? = null): SearchResponse = withContext(Dispatchers.Default) {
        val allAssets = dao.assets()
        val access = ReadAccessSnapshot(context)
        val readable = allAssets.filter { !it.trashed && access.canRead(it) }.map { it.id }.toSet()
        allAssets.filter { !it.trashed && it.id !in readable && it.status != IndexStatus.NO_ACCESS }.forEach {
            dao.replaceChunks(it.id, emptyList(), IndexStatus.NO_ACCESS, "Access revoked; select this file again")
        }
        val allowed = allAssets.filter { it.id in readable && it.status != IndexStatus.NO_ACCESS &&
            (kind == null || it.kind == kind) && (!favorites || it.favorite) && (parentUri == null || it.parentUri == parentUri) && (assetId == null || it.id == assetId) }
        if (query.isBlank()) return@withContext SearchResponse(allowed.map { SearchHit(it, reason = it.detail) })
        val byId = allowed.associateBy { it.id }
        val namespace = provider.namespace
        val chunks = if (content) dao.chunks(namespace).filter { it.assetId in byId && byId[it.assetId]?.contentSearchExcluded == false } else emptyList()
        val chunksByAsset = chunks.groupBy { it.assetId }
        val lexical = allowed.map { asset ->
            val text = chunksByAsset[asset.id].orEmpty().joinToString(" ") { it.text }
            asset.id to Ranking.lexicalScore(query, asset.name, text)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        var notice: String? = null
        val semantic = mutableMapOf<String, Pair<ChunkEntity, Float>>()
        val mediaCandidates = mutableMapOf<String, MutableList<Pair<ChunkEntity, Float>>>()
        if (content && provider.state.value.installed && chunks.any { it.vector != null }) {
            try {
                val vector = provider.embed(LocalEmbeddingProvider.query(query), namespace)
                chunks.filter { it.vector != null && it.namespace == namespace }.forEach { chunk ->
                    val score = maxOf(Vectors.cosine(vector, Vectors.decode(chunk.vector!!)),
                        chunk.visualVector?.let { Vectors.cosine(vector, Vectors.decode(it)) } ?: -1f)
                    val previous = semantic[chunk.assetId]
                    if (score > 0 && (previous == null || score > previous.second)) semantic[chunk.assetId] = chunk to score
                    if (score > 0 && chunk.recipe in listOf(VideoWindows.RECIPE, AudioWindows.RECIPE) && chunk.startMs != null && chunk.endMs != null)
                        mediaCandidates.getOrPut(chunk.assetId) { mutableListOf() }.add(chunk to score)
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
                chunk?.startMs != null && asset.kind == AssetKind.VIDEO -> if (chunk.recipe == VideoWindows.RECIPE)
                    "Similar moment ${formatTime(chunk.startMs)}–${formatTime(chunk.endMs ?: chunk.startMs)}" else "Similar frame at ${formatTime(chunk.startMs)}"
                chunk?.startMs != null -> "Similar audio ${formatTime(chunk.startMs)}–${formatTime(chunk.endMs ?: chunk.startMs)}"
                chunk != null -> "Similar content"
                else -> "Text or filename match"
            }
            val candidates = mediaCandidates[id].orEmpty()
            val chosen = MomentRanking.select(candidates.map { (c, score) -> ScoredMoment(c.id, TimeWindow(c.startMs!!, c.endMs!!), score) })
            val byChunk = candidates.associate { it.first.id to it.first }
            SearchHit(asset, chunk, reason, chosen.mapNotNull { byChunk[it.id] })
        }
        SearchResponse(hits, notice)
    }

    suspend fun queueAll(kinds: Set<AssetKind> = AssetKind.entries.toSet()): PreparationResult {
        val limit = context.getSharedPreferences("preparation", Context.MODE_PRIVATE).getInt("batchSize", 25).takeIf { it > 0 } ?: Int.MAX_VALUE
        return queueSelected(null, kinds, limit)
    }

    suspend fun queueSelected(ids: Set<String>?, kinds: Set<AssetKind> = AssetKind.entries.toSet(), maxNewFiles: Int = Int.MAX_VALUE, automatic: Boolean = false): PreparationResult = withContext(Dispatchers.IO) { queueGate.withLock {
        val policy = preparation.controls.value
        check(automatic || !policy.automaticEnabled) { "Turn off automatic preparation before starting a manual run." }
        check(automatic || !policy.paused || dao.nextPreparation(false) == null) { "Resume or end the paused run before starting a new selection." }
        if (automatic && (!policy.automaticEnabled || IndexWorker.isPaused(context))) return@withLock PreparationResult(0, 0, 0, 0, 0)
        val installed = provider.state.value.installed
        val namespace = provider.namespace
        val compatible = dao.compatiblePreparedIds(namespace, !installed).toSet()
        val access = ReadAccessSnapshot(context)
        val files = dao.assets().filter { it.kind in kinds && (ids == null || it.id in ids) &&
            (!automatic || it.kind in policy.automaticKinds && (policy.automaticIds == null || it.id in policy.automaticIds) &&
                it.status != IndexStatus.FAILED && !(it.status == IndexStatus.PARTIAL && (it.detail.startsWith("Stopped after") || it.detail.contains("decoder stopped")))) }
        val selected = mutableListOf<String>()
        var complete = 0; var modelNeeded = 0; var unsupported = 0; var pending = 0
        for (asset in files) {
            val hasIndex = asset.id in compatible
            // Partial windows can still need continuation; capped text/PDF is intentionally complete.
            val finished = hasIndex && (asset.status != IndexStatus.PARTIAL || when (asset.kind) {
                AssetKind.TEXT -> asset.detail.startsWith("First 500 KB")
                AssetKind.DOCUMENT -> asset.detail.startsWith("First 24")
                AssetKind.VIDEO, AssetKind.AUDIO -> asset.durationMs > 0 && dao.assetChunks(asset.id).count {
                    it.namespace == namespace && it.vector != null && it.recipe == if (asset.kind == AssetKind.VIDEO) VideoWindows.RECIPE else AudioWindows.RECIPE
                } >= Chunking.windows(asset.durationMs).size
                else -> false
            })
            when (Preparation.choice(asset.kind, asset.mimeType, asset.name, asset.status, installed, finished, asset.contentSearchExcluded, asset.trashed)) {
                PreparationChoice.COMPLETE -> complete++
                PreparationChoice.MODEL_NEEDED -> modelNeeded++
                PreparationChoice.UNSUPPORTED -> unsupported++
                PreparationChoice.BUSY -> if (!automatic && asset.automaticPreparation && asset.status == IndexStatus.QUEUED) selected += asset.id else pending++
                PreparationChoice.PREPARE -> if (access.canRead(asset)) selected += asset.id
                PreparationChoice.UNAVAILABLE -> Unit
            }
        }
        var queued = 0
        if (selected.isNotEmpty() && maxNewFiles > 0) {
            if (automatic && (!preparation.controls.value.automaticEnabled || IndexWorker.isPaused(context))) return@withLock PreparationResult(0, complete, modelNeeded, unsupported, pending)
            if (!automatic && dao.nextPreparation(false) == null) {
                preparation.beginSession(PreparationProgress.from(dao.assets(), installed).ready)
                if (preparation.commandResume(policy.revision) < 0) return@withLock PreparationResult(0, complete, modelNeeded, unsupported, pending)
            }
            selected.take(maxNewFiles.coerceAtLeast(0)).chunked(128).forEach { queued += dao.queuePreparation(it, automatic) }
            if (queued > 0 && preparation.controls.value.status.phase !in listOf(PreparationPhase.PROCESSING, PreparationPhase.RESTING))
                preparation.publish(preparation.controls.value.revision, PreparationPhase.SCHEDULED, "Waiting for Android and the selected preparation conditions")
            // One persistent library pump, rather than thousands of concurrent preprocessing jobs.
            IndexWorker.enqueueQueue(context, automatic = automatic)
        }
        PreparationResult(queued, complete, modelNeeded, unsupported, pending, (selected.size - queued).coerceAtLeast(0))
    } }

    suspend fun queue(asset: AssetEntity): Boolean = queueSelected(setOf(asset.id)).queued > 0

    suspend fun resumePending() = withContext(Dispatchers.IO) { queueGate.withLock {
        dao.recoverLegacyPausedSelection()
        if (!IndexWorker.isPaused(context)) {
            if (executionGate.tryLock()) { try { dao.recoverPreparation() } finally { executionGate.unlock() } }
            val automatic = preparation.controls.value.automaticEnabled
            if (dao.nextPreparation(automatic) != null) {
                if (preparation.controls.value.status.phase == PreparationPhase.IDLE) preparation.publish(preparation.controls.value.revision, PreparationPhase.SCHEDULED, "Waiting for Android to continue the saved queue")
                IndexWorker.enqueueQueue(context, automatic = automatic)
            }
        }
    } }

    suspend fun pause(expectedRevision: Long? = null) = withContext(Dispatchers.IO) {
        if (expectedRevision != null && preparation.controls.value.revision != expectedRevision) return@withContext
        preparation.requestPause(expectedRevision)
        val pausedRevision = preparation.controls.value.revision
        queueGate.withLock {
            if (preparation.controls.value.revision != pausedRevision || !preparation.controls.value.paused) return@withLock
            AutomaticPreparation.cancel(context)
            WorkManager.getInstance(context).cancelAllWorkByTag("indexing").result.get()
            dao.pausePreparation()
        }
    }

    /** Resume is deliberately not queueAll: only retained IDs and their original mode may continue. */
    suspend fun resumePreparation(chargingOnly: Boolean? = null, expectedRevision: Long? = null) {
      val resumed = withContext(Dispatchers.IO) { queueGate.withLock {
        val controls = preparation.controls.value
        if (expectedRevision != null && controls.revision != expectedRevision) return@withLock false
        if (!controls.status.canResume) return@withLock false
        dao.recoverPreparation() // A stopped process can leave its final row INDEXING; the paused queue still owns it.
        if (chargingOnly != null && !controls.automaticEnabled) preparation.chargingOnly(chargingOnly)
        WorkManager.getInstance(context).cancelAllWorkByTag("indexing").result.get()
        if (preparation.commandResume(controls.revision) < 0) return@withLock false
        if (controls.automaticEnabled) AutomaticPreparation.schedule(context, soon = true)
        val automatic = controls.automaticEnabled
        if (dao.nextPreparation(automatic) != null) IndexWorker.enqueueQueue(context, automatic = automatic)
        else if (!automatic) preparation.publish(preparation.controls.value.revision, PreparationPhase.COMPLETE, "No files remain in this run. Choose a scope to start another.")
        true
      } }
      if (resumed) seedAutomaticQueue()
    }

    suspend fun endManualRun() = withContext(Dispatchers.IO) { queueGate.withLock {
        check(!preparation.controls.value.automaticEnabled) { "Turn off automatic preparation to end its run." }
        check(preparation.controls.value.status.phase == PreparationPhase.PAUSED) { "Pause the run and wait for the current step to finish first." }
        preparation.requestPause()
        WorkManager.getInstance(context).cancelAllWorkByTag("indexing").result.get()
        dao.stopManualPreparation()
        preparation.endSession()
    } }

    suspend fun automatic(enabled: Boolean, kinds: Set<AssetKind> = preparation.controls.value.automaticKinds, ids: Set<String>? = null) {
      val accepted = withContext(Dispatchers.IO) { queueGate.withLock {
        val previousRevision = preparation.controls.value.revision
        check(preparation.controls.value.status.phase != PreparationPhase.PAUSING) { "Wait for the current step to finish." }
        if (enabled && !preparation.controls.value.automaticEnabled) check(dao.nextPreparation(false) == null) { "Resume or end the manual run before enabling automatic preparation." }
        if (enabled && !preparation.controls.value.automaticEnabled) preparation.beginSession(PreparationProgress.from(dao.assets(), provider.state.value.installed).ready)
        if (enabled) {
            preparation.automatic(true, kinds, ids)
            if (preparation.commandResume(previousRevision) < 0) return@withLock false
            AutomaticPreparation.schedule(context, soon = true)
        } else {
            preparation.requestPause()
            preparation.automatic(false, kinds, ids)
            AutomaticPreparation.cancel(context)
            WorkManager.getInstance(context).cancelAllWorkByTag("indexing-auto").result.get()
            dao.stopAutomaticPreparation()
            // Wait for the worker's finalizer before displaying an idle run.
            if (dao.nextPreparation(false) == null) preparation.endWhenStopped()
        }
        true
      } }
      if (accepted && enabled) seedAutomaticQueue()
    }

    /** Populate approved catalog work before screen-off, without doing inference or expanding a selected-ID scope. */
    private suspend fun seedAutomaticQueue() {
        val controls = preparation.controls.value
        if (!controls.automaticEnabled || controls.paused) return
        val result = queueSelected(controls.automaticIds, controls.automaticKinds, automatic = true)
        if (!preparation.current(controls.revision)) return
        val pending = dao.assets().any { it.automaticPreparation && it.status in listOf(IndexStatus.QUEUED, IndexStatus.INDEXING) && !it.contentSearchExcluded && !it.trashed }
        if (pending) {
            if (preparation.controls.value.status.phase !in listOf(PreparationPhase.PROCESSING, PreparationPhase.RESTING))
                preparation.blockedReason(true)?.let { preparation.publish(controls.revision, PreparationPhase.WAITING, it) }
        } else preparation.publish(controls.revision, if (result.modelNeeded > 0) PreparationPhase.NEEDS_REVIEW else if (result.complete > 0) PreparationPhase.COMPLETE else PreparationPhase.WAITING,
            if (result.modelNeeded > 0) "A compatible search model is needed for the approved content types."
            else if (result.complete > 0) "Approved catalog files are prepared. New files will be checked when allowed."
            else "No eligible files found yet in the approved scope. Discovery will check again when charging with the screen off.")
    }

    /** Screen-off/charger events may advance an idle delayed request; never replace a running pass or undo Pause. */
    suspend fun wakeAutomatic() {
        val wake = withContext(Dispatchers.IO) { queueGate.withLock {
            val controls = preparation.controls.value
            if (!controls.automaticEnabled || controls.paused || preparation.blockedReason(true) != null) return@withLock false
            val manager = WorkManager.getInstance(context)
            if (manager.getWorkInfosForUniqueWork(IndexWorker.AUTO_QUEUE_NAME).get().any { it.state == WorkInfo.State.RUNNING }) return@withLock false
            manager.cancelUniqueWork(IndexWorker.AUTO_QUEUE_NAME).result.get()
            preparation.publish(controls.revision, PreparationPhase.SCHEDULED, "Conditions allow preparation. Waiting for Android to start the saved queue.")
            if (dao.nextPreparation(true) != null) IndexWorker.enqueueQueue(context, automatic = true, expectedRevision = controls.revision)
            true
        } }
        if (wake) {
            seedAutomaticQueue()
            AutomaticPreparation.schedule(context, soon = true, delaySeconds = 0)
        }
    }

    suspend fun applyModelChange(previousNamespace: String) {
        if (previousNamespace != provider.namespace) dao.invalidateEmbeddings()
    }

    suspend fun remove(asset: AssetEntity) {
        WorkManager.getInstance(context).cancelUniqueWork("asset-${asset.id}").result.get()
        dao.remove(asset.id)
        // Never release a shared tree grant when removing one catalog item.
        try { if (asset.treeUri == null) context.contentResolver.releasePersistableUriPermission(Uri.parse(asset.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Provider may already have revoked it. */ }
    }

    suspend fun exclude(asset: AssetEntity, excluded: Boolean) {
        dao.exclude(asset.id, excluded)
        WorkManager.getInstance(context).cancelUniqueWork("asset-${asset.id}").result.get()
    }

    suspend fun excludeSelected(ids: List<String>, excluded: Boolean) = withContext(Dispatchers.IO) {
        dao.excludeMany(ids, excluded)
        ids.distinct().forEach { WorkManager.getInstance(context).cancelUniqueWork("asset-$it").result.get() }
    }
}
