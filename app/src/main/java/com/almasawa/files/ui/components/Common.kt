package com.almasawa.files.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.almasawa.files.R
import com.almasawa.files.data.files.FileItem
import com.almasawa.files.util.Access
import java.util.Date

fun iconFor(item: FileItem): ImageVector {
    if (item.isDir) return Icons.Default.Folder
    return when (item.ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov", "3gp", "webm" -> Icons.Default.Movie
        "mp3", "m4a", "wav", "flac", "ogg", "aac" -> Icons.Default.MusicNote
        "apk" -> Icons.Default.Android
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        else -> Icons.Default.Description
    }
}

val PanelIcons: Map<String, ImageVector> = linkedMapOf(
    "folder" to Icons.Default.Folder,
    "work" to Icons.Default.Work,
    "download" to Icons.Default.Download,
    "doc" to Icons.Default.Description,
    "photo" to Icons.Default.Image,
    "video" to Icons.Default.Movie,
    "music" to Icons.Default.MusicNote,
    "star" to Icons.Default.Star,
    "tool" to Icons.Default.Build,
)

val PanelColors: List<Long> = listOf(
    0xFF0E7C7B, 0xFFC8963E, 0xFF3A6EA5, 0xFF8E44AD, 0xFFC0392B, 0xFF2E8B57,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    subtitle: String? = null,
) {
    val ctx = LocalContext.current
    val date = remember(item.modified) { DateFormat.getMediumDateFormat(ctx).format(Date(item.modified)) }
    val sub = if (item.isDir) stringResource(R.string.files_count, item.children) + " · " + date
    else Formatter.formatShortFileSize(ctx, item.size) + " · " + date
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        leadingContent = { Icon(iconFor(item), contentDescription = null) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle ?: sub, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (selecting) {
            { Checkbox(checked = selected, onCheckedChange = null) }
        } else null,
    )
}

@Composable
fun NameDialog(titleRes: Int, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Shows [content] only when storage access is granted; otherwise explains and requests it. */
@Composable
fun AccessGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(Access.granted(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ok = Access.granted(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = Access.granted(ctx)
    }
    if (ok) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.perm_body), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    launcher.launch(
                        arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ),
                    )
                }
            }) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}
