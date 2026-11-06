package com.almasawa.files.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val showHidden = booleanPreferencesKey("show_hidden")
        val sortBy = stringPreferencesKey("sort_by")
        val sortAsc = booleanPreferencesKey("sort_asc")
    }

    val flow: Flow<AppSettings> = ctx.settingsStore.data.map { p: Preferences ->
        AppSettings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            showHidden = p[Keys.showHidden] ?: false,
            sortBy = p[Keys.sortBy]?.let { runCatching { SortBy.valueOf(it) }.getOrNull() } ?: SortBy.NAME,
            sortAsc = p[Keys.sortAsc] ?: true,
        )
    }

    suspend fun setTheme(m: ThemeMode) { ctx.settingsStore.edit { it[Keys.theme] = m.name } }
    suspend fun setShowHidden(v: Boolean) { ctx.settingsStore.edit { it[Keys.showHidden] = v } }
    suspend fun setSortBy(s: SortBy) { ctx.settingsStore.edit { it[Keys.sortBy] = s.name } }
    suspend fun setSortAsc(v: Boolean) { ctx.settingsStore.edit { it[Keys.sortAsc] = v } }
}
