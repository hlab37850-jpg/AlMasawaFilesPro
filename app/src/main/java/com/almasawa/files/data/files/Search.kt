package com.almasawa.files.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

enum class SearchType { ALL, IMAGES, VIDEO, AUDIO, PDF, WORD, EXCEL, APK, ZIP, LARGE, RECENT, OLD }

private val EXTENSIONS: Map<SearchType, Set<String>> = mapOf(
    SearchType.IMAGES to setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "tif", "tiff"),
    SearchType.VIDEO to setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv"),
    SearchType.AUDIO to setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus"),
    SearchType.PDF to setOf("pdf"),
    SearchType.WORD to setOf("doc", "docx", "odt", "rtf"),
    SearchType.EXCEL to setOf("xls", "xlsx", "ods", "csv"),
    SearchType.APK to setOf("apk"),
    SearchType.ZIP to setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz"),
)

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val LARGE_BYTES = 100L * 1024 * 1024

fun matchesType(item: FileItem, type: SearchType, now: Long): Boolean {
    if (type == SearchType.ALL) return true
    if (item.isDir) return false
    return when (type) {
        SearchType.LARGE -> item.size >= LARGE_BYTES
        SearchType.RECENT -> item.modified >= now - 7 * DAY_MS
        SearchType.OLD -> item.modified in 1 until (now - 365 * DAY_MS)
        else -> item.ext in (EXTENSIONS[type] ?: emptySet())
    }
}

/** Recursive name search under [root]. Runs on IO, is cancellable, and stops after [limit] hits. */
fun searchFiles(
    root: String,
    query: String,
    type: SearchType,
    showHidden: Boolean,
    limit: Int = 2000,
): Flow<FileItem> = flow {
    val q = query.trim()
    val now = System.currentTimeMillis()
    var count = 0
    val seq = File(root).walkTopDown()
        .onEnter { d ->
            (showHidden || !d.name.startsWith(".")) &&
                !(d.parentFile?.name == "Android" && (d.name == "data" || d.name == "obb"))
        }
        .onFail { _, _ -> }
    for (f in seq) {
        currentCoroutineContext().ensureActive()
        if (f.path == root) continue
        if (!showHidden && f.name.startsWith(".")) continue
        if (q.isNotEmpty() && !f.name.contains(q, ignoreCase = true)) continue
        val item = FileItem(
            path = f.absolutePath,
            name = f.name,
            isDir = f.isDirectory,
            size = if (f.isDirectory) 0 else f.length(),
            modified = f.lastModified(),
            children = 0,
        )
        if (!matchesType(item, type, now)) continue
        emit(item)
        if (++count >= limit) break
    }
}.flowOn(Dispatchers.IO)
