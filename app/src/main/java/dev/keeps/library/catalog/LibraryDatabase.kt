package dev.keeps.library.catalog

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import dev.keeps.core.VideoWindows
import dev.keeps.core.AudioWindows
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "assets", indices = [Index(value = ["uri"], unique = true), Index(value = ["documentKey"], unique = true)])
data class AssetEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val kind: AssetKind,
    val size: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val status: IndexStatus = IndexStatus.IMPORTED,
    val detail: String = "Ready to index",
    val documentKey: String? = null,
    val treeUri: String? = null,
    val parentUri: String? = null,
    val folderName: String? = null,
    @ColumnInfo(defaultValue = "0") val modifiedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val providerFlags: Int = 0,
    @ColumnInfo(defaultValue = "0") val searchExcluded: Boolean = false,
    @ColumnInfo(defaultValue = "0") val folderSearchExcluded: Boolean = false,
    @ColumnInfo(defaultValue = "0") val automaticPreparation: Boolean = false,
    @ColumnInfo(defaultValue = "0") val capturedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastOpenedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val trashed: Boolean = false,
)

val AssetEntity.contentSearchExcluded get() = searchExcluded || folderSearchExcluded

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(@PrimaryKey val uri: String, val name: String, val pathKey: String? = null)

@Entity(tableName = "locations")
data class LocationEntity(@PrimaryKey val uri: String, val name: String, val detail: String = "Chosen folder · read access")

@Entity(tableName = "folders", indices = [Index("parentUri")])
data class FolderEntity(@PrimaryKey val uri: String, val treeUri: String, val parentUri: String?, val name: String,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false)

@Entity(tableName = "operations")
data class OperationEntity(@PrimaryKey val id: String, val type: String, val assetId: String,
    val sourceUri: String, val sourceName: String, val sourceSize: Long, val sourceModified: Long,
    val sourceTree: String?,
    val destinationTree: String, val destinationParent: String, val requestedName: String,
    val resultUri: String? = null, val bytes: Long = 0, val state: String = "QUEUED",
    val detail: String = "Waiting", val verifiedHash: String? = null, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "trash")
data class TrashEntity(@PrimaryKey val assetId: String, val originalTree: String, val originalParent: String,
    val originalName: String, val trashedAt: Long = System.currentTimeMillis())

@Entity(tableName = "moments", foreignKeys = [ForeignKey(entity = AssetEntity::class,
    parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)], indices = [Index("assetId")])
data class SavedMomentEntity(@PrimaryKey val id: String, val assetId: String, val startMs: Long, val endMs: Long,
    val label: String, val savedAt: Long = System.currentTimeMillis())

@Entity(tableName = "chunks", foreignKeys = [ForeignKey(
    entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("assetId"), Index("namespace")])
data class ChunkEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val namespace: String,
    val text: String = "",
    val pageNumber: Int? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val vector: ByteArray? = null,
    @ColumnInfo(defaultValue = "'legacy-v1'") val recipe: String = "legacy-v1",
    @ColumnInfo(defaultValue = "0") val frameCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val hasAudio: Boolean = false,
    @ColumnInfo(defaultValue = "0") val audioUnavailable: Boolean = false,
    val visualVector: ByteArray? = null,
)

