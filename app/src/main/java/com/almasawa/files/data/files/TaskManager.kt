package com.almasawa.files.data.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class TaskKind { COPY, MOVE, ZIP, UNZIP }
enum class TaskStatus { RUNNING, DONE, FAILED, CANCELLED, NO_SPACE, SELF }

data class TaskState(
    val kind: TaskKind,
    val label: String,
    val done: Long,
    val total: Long,
    val bytesPerSec: Long,
    val status: TaskStatus,
)

data class Clip(val paths: List<String>, val cut: Boolean)

/** App-wide runner for long file operations (one at a time) plus the shared clipboard. */
@Singleton
class TaskManager @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<TaskState?>(null)
    val state: StateFlow<TaskState?> = _state.asStateFlow()

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    /** Increments whenever a task finishes so screens can refresh their listings. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _pending = MutableStateFlow<String?>(null)
    val pendingConflict: StateFlow<String?> = _pending.asStateFlow()

    @Volatile private var cancelFlag = false

    fun busy() = _state.value?.status == TaskStatus.RUNNING

    fun setClip(paths: List<String>, cut: Boolean) { _clip.value = Clip(paths, cut) }
    fun clearClip() { _clip.value = null }

    fun requestPaste(dstPath: String) {
        val c = _clip.value ?: return
        if (busy()) return
        val conflict = c.paths.any {
            val src = File(it)
            val t = File(dstPath, src.name)
            t.exists() && !(c.cut && t.canonicalPath == src.canonicalPath)
        }
        if (conflict) _pending.value = dstPath else paste(dstPath, ConflictPolicy.KEEP_BOTH)
    }

    fun resolveConflict(policy: ConflictPolicy) {
        val dst = _pending.value ?: return
        _pending.value = null
        paste(dst, policy)
    }

    fun dismissConflict() { _pending.value = null }

    private fun paste(dstPath: String, policy: ConflictPolicy) {
        val c = _clip.value ?: return
        val kind = if (c.cut) TaskKind.MOVE else TaskKind.COPY
        runTask(kind, File(c.paths.first()).name) { p, setTotal ->
            val dst = File(dstPath)
            val srcs = c.paths.map { File(it) }.filter { it.exists() }
            if (srcs.any { it.isDirectory && isInside(it, dst) }) throw SelfCopyException()
            val total = srcs.sumOf { treeSize(it) }
            setTotal(total)
            if (!c.cut && dst.usableSpace < total) throw NoSpaceException()
            srcs.forEach { if (c.cut) moveEntry(it, dst, policy, p) else copyEntry(it, dst, policy, p) }
            if (c.cut) _clip.value = null
        }
    }

    fun zip(paths: List<String>, out: File) {
        runTask(TaskKind.ZIP, out.name) { p, setTotal ->
            val srcs = paths.map { File(it) }.filter { it.exists() }
            setTotal(srcs.sumOf { treeSize(it) })
            try {
                zipEntries(srcs, out, p)
            } catch (e: Exception) {
                out.delete()
                throw e
            }
        }
    }

    fun unzip(zip: File, dest: File) {
        runTask(TaskKind.UNZIP, zip.name) { p, setTotal ->
            setTotal(zipTotal(zip))
            try {
                unzipTo(zip, dest, p)
            } catch (e: Exception) {
                dest.deleteRecursively()
                throw e
            }
        }
    }

    fun cancel() { cancelFlag = true }

    fun dismiss() { if (!busy()) _state.value = null }

    private fun runTask(kind: TaskKind, label: String, work: (Progress, (Long) -> Unit) -> Unit) {
        if (busy()) return
        cancelFlag = false
        _state.value = TaskState(kind, label, 0, 0, 0, TaskStatus.RUNNING)
        scope.launch {
            var done = 0L
            var total = 0L
            var last = 0L
            val start = System.nanoTime()
            fun emit(status: TaskStatus) {
                val secs = ((System.nanoTime() - start) / 1e9).coerceAtLeast(0.001)
                _state.value = TaskState(kind, label, done, total, (done / secs).toLong(), status)
            }
            val progress = Progress({ cancelFlag }) { n ->
                done += n
                val now = System.nanoTime()
                if (now - last > 150_000_000L) { last = now; emit(TaskStatus.RUNNING) }
            }
            val status = try {
                work(progress) { t -> total = t; emit(TaskStatus.RUNNING) }
                TaskStatus.DONE
            } catch (e: OpCancelled) {
                TaskStatus.CANCELLED
            } catch (e: NoSpaceException) {
                TaskStatus.NO_SPACE
            } catch (e: SelfCopyException) {
                TaskStatus.SELF
            } catch (e: Exception) {
                TaskStatus.FAILED
            }
            emit(status)
            _version.update { it + 1 }
        }
    }
}
