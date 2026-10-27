package dev.keeps.library.catalog

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FolderScopeTest {
    private fun withDatabase(block: suspend (LibraryDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LibraryDatabase::class.java).build()
        try { block(db.library()) } finally { db.close() }
    }
    private fun item(id: String, path: String) = AssetEntity(id, "content://fixture/$id", "$id.jpg", "image/jpeg", AssetKind.IMAGE, 20,
        documentKey = "com.android.externalstorage.documents:primary:$path", parentUri = "media-album:external_primary:${path.substringBeforeLast('/')}/")

    @Test fun rulesCoverDescendantsNewArrivalsAliasesAndRejectSiblingPrefixes() = withDatabase { dao ->
        val folder = FolderEntity("media-album:external_primary:Pictures/Private/", "mediastore", null, "Private")
        dao.putFolder(folder)
        val a = item("a", "Pictures/Private/a.jpg").copy(favorite = true)
        val sibling = item("sibling", "Pictures/PrivateOther/b.jpg")
        dao.observeBatch(listOf(a, sibling), emptyList())
        dao.replaceChunks(a.id, listOf(ChunkEntity("a:vector", a.id, "model", vector = Vectors.encode(floatArrayOf(1f, 0f)))), IndexStatus.AI_READY, "Ready")
        dao.excludeFolder(folder, true)
        assertTrue(dao.asset(a.id)!!.folderSearchExcluded)
        assertTrue(dao.asset(a.id)!!.favorite)
        assertEquals(a.uri, dao.asset(a.id)!!.uri)
        assertFalse(dao.asset(sibling.id)!!.folderSearchExcluded)
        assertTrue(dao.assetChunks(a.id).isEmpty())
        assertFalse(dao.commitIndex(a, listOf(ChunkEntity("late", a.id, "model")), IndexStatus.AI_READY, "Late"))
        val nested = item("nested", "Pictures/Private/Sub/new.jpg")
        dao.observeBatch(listOf(nested), emptyList())
        assertTrue(dao.asset(nested.id)!!.folderSearchExcluded)
        assertEquals(0, dao.queuePreparation(listOf(nested.id)))
        dao.exclude(a.id, true)
        val alias = folder.copy(uri = "content://com.android.externalstorage.documents/tree/primary%3APictures%2FPrivate/document/primary%3APictures%2FPrivate")
        dao.excludeFolder(alias, false)
        assertTrue(dao.excludedFolders().isEmpty())
        assertFalse(dao.asset(a.id)!!.folderSearchExcluded)
        assertTrue(dao.asset(a.id)!!.searchExcluded)
        assertFalse(dao.asset(nested.id)!!.contentSearchExcluded)
    }

    @Test fun opaqueProvidersUseAncestorIdentityAndMovesReapplyRules() = withDatabase { dao ->
        val parent = FolderEntity("content://fixture/parent", "content://fixture/tree", null, "Parent")
        val child = FolderEntity("content://fixture/child", parent.treeUri, parent.uri, "Child")
        dao.putFolder(parent); dao.putFolder(child)
        dao.excludeFolder(parent, true)
        val a = item("opaque", "Elsewhere/a.jpg").copy(documentKey = null, parentUri = child.uri)
        dao.insertObservedAsset(a)
        assertTrue(dao.asset(a.id)!!.folderSearchExcluded)
        val moved = dao.asset(a.id)!!.copy(parentUri = "content://fixture/outside", uri = "content://fixture/moved")
        dao.updateAsset(moved)
        assertFalse(dao.asset(a.id)!!.folderSearchExcluded)
        dao.updateAsset(moved.copy(parentUri = child.uri))
        assertTrue(dao.asset(a.id)!!.folderSearchExcluded)
    }

    @Test fun manualAndAutomaticQueuesStaySeparateAndPromotionIsScoped() = withDatabase { dao ->
        val a = item("automatic", "Pictures/a.jpg")
        val b = item("manual", "Pictures/b.jpg")
        dao.insertAsset(a); dao.insertAsset(b)
        assertEquals(1, dao.queuePreparation(listOf(a.id), true))
        assertEquals(1, dao.queuePreparation(listOf(b.id), false))
        assertEquals(a.id, dao.nextPreparation(true)!!.id)
        assertEquals(b.id, dao.nextPreparation(false)!!.id)
        assertEquals(1, dao.queuePreparation(listOf(a.id), false))
        assertNull(dao.nextPreparation(true))
        dao.stopAutomaticPreparation()
        assertEquals(IndexStatus.QUEUED, dao.asset(a.id)!!.status)
        assertEquals(IndexStatus.QUEUED, dao.asset(b.id)!!.status)
    }
}
