package dev.keeps.library.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dev.keeps.core.AssetKind
import dev.keeps.library.catalog.AssetEntity
import dev.keeps.library.catalog.LibraryDao
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportResult(val added: Int, val restored: Int)

class DocumentImporter(private val context: Context, private val dao: LibraryDao) {
    suspend fun import(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var restored = 0
        for (uri in uris) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val existing = dao.findByUri(uri.toString())
            if (existing != null) {
                if (existing.status == dev.keeps.core.IndexStatus.NO_ACCESS) {
                    dao.status(existing.id, dev.keeps.core.IndexStatus.IMPORTED, "Access restored; index again")
                    restored++
                }
                continue
            }
            var name = "Selected file"
            var size = -1L
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                }
            } finally {
                cursor?.close()
            }
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val kind = when {
                mime.startsWith("image/") -> AssetKind.IMAGE
                mime.startsWith("video/") -> AssetKind.VIDEO
                mime.startsWith("audio/") -> AssetKind.AUDIO
                mime == "application/pdf" -> AssetKind.DOCUMENT
                mime.startsWith("text/") || name.endsWith(".md", true) -> AssetKind.TEXT
                else -> AssetKind.OTHER
            }
            dao.insertAsset(AssetEntity(UUID.randomUUID().toString(), uri.toString(), name, mime, kind, size))
            imported++
        }
        ImportResult(imported, restored)
    }
}
