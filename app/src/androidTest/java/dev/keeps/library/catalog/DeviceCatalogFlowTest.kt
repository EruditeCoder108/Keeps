package dev.keeps.library.catalog

import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.library.KeepsApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Uses only the explicitly selected six generated fixtures. Original files are never mutated. */
class DeviceCatalogFlowTest {
    @Test fun folderDiscoveryAndExclusionLeaveOrdinarySearchUsable(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsCatalogTests") == "true")
        val graph = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KeepsApplication).graph
        val assets = graph.dao.assets()
        val names = setOf("fixture-01.png", "fixture-02.png", "fixture-note.txt", "fixture-pages.pdf", "fixture-tone.wav", "fixture-clip.mp4")
        assumeTrue("Requires only the six chosen-folder fixtures", assets.size == 6 && assets.map { it.name }.toSet() == names)
        val tree = assets.mapNotNull { it.treeUri }.toSet().single()
        val scan = graph.importer.scan(tree)
        assertEquals(6, scan.files)
        assertFalse(scan.limited)
        assertEquals(0, scan.failedFolders)
        val pictures = graph.dao.folders(tree).single { it.name == "Pictures" }
        graph.importer.openFolder(pictures)
        val refreshed = graph.dao.assets()
        assertEquals(assets.toSet(), refreshed.toSet())
        assertEquals(6, assets.mapNotNull { it.documentKey }.toSet().size)
        assertEquals(setOf("Pictures", "Documents", "Media"), assets.map { it.folderName }.toSet())
        assertEquals("fixture-note.txt", graph.repository.search("fixture-note.txt", content = false).hits.first().asset.name)
        val note = assets.single { it.name == "fixture-note.txt" }
        val namespace = graph.embeddings.namespace
        val saved = graph.dao.chunks(namespace).filter { it.assetId == note.id }
        try {
            graph.repository.exclude(note, true)
            assertTrue(graph.dao.asset(note.id)!!.searchExcluded)
            assertEquals(note.favorite, graph.dao.asset(note.id)!!.favorite)
            assertTrue(graph.dao.chunks(namespace).none { it.assetId == note.id })
            assertTrue(graph.repository.search("fixture-note.txt", content = false).hits.any { it.asset.id == note.id })
            assertTrue(graph.repository.search("", content = false).hits.any { it.asset.id == note.id })
            assertFalse(graph.repository.search("green helmet").hits.any { it.asset.id == note.id })
        } finally {
            graph.dao.exclude(note.id, note.searchExcluded)
            graph.dao.replaceChunks(note.id, saved, note.status, note.detail)
        }
    }
}
