package com.almasawa.files.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.BuildConfig
import com.almasawa.files.R
import com.almasawa.files.data.files.SortBy
import com.almasawa.files.data.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Section(R.string.settings_appearance)
            Radio(R.string.theme_system, s.theme == ThemeMode.SYSTEM) { vm.theme(ThemeMode.SYSTEM) }
            Radio(R.string.theme_light, s.theme == ThemeMode.LIGHT) { vm.theme(ThemeMode.LIGHT) }
            Radio(R.string.theme_dark, s.theme == ThemeMode.DARK) { vm.theme(ThemeMode.DARK) }

            Section(R.string.settings_browsing)
            SwitchRow(R.string.show_hidden, s.showHidden, vm::showHidden)
            Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Radio(R.string.sort_name, s.sortBy == SortBy.NAME) { vm.sortBy(SortBy.NAME) }
            Radio(R.string.sort_size, s.sortBy == SortBy.SIZE) { vm.sortBy(SortBy.SIZE) }
            Radio(R.string.sort_type, s.sortBy == SortBy.TYPE) { vm.sortBy(SortBy.TYPE) }
            Radio(R.string.sort_date, s.sortBy == SortBy.MODIFIED) { vm.sortBy(SortBy.MODIFIED) }
            SwitchRow(R.string.sort_asc, s.sortAsc, vm::sortAsc)

            Section(R.string.settings_about)
            Text(stringResource(R.string.about_body, BuildConfig.VERSION_NAME), modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Section(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Radio(res: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(res), Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
    }
}

@Composable
private fun SwitchRow(res: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(res), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
