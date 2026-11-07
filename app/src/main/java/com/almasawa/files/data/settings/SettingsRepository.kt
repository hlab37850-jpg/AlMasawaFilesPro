package com.almasawa.files.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.almasawa.files.data.files.SortBy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    val sortBy: SortBy = SortBy.NAME,
    val sortAsc: Boolean = true,
    /** Dual pane is opt-in: OFF by default. */
    val dualPane: Boolean = false,
    val tabs: List<String> = emptyList(),
    val tabIndex: Int = 0,
    val leftPath: String? = null,
    val rightPath: String? = null,
    val searchHistory: List<String> = emptyList(),
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

private fun String?.toLines(): List<String> = if (isNullOrEmpty()) emptyList() else split('\n')

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val showHidden = booleanPreferencesKey("show_hidden")
        val sortBy = stringPreferencesKey("sort_by")
        val sortAsc = booleanPreferencesKey("sort_asc")
        val dualPane = booleanPreferencesKey("dual_pane")
        val tabs = stringPreferencesKey("tabs")
        val tabIndex = intPreferencesKey("tab_index")
        val left = stringPreferencesKey("pane_left")
        val right = stringPreferencesKey("pane_right")
        val history = stringPreferencesKey("search_history")
    }

    val flow: Flow<AppSettings> = ctx.settingsStore.data.map { p: Preferences ->
        AppSettings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            showHidden = p[Keys.showHidden] ?: false,
            sortBy = p[Keys.sortBy]?.let { runCatching { SortBy.valueOf(it) }.getOrNull() } ?: SortBy.NAME,
            sortAsc = p[Keys.sortAsc] ?: true,
            dualPane = p[Keys.dualPane] ?: false,
            tabs = p[Keys.tabs].toLines(),
            tabIndex = p[Keys.tabIndex] ?: 0,
            leftPath = p[Keys.left],
            rightPath = p[Keys.right],
            searchHistory = p[Keys.history].toLines(),
        )
    }

    suspend fun setTheme(m: ThemeMode) { ctx.settingsStore.edit { it[Keys.theme] = m.name } }
    suspend fun setShowHidden(v: Boolean) { ctx.settingsStore.edit { it[Keys.showHidden] = v } }
    suspend fun setSortBy(s: SortBy) { ctx.settingsStore.edit { it[Keys.sortBy] = s.name } }
    suspend fun setSortAsc(v: Boolean) { ctx.settingsStore.edit { it[Keys.sortAsc] = v } }
    suspend fun setDualPane(v: Boolean) { ctx.settingsStore.edit { it[Keys.dualPane] = v } }
    suspend fun setTabs(paths: List<String>, index: Int) {
        ctx.settingsStore.edit { it[Keys.tabs] = paths.joinToString("\n"); it[Keys.tabIndex] = index }
    }
    suspend fun setPanes(left: String, right: String) {
        ctx.settingsStore.edit { it[Keys.left] = left; it[Keys.right] = right }
    }
    suspend fun setSearchHistory(items: List<String>) {
        ctx.settingsStore.edit { it[Keys.history] = items.joinToString("\n") }
    }
}
