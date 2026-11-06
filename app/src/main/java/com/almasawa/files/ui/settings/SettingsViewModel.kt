package com.almasawa.files.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almasawa.files.data.files.SortBy
import com.almasawa.files.data.settings.AppSettings
import com.almasawa.files.data.settings.SettingsRepository
import com.almasawa.files.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun theme(m: ThemeMode) { viewModelScope.launch { repo.setTheme(m) } }
    fun showHidden(v: Boolean) { viewModelScope.launch { repo.setShowHidden(v) } }
    fun sortBy(s: SortBy) { viewModelScope.launch { repo.setSortBy(s) } }
    fun sortAsc(v: Boolean) { viewModelScope.launch { repo.setSortAsc(v) } }
}
