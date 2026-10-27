package dev.keeps.library.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.keeps.core.*
import dev.keeps.library.*
import dev.keeps.library.catalog.*
import dev.keeps.library.inference.ModelState

internal fun settingsTitle(page: String) = when (page) {
    "search" -> "Search library"; "excluded" -> "Review exclusions"; "videos" -> "Review long videos"; "folders" -> "Excluded folders"
    "access" -> "Device access"; "appearance" -> "Appearance"; "storage" -> "Storage"
    "privacy" -> "Privacy & help"; "advanced" -> "Advanced"; "activity" -> "File activity"; else -> "Settings"
}
internal fun settingsParent(page: String) = if (page in listOf("excluded", "videos", "folders")) "search" else "root"

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable internal fun SettingsHub(page: String, navigate: (String) -> Unit, ui: LibraryUiState, modelState: ModelState,
    assets: List<AssetEntity>, locations: List<LocationEntity>, model: LibraryViewModel, importModel: () -> Unit,
    chooseFolder: () -> Unit, showNotices: () -> Unit, connectMedia: () -> Unit, connectStorage: () -> Unit, access: DeviceAccessUi) {
    val progress by model.preparation.collectAsStateWithLifecycle()
    val scope by model.preparationScope.collectAsStateWithLifecycle()
    val kinds by model.preparationKinds.collectAsStateWithLifecycle()
    val controls by model.preparationControls.collectAsStateWithLifecycle()
    val paused = controls.paused
    val status = controls.status
    val sessionNow by produceState(System.currentTimeMillis(), controls.sessionStartedAt, controls.sessionEndedAt) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(15_000) }
    }
    val memory by model.memory.collectAsStateWithLifecycle()
    val operations by model.operations.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val excludedFolders by model.excludedFolders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    var confirmAutomatic by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(dev.keeps.library.indexing.PreparationNotifications.allowed(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }
    fun requestNotifications() { if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    LaunchedEffect(page) { if (page == "advanced") model.refreshMemory() }
    AnimatedContent(page, transitionSpec = { (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 18 }) togetherWith fadeOut(tween(100)) }, label = "Settings destination") { target ->
        if (target == "folders") {
            FolderExclusionReview(folders, excludedFolders, ui.busy, model::excludeFolder)
        } else if (target in listOf("excluded", "videos")) {
            ExclusionReview(assets, target == "videos", ui.busy, model::excludeSelected)
        } else LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (target) {
                "root" -> {
                    item { LibraryHero(progress.ready, "Ready for content search", "Your library, connected.", if (modelState.installed) "Your files, photos and moments. Search preparation is always your choice." else "Browse your library now. Add private content search when you’re ready.") { navigate("search") } }
                    item { HubRow("Search library", if (status.phase != dev.keeps.core.PreparationPhase.IDLE) status.title else "${progress.ready} ready · scope, exclusions & preparation", Icons.Outlined.ManageSearch, { navigate("search") }) }
                    item { HubRow("Device access", "Files, gallery and connected folders", Icons.Outlined.FolderOpen, { navigate("access") }) }
                    item { HubRow("Appearance", "${ui.appearance.label} · gallery layout", Icons.Outlined.Contrast, { navigate("appearance") }) }
                    item { HubRow("Storage", "Model, search data and cache", Icons.Outlined.Storage, { navigate("storage") }) }
                    item { HubRow("Privacy & help", "Local processing, battery and background work", Icons.Outlined.Shield, { navigate("privacy") }) }
                    item { HubRow("Advanced", "Model import and diagnostics", Icons.Outlined.Tune, { navigate("advanced") }) }
                    item { Text("KEEPS / DEVELOPMENT PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
                }
                "search" -> {
                    item { LibraryHero(progress.ready, "Search library", "Files ready to find.", if (status.phase != dev.keeps.core.PreparationPhase.IDLE) status.title else "Prepare once. Find photos, sounds, pages and video moments.") }
                    if (controls.sessionStartedAt > 0) item {
                        HubCard {
                            Text("This preparation session", style = MaterialTheme.typography.titleMedium)
                            Text("${progress.ready} searchable now · ${controls.sessionInitialReady} at start", style = MaterialTheme.typography.bodyLarge)
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(java.time.ZoneId.systemDefault())
                            Text("Started ${formatter.format(java.time.Instant.ofEpochMilli(controls.sessionStartedAt))}", style = MaterialTheme.typography.bodySmall)
                            Text(if (controls.lastProgressAt > 0) "Last saved progress ${formatter.format(java.time.Instant.ofEpochMilli(controls.lastProgressAt))}" else "Waiting for the first saved result", style = MaterialTheme.typography.bodySmall)
                            Text("Total elapsed: ${preparationTime(controls.elapsedMs(sessionNow))}", style = MaterialTheme.typography.bodyLarge)
                            Text("Processing: ${preparationTime(controls.sessionWorkMs)} · app breaks: ${preparationTime(controls.sessionRestMs)}", style = MaterialTheme.typography.bodySmall)
                            Text("Other time: ${preparationTime(controls.otherMs(sessionNow))} · includes Android scheduling, cooling, phone use and manual Pause. Counts can change when files are excluded or changed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (progress.active || status.ongoing || paused || controls.automaticEnabled) item {
                        HubCard {
                            Text(status.title, style = MaterialTheme.typography.titleSmall)
                            if (status.detail.isNotBlank()) Text(status.detail, style = MaterialTheme.typography.bodySmall)
                            Text(progress.summary, style = MaterialTheme.typography.bodySmall)
                            if (status.phase == dev.keeps.core.PreparationPhase.PROCESSING && progress.running > 0) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(progress.currentName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            if (status.canPause || controls.automaticEnabled && !paused) OutlinedButton(onClick = model::pause, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Pause preparation") }
                            if (status.canResume) {
                                Text(if (controls.automaticEnabled) "Automatic · charging and screen off" else if (controls.chargingOnly) "Manual · while charging" else "Manual · battery use allowed", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { model.resumePreparation() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Resume preparation") }
                                if (!controls.automaticEnabled) TextButton(onClick = { confirmEnd = true }, enabled = !ui.busy) { Text("End this run") }
                            }
                        }
                    }
                    if (!modelState.installed) item {
                        HubCard {
                            Text("Add private content search", style = MaterialTheme.typography.titleMedium)
                            Text("The model download is about 485 MB. Download it in your browser, then choose the downloaded file. Filenames already work.", style = MaterialTheme.typography.bodyMedium)
                            if (modelState.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm?download=true"))) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Download model") }
                            OutlinedButton(onClick = importModel, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Choose downloaded model") }
                        }
                    }
                    item {
                        HubCard {
                            Text("What’s in your search library", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(AssetKind.IMAGE to "Photos", AssetKind.VIDEO to "Videos", AssetKind.AUDIO to "Audio", AssetKind.DOCUMENT to "PDFs", AssetKind.TEXT to "Text").forEach { (kind, label) ->
                                    FilterChip(selected = kind in kinds, onClick = { model.togglePreparationKind(kind) }, enabled = !ui.busy && !progress.active && !controls.automaticEnabled, label = { Text(label) })
                                }
                            }
                            Text("${scope.supported + scope.modelNeeded} supported files in scope · ${assets.count { it.contentSearchExcluded && !it.trashed }} excluded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item { HubRow("Review exclusions", "Leave out a few files. Keep everything else.", Icons.Outlined.RemoveCircleOutline, { navigate("excluded") }) }
                    item { HubRow("Review long videos", "Longest first · exclude what you don’t need", Icons.Outlined.SlowMotionVideo, { navigate("videos") }) }
                    item { HubRow("Excluded folders", "${excludedFolders.size} rules · also applies to future files", Icons.Outlined.FolderOff, { navigate("folders") }) }
                    item {
                        HubCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Prepare automatically", style = MaterialTheme.typography.titleMedium); Text("While charging, with the screen off", style = MaterialTheme.typography.bodySmall) }
                                Switch(checked = controls.automaticEnabled, onCheckedChange = { if (it) confirmAutomatic = true else model.automaticPreparation(false) }, enabled = !ui.busy && status.phase != dev.keeps.core.PreparationPhase.PAUSING && (controls.automaticEnabled || !progress.active && scope.supported > 0 && kinds.isNotEmpty()), modifier = Modifier.semantics { contentDescription = "Enable automatic preparation" })
                            }
                            Text(if (controls.automaticEnabled) "Approved types: ${controls.automaticKinds.joinToString { it.name.lowercase() }}. ${controls.automaticIds?.let { "Only ${it.size} selected files." } ?: "Includes new files found later."} Folder and individual exclusions always apply." else "Enable once for the types above. Keeps checks for new files, saves progress, takes rests and resumes when conditions allow. Android may defer work.", style = MaterialTheme.typography.bodyMedium)
                            if (!notificationsAllowed) TextButton(onClick = ::requestNotifications) { Text("Allow progress notifications") }
                        }
                    }
                    if (!controls.automaticEnabled && !progress.active && status.phase != dev.keeps.core.PreparationPhase.PAUSING) item {
                        HubCard {
                            Text("Start a manual run", style = MaterialTheme.typography.titleMedium)
                            Text("Prepares the selected types once. Short breaks adapt to warmth; cooling checks remain active. Completed files are skipped. Resume always continues the saved queue above.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { confirm = true }, enabled = !ui.busy && scope.supported > 0 && kinds.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Outlined.BatteryChargingFull, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Prepare while charging") }
                            TextButton(onClick = { confirm = false }, enabled = !ui.busy && !progress.active && scope.supported > 0 && kinds.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Prepare now, including on battery") }
                            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (progress.failed + progress.partial > 0) Text("${progress.partial} partial · ${progress.failed} need attention. Preparing again resumes supported media and retries failed files.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { Text("Photos and videos remain in their original locations. Exclusions affect content search; browsing and filename search stay available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                "access" -> {
                    item { HubCard { Text("Your device, your choice", style = MaterialTheme.typography.titleLarge); Text("${if (access.files) "Device files allowed" else "Device files need permission"} · ${if (access.fullMedia) "Full gallery access" else if (access.media) "Selected gallery access" else "Gallery needs permission"}"); Button(onClick = connectStorage) { Text(if (access.files) "Browse device storage" else "Allow device files") }; OutlinedButton(onClick = connectMedia) { Text(if (access.fullMedia) "Refresh gallery" else "Allow gallery access") } } }
                    item { HubRow("Connect a folder", "${locations.size} connected · optional", Icons.Outlined.CreateNewFolder, chooseFolder) }
                    item { Text("Android’s folder picker cannot grant the internal-storage root. Device files access is separate and still excludes other apps’ private storage.", style = MaterialTheme.typography.bodyMedium) }
                    item { TextButton(onClick = model::refreshLocations, enabled = !ui.busy && locations.isNotEmpty()) { Text("Refresh connected folders") } }
                }
                "appearance" -> {
                    item { HubCard { DotHeading("KEEPS", height = 30.dp); Text("Quiet surfaces. Clear intent.", style = MaterialTheme.typography.titleLarge); Text("Choose how your library feels.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Appearance.entries.forEach { value -> FilterChip(selected = ui.appearance == value, onClick = { model.appearance(value) }, label = { Text(value.label) }) } } }
                    item { HubRow("Gallery density", "${ui.columns} columns · tap to change", Icons.Outlined.GridView, model::density) }
                    item { Text("Motion follows Android’s animator settings. File names and controls use a readable typeface; dot lettering is reserved for headings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                "storage" -> {
                    item { LibraryHero((modelState.size / 1_000_000).toInt(), "Model storage / MB", if (modelState.installed) "Search model installed." else "No search model installed.", "The model is separate from your original files. Search data and runtime cache use additional space.") }
                    item { HubRow("Android storage controls", "Review Keeps’ app storage", Icons.Outlined.Storage, { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) }
                    item { Text("Clearing app storage removes the model, catalog and prepared search data. Shared original files stay in place. Cache can be rebuilt. Separate in-app storage totals and cleanup controls are still being completed.", style = MaterialTheme.typography.bodyMedium) }
                }
                "privacy" -> {
                    item { HubCard { Text("Private by design", style = MaterialTheme.typography.titleLarge); Text("Content processing runs on this phone. Keeps has no Internet permission or account. Model download opens your browser; a selected cloud file provider may download its own files.") } }
                    item { HubCard { Text("Battery & background work", style = MaterialTheme.typography.titleMedium); Text("Initial preparation uses sustained compute and can warm the phone. Charging-only preparation waits when unplugged. Cooling checks always apply. Searchable files are kept between sessions."); Text("Home and screen lock do not mean Pause. Android can delay scheduled work; Force stop prevents work until you reopen Keeps. Full-night completion and battery cost have not yet been measured for this build.", style = MaterialTheme.typography.bodySmall) } }
                    item { HubRow("Android battery information", "Open Keeps’ app information", Icons.Outlined.BatteryStd, { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) }
                    item { Text("Video results are matching candidates. Exact action boundaries, every spoken word and every file format are not guaranteed.", style = MaterialTheme.typography.bodySmall) }
                    item { TextButton(onClick = showNotices) { Text("Open-source notices") } }
                }
                "advanced" -> {
                    item { HubRow(if (modelState.installed) "Replace search model" else "Import search model", "Choose a compatible .litertlm file", Icons.Outlined.Memory, importModel) }
                    item { HubCard { Text("Diagnostics", style = MaterialTheme.typography.titleMedium); Text(memory, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { model.refreshMemory() }) { Text("Refresh measurement") }; TextButton(onClick = model::unloadModel, enabled = !ui.busy) { Text("Release AI memory") } } }
                    item { HubCard { Text("Preparation policy", style = MaterialTheme.typography.titleMedium); Text("${controls.pace.label} · ${if (controls.chargingOnly) "charging only" else "battery allowed"}. Native calls finish before a requested pause can complete. The model unloads after 60 seconds idle.", style = MaterialTheme.typography.bodyMedium) } }
                }
                "activity" -> {
                    if (operations.isEmpty()) item { HubCard { Text("All clear", style = MaterialTheme.typography.titleLarge); Text("File transfers and recovery appear here.") } }
                    items(operations, key = { it.id }) { operation -> HubCard {
                        Text(operation.sourceName, fontWeight = FontWeight.Medium)
                        Text("${operation.type.lowercase()} · ${operation.state.lowercase().replace('_', ' ')} · ${fileSize(operation.bytes)}", style = MaterialTheme.typography.bodySmall)
                        Text(operation.detail, style = MaterialTheme.typography.bodySmall)
                        if (operation.state in listOf("FAILED", "CANCELLED", "REVIEW")) TextButton(onClick = { model.retryOperation(operation.id) }) { Text("Retry safely") }
                        if (operation.state in listOf("FAILED", "CANCELLED", "REVIEW") && operation.verifiedHash != null) TextButton(onClick = { model.recoverOperation(operation.id) }) { Text("Use verified copy") }
                        if (operation.state in listOf("QUEUED", "COPYING", "VERIFYING")) TextButton(onClick = { model.cancelOperation(operation.id) }) { Text("Cancel") }
                    } }
                }
            }
        }
    }
    confirm?.let { charging -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (charging) "Prepare while charging?" else "Prepare on this phone?") },
        text = { Text("Prepare the remaining supported files in your chosen types, excluding your exclusions. ${if (charging) "Work waits until connected to power." else "This can use substantial battery."} Keeps takes rests and checks cooling signals. Android may defer work. Originals stay in place. Use Pause preparation to stop.") },
        confirmButton = { TextButton(onClick = { confirm = null; requestNotifications(); model.prepareLibrary(charging) }) { Text("Start preparation") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Not now") } }) }
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("End this run?") }, text = { Text("Remove the waiting files from this run. Completed search data and original files are kept. You can choose a new scope afterward.") }, confirmButton = { TextButton(onClick = { confirmEnd = false; model.endPreparation() }) { Text("End run") } }, dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep paused") } })
    if (confirmAutomatic) AlertDialog(onDismissRequest = { confirmAutomatic = false }, title = { Text("Enable automatic preparation?") }, text = { Text("Approve the selected content types across your accessible library, including new files found later. Excluded files and folders stay out. Work runs while charging with the screen off, pauses for cooling and keeps completed progress. Android may delay it; one night is not a guaranteed deadline. Pause stays paused until you resume.") },
        confirmButton = { TextButton(onClick = { confirmAutomatic = false; requestNotifications(); model.automaticPreparation(true) }) { Text("Enable automatic preparation") } }, dismissButton = { TextButton(onClick = { confirmAutomatic = false }) { Text("Not now") } })
}

private fun preparationTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1_000
    return if (seconds >= 3_600) "${seconds / 3_600} h ${seconds / 60 % 60} min" else if (seconds >= 60) "${seconds / 60} min ${seconds % 60} s" else "$seconds s"
}

@Composable private fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable private fun ExclusionReview(assets: List<AssetEntity>, videos: Boolean, busy: Boolean, apply: (List<AssetEntity>, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var excludedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val files = remember(assets, query, excludedOnly, videos) {
        assets.filter { !it.trashed && (!videos || it.kind == AssetKind.VIDEO) && (!excludedOnly || it.contentSearchExcluded) && it.name.contains(query, true) }
            .sortedWith(if (videos) compareByDescending<AssetEntity> { it.durationMs }.thenByDescending { it.size } else compareByDescending<AssetEntity> { it.size }.thenBy { it.name })
    }
    LaunchedEffect(files) { selected = selected.filter { id -> files.any { it.id == id } } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (videos) "Longest videos first. Leave out the ones you don’t need." else "Exclude a few files. Everything else stays in scope.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Find a file") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !excludedOnly, onClick = { excludedOnly = false; selected = emptyList() }, label = { Text("All files") })
                FilterChip(selected = excludedOnly, onClick = { excludedOnly = true; selected = emptyList() }, label = { Text("Excluded") })
            }
            Text("${files.size} files · ${selected.size} selected", style = MaterialTheme.typography.labelMedium)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            if (files.isEmpty()) item { Text("No files here", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(files, key = { it.id }) { file ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { selected = if (file.id in selected) selected - file.id else selected + file.id }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(file.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + file.id else selected - file.id }, modifier = Modifier.semantics { contentDescription = "Select ${file.name}" })
                    Icon(kindIcon(file.kind), null, Modifier.padding(horizontal = 8.dp).size(22.dp))
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("${fileSize(file.size)}${if (file.durationMs > 0) " · ${formatTime(file.durationMs)}" else ""}${if (file.folderSearchExcluded) " · Excluded by folder" else if (file.searchExcluded) " · Excluded" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Surface(shadowElevation = 3.dp) { Column(Modifier.navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { apply(assets.filter { it.id in selected }, !excludedOnly); selected = emptyList() }, enabled = !busy && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if (excludedOnly) "Include selected in search" else "Exclude selected from AI search") }
            Text("Originals, browsing and filename search stay available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}
