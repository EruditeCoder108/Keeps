package dev.keeps.library.catalog

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.IndexStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class MigrationTest {
    @Test fun upgradeRetainsOriginalUrisFavoritesAndSearchChunks() = runBlocking { upgrade(1) }
    @Test fun upgradeV4RetainsDataAndAddsOperationsAndMoments() = runBlocking { upgrade(4) }
    @Test fun upgradeV5RetainsDataAndAddsFolderRulesAndManualQueueDefault() = runBlocking { upgrade(5) }
    private suspend fun upgrade(version: Int) {
        val name = "migration-preserves-v$version"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.deleteDatabase(name)
        // Recreate the exact exported v1 schema with platform JSON/SQLite. Room's test helper
        // has a serialization ABI conflict with the runtime's resolved dependency in this stack.
        val schema = org.json.JSONObject(instrumentation.context.assets.open("dev.keeps.library.catalog.LibraryDatabase/$version.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.version = version
            with(old) {
            execSQL("INSERT INTO assets (id, uri, name, mimeType, kind, size, addedAt, favorite, status, detail) VALUES ('kept', 'content://fixture/original', 'नोट.txt', 'text/plain', 'TEXT', 20, 123, 1, 'TEXT_READY', 'Prepared')")
            execSQL("INSERT INTO chunks (id, assetId, namespace, text) VALUES ('kept:text', 'kept', 'lexical-v1', 'retain this text')")
            }
        }
        // Opening through production Room runs both migrations and validates every resulting table.
        val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
            .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3, LibraryDatabase.MIGRATION_3_4, LibraryDatabase.MIGRATION_4_5, LibraryDatabase.MIGRATION_5_6).build()
        try {
            val asset = db.library().asset("kept")!!
            assertEquals("content://fixture/original", asset.uri)
            assertEquals("नोट.txt", asset.name)
            assertTrue(asset.favorite)
            assertFalse(asset.searchExcluded)
            assertFalse(asset.folderSearchExcluded)
            assertFalse(asset.automaticPreparation)
            assertTrue(db.library().excludedFolders().isEmpty())
            assertEquals(IndexStatus.TEXT_READY, asset.status)
            assertEquals("retain this text", db.library().chunks("new-model").single().text)
            assertEquals("legacy-v1", db.library().chunks("new-model").single().recipe)
            assertEquals(0L, asset.lastOpenedAt)
            assertFalse(asset.trashed)
            db.library().saveMoment(SavedMomentEntity("saved", asset.id, 1_000, 3_000, "Saved"))
            db.library().putOperation(OperationEntity("op", "COPY", asset.id, asset.uri, asset.name, asset.size, 0, null, "tree", "parent", asset.name))
            assertEquals("QUEUED", db.library().operation("op")!!.state)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
