package dev.keeps.library.catalog

import android.os.SystemClock
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.library.KeepsApplication
import dev.keeps.library.sources.MediaLibrary
import dev.keeps.library.sources.ReadAccessSnapshot
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Authorized device metadata only. Isolated catalog; no file contents, mutations or AI work. */
class DeviceDiscoveryTest {
    @Test fun coldAndWarmDiscoveryOfPermittedRealLibrary(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val access = ReadAccessSnapshot(context)
        check(access.hasMedia) { "Allow device media first" }
        val production = (context.applicationContext as KeepsApplication).graph
        val original = production.dao.assets().associateBy { it.id }
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val library = MediaLibrary(context, db.library())
            val start = SystemClock.elapsedRealtime()
            val coldCount = library.scan()
            val coldMs = SystemClock.elapsedRealtime() - start
            val first = db.library().assets()
            assertTrue("Permitted real metadata discovered", coldCount > 0)
            val warmStart = SystemClock.elapsedRealtime()
            assertEquals(coldCount, library.scan())
            val warmMs = SystemClock.elapsedRealtime() - warmStart
            val second = db.library().assets()
            assertEquals(first.map { it.id }.toSet(), second.map { it.id }.toSet())
            assertEquals(first.map { it.addedAt }, second.map { it.addedAt })
            assertFalse(library.progress.value.running)
            val after = production.dao.assets().associateBy { it.id }
            original.forEach { (id, before) ->
                after[id]?.let { current ->
                    assertEquals(before.favorite, current.favorite)
                    assertEquals(before.searchExcluded, current.searchExcluded)
                }
            }
            val report = JSONObject().put("allFilesGranted", access.allFiles).put("coldCount", coldCount)
                .put("uniqueAssets", first.size).put("coldMs", coldMs).put("warmMs", warmMs)
                .put("batchSize", 128).put("imageCount", first.count { it.kind == dev.keeps.core.AssetKind.IMAGE })
                .put("videoCount", first.count { it.kind == dev.keeps.core.AssetKind.VIDEO })
                .put("audioCount", first.count { it.kind == dev.keeps.core.AssetKind.AUDIO })
                .put("nonMediaCount", first.count { it.kind !in listOf(dev.keeps.core.AssetKind.IMAGE, dev.keeps.core.AssetKind.VIDEO, dev.keeps.core.AssetKind.AUDIO) })
                .put("originalMutations", 0).put("aiQueued", 0)
            File(context.filesDir, "device-discovery-probe.json").writeText(report.toString(2))
        } finally { db.close() }
    }
}
