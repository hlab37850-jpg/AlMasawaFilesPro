package com.almasawa.files.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.R
import com.almasawa.files.data.db.PanelEntity
import com.almasawa.files.data.db.PanelItemEntity
import com.almasawa.files.data.db.PanelWithItems
import com.almasawa.files.ui.components.PanelColors
import com.almasawa.files.ui.components.PanelIcons
import com.almasawa.files.util.openFile
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsScreen(onOpenFolder: (String) -> Unit, onOpenFile: (String) -> Unit, vm: PanelsViewModel = hiltViewModel()) {
    val panels by vm.panels.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showHidden by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PanelEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val noApp = stringResource(R.string.msg_no_app)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.panels_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.panel_new))
            }
        },
    ) { pad ->
        val list = panels
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (list == null) return@Box
            val visible = list.filter { showHidden || !it.panel.hidden }
            if (list.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.panels_empty), textAlign = TextAlign.Center)
                    Button(onClick = vm::addDefaults) { Text(stringResource(R.string.panels_add_defaults)) }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.panel_show_hidden), Modifier.weight(1f))
                            Switch(checked = showHidden, onCheckedChange = { showHidden = it })
                        }
                    }
                    items(visible, key = { it.panel.id }) { p ->
                        PanelCard(
                            p,
                            onOpen = { item ->
                                if (item.isDir) onOpenFolder(item.path) else onOpenFile(item.path)
                            },
                            onEdit = { editing = p.panel },
                            onTogglePin = { vm.update(p.panel.copy(pinned = !p.panel.pinned)) },
                            onToggleHidden = { vm.update(p.panel.copy(hidden = !p.panel.hidden)) },
                            onDelete = { vm.delete(p.panel.id) },
                            onRemoveItem = vm::removeItem,
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        PanelDialog(null, { creating = false }) { n, i, c -> vm.create(n, i, c); creating = false }
    }
    editing?.let { e ->
        PanelDialog(e, { editing = null }) { n, i, c ->
            vm.update(e.copy(name = n.trim(), icon = i, color = c)); editing = null
        }
    }
}

@Composable
private fun PanelCard(
    p: PanelWithItems,
    onOpen: (PanelItemEntity) -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    onRemoveItem: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(Color(p.panel.color)).padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PanelIcons[p.panel.icon] ?: Icons.Default.Folder, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(p.panel.name, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (p.panel.pinned) Icon(Icons.Default.PushPin, null, tint = Color.White)
            if (p.panel.hidden) Icon(Icons.Default.VisibilityOff, null, tint = Color.White)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.panel_edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (p.panel.pinned) R.string.panel_unpin else R.string.panel_pin)) },
                        onClick = { menu = false; onTogglePin() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (p.panel.hidden) R.string.panel_unhide else R.string.panel_hide)) },
                        onClick = { menu = false; onToggleHidden() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (p.items.isEmpty()) {
            Text(stringResource(R.string.panel_empty_items), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        } else {
            p.items.forEach { it ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(it) },
                    leadingContent = { Icon(if (it.isDir) Icons.Default.Folder else Icons.Default.Description, null) },
                    headlineContent = { Text(it.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(it.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { onRemoveItem(it.id) }) { Icon(Icons.Default.Close, null) }
                    },
                )
            }
        }
    }
}

@Composable
private fun PanelDialog(initial: PanelEntity?, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "folder") }
    var color by remember { mutableStateOf(initial?.color ?: PanelColors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.panel_new else R.string.panel_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.name)) },
                )
                Text(stringResource(R.string.panel_icon), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelIcons.forEach { (key, vec) ->
                        FilterChip(selected = icon == key, onClick = { icon = key }, label = { Icon(vec, null) })
                    }
                }
                Text(stringResource(R.string.panel_color), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelColors.forEach { c ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, icon, color) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
