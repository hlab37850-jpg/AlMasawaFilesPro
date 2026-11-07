package com.almasawa.files.ui.components

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.almasawa.files.R
import com.almasawa.files.data.files.fileHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

@Composable
fun PropertiesDialog(path: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val f = File(path)
    val scope = rememberCoroutineScope()
    val stats by produceState<Pair<Long, Int>?>(null, path) {
        value = withContext(Dispatchers.IO) {
            if (f.isDirectory) {
                var size = 0L
                var n = 0
                f.walkTopDown().forEach { if (it.isFile) size += it.length(); n++ }
                size to (n - 1)
            } else {
                f.length() to 0
            }
        }
    }
    var hash by remember { mutableStateOf<String?>(null) }
    var hashLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Prop(stringResource(R.string.prop_path), f.absolutePath)
                Prop(
                    stringResource(R.string.prop_size),
                    stats?.let { Formatter.formatFileSize(ctx, it.first) } ?: stringResource(R.string.prop_computing),
                )
                if (f.isDirectory) {
                    Prop(stringResource(R.string.prop_items), stats?.second?.toString() ?: stringResource(R.string.prop_computing))
                }
                Prop(
                    stringResource(R.string.prop_modified),
                    DateFormat.getMediumDateFormat(ctx).format(Date(f.lastModified())) + " " +
                        DateFormat.getTimeFormat(ctx).format(Date(f.lastModified())),
                )
                if (f.isFile) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            hash = ""; hashLabel = "MD5"
                            scope.launch { hash = withContext(Dispatchers.IO) { fileHash(f, "MD5") } }
                        }) { Text("MD5") }
                        TextButton(onClick = {
                            hash = ""; hashLabel = "SHA-256"
                            scope.launch { hash = withContext(Dispatchers.IO) { fileHash(f, "SHA-256") } }
                        }) { Text("SHA-256") }
                    }
                    hash?.let { h ->
                        Prop(hashLabel, if (h.isEmpty()) stringResource(R.string.prop_computing) else h)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun Prop(label: String, value: String) {
    Column(Modifier.padding(bottom = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
