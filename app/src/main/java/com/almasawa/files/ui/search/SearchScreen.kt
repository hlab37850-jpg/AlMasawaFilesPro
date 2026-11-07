package com.almasawa.files.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.R
import com.almasawa.files.data.files.SearchType
import com.almasawa.files.ui.components.FileRow
import java.io.File

private fun typeLabel(t: SearchType): Int = when (t) {
    SearchType.ALL -> R.string.flt_all
    SearchType.IMAGES -> R.string.flt_images
    SearchType.VIDEO -> R.string.flt_video
    SearchType.AUDIO -> R.string.flt_audio
    SearchType.PDF -> R.string.flt_pdf
    SearchType.WORD -> R.string.flt_word
    SearchType.EXCEL -> R.string.flt_excel
    SearchType.APK -> R.string.flt_apk
    SearchType.ZIP -> R.string.flt_zip
    SearchType.LARGE -> R.string.flt_large
    SearchType.RECENT -> R.string.flt_recent
    SearchType.OLD -> R.string.flt_old
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, null) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.run() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchType.entries.forEach { t ->
                    FilterChip(selected = ui.type == t, onClick = { vm.setType(t) }, label = { Text(stringResource(typeLabel(t))) })
                }
            }
            Text(
                stringResource(R.string.search_in, File(vm.scopePath).name.ifEmpty { vm.scopePath }),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (ui.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))

            Box(Modifier.fillMaxSize()) {
                when {
                    ui.results.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        items(ui.results, key = { it.path }) { f ->
                            FileRow(
                                item = f, selected = false, selecting = false,
                                subtitle = f.path,
                                onClick = { if (f.isDir) onOpenFolder(f.path) else onOpenFile(f.path) },
                                onLongClick = { File(f.path).parentFile?.let { onOpenFolder(it.absolutePath) } },
                            )
                        }
                    }
                    ui.searched && !ui.searching -> Text(stringResource(R.string.search_none), Modifier.align(Alignment.Center))
                    history.isNotEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.search_history), style = MaterialTheme.typography.titleSmall)
                        history.forEach { h ->
                            AssistChip(onClick = { vm.setQuery(h); vm.run() }, label = { Text(h) })
                        }
                    }
                }
            }
        }
    }
}
