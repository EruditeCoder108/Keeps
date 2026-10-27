package dev.keeps.library.sources

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Docs
import androidx.work.*
import dev.keeps.core.FileNames
import dev.keeps.core.IndexStatus
import dev.keeps.library.KeepsApplication
import dev.keeps.library.catalog.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistent per-file journal. Original deletion is strictly after a reopened, hashed copy. */
class FileOperations(private val context: Context, private val dao: LibraryDao, private val storage: StorageAccess,
    private val importer: DocumentImporter) {
    private val work get() = WorkManager.getInstance(context)
    private val gate = Mutex()

    suspend fun enqueue(asset: AssetEntity, destination: FolderEntity, type: String = "COPY", name: String = asset.name): String {
        require(type in listOf("COPY", "MOVE", "TRASH", "RESTORE"))
        check(storage.capabilities(Uri.parse(destination.uri), destination.treeUri).create) { "Destination needs write access" }
        if (type != "COPY") check(storage.capabilities(Uri.parse(asset.uri), asset.treeUri).delete) { "Source needs delete permission; choose its folder again to allow changes" }
        val source = storage.info(Uri.parse(asset.uri))
        val operation = OperationEntity(UUID.randomUUID().toString(), type, asset.id, asset.uri, source.name, source.size,
            source.modified, asset.treeUri, destination.treeUri, destination.uri, FileNames.validate(name))
        dao.putOperation(operation)
        schedule(operation.id)
        return operation.id
    }

    private fun schedule(id: String) {
        work.enqueueUniqueWork("transfer-$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TransferWorker>().setInputData(workDataOf("operation" to id)).addTag("transfers").build())
    }
    suspend fun retry(id: String) {
        val op = dao.operation(id) ?: return
        if (op.state == "DONE") return
        work.cancelUniqueWork("transfer-$id").result.get()
        // A verified copy is reused, but source metadata and hashes are rechecked before deletion.
        dao.putOperation(op.copy(state = if (op.verifiedHash != null) "RESUME_DELETE" else "QUEUED", detail = "Retry requested"))
        schedule(id)
    }
    suspend fun cancel(id: String) {
        work.cancelUniqueWork("transfer-$id").result.get()
        val op = dao.operation(id) ?: return
        if (op.state != "DONE") dao.putOperation(op.copy(state = "CANCELLED", detail = if (op.verifiedHash == null)
            "Cancelled; original retained. Retry restarts the partial copy." else "Verified destination retained; review before resuming removal."))
    }

    /** Explicit recovery adopts only the journal's verified destination; never deletes the source. */
    suspend fun recoverDestination(id: String) = gate.withLock {
        val op = dao.operation(id) ?: return@withLock
        check(op.state in listOf("REVIEW", "CANCELLED", "FAILED") && op.verifiedHash != null)
        val target = Uri.parse(requireNotNull(op.resultUri))
        check(fingerprint(target) == (op.bytes to op.verifiedHash)) { "Destination changed; recovery stopped" }
        val info = storage.info(target)
        if (op.type == "COPY") importer.catalogObserved(info, op.destinationTree, op.destinationParent, "Recovered files")
        else {
            val asset = dao.asset(op.assetId) ?: error("File left the catalog")
            dao.updateAsset(asset.copy(uri = target.toString(), name = info.name, treeUri = op.destinationTree,
                parentUri = op.destinationParent, folderName = if (op.type == "TRASH") "Trash" else "Recovered files",
                documentKey = null, modifiedAt = info.modified, providerFlags = info.flags, size = info.size, trashed = op.type == "TRASH"))
            if (op.type == "RESTORE") dao.removeTrashEntry(asset.id)
        }
        dao.putOperation(op.copy(state = "DONE", detail = "Verified destination recovered. Any remaining source was left untouched."))
    }

    suspend fun rename(asset: AssetEntity, name: String) = gate.withLock {
        val safe = FileNames.validate(name)
        if (safe == asset.name) return@withLock
        asset.parentUri?.let { parent -> check(storage.children(Uri.parse(parent)).none { it.name.equals(safe, true) }) { "That name already exists" } }
        work.cancelUniqueWork("asset-${asset.id}").result.get()
        val uri = storage.rename(Uri.parse(asset.uri), asset.treeUri, safe)
        val info = storage.info(uri)
        dao.updateAsset(asset.copy(uri = uri.toString(), name = info.name, size = info.size, modifiedAt = info.modified,
            documentKey = null, providerFlags = info.flags))
        dao.replaceChunks(asset.id, emptyList(), IndexStatus.IMPORTED, "Renamed; prepare content search again")
    }

    suspend fun createFolder(parent: FolderEntity, name: String) {
        val uri = storage.create(Uri.parse(parent.uri), parent.treeUri, name, Docs.Document.MIME_TYPE_DIR)
        dao.putFolder(FolderEntity(uri.toString(), parent.treeUri, parent.uri, storage.info(uri).name))
    }

    suspend fun trash(asset: AssetEntity) {
        val tree = asset.treeUri ?: error("Choose this file's folder to enable recoverable trash")
        val parent = asset.parentUri ?: error("Choose this file's folder to enable recoverable trash")
        check(tree != "mediastore") { "Use Android's trash approval for gallery items" }
        val root = if (Uri.parse(tree).scheme == "file") Uri.parse(tree) else
            Docs.buildDocumentUriUsingTree(Uri.parse(tree), Docs.getTreeDocumentId(Uri.parse(tree)))
        val trash = storage.children(root).firstOrNull { it.name == ".KeepsTrash" && it.mime == Docs.Document.MIME_TYPE_DIR }?.uri
            ?: storage.create(root, tree, ".KeepsTrash", Docs.Document.MIME_TYPE_DIR)
        dao.putTrash(TrashEntity(asset.id, tree, parent, asset.name))
        enqueue(asset, FolderEntity(trash.toString(), tree, root.toString(), "Trash"), "TRASH")
    }

    suspend fun restore(asset: AssetEntity) {
        val entry = dao.trashEntry(asset.id) ?: error("The original folder is not recorded")
        enqueue(asset, FolderEntity(entry.originalParent, entry.originalTree, null, "Restored files"), "RESTORE", entry.originalName)
    }

    suspend fun deletePermanently(asset: AssetEntity) = gate.withLock {
        check(asset.trashed) { "Move to trash before permanently deleting" }
        storage.delete(Uri.parse(asset.uri), asset.treeUri)
        dao.removeTrashEntry(asset.id)
        dao.remove(asset.id)
    }

    private suspend fun fingerprint(uri: Uri): Pair<Long, String> {
        val hash = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        storage.resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer); if (n < 0) break
                bytes += n; hash.update(buffer, 0, n)
            }
        } ?: error("Cannot read the file for verification")
        return bytes to hash.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun execute(id: String): Boolean = withContext(Dispatchers.IO) { gate.withLock {
        var op = dao.operation(id) ?: return@withLock false
        if (op.state == "DONE") return@withLock true
        if (op.state in listOf("CANCELLED", "REVIEW", "SOURCE_DELETE_PENDING")) {
            dao.putOperation(op.copy(state = "REVIEW", detail = "Transfer interrupted. Review the destination, then retry; source is checked again before removal."))
            return@withLock false
        }
        try {
            val original = Uri.parse(op.sourceUri)
            val source = storage.info(original)
            check(source.size == op.sourceSize && source.modified == op.sourceModified && source.name == op.sourceName) { "Source changed since this transfer started; original retained" }
            val asset = dao.asset(op.assetId) ?: error("File left the catalog")
            if (op.verifiedHash == null) {
                op.resultUri?.let { storage.delete(Uri.parse(it), op.destinationTree) }
                val target = storage.create(Uri.parse(op.destinationParent), op.destinationTree, op.requestedName, asset.mimeType)
                op = op.copy(resultUri = target.toString(), state = "COPYING", bytes = 0, detail = "Copying; original retained")
                dao.putOperation(op)
                val hash = MessageDigest.getInstance("SHA-256")
                var bytes = 0L; var reported = 0L
                storage.resolver.openInputStream(original)?.use { input ->
                    storage.resolver.openOutputStream(target, "wt")?.use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            output.write(buffer, 0, n); hash.update(buffer, 0, n); bytes += n
                            if (bytes - reported >= 4 * 1024 * 1024) { reported = bytes; dao.putOperation(op.copy(bytes = bytes)) }
                        }
                        output.flush()
                    } ?: error("Cannot write destination; original retained")
                } ?: error("Cannot read source")
                op = op.copy(state = "VERIFYING", bytes = bytes, detail = "Checking destination bytes")
                dao.putOperation(op)
                val expected = hash.digest().joinToString("") { "%02x".format(it) }
                check(fingerprint(target) == (bytes to expected)) { "Destination verification failed; original retained" }
                check(source.size < 0 || bytes == source.size) { "Source length changed; original retained" }
                op = op.copy(verifiedHash = expected)
                dao.putOperation(op)
            }
            val target = Uri.parse(requireNotNull(op.resultUri))
            check(fingerprint(target) == (op.bytes to op.verifiedHash)) { "Destination changed; original retained" }
            if (op.type != "COPY") {
                check(fingerprint(original) == (op.bytes to op.verifiedHash)) { "Source changed; both files retained" }
                val current = storage.info(original)
                check(current.size == op.sourceSize && current.modified == op.sourceModified) { "Source changed; both files retained" }
                work.cancelUniqueWork("asset-${asset.id}").result.get()
                op = op.copy(state = "SOURCE_DELETE_PENDING", detail = "Copy verified; completing source removal")
                dao.putOperation(op)
                storage.delete(original, op.sourceTree)
            }
            val info = storage.info(target)
            if (op.type == "COPY") importer.catalogObserved(info, op.destinationTree, op.destinationParent, storage.info(Uri.parse(op.destinationParent)).name)
            else {
                val latest = dao.asset(asset.id) ?: asset
                dao.updateAsset(latest.copy(uri = target.toString(), name = info.name, treeUri = op.destinationTree,
                    parentUri = op.destinationParent, folderName = storage.info(Uri.parse(op.destinationParent)).name,
                    documentKey = null, size = info.size, modifiedAt = info.modified, providerFlags = info.flags, trashed = op.type == "TRASH"))
                if (op.type == "RESTORE") dao.removeTrashEntry(asset.id)
                if (latest.status in listOf(IndexStatus.INDEXING, IndexStatus.QUEUED)) dao.status(asset.id, IndexStatus.IMPORTED, "Transfer complete; resume preparation")
            }
            dao.putOperation(op.copy(state = "DONE", detail = when (op.type) { "TRASH" -> "In Keeps trash; restore anytime"; "RESTORE" -> "Restored; conflicts keep both files"; "MOVE" -> "Moved; bytes verified"; else -> "Copied; bytes verified" }))
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            dao.putOperation(op.copy(state = if (op.verifiedHash != null) "REVIEW" else "FAILED", detail = error.message ?: "Transfer failed; check access and free space"))
            false
        }
    } }
}

class TransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("operation") ?: return Result.failure()
        val graph = (applicationContext as KeepsApplication).graph
        return if (graph.operations.execute(id)) Result.success() else Result.failure()
    }
}
