package com.almasawa.files.ui.components

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almasawa.files.R
import com.almasawa.files.data.files.ConflictPolicy
import com.almasawa.files.data.files.TaskKind
import com.almasawa.files.data.files.TaskManager
import com.almasawa.files.data.files.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(val tasks: TaskManager) : ViewModel()

private fun kindRes(k: TaskKind) = when (k) {
    TaskKind.COPY -> R.string.task_copy
    TaskKind.MOVE -> R.string.task_move
    TaskKind.ZIP -> R.string.task_zip
    TaskKind.UNZIP -> R.string.task_unzip
}

private fun statusRes(s: TaskStatus) = when (s) {
    TaskStatus.DONE -> R.string.task_done
    TaskStatus.FAILED -> R.string.task_failed
    TaskStatus.CANCELLED -> R.string.task_cancelled
    TaskStatus.NO_SPACE -> R.string.task_no_space
    TaskStatus.SELF -> R.string.task_self
    TaskStatus.RUNNING -> R.string.task_done
}

/** Progress card (speed, ETA, cancel) and the conflict dialog shared by every screen. */
@Composable
fun TaskOverlay(vm: TaskViewModel = hiltViewModel()) {
    val st by vm.tasks.state.collectAsStateWithLifecycle()
    val pending by vm.tasks.pendingConflict.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(st?.status) {
        if (st?.status == TaskStatus.DONE) {
            delay(2500)
            vm.tasks.dismiss()
        }
    }

    val s = st
    if (s != null) {
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.status == TaskStatus.RUNNING) {
                    Text(
                        stringResource(kindRes(s.kind)) + " · " + s.label,
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { (s.done.toFloat() / s.total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val remaining = if (s.bytesPerSec > 0) (s.total - s.done) / s.bytesPerSec else 0L
                        Text(
                            stringResource(
                                R.string.task_progress,
                                Formatter.formatShortFileSize(ctx, s.done),
                                Formatter.formatShortFileSize(ctx, s.total),
                            ) + " · " + stringResource(R.string.task_speed, Formatter.formatShortFileSize(ctx, s.bytesPerSec)) +
                                (if (s.bytesPerSec > 0) " · " + stringResource(R.string.task_eta, DateUtils.formatElapsedTime(remaining)) else ""),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.tasks.cancel() }) { Text(stringResource(R.string.cancel)) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(statusRes(s.status)) + " · " + s.label,
                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { vm.tasks.dismiss() }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { vm.tasks.dismissConflict() },
            title = { Text(stringResource(R.string.conflict_title)) },
            text = { Text(stringResource(R.string.conflict_body)) },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.tasks.resolveConflict(ConflictPolicy.REPLACE) }) { Text(stringResource(R.string.conflict_replace)) }
                    TextButton(onClick = { vm.tasks.resolveConflict(ConflictPolicy.KEEP_BOTH) }) { Text(stringResource(R.string.conflict_keep)) }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.tasks.resolveConflict(ConflictPolicy.SKIP) }) { Text(stringResource(R.string.conflict_skip)) }
                    TextButton(onClick = { vm.tasks.dismissConflict() }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}
