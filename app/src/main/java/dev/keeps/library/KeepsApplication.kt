package dev.keeps.library

import android.app.Application
import android.content.Context
import androidx.room.Room
import dev.keeps.library.catalog.LibraryDatabase
import dev.keeps.library.catalog.LibraryRepository
import dev.keeps.library.indexing.AssetIndexer
import dev.keeps.library.inference.LocalEmbeddingProvider
import dev.keeps.library.sources.DocumentImporter

class KeepsApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

class AppGraph(context: Context) {
    // Legacy per-file requests and the bounded library pump share the same preprocessing lock.
    val preparationGate = kotlinx.coroutines.sync.Mutex()
    val database = Room.databaseBuilder(context, LibraryDatabase::class.java, "keeps.db")
        .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3, LibraryDatabase.MIGRATION_3_4, LibraryDatabase.MIGRATION_4_5, LibraryDatabase.MIGRATION_5_6).build()
    val dao = database.library()
    val preparation = dev.keeps.library.indexing.PreparationController(context)
    val embeddings = LocalEmbeddingProvider(context)
    val repository = LibraryRepository(context, dao, embeddings, preparation, preparationGate)
    val importer = DocumentImporter(context, dao)
    val media = dev.keeps.library.sources.MediaLibrary(context, dao)
    val storage = dev.keeps.library.sources.StorageAccess(context)
    val operations = dev.keeps.library.sources.FileOperations(context, dao, storage, importer)
    // Replaceable within this module for lifecycle tests; production always starts with the real indexer.
    var indexer: dev.keeps.library.indexing.AssetPreparation = AssetIndexer(context, embeddings)
        internal set
    init {
        dev.keeps.library.indexing.AutomaticPreparation.observeOpportunities(context, repository)
        dev.keeps.library.indexing.PreparationNotifications.refresh(context)
        dev.keeps.library.indexing.AutomaticPreparation.schedule(context)
    }
}
