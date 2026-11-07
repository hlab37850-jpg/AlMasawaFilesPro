package com.almasawa.files.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.R
import com.almasawa.files.ui.components.FileRow
import com.almasawa.files.ui.components.NameDialog
import com.almasawa.files.ui.components.PropertiesDialog
import com.almasawa.files.util.copyText
import com.almasawa.files.util.shareFiles
import java.io.File

private enum class Dlg { NEW_FOLDER, NEW_FILE, RENAME, DELETE, ADD_PANEL, PROPS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenFile: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenDual: () -> Unit,
    vm: BrowserViewModel = hiltViewModel(),
) {
    val ui by vm.pane.ui.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val clip by vm.tasks.clip.collectAsStateWithLifecycle()
    val panels by vm.panels.collectAsStateWithLifecycle()
    val dual by vm.dualPane.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }

    val selecting = ui.selection.isNotEmpty()
    val atRoot = vm.pane.atRoot(ui.path)
    val sel = ui.selection.toList()
    val single = sel.singleOrNull()
    val isZip = single?.endsWith(".zip", ignoreCase = true) == true
    val currentClip = clip

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(ctx.getString(it)); vm.messageShown() }
    }
    BackHandler(enabled = selecting || !atRoot) {
        if (selecting) vm.pane.clearSelection() else vm.pane.up()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (currentClip != null && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = vm::paste,
                    icon = { Icon(Icons.Default.ContentPaste, null) },
                    text = { Text(stringResource(R.string.paste, currentClip.paths.size)) },
                )
            }
        },
        topBar = {
            Column {
                if (selecting) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.selected_count, sel.size)) },
                        navigationIcon = { IconButton(onClick = vm.pane::clearSelection) { Icon(Icons.Default.Close, null) } },
                        actions = {
                            IconButton(onClick = { vm.copySelection(false) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.copy)) }
                            IconButton(onClick = { vm.copySelection(true) }) { Icon(Icons.Default.ContentCut, stringResource(R.string.cut)) }
                            IconButton(onClick = {
                                if (!shareFiles(ctx, sel)) vm.notify(R.string.msg_share_files_only)
                            }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                            IconButton(onClick = { dialog = Dlg.DELETE }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            Box {
                                IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, onClick = { more = false; vm.pane.selectAll() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.invert)) }, onClick = { more = false; vm.pane.invertSelection() })
                                    if (single != null) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { more = false; dialog = Dlg.RENAME })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.properties)) }, onClick = { more = false; dialog = Dlg.PROPS })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.add_to_panel)) }, onClick = { more = false; dialog = Dlg.ADD_PANEL })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.zip)) }, onClick = { more = false; vm.zipSelected() })
                                    if (isZip) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.extract_here)) }, onClick = { more = false; vm.unzipSelected() })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.copy_path)) }, onClick = {
                                        more = false
                                        copyText(ctx, sel.joinToString("\n"))
                                        vm.notify(R.string.msg_path_copied)
                                    })
                                }
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    if (atRoot) stringResource(R.string.home_internal) else File(ui.path).name,
                                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(ui.path, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = {
                            if (!atRoot) IconButton(onClick = vm.pane::up) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.up)) }
                        },
                        actions = {
                            IconButton(onClick = { onSearch(ui.path) }) { Icon(Icons.Default.Search, stringResource(R.string.search_title)) }
                            if (dual) {
                                IconButton(onClick = onOpenDual) { Icon(Icons.Default.VerticalSplit, stringResource(R.string.dual_title)) }
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.new_folder)) }, onClick = { menu = false; dialog = Dlg.NEW_FOLDER })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.new_file)) }, onClick = { menu = false; dialog = Dlg.NEW_FILE })
                                    if (currentClip != null) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.clip_clear)) }, onClick = { menu = false; vm.tasks.clearClip() })
                                    }
                                }
                            }
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScrollableTabRow(
                            selectedTabIndex = tabs.index.coerceIn(0, tabs.paths.lastIndex),
                            modifier = Modifier.weight(1f),
                            edgePadding = 0.dp,
                        ) {
                            tabs.paths.forEachIndexed { i, p ->
                                Tab(
                                    selected = i == tabs.index,
                                    onClick = { vm.switchTab(i) },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                if (p == vm.rootPath) stringResource(R.string.home_internal) else File(p).name.ifEmpty { "/" },
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            if (tabs.paths.size > 1) {
                                                Icon(
                                                    Icons.Default.Close, stringResource(R.string.tab_close),
                                                    Modifier.padding(start = 6.dp).size(16.dp).clickable { vm.closeTab(i) },
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        IconButton(onClick = vm::newTab) { Icon(Icons.Default.Add, stringResource(R.string.tab_new)) }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                ui.loading && ui.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error -> Text(stringResource(R.string.err_list), Modifier.align(Alignment.Center))
                ui.items.isEmpty() -> Text(stringResource(R.string.empty_folder), Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.items, key = { it.path }) { f ->
                        FileRow(
                            item = f,
                            selected = f.path in ui.selection,
                            selecting = selecting,
                            onClick = {
                                when {
                                    selecting -> vm.pane.toggleSelect(f.path)
                                    f.isDir -> vm.pane.open(f.path)
                                    else -> onOpenFile(f.path)
                                }
                            },
                            onLongClick = { vm.pane.toggleSelect(f.path) },
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        Dlg.NEW_FOLDER -> NameDialog(R.string.new_folder, "", { dialog = null }) { vm.createFolder(it); dialog = null }
        Dlg.NEW_FILE -> NameDialog(R.string.new_file, "", { dialog = null }) { vm.createFile(it); dialog = null }
        Dlg.RENAME -> NameDialog(R.string.rename, single?.let { File(it).name } ?: "", { dialog = null }) {
            vm.renameSelected(it); dialog = null
        }
        Dlg.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, sel.size)) },
            confirmButton = { TextButton(onClick = { vm.deleteSelected(); dialog = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        Dlg.PROPS -> single?.let { PropertiesDialog(it) { dialog = null } }
        Dlg.ADD_PANEL -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.add_to_panel)) },
            text = {
                if (panels.isEmpty()) {
                    Text(stringResource(R.string.panels_empty))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        panels.forEach { p ->
                            Text(
                                p.panel.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.addSelectionToPanel(p.panel.id); dialog = null }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}
