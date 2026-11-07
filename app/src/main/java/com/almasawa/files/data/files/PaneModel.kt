package com.almasawa.files.data.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PaneUi(
    val path: String,
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val selection: Set<String> = emptySet(),
)

/** One independent folder view (path, listing, selection). Used by the browser and by each dual-pane side. */
class PaneModel(
    private val scope: CoroutineScope,
    private val repo: FileRepository,
    initialPath: String,
) {
    private val _ui = MutableStateFlow(PaneUi(initialPath))
    val ui: StateFlow<PaneUi> = _ui.asStateFlow()

    private var showHidden = false
    private var sortBy = SortBy.NAME
    private var asc = true
    private var job: Job? = null

    fun start(path: String, showHidden: Boolean, sortBy: SortBy, asc: Boolean) {
        this.showHidden = showHidden; this.sortBy = sortBy; this.asc = asc
        _ui.update { it.copy(path = path, selection = emptySet()) }
        reload()
    }

    fun configure(showHidden: Boolean, sortBy: SortBy, asc: Boolean) {
        this.showHidden = showHidden; this.sortBy = sortBy; this.asc = asc
        reload()
    }

    fun reload() {
        job?.cancel()
        job = scope.launch {
            val current = _ui.value.path
            _ui.update { it.copy(loading = true) }
            val r = repo.list(current, showHidden, sortBy, asc)
            _ui.update { s ->
                r.fold(
                    onSuccess = { s.copy(items = it, loading = false, error = false) },
                    onFailure = { s.copy(items = emptyList(), loading = false, error = true) },
                )
            }
        }
    }

    fun atRoot(path: String = _ui.value.path): Boolean =
        path == repo.root.absolutePath || File(path).parentFile == null

    fun open(path: String) {
        _ui.update { it.copy(path = path, selection = emptySet()) }
        reload()
    }

    fun up() {
        val parent = File(_ui.value.path).parentFile ?: return
        open(parent.absolutePath)
    }

    fun toggleSelect(path: String) = _ui.update {
        it.copy(selection = if (path in it.selection) it.selection - path else it.selection + path)
    }
    fun clearSelection() = _ui.update { it.copy(selection = emptySet()) }
    fun selectAll() = _ui.update { s -> s.copy(selection = s.items.map { it.path }.toSet()) }
    fun invertSelection() = _ui.update { s -> s.copy(selection = s.items.map { it.path }.toSet() - s.selection) }
}
