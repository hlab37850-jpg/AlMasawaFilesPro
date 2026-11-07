package com.almasawa.files.ui.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.almasawa.files.R
import com.almasawa.files.data.files.Category
import com.almasawa.files.data.files.FileItem
import com.almasawa.files.data.files.FileRepository
import com.almasawa.files.ui.components.FileRow
import com.almasawa.files.ui.home.categoryLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(handle: SavedStateHandle, repo: FileRepository) : ViewModel() {
    val category: Category = Category.valueOf(handle.get<String>("cat") ?: Category.IMAGES.name)
    private val _items = MutableStateFlow<List<FileItem>?>(null)
    val items: StateFlow<List<FileItem>?> = _items

    init {
        viewModelScope.launch { _items.value = repo.categoryFiles(category) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(onBack: () -> Unit, onOpenFile: (String) -> Unit, vm: CategoryViewModel = hiltViewModel()) {
    val loaded by vm.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(categoryLabel(vm.category))) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val list = loaded
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(stringResource(R.string.empty_folder), modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn {
                    items(list, key = { it.path }) { f ->
                        FileRow(f, selected = false, selecting = false, onClick = { onOpenFile(f.path) }, onLongClick = {})
                    }
                }
            }
        }
    }
}
