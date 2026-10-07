package dev.keeps.library.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import dev.keeps.core.*
import dev.keeps.library.*
import dev.keeps.library.catalog.AssetEntity
import dev.keeps.library.catalog.SearchHit
import dev.keeps.library.inference.ModelState

@Composable fun KeepsApp(model: LibraryViewModel = viewModel()) {
    KeepsTheme {
        val ui by model.ui.collectAsStateWithLifecycle()
        val assets by model.assets.collectAsStateWithLifecycle()
        val modelState by model.model.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        val listState = rememberLazyListState()
        val galleryState = rememberLazyGridState()
        var preview by remember { mutableStateOf<SearchHit?>(null) }
        var removing by remember { mutableStateOf<AssetEntity?>(null) }
        val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) model.importFiles(uris)
        }
        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(model::importModel)
        }
        LaunchedEffect(ui.notice) {
            ui.notice?.let { snackbar.showSnackbar(it); model.clearNotice() }
        }
        // Wait for the new results to publish, then expose the best match. Resetting
        // before publication lets keyed lists retain the previous first visible file.
        LaunchedEffect(ui.resultsFor) {
            if (ui.resultsFor != null) {
                listState.scrollToItem(0)
                galleryState.scrollToItem(0)
            }
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    val destinations = listOf(
                        Triple(Destination.SEARCH, "Search", Icons.Outlined.Search),
                        Triple(Destination.GALLERY, "Gallery", Icons.Outlined.PhotoLibrary),
                        Triple(Destination.FILES, "Files", Icons.Outlined.FolderOpen),
                        Triple(Destination.SAVED, "Saved", Icons.Outlined.BookmarkBorder),
                        Triple(Destination.SETTINGS, "Settings", Icons.Outlined.Tune),
                    )
                    destinations.forEach { (destination, label, icon) ->
                        NavigationBarItem(selected = ui.destination == destination,
                            onClick = { model.destination(destination) },
                            icon = { Icon(icon, contentDescription = label) }, label = { Text(label) })
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("keeps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Shield, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("On your phone", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (ui.destination == Destination.SETTINGS) {
                    SettingsScreen(modelState, ui.busy, { modelPicker.launch(arrayOf("*/*")) }, model::index, model::pause)
                } else {
                    LibraryHeader(ui, assets, modelState)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = ui.query, onValueChange = model::query, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(20.dp),
                        placeholder = { Text(if (modelState.installed) "Describe what you remember…" else "Search filenames and indexed text…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (ui.query.isNotEmpty()) IconButton(onClick = { model.query("") }) { Icon(Icons.Outlined.Close, "Clear search") }
                        },
                    )
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { filesPicker.launch(arrayOf("*/*")) }, enabled = !ui.busy) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add files")
                        }
                        OutlinedButton(onClick = model::index, enabled = !ui.busy && assets.isNotEmpty()) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Index library")
                        }
                    }
                    if (ui.destination == Destination.SEARCH || ui.destination == Destination.FILES) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(null to "All", AssetKind.IMAGE to "Photos", AssetKind.VIDEO to "Video", AssetKind.AUDIO to "Audio", AssetKind.DOCUMENT to "PDFs", AssetKind.TEXT to "Text").forEach { (kind, label) ->
                                FilterChip(selected = ui.kind == kind, onClick = { model.filter(kind) }, label = { Text(label) })
                            }
                        }
                    }
                    if (ui.searching || ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ui.query.isBlank()) "YOUR LIBRARY" else "SEARCH RESULTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${ui.hits.size} ${if (ui.hits.size == 1) "item" else "items"}", style = MaterialTheme.typography.labelMedium)
                    }
                    if (ui.hits.isEmpty()) {
                        EmptyLibrary(assets.isEmpty(), ui.query.isNotBlank(), ui.destination, Modifier.weight(1f))
                    } else if (ui.destination == Destination.GALLERY) {
                        LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), state = galleryState, modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(ui.hits, key = { it.asset.id }) { hit -> GalleryCard(hit) { preview = hit } }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(ui.hits, key = { it.asset.id }) { hit ->
                                AssetRow(hit, { preview = hit }, { model.favorite(hit.asset) }, { removing = hit.asset })
                            }
                        }
                    }
                }
            }
        }
        preview?.let { hit -> FilePreview(hit, onDismiss = { preview = null }, onUnavailable = { model.unavailable(hit.asset) }) }
        removing?.let { asset ->
            AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove from Keeps?") },
                text = { Text("The original file stays on your phone. This removes its local search data and saved status.") },
                confirmButton = { TextButton(onClick = { model.remove(asset); removing = null }) { Text("Remove") } },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
        }
    }
}

