package dev.keeps.library

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.keeps.core.AssetKind
import dev.keeps.core.IndexStatus
import dev.keeps.library.catalog.*
import dev.keeps.library.sources.ReadAccessSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Destination { BROWSE, GALLERY, SEARCH, SETTINGS, TRASH }
enum class FileSort(val label: String) { NEWEST("File date"), ADDED("Recently added"), NAME("Name"), SIZE("Largest first") }
enum class GalleryFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), SCREENSHOTS("Screenshots") }
enum class BrowseCategory(val label: String) { IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio"), DOCUMENTS("Documents"), DOWNLOADS("Downloads"), ARCHIVES("Archives") }
enum class Appearance(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
data class DeviceAccessUi(val files: Boolean = false, val media: Boolean = false, val fullMedia: Boolean = false, val revision: Int = 0)
data class SearchContext(val query: String, val kind: AssetKind?, val destination: Destination, val folder: String?, val category: BrowseCategory?, val favorites: Boolean, val galleryFilter: GalleryFilter, val sort: FileSort, val videoId: String?)
data class LibraryUiState(
    val destination: Destination = Destination.BROWSE,
    val query: String = "",
    val videoId: String? = null,
    val kind: AssetKind? = null,
    val category: BrowseCategory? = null,
    val folderUri: String? = null,
    val favorites: Boolean = false,
    val sort: FileSort = FileSort.NEWEST,
    val albums: Boolean = false,
    val galleryFilter: GalleryFilter = GalleryFilter.ALL,
    val columns: Int = 3,
    val appearance: Appearance = Appearance.SYSTEM,
    val hits: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
    val resultsFor: SearchContext? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as KeepsApplication).graph
    private val preferences = application.getSharedPreferences("interface", Context.MODE_PRIVATE)
    private val mutableUi = MutableStateFlow(LibraryUiState(
        destination = if (preferences.getString("lastTab", "BROWSE") == "GALLERY") Destination.GALLERY else Destination.BROWSE,
        folderUri = if (preferences.getString("lastTab", "BROWSE") == "GALLERY") null else preferences.getString("folder", null),
        columns = preferences.getInt("columns", 3).coerceIn(3, 4),
        appearance = Appearance.entries.firstOrNull { it.name == preferences.getString("appearance", "SYSTEM") } ?: Appearance.SYSTEM,
        sort = FileSort.entries.firstOrNull { it.name == preferences.getString("sort", "NEWEST") } ?: FileSort.NEWEST,
    ))
    val ui = mutableUi.asStateFlow()
    val model = graph.embeddings.state
    val preparationControls = graph.preparation.controls
    private val mutablePreparationPaused = MutableStateFlow(dev.keeps.library.indexing.IndexWorker.isPaused(application))
    val preparationPaused = mutablePreparationPaused.asStateFlow()
    fun preparationPace(value: dev.keeps.core.PreparationPace) = graph.preparation.pace(value)
    fun preparationBatch(value: Int) = graph.preparation.batchSize(value)
    fun preparationChargingOnly(value: Boolean) = graph.preparation.chargingOnly(value)
    private val mutablePreparationKinds = MutableStateFlow(
        preferences.getStringSet("preparationKindsV1", null)?.mapNotNull { saved -> AssetKind.entries.firstOrNull { it.name == saved && it != AssetKind.OTHER } }?.toSet()
            ?: (AssetKind.entries.toSet() - AssetKind.OTHER))
    val preparationKinds = mutablePreparationKinds.asStateFlow()
    val assets = graph.repository.assets.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val preparation = combine(assets, model, preparationControls) { files, model, controls -> Triple(files, model.installed, controls.automaticEnabled) }
        .mapLatest { (files, installed, automatic) -> withContext(Dispatchers.Default) { PreparationProgress.from(files, installed, automatic) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreparationProgress())
    val preparationScope = combine(assets, model, preparationKinds) { files, model, kinds -> files.filter { it.kind in kinds } to model.installed }
        .mapLatest { (files, installed) -> withContext(Dispatchers.Default) { PreparationProgress.from(files, installed) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreparationProgress())
    private val mutableAccess = MutableStateFlow(DeviceAccessUi())
    val deviceAccess = mutableAccess.asStateFlow()
    private val mutableSetup = MutableStateFlow(!preferences.getBoolean("deviceAccessSetupV1", false))
    val accessSetup = mutableSetup.asStateFlow()
    val discovery = graph.media.progress
    val visibleAssets = combine(assets, deviceAccess) { files, _ -> files }.debounce(80).mapLatest { files ->
        withContext(Dispatchers.IO) {
            val access = ReadAccessSnapshot(getApplication())
            files.filter { !it.trashed && it.status != IndexStatus.NO_ACCESS && access.canRead(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = graph.dao.observeLocations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = graph.dao.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val excludedFolders = graph.dao.observeExcludedFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val operations = graph.dao.observeOperations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val moments = graph.dao.observeMoments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableMemory = MutableStateFlow("Tap Refresh to measure memory")
    val memory = mutableMemory.asStateFlow()
    private var mediaRefresh: Job? = null
    private var sourceRefresh: Job? = null
    private var accessSignature: String? = null
    private var mediaObserver: android.database.ContentObserver? = null
    private val tabStates = mutableMapOf<Destination, LibraryUiState>()
    private var searchJob: Job? = null
    private var generation = 0L
    private var previousTab = Destination.BROWSE

    init {
        viewModelScope.launch { assets.debounce(150).collect { refresh() } }
        viewModelScope.launch(Dispatchers.IO) { graph.repository.resumePending() }
        viewModelScope.launch { graph.preparation.controls.collect { mutablePreparationPaused.value = it.paused } }
        deviceResumed()
    }
    /** Re-check once on resume, refresh only on a grant change or explicit refresh. */
    fun deviceResumed(force: Boolean = false) {
        val context = getApplication<Application>()
        val files = android.os.Environment.isExternalStorageManager()
        val kinds = listOf(AssetKind.IMAGE, AssetKind.VIDEO, AssetKind.AUDIO)
        val allowedKinds = kinds.filter { dev.keeps.library.sources.mediaAccess(context, it) }
        val fullKinds = kinds.filter { dev.keeps.library.sources.fullMediaAccess(context, it) }
        val media = allowedKinds.isNotEmpty()
        val full = fullKinds.size == 3
        val selectedMedia = allowedKinds.any { it != AssetKind.AUDIO && it !in fullKinds }
        val signature = "$files:$allowedKinds:$fullKinds:" + context.contentResolver.persistedUriPermissions.joinToString { "${it.uri}:${it.isReadPermission}:${it.isWritePermission}" }
        if (!force && signature == accessSignature && !selectedMedia) return
        accessSignature = signature
        mutableAccess.value = DeviceAccessUi(files, media, full, deviceAccess.value.revision + 1)
        if (media || files) startMediaWatch()
        if (media || files) {
            sourceRefresh?.cancel()
            sourceRefresh = viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (files) graph.importer.connectStorage()
                    val count = graph.media.scan()
                    preferences.edit().putBoolean("mediaEnabled", media).apply()
                    message("$count device files found. AI preparation is optional.")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { message(error.message ?: "Could not refresh the device library. Check access in Settings.") }
            }
        }
        refresh()
    }
    fun finishAccessSetup() { preferences.edit().putBoolean("deviceAccessSetupV1", true).apply(); mutableSetup.value = false }
    fun showAccessSetup() { mutableSetup.value = true }
    private fun startMediaWatch() {
        if (mediaObserver != null) return
        mediaObserver = graph.media.watch {
            mediaRefresh?.cancel()
            mediaRefresh = viewModelScope.launch { delay(1_000); deviceResumed(force = true) }
        }
    }
    override fun onCleared() { mediaObserver?.let(graph.media::stop); super.onCleared() }

    fun destination(value: Destination) {
        if (value == ui.value.destination) return
        val old = ui.value
        tabStates[old.destination] = old
        if (value in listOf(Destination.SETTINGS, Destination.TRASH)) previousTab = old.destination
        val saved = tabStates[value] ?: LibraryUiState(destination = value)
        mutableUi.value = saved.copy(busy = old.busy, notice = old.notice, appearance = old.appearance, columns = old.columns)
        if (value in listOf(Destination.BROWSE, Destination.GALLERY)) preferences.edit().putString("lastTab", value.name).apply()
        refresh()
    }
    fun back() {
        when {
            ui.value.videoId != null -> clearVideoScope()
            ui.value.destination in listOf(Destination.SETTINGS, Destination.TRASH) -> destination(previousTab)
            ui.value.destination == Destination.BROWSE && ui.value.folderUri != null -> {
                val parent = folders.value.firstOrNull { it.uri == ui.value.folderUri }?.parentUri
                mutableUi.update { it.copy(folderUri = parent, query = "") }
                preferences.edit().putString("folder", parent).apply()
                refresh()
            }
            ui.value.category != null || ui.value.favorites -> home()
            ui.value.destination == Destination.GALLERY && ui.value.folderUri != null -> { mutableUi.update { it.copy(folderUri = null) }; refresh() }
        }
    }
    fun home() { mutableUi.update { it.copy(folderUri = null, query = "", category = null, favorites = false) }; preferences.edit().remove("folder").apply(); refresh() }
    fun openFolder(folder: FolderEntity) {
        mutableUi.update { it.copy(folderUri = folder.uri, query = "", category = null, favorites = false) }
        if (ui.value.destination == Destination.BROWSE) preferences.edit().putString("folder", folder.uri).apply()
        refresh()
        action { graph.importer.openFolder(folder) }
    }
    fun category(value: BrowseCategory) { mutableUi.update { it.copy(category = value, folderUri = null, query = "", favorites = false) }; refresh() }
    fun query(value: String) { mutableUi.update { it.copy(query = value) }; refresh() }
    fun filter(value: AssetKind?) { mutableUi.update { it.copy(kind = value) }; refresh() }
    fun searchVideo(asset: AssetEntity) {
        destination(Destination.SEARCH)
        mutableUi.update { it.copy(query = "", videoId = asset.id, kind = AssetKind.VIDEO, folderUri = null, favorites = false) }
        refresh()
    }
    fun clearVideoScope() { mutableUi.update { it.copy(videoId = null, kind = null) }; refresh() }
    fun favorites() { mutableUi.update { it.copy(favorites = !it.favorites) }; refresh() }
    fun galleryFilter(value: GalleryFilter) { mutableUi.update { it.copy(galleryFilter = value) }; refresh() }
    fun albums(value: Boolean) { mutableUi.update { it.copy(albums = value, folderUri = null) }; refresh() }
    fun sort(value: FileSort) { mutableUi.update { it.copy(sort = value) }; preferences.edit().putString("sort", value.name).apply(); refresh() }
    fun density() { mutableUi.update { it.copy(columns = if (it.columns == 3) 4 else 3) }; preferences.edit().putInt("columns", ui.value.columns).apply() }
    fun appearance(value: Appearance) { mutableUi.update { it.copy(appearance = value) }; preferences.edit().putString("appearance", value.name).apply() }
    fun clearNotice() { mutableUi.update { it.copy(notice = null) } }

    private fun refresh() {
        searchJob?.cancel()
        val token = ++generation
        val snapshot = ui.value
        if (snapshot.destination == Destination.SETTINGS) return
        if (snapshot.destination == Destination.TRASH) {
            searchJob = viewModelScope.launch { mutableUi.update { it.copy(hits = graph.dao.assets().filter { a -> a.trashed }.map { a -> SearchHit(a, reason = "In trash") }, searching = false) } }
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            mutableUi.update { it.copy(searching = true) }
            try {
                delay(if (snapshot.query.isBlank()) 0 else 180)
                fun visible(hits: List<SearchHit>): List<SearchHit> {
                    var filtered = hits.filter { hit ->
                        val a = hit.asset
                        val categoryMatch = when (snapshot.category) {
                            BrowseCategory.IMAGES -> a.kind == AssetKind.IMAGE
                            BrowseCategory.VIDEOS -> a.kind == AssetKind.VIDEO
                            BrowseCategory.AUDIO -> a.kind == AssetKind.AUDIO
                            BrowseCategory.DOCUMENTS -> a.kind in listOf(AssetKind.DOCUMENT, AssetKind.TEXT)
                            BrowseCategory.DOWNLOADS -> a.folderName.equals("Download", true) || a.folderName.equals("Downloads", true) || a.uri.contains("Download", true)
                            BrowseCategory.ARCHIVES -> a.name.substringAfterLast('.', "").lowercase() in listOf("zip", "7z", "rar", "tar", "gz")
                            null -> true
                        }
                        categoryMatch && (snapshot.destination != Destination.GALLERY || when (snapshot.galleryFilter) {
                            GalleryFilter.ALL -> a.kind in listOf(AssetKind.IMAGE, AssetKind.VIDEO)
                            GalleryFilter.PHOTOS -> a.kind == AssetKind.IMAGE
                            GalleryFilter.VIDEOS -> a.kind == AssetKind.VIDEO
                            GalleryFilter.SCREENSHOTS -> a.kind == AssetKind.IMAGE && a.folderName.equals("Screenshots", true)
                        })
                    }
                    if (snapshot.destination != Destination.SEARCH || snapshot.query.isBlank()) filtered = when (snapshot.sort) {
                        FileSort.NEWEST -> filtered.sortedByDescending { it.asset.capturedAt.takeIf { t -> t > 0 } ?: it.asset.modifiedAt.takeIf { t -> t > 0 } ?: it.asset.addedAt }
                        FileSort.ADDED -> filtered.sortedByDescending { it.asset.addedAt }
                        FileSort.NAME -> filtered.sortedBy { it.asset.name.lowercase() }
                        FileSort.SIZE -> filtered.sortedByDescending { it.asset.size }
                    }
                    return filtered
                }
                fun publish(response: SearchResponse, searching: Boolean) {
                    if (token != generation) return
                    mutableUi.update { it.copy(hits = visible(response.hits), searching = searching, notice = response.notice ?: it.notice,
                        resultsFor = SearchContext(snapshot.query, snapshot.kind, snapshot.destination, snapshot.folderUri,
                            snapshot.category, snapshot.favorites, snapshot.galleryFilter, snapshot.sort, snapshot.videoId)) }
                }
                val names = graph.repository.search(snapshot.query, snapshot.kind, snapshot.favorites, content = false, parentUri = snapshot.folderUri, assetId = snapshot.videoId)
                val semantic = snapshot.destination == Destination.SEARCH && snapshot.query.isNotBlank()
                publish(names, semantic)
                if (semantic) {
                    val result = graph.repository.search(snapshot.query, snapshot.kind, snapshot.favorites, parentUri = snapshot.folderUri, assetId = snapshot.videoId)
                    ensureActive()
                    publish(result, false)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) mutableUi.update { it.copy(searching = false, notice = "Could not search. Refresh access or try again.") } }
        }
    }

    fun importFiles(uris: List<Uri>) = action {
        val result = graph.importer.import(uris)
        message(when {
            result.added > 0 -> "Added ${result.added} files. Browsing is ready; content preparation is optional."
            result.restored > 0 -> "Restored access to ${result.restored} files."
            else -> "These files are already in Keeps."
        })
    }
    fun chooseFolder(uri: Uri) = action { val result = graph.importer.chooseFolder(uri); message(scanMessage(result.files, result.limited, result.failedFolders)) }
    fun refreshLocations() = action {
        graph.dao.locations().forEach { val result = graph.importer.scan(it.uri); message(scanMessage(result.files, result.limited, result.failedFolders)) }
        if (preferences.getBoolean("mediaEnabled", false)) graph.media.scan()
        refresh()
    }
    private fun scanMessage(files: Int, limited: Boolean, failed: Int) = when {
        failed > 0 -> "$files files found; $failed folders unavailable. Reconnect storage or choose the folder again."
        limited -> "$files files discovered. Discovery is partial; open a folder to see more."
        else -> "$files files found. Originals stay in their folders."
    }
    fun importModel(uri: Uri) = action {
        val previousNamespace = graph.embeddings.namespace
        graph.repository.pause(); syncPreparationPause(); graph.embeddings.importModel(uri); graph.repository.applyModelChange(previousNamespace); refresh()
        message(if (previousNamespace == graph.embeddings.namespace) "Same model verified. Existing prepared content is retained; resume when ready." else "Model installed. Review your exclusions, then prepare content search.")
    }
    fun togglePreparationKind(kind: AssetKind) {
        mutablePreparationKinds.update { if (kind in it) it - kind else it + kind }
        preferences.edit().putStringSet("preparationKindsV1", preparationKinds.value.map { it.name }.toSet()).apply()
    }
    fun index() = action { message(graph.repository.queueAll(preparationKinds.value).message); syncPreparationPause() }
    fun prepareLibrary(chargingOnly: Boolean) = action {
        graph.preparation.chargingOnly(chargingOnly)
        graph.preparation.batchSize(0)
        message(graph.repository.queueAll(preparationKinds.value).message)
        syncPreparationPause()
    }
    private fun syncPreparationPause() { mutablePreparationPaused.value = dev.keeps.library.indexing.IndexWorker.isPaused(getApplication()) }
    fun prepare(asset: AssetEntity) = action { message(graph.repository.queueSelected(setOf(asset.id)).message); syncPreparationPause() }
    fun prepareSelected(files: List<AssetEntity>) = action { message(graph.repository.queueSelected(files.map { it.id }.toSet()).message); syncPreparationPause() }
    fun pause() = action { graph.repository.pause(); syncPreparationPause(); message(graph.preparation.controls.value.status.title) }
    fun resumePreparation(chargingOnly: Boolean? = null) = action { graph.repository.resumePreparation(chargingOnly); syncPreparationPause() }
    fun endPreparation() = action { graph.repository.endManualRun(); syncPreparationPause(); message("Run ended. Completed search data is kept.") }
    fun favorite(asset: AssetEntity) = action { graph.dao.toggleFavorite(asset.id) }
    fun exclude(asset: AssetEntity) = action {
        if (asset.folderSearchExcluded) message("This file is excluded by a folder rule. Review excluded folders in Search library.")
        else { graph.repository.exclude(asset, !asset.searchExcluded); message(if (!asset.searchExcluded) "Content search data removed. The file stays visible and searchable by filename." else "Included in content search. Prepare again when ready.") }
    }
    fun excludeSelected(files: List<AssetEntity>, excluded: Boolean) = action {
        val eligible = if (excluded) files else files.filter { !it.folderSearchExcluded }
        graph.repository.excludeSelected(eligible.map { it.id }, excluded)
        message(if (excluded) "${eligible.size} files excluded. Originals and filename search are kept." else "${eligible.size} files included. ${files.size - eligible.size} remain excluded by folder rules.")
    }
    fun excludeFolder(folder: FolderEntity, excluded: Boolean) = action {
        graph.dao.excludeFolder(folder, excluded)
        message(if (excluded) "Folder excluded, including future files. Originals stay visible." else "Folder rule removed. Individual exclusions are kept; prepare included files when ready.")
    }
    fun automaticPreparation(enabled: Boolean, selected: List<AssetEntity>? = null) = action {
        graph.repository.automatic(enabled, if (selected == null) preparationKinds.value else selected.map { it.kind }.toSet(), selected?.map { it.id }?.toSet())
        syncPreparationPause()
        message(if (enabled) "Automatic preparation enabled for the approved scope. It waits for charging and screen off." else "Automatic preparation disabled. Completed search data is kept.")
    }
    fun resumeAutomaticPreparation() = action {
        graph.repository.resumePreparation(); syncPreparationPause()
    }
    fun remove(asset: AssetEntity) = action { graph.repository.remove(asset); message("Removed from Keeps. The original is unchanged.") }
    fun unavailable(asset: AssetEntity) = action { graph.dao.replaceChunks(asset.id, emptyList(), IndexStatus.NO_ACCESS, "File unavailable; select it again"); message("This file is unavailable. Choose it again or reconnect storage.") }
    fun opened(asset: AssetEntity) = action { graph.dao.opened(asset.id, System.currentTimeMillis()) }
    fun pin(folder: FolderEntity) = action { graph.dao.pinFolder(folder.uri) }
    fun connectMedia() {
        deviceResumed(force = true)
        if (!deviceAccess.value.media) message("Gallery access wasn't granted. You can still choose a folder or individual files.")
    }
    fun connectStorage() { deviceResumed(force = true); if (!deviceAccess.value.files) message("Allow all files access in Android settings to browse Internal storage.") }
    fun rename(asset: AssetEntity, name: String) = action { graph.operations.rename(asset, name); message("File renamed") }
    fun renameMedia(asset: AssetEntity, name: String) = action {
        val safe = dev.keeps.core.FileNames.validate(name)
        androidx.work.WorkManager.getInstance(getApplication()).cancelUniqueWork("asset-${asset.id}").result.get()
        check(getApplication<Application>().contentResolver.update(Uri.parse(asset.uri), android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safe) }, null, null) == 1) { "Android could not rename this item" }
        val info = graph.storage.info(Uri.parse(asset.uri))
        val updated = (graph.dao.asset(asset.id) ?: asset).copy(name = info.name, documentKey = null, size = info.size, modifiedAt = info.modified)
        graph.dao.updateAsset(updated)
        graph.dao.replaceChunks(asset.id, emptyList(), IndexStatus.IMPORTED, "Renamed; prepare content search again")
        message("File renamed")
    }
    fun newFolder(folder: FolderEntity, name: String) = action { graph.operations.createFolder(folder, name); message("Folder created") }
    fun transfer(files: List<AssetEntity>, tree: Uri, move: Boolean) = action {
        graph.importer.chooseFolder(tree)
        val root = graph.dao.folders(tree.toString()).first { it.parentUri == null }
        files.forEach { graph.operations.enqueue(it, root, if (move) "MOVE" else "COPY") }
        message("Queued ${files.size} files. Names that already exist keep both copies.")
    }
    fun trash(files: List<AssetEntity>) = action { files.forEach { graph.operations.trash(it) }; message("Moving ${files.size} files to recoverable Keeps trash") }
    fun restore(asset: AssetEntity) = action { graph.operations.restore(asset); message("Restoring file to its original folder") }
    fun permanentDelete(asset: AssetEntity) = action { graph.operations.deletePermanently(asset); message("Permanently deleted") }
    fun retryOperation(id: String) = action { graph.operations.retry(id) }
    fun cancelOperation(id: String) = action { graph.operations.cancel(id) }
    fun recoverOperation(id: String) = action { graph.operations.recoverDestination(id); message("Verified destination recovered; any remaining source was left untouched") }
    fun capabilities(asset: AssetEntity) = graph.storage.capabilities(Uri.parse(asset.uri), asset.treeUri)
    fun folderCapabilities(folder: FolderEntity) = graph.storage.capabilities(Uri.parse(folder.uri), folder.treeUri)
    fun mediaTrashComplete(files: List<AssetEntity>, trashed: Boolean, deleted: Boolean = false) = action {
        files.forEach { asset ->
            if (deleted) graph.dao.remove(asset.id)
            else { graph.dao.updateAsset((graph.dao.asset(asset.id) ?: asset).copy(trashed = trashed)) }
        }
        message(if (deleted) "Permanently deleted by Android" else if (trashed) "Moved to Android trash" else "Restored from Android trash")
    }
    fun saveMoment(asset: AssetEntity, chunk: ChunkEntity) = action {
        val start = chunk.startMs ?: return@action; val end = chunk.endMs ?: return@action
        graph.dao.saveMoment(SavedMomentEntity("${asset.id}:$start:$end", asset.id, start, end, ui.value.query.ifBlank { asset.name }))
        message("Moment saved. The original video is unchanged.")
    }
    suspend fun previewHit(assetId: String, chunkId: String?): SearchHit? {
        val asset = graph.dao.asset(assetId) ?: return null
        val chunks = graph.dao.assetChunks(assetId)
        val saved = moments.value.firstOrNull { it.id == chunkId && it.assetId == assetId }
        val chunk = chunks.firstOrNull { it.id == chunkId } ?: saved?.let { ChunkEntity(it.id, assetId, "saved", startMs = it.startMs, endMs = it.endMs, recipe = dev.keeps.core.VideoWindows.RECIPE) }
        return SearchHit(asset, chunk, reason = asset.detail, moments = chunks.filter { it.startMs != null }.take(5))
    }
    fun removeMoment(id: String) = action { graph.dao.removeMoment(id) }
    fun unloadModel() = action { graph.embeddings.unload(); refreshMemory(); message("Model memory released. Your pack and search index are kept.") }
    fun refreshMemory() = viewModelScope.launch(Dispatchers.IO) {
        val info = android.os.Debug.MemoryInfo(); android.os.Debug.getMemoryInfo(info)
        val mib = info.totalPss / 1024.0
        val activity = getApplication<Application>().getSystemService(android.app.ActivityManager::class.java)
        val system = android.app.ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        mutableMemory.value = "App RAM: %.0f MiB PSS\nJava: %.0f · Native: %.0f MiB\nPhone available: %.0f MiB\nModel on disk: %.0f MiB\nMeasured %s".format(mib, info.dalvikPss / 1024.0, info.nativePss / 1024.0, system.availMem / 1048576.0, model.value.size / 1048576.0, java.time.LocalTime.now().withNano(0))
    }
    private fun message(text: String) { mutableUi.update { it.copy(notice = text) } }
    private fun action(block: suspend () -> Unit) {
        if (ui.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            mutableUi.update { it.copy(busy = true) }
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message(error.message ?: "Could not finish. Check access, storage space or model compatibility.") }
            finally { mutableUi.update { it.copy(busy = false) } }
        }
    }
}
