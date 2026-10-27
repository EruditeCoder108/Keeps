package dev.keeps.library.catalog

import android.net.Uri
import android.provider.DocumentsContract as Docs
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.IndexStatus
import dev.keeps.library.sources.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Mutates only newly generated disposable files inside the chosen fixture tree. */
class DeviceFileOperationsTest {
    @Test fun verifiedCopyMoveTrashRestoreAndConflictKeepOriginalBytes(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val grant = context.contentResolver.persistedUriPermissions.single { it.uri.toString().contains("KeepsBrowseTest") }
        check(grant.isWritePermission) { "Select KeepsBrowseTest again to grant writes" }
        val tree = grant.uri.toString()
        val storage = StorageAccess(context)
        val root = Docs.buildDocumentUriUsingTree(grant.uri, Docs.getTreeDocumentId(grant.uri))
        val own = storage.create(root, tree, "KeepsOpsTest-${UUID.randomUUID()}", Docs.Document.MIME_TYPE_DIR)
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val dao = db.library(); val importer = DocumentImporter(context, dao)
        val operations = FileOperations(context, dao, storage, importer)
        val owned = mutableSetOf<Uri>()
        suspend fun transfer(asset: AssetEntity, destination: Uri, type: String, name: String = asset.name): OperationEntity {
            val info = storage.info(Uri.parse(asset.uri))
            val op = OperationEntity(UUID.randomUUID().toString(), type, asset.id, asset.uri, info.name, info.size, info.modified,
                tree, tree, destination.toString(), name)
            dao.putOperation(op)
            assertTrue("$type: ${dao.operation(op.id)?.detail}", operations.execute(op.id))
            return dao.operation(op.id)!!.also { owned += Uri.parse(it.resultUri) }
        }
        fun bytes(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        try {
            val destination = storage.create(own, tree, "Destination", Docs.Document.MIME_TYPE_DIR)
            operations.createFolder(FolderEntity(own.toString(), tree, root.toString(), "Fixture"), "Created")
            val createdFolder = dao.folders(tree).single { it.name == "Created" }
            dao.pinFolder(createdFolder.uri); dao.putFolder(createdFolder)
            assertTrue(dao.folder(createdFolder.uri)!!.pinned)
            val source = storage.create(own, tree, "नोट.txt", "text/plain").also { owned += it }
            val expected = ByteArray(1_048_589) { (it * 31).toByte() }
            context.contentResolver.openOutputStream(source, "wt")!!.use { it.write(expected) }
            val asset = importer.catalogObserved(storage.info(source), tree, own.toString(), "Operations fixture")
            dao.toggleFavorite(asset.id)
            dao.saveMoment(SavedMomentEntity("saved", asset.id, 0, 2_000, "Persistent bookmark"))
            val first = transfer(asset, destination, "COPY")
            val second = transfer(asset, destination, "COPY")
            assertNotEquals(first.resultUri, second.resultUri)
            assertEquals("नोट (2).txt", storage.info(Uri.parse(second.resultUri)).name)
            assertArrayEquals(expected, bytes(source))
            assertArrayEquals(expected, bytes(Uri.parse(first.resultUri)))
            operations.rename(dao.asset(asset.id)!!, "renamed.txt")
            val renamed = dao.asset(asset.id)!!; owned += Uri.parse(renamed.uri)
            assertTrue(renamed.favorite)
            transfer(renamed, destination, "MOVE")
            val moved = dao.asset(asset.id)!!
            assertTrue(moved.favorite); assertEquals(asset.id, moved.id)
            assertTrue(runCatching { storage.info(Uri.parse(renamed.uri)) }.isFailure)
            assertArrayEquals(expected, bytes(Uri.parse(moved.uri)))
            val trash = storage.create(own, tree, "Trash", Docs.Document.MIME_TYPE_DIR)
            dao.putTrash(TrashEntity(asset.id, tree, destination.toString(), moved.name))
            transfer(moved, trash, "TRASH")
            assertTrue(dao.asset(asset.id)!!.trashed)
            assertFalse(dao.commitIndex(moved, emptyList(), IndexStatus.AI_READY, "late result"))
            val conflict = storage.create(destination, tree, moved.name, "text/plain").also { owned += it }
            context.contentResolver.openOutputStream(conflict, "wt")!!.use { it.write("retain conflicting file".toByteArray()) }
            transfer(dao.asset(asset.id)!!, destination, "RESTORE", moved.name)
            val restored = dao.asset(asset.id)!!
            assertFalse(restored.trashed); assertTrue(restored.favorite)
            assertEquals("renamed (2).txt", restored.name)
            assertArrayEquals(expected, bytes(Uri.parse(restored.uri)))
            assertEquals("retain conflicting file", bytes(conflict).toString(Charsets.UTF_8))
            assertNull(dao.trashEntry(asset.id))
            val moments = db.openHelper.readableDatabase.query("SELECT assetId FROM moments WHERE id='saved'")
            moments.use { assertTrue(it.moveToFirst()); assertEquals(asset.id, it.getString(0)) }
            transfer(restored, trash, "TRASH")
            operations.deletePermanently(dao.asset(asset.id)!!)
            assertNull(dao.asset(asset.id))
            assertEquals("retain conflicting file", bytes(conflict).toString(Charsets.UTF_8))
            // Only our generated files are deleted; the pre-existing fixtures are untouched.
            owned.forEach { runCatching { storage.delete(it, tree) } }
            storage.delete(destination, tree); storage.delete(trash, tree); storage.delete(Uri.parse(createdFolder.uri), tree)
        } finally {
            owned.forEach { runCatching { storage.delete(it, tree) } }
            db.close()
            if (storage.children(own).isEmpty()) storage.delete(own, tree)
        }
    }

    @Test fun interruptionAndSourceChangeKeepSourceAndRecoverVerifiedDestination(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val grant = context.contentResolver.persistedUriPermissions.single { it.uri.toString().contains("KeepsBrowseTest") }
        check(grant.isWritePermission)
        val storage = StorageAccess(context); val tree = grant.uri.toString()
        val root = Docs.buildDocumentUriUsingTree(grant.uri, Docs.getTreeDocumentId(grant.uri))
        val own = storage.create(root, tree, "KeepsRecoveryTest-${UUID.randomUUID()}", Docs.Document.MIME_TYPE_DIR)
        val name = "operation-recovery-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        val source = storage.create(own, tree, "source.txt", "text/plain")
        val target = storage.create(own, tree, "target.txt", "text/plain")
        val payload = "verified original".toByteArray()
        fun write(uri: Uri, data: ByteArray) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(data) } }
        write(source, payload); write(target, payload)
        try {
            val importer = DocumentImporter(context, db.library())
            val asset = importer.catalogObserved(storage.info(source), tree, own.toString(), "Recovery fixture")
            val info = storage.info(source)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            val op = OperationEntity(UUID.randomUUID().toString(), "MOVE", asset.id, source.toString(), info.name, info.size, info.modified,
                tree, tree, own.toString(), "target.txt", resultUri = target.toString(), bytes = payload.size.toLong(),
                state = "SOURCE_DELETE_PENDING", verifiedHash = hash)
            db.library().putOperation(op)
            db.close() // Reopen the persisted journal as after process loss.
            db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
            val dao = db.library(); val operations = FileOperations(context, dao, storage, DocumentImporter(context, dao))
            assertFalse(operations.execute(op.id))
            assertEquals("REVIEW", dao.operation(op.id)!!.state)
            assertArrayEquals(payload, context.contentResolver.openInputStream(source)!!.use { it.readBytes() })
            write(source, "user changed the original".toByteArray())
            dao.putOperation(dao.operation(op.id)!!.copy(state = "RESUME_DELETE"))
            assertFalse(operations.execute(op.id))
            assertEquals("user changed the original", context.contentResolver.openInputStream(source)!!.use { it.readBytes().toString(Charsets.UTF_8) })
            operations.recoverDestination(op.id)
            assertEquals(target.toString(), dao.asset(asset.id)!!.uri)
            assertEquals("DONE", dao.operation(op.id)!!.state)
            assertEquals("user changed the original", context.contentResolver.openInputStream(source)!!.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally {
            db.close(); context.deleteDatabase(name)
            storage.delete(source, tree); storage.delete(target, tree); storage.delete(own, tree)
        }
    }
}
