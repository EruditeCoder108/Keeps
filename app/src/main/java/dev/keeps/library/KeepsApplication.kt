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
    val database = Room.databaseBuilder(context, LibraryDatabase::class.java, "keeps.db").build()
    val dao = database.library()
    val embeddings = LocalEmbeddingProvider(context)
    val repository = LibraryRepository(context, dao, embeddings)
    val importer = DocumentImporter(context, dao)
    val indexer = AssetIndexer(context, embeddings)
}
