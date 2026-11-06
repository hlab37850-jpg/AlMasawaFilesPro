package com.almasawa.files.ui.browser

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almasawa.files.R
import com.almasawa.files.data.db.PanelDao
import com.almasawa.files.data.db.PanelItemEntity
import com.almasawa.files.data.db.PanelWithItems
import com.almasawa.files.data.files.FileItem
import com.almasawa.files.data.files.FileOpException
import com.almasawa.files.data.files.FileRepository
import com.almasawa.files.data.settings.AppSettings
import com.almasawa.files.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BrowserUi(
    val path: String,
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val selection: Set<String> = emptySet(),
    @StringRes val message: Int? = null,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: FileRepository,
    settings: SettingsRepository,
    private val dao: PanelDao,
) : ViewModel() {

    val rootPath: String = repo.root.absolutePath
    private val _ui = MutableStateFlow(
        BrowserUi(path = handle.get<String>("path")?.takeIf { it.isNotBlank() } ?: rootPath),
    )
    val ui: StateFlow<BrowserUi> = _ui.asStateFlow()

    val panels: StateFlow<List<PanelWithItems>> =
        dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var cfg = AppSettings()
    private var job: Job? = null

    init {
        viewModelScope.launch { settings.flow.collect { cfg = it; reload() } }
    }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            val current = _ui.value.path
            _ui.update { it.copy(loading = true) }
            val r = repo.list(current, cfg.showHidden, cfg.sortBy, cfg.sortAsc)
            _ui.update { s ->
                r.fold(
                    onSuccess = { s.copy(items = it, loading = false, error = false) },
                    onFailure = { s.copy(items = emptyList(), loading = false, error = true) },
                )
            }
        }
    }

    fun atRoot(path: String = _ui.value.path) = path == rootPath || File(path).parentFile == null

    fun open(path: String) {
        _ui.update { it.copy(path = path, selection = emptySet()) }
        reload()
    }

    fun up() {
        val parent = File(_ui.value.path).parentFile ?: return
        open(parent.absolutePath)
    }

    // ---- selection ----------------------------------------------------------------------
    fun toggleSelect(path: String) = _ui.update {
        it.copy(selection = if (path in it.selection) it.selection - path else it.selection + path)
    }
    fun clearSelection() = _ui.update { it.copy(selection = emptySet()) }
    fun selectAll() = _ui.update { s -> s.copy(selection = s.items.map { it.path }.toSet()) }
    fun invertSelection() = _ui.update { s ->
        s.copy(selection = s.items.map { it.path }.toSet() - s.selection)
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
    fun notify(@StringRes id: Int) = _ui.update { it.copy(message = id) }

    // ---- file operations ----------------------------------------------------------------
    private fun errRes(e: Throwable): Int = when ((e as? FileOpException)?.kind) {
        FileOpException.Kind.EXISTS -> R.string.msg_exists
        FileOpException.Kind.INVALID -> R.string.msg_invalid_name
        else -> R.string.msg_failed
    }

    private fun run(@StringRes okMsg: Int, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block().fold(
                onSuccess = { _ui.update { it.copy(message = okMsg, selection = emptySet()) }; reload() },
                onFailure = { e -> _ui.update { it.copy(message = errRes(e)) }; reload() },
            )
        }
    }

    fun createFolder(name: String) = run(R.string.msg_created) { repo.createFolder(_ui.value.path, name) }
    fun createFile(name: String) = run(R.string.msg_created) { repo.createFile(_ui.value.path, name) }
    fun renameSelected(newName: String) {
        val p = _ui.value.selection.singleOrNull() ?: return
        run(R.string.msg_renamed) { repo.rename(p, newName) }
    }
    fun deleteSelected() {
        val paths = _ui.value.selection.toList()
        run(R.string.msg_deleted) { repo.delete(paths) }
    }

    fun addSelectionToPanel(panelId: Long) {
        val chosen = _ui.value.items.filter { it.path in _ui.value.selection }
        viewModelScope.launch {
            chosen.forEach { f ->
                if (dao.countItem(panelId, f.path) == 0) {
                    dao.addItem(PanelItemEntity(panelId = panelId, path = f.path, label = f.name, isDir = f.isDir))
                }
            }
            _ui.update { it.copy(message = R.string.msg_added, selection = emptySet()) }
        }
    }
}
