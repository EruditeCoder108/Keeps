package dev.keeps.library.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.keeps.library.Destination

// Original 5x7 glyphs, drawn as circles. Body copy always uses a readable typeface.
private val dotGlyphs = mapOf(
    'A' to "01110/10001/10001/11111/10001/10001/10001", 'B' to "11110/10001/10001/11110/10001/10001/11110",
    'C' to "01111/10000/10000/10000/10000/10000/01111", 'D' to "11110/10001/10001/10001/10001/10001/11110",
    'E' to "11111/10000/10000/11110/10000/10000/11111", 'F' to "11111/10000/10000/11110/10000/10000/10000",
    'G' to "01111/10000/10000/10111/10001/10001/01111", 'H' to "10001/10001/10001/11111/10001/10001/10001",
    'I' to "11111/00100/00100/00100/00100/00100/11111", 'J' to "00111/00010/00010/00010/10010/10010/01100",
    'K' to "10001/10010/10100/11000/10100/10010/10001", 'L' to "10000/10000/10000/10000/10000/10000/11111",
    'M' to "10001/11011/10101/10101/10001/10001/10001", 'N' to "10001/11001/10101/10011/10001/10001/10001",
    'O' to "01110/10001/10001/10001/10001/10001/01110", 'P' to "11110/10001/10001/11110/10000/10000/10000",
    'Q' to "01110/10001/10001/10001/10101/10010/01101", 'R' to "11110/10001/10001/11110/10100/10010/10001",
    'S' to "01111/10000/10000/01110/00001/00001/11110", 'T' to "11111/00100/00100/00100/00100/00100/00100",
    'U' to "10001/10001/10001/10001/10001/10001/01110", 'V' to "10001/10001/10001/10001/10001/01010/00100",
    'W' to "10001/10001/10001/10101/10101/11011/10001", 'X' to "10001/10001/01010/00100/01010/10001/10001",
    'Y' to "10001/10001/01010/00100/00100/00100/00100", 'Z' to "11111/00001/00010/00100/01000/10000/11111",
)

@Composable internal fun DotHeading(text: String, modifier: Modifier = Modifier, height: Dp = 25.dp, color: Color = MaterialTheme.colorScheme.onSurface) {
    val scale = LocalDensity.current.fontScale
    val letters = remember(text) { text.uppercase().map { dotGlyphs[it]?.split('/') } }
    Canvas(modifier.width(height * (text.length * 6f / 7f) * scale).height(height * scale)
        .semantics { contentDescription = text; heading() }) {
        val step = minOf(size.height / 7f, size.width / (text.length.coerceAtLeast(1) * 6f))
        letters.forEachIndexed { index, rows -> rows?.forEachIndexed { y, row -> row.forEachIndexed { x, bit ->
            if (bit == '1') drawCircle(color, step * .30f, Offset((index * 6 + x + .5f) * step, (y + .5f) * step))
        } } }
    }
}

@Composable internal fun DotField(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)) {
    Canvas(modifier) {
        val pitch = 10.dp.toPx()
        for (y in 0..(size.height / pitch).toInt()) for (x in 0..(size.width / pitch).toInt()) {
            drawCircle(color, 1.dp.toPx(), Offset(x * pitch, y * pitch))
        }
    }
}

@Composable internal fun KeepsDock(destination: Destination, select: (Destination) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.padding(horizontal = 28.dp).navigationBarsPadding().padding(bottom = 8.dp),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .93f),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)), shadowElevation = 1.dp) {
        Row(Modifier.selectableGroup().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Triple(Destination.BROWSE, "Browse", Icons.Outlined.FolderOpen), Triple(Destination.GALLERY, "Gallery", Icons.Outlined.PhotoLibrary), Triple(Destination.SEARCH, "Search", Icons.Outlined.Search)).forEach { (tab, label, icon) ->
                val selected = destination == tab
                val ink by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(180), label = "Dock ink")
                val line by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "Dock position")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .selectable(selected, onClick = { select(tab) }, role = Role.Tab).heightIn(min = 54.dp).padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(icon, null, Modifier.size(21.dp), tint = ink)
                    Text(label, color = ink, style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.width(14.dp).height(2.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = line), CircleShape))
                }
            }
        }
    }
}

@Composable internal fun GalleryMode(albums: Boolean, choose: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to "Photos", true to "Albums").forEach { (value, label) ->
                val selected = albums == value
                val fill by animateColorAsState(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent, tween(180), label = "Gallery mode")
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(fill).selectable(selected, onClick = { choose(value) }, role = Role.Tab)
                    .heightIn(min = 42.dp).padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable internal fun HubRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) { Icon(icon, null, Modifier.padding(12.dp).size(22.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun LibraryHero(count: Int, caption: String, title: String, detail: String, onClick: (() -> Unit)? = null) {
    val ink = Color.White
    Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFF171719),
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Box {
            DotField(Modifier.align(Alignment.TopEnd).padding(20.dp).size(86.dp), Color.White.copy(alpha = .13f))
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(6.dp).background(Color(0xFFFF334E), CircleShape))
                    Text(caption.uppercase(), color = Color(0xFFB9B9BE), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp)
                }
                Text(count.toString(), color = ink, fontSize = 44.sp, lineHeight = 49.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp)
                Text(title, color = ink, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = Color(0xFFB9B9BE), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(.88f))
            }
        }
    }
}
