package dev.keeps.library.sources

import android.content.Context
import android.net.Uri
import android.os.Environment
import dev.keeps.core.AssetKind
import dev.keeps.library.catalog.AssetEntity

/** One permission/grant lookup per pass, never per item during Compose rendering. */
class ReadAccessSnapshot(private val context: Context) {
    val allFiles = Environment.isExternalStorageManager()
    private val grants = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
    private val full = listOf(AssetKind.IMAGE, AssetKind.VIDEO, AssetKind.AUDIO).filter { fullMediaAccess(context, it) }.toSet()
    private val selected = listOf(AssetKind.IMAGE, AssetKind.VIDEO).filter { mediaAccess(context, it) }.toSet()
    val hasMedia = allFiles || full.isNotEmpty() || selected.isNotEmpty()
    val fullMedia = allFiles || full.size == 3
    val roots = if (allFiles) StorageAccess(context).roots().map { it.canonicalFile } else emptyList()
    fun fileRoot(file: java.io.File): java.io.File? = roots.firstOrNull { file.canonicalPath.startsWith(it.path + "/") }
    fun canReadFile(file: java.io.File) = allFiles && file.canRead() && fileRoot(file) != null

    /** Selected-media validation and direct-file checks belong on an IO dispatcher. */
    fun canRead(asset: AssetEntity): Boolean {
        if (asset.uri in grants || asset.treeUri in grants) return true
        val uri = Uri.parse(asset.uri)
        if (uri.scheme == "file") return canReadFile(java.io.File(requireNotNull(uri.path)))
        if (uri.authority != android.provider.MediaStore.AUTHORITY) return false
        if (allFiles || asset.kind in full) return true
        if (asset.kind !in selected) return false
        return runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
    }
}
