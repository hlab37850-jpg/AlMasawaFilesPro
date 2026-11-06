package com.almasawa.files

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.data.settings.AppSettings
import com.almasawa.files.data.settings.SettingsRepository
import com.almasawa.files.ui.components.AccessGate
import com.almasawa.files.ui.nav.AppNav
import com.almasawa.files.ui.theme.AlMasawaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val cfg by settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
            AlMasawaTheme(cfg.theme) {
                AccessGate { AppNav() }
            }
        }
    }
}