class CatalogConverters {
    @TypeConverter fun kind(value: String): AssetKind = AssetKind.valueOf(value)
    @TypeConverter fun kind(value: AssetKind): String = value.name
    @TypeConverter fun status(value: String): IndexStatus = IndexStatus.valueOf(value)
    @TypeConverter fun status(value: IndexStatus): String = value.name
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM assets ORDER BY addedAt DESC") fun observeAssets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM assets ORDER BY addedAt DESC") suspend fun assets(): List<AssetEntity>
    @Query("SELECT * FROM assets WHERE id = :id") suspend fun asset(id: String): AssetEntity?
    @Query("SELECT * FROM assets WHERE status = 'QUEUED' AND searchExcluded = 0 AND folderSearchExcluded = 0 AND trashed = 0 AND automaticPreparation = :automatic ORDER BY CASE kind WHEN 'VIDEO' THEN 1 WHEN 'AUDIO' THEN 1 ELSE 0 END, addedAt, id LIMIT 1")
    suspend fun nextPreparation(automatic: Boolean = false): AssetEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM assets WHERE status IN ('QUEUED','INDEXING') AND searchExcluded = 0 AND folderSearchExcluded = 0 AND trashed = 0)")
    suspend fun hasPendingPreparation(): Boolean
    @Query("UPDATE assets SET status = 'QUEUED', automaticPreparation = :automatic, detail = 'Waiting for content search preparation' WHERE id IN (:ids) AND searchExcluded = 0 AND folderSearchExcluded = 0 AND trashed = 0 AND (status NOT IN ('QUEUED','INDEXING','NO_ACCESS') OR (:automatic = 0 AND automaticPreparation = 1 AND status = 'QUEUED'))")
    suspend fun queuePreparation(ids: List<String>, automatic: Boolean = false): Int
    @Query("SELECT assetId FROM chunks WHERE (namespace = :namespace AND vector IS NOT NULL) OR (:lexicalOnly AND namespace = 'lexical-v1') GROUP BY assetId")
    suspend fun compatiblePreparedIds(namespace: String, lexicalOnly: Boolean): List<String>
    @Query("UPDATE assets SET status = 'QUEUED', detail = 'Continuing interrupted preparation' WHERE status = 'INDEXING' AND searchExcluded = 0 AND folderSearchExcluded = 0 AND trashed = 0")
    suspend fun recoverPreparation()
    // Upgrade the old Pause implementation, which kept the selected IDs only in their status detail.
    @Query("UPDATE assets SET status = 'QUEUED' WHERE status IN ('IMPORTED','PARTIAL') AND detail = 'Preparation paused; completed content retained' AND searchExcluded = 0 AND folderSearchExcluded = 0 AND trashed = 0")
    suspend fun recoverLegacyPausedSelection()
    @Query("UPDATE assets SET status = 'QUEUED', detail = 'Preparation paused; queue and completed content retained' WHERE status IN ('QUEUED','INDEXING')")
    suspend fun pausePreparation()
    @Query("UPDATE assets SET status = CASE WHEN EXISTS (SELECT 1 FROM chunks WHERE chunks.assetId = assets.id) THEN 'PARTIAL' ELSE 'IMPORTED' END, detail = 'Automatic preparation disabled; completed content retained' WHERE status IN ('QUEUED','INDEXING') AND automaticPreparation = 1")
    suspend fun stopAutomaticPreparation()
    @Query("UPDATE assets SET status = CASE WHEN EXISTS (SELECT 1 FROM chunks WHERE chunks.assetId = assets.id) THEN 'PARTIAL' ELSE 'IMPORTED' END, detail = 'Run ended; completed content retained' WHERE status IN ('QUEUED','INDEXING') AND automaticPreparation = 0")
    suspend fun stopManualPreparation()
    @Query("SELECT * FROM assets WHERE uri = :uri") suspend fun findByUri(uri: String): AssetEntity?
    @Query("SELECT * FROM assets WHERE documentKey = :key") suspend fun findByDocumentKey(key: String): AssetEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAsset(asset: AssetEntity)
    @Transaction suspend fun insertObservedAsset(asset: AssetEntity) {
        val rules = excludedFolders()
        val blocked = FolderScopes.excludes(asset, rules, if (rules.isEmpty()) emptyMap() else allFolders().associateBy { it.uri })
        insertAsset(asset.copy(folderSearchExcluded = blocked))
    }
    @Update suspend fun updateAssetRow(asset: AssetEntity)
    @Transaction suspend fun updateAsset(asset: AssetEntity) {
        val current = asset(asset.id)
        val relocated = current != null && (current.parentUri != asset.parentUri || current.uri != asset.uri || current.documentKey != asset.documentKey)
        val blocked = if (relocated) FolderScopes.excludes(asset, excludedFolders(), allFolders().associateBy { it.uri }) else asset.folderSearchExcluded
        updateAssetRow(asset.copy(folderSearchExcluded = blocked))
        if (blocked && current?.folderSearchExcluded != true) { clearChunks(asset.id); status(asset.id, IndexStatus.IMPORTED, "Excluded by folder rule") }
    }
    @Transaction suspend fun observeSource(observed: AssetEntity, folderBlocked: Boolean? = null) {
        val current = asset(observed.id) ?: return
        val blocked = folderBlocked ?: FolderScopes.excludes(observed, excludedFolders(), allFolders().associateBy { it.uri })
        val merged = observed.copy(addedAt = current.addedAt, favorite = current.favorite, searchExcluded = current.searchExcluded,
            folderSearchExcluded = blocked, automaticPreparation = current.automaticPreparation,
            status = current.status, detail = current.detail, lastOpenedAt = current.lastOpenedAt,
            trashed = current.trashed, capturedAt = observed.capturedAt.takeIf { it > 0 } ?: current.capturedAt,
            durationMs = observed.durationMs.takeIf { it > 0 } ?: current.durationMs)
        if (merged != current) updateAsset(merged)
        if (blocked && !current.folderSearchExcluded) { clearChunks(current.id); status(current.id, IndexStatus.IMPORTED, "Excluded by folder rule") }
    }
    /** A discovery batch produces one Room invalidation, rather than one per file. */
    @Transaction suspend fun observeBatch(observed: List<AssetEntity>, folders: List<FolderEntity>) {
        folders.forEach { putFolder(it) }
        val rules = excludedFolders()
        val parents = if (rules.isEmpty()) emptyMap() else allFolders().associateBy { it.uri }
        observed.forEach { row ->
            val blocked = FolderScopes.excludes(row, rules, parents)
            val current = asset(row.id) ?: findByUri(row.uri) ?: row.documentKey?.let { findByDocumentKey(it) }
            if (current == null) insertAsset(row.copy(folderSearchExcluded = blocked))
            else if (!current.trashed && (current.uri == row.uri || current.documentKey == row.documentKey)) {
                observeSource(row.copy(id = current.id), blocked)
                if (current.size != row.size || current.modifiedAt != row.modifiedAt || current.name != row.name || current.status == IndexStatus.NO_ACCESS)
                    replaceChunks(current.id, emptyList(), IndexStatus.IMPORTED, "Source changed or access restored; prepare again")
            }
        }
    }
    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE") fun observeLocations(): Flow<List<LocationEntity>>
    @Transaction suspend fun markUnavailable(ids: List<String>) {
        ids.forEach { replaceChunks(it, emptyList(), IndexStatus.NO_ACCESS, "Media unavailable or outside your selected access") }
    }
    @Query("SELECT * FROM locations") suspend fun locations(): List<LocationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLocation(location: LocationEntity)
    @Query("SELECT * FROM folders") fun observeFolders(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders") suspend fun allFolders(): List<FolderEntity>
    @Query("SELECT * FROM excluded_folders ORDER BY name COLLATE NOCASE") fun observeExcludedFolders(): Flow<List<ExcludedFolderEntity>>
    @Query("SELECT * FROM excluded_folders") suspend fun excludedFolders(): List<ExcludedFolderEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putExcludedFolder(rule: ExcludedFolderEntity)
    @Query("DELETE FROM excluded_folders WHERE uri = :uri") suspend fun removeExcludedFolder(uri: String)
    @Transaction suspend fun excludeFolder(folder: FolderEntity, excluded: Boolean) {
        val key = FolderScopes.key(folder.uri)
        if (excluded) putExcludedFolder(ExcludedFolderEntity(folder.uri, folder.name, key))
        else excludedFolders().filter { it.uri == folder.uri || key != null && it.pathKey == key }.forEach { removeExcludedFolder(it.uri) }
        val rules = excludedFolders()
        val parents = allFolders().associateBy { it.uri }
        assets().forEach { current ->
            val blocked = FolderScopes.excludes(current, rules, parents)
            if (blocked != current.folderSearchExcluded) {
                if (blocked) clearChunks(current.id)
                updateAsset(current.copy(folderSearchExcluded = blocked, status = IndexStatus.IMPORTED,
                    detail = if (blocked || current.searchExcluded) "Excluded from content search" else "Ready to prepare content search"))
            }
        }
    }
    @Query("SELECT * FROM folders WHERE treeUri = :tree") suspend fun folders(tree: String): List<FolderEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFolder(folder: FolderEntity)
    @Query("SELECT * FROM folders WHERE uri = :uri") suspend fun folder(uri: String): FolderEntity?
    @Transaction suspend fun putFolder(folder: FolderEntity) {
        val current = this.folder(folder.uri)
        val merged = folder.copy(pinned = current?.pinned ?: folder.pinned)
        if (merged != current) insertFolder(merged)
    }
    @Query("DELETE FROM folders WHERE uri = :uri") suspend fun removeFolder(uri: String)
    @Query("UPDATE folders SET pinned = NOT pinned WHERE uri = :uri") suspend fun pinFolder(uri: String)
    @Query("UPDATE assets SET lastOpenedAt = :time WHERE id = :id") suspend fun opened(id: String, time: Long)
    @Query("SELECT * FROM operations ORDER BY createdAt DESC LIMIT 40") fun observeOperations(): Flow<List<OperationEntity>>
    @Query("SELECT * FROM operations WHERE id = :id") suspend fun operation(id: String): OperationEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOperation(operation: OperationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTrash(entry: TrashEntity)
    @Query("SELECT * FROM trash WHERE assetId = :id") suspend fun trashEntry(id: String): TrashEntity?
    @Query("DELETE FROM trash WHERE assetId = :id") suspend fun removeTrashEntry(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveMoment(moment: SavedMomentEntity)
    @Query("SELECT * FROM moments ORDER BY savedAt DESC") fun observeMoments(): Flow<List<SavedMomentEntity>>
    @Query("DELETE FROM moments WHERE id = :id") suspend fun removeMoment(id: String)
    @Query("UPDATE assets SET status = :status, detail = :detail WHERE id = :id")
    suspend fun status(id: String, status: IndexStatus, detail: String)
    @Transaction suspend fun indexState(source: AssetEntity, state: IndexStatus, detail: String): Boolean {
        val current = asset(source.id) ?: return false
        if (current.contentSearchExcluded || current.trashed || current.uri != source.uri || current.modifiedAt != source.modifiedAt || current.size != source.size || current.status == IndexStatus.NO_ACCESS) return false
        status(source.id, state, detail)
        return true
    }
    @Query("UPDATE assets SET favorite = NOT favorite WHERE id = :id") suspend fun toggleFavorite(id: String)
    @Query("DELETE FROM assets WHERE id = :id") suspend fun remove(id: String)
    @Query("DELETE FROM chunks WHERE assetId = :assetId") suspend fun clearChunks(assetId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChunks(chunks: List<ChunkEntity>)
    @Query("SELECT * FROM chunks WHERE namespace = :namespace OR namespace = 'lexical-v1'")
    suspend fun chunks(namespace: String): List<ChunkEntity>
    @Query("SELECT * FROM chunks WHERE assetId = :id") suspend fun assetChunks(id: String): List<ChunkEntity>
    @Query("DELETE FROM chunks WHERE namespace != 'lexical-v1'") suspend fun clearVectors()
    @Query("UPDATE assets SET status = 'IMPORTED', detail = 'Model changed; index again' WHERE status IN ('AI_READY','PARTIAL')")
    suspend fun invalidateModel()

    @Query("UPDATE assets SET searchExcluded = :excluded, status = 'IMPORTED', detail = :detail WHERE id = :id")
    suspend fun searchScope(id: String, excluded: Boolean, detail: String)

    @Transaction suspend fun exclude(id: String, excluded: Boolean) {
        clearChunks(id)
        searchScope(id, excluded, if (excluded) "Excluded from content search" else "Ready to prepare content search")
    }

    @Transaction suspend fun excludeMany(ids: List<String>, excluded: Boolean) {
        ids.distinct().forEach { id ->
            val current = asset(id) ?: return@forEach
            // Reapplying an existing choice must not erase a compatible prepared index.
            if (current.searchExcluded != excluded) exclude(id, excluded)
        }
    }

    // An in-flight worker must not restore derived data after exclusion or source refresh.
    @Transaction suspend fun commitIndex(asset: AssetEntity, chunks: List<ChunkEntity>, status: IndexStatus, detail: String): Boolean {
        val current = asset(asset.id) ?: return false
        if (current.contentSearchExcluded || current.trashed || current.uri != asset.uri || current.modifiedAt != asset.modifiedAt || current.size != asset.size || current.status == IndexStatus.NO_ACCESS) return false
        replaceChunks(asset.id, chunks, status, detail)
        return true
    }

    @Transaction suspend fun replaceChunks(assetId: String, chunks: List<ChunkEntity>, status: IndexStatus, detail: String) {
        clearChunks(assetId)
        insertChunks(chunks)
        status(assetId, status, detail)
    }

    @Transaction suspend fun checkpointMedia(asset: AssetEntity, chunk: ChunkEntity, detail: String): Boolean {
        val current = asset(asset.id) ?: return false
        if (current.contentSearchExcluded || current.trashed || current.uri != asset.uri || current.modifiedAt != asset.modifiedAt || current.size != asset.size || current.status == IndexStatus.NO_ACCESS) return false
        require(chunk.assetId == asset.id && when (current.kind) {
            AssetKind.VIDEO -> chunk.recipe == VideoWindows.RECIPE
            AssetKind.AUDIO -> chunk.recipe == AudioWindows.RECIPE
            else -> false
        })
        if (assetChunks(asset.id).any { it.namespace != chunk.namespace || it.recipe != chunk.recipe }) clearChunks(asset.id)
        insertChunks(listOf(chunk))
        status(asset.id, IndexStatus.INDEXING, detail)
        return true
    }

    @Transaction suspend fun checkpointDocument(asset: AssetEntity, chunk: ChunkEntity, detail: String): Boolean {
        val current = asset(asset.id) ?: return false
        if (current.contentSearchExcluded || current.trashed || current.uri != asset.uri || current.modifiedAt != asset.modifiedAt || current.size != asset.size || current.status == IndexStatus.NO_ACCESS) return false
        require(chunk.assetId == asset.id && (current.kind == AssetKind.TEXT || current.kind == AssetKind.DOCUMENT && chunk.pageNumber != null))
        insertChunks(listOf(chunk))
        status(asset.id, IndexStatus.INDEXING, detail)
        return true
    }

    @Transaction suspend fun invalidateEmbeddings() {
        clearVectors()
        invalidateModel()
    }
}

@Database(entities = [AssetEntity::class, ChunkEntity::class, LocationEntity::class, FolderEntity::class,
    OperationEntity::class, TrashEntity::class, SavedMomentEntity::class, ExcludedFolderEntity::class], version = 6, exportSchema = true)
@TypeConverters(CatalogConverters::class)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN folderSearchExcluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE assets ADD COLUMN automaticPreparation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE excluded_folders (uri TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, pathKey TEXT)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("capturedAt", "durationMs", "lastOpenedAt", "trashed").forEach {
                    db.execSQL("ALTER TABLE assets ADD COLUMN $it INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("ALTER TABLE folders ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE operations (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, assetId TEXT NOT NULL, sourceUri TEXT NOT NULL, sourceName TEXT NOT NULL, sourceSize INTEGER NOT NULL, sourceModified INTEGER NOT NULL, sourceTree TEXT, destinationTree TEXT NOT NULL, destinationParent TEXT NOT NULL, requestedName TEXT NOT NULL, resultUri TEXT, bytes INTEGER NOT NULL, state TEXT NOT NULL, detail TEXT NOT NULL, verifiedHash TEXT, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE trash (assetId TEXT NOT NULL PRIMARY KEY, originalTree TEXT NOT NULL, originalParent TEXT NOT NULL, originalName TEXT NOT NULL, trashedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE moments (id TEXT NOT NULL PRIMARY KEY, assetId TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, label TEXT NOT NULL, savedAt INTEGER NOT NULL, FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_moments_assetId ON moments(assetId)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chunks ADD COLUMN visualVector BLOB")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chunks ADD COLUMN recipe TEXT NOT NULL DEFAULT 'legacy-v1'")
                db.execSQL("ALTER TABLE chunks ADD COLUMN frameCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chunks ADD COLUMN hasAudio INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chunks ADD COLUMN audioUnavailable INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN documentKey TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN treeUri TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN parentUri TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN folderName TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE assets ADD COLUMN providerFlags INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE assets ADD COLUMN searchExcluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE UNIQUE INDEX index_assets_documentKey ON assets(documentKey)")
                db.execSQL("CREATE TABLE locations (uri TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, detail TEXT NOT NULL)")
                db.execSQL("CREATE TABLE folders (uri TEXT NOT NULL PRIMARY KEY, treeUri TEXT NOT NULL, parentUri TEXT, name TEXT NOT NULL)")
                db.execSQL("CREATE INDEX index_folders_parentUri ON folders(parentUri)")
            }
        }
    }
}
