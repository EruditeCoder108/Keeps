package dev.keeps.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.keeps.library.catalog.*

@Composable internal fun FolderExclusionReview(folders: List<FolderEntity>, rules: List<ExcludedFolderEntity>, busy: Boolean, apply: (FolderEntity, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<FolderEntity?>(null) }
    val all = remember(folders, rules, query) {
        (rules.map { rule -> folders.firstOrNull { it.uri == rule.uri } ?: FolderEntity(rule.uri, rule.uri, null, rule.name) } + folders)
            .distinctBy { FolderScopes.key(it.uri) ?: it.uri }.filter { it.name.contains(query, true) || FolderScopes.key(it.uri).orEmpty().contains(query, true) }.sortedBy { it.name.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Exclude a folder and its contents, including files added later. Individual exclusions are kept when a folder rule is removed.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Find a folder") })
            Text("${rules.size} folder rules · ${all.size} matching folders", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(contentPadding = PaddingValues(14.dp), modifier = Modifier.weight(1f)) {
            if (all.isEmpty()) item { Text("No connected folders found. Connect a folder in Device access.", Modifier.padding(16.dp)) }
            items(all, key = { it.uri }) { folder ->
                val key = FolderScopes.key(folder.uri)
                val excluded = rules.any { it.uri == folder.uri || key != null && it.pathKey == key }
                fun toggle() { if (excluded) apply(folder, false) else confirm = folder }
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = ::toggle).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null, Modifier.padding(12.dp).size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(key?.removePrefix("storage:") ?: "Connected folder", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(excluded, { toggle() }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Exclude ${folder.name} from AI search" })
                }
            }
        }
    }
    confirm?.let { folder -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Exclude ${folder.name}?") }, text = { Text("Content search data for this folder and its subfolders will be removed. New files here will also be excluded. Originals, browsing, favorites and filename search stay available. Including the folder later requires preparing its contents again.") },
        confirmButton = { TextButton(onClick = { confirm = null; apply(folder, true) }) { Text("Exclude folder") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }) }
}
