package dev.keeps.library.catalog

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.AssetKind
import dev.keeps.library.sources.MediaLibrary
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Temporary shell identity; SQL prefix restricts every query to newly generated fixtures. */
class DeviceMediaLibraryTest {
    @Test fun realMediaDiscoveryAlbumsDatesDurationAndIdentity(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefix = "fixture-keeps-media-${UUID.randomUUID()}"
        val created = mutableListOf<Uri>()
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val resolver = context.contentResolver
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO")
        try {
            for (i in 0..2) {
                val video = i == 2
                val values = ContentValues().apply {
                    put("_display_name", "$prefix-$i.${if (video) "mp4" else "png"}")
                    put("mime_type", if (video) "video/mp4" else "image/png")
                    put("relative_path", if (video) "Movies/KeepsMediaTest/" else "Pictures/KeepsMediaTest/")
                    put("datetaken", 1_790_000_000_000L + i * 1000)
                    put("is_pending", 1)
                    if (video) put("duration", 18_000)
                }
                val collection = if (video) MediaStore.Video.Media.getContentUri("external_primary") else MediaStore.Images.Media.getContentUri("external_primary")
                val uri = resolver.insert(collection, values)!!; created += uri
                resolver.openOutputStream(uri)!!.use { output ->
                    if (video) instrumentation.context.assets.open("video/fixture-moments.mp4").use { it.copyTo(output) }
                    else Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { it.eraseColor(if (i == 0) android.graphics.Color.RED else android.graphics.Color.BLUE) }.let { bitmap -> try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) } finally { bitmap.recycle() } }
                }
                resolver.update(uri, ContentValues().apply { put("is_pending", 0) }, null, null)
            }
            val library = MediaLibrary(context, db.library())
            assertEquals(3, library.scanFixtureMedia(prefix))
            val assets = db.library().assets()
            assertEquals(3, assets.size)
            assertEquals(2, assets.count { it.kind == AssetKind.IMAGE })
            assertEquals(setOf("KeepsMediaTest"), assets.map { it.folderName }.toSet())
            assertTrue("Published byte sizes: ${assets.map { it.size }}", assets.all { it.size > 0 })
            assertTrue("Real modified-date fallback", assets.all { it.modifiedAt > 0 })
            assertTrue(assets.all { it.parentUri!!.startsWith("media-album:") })
            assertTrue(assets.single { it.kind == AssetKind.VIDEO }.durationMs > 0)
            db.library().toggleFavorite(assets.first().id)
            assertEquals(3, library.scanFixtureMedia(prefix))
            val refreshed = db.library().assets()
            assertEquals(assets.map { it.id }.toSet(), refreshed.map { it.id }.toSet())
            assertTrue(refreshed.single { it.id == assets.first().id }.favorite)
            assertEquals(2, db.library().folders("mediastore").size)
        } finally {
            created.forEach { resolver.delete(it, null, null) }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            db.close()
        }
    }
}
