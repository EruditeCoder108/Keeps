package dev.keeps.library.catalog

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LibraryDatabaseTest {
    private lateinit var database: LibraryDatabase
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LibraryDatabase::class.java).build()
    }
    @After fun tearDown() { database.close() }
    private fun asset(id: String) = AssetEntity(id, "content://fixture/$id", "$id.txt", "text/plain", AssetKind.TEXT, 20)

    @Test fun removingAnAssetCascadesItsSearchData() = runBlocking {
        val dao = database.library()
        dao.insertAsset(asset("a"))
        dao.replaceChunks("a", listOf(ChunkEntity("a:1", "a", "lexical-v1", "private note")), IndexStatus.TEXT_READY, "Indexed")
        dao.remove("a")
        assertTrue(dao.chunks("model-a").isEmpty())
    }

    @Test fun modelChangePreservesLexicalSearchAndRemovesIncompatibleVectors() = runBlocking {
        val dao = database.library()
        dao.insertAsset(asset("a"))
        dao.replaceChunks("a", listOf(
            ChunkEntity("a:text", "a", "lexical-v1", "invoice"),
            ChunkEntity("a:vector", "a", "model-a", vector = byteArrayOf(1, 2, 3, 4)),
        ), IndexStatus.AI_READY, "Indexed")
        assertEquals(1, dao.chunks("model-b").size)
        dao.invalidateEmbeddings()
        assertEquals("invoice", dao.chunks("model-a").single().text)
        assertEquals(IndexStatus.IMPORTED, dao.asset("a")!!.status)
    }

    @Test fun duplicateSourceCannotCreateTwoCatalogItems() = runBlocking {
        val dao = database.library()
        dao.insertAsset(asset("a"))
        dao.insertAsset(asset("a").copy(id = "b"))
        assertEquals(1, dao.assets().size)
    }
}
