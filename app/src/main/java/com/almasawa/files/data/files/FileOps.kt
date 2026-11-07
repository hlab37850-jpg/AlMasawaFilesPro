package com.almasawa.files.data.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class ConflictPolicy { REPLACE, KEEP_BOTH, SKIP }

class OpCancelled : Exception()
class NoSpaceException : Exception()
class SelfCopyException : Exception()

/** Reports processed bytes and lets long operations be cancelled cooperatively. */
class Progress(private val cancelled: () -> Boolean, private val sink: (Long) -> Unit) {
    fun add(n: Long) = sink(n)
    fun check() { if (cancelled()) throw OpCancelled() }
}

fun uniqueName(dir: File, name: String): String {
    if (!File(dir, name).exists()) return name
    val dot = name.lastIndexOf('.')
    var i = 1
    while (true) {
        val cand = if (dot <= 0) "$name ($i)" else name.substring(0, dot) + " ($i)" + name.substring(dot)
        if (!File(dir, cand).exists()) return cand
        i++
    }
}

fun treeSize(f: File): Long =
    if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()

/** True when [child] is [parent] itself or lives somewhere below it. */
fun isInside(parent: File, child: File): Boolean {
    val p = parent.canonicalFile.path
    val c = child.canonicalFile.path
    return c == p || c.startsWith(p + File.separator)
}

fun pump(i: InputStream, o: OutputStream, p: Progress) {
    val buf = ByteArray(64 * 1024)
    while (true) {
        p.check()
        val n = i.read(buf)
        if (n < 0) break
        o.write(buf, 0, n)
        p.add(n.toLong())
    }
}

private fun copyFile(src: File, dst: File, p: Progress) {
    try {
        src.inputStream().use { i -> dst.outputStream().use { o -> pump(i, o, p) } }
    } catch (e: Exception) {
        dst.delete()
        throw e
    }
    dst.setLastModified(src.lastModified())
}

private fun copyNew(src: File, target: File, p: Progress) {
    if (src.isDirectory) {
        if (!target.mkdirs() && !target.isDirectory) throw IOException("mkdirs")
        src.listFiles()?.forEach { copyNew(it, File(target, it.name), p) }
    } else {
        copyFile(src, target, p)
    }
}

fun copyEntry(src: File, dstDir: File, policy: ConflictPolicy, p: Progress) {
    p.check()
    var target = File(dstDir, src.name)
    if (target.exists()) {
        val same = target.canonicalPath == src.canonicalPath
        val bothDirs = src.isDirectory && target.isDirectory
        when {
            same || policy == ConflictPolicy.KEEP_BOTH -> target = File(dstDir, uniqueName(dstDir, src.name))
            bothDirs -> {
                src.listFiles()?.forEach { copyEntry(it, target, policy, p) }
                return
            }
            policy == ConflictPolicy.SKIP -> { p.add(treeSize(src)); return }
            else -> if (!target.deleteRecursively()) throw IOException("delete")
        }
    }
    copyNew(src, target, p)
}

fun moveEntry(src: File, dstDir: File, policy: ConflictPolicy, p: Progress) {
    p.check()
    var target = File(dstDir, src.name)
    if (target.exists()) {
        val same = target.canonicalPath == src.canonicalPath
        if (same) { p.add(treeSize(src)); return }
        val bothDirs = src.isDirectory && target.isDirectory
        when {
            policy == ConflictPolicy.KEEP_BOTH -> target = File(dstDir, uniqueName(dstDir, src.name))
            bothDirs -> {
                src.listFiles()?.forEach { moveEntry(it, target, policy, p) }
                if (src.list().isNullOrEmpty()) src.delete()
                return
            }
            policy == ConflictPolicy.SKIP -> { p.add(treeSize(src)); return }
            else -> if (!target.deleteRecursively()) throw IOException("delete")
        }
    }
    val size = treeSize(src)
    if (src.renameTo(target)) { p.add(size); return }
    copyNew(src, target, p)
    if (!src.deleteRecursively()) throw IOException("delete source")
}

// ---- ZIP ------------------------------------------------------------------------------------

fun zipEntries(sources: List<File>, out: File, p: Progress) {
    ZipOutputStream(out.outputStream().buffered()).use { zos ->
        sources.forEach { addToZip(zos, it, it.name, p) }
    }
}

private fun addToZip(zos: ZipOutputStream, f: File, entryName: String, p: Progress) {
    p.check()
    if (f.isDirectory) {
        val n = entryName.trimEnd('/') + "/"
        zos.putNextEntry(ZipEntry(n))
        zos.closeEntry()
        f.listFiles()?.forEach { addToZip(zos, it, n + it.name, p) }
    } else {
        val e = ZipEntry(entryName)
        e.time = f.lastModified()
        zos.putNextEntry(e)
        f.inputStream().use { pump(it, zos, p) }
        zos.closeEntry()
    }
}

fun zipTotal(zip: File): Long =
    ZipFile(zip).use { z -> z.entries().asSequence().sumOf { maxOf(it.size, 0L) } }

/** Extracts [zip] into [dest]; entries that would escape [dest] (zip-slip) abort the operation. */
fun unzipTo(zip: File, dest: File, p: Progress) {
    if (!dest.exists() && !dest.mkdirs()) throw IOException("mkdirs")
    val base = dest.canonicalPath
    ZipFile(zip).use { z ->
        for (e in z.entries().asSequence()) {
            p.check()
            val out = File(dest, e.name)
            val canon = out.canonicalPath
            if (canon != base && !canon.startsWith(base + File.separator)) throw IOException("blocked entry")
            if (e.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                z.getInputStream(e).use { i -> out.outputStream().use { o -> pump(i, o, p) } }
            }
        }
    }
}

fun fileHash(f: File, algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    f.inputStream().use { i ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = i.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
