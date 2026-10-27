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

    @Test fun persistentPreparationQueueRespectsScopeAndRecoversInterruptedRows() = runBlocking {
        val dao = database.library()
        val waiting = asset("waiting")
        val blocked = asset("excluded").copy(searchExcluded = true)
        val trashed = asset("trashed").copy(trashed = true)
        val inaccessible = asset("lost").copy(status = IndexStatus.NO_ACCESS)
        listOf(waiting, blocked, trashed, inaccessible).forEach { dao.insertAsset(it) }
        assertEquals(1, dao.queuePreparation(listOf("waiting", "excluded", "trashed", "lost")))
        assertEquals(0, dao.queuePreparation(listOf("waiting")))
        assertEquals("waiting", dao.nextPreparation()!!.id)
        dao.indexState(waiting, IndexStatus.INDEXING, "Running")
        assertTrue(dao.hasPendingPreparation()); assertNull(dao.nextPreparation())
        dao.recoverPreparation(); assertEquals("waiting", dao.nextPreparation()!!.id)
        dao.pausePreparation(); assertTrue(dao.hasPendingPreparation())
        assertEquals(IndexStatus.QUEUED, dao.asset("waiting")!!.status)
        assertTrue(dao.asset("excluded")!!.searchExcluded); assertTrue(dao.asset("trashed")!!.trashed)
        assertEquals(IndexStatus.NO_ACCESS, dao.asset("lost")!!.status)
    }

    @Test fun discoveryBatchPreservesUserStateAndCompatibleIndex() = runBlocking {
        val dao = database.library()
        val a = asset("batch").copy(addedAt = 100, modifiedAt = 200, favorite = true, lastOpenedAt = 300, documentKey = "fixture:batch")
        val excluded = asset("excluded").copy(searchExcluded = true)
        dao.insertAsset(a); dao.insertAsset(excluded)
        dao.replaceChunks(a.id, listOf(ChunkEntity("batch:vector", a.id, "model", vector = byteArrayOf(1, 2, 3, 4))), IndexStatus.AI_READY, "Ready")
        val folder = FolderEntity("folder", "tree", null, "Pinned", pinned = true)
        dao.putFolder(folder)
        dao.observeBatch(listOf(a.copy(addedAt = 999, favorite = false, capturedAt = 400), excluded.copy(searchExcluded = false)), listOf(folder.copy(pinned = false)))
        val saved = dao.asset(a.id)!!
        assertEquals(100, saved.addedAt); assertEquals(300, saved.lastOpenedAt)
        assertTrue(saved.favorite); assertEquals(400, saved.capturedAt)
        assertEquals(IndexStatus.AI_READY, saved.status); assertEquals(1, dao.assetChunks(a.id).size)
        assertTrue(dao.asset(excluded.id)!!.searchExcluded); assertTrue(dao.folder("folder")!!.pinned)
        // A scan snapshot cannot roll back a rename/move which completed after its query.
        val moved = saved.copy(uri = "content://fixture/moved", documentKey = null, name = "moved.txt")
        dao.updateAsset(moved)
        dao.observeBatch(listOf(a), emptyList())
        assertEquals(moved, dao.asset(a.id))
    }

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

    @Test fun exclusionKeepsFileAndFavoriteButClearsTextAndBlocksLateIndexCommit() = runBlocking {
        val dao = database.library()
        val original = asset("a").copy(favorite = true)
        dao.insertAsset(original)
        val chunks = listOf(ChunkEntity("a:text", "a", "lexical-v1", "private content"))
        dao.replaceChunks("a", chunks, IndexStatus.TEXT_READY, "Ready")
        dao.exclude("a", true)
        assertTrue(dao.asset("a")!!.favorite)
        assertTrue(dao.asset("a")!!.searchExcluded)
        assertTrue(dao.chunks("model-a").isEmpty())
        assertFalse(dao.commitIndex(original, chunks, IndexStatus.TEXT_READY, "Late result"))
        assertTrue(dao.chunks("model-a").isEmpty())
        dao.exclude("a", false)
        assertTrue(dao.commitIndex(original, chunks, IndexStatus.TEXT_READY, "Prepared"))
    }

    @Test fun bulkExclusionPreservesOriginalsAndDoesNotEraseAlreadyIncludedIndex() = runBlocking {
        val dao = database.library()
        val a = asset("bulk-a").copy(favorite = true)
        val b = asset("bulk-b")
        listOf(a, b).forEach { file ->
            dao.insertAsset(file)
            dao.replaceChunks(file.id, listOf(ChunkEntity("${file.id}:text", file.id, "lexical-v1", "prepared")), IndexStatus.TEXT_READY, "Ready")
        }
        dao.excludeMany(listOf(a.id, b.id), false)
        assertEquals(1, dao.assetChunks(a.id).size)
        dao.excludeMany(listOf(a.id, a.id, b.id, "missing"), true)
        assertTrue(dao.asset(a.id)!!.favorite)
        assertEquals(a.uri, dao.asset(a.id)!!.uri)
        assertTrue(dao.asset(a.id)!!.searchExcluded)
        assertTrue(dao.asset(b.id)!!.searchExcluded)
        assertTrue(dao.assetChunks(a.id).isEmpty())
        assertFalse(dao.commitIndex(a, listOf(ChunkEntity("late", a.id, "lexical-v1", "late")), IndexStatus.TEXT_READY, "Late"))
        dao.excludeMany(listOf(a.id, b.id), false)
        assertFalse(dao.asset(a.id)!!.searchExcluded)
        assertTrue(dao.assetChunks(a.id).isEmpty())
        assertEquals(2, dao.assets().size)
    }

    @Test fun documentCheckpointsRetainPriorPagesAndRejectExcludedOrChangedSources() = runBlocking {
        val dao = database.library()
        val source = asset("pdf-checkpoint").copy(kind = AssetKind.DOCUMENT, mimeType = "application/pdf")
        dao.insertAsset(source)
        val first = ChunkEntity("page:1", source.id, "model", pageNumber = 1, vector = byteArrayOf(1, 2, 3, 4))
        val second = first.copy(id = "page:2", pageNumber = 2)
        assertTrue(dao.checkpointDocument(source, first, "1/3 pages"))
        assertTrue(dao.checkpointDocument(source, second, "2/3 pages"))
        assertEquals(2, dao.assetChunks(source.id).size)
        dao.updateAsset(source.copy(modifiedAt = 2))
        assertFalse(dao.checkpointDocument(source, second.copy(id = "stale"), "stale"))
        val updated = dao.asset(source.id)!!
        dao.exclude(source.id, true)
        assertFalse(dao.checkpointDocument(updated, second, "excluded"))
        assertTrue(dao.assetChunks(source.id).isEmpty())
    }

    @Test fun sourceVersionChangeRejectsAnOldWorkerResult() = runBlocking {
        val dao = database.library()
        val original = asset("a").copy(modifiedAt = 100)
        dao.insertAsset(original)
        dao.updateAsset(original.copy(modifiedAt = 200))
        assertFalse(dao.commitIndex(original, listOf(ChunkEntity("a:old", "a", "lexical-v1", "old")), IndexStatus.TEXT_READY, "Stale"))
        assertTrue(dao.chunks("model-a").isEmpty())
    }

    @Test fun videoCheckpointCannotRestoreExcludedOrChangedContent() = runBlocking {
        val dao = database.library()
        val video = asset("v").copy(kind = AssetKind.VIDEO, modifiedAt = 100)
        dao.insertAsset(video)
        val window = ChunkEntity("v:window", "v", "model", startMs = 0, endMs = 8000,
            vector = byteArrayOf(1, 2, 3, 4), recipe = dev.keeps.core.VideoWindows.RECIPE, frameCount = 4)
        assertTrue(dao.checkpointMedia(video, window, "First window"))
        dao.exclude(video.id, true)
        assertFalse(dao.checkpointMedia(video, window, "Late window"))
        assertFalse(dao.indexState(video, IndexStatus.FAILED, "Late failure"))
        assertTrue(dao.assetChunks(video.id).isEmpty())
        dao.exclude(video.id, false)
        dao.updateAsset(dao.asset(video.id)!!.copy(modifiedAt = 200))
        assertFalse(dao.checkpointMedia(video, window, "Stale window"))
    }

    @Test fun audioCheckpointChecksRecipeAndCannotRestoreExcludedContent() = runBlocking {
        val dao = database.library()
        val audio = asset("audio").copy(kind = AssetKind.AUDIO, modifiedAt = 100)
        dao.insertAsset(audio)
        val interval = ChunkEntity("audio:window", audio.id, "model", startMs = 0, endMs = 8000,
            vector = byteArrayOf(1, 2, 3, 4), recipe = dev.keeps.core.AudioWindows.RECIPE, hasAudio = true)
        assertTrue(dao.checkpointMedia(audio, interval, "First audio interval"))
        try {
            dao.checkpointMedia(audio, interval.copy(recipe = dev.keeps.core.VideoWindows.RECIPE), "Wrong recipe")
            fail("Audio must not accept a video recipe")
        } catch (_: IllegalArgumentException) { }
        dao.exclude(audio.id, true)
        assertFalse(dao.checkpointMedia(audio, interval, "Late audio interval"))
        assertTrue(dao.assetChunks(audio.id).isEmpty())
    }
}
