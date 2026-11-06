package com.almasawa.files.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.almasawa.files.ui.components.NameDialog
import com.almasawa.files.ui.components.FileRow
import com.almasawa.files.util.openFile
import java.io.File

private enum class Dlg { NEW_FOLDER, NEW_FILE, RENAME, DELETE, ADD_PANEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(vm: BrowserViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val panels by vm.panels.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var menu by remember { mutableStateOf(false) }
    val selecting = ui.selection.isNotEmpty()
    val atRoot = vm.atRoot(ui.path)

    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(ctx.getString(it)); vm.messageShown() }
    }
    BackHandler(enabled = selecting || !atRoot) {
        if (selecting) vm.clearSelection() else vm.up()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, ui.selection.size)) },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, null) } },
                    actions = {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Default.SelectAll, stringResource(R.string.select_all)) }
                        IconButton(onClick = vm::invertSelection) { Icon(Icons.Default.SwapHoriz, stringResource(R.string.invert)) }
                        if (ui.selection.size == 1) {
                            IconButton(onClick = { dialog = Dlg.RENAME }) { Icon(Icons.Default.Edit, stringResource(R.string.rename)) }
                        }
                        IconButton(onClick = { dialog = Dlg.ADD_PANEL }) { Icon(Icons.Default.Bookmark, stringResource(R.string.add_to_panel)) }
                        IconButton(onClick = { dialog = Dlg.DELETE }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
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
                        if (!atRoot) IconButton(onClick = vm::up) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.up)) }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.new_folder)) },
                                    onClick = { menu = false; dialog = Dlg.NEW_FOLDER },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.new_file)) },
                                    onClick = { menu = false; dialog = Dlg.NEW_FILE },
                                )
                            }
                        }
                    },
                )
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
                                    selecting -> vm.toggleSelect(f.path)
                                    f.isDir -> vm.open(f.path)
                                    !openFile(ctx, f.path) -> vm.notify(R.string.msg_no_app)
                                }
                            },
                            onLongClick = { vm.toggleSelect(f.path) },
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        Dlg.NEW_FOLDER -> NameDialog(R.string.new_folder, "", { dialog = null }) { vm.createFolder(it); dialog = null }
        Dlg.NEW_FILE -> NameDialog(R.string.new_file, "", { dialog = null }) { vm.createFile(it); dialog = null }
        Dlg.RENAME -> NameDialog(
            R.string.rename,
            ui.selection.singleOrNull()?.let { File(it).name } ?: "",
            { dialog = null },
        ) { vm.renameSelected(it); dialog = null }
        Dlg.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, ui.selection.size)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelected(); dialog = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        Dlg.ADD_PANEL -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.add_to_panel)) },
            text = {
                if (panels.isEmpty()) {
                    Text(stringResource(R.string.panels_empty))
                } else {
                    Column {
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
