package dev.keeps.library.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import dev.keeps.library.catalog.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Provider identity, never a guessed match based on filename/size. */
fun documentKey(uri: Uri): String? = runCatching {
    "${uri.authority}:${DocumentsContract.getDocumentId(uri)}"
}.getOrNull()

fun hasReadAccess(context: Context, asset: AssetEntity): Boolean = ReadAccessSnapshot(context).canRead(asset)

fun assetKind(mime: String, name: String): AssetKind = when {
    mime.startsWith("image/") -> AssetKind.IMAGE
    mime.startsWith("video/") -> AssetKind.VIDEO
    mime.startsWith("audio/") -> AssetKind.AUDIO
    mime == "application/pdf" -> AssetKind.DOCUMENT
    mime.startsWith("text/") || name.endsWith(".md", true) -> AssetKind.TEXT
    else -> AssetKind.OTHER
}

data class ImportResult(val added: Int, val restored: Int)
data class FolderScan(val files: Int, val limited: Boolean, val failedFolders: Int)

class DocumentImporter(private val context: Context, private val dao: LibraryDao) {
    private val resolver get() = context.contentResolver

    suspend fun import(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var restored = 0
        for (uri in uris) {
            currentCoroutineContext().ensureActive()
            try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            catch (_: SecurityException) { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val result = catalog(uri)
            if (result.first) added++
            if (result.second) restored++
        }
        ImportResult(added, restored)
    }

    suspend fun chooseFolder(tree: Uri): FolderScan = withContext(Dispatchers.IO) {
        try { resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) { resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = metadata(root).name
        dao.putLocation(LocationEntity(tree.toString(), name))
        dao.putFolder(FolderEntity(root.toString(), tree.toString(), null, name))
        scan(tree.toString())
    }

    suspend fun scan(treeString: String): FolderScan = withContext(Dispatchers.IO) {
        val tree = Uri.parse(treeString)
        if (tree.scheme == "file") {
            val root = FolderEntity(treeString, treeString, null, StorageAccess(context).info(tree).name)
            val count = enumerateFiles(root)
            return@withContext FolderScan(count, false, 0)
        }
        val queue = ArrayDeque<Pair<Uri, Int>>()
        queue.add(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)) to 0)
        val visited = mutableSetOf<String>()
        var files = 0
        var entries = 0
        var failed = 0
        var limited = false
        // Bounded discovery; opening a folder can enumerate its direct children on demand.
        while (queue.isNotEmpty() && visited.size < 200 && entries < 2_000) {
            currentCoroutineContext().ensureActive()
            val (folder, depth) = queue.removeFirst()
            if (!visited.add(folder.toString())) continue
            try {
                val children = enumerate(tree, folder, 2_000 - entries)
                files += children.fileCount
                entries += children.entryCount
                limited = limited || children.limited
                if (depth < 8) children.folders.forEach { queue.add(Uri.parse(it.uri) to depth + 1) }
                else if (children.folders.isNotEmpty()) limited = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                failed++
                dao.assets().filter { it.treeUri == treeString && (depth == 0 || it.parentUri == folder.toString()) }.forEach {
                    dao.replaceChunks(it.id, emptyList(), IndexStatus.NO_ACCESS, "Folder unavailable; reconnect or allow access again")
                }
            }
        }
        if (queue.isNotEmpty()) limited = true
        val location = dao.locations().firstOrNull { it.uri == treeString }
        if (location != null) dao.putLocation(location.copy(detail = when {
            failed > 0 -> "$failed folders unavailable · refresh or allow access again"
            limited -> "Partial discovery · open folders to see more"
            else -> "Chosen folder · ${StorageAccess(context).capabilities(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), treeString).label.lowercase()}"
        }))
        FolderScan(files, limited, failed)
    }

