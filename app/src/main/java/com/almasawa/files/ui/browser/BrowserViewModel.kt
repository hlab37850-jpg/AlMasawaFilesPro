package com.almasawa.files.ui.browser

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almasawa.files.R
import com.almasawa.files.data.db.PanelDao
import com.almasawa.files.data.db.PanelItemEntity
import com.almasawa.files.data.db.PanelWithItems
import com.almasawa.files.data.files.FileOpException
import com.almasawa.files.data.files.FileRepository
import com.almasawa.files.data.files.PaneModel
import com.almasawa.files.data.files.TaskManager
import com.almasawa.files.data.files.uniqueName
import com.almasawa.files.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TabsUi(val paths: List<String>, val index: Int)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: FileRepository,
    private val settings: SettingsRepository,
    private val dao: PanelDao,
    val tasks: TaskManager,
) : ViewModel() {

    val rootPath: String = repo.root.absolutePath
    val pane = PaneModel(viewModelScope, repo, rootPath)

    private val _tabs = MutableStateFlow(TabsUi(listOf(rootPath), 0))
    val tabs: StateFlow<TabsUi> = _tabs.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    val panels: StateFlow<List<PanelWithItems>> =
        dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dualPane: StateFlow<Boolean> =
        settings.flow.map { it.dualPane }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var started = false

    init {
        viewModelScope.launch {
            val s = settings.flow.first()
            val navPath = handle.get<String>("path")?.takeIf { it.isNotBlank() }
            var paths = s.tabs.filter { File(it).isDirectory }.ifEmpty { listOf(rootPath) }
            var idx = s.tabIndex.coerceIn(0, paths.lastIndex)
            if (navPath != null) {
                val existing = paths.indexOf(navPath)
                if (existing >= 0) idx = existing else { paths = paths + navPath; idx = paths.lastIndex }
            }
            _tabs.value = TabsUi(paths, idx)
            pane.start(paths[idx], s.showHidden, s.sortBy, s.sortAsc)
            started = true
            settings.flow.map { Triple(it.showHidden, it.sortBy, it.sortAsc) }
                .distinctUntilChanged()
                .drop(1)
                .collect { pane.configure(it.first, it.second, it.third) }
        }
        viewModelScope.launch {
            pane.ui.map { it.path }.distinctUntilChanged().collect { p -> if (started) onPathChanged(p) }
        }
        viewModelScope.launch { tasks.version.drop(1).collect { pane.reload() } }
    }

    // ---- tabs --------------------------------------------------------------------------------
    private fun onPathChanged(p: String) {
        val t = _tabs.value
        if (t.paths.getOrNull(t.index) == p) return
        val l = t.paths.toMutableList()
        l[t.index] = p
        _tabs.value = t.copy(paths = l)
        persistTabs()
    }

    private fun persistTabs() {
        val t = _tabs.value
        viewModelScope.launch { settings.setTabs(t.paths, t.index) }
    }

    fun newTab() {
        val l = _tabs.value.paths + pane.ui.value.path
        _tabs.value = TabsUi(l, l.lastIndex)
        persistTabs()
    }

    fun switchTab(i: Int) {
        val t = _tabs.value
        if (i !in t.paths.indices || i == t.index) return
        _tabs.value = t.copy(index = i)
        pane.open(t.paths[i])
        persistTabs()
    }

    fun closeTab(i: Int) {
        val t = _tabs.value
        if (t.paths.size <= 1 || i !in t.paths.indices) return
        val l = t.paths.toMutableList().also { it.removeAt(i) }
        val newIdx = when {
            i < t.index -> t.index - 1
            i == t.index -> minOf(i, l.lastIndex)
            else -> t.index
        }
        _tabs.value = TabsUi(l, newIdx)
        if (i == t.index) pane.open(l[newIdx])
        persistTabs()
    }

    // ---- messages ----------------------------------------------------------------------------
    fun messageShown() { _message.value = null }
    fun notify(@StringRes id: Int) { _message.value = id }

    private fun errRes(e: Throwable): Int = when ((e as? FileOpException)?.kind) {
        FileOpException.Kind.EXISTS -> R.string.msg_exists
        FileOpException.Kind.INVALID -> R.string.msg_invalid_name
        else -> R.string.msg_failed
    }

    private fun run(@StringRes okMsg: Int, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block().fold(
                onSuccess = { _message.value = okMsg; pane.clearSelection() },
                onFailure = { e -> _message.value = errRes(e) },
            )
            pane.reload()
        }
    }

    // ---- file operations -----------------------------------------------------------------------
    fun createFolder(name: String) = run(R.string.msg_created) { repo.createFolder(pane.ui.value.path, name) }
    fun createFile(name: String) = run(R.string.msg_created) { repo.createFile(pane.ui.value.path, name) }
    fun renameSelected(newName: String) {
        val p = pane.ui.value.selection.singleOrNull() ?: return
        run(R.string.msg_renamed) { repo.rename(p, newName) }
    }
    fun deleteSelected() {
        val paths = pane.ui.value.selection.toList()
        run(R.string.msg_deleted) { repo.delete(paths) }
    }

    fun copySelection(cut: Boolean) {
        val sel = pane.ui.value.selection.toList()
        if (sel.isEmpty()) return
        tasks.setClip(sel, cut)
        pane.clearSelection()
        _message.value = if (cut) R.string.msg_clip_cut else R.string.msg_clip_copy
    }

    fun paste() = tasks.requestPaste(pane.ui.value.path)

    fun zipSelected() {
        val sel = pane.ui.value.selection.toList()
        if (sel.isEmpty()) return
        val dir = File(pane.ui.value.path)
        val base = if (sel.size == 1) File(sel[0]).name.substringBeforeLast('.', File(sel[0]).name) + ".zip" else "archive.zip"
        tasks.zip(sel, File(dir, uniqueName(dir, base)))
        pane.clearSelection()
    }

    fun unzipSelected() {
        val zip = pane.ui.value.selection.singleOrNull()?.let { File(it) } ?: return
        val dir = File(pane.ui.value.path)
        tasks.unzip(zip, File(dir, uniqueName(dir, zip.nameWithoutExtension)))
        pane.clearSelection()
    }

    fun addSelectionToPanel(panelId: Long) {
        val ui = pane.ui.value
        val chosen = ui.items.filter { it.path in ui.selection }
        viewModelScope.launch {
            chosen.forEach { f ->
                if (dao.countItem(panelId, f.path) == 0) {
                    dao.addItem(PanelItemEntity(panelId = panelId, path = f.path, label = f.name, isDir = f.isDir))
                }
            }
            _message.value = R.string.msg_added
            pane.clearSelection()
        }
    }
}
