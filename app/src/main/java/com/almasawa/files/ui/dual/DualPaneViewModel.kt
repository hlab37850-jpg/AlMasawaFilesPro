package com.almasawa.files.ui.dual

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almasawa.files.R
import com.almasawa.files.data.files.FileOpException
import com.almasawa.files.data.files.FileRepository
import com.almasawa.files.data.files.PaneModel
import com.almasawa.files.data.files.TaskManager
import com.almasawa.files.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DualPaneViewModel @Inject constructor(
    private val repo: FileRepository,
    private val settings: SettingsRepository,
    val tasks: TaskManager,
) : ViewModel() {

    val left = PaneModel(viewModelScope, repo, repo.root.absolutePath)
    val right = PaneModel(viewModelScope, repo, repo.root.absolutePath)

    private val _active = MutableStateFlow(0)
    val active: StateFlow<Int> = _active.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    private var started = false

    init {
        viewModelScope.launch {
            val s = settings.flow.first()
            val root = repo.root.absolutePath
            val lp = s.leftPath?.takeIf { File(it).isDirectory } ?: root
            val rp = s.rightPath?.takeIf { File(it).isDirectory } ?: root
            left.start(lp, s.showHidden, s.sortBy, s.sortAsc)
            right.start(rp, s.showHidden, s.sortBy, s.sortAsc)
            started = true
            settings.flow.map { Triple(it.showHidden, it.sortBy, it.sortAsc) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    left.configure(it.first, it.second, it.third)
                    right.configure(it.first, it.second, it.third)
                }
        }
        viewModelScope.launch {
            combine(left.ui.map { it.path }, right.ui.map { it.path }) { l, r -> l to r }
                .distinctUntilChanged()
                .collect { if (started) settings.setPanes(it.first, it.second) }
        }
        viewModelScope.launch {
            tasks.version.drop(1).collect { left.reload(); right.reload() }
        }
    }

    fun setActive(i: Int) { _active.value = i }
    private fun pane(i: Int) = if (i == 0) left else right
    fun messageShown() { _message.value = null }

    fun copyToOther(cut: Boolean) {
        val a = pane(_active.value)
        val b = pane(1 - _active.value)
        val sel = a.ui.value.selection.toList()
        if (sel.isEmpty()) return
        tasks.setClip(sel, cut)
        a.clearSelection()
        tasks.requestPaste(b.ui.value.path)
    }

    fun swap() {
        val l = left.ui.value.path
        val r = right.ui.value.path
        left.open(r)
        right.open(l)
    }

    fun createFolder(name: String) = act(R.string.msg_created) {
        repo.createFolder(pane(_active.value).ui.value.path, name)
    }

    fun deleteSelected() {
        val paths = pane(_active.value).ui.value.selection.toList()
        act(R.string.msg_deleted) { repo.delete(paths) }
    }

    private fun act(@StringRes ok: Int, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val p = pane(_active.value)
            block().fold(
                onSuccess = { _message.value = ok; p.clearSelection() },
                onFailure = { e ->
                    _message.value = when ((e as? FileOpException)?.kind) {
                        FileOpException.Kind.EXISTS -> R.string.msg_exists
                        FileOpException.Kind.INVALID -> R.string.msg_invalid_name
                        else -> R.string.msg_failed
                    }
                },
            )
            p.reload()
        }
    }
}
