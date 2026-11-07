package com.almasawa.files.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almasawa.files.data.files.Category
import com.almasawa.files.data.files.CategoryStat
import com.almasawa.files.data.files.FileItem
import com.almasawa.files.data.files.FileRepository
import com.almasawa.files.data.files.StorageInfo
import com.almasawa.files.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUi(
    val storage: StorageInfo? = null,
    val stats: Map<Category, CategoryStat> = emptyMap(),
    val recent: List<FileItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: FileRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val dualPane: StateFlow<Boolean> =
        settings.flow.map { it.dualPane }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rootPath: String = repo.root.absolutePath
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val storage = runCatching { repo.storage() }.getOrNull()
            _ui.update { it.copy(storage = storage) }
            val stats = Category.entries.associateWith { repo.categoryStat(it) }
            val recent = repo.recent(10)
            _ui.update { it.copy(stats = stats, recent = recent) }
        }
    }
}
