package com.almasawa.files.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.R
import com.almasawa.files.data.files.Category
import com.almasawa.files.ui.components.FileRow
import com.almasawa.files.util.openFile

fun categoryLabel(c: Category): Int = when (c) {
    Category.IMAGES -> R.string.cat_images
    Category.VIDEO -> R.string.cat_video
    Category.AUDIO -> R.string.cat_audio
    Category.DOCS -> R.string.cat_docs
    Category.APK -> R.string.cat_apk
    Category.ARCHIVES -> R.string.cat_archives
}

private fun categoryIcon(c: Category): ImageVector = when (c) {
    Category.IMAGES -> Icons.Default.Image
    Category.VIDEO -> Icons.Default.Movie
    Category.AUDIO -> Icons.Default.MusicNote
    Category.DOCS -> Icons.Default.Description
    Category.APK -> Icons.Default.Android
    Category.ARCHIVES -> Icons.Default.FolderZip
}

@Composable
fun HomeScreen(
    onOpenFolder: (String) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenFile: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenDual: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val dual by vm.dualPane.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.tagline), color = MaterialTheme.colorScheme.secondary)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSearch(vm.rootPath) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_search))
                }
                if (dual) {
                    OutlinedButton(onClick = onOpenDual, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.VerticalSplit, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_dual))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_internal), style = MaterialTheme.typography.titleMedium)
                    val s = ui.storage
                    if (s != null && s.total > 0) {
                        LinearProgressIndicator(
                            progress = { s.used.toFloat() / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat(R.string.storage_used, Formatter.formatShortFileSize(ctx, s.used))
                            Stat(R.string.storage_free, Formatter.formatShortFileSize(ctx, s.free))
                            Stat(R.string.storage_total, Formatter.formatShortFileSize(ctx, s.total))
                        }
                    }
                    OutlinedButton(onClick = { onOpenFolder(vm.rootPath) }) {
                        Text(stringResource(R.string.home_open_storage))
                    }
                }
            }
        }
        item { Text(stringResource(R.string.home_categories), style = MaterialTheme.typography.titleMedium) }
        items(Category.entries.chunked(3)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val st = ui.stats[c]
                    Card(Modifier.weight(1f).clickable { onOpenCategory(c) }) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(categoryIcon(c), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(categoryLabel(c)), style = MaterialTheme.typography.labelLarge)
                            if (st != null) {
                                Text(
                                    stringResource(R.string.files_count, st.count) + "\n" +
                                        Formatter.formatShortFileSize(ctx, st.bytes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (ui.recent.isNotEmpty()) {
            item { Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium) }
            items(ui.recent, key = { it.path }) { f ->
                FileRow(f, selected = false, selecting = false, onClick = { onOpenFile(f.path) }, onLongClick = {})
            }
        }
    }
}

@Composable
private fun Stat(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