@Composable private fun LibraryHeader(ui: LibraryUiState, assets: List<AssetEntity>, model: ModelState) {
    val title = when (ui.destination) {
        Destination.SEARCH -> "Remember it.\nFind it."
        Destination.GALLERY -> "Your moments,\nclose at hand."
        Destination.FILES -> "A place for\neverything."
        Destination.SAVED -> "Worth keeping\nwithin reach."
        Destination.SETTINGS -> "Make it yours."
    }
    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
    Text(when {
        ui.destination == Destination.SAVED -> "Your favorite files, all together."
        !model.installed -> "A private library for photos, videos, audio and documents."
        else -> "Find related content without sending files to a server."
    }, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (assets.isNotEmpty()) {
        val indexed = assets.count { it.status in listOf(IndexStatus.AI_READY, IndexStatus.TEXT_READY, IndexStatus.PARTIAL) }
        val waiting = assets.count { it.status == IndexStatus.QUEUED || it.status == IndexStatus.INDEXING }
        Text("$indexed of ${assets.size} files indexed${if (waiting > 0) " · $waiting in progress" else ""}",
            Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun EmptyLibrary(empty: Boolean, searching: Boolean, destination: Destination, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(if (searching) Icons.Outlined.Search else Icons.Outlined.FolderOpen, null, Modifier.padding(20.dp).size(34.dp))
        }
        Text(when { searching -> "Nothing found yet"; destination == Destination.SAVED -> "Keep your favorites here"; empty -> "Your library starts here"; else -> "No files in this view" },
            Modifier.padding(top = 18.dp), style = MaterialTheme.typography.titleMedium)
        Text(when { searching -> "Try a filename, a different description, or index more files."; destination == Destination.SAVED -> "Tap the bookmark beside a file to save it."; empty -> "Add a few files to get started. You choose what Keeps can access."; else -> "Add photos or videos to see them here." },
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AssetRow(hit: SearchHit, open: () -> Unit, favorite: () -> Unit, remove: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = open).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(hit.asset, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(hit.asset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(statusLabel(hit.asset.status), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Column {
                IconButton(onClick = favorite, modifier = Modifier.size(36.dp)) { Icon(if (hit.asset.favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "Toggle favorite", Modifier.size(20.dp)) }
                IconButton(onClick = remove, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, "Remove from library", Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable private fun GalleryCard(hit: SearchHit, open: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = open)) {
        Thumbnail(hit.asset, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(hit.asset.name, Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun Thumbnail(asset: AssetEntity, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(kindIcon(asset.kind), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        if (asset.kind == AssetKind.IMAGE || asset.kind == AssetKind.VIDEO) {
            val context = LocalContext.current
            val request = remember(asset.uri) { ImageRequest.Builder(context).data(Uri.parse(asset.uri)).size(512)
                .apply { if (asset.kind == AssetKind.VIDEO) decoderFactory(VideoFrameDecoder.Factory()) }.build() }
            AsyncImage(request, contentDescription = asset.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable private fun SettingsScreen(model: ModelState, busy: Boolean, importModel: () -> Unit, index: () -> Unit, pause: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("A little more private.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold) }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(22.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Text("Your local search model", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLarge)
                    Text(if (model.installed) "Model installed · ${model.size / 1_000_000} MB" else "Import the full EmbeddingGemma 2 .litertlm model pack to search inside your files.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = importModel, enabled = !busy, modifier = Modifier.padding(top = 16.dp)) {
                        Text(if (model.importing) "Checking model…" else if (model.installed) "Replace model pack" else "Import model pack")
                    }
                }
            }
        }
        item {
            Text("Indexing", style = MaterialTheme.typography.titleMedium)
            Text("Content search becomes available as files are indexed. Keep your phone charged during larger batches.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = index, enabled = !busy) { Text("Index library") }
                OutlinedButton(onClick = pause, enabled = !busy) { Text("Pause") }
            }
        }
        item {
            HorizontalDivider()
            Text("Private by design", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
            Text("Files and search data stay on this phone. Keeps has no network permission. You can remove any file from its index without deleting the original.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Text("Early access · 0.1", style = MaterialTheme.typography.titleMedium)
            Text("Photos, text and PDF pages support content search. Video search uses sampled frames. Audio currently supports mono 16 kHz PCM WAV recordings up to 30 seconds. Other formats remain searchable by filename.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun kindIcon(kind: AssetKind): ImageVector = when (kind) {
    AssetKind.IMAGE -> Icons.Outlined.Image
    AssetKind.VIDEO -> Icons.Outlined.PlayCircleOutline
    AssetKind.AUDIO -> Icons.Outlined.GraphicEq
    AssetKind.DOCUMENT -> Icons.Outlined.Description
    AssetKind.TEXT -> Icons.AutoMirrored.Outlined.Notes
    AssetKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

private fun statusLabel(status: IndexStatus): String = when (status) {
    IndexStatus.IMPORTED -> "Added"
    IndexStatus.QUEUED -> "Queued"
    IndexStatus.INDEXING -> "Indexing"
    IndexStatus.TEXT_READY -> "Text searchable"
    IndexStatus.AI_READY -> "Content searchable"
    IndexStatus.PARTIAL -> "Partly indexed"
    IndexStatus.FAILED -> "Retry needed"
    IndexStatus.NO_ACCESS -> "Access needed"
    IndexStatus.UNSUPPORTED -> "Filename searchable"
}
