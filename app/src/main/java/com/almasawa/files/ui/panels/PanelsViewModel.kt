package com.almasawa.files.ui.panels

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almasawa.files.R
import com.almasawa.files.data.db.PanelDao
import com.almasawa.files.data.db.PanelEntity
import com.almasawa.files.data.db.PanelItemEntity
import com.almasawa.files.data.db.PanelWithItems
import com.almasawa.files.ui.components.PanelColors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PanelsViewModel @Inject constructor(
    private val dao: PanelDao,
    @ApplicationContext private val app: Context,
) : ViewModel() {

    /** null while the first database read is in flight. */
    val panels: StateFlow<List<PanelWithItems>?> =
        dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun create(name: String, icon: String, color: Long) {
        viewModelScope.launch {
            dao.insert(PanelEntity(name = name.trim(), icon = icon, color = color, position = panels.value?.size ?: 0))
        }
    }

    fun update(p: PanelEntity) { viewModelScope.launch { dao.update(p) } }
    fun delete(id: Long) { viewModelScope.launch { dao.delete(id) } }
    fun removeItem(id: Long) { viewModelScope.launch { dao.removeItem(id) } }

    fun addDefaults() {
        viewModelScope.launch {
            val specs = listOf(
                Triple(R.string.dp_downloads, "download", Environment.DIRECTORY_DOWNLOADS),
                Triple(R.string.dp_documents, "doc", Environment.DIRECTORY_DOCUMENTS),
                Triple(R.string.dp_pictures, "photo", Environment.DIRECTORY_PICTURES),
            )
            val base = panels.value?.size ?: 0
            specs.forEachIndexed { i, (res, icon, dir) ->
                @Suppress("DEPRECATION")
                val folder = Environment.getExternalStoragePublicDirectory(dir)
                val id = dao.insert(
                    PanelEntity(name = app.getString(res), icon = icon, color = PanelColors[i], position = base + i),
                )
                dao.addItem(PanelItemEntity(panelId = id, path = folder.absolutePath, label = folder.name, isDir = true))
            }
        }
    }
}