    suspend fun openFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        if (Uri.parse(folder.uri).scheme == "file") enumerateFiles(folder)
        else if (folder.treeUri != "mediastore") enumerate(Uri.parse(folder.treeUri), Uri.parse(folder.uri), 2_000)
        Unit
    }

    suspend fun connectStorage(): Int = withContext(Dispatchers.IO) {
        val storage = StorageAccess(context)
        check(android.os.Environment.isExternalStorageManager()) { "Allow storage access in Android settings" }
        storage.roots().forEach { root ->
            val uri = Uri.fromFile(root).toString()
            val name = if (root == android.os.Environment.getExternalStorageDirectory()) "Internal storage" else root.name
            dao.putLocation(LocationEntity(uri, name, "Device storage"))
            dao.putFolder(FolderEntity(uri, uri, null, name))
        }
        storage.roots().size
    }
    private suspend fun enumerateFiles(folder: FolderEntity): Int {
        val children = StorageAccess(context).children(Uri.parse(folder.uri)).filter { it.name != ".KeepsTrash" }
        for (info in children) {
            currentCoroutineContext().ensureActive()
            if (info.mime == DocumentsContract.Document.MIME_TYPE_DIR) dao.putFolder(FolderEntity(info.uri.toString(), folder.treeUri, folder.uri, info.name))
            else catalogObserved(info, folder.treeUri, folder.uri, folder.name)
        }
        val seen = children.map { it.uri.toString() }.toSet()
        dao.assets().filter { it.parentUri == folder.uri && !it.trashed && it.uri !in seen }.forEach {
            dao.replaceChunks(it.id, emptyList(), IndexStatus.NO_ACCESS, "File missing; refresh or reconnect storage")
        }
        return children.count { it.mime != DocumentsContract.Document.MIME_TYPE_DIR }
    }

    suspend fun catalogObserved(info: StorageInfo, tree: String?, parent: String?, folderName: String?): AssetEntity {
        catalog(info.uri, Metadata(info.name, info.mime, info.size, info.modified, info.flags), tree, parent, folderName)
        return dao.findByUri(info.uri.toString()) ?: sourceKey(info.uri)?.let { dao.findByDocumentKey(it) } ?: error("Could not catalog this file")
    }

    private data class Children(val folders: List<FolderEntity>, val fileCount: Int, val entryCount: Int, val limited: Boolean)
    private suspend fun enumerate(tree: Uri, parent: Uri, limit: Int): Children {
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS)
        val parentName = metadata(parent).name
        val folders = mutableListOf<FolderEntity>()
        val seenKeys = mutableSetOf<String>()
        val seenFolders = mutableSetOf<String>()
        var files = 0
        var rows = 0
        var limited = false
        resolver.query(childUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (rows >= limit) { limited = true; break }
                rows++
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                val name = cursor.getString(1) ?: "Unnamed file"
                if (name == ".KeepsTrash") continue
                val mime = cursor.getString(2) ?: "application/octet-stream"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val folder = FolderEntity(uri.toString(), tree.toString(), parent.toString(), name)
                    dao.putFolder(folder)
                    seenFolders += folder.uri
                    folders += folder
                } else {
                    val size = if (cursor.isNull(3)) -1L else cursor.getLong(3)
                    val modified = if (cursor.isNull(4)) 0 else cursor.getLong(4)
                    val flags = if (cursor.isNull(5)) 0 else cursor.getInt(5)
                    catalog(uri, Metadata(name, mime, size, modified, flags), tree.toString(), parent.toString(), parentName)
                    documentKey(uri)?.let { seenKeys += it }
                    files++
                }
            }
        } ?: error("This folder is unavailable. Allow access again or reconnect storage.")
        // A failed/truncated listing cannot prove that files disappeared.
        if (!limited) {
            dao.assets().filter { !it.trashed && it.parentUri == parent.toString() && it.documentKey !in seenKeys }.forEach {
                dao.replaceChunks(it.id, emptyList(), IndexStatus.NO_ACCESS, "File missing from this folder; refresh or select again")
            }
            dao.folders(tree.toString()).filter { it.parentUri == parent.toString() && it.uri !in seenFolders }.forEach { dao.removeFolder(it.uri) }
        }
        return Children(folders, files, rows, limited)
    }

    private data class Metadata(val name: String, val mime: String, val size: Long, val modified: Long, val flags: Int)
    private fun metadata(uri: Uri): Metadata {
        var name = "Selected file"
        var size = -1L
        var modified = 0L
        var flags = 0
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) error("This file is unavailable")
            fun long(column: String, fallback: Long): Long {
                val index = cursor.getColumnIndex(column)
                return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else fallback
            }
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
            size = long(OpenableColumns.SIZE, -1)
            modified = long(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0)
            flags = long(DocumentsContract.Document.COLUMN_FLAGS, 0).toInt()
        } ?: error("Cannot read file metadata")
        return Metadata(name, resolver.getType(uri) ?: "application/octet-stream", size, modified, flags)
    }

    private suspend fun catalog(uri: Uri, supplied: Metadata? = null, tree: String? = null, parent: String? = null, folderName: String? = null): Pair<Boolean, Boolean> {
        val info = supplied ?: metadata(uri)
        val key = sourceKey(uri)
        val existing = dao.findByUri(uri.toString()) ?: key?.let { dao.findByDocumentKey(it) }
            // v1 rows have no document key; preserve their IDs, favorites and compatible vectors.
            ?: if (key != null) dao.assets().firstOrNull { it.documentKey == null && documentKey(Uri.parse(it.uri)) == key } else null
        if (existing == null) {
            dao.insertObservedAsset(AssetEntity(UUID.randomUUID().toString(), uri.toString(), info.name, info.mime,
                assetKind(info.mime, info.name), info.size, documentKey = key, treeUri = tree, parentUri = parent,
                folderName = folderName, modifiedAt = info.modified, providerFlags = info.flags))
            return true to false
        }
        val changed = existing.size != info.size || existing.name != info.name ||
            (existing.modifiedAt > 0 && info.modified > 0 && existing.modifiedAt != info.modified)
        val restored = existing.status == IndexStatus.NO_ACCESS
        // Keep a usable URI route. Tree discovery adds truthful folder metadata to selected files.
        dao.observeSource(existing.copy(uri = if (tree != null || !hasReadAccess(context, existing)) uri.toString() else existing.uri,
            name = info.name, mimeType = info.mime, kind = assetKind(info.mime, info.name), size = info.size,
            documentKey = key, treeUri = tree ?: existing.treeUri, parentUri = parent ?: existing.parentUri,
            folderName = folderName ?: existing.folderName, modifiedAt = info.modified, providerFlags = info.flags))
        if (changed || restored) dao.replaceChunks(existing.id, emptyList(), IndexStatus.IMPORTED,
            if (existing.searchExcluded) "Excluded from content search" else "File changed or access restored; prepare again")
        return false to restored
    }

    private fun sourceKey(uri: Uri): String? {
        if (uri.scheme != "file") return documentKey(uri)
        val file = java.io.File(uri.path!!)
        val root = StorageAccess(context).roots().firstOrNull { file.canonicalPath.startsWith(it.canonicalPath + "/") } ?: return "file:${file.canonicalPath}"
        val volume = if (root == android.os.Environment.getExternalStorageDirectory()) "primary" else root.name
        return "com.android.externalstorage.documents:$volume:${file.relativeTo(root).invariantSeparatorsPath}"
    }
}
