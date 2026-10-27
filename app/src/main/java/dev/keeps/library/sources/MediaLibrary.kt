package dev.keeps.library.sources

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.DocumentsContract as Docs
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import dev.keeps.library.catalog.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DiscoveryProgress(val running: Boolean = false, val files: Int = 0, val label: String = "")

/** Permission-scoped metadata discovery. Batched writes never queue AI or read file contents. */
class MediaLibrary(private val context: Context, private val dao: LibraryDao) {
    private val gate = Mutex()
    private val mutableProgress = MutableStateFlow(DiscoveryProgress())
    val progress = mutableProgress.asStateFlow()

    suspend fun scan(fixturePrefix: String? = null): Int {
        val access = ReadAccessSnapshot(context)
        return scanAllowed(listOf(AssetKind.IMAGE, AssetKind.VIDEO, AssetKind.AUDIO).filter { mediaAccess(context, it) }, fixturePrefix, access)
    }
    /** Shell identity does not change checkSelfPermission's UID. Every test query is UUID-scoped. */
    suspend fun scanFixtureMedia(prefix: String): Int {
        require(prefix.matches(Regex("fixture-keeps-media-[0-9a-f-]{36}")))
        return scanAllowed(listOf(AssetKind.IMAGE, AssetKind.VIDEO, AssetKind.AUDIO), prefix, ReadAccessSnapshot(context))
    }
    private suspend fun scanAllowed(kinds: List<AssetKind>, fixturePrefix: String?, access: ReadAccessSnapshot): Int = withContext(Dispatchers.IO) { gate.withLock {
        var count = 0
        mutableProgress.value = DiscoveryProgress(true, 0, "Finding device media")
        try {
            val previous = dao.assets()
            val byUri = previous.associateBy { it.uri }
            val byKey = previous.filter { it.documentKey != null }.associateBy { it.documentKey }
            val rows = mutableListOf<AssetEntity>()
            val folders = linkedMapOf<String, FolderEntity>()
            val knownFolders = mutableSetOf<String>()
            suspend fun flush() {
                if (rows.isEmpty()) return
                dao.observeBatch(rows.toList(), folders.values.toList())
                rows.clear(); folders.clear()
                mutableProgress.value = mutableProgress.value.copy(files = count)
                yield()
            }
            fun folder(folder: FolderEntity) { if (knownFolders.add(folder.uri)) folders[folder.uri] = folder }
            for (volume in MediaStore.getExternalVolumeNames(context)) {
                for (kind in kinds) {
                    val collection = when (kind) {
                        AssetKind.IMAGE -> MediaStore.Images.Media.getContentUri(volume)
                        AssetKind.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
                        else -> MediaStore.Audio.Media.getContentUri(volume)
                    }
                    val date = if (kind == AssetKind.AUDIO) "date_added" else "datetaken"
                    val duration = if (kind == AssetKind.IMAGE) "date_added" else "duration"
                    val seen = mutableSetOf<String>()
                    context.contentResolver.query(collection, arrayOf("_id", "_display_name", "mime_type", "_size", "date_modified", "relative_path", date, duration),
                        "is_pending=0 AND is_trashed=0" + if (fixturePrefix == null) "" else " AND _display_name LIKE ?",
                        fixturePrefix?.let { arrayOf("$it%") }, "date_modified DESC")?.use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                            val name = cursor.getString(1) ?: "Media"
                            val path = cursor.getString(5).orEmpty()
                            val parent = "media-album:$volume:$path"
                            val album = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Media" }
                            folder(FolderEntity(parent, "mediastore", null, album))
                            // Primary shared-storage identity is exact; avoid one provider RPC per item.
                            val key = if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) "com.android.externalstorage.documents:primary:$path$name"
                            else runCatching { MediaStore.getDocumentUri(context, uri)?.let(::documentKey) }.getOrNull() ?: "media:$volume:$kind:${cursor.getLong(0)}"
                            val old = byUri[uri.toString()] ?: byKey[key]
                            val captured = cursor.getLong(6) * if (kind == AssetKind.AUDIO) 1_000 else 1
                            val observed = AssetEntity(old?.id ?: UUID.randomUUID().toString(), uri.toString(), name,
                                cursor.getString(2) ?: "application/octet-stream", kind, cursor.getLong(3), documentKey = key,
                                treeUri = "mediastore", parentUri = parent, folderName = album, modifiedAt = cursor.getLong(4) * 1_000,
                                capturedAt = captured, durationMs = if (kind == AssetKind.IMAGE) 0 else cursor.getLong(7))
                            rows += if (old != null && old.treeUri != "mediastore" && access.canRead(old))
                                old.copy(capturedAt = captured, durationMs = observed.durationMs) else observed
                            seen += uri.toString(); count++
                            if (rows.size >= 128) flush()
                        }
                    } ?: error("Media access is unavailable; allow photos, videos or audio again")
                    flush()
                    val missing = previous.filter { it.treeUri == "mediastore" && it.kind == kind && !it.trashed && it.status != IndexStatus.NO_ACCESS &&
                        it.uri.startsWith(collection.toString() + "/") && it.uri !in seen && (fixturePrefix == null || it.name.startsWith(fixturePrefix)) }
                    dao.markUnavailable(missing.map { it.id })
                }
                if (access.allFiles && fixturePrefix == null) {
                    mutableProgress.value = DiscoveryProgress(true, count, "Finding documents and other files")
                    val collection = MediaStore.Files.getContentUri(volume)
                    context.contentResolver.query(collection, arrayOf("_data", "_display_name", "mime_type"),
                        "media_type NOT IN (1,2,3) AND mime_type IS NOT NULL AND is_pending=0 AND is_trashed=0", null, "date_modified DESC")?.use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val file = cursor.getString(0)?.let(::File)?.canonicalFile ?: continue
                            if (!access.canReadFile(file) || !file.isFile || file.path.contains("/.KeepsTrash/")) continue
                            val root = access.fileRoot(file) ?: continue
                            val tree = Uri.fromFile(root).toString()
                            val volumeId = if (root == android.os.Environment.getExternalStorageDirectory().canonicalFile) "primary" else root.name
                            val key = "com.android.externalstorage.documents:$volumeId:${file.relativeTo(root).invariantSeparatorsPath}"
                            val uri = Uri.fromFile(file).toString()
                            val old = byUri[uri] ?: byKey[key]
                            var ancestor: File? = file.parentFile
                            while (ancestor != null && ancestor.path.startsWith(root.path)) {
                                val parent = if (ancestor == root) null else ancestor.parentFile?.let { Uri.fromFile(it).toString() }
                                folder(FolderEntity(Uri.fromFile(ancestor).toString(), tree, parent, if (ancestor == root && volumeId == "primary") "Internal storage" else ancestor.name))
                                if (ancestor == root) break
                                ancestor = ancestor.parentFile
                            }
                            val name = cursor.getString(1) ?: file.name
                            val mime = cursor.getString(2) ?: "application/octet-stream"
                            val observed = AssetEntity(old?.id ?: UUID.randomUUID().toString(), uri, name, mime, assetKind(mime, name), file.length(),
                                documentKey = key, treeUri = tree, parentUri = Uri.fromFile(file.parentFile!!).toString(), folderName = file.parentFile!!.name,
                                modifiedAt = file.lastModified(), providerFlags = if (file.canWrite()) Docs.Document.FLAG_SUPPORTS_RENAME or Docs.Document.FLAG_SUPPORTS_DELETE or Docs.Document.FLAG_SUPPORTS_WRITE else 0)
                            rows += if (old != null && old.treeUri != "mediastore" && access.canRead(old)) old.copy(name = name, size = observed.size, modifiedAt = observed.modifiedAt) else observed
                            count++
                            if (rows.size >= 128) flush()
                        }
                    } ?: error("Device file access is unavailable; allow all files access again")
                    flush()
                }
            }
            count
        } finally { mutableProgress.value = DiscoveryProgress(false, count) }
    } }

    fun watch(changed: () -> Unit): ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = changed()
    }.also { context.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, it) }
    fun stop(observer: ContentObserver) = context.contentResolver.unregisterContentObserver(observer)
}
