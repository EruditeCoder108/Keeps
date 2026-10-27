package dev.keeps.library.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import dev.keeps.core.*
import dev.keeps.library.*
import dev.keeps.library.catalog.*
import dev.keeps.library.inference.ModelState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable fun KeepsApp(model: LibraryViewModel = viewModel(), requestedPage: String? = null, onPageHandled: () -> Unit = {}) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val assets by model.assets.collectAsStateWithLifecycle()
    val visibleAssets by model.visibleAssets.collectAsStateWithLifecycle()
    val access by model.deviceAccess.collectAsStateWithLifecycle()
    val accessSetup by model.accessSetup.collectAsStateWithLifecycle()
    val discovery by model.discovery.collectAsStateWithLifecycle()
    val preparationProgress by model.preparation.collectAsStateWithLifecycle()
    val preparationControls by model.preparationControls.collectAsStateWithLifecycle()
    val locations by model.locations.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val modelState by model.model.collectAsStateWithLifecycle()
    val operations by model.operations.collectAsStateWithLifecycle()
    val moments by model.moments.collectAsStateWithLifecycle()
    KeepsTheme(ui.appearance) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, model) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) model.deviceResumed()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val snackbar = remember { SnackbarHostState() }
        val browseState = rememberLazyListState()
        val searchState = rememberLazyListState()
        val galleryState = rememberLazyGridState()
        var previewId by rememberSaveable { mutableStateOf<String?>(null) }
        var previewChunk by rememberSaveable { mutableStateOf<String?>(null) }
        val preview by produceState<SearchHit?>(null, previewId, previewChunk, ui.hits) {
            value = previewId?.let { id -> ui.hits.firstOrNull { it.asset.id == id }?.takeIf { it.chunk?.id == previewChunk } ?: model.previewHit(id, previewChunk) }
        }
        var details by remember { mutableStateOf<AssetEntity?>(null) }
        var removing by remember { mutableStateOf<AssetEntity?>(null) }
        var preparation by remember { mutableStateOf(false) }
        var notices by remember { mutableStateOf(false) }
        var settingsPage by rememberSaveable { mutableStateOf("root") }
        fun openSettings(page: String = "root") { settingsPage = page; model.destination(Destination.SETTINGS) }
        LaunchedEffect(requestedPage) { if (requestedPage == "search") { openSettings("search"); onPageHandled() } }
        fun goBack() {
            if (ui.destination == Destination.SETTINGS && settingsPage != "root") settingsPage = settingsParent(settingsPage)
            else model.back()
        }
        val license by produceState("", notices) {
            if (notices) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                listOf("google-ai-edge-gallery-LICENSE.txt", "geist-OFL.txt").joinToString("\n\n") { notice ->
                    context.assets.open("notices/$notice").bufferedReader().use { it.readText() }
                }
            }
        }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var selecting by remember { mutableStateOf(false) }
        var toolsMenu by remember { mutableStateOf(false) }
        var changingName by remember { mutableStateOf<AssetEntity?>(null) }
        var nameText by rememberSaveable { mutableStateOf("") }
        var creatingFolder by remember { mutableStateOf(false) }
        var trashConfirm by remember { mutableStateOf<List<AssetEntity>>(emptyList()) }
        var permanentConfirm by remember { mutableStateOf<AssetEntity?>(null) }
        var transferIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var transferMove by rememberSaveable { mutableStateOf(false) }
        var approvedMediaIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var mediaAction by rememberSaveable { mutableStateOf("TRASH") }
        val mediaApproval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val files = assets.filter { it.id in approvedMediaIds }
                if (mediaAction == "RENAME") files.firstOrNull()?.let { model.renameMedia(it, nameText) }
                else model.mediaTrashComplete(files, mediaAction == "TRASH", mediaAction == "DELETE")
            }
            approvedMediaIds = emptyList()
        }
        fun requestMedia(files: List<AssetEntity>, action: String) {
            approvedMediaIds = files.map { it.id }; mediaAction = action
            val uris = files.map { Uri.parse(it.uri) }
            val request = when (action) {
                "DELETE" -> android.provider.MediaStore.createDeleteRequest(context.contentResolver, uris)
                "RENAME" -> android.provider.MediaStore.createWriteRequest(context.contentResolver, uris)
                else -> android.provider.MediaStore.createTrashRequest(context.contentResolver, uris, action == "TRASH")
            }
            mediaApproval.launch(androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build())
        }
        var settingUpAccess by rememberSaveable { mutableStateOf(false) }
        val storageSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            model.connectStorage()
            if (settingUpAccess) { settingUpAccess = false; model.finishAccessSetup() }
            if (android.os.Environment.isExternalStorageManager()) { model.destination(Destination.BROWSE); model.home() }
        }
        fun connectStorage() {
            if (android.os.Environment.isExternalStorageManager()) {
                model.connectStorage(); model.destination(Destination.BROWSE); model.home()
                if (settingUpAccess) { settingUpAccess = false; model.finishAccessSetup() }
            } else try {
                storageSettings.launch(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            } catch (_: android.content.ActivityNotFoundException) {
                storageSettings.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
        val mediaPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            model.connectMedia()
            if (settingUpAccess) connectStorage()
        }
        fun connectGallery() { if (access.fullMedia) model.connectMedia() else mediaPermissions.launch(dev.keeps.library.sources.mediaPermissions()) }
        fun beginAccessSetup() {
            settingUpAccess = true
            if (access.fullMedia) connectStorage() else mediaPermissions.launch(dev.keeps.library.sources.mediaPermissions())
        }
        LaunchedEffect(accessSetup, access.files, access.fullMedia) {
            if (accessSetup && access.files && access.fullMedia) model.finishAccessSetup()
        }
        val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.importFiles(it) }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(model::chooseFolder) }
        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importModel) }
        val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) model.transfer(assets.filter { it.id in transferIds }, uri, transferMove)
            transferIds = emptyList()
        }
        fun transfer(files: List<AssetEntity>, move: Boolean) { transferIds = files.map { it.id }; transferMove = move; destinationPicker.launch(null) }
        val currentFolder = folders.firstOrNull { it.uri == ui.folderUri }
        val currentCapabilities by produceState(dev.keeps.library.sources.StorageCapabilities(false, false, false, false), currentFolder, access.revision) {
            value = dev.keeps.library.sources.StorageCapabilities(false, false, false, false)
            currentFolder?.takeIf { it.treeUri != "mediastore" }?.let { folder ->
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { model.folderCapabilities(folder) }
            }
        }
        val selectedCapabilities by produceState<Map<String, dev.keeps.library.sources.StorageCapabilities>>(emptyMap(), selected, assets, access.revision) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                assets.filter { it.id in selected }.associate { it.id to model.capabilities(it) }
            }
        }
        val browseHome = ui.destination == Destination.BROWSE && ui.folderUri == null && ui.category == null && !ui.favorites && ui.query.isBlank()
        fun toggle(id: String) { selecting = true; selected = if (id in selected) selected - id else selected + id }
        fun open(hit: SearchHit) { if (selecting) toggle(hit.asset.id) else { model.opened(hit.asset); previewId = hit.asset.id; previewChunk = hit.chunk?.id } }
        fun share(files: List<AssetEntity>) {
            if (files.isEmpty()) return
            val uris = ArrayList(files.map { dev.keeps.library.sources.StorageAccess(context).shareUri(Uri.parse(it.uri)) })
            val intent = if (files.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first()) else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            intent.type = if (files.map { it.mimeType }.distinct().size == 1) files.first().mimeType else "*/*"
            intent.clipData = ClipData.newUri(context.contentResolver, "Shared files", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            context.startActivity(Intent.createChooser(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share files"))
        }
        BackHandler(enabled = selecting || ui.destination in listOf(Destination.SETTINGS, Destination.TRASH) || ui.folderUri != null || ui.category != null || ui.favorites || ui.videoId != null) {
            if (selecting) { selecting = false; selected = emptySet() } else goBack()
        }
        LaunchedEffect(ui.notice) { ui.notice?.let { snackbar.showSnackbar(it); model.clearNotice() } }
        val keys = remember { mutableMapOf<Destination, SearchContext>() }
        val completedKeys = remember { mutableMapOf<Destination, SearchContext>() }
        LaunchedEffect(ui.resultsFor, ui.searching) {
            val key = ui.resultsFor ?: return@LaunchedEffect
            // Filenames publish first. Expose the final ranking once per new context;
            // background refresh of the same query must preserve reading position.
            if (keys[key.destination] != key || (!ui.searching && completedKeys[key.destination] != key)) {
                when (key.destination) { Destination.BROWSE -> browseState.scrollToItem(0); Destination.SEARCH -> searchState.scrollToItem(0); Destination.GALLERY -> galleryState.scrollToItem(0); else -> Unit }
                keys[key.destination] = key
            }
            if (!ui.searching) completedKeys[key.destination] = key
        }
        LaunchedEffect(ui.destination, ui.folderUri, ui.category, ui.query, ui.galleryFilter, ui.favorites) { selecting = false; selected = emptySet() }
        LaunchedEffect(ui.hits) { selected = selected.intersect(ui.hits.map { it.asset.id }.toSet()) }
        if (preview != null) {
            val hit = preview!!
            val index = ui.hits.indexOfFirst { it.asset.id == hit.asset.id }
            FilePreview(hit, onDismiss = { previewId = null }, onUnavailable = { model.unavailable(hit.asset) },
                onFavorite = { model.favorite(hit.asset) }, onSearchVideo = { previewId = null; model.searchVideo(hit.asset) }, onSaveMoment = { model.saveMoment(hit.asset, it) },
                previous = if (ui.destination == Destination.GALLERY && index > 0) ({ open(ui.hits[index - 1]) }) else null,
                next = if (ui.destination == Destination.GALLERY && index in 0 until ui.hits.lastIndex) ({ open(ui.hits[index + 1]) }) else null)
            return@KeepsTheme
        }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val canBack = ui.destination in listOf(Destination.SETTINGS, Destination.TRASH) || ui.folderUri != null || ui.category != null || ui.favorites
                if (selecting || canBack) IconButton(onClick = { if (selecting) { selecting = false; selected = emptySet() } else goBack() }) { Icon(if (selecting) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, if (selecting) "Exit selection" else "Back") }
                Column(Modifier.weight(1f).padding(start = if (canBack || selecting) 0.dp else 4.dp)) {
                    if (!selecting && !canBack) {
                        DotHeading(ui.destination.name, height = 26.dp)
                        Text(if (ui.destination == Destination.GALLERY) "YOUR LIFE, IN FRAME" else if (ui.destination == Destination.SEARCH) "FIND WHAT YOU REMEMBER" else "EVERYTHING IN ITS PLACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    } else Text(when { selecting -> "${selected.size} selected"; ui.destination == Destination.SETTINGS -> settingsTitle(settingsPage); ui.destination == Destination.TRASH -> "Trash"; ui.folderUri != null -> currentFolder?.name ?: "Folder"; ui.category != null -> ui.category!!.label; ui.favorites -> "Favorites"; else -> ui.destination.name.lowercase().replaceFirstChar { it.titlecase() } }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (currentFolder != null && !selecting) Text(if (currentFolder.treeUri == "mediastore") "Gallery album" else currentCapabilities.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selecting) Box {
                    IconButton(onClick = { toolsMenu = true }, enabled = selected.isNotEmpty()) { Icon(Icons.Outlined.MoreVert, "Selected file actions") }
                    val files = ui.hits.filter { it.asset.id in selected }.map { it.asset }
                    DropdownMenu(toolsMenu, onDismissRequest = { toolsMenu = false }) {
                        if (files.size == 1) {
                            val file = files.single()
                            DropdownMenuItem(text = { Text("Details") }, onClick = { details = file; toolsMenu = false })
                            DropdownMenuItem(text = { Text(if (file.favorite) "Remove favorite" else "Favorite") }, onClick = { model.favorite(file); toolsMenu = false })
                            DropdownMenuItem(text = { Text("Rename") }, enabled = selectedCapabilities[file.id]?.let { it.rename || it.mediaConsent } == true, onClick = { changingName = file; nameText = file.name; toolsMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Share selected files") }, onClick = { share(files); toolsMenu = false })
                        DropdownMenuItem(text = { Text("Copy to…") }, onClick = { transfer(files, false); toolsMenu = false })
                        DropdownMenuItem(text = { Text("Move to…") }, enabled = files.all { selectedCapabilities[it.id]?.delete == true }, onClick = { transfer(files, true); toolsMenu = false })
                        DropdownMenuItem(text = { Text("Move to trash") }, enabled = files.all { selectedCapabilities[it.id]?.let { c -> c.delete || c.mediaConsent } == true }, onClick = { trashConfirm = files; toolsMenu = false })
                        DropdownMenuItem(text = { Text("Prepare selected for AI search") }, onClick = { model.prepareSelected(files); toolsMenu = false })
                        DropdownMenuItem(text = { Text("Exclude selected from AI search") }, onClick = { model.excludeSelected(files, true); toolsMenu = false; selecting = false; selected = emptySet() })
                        if (files.any { it.contentSearchExcluded }) DropdownMenuItem(text = { Text("Include selected in AI search") }, onClick = { model.excludeSelected(files, false); toolsMenu = false; selecting = false; selected = emptySet() })
                    }
                }
                else if (ui.destination != Destination.SETTINGS) {
                    Box {
                        IconButton(onClick = { toolsMenu = true }) { Icon(Icons.Outlined.Tune, "View options") }
                        DropdownMenu(toolsMenu, onDismissRequest = { toolsMenu = false }) {
                            FileSort.entries.forEach { sort -> DropdownMenuItem(text = { Text("${if (ui.sort == sort) "✓ " else ""}${sort.label}") }, onClick = { model.sort(sort); toolsMenu = false }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (ui.favorites) "Show all files" else "Favorites only") }, onClick = { model.favorites(); toolsMenu = false })
                            if (ui.destination == Destination.GALLERY) DropdownMenuItem(text = { Text("${if (ui.columns == 3) "4" else "3"} columns") }, onClick = { model.density(); toolsMenu = false })
                            DropdownMenuItem(text = { Text("Select files") }, onClick = { selecting = true; toolsMenu = false })
                            currentFolder?.takeIf { it.treeUri != "mediastore" }?.let { folder ->
                                DropdownMenuItem(text = { Text(if (folder.pinned) "Unpin folder" else "Pin folder") }, onClick = { model.pin(folder); toolsMenu = false })
                                DropdownMenuItem(text = { Text("New folder") }, enabled = currentCapabilities.create, onClick = { creatingFolder = true; nameText = ""; toolsMenu = false })
                            }
                            DropdownMenuItem(text = { Text("Trash") }, onClick = { model.destination(Destination.TRASH); toolsMenu = false })
                            DropdownMenuItem(text = { Text("File activity") }, onClick = { openSettings("activity"); toolsMenu = false })
                            if (ui.destination == Destination.GALLERY && access.fullMedia) DropdownMenuItem(text = { Text("Refresh gallery") }, onClick = { connectGallery(); toolsMenu = false })
                            DropdownMenuItem(text = { Text("Refresh folders") }, enabled = !ui.busy && locations.isNotEmpty(), onClick = { model.refreshLocations(); toolsMenu = false })
                        }
                    }
                    IconButton(onClick = { openSettings() }) { Icon(Icons.Outlined.Settings, "Settings") }
                }
            }
        }, bottomBar = {
            if (ui.destination !in listOf(Destination.SETTINGS, Destination.TRASH)) KeepsDock(ui.destination, model::destination)

        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (preparationProgress.active && ui.destination != Destination.SETTINGS) Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(preparationControls.status.title, style = MaterialTheme.typography.labelLarge)
                        Text(preparationProgress.summary, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (ui.searching || ui.busy || discovery.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (discovery.running) Text("${discovery.label} · ${discovery.files} found", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                operations.firstOrNull { it.state in listOf("QUEUED", "COPYING", "VERIFYING", "SOURCE_DELETE_PENDING", "FAILED", "REVIEW") }?.let { operation ->
                    Row(Modifier.fillMaxWidth().clickable { openSettings("activity") }.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text("${operation.type.lowercase().replaceFirstChar { it.titlecase() }} · ${operation.sourceName}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        Text(if (operation.state in listOf("FAILED", "REVIEW")) "Review" else "In progress", style = MaterialTheme.typography.labelMedium)
                    }
                }
                when (ui.destination) {
                    Destination.SETTINGS -> SettingsHub(settingsPage, { settingsPage = it }, ui, modelState, assets, locations, model, { modelPicker.launch(arrayOf("*/*")) }, { folderPicker.launch(null) }, { notices = true }, ::connectGallery, ::connectStorage, access)
                    Destination.TRASH -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Keeps trash stays until you restore or delete it. Android media trash follows Android's retention period. Originals are removed only after a verified Keeps transfer.", style = MaterialTheme.typography.bodyMedium) }
                        if (ui.hits.isEmpty()) item { Text("Trash is empty", Modifier.padding(vertical = 32.dp)) }
                        items(ui.hits, key = { it.asset.id }) { hit ->
                            OutlinedCard { Column(Modifier.padding(16.dp)) {
                                Text(hit.asset.name, fontWeight = FontWeight.SemiBold)
                                Text(fileSize(hit.asset.size), style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { if (Uri.parse(hit.asset.uri).authority == android.provider.MediaStore.AUTHORITY) requestMedia(listOf(hit.asset), "RESTORE") else model.restore(hit.asset) }) { Text("Restore") }
                                    TextButton(onClick = { permanentConfirm = hit.asset }) { Text("Delete permanently") }
                                }
                            } }
                        }
                    }
                    Destination.GALLERY -> {
                        if (!access.fullMedia) TextButton(onClick = ::connectGallery, modifier = Modifier.padding(horizontal = 12.dp)) { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (access.fullMedia) "Refresh gallery" else "Allow gallery access") }
                        GalleryMode(ui.albums, model::albums)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { GalleryFilter.entries.forEach { filter -> KeepsChip(selected = ui.galleryFilter == filter, onClick = { model.galleryFilter(filter) }, label = { Text(filter.label) }) } }
                        if (ui.albums && ui.folderUri == null) {
                            val albums = remember(ui.hits) { ui.hits.filter { it.asset.parentUri != null }.groupBy { it.asset.parentUri!! } }
                            LazyVerticalGrid(columns = GridCells.Adaptive(156.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                if (albums.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Your folders become albums", "Choose a media folder. Individually selected files appear in Photos.", "Choose a folder") { folderPicker.launch(null) } }
                                items(albums.entries.toList(), key = { it.key }) { album -> Column(Modifier.clickable { folders.firstOrNull { it.uri == album.key }?.let(model::openFolder) }) {
                                    Thumbnail(album.value.first().asset, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                                    Text(album.value.first().asset.folderName ?: "Folder", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
                                    Text("${album.value.size} ${if (album.value.size == 1) "item" else "items"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } }
                            }
                        } else if (ui.hits.isEmpty() && (ui.searching || discovery.running)) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Loading your library…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (ui.hits.isEmpty()) Box(Modifier.weight(1f)) { EmptyState("A little space for your moments", "Your chosen photos and videos appear here.", "Choose photos & videos") { filesPicker.launch(arrayOf("image/*", "video/*")) } }
                        else {
                            val groups by produceState<Map<String, List<SearchHit>>>(emptyMap(), ui.hits, ui.sort) {
                                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    ui.hits.groupBy { if (ui.sort in listOf(FileSort.NAME, FileSort.SIZE)) ui.sort.label else dateHeading(if (ui.sort == FileSort.ADDED) it.asset.addedAt else it.asset.capturedAt.takeIf { t -> t > 0 } ?: it.asset.modifiedAt.takeIf { t -> t > 0 } ?: it.asset.addedAt) }
                                }
                            }
                            LazyVerticalGrid(columns = GridCells.Fixed(ui.columns), state = galleryState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                groups.forEach { (date, hits) ->
                                    item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) { Text(date, Modifier.padding(start = 12.dp, top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.titleMedium) }
                                    items(hits, key = { it.asset.id }) { hit -> GalleryTile(hit, selecting, hit.asset.id in selected, { open(hit) }, { toggle(hit.asset.id) }) }
                                }
                            }
                        }
                    }
                    Destination.BROWSE, Destination.SEARCH -> {
                        if (ui.destination == Destination.SEARCH && ui.videoId != null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("In ${assets.firstOrNull { it.id == ui.videoId }?.name ?: "this video"}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = model::clearVideoScope) { Text("Whole library") }
                        }
                        SearchField(ui.query, model::query, if (ui.destination == Destination.SEARCH) "Search names or remembered content" else if (ui.folderUri != null) "Search filenames in this folder" else "Search filenames", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        if (ui.destination == Destination.SEARCH) {
                            if (ui.videoId == null) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(null to "All files", AssetKind.IMAGE to "Photos", AssetKind.VIDEO to "Videos", AssetKind.AUDIO to "Audio", AssetKind.DOCUMENT to "PDFs", AssetKind.TEXT to "Text").forEach { (kind, label) -> KeepsChip(selected = ui.kind == kind, onClick = { model.filter(kind) }, label = { Text(label) }) }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).clickable { openSettings("search") }.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ManageSearch, null, Modifier.size(20.dp))
                                Text(if (modelState.installed) "${preparationProgress.ready} files ready for content search" else "Set up private content search", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                            }
                        }
                        LazyColumn(state = if (ui.destination == Destination.BROWSE) browseState else searchState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
                            if (browseHome) {
                                item {
                                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("ON THIS DEVICE", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(onClick = { folderPicker.launch(null) }, enabled = !ui.busy) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("Folder") }
                                    }
                                    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                        LibraryHero(visibleAssets.size, "Your library", "Everything, within reach.", "Files, photos and moments. Yours to browse.")
                                    }
                                    CategoryGrid(visibleAssets, model::category)
                                }
                                item { SectionTitle("Locations", "${locations.size}") }
                                item { OutlinedButton(onClick = ::connectStorage, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Icon(Icons.Outlined.Storage, null); Spacer(Modifier.width(8.dp)); Text(if (access.files) "Browse device storage" else "Allow device file access") } }
                                if (locations.isEmpty()) item { EmptyState("Make room for what matters", "Choose a folder to browse. Content preparation starts only when you ask.", "Choose a folder") { folderPicker.launch(null) } }
                                items(locations, key = { it.uri }) { location -> FolderRow(location.name, location.detail, { folders.firstOrNull { it.treeUri == location.uri && it.parentUri == null }?.let(model::openFolder) }) }
                                if (folders.any { it.pinned }) item { SectionTitle("Pinned folders", "") }
                                items(folders.filter { it.pinned }, key = { "pin:${it.uri}" }) { folder -> FolderRow(folder.name, "Pinned · ${folder.treeUri.let { if (it.startsWith("file:")) "Device storage" else "Chosen folder" }}", { model.openFolder(folder) }) }
                                item {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = model::favorites, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Favorites") }
                                        OutlinedButton(onClick = { filesPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f), enabled = !ui.busy) { Text("Add files") }
                                    }
                                }
                            }
                            if (ui.destination == Destination.BROWSE && ui.folderUri != null && ui.query.isBlank()) {
                                items(folders.filter { it.parentUri == ui.folderUri }.sortedBy { it.name.lowercase() }, key = { it.uri }) { folder -> FolderRow(folder.name, "Folder", { model.openFolder(folder) }) }
                            }
                            if (ui.destination == Destination.SEARCH && ui.query.isBlank()) {
                                item { EmptyState("Find what you remember", "Describe a photo, a page, a sound or a moment. Search one video or your prepared library.", if (modelState.installed) "Review & prepare" else "Set up local AI") { openSettings("search") } }
                                if (moments.isNotEmpty()) item { SectionTitle("Saved moments", "${moments.size}") }
                                items(moments, key = { "moment:${it.id}" }) { saved ->
                                    visibleAssets.firstOrNull { it.id == saved.assetId }?.let { asset ->
                                        Row(Modifier.fillMaxWidth().clickable { open(SearchHit(asset, ChunkEntity(saved.id, asset.id, "saved", startMs = saved.startMs, endMs = saved.endMs, recipe = dev.keeps.core.VideoWindows.RECIPE), saved.label)) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Thumbnail(asset, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                                            Column(Modifier.weight(1f).padding(12.dp)) { Text(saved.label, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${dev.keeps.core.formatTime(saved.startMs)}–${dev.keeps.core.formatTime(saved.endMs)} · ${asset.name}", style = MaterialTheme.typography.bodySmall) }
                                            IconButton(onClick = { model.removeMoment(saved.id) }) { Icon(Icons.Outlined.BookmarkRemove, "Remove saved moment") }
                                        }
                                    }
                                }
                            }
                            else {
                                item { SectionTitle(if (browseHome) "Recent" else if (ui.destination == Destination.SEARCH) "Matches" else "Files", if (browseHome) "" else "${ui.hits.size}") }
                                val hits = if (browseHome) ui.hits.sortedByDescending { maxOf(it.asset.lastOpenedAt, it.asset.addedAt) }.take(6) else ui.hits
                                if (hits.isEmpty() && !browseHome && (ui.query.isNotBlank() || folders.none { it.parentUri == ui.folderUri })) item { EmptyState(if (ui.query.isNotBlank()) "No matches here" else "No files in this view", "Try another filename, clear filters, or choose more files.", "Clear filters") { model.query(""); model.filter(null); if (ui.favorites) model.favorites(); if (ui.category != null) model.home() } }
                                items(hits, key = { it.asset.id }) { hit -> FileRow(hit, ui.destination == Destination.SEARCH && ui.query.isNotBlank(), selecting, hit.asset.id in selected,
                                    { open(hit) }, { toggle(hit.asset.id) }, { model.favorite(hit.asset) }, { share(listOf(hit.asset)) }, { details = hit.asset }, { model.exclude(hit.asset) }, { removing = hit.asset }, { model.prepare(hit.asset) }, { model.searchVideo(hit.asset) },
                                    { changingName = hit.asset; nameText = hit.asset.name }, { transfer(listOf(hit.asset), false) }, { transfer(listOf(hit.asset), true) }, { trashConfirm = listOf(hit.asset) }, rememberCapabilities(model, hit.asset, access.revision)) }
                            }
                        }
                    }
                }
            }
        }
        if (notices) AlertDialog(onDismissRequest = { notices = false }, title = { Text("Open-source notices") },
            text = { Text("Google AI Edge Gallery\nCopyright 2026 Google LLC\nAdapted audio/frame ordering helper.\n\n$license", Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { notices = false }) { Text("Close") } })
        details?.let { asset -> val capabilities = rememberCapabilities(model, asset, access.revision); AlertDialog(onDismissRequest = { details = null }, title = { Text(asset.name) }, text = { Text("${asset.mimeType}\n${fileSize(asset.size)}\n${asset.folderName ?: "Individually selected file"}\n${if (asset.modifiedAt > 0) "Modified ${dateHeading(asset.modifiedAt)}" else "Modified date unavailable"}\n${capabilities.label}\n${if (asset.contentSearchExcluded) "Excluded from content search" else asset.detail}") }, confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } }) }
        if (changingName != null || creatingFolder) AlertDialog(onDismissRequest = { changingName = null; creatingFolder = false }, title = { Text(if (creatingFolder) "New folder" else "Rename file") }, text = { OutlinedTextField(nameText, { nameText = it }, label = { Text("Name") }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (creatingFolder) currentFolder?.let { model.newFolder(it, nameText) } else changingName?.let { if (Uri.parse(it.uri).authority == android.provider.MediaStore.AUTHORITY) requestMedia(listOf(it), "RENAME") else model.rename(it, nameText) }; changingName = null; creatingFolder = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { changingName = null; creatingFolder = false }) { Text("Cancel") } })
        if (trashConfirm.isNotEmpty()) AlertDialog(onDismissRequest = { trashConfirm = emptyList() }, title = { Text("Move ${trashConfirm.size} files to trash?") }, text = { Text("You can restore them from Trash. Keeps verifies its copy before removing the original. Gallery items use Android's approval and retention period.") }, confirmButton = { TextButton(onClick = { val media = trashConfirm.filter { Uri.parse(it.uri).authority == android.provider.MediaStore.AUTHORITY }; val ordinary = trashConfirm - media.toSet(); if (ordinary.isNotEmpty()) model.trash(ordinary); if (media.isNotEmpty()) requestMedia(media, "TRASH"); trashConfirm = emptyList(); selecting = false; selected = emptySet() }) { Text("Move to trash") } }, dismissButton = { TextButton(onClick = { trashConfirm = emptyList() }) { Text("Cancel") } })
        permanentConfirm?.let { asset -> AlertDialog(onDismissRequest = { permanentConfirm = null }, title = { Text("Delete permanently?") }, text = { Text("${asset.name} will be removed from storage. This cannot be undone.") }, confirmButton = { TextButton(onClick = { if (Uri.parse(asset.uri).authority == android.provider.MediaStore.AUTHORITY) requestMedia(listOf(asset), "DELETE") else model.permanentDelete(asset); permanentConfirm = null }) { Text("Delete permanently") } }, dismissButton = { TextButton(onClick = { permanentConfirm = null }) { Text("Cancel") } }) }
        removing?.let { asset -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove from Keeps?") }, text = { Text("The original stays in place. Local search data and favorite status are removed. Files in chosen folders reappear on refresh.") }, confirmButton = { TextButton(onClick = { model.remove(asset); removing = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
        if (preparation) AlertDialog(onDismissRequest = { preparation = false }, title = { Text(if (modelState.installed) "Prepare content search?" else "Prepare text only?") }, text = {
            Text("${preparationProgress.supported} supported files. ${preparationProgress.ready} already ready; compatible completed work is skipped. ${preparationProgress.unsupported} files have filename search only. " +
                if (!modelState.installed) "No AI model is installed. Photos, videos and audio cannot be prepared yet. This action extracts supported text only." else "Files are prepared one at a time on this phone. Video saves completed intervals. Thousands of files can take hours or longer; you can pause. Originals stay unchanged and excluded files stay visible.")
        }, confirmButton = { TextButton(onClick = { preparation = false; model.index() }, enabled = !ui.busy && !preparationProgress.active) { Text(if (modelState.installed) "Prepare" else "Prepare text only") } }, dismissButton = { TextButton(onClick = { preparation = false }) { Text("Later") } })
        if (accessSetup && !settingUpAccess) AlertDialog(
            onDismissRequest = model::finishAccessSetup,
            title = { Text("Open your device library") },
            text = { Text(if (access.fullMedia) "Your gallery access is ready. Allow device file access to browse Internal storage, Downloads and documents. Android opens a separate All files access toggle. AI preparation stays optional." else "Allow photos, videos and audio, then device file access. Keeps loads the permitted library automatically. You can limit access or choose individual folders instead. AI preparation stays optional.") },
            confirmButton = { TextButton(onClick = ::beginAccessSetup) { Text(if (access.fullMedia) "Allow device files" else "Allow device access") } },
            dismissButton = { TextButton(onClick = model::finishAccessSetup) { Text("Not now") } },
        )
    }
}

@Composable private fun rememberCapabilities(model: LibraryViewModel, asset: AssetEntity, revision: Int): dev.keeps.library.sources.StorageCapabilities {
    val capabilities by produceState(dev.keeps.library.sources.StorageCapabilities(false, false, false, false), asset, revision) {
        value = dev.keeps.library.sources.StorageCapabilities(false, false, false, false)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { model.capabilities(asset) }
    }
    return capabilities
}

@Composable private fun SearchField(value: String, change: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(value, change, modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = if (value.isNotEmpty()) { { IconButton(onClick = { change("") }) { Icon(Icons.Outlined.Close, "Clear search") } } } else null,
        shape = RoundedCornerShape(16.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer))
}
@Composable private fun CategoryGrid(assets: List<AssetEntity>, choose: (BrowseCategory) -> Unit) {
    val categories = BrowseCategory.entries
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { category ->
            val (icon, color) = when (category) {
                BrowseCategory.IMAGES -> Icons.Outlined.Image to MaterialTheme.colorScheme.primary
                BrowseCategory.VIDEOS -> Icons.Outlined.PlayCircleOutline to MaterialTheme.colorScheme.primary
                BrowseCategory.AUDIO -> Icons.Outlined.MusicNote to MaterialTheme.colorScheme.primary
                BrowseCategory.DOCUMENTS -> Icons.Outlined.Description to MaterialTheme.colorScheme.primary
                BrowseCategory.ARCHIVES -> Icons.Outlined.Inventory2 to MaterialTheme.colorScheme.primary
                BrowseCategory.DOWNLOADS -> Icons.Outlined.Download to MaterialTheme.colorScheme.primary
            }
            Surface(Modifier.weight(1f).heightIn(min = 106.dp).clickable { choose(category) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Text(category.label, style = MaterialTheme.typography.labelLarge)
                    val count = assets.count { when (category) { BrowseCategory.IMAGES -> it.kind == AssetKind.IMAGE; BrowseCategory.VIDEOS -> it.kind == AssetKind.VIDEO; BrowseCategory.AUDIO -> it.kind == AssetKind.AUDIO; BrowseCategory.DOCUMENTS -> it.kind in listOf(AssetKind.DOCUMENT, AssetKind.TEXT); BrowseCategory.ARCHIVES -> it.name.substringAfterLast('.', "").lowercase() in listOf("zip", "7z", "rar", "tar", "gz"); BrowseCategory.DOWNLOADS -> it.folderName.equals("Download", true) || it.folderName.equals("Downloads", true) || it.uri.contains("Download", true) } }
                    Text("$count ${if (count == 1) "file" else "files"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } } }
    }
}
@Composable private fun SectionTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium); Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun FolderRow(name: String, detail: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Folder, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FileRow(hit: SearchHit, result: Boolean, selecting: Boolean, selected: Boolean, open: () -> Unit, select: () -> Unit, favorite: () -> Unit, share: () -> Unit, details: () -> Unit, exclude: () -> Unit, remove: () -> Unit, prepare: () -> Unit, searchVideo: () -> Unit, rename: () -> Unit, copy: () -> Unit, move: () -> Unit, trash: () -> Unit, capabilities: dev.keeps.library.sources.StorageCapabilities) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent).combinedClickable(onClick = open, onLongClick = select).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selecting) Checkbox(selected, onCheckedChange = { select() }, modifier = Modifier.semantics { contentDescription = "Select ${hit.asset.name}" }) else Thumbnail(hit.asset, Modifier.size(if (result) 80.dp else 52.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(hit.asset.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (result) hit.reason else "${kindLabel(hit.asset.kind)} · ${fileSize(hit.asset.size)}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hit.asset.favorite) Text("Favorite", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (!selecting) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Actions for ${hit.asset.name}") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                listOf(Triple("Rename", capabilities.rename || capabilities.mediaConsent, rename), Triple("Copy to…", capabilities.read, copy), Triple("Move to…", capabilities.delete, move), Triple("Move to trash", capabilities.delete || capabilities.mediaConsent, trash)).forEach { (label, enabled, action) -> DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { menu = false; action() }) }
                if (!capabilities.delete && !capabilities.mediaConsent) DropdownMenuItem(text = { Text("Allow changes by choosing this folder again") }, enabled = false, onClick = {})
                HorizontalDivider()
                listOf((if (hit.asset.favorite) "Remove favorite" else "Favorite") to favorite, "Share" to share, "Details" to details, (if (hit.asset.contentSearchExcluded) "Include in content search" else "Exclude from content search") to exclude, "Select" to select, "Remove from Keeps" to remove).forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() }) }
                if (!hit.asset.contentSearchExcluded) DropdownMenuItem(text = { Text("Prepare content search") }, onClick = { menu = false; prepare() })
                if (hit.asset.kind == AssetKind.VIDEO) DropdownMenuItem(text = { Text("Search this video") }, onClick = { menu = false; searchVideo() })
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun GalleryTile(hit: SearchHit, selecting: Boolean, selected: Boolean, open: () -> Unit, select: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = open, onLongClick = select).semantics { contentDescription = "${hit.asset.name}, ${kindLabel(hit.asset.kind)}${if (selected) ", selected" else ""}" }) {
        Thumbnail(hit.asset, Modifier.fillMaxSize())
        if (hit.asset.kind == AssetKind.VIDEO) Surface(Modifier.align(Alignment.BottomStart).padding(6.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.7f)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.PlayArrow, null, Modifier.padding(4.dp).size(16.dp), tint = Color.White); if (hit.asset.durationMs > 0) Text(dev.keeps.core.formatTime(hit.asset.durationMs), Modifier.padding(end = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) } }
        if (hit.asset.favorite) Icon(Icons.Outlined.Favorite, null, Modifier.align(Alignment.BottomEnd).padding(8.dp).size(18.dp), tint = Color.White)
        if (selecting) Surface(Modifier.align(Alignment.TopEnd).padding(6.dp), shape = RoundedCornerShape(50), color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f)) { Icon(if (selected) Icons.Outlined.Check else Icons.Outlined.RadioButtonUnchecked, null, Modifier.padding(5.dp).size(18.dp), tint = Color.White) }
    }
}
@Composable private fun Thumbnail(asset: AssetEntity, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Icon(kindIcon(asset.kind), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (asset.kind in listOf(AssetKind.IMAGE, AssetKind.VIDEO)) {
            val context = LocalContext.current
            val request = remember(asset.uri, asset.modifiedAt) { ImageRequest.Builder(context).data(Uri.parse(asset.uri)).size(512).memoryCacheKey("${asset.uri}:${asset.modifiedAt}").diskCacheKey("${asset.uri}:${asset.modifiedAt}").apply { if (asset.kind == AssetKind.VIDEO) decoderFactory(VideoFrameDecoder.Factory()) }.build() }
            AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
@Composable private fun EmptyState(title: String, description: String, action: String, click: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(14.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = click) { Text(action); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) }
    }
}
@Composable private fun SettingsTitle(title: String, description: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium); Text(description, Modifier.padding(top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
internal fun kindIcon(kind: AssetKind): ImageVector = when (kind) { AssetKind.IMAGE -> Icons.Outlined.Image; AssetKind.VIDEO -> Icons.Outlined.PlayCircleOutline; AssetKind.AUDIO -> Icons.Outlined.GraphicEq; AssetKind.DOCUMENT -> Icons.Outlined.Description; AssetKind.TEXT -> Icons.AutoMirrored.Outlined.Notes; AssetKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile }
private fun kindLabel(kind: AssetKind) = when (kind) { AssetKind.IMAGE -> "Image"; AssetKind.VIDEO -> "Video"; AssetKind.AUDIO -> "Audio"; AssetKind.DOCUMENT -> "PDF"; AssetKind.TEXT -> "Text"; AssetKind.OTHER -> "File" }
internal fun fileSize(bytes: Long): String = when { bytes < 0 -> "Size unavailable"; bytes < 1000 -> "$bytes B"; bytes < 1_000_000 -> "${bytes / 1000} KB"; bytes < 1_000_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0); else -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0) }
private fun dateHeading(time: Long): String {
    val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return when (date) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) }
}

@Composable private fun KeepsChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected, onClick, { ProvideTextStyle(MaterialTheme.typography.labelMedium, label) }, modifier, shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .08f), selectedLabelColor = MaterialTheme.colorScheme.primary),
        border = null)
}
