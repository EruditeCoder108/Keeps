package dev.keeps.library.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.keeps.core.AssetKind
import dev.keeps.core.readBounded
import dev.keeps.library.catalog.SearchHit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun FilePreview(hit: SearchHit, onDismiss: () -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val uri = remember(hit.asset.uri) { Uri.parse(hit.asset.uri) }
    val access by produceState<Boolean?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }
            catch (_: Exception) { false }
        }
    }
    LaunchedEffect(access) { if (access == false) onUnavailable() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(hit.asset.name, style = MaterialTheme.typography.titleMedium)
                        Text(hit.reason, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close preview") }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (access) {
                        null -> CircularProgressIndicator()
                        false -> Text("This file is no longer accessible.")
                        true -> when (hit.asset.kind) {
                            AssetKind.IMAGE -> AsyncImage(uri, contentDescription = hit.asset.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            AssetKind.VIDEO, AssetKind.AUDIO -> MediaPreview(uri, hit.chunk?.startMs ?: 0)
                            AssetKind.DOCUMENT -> PdfPreview(uri, hit.chunk?.pageNumber ?: 1)
                            AssetKind.TEXT -> TextPreview(uri)
                            AssetKind.OTHER -> Text("Open this file with another app.")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = access == true, onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, hit.asset.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch (_: android.content.ActivityNotFoundException) { /* The built-in preview remains available. */ }
                    }) { Text("Open with") }
                    OutlinedButton(enabled = access == true, onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType(hit.asset.mimeType).putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(share, "Share file"))
                    }) { Text("Share") }
                }
            }
        }
    }
}

@Composable private fun MediaPreview(uri: Uri, startMs: Long) {
    val context = LocalContext.current
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(uri)); prepare(); seekTo((startMs - 1_000).coerceAtLeast(0))
    } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxSize())
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
