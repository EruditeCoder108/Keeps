package dev.keeps.library.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.request.videoFrameOption
import dev.keeps.core.AssetKind
import dev.keeps.core.VideoWindows
import dev.keeps.core.AudioWindows
import dev.keeps.core.formatTime
import dev.keeps.core.readBounded
import dev.keeps.library.catalog.SearchHit
import dev.keeps.library.catalog.ChunkEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun FilePreview(hit: SearchHit, onDismiss: () -> Unit, onUnavailable: () -> Unit, onFavorite: () -> Unit = {}, onSearchVideo: (() -> Unit)? = null, onSaveMoment: (ChunkEntity) -> Unit = {}, previous: (() -> Unit)? = null, next: (() -> Unit)? = null) {
    val context = LocalContext.current
    val uri = remember(hit.asset.uri) { Uri.parse(hit.asset.uri) }
    var favorited by remember(hit.asset.id) { mutableStateOf(hit.asset.favorite) }
    var momentId by rememberSaveable(hit.asset.id, hit.chunk?.id) { mutableStateOf(hit.chunk?.id) }
    val moment = hit.moments.firstOrNull { it.id == momentId } ?: hit.chunk
    val access by produceState<Boolean?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }
            catch (_: Exception) { false }
        }
    }
    LaunchedEffect(access) { if (access == false) onUnavailable() }
    BackHandler(onBack = onDismiss)
    val viewerColors = MaterialTheme.colorScheme.copy(background = Color.Black, surface = Color.Black, onSurface = Color.White, onBackground = Color.White, onSurfaceVariant = Color(0xFFCCCCCC), primary = Color(0xFFFF0026), onPrimary = Color.Black, primaryContainer = Color.White, onPrimaryContainer = Color.Black)
    MaterialTheme(colorScheme = viewerColors) {
    KeepsSystemBars()
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(hit.asset.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(if (moment?.recipe in listOf(VideoWindows.RECIPE, AudioWindows.RECIPE)) "Candidate ${formatTime(moment!!.startMs ?: 0)}–${formatTime(moment!!.endMs ?: 0)}" else hit.reason, style = MaterialTheme.typography.bodySmall)
                }
                if (previous != null) IconButton(onClick = previous) { Icon(Icons.Outlined.ChevronLeft, "Previous item") }
                if (next != null) IconButton(onClick = next) { Icon(Icons.Outlined.ChevronRight, "Next item") }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close preview") }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (access) {
                    null -> CircularProgressIndicator()
                    false -> Text("This file is no longer accessible.")
                    true -> when (hit.asset.kind) {
                        AssetKind.IMAGE -> ZoomableImage(uri, hit.asset.name)
                        AssetKind.VIDEO, AssetKind.AUDIO -> MediaPreview(uri, moment?.startMs ?: 0, hit.moments)
                        AssetKind.DOCUMENT -> PdfPreview(uri, hit.chunk?.pageNumber ?: 1)
                        AssetKind.TEXT -> TextPreview(uri)
                        AssetKind.OTHER -> Text("Open this file with another app.")
                    }
                }
            }
            if (hit.asset.kind in listOf(AssetKind.VIDEO, AssetKind.AUDIO)) {
                if (hit.moments.isNotEmpty()) {
                    Text("Candidate moments", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        hit.moments.sortedBy { it.startMs }.forEach { candidate ->
                            val chosen = candidate.id == moment?.id
                            OutlinedCard(onClick = { momentId = candidate.id }, modifier = Modifier.width(108.dp).heightIn(min = 48.dp)
                                .semantics { selected = chosen },
                                colors = CardDefaults.outlinedCardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                                if (hit.asset.kind == AssetKind.VIDEO) {
                                    val request = remember(uri, candidate.id) {
                                        val start = candidate.startMs ?: 0
                                        ImageRequest.Builder(context).data(uri).decoderFactory(VideoFrameDecoder.Factory())
                                            .videoFrameMillis(start + ((candidate.endMs ?: start) - start) / 2)
                                            .videoFrameOption(MediaMetadataRetriever.OPTION_CLOSEST).size(192, 108).build()
                                    }
                                    AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(60.dp))
                                }
                                Text("${formatTime(candidate.startMs ?: 0)}–${formatTime(candidate.endMs ?: 0)}",
                                    Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (onSearchVideo != null && hit.asset.kind == AssetKind.VIDEO) TextButton(onClick = onSearchVideo) { Text("Search this video") }
            }
            if (moment?.startMs != null && moment?.endMs != null) Button(onClick = { onSaveMoment(moment!!) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.BookmarkBorder, null); Spacer(Modifier.width(8.dp)); Text("Save moment") }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { favorited = !favorited; onFavorite() }) { Icon(Icons.Outlined.FavoriteBorder, if (favorited) "Remove favorite" else "Favorite", tint = if (favorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                OutlinedButton(enabled = access == true, onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(dev.keeps.library.sources.StorageAccess(context).shareUri(uri), hit.asset.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    catch (_: android.content.ActivityNotFoundException) { /* The built-in preview remains available. */ }
                }) { Text("Open with") }
                OutlinedButton(enabled = access == true, onClick = {
                    val shared = dev.keeps.library.sources.StorageAccess(context).shareUri(uri)
                    val share = Intent(Intent.ACTION_SEND).setType(hit.asset.mimeType).putExtra(Intent.EXTRA_STREAM, shared)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    share.clipData = android.content.ClipData.newUri(context.contentResolver, "Shared file", shared)
                    context.startActivity(Intent.createChooser(share, "Share file"))
                }) { Text("Share") }
            }
        }
    }
    }
}
@Composable private fun ZoomableImage(uri: Uri, name: String) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        val maxX = size.width * (scale - 1f) / 2
        val maxY = size.height * (scale - 1f) / 2
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { size = it }.transformable(state)) {
            AsyncImage(uri, contentDescription = name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
        }
        Row {
            TextButton(onClick = { scale = (scale * 1.5f).coerceAtMost(6f) }) { Text("Zoom in") }
            TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Reset zoom") }
        }
    }
}

