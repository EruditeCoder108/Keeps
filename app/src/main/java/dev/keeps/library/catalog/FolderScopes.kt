package dev.keeps.library.catalog

import android.net.Uri
import android.provider.DocumentsContract

/** Identity-based rules; arbitrary provider URIs are never compared by string prefix. */
object FolderScopes {
    fun key(uri: String): String? {
        if (uri.startsWith("media-album:")) {
            val parts = uri.removePrefix("media-album:").split(':', limit = 2)
            if (parts.size != 2) return null
            return storage(parts[0], parts[1])
        }
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            val path = parsed.path ?: return null
            val primary = android.os.Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
            if (path == primary || path.startsWith("$primary/")) return storage("primary", path.removePrefix(primary))
            if (path.startsWith("/storage/")) {
                val parts = path.removePrefix("/storage/").split('/', limit = 2)
                return storage(parts[0], parts.getOrElse(1) { "" })
            }
        }
        if (parsed.authority == "com.android.externalstorage.documents") {
            val id = runCatching { DocumentsContract.getDocumentId(parsed) }.getOrNull()
                ?: runCatching { DocumentsContract.getTreeDocumentId(parsed) }.getOrNull() ?: return null
            val parts = id.split(':', limit = 2)
            if (parts.size == 2) return storage(parts[0], parts[1])
        }
        return null
    }
    private fun storage(volume: String, path: String) = "storage:${if (volume == "external_primary") "primary" else volume.lowercase()}:${path.trim('/')}"
    private fun assetKey(asset: AssetEntity): String? {
        val prefix = "com.android.externalstorage.documents:"
        val id = asset.documentKey?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
        if (id != null) {
            val parts = id.split(':', limit = 2)
            if (parts.size == 2) return storage(parts[0], parts[1])
        }
        return key(asset.uri)
    }
    fun excludes(asset: AssetEntity, rules: List<ExcludedFolderEntity>, folders: Map<String, FolderEntity>): Boolean {
        val path = assetKey(asset)
        val ancestors = mutableSetOf<String>()
        var parent = asset.parentUri
        while (parent != null && ancestors.add(parent)) parent = folders[parent]?.parentUri
        return rules.any { rule -> rule.uri in ancestors || rule.uri == asset.treeUri ||
            rule.pathKey?.let { root -> path != null && (path == root || path.startsWith(if (root.endsWith(':')) root else "$root/")) } == true }
    }
}
