package com.almasawa.files.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almasawa.files.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val MAX_BYTES = 2_000_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(path: String, onBack: () -> Unit) {
    val file = File(path)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var tf by remember { mutableStateOf(TextFieldValue("")) }
    var original by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var tooLarge by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val dirty = loaded && tf.text != original

    LaunchedEffect(path) {
        val text = withContext(Dispatchers.IO) { if (file.length() > MAX_BYTES) null else runCatching { file.readText() }.getOrNull() }
        if (text == null) tooLarge = true else { original = text; tf = TextFieldValue(text) }
        loaded = true
    }
    LaunchedEffect(notice) {
        notice?.let { snack.showSnackbar(ctx.getString(it)); notice = null }
    }
    BackHandler(enabled = dirty) { confirmLeave = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(file.name + if (dirty) " *" else "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmLeave = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { finding = !finding }) { Icon(Icons.Default.Search, stringResource(R.string.text_find)) }
                    if (file.extension.equals("json", ignoreCase = true)) {
                        IconButton(onClick = {
                            val formatted = runCatching {
                                val t = tf.text.trim()
                                if (t.startsWith("[")) JSONArray(t).toString(2) else JSONObject(t).toString(2)
                            }.getOrNull()
                            if (formatted == null) notice = R.string.text_json_invalid else tf = TextFieldValue(formatted)
                        }) { Icon(Icons.Default.DataObject, stringResource(R.string.text_format_json)) }
                    }
                    IconButton(
                        enabled = dirty,
                        onClick = {
                            val content = tf.text
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { runCatching { file.writeText(content) }.isSuccess }
                                if (ok) { original = content; notice = R.string.text_saved } else notice = R.string.msg_failed
                            }
                        },
                    ) { Icon(Icons.Default.Save, stringResource(R.string.save)) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (finding) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.text_find)) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (query.isNotEmpty()) {
                            val text = tf.text
                            var idx = text.indexOf(query, tf.selection.end, ignoreCase = true)
                            if (idx < 0) idx = text.indexOf(query, 0, ignoreCase = true)
                            if (idx >= 0) tf = tf.copy(selection = TextRange(idx, idx + query.length))
                        }
                    }) { Icon(Icons.Default.Check, null) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    tooLarge -> Text(stringResource(R.string.text_too_large), Modifier.align(Alignment.Center).padding(24.dp))
                    else -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        BasicTextField(
                            value = tf,
                            onValueChange = { tf = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp).padding(12.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.text_discard_title)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.text_discard)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
