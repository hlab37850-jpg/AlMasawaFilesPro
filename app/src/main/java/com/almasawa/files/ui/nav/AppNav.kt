package com.almasawa.files.ui.nav

import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.almasawa.files.R
import com.almasawa.files.ui.browser.BrowserScreen
import com.almasawa.files.ui.category.CategoryScreen
import com.almasawa.files.ui.components.TaskOverlay
import com.almasawa.files.ui.dual.DualPaneScreen
import com.almasawa.files.ui.home.HomeScreen
import com.almasawa.files.ui.panels.PanelsScreen
import com.almasawa.files.ui.search.SearchScreen
import com.almasawa.files.ui.settings.SettingsScreen
import com.almasawa.files.ui.viewer.ImageViewerScreen
import com.almasawa.files.ui.viewer.TextEditorScreen
import com.almasawa.files.util.ViewerKind
import com.almasawa.files.util.openFile
import com.almasawa.files.util.viewerKind

private data class Tab(val route: String, val pattern: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "home", R.string.nav_home, Icons.Default.Home),
    Tab("browser", "browser?path={path}", R.string.nav_files, Icons.Default.Folder),
    Tab("panels", "panels", R.string.nav_panels, Icons.Default.Dashboard),
    Tab("settings", "settings", R.string.nav_settings, Icons.Default.Settings),
)

private fun pathArg() = listOf(
    navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ctx = LocalContext.current

    val openFolder: (String) -> Unit = { p -> nav.navigate("browser?path=${Uri.encode(p)}") }
    val openFileCb: (String) -> Unit = { p ->
        when (viewerKind(p)) {
            ViewerKind.IMAGE -> nav.navigate("image?path=${Uri.encode(p)}")
            ViewerKind.TEXT -> nav.navigate("text?path=${Uri.encode(p)}")
            ViewerKind.OTHER -> if (!openFile(ctx, p)) Toast.makeText(ctx, R.string.msg_no_app, Toast.LENGTH_SHORT).show()
        }
    }
    val openSearch: (String) -> Unit = { p -> nav.navigate("search?path=${Uri.encode(p)}") }
    val openDual: () -> Unit = { nav.navigate("dual") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                TaskOverlay()
                if (tabs.any { it.pattern == route }) {
                    NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.pattern,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label)) },
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    onOpenFolder = openFolder,
                    onOpenCategory = { c -> nav.navigate("category/${c.name}") },
                    onOpenFile = openFileCb,
                    onSearch = openSearch,
                    onOpenDual = openDual,
                )
            }
            composable("browser?path={path}", arguments = pathArg()) {
                BrowserScreen(onOpenFile = openFileCb, onSearch = openSearch, onOpenDual = openDual)
            }
            composable("panels") { PanelsScreen(onOpenFolder = openFolder, onOpenFile = openFileCb) }
            composable("settings") { SettingsScreen() }
            composable("category/{cat}", arguments = listOf(navArgument("cat") { type = NavType.StringType })) {
                CategoryScreen(onBack = { nav.popBackStack() }, onOpenFile = openFileCb)
            }
            composable("search?path={path}", arguments = pathArg()) {
                SearchScreen(onBack = { nav.popBackStack() }, onOpenFolder = openFolder, onOpenFile = openFileCb)
            }
            composable("dual") { DualPaneScreen(onBack = { nav.popBackStack() }, onOpenFile = openFileCb) }
            composable("image?path={path}", arguments = pathArg()) { e ->
                ImageViewerScreen(e.arguments?.getString("path") ?: "", onBack = { nav.popBackStack() })
            }
            composable("text?path={path}", arguments = pathArg()) { e ->
                TextEditorScreen(e.arguments?.getString("path") ?: "", onBack = { nav.popBackStack() })
            }
        }
    }
}
