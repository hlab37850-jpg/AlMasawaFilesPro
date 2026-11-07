package com.almasawa.files.ui.dual

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.R
import com.almasawa.files.data.files.PaneModel
import com.almasawa.files.ui.components.FileRow
import com.almasawa.files.ui.components.NameDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualPaneScreen(onBack: () -> Unit, onOpenFile: (String) -> Unit, vm: DualPaneViewModel = hiltViewModel()) {
    val active by vm.active.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activePane = if (active == 0) vm.left else vm.right
    val activeUi by activePane.ui.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(ctx.getString(it)); vm.messageShown() }
    }
    BackHandler(enabled = !activePane.atRoot(activeUi.path) || activeUi.selection.isNotEmpty()) {
        if (activeUi.selection.isNotEmpty()) activePane.clearSelection() else activePane.up()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dual_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = { vm.copyToOther(false) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.dual_copy_other)) }
                IconButton(onClick = { vm.copyToOther(true) }) { Icon(Icons.Default.ContentCut, stringResource(R.string.dual_move_other)) }
                IconButton(onClick = vm::swap) { Icon(Icons.Default.SwapVert, stringResource(R.string.dual_swap)) }
                IconButton(onClick = { newFolder = true }) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder)) }
                IconButton(onClick = { confirmDelete = true }, enabled = activeUi.selection.isNotEmpty()) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete))
                }
            }
        },
    ) { pad ->
        val content: @Composable (Modifier, Modifier) -> Unit = { m1, m2 ->
            PaneView(vm.left, active == 0, { vm.setActive(0) }, onOpenFile, m1)
            PaneView(vm.right, active == 1, { vm.setActive(1) }, onOpenFile, m2)
        }
        if (landscape) {
            Row(Modifier.padding(pad).fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxHeight(), Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.padding(pad).fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth(), Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    if (newFolder) NameDialog(R.string.new_folder, "", { newFolder = false }) { vm.createFolder(it); newFolder = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, activeUi.selection.size)) },
            confirmButton = { TextButton(onClick = { vm.deleteSelected(); confirmDelete = false }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PaneView(
    model: PaneModel,
    isActive: Boolean,
    onActivate: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier,
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val selecting = ui.selection.isNotEmpty()
    val free = remember(ui.path, ui.items) { Formatter.formatShortFileSize(ctx, File(ui.path).usableSpace) }

    Column(modifier.border(if (isActive) 2.dp else 0.dp, MaterialTheme.colorScheme.primary).padding(1.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onActivate(); model.up() }, enabled = !model.atRoot(ui.path)) {
                Icon(Icons.Default.ArrowUpward, stringResource(R.string.up))
            }
            Column(Modifier.weight(1f)) {
                Text(ui.path, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.storage_free) + ": " + free,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (ui.items.isEmpty() && !ui.loading) {
            Text(
                stringResource(if (ui.error) R.string.err_list else R.string.empty_folder),
                Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(ui.items, key = { it.path }) { f ->
                    FileRow(
                        item = f,
                        selected = f.path in ui.selection,
                        selecting = selecting,
                        onClick = {
                            onActivate()
                            when {
                                selecting -> model.toggleSelect(f.path)
                                f.isDir -> model.open(f.path)
                                else -> onOpenFile(f.path)
                            }
                        },
                        onLongClick = { onActivate(); model.toggleSelect(f.path) },
                    )
                }
            }
        }
    }
}