@Composable private fun MediaPreview(uri: Uri, startMs: Long, moments: List<ChunkEntity>) {
    val context = LocalContext.current
    var savedPosition by rememberSaveable(uri.toString()) { mutableLongStateOf(0) }
    var position by remember(uri) { mutableLongStateOf(0) }
    var duration by remember(uri) { mutableLongStateOf(0) }
    var playing by remember(uri) { mutableStateOf(false) }
    var dragging by remember(uri) { mutableStateOf(false) }
    var first by remember(uri) { mutableStateOf(true) }
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(uri)); prepare()
    } }
    LaunchedEffect(startMs) { player.pause(); player.seekTo(if (first && savedPosition > 0) savedPosition else (startMs - 1_000).coerceAtLeast(0)); first = false }
    LaunchedEffect(player) {
        while (true) { if (!dragging) position = player.currentPosition.coerceAtLeast(0); savedPosition = position; duration = player.duration.coerceAtLeast(0); playing = player.isPlaying; kotlinx.coroutines.delay(250) }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize()) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (duration > 0) {
            val red = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                moments.forEach { window ->
                    val start = (window.startMs ?: 0).coerceIn(0, duration).toFloat() / duration
                    val end = (window.endMs ?: 0).coerceIn(0, duration).toFloat() / duration
                    drawRoundRect(red.copy(alpha = if (window.startMs == startMs) 1f else .4f), topLeft = Offset(size.width * start, 2f), size = androidx.compose.ui.geometry.Size((size.width * (end - start)).coerceAtLeast(2f), size.height - 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                }
            }
            Slider(value = position.coerceIn(0, duration).toFloat(), onValueChange = { dragging = true; position = it.toLong() }, onValueChangeFinished = { player.seekTo(position); savedPosition = position; dragging = false }, valueRange = 0f..duration.toFloat(), modifier = Modifier.semantics { contentDescription = "Playback timeline" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${formatTime(position)} / ${formatTime(duration)}", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Icon(Icons.Outlined.Replay10, "Back 10 seconds") }
            FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(56.dp)) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play") }
            IconButton(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration)) }) { Icon(Icons.Outlined.Forward10, "Forward 10 seconds") }
        }
        if (startMs > 0) TextButton(onClick = { player.pause(); player.seekTo(startMs) }) { Text("Return to match") }
    }
}

private data class PdfPage(val bitmap: Bitmap? = null, val count: Int = 0, val error: Boolean = false)

@Composable private fun PdfPreview(uri: Uri, initialPage: Int) {
    val context = LocalContext.current
    var page by remember(uri) { mutableIntStateOf(initialPage.coerceAtLeast(1)) }
    val rendered by produceState<PdfPage?>(null, uri, page) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Missing file")
                descriptor.use { PdfRenderer(it).use { renderer ->
                    renderer.openPage((page - 1).coerceIn(0, renderer.pageCount - 1)).use { source ->
                        val scale = 1200.0 / maxOf(source.width, source.height)
                        val bitmap = Bitmap.createBitmap((source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfPage(bitmap, renderer.pageCount)
                    }
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { PdfPage(error = true) }
        }
    }
    DisposableEffect(rendered?.bitmap) { val bitmap = rendered?.bitmap; onDispose { bitmap?.recycle() } }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bitmap = rendered?.bitmap
            when { rendered == null -> CircularProgressIndicator(); bitmap != null -> Image(bitmap.asImageBitmap(), "Page $page", Modifier.fillMaxSize(), contentScale = ContentScale.Fit); else -> Text("Cannot preview this PDF.") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { page-- }, enabled = page > 1 && rendered != null) { Text("Previous") }
            Text("$page / ${rendered?.count ?: "…"}")
            TextButton(onClick = { page++ }, enabled = page < (rendered?.count ?: 0)) { Text("Next") }
        }
    }
}

@Composable private fun TextPreview(uri: Uri) {
    val context = LocalContext.current
    val text by produceState<String?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try { context.contentResolver.openInputStream(uri)?.use { it.readBounded(512_000).toString(Charsets.UTF_8) } ?: "Cannot read this file." }
            catch (_: Exception) { "Cannot read this file." }
        }
    }
    if (text == null) CircularProgressIndicator() else Text(text!!, Modifier.fillMaxSize().verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
}
