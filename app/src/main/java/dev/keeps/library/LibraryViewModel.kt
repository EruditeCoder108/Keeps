package dev.keeps.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import dev.keeps.library.catalog.AssetEntity
import dev.keeps.library.catalog.SearchHit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Destination { SEARCH, GALLERY, FILES, SAVED, SETTINGS }
data class SearchContext(val query: String, val kind: AssetKind?, val destination: Destination)
data class LibraryUiState(
    val destination: Destination = Destination.SEARCH,
    val query: String = "",
    val kind: AssetKind? = null,
    val hits: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
    val resultsFor: SearchContext? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as KeepsApplication).graph
    private val mutableUi = MutableStateFlow(LibraryUiState())
    val ui = mutableUi.asStateFlow()
    val model = graph.embeddings.state
    val assets = graph.repository.assets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var searchJob: Job? = null

    init { viewModelScope.launch { graph.repository.assets.distinctUntilChanged().collect { refresh() } } }

    fun destination(value: Destination) {
        mutableUi.update { it.copy(destination = value, query = "", kind = null) }
        refresh()
    }
    fun query(value: String) { mutableUi.update { it.copy(query = value) }; refresh() }
    fun filter(value: AssetKind?) { mutableUi.update { it.copy(kind = value) }; refresh() }
    fun clearNotice() { mutableUi.update { it.copy(notice = null) } }

    private fun refresh() {
        searchJob?.cancel()
        val snapshot = ui.value
        searchJob = viewModelScope.launch {
            mutableUi.update { it.copy(searching = true) }
            try {
                delay(if (snapshot.query.isBlank()) 0 else 300)
                val result = graph.repository.search(snapshot.query, snapshot.kind, snapshot.destination == Destination.SAVED)
                val hits = if (snapshot.destination == Destination.GALLERY) result.hits.filter { it.asset.kind in listOf(AssetKind.IMAGE, AssetKind.VIDEO) } else result.hits
                mutableUi.update { it.copy(hits = hits, searching = false, notice = result.notice ?: it.notice,
                    resultsFor = SearchContext(snapshot.query, snapshot.kind, snapshot.destination)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUi.update { it.copy(searching = false, notice = "Could not search the library. Please retry.") } }
        }
    }

    fun importFiles(uris: List<Uri>) = action {
        val result = graph.importer.import(uris)
        val changes = buildList {
            if (result.added > 0) add("Added ${result.added} ${if (result.added == 1) "file" else "files"}")
            if (result.restored > 0) add("Restored access to ${result.restored} ${if (result.restored == 1) "file" else "files"}")
        }
        message(if (changes.isEmpty()) "These files are already in Keeps."
            else "${changes.joinToString(". ")}. Use Index library to search their content.")
    }
    fun importModel(uri: Uri) = action {
        graph.repository.pause()
        graph.embeddings.importModel(uri)
        graph.dao.invalidateEmbeddings()
        refresh()
        message("Model installed. Index your library to enable content search.")
    }
    fun index() = action { val count = graph.repository.queueAll(); message("Queued $count files for local indexing.") }
    fun pause() = action { graph.repository.pause(); graph.embeddings.unload(); message("Indexing paused.") }
    fun favorite(asset: AssetEntity) = action { graph.dao.toggleFavorite(asset.id) }
    fun remove(asset: AssetEntity) = action { graph.repository.remove(asset); message("Removed from Keeps. The original file is still on your phone.") }
    fun unavailable(asset: AssetEntity) = action {
        graph.dao.replaceChunks(asset.id, emptyList(), IndexStatus.NO_ACCESS, "File unavailable; select it again")
        message("This file is no longer accessible. Select it again or remove it from the library.")
    }

    private fun message(text: String) { mutableUi.update { it.copy(notice = text) } }
    private fun action(block: suspend () -> Unit) {
        if (ui.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            mutableUi.update { it.copy(busy = true) }
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("That action could not finish. Check the selected file, available storage, and model compatibility.") }
            finally { mutableUi.update { it.copy(busy = false) } }
        }
    }
}
