package com.almasawa.files.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object Access {
    fun granted(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

/** Opens a file with another app through the system chooser. Returns false if nothing can open it. */
fun openFile(ctx: Context, path: String): Boolean = try {
    val f = File(path)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
    true
} catch (e: Exception) {
    false
}

enum class ViewerKind { IMAGE, TEXT, OTHER }

val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
private val TEXT_EXTS = setOf(
    "txt", "md", "json", "csv", "xml", "html", "htm", "log", "kt", "java", "js", "css",
    "yml", "yaml", "ini", "properties", "sh", "gradle", "kts", "conf", "cfg", "sql",
)

fun viewerKind(path: String): ViewerKind {
    val ext = File(path).extension.lowercase()
    return when {
        ext in IMAGE_EXTS -> ViewerKind.IMAGE
        ext in TEXT_EXTS -> ViewerKind.TEXT
        else -> ViewerKind.OTHER
    }
}

/** Shares files (not folders) through the system chooser. */
fun shareFiles(ctx: Context, paths: List<String>): Boolean = try {
    val files = paths.map { File(it) }.filter { it.isFile }
    if (files.isEmpty()) {
        false
    } else {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", it) })
        val intent = if (uris.size == 1) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(files[0].extension.lowercase()) ?: "*/*"
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(mime)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }
} catch (e: Exception) {
    false
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("path", text))
}
