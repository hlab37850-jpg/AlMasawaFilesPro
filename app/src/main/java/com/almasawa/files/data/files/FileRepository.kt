package com.almasawa.files.data.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class FileItem(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val children: Int,
) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

enum class SortBy { NAME, SIZE, TYPE, MODIFIED }

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = total - free
}

enum class Category { IMAGES, VIDEO, AUDIO, DOCS, APK, ARCHIVES }

data class CategoryStat(val count: Int, val bytes: Long)

class FileOpException(val kind: Kind) : Exception(kind.name) {
    enum class Kind { EXISTS, INVALID, FAILED }
}

fun isValidName(name: String): Boolean {
    val n = name.trim()
    return n.isNotEmpty() && n.length <= 255 && n != "." && n != ".." &&
        n.none { it == '/' || it == '\u0000' }
}

/** Folders always come first; [asc] only affects the order inside each group. */
fun fileComparator(sort: SortBy, asc: Boolean): Comparator<FileItem> {
    val base = Comparator<FileItem> { a, b ->
        when (sort) {
            SortBy.NAME -> a.name.compareTo(b.name, ignoreCase = true)
            SortBy.SIZE -> a.size.compareTo(b.size)
            SortBy.TYPE -> {
                val c = a.ext.compareTo(b.ext)
                if (c != 0) c else a.name.compareTo(b.name, ignoreCase = true)
            }
            SortBy.MODIFIED -> a.modified.compareTo(b.modified)
        }
    }
    return Comparator { a, b ->
        when {
            a.isDir != b.isDir -> if (a.isDir) -1 else 1
            asc -> base.compare(a, b)
            else -> base.compare(b, a)
        }
    }
}

@Singleton
class FileRepository @Inject constructor(@ApplicationContext private val ctx: Context) {

    @Suppress("DEPRECATION")
    val root: File get() = Environment.getExternalStorageDirectory()

    fun storage(): StorageInfo {
        val s = StatFs(root.path)
        return StorageInfo(total = s.totalBytes, free = s.availableBytes)
    }

