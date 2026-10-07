package dev.keeps.library.catalog

import androidx.room.*
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "assets", indices = [Index(value = ["uri"], unique = true)])
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
)

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
    @Query("SELECT * FROM assets WHERE uri = :uri") suspend fun findByUri(uri: String): AssetEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAsset(asset: AssetEntity)
    @Query("UPDATE assets SET status = :status, detail = :detail WHERE id = :id")
    suspend fun status(id: String, status: IndexStatus, detail: String)
    @Query("UPDATE assets SET favorite = NOT favorite WHERE id = :id") suspend fun toggleFavorite(id: String)
    @Query("DELETE FROM assets WHERE id = :id") suspend fun remove(id: String)
    @Query("DELETE FROM chunks WHERE assetId = :assetId") suspend fun clearChunks(assetId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChunks(chunks: List<ChunkEntity>)
    @Query("SELECT * FROM chunks WHERE namespace = :namespace OR namespace = 'lexical-v1'")
    suspend fun chunks(namespace: String): List<ChunkEntity>
    @Query("DELETE FROM chunks WHERE namespace != 'lexical-v1'") suspend fun clearVectors()
    @Query("UPDATE assets SET status = 'IMPORTED', detail = 'Model changed; index again' WHERE status IN ('AI_READY','PARTIAL')")
    suspend fun invalidateModel()

    @Transaction suspend fun replaceChunks(assetId: String, chunks: List<ChunkEntity>, status: IndexStatus, detail: String) {
        clearChunks(assetId)
        insertChunks(chunks)
        status(assetId, status, detail)
    }

    @Transaction suspend fun invalidateEmbeddings() {
        clearVectors()
        invalidateModel()
    }
}

@Database(entities = [AssetEntity::class, ChunkEntity::class], version = 1, exportSchema = true)
@TypeConverters(CatalogConverters::class)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}
