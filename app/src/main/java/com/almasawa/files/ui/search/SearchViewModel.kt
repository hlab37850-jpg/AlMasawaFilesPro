package com.almasawa.files.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almasawa.files.data.files.FileItem
import com.almasawa.files.data.files.FileRepository
import com.almasawa.files.data.files.SearchType
import com.almasawa.files.data.files.searchFiles
import com.almasawa.files.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUi(
    val query: String = "",
    val type: SearchType = SearchType.ALL,
    val results: List<FileItem> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    handle: SavedStateHandle,
    repo: FileRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val scopePath: String = handle.get<String>("path")?.takeIf { it.isNotBlank() } ?: repo.root.absolutePath

    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()

    val history: StateFlow<List<String>> =
        settings.flow.map { it.searchHistory }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var job: Job? = null

    fun setQuery(q: String) { _ui.update { it.copy(query = q) } }

    fun setType(t: SearchType) {
        _ui.update { it.copy(type = t) }
        run()
    }

    fun run() {
        job?.cancel()
        val s = _ui.value
        if (s.query.isBlank() && s.type == SearchType.ALL) {
            _ui.update { it.copy(results = emptyList(), searching = false, searched = false) }
            return
        }
        job = viewModelScope.launch {
            _ui.update { it.copy(searching = true, searched = true, results = emptyList()) }
            if (s.query.isNotBlank()) rememberQuery(s.query.trim())
            val showHidden = settings.flow.first().showHidden
            val acc = ArrayList<FileItem>()
            try {
                searchFiles(scopePath, s.query, s.type, showHidden).collect {
                    acc += it
                    if (acc.size <= 20 || acc.size % 50 == 0) _ui.update { u -> u.copy(results = acc.toList()) }
                }
            } finally {
                _ui.update { u -> u.copy(results = acc.toList(), searching = false) }
            }
        }
    }

    private suspend fun rememberQuery(q: String) {
        val cur = settings.flow.first().searchHistory
        settings.setSearchHistory((listOf(q) + cur.filter { !it.equals(q, ignoreCase = true) }).take(10))
    }
}