    suspend fun list(path: String, showHidden: Boolean, sort: SortBy, asc: Boolean): Result<List<FileItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val files = File(path).listFiles() ?: throw IOException("unreadable")
                files.asSequence()
                    .filter { showHidden || !it.name.startsWith(".") }
                    .map { f ->
                        val d = f.isDirectory
                        FileItem(
                            path = f.absolutePath,
                            name = f.name,
                            isDir = d,
                            size = if (d) 0 else f.length(),
                            modified = f.lastModified(),
                            children = if (d) (f.list()?.size ?: 0) else 0,
                        )
                    }
                    .sortedWith(fileComparator(sort, asc))
                    .toList()
            }
        }

    // ---- operations -------------------------------------------------------------------------

    private suspend fun op(block: () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching(block) }

    private fun requireValid(name: String) {
        if (!isValidName(name)) throw FileOpException(FileOpException.Kind.INVALID)
    }

    suspend fun createFolder(parent: String, name: String): Result<Unit> = op {
        requireValid(name)
        val f = File(parent, name.trim())
        if (f.exists()) throw FileOpException(FileOpException.Kind.EXISTS)
        if (!f.mkdir()) throw FileOpException(FileOpException.Kind.FAILED)
    }

    suspend fun createFile(parent: String, name: String): Result<Unit> = op {
        requireValid(name)
        val f = File(parent, name.trim())
        if (f.exists()) throw FileOpException(FileOpException.Kind.EXISTS)
        if (!f.createNewFile()) throw FileOpException(FileOpException.Kind.FAILED)
    }

    suspend fun rename(path: String, newName: String): Result<Unit> = op {
        requireValid(newName)
        val src = File(path)
        val dst = File(src.parentFile, newName.trim())
        if (dst.exists()) throw FileOpException(FileOpException.Kind.EXISTS)
        if (!src.renameTo(dst)) throw FileOpException(FileOpException.Kind.FAILED)
    }

    /** Permanent delete (the recycle bin arrives in phase 3). Fails if any item could not be removed. */
    suspend fun delete(paths: Collection<String>): Result<Unit> = op {
        val failed = paths.count { !File(it).deleteRecursively() }
        if (failed > 0) throw FileOpException(FileOpException.Kind.FAILED)
    }

    // ---- MediaStore-backed categories (real data, no samples) ---------------------------------

    private val filesUri: Uri = MediaStore.Files.getContentUri("external")

    @Suppress("DEPRECATION")
    private val dataCol = MediaStore.Files.FileColumns.DATA
    private val sizeCol = MediaStore.Files.FileColumns.SIZE
    private val mimeCol = MediaStore.Files.FileColumns.MIME_TYPE
    private val typeCol = MediaStore.Files.FileColumns.MEDIA_TYPE
    private val dateCol = MediaStore.Files.FileColumns.DATE_MODIFIED
    private val nameCol = MediaStore.Files.FileColumns.DISPLAY_NAME

    private val docMimes = listOf(
        "application/pdf", "text/plain", "text/csv", "application/json", "application/xml",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
    private val archiveMimes = listOf(
        "application/zip", "application/x-7z-compressed", "application/x-rar-compressed",
        "application/vnd.rar", "application/x-tar", "application/gzip", "application/x-gzip",
        "application/x-bzip2", "application/x-xz",
    )

    private fun mimeIn(mimes: List<String>) =
        "$mimeCol IN (${mimes.joinToString(",") { "?" }})" to mimes.toTypedArray()

    private fun selection(c: Category): Pair<String, Array<String>> = when (c) {
        Category.IMAGES -> "$typeCol=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        Category.VIDEO -> "$typeCol=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        Category.AUDIO -> "$typeCol=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
        Category.DOCS -> mimeIn(docMimes)
        Category.APK -> mimeIn(listOf("application/vnd.android.package-archive"))
        Category.ARCHIVES -> mimeIn(archiveMimes)
    }

    private fun queryFiles(sel: String, args: Array<String>, limit: Int): List<FileItem> {
        val bundle = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(dateCol))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val out = ArrayList<FileItem>()
        ctx.contentResolver.query(filesUri, arrayOf(dataCol, nameCol, sizeCol, dateCol), bundle, null)?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(0) ?: continue
                out += FileItem(
                    path = path,
                    name = c.getString(1) ?: File(path).name,
                    isDir = false,
                    size = c.getLong(2),
                    modified = c.getLong(3) * 1000,
                    children = 0,
                )
            }
        }
        return out
    }

    suspend fun categoryStat(c: Category): CategoryStat = withContext(Dispatchers.IO) {
        val (sel, args) = selection(c)
        runCatching {
            ctx.contentResolver.query(filesUri, arrayOf(sizeCol), sel, args, null)?.use { cur ->
                var n = 0
                var bytes = 0L
                while (cur.moveToNext()) { n++; bytes += cur.getLong(0) }
                CategoryStat(n, bytes)
            } ?: CategoryStat(0, 0)
        }.getOrDefault(CategoryStat(0, 0))
    }

    suspend fun categoryFiles(c: Category, limit: Int = 500): List<FileItem> = withContext(Dispatchers.IO) {
        val (sel, args) = selection(c)
        runCatching { queryFiles(sel, args, limit) }.getOrDefault(emptyList())
    }

    suspend fun recent(limit: Int = 10): List<FileItem> = withContext(Dispatchers.IO) {
        val sel = "$sizeCol>0 AND $mimeCol IS NOT NULL AND $dataCol NOT LIKE ? AND $dataCol NOT LIKE ?"
        runCatching { queryFiles(sel, arrayOf("%/.%", "%/Android/%"), limit) }.getOrDefault(emptyList())
    }
}
