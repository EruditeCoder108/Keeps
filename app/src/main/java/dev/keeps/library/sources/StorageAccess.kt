package dev.keeps.library.sources

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract as Docs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.keeps.core.FileNames
import java.io.File

data class StorageInfo(val uri: Uri, val name: String, val mime: String, val size: Long, val modified: Long, val flags: Int)
data class StorageCapabilities(val read: Boolean, val rename: Boolean, val delete: Boolean, val create: Boolean,
    val mediaConsent: Boolean = false) {
    val label get() = when { mediaConsent -> "Android approval for changes"; rename || delete || create -> "Read & write"; read -> "Read only"; else -> "Access unavailable" }
}

/** Live permission and provider flags are checked again at the mutation boundary. */
class StorageAccess(private val context: Context) {
    val resolver get() = context.contentResolver
    fun roots(): List<File> = context.getSystemService(StorageManager::class.java).storageVolumes
        .mapNotNull { it.directory }.filter { it.isDirectory }.distinctBy { it.absolutePath }
    fun managed(file: File): Boolean = Environment.isExternalStorageManager() && roots().any { root ->
        val path = file.canonicalPath; val base = root.canonicalPath
        path == base || path.startsWith("$base/")
    }
    fun info(uri: Uri): StorageInfo {
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path)); require(managed(file) && file.exists()) { "Allow storage access or reconnect this volume" }
            val directory = file.isDirectory
            val mime = if (directory) Docs.Document.MIME_TYPE_DIR else MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: if (file.extension in listOf("txt", "md", "csv", "log")) "text/plain" else "application/octet-stream"
            return StorageInfo(uri, file.name.ifEmpty { "Internal storage" }, mime, if (directory) 0 else file.length(), file.lastModified(),
                (if (file.canWrite()) Docs.Document.FLAG_SUPPORTS_RENAME or Docs.Document.FLAG_SUPPORTS_DELETE or Docs.Document.FLAG_SUPPORTS_WRITE else 0) or
                    (if (directory && file.canWrite()) Docs.Document.FLAG_DIR_SUPPORTS_CREATE else 0))
        }
        return resolver.query(uri, null, null, null, null)?.use { c ->
            check(c.moveToFirst()) { "This file is unavailable" }
            fun text(key: String, fallback: String) = c.getColumnIndex(key).let { if (it >= 0 && !c.isNull(it)) c.getString(it) else fallback }
            fun number(key: String, fallback: Long) = c.getColumnIndex(key).let { if (it >= 0 && !c.isNull(it)) c.getLong(it) else fallback }
            StorageInfo(uri, text(Docs.Document.COLUMN_DISPLAY_NAME, "File"), resolver.getType(uri) ?: "application/octet-stream",
                number(Docs.Document.COLUMN_SIZE, -1), number(Docs.Document.COLUMN_LAST_MODIFIED, 0), number(Docs.Document.COLUMN_FLAGS, 0).toInt())
        } ?: error("This file is unavailable")
    }
    fun capabilities(uri: Uri, tree: String? = null): StorageCapabilities = runCatching {
        val flags = info(uri).flags
        val file = uri.scheme == "file"
        val media = uri.authority == MediaStore.AUTHORITY && uri.path.orEmpty().matches(Regex("/[^/]+/(images|video|audio)/media/[0-9]+"))
        val write = file || resolver.persistedUriPermissions.any { it.isWritePermission && (it.uri == uri || it.uri.toString() == tree) }
        StorageCapabilities(true, write && flags and Docs.Document.FLAG_SUPPORTS_RENAME != 0,
            write && flags and Docs.Document.FLAG_SUPPORTS_DELETE != 0,
            write && flags and Docs.Document.FLAG_DIR_SUPPORTS_CREATE != 0, media)
    }.getOrDefault(StorageCapabilities(false, false, false, false))
    fun children(parent: Uri): List<StorageInfo> {
        if (parent.scheme == "file") {
            val folder = File(parent.path!!); require(managed(folder))
            return folder.listFiles()?.map { info(Uri.fromFile(it)) } ?: error("This folder cannot be read")
        }
        val listing = Docs.buildChildDocumentsUriUsingTree(parent, Docs.getDocumentId(parent))
        val result = mutableListOf<StorageInfo>()
        resolver.query(listing, arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME,
            Docs.Document.COLUMN_MIME_TYPE, Docs.Document.COLUMN_SIZE, Docs.Document.COLUMN_LAST_MODIFIED, Docs.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
            while (c.moveToNext()) result += StorageInfo(Docs.buildDocumentUriUsingTree(parent, c.getString(0)), c.getString(1) ?: "File",
                c.getString(2) ?: "application/octet-stream", if (c.isNull(3)) -1 else c.getLong(3), if (c.isNull(4)) 0 else c.getLong(4), if (c.isNull(5)) 0 else c.getInt(5))
        } ?: error("This folder cannot be read")
        return result
    }
    fun create(parent: Uri, tree: String, name: String, mime: String): Uri {
        check(capabilities(parent, tree).create) { "This destination is read only; allow write access or choose another folder" }
        val safe = FileNames.available(name, children(parent).map { it.name })
        if (parent.scheme == "file") {
            val file = File(File(parent.path!!), safe); check(managed(file))
            check(if (mime == Docs.Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()) { "Name conflict or storage full" }
            return Uri.fromFile(file)
        }
        return Docs.createDocument(resolver, parent, mime, safe) ?: error("Provider could not create the destination")
    }
    fun rename(uri: Uri, tree: String?, name: String): Uri {
        check(capabilities(uri, tree).rename) { "Renaming needs write access and provider support" }
        val safe = FileNames.validate(name)
        if (uri.scheme == "file") {
            val original = File(uri.path!!); val target = File(original.parentFile, safe)
            check(managed(target) && !target.exists() && original.renameTo(target)) { "Name conflict or rename failed" }
            return Uri.fromFile(target)
        }
        return Docs.renameDocument(resolver, uri, safe) ?: error("Provider could not rename this file")
    }
    fun delete(uri: Uri, tree: String?) {
        check(capabilities(uri, tree).delete) { "Deletion needs write access and provider support" }
        check(if (uri.scheme == "file") File(uri.path!!).delete() else Docs.deleteDocument(resolver, uri)) { "Provider did not delete the file" }
    }
    fun shareUri(uri: Uri): Uri = if (uri.scheme == "file") {
        check(managed(File(uri.path!!)))
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(uri.path!!))
    } else uri
}

fun mediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) buildList {
    add(Manifest.permission.READ_MEDIA_IMAGES); add(Manifest.permission.READ_MEDIA_VIDEO); add(Manifest.permission.READ_MEDIA_AUDIO)
    if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}.toTypedArray() else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun fullMediaAccess(context: Context, kind: dev.keeps.core.AssetKind): Boolean {
    if (Environment.isExternalStorageManager()) return true
    val permission = if (Build.VERSION.SDK_INT < 33) Manifest.permission.READ_EXTERNAL_STORAGE else when (kind) {
        dev.keeps.core.AssetKind.IMAGE -> Manifest.permission.READ_MEDIA_IMAGES
        dev.keeps.core.AssetKind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
        dev.keeps.core.AssetKind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
        else -> return false
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
fun mediaAccess(context: Context, kind: dev.keeps.core.AssetKind): Boolean = fullMediaAccess(context, kind) ||
        (Build.VERSION.SDK_INT >= 34 && kind in listOf(dev.keeps.core.AssetKind.IMAGE, dev.keeps.core.AssetKind.VIDEO) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
