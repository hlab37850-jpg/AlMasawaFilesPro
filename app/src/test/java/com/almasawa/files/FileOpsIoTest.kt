package com.almasawa.files

import com.almasawa.files.data.files.ConflictPolicy
import com.almasawa.files.data.files.OpCancelled
import com.almasawa.files.data.files.Progress
import com.almasawa.files.data.files.copyEntry
import com.almasawa.files.data.files.fileHash
import com.almasawa.files.data.files.isInside
import com.almasawa.files.data.files.moveEntry
import com.almasawa.files.data.files.treeSize
import com.almasawa.files.data.files.uniqueName
import com.almasawa.files.data.files.unzipTo
import com.almasawa.files.data.files.zipEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileOpsIoTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun progress(bytes: LongArray = LongArray(1)) = Progress({ false }) { bytes[0] += it }

    private fun file(dir: File, name: String, text: String): File =
        File(dir, name).also { it.parentFile?.mkdirs(); it.writeText(text) }

    @Test fun uniqueNameAddsCounter() {
        val d = tmp.newFolder()
        file(d, "a.txt", "x")
        assertEquals("a (1).txt", uniqueName(d, "a.txt"))
        file(d, "a (1).txt", "y")
        assertEquals("a (2).txt", uniqueName(d, "a.txt"))
        assertEquals("fresh.txt", uniqueName(d, "fresh.txt"))
        file(d, "noext", "z")
        assertEquals("noext (1)", uniqueName(d, "noext"))
    }

    @Test fun copyFileReportsBytesAndKeepsSource() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        val f = file(src, "a.txt", "hello")
        val bytes = LongArray(1)
        copyEntry(f, dst, ConflictPolicy.KEEP_BOTH, progress(bytes))
        assertEquals("hello", File(dst, "a.txt").readText())
        assertTrue(f.exists())
        assertEquals(5L, bytes[0])
    }

    @Test fun copyKeepBothRenamesConflict() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        val f = file(src, "a.txt", "new")
        file(dst, "a.txt", "old")
        copyEntry(f, dst, ConflictPolicy.KEEP_BOTH, progress())
        assertEquals("old", File(dst, "a.txt").readText())
        assertEquals("new", File(dst, "a (1).txt").readText())
    }

    @Test fun copyReplaceOverwrites() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        val f = file(src, "a.txt", "new")
        file(dst, "a.txt", "old")
        copyEntry(f, dst, ConflictPolicy.REPLACE, progress())
        assertEquals("new", File(dst, "a.txt").readText())
    }

    @Test fun copySkipKeepsDestination() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        val f = file(src, "a.txt", "new")
        file(dst, "a.txt", "old")
        copyEntry(f, dst, ConflictPolicy.SKIP, progress())
        assertEquals("old", File(dst, "a.txt").readText())
    }

    @Test fun copyIntoSameFolderNeverDestroysSource() {
        val d = tmp.newFolder()
        val f = file(d, "a.txt", "data")
        copyEntry(f, d, ConflictPolicy.REPLACE, progress())
        assertEquals("data", f.readText())
        assertEquals("data", File(d, "a (1).txt").readText())
    }

    @Test fun copyFolderRecursively() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        file(src, "dir/sub/b.txt", "bb")
        file(src, "dir/a.txt", "a")
        copyEntry(File(src, "dir"), dst, ConflictPolicy.KEEP_BOTH, progress())
        assertEquals("bb", File(dst, "dir/sub/b.txt").readText())
        assertEquals("a", File(dst, "dir/a.txt").readText())
        assertEquals(3L, treeSize(File(dst, "dir")))
    }

    @Test fun moveRemovesSource() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        val f = file(src, "a.txt", "m")
        moveEntry(f, dst, ConflictPolicy.KEEP_BOTH, progress())
        assertFalse(f.exists())
        assertEquals("m", File(dst, "a.txt").readText())
    }

    @Test fun moveSkipKeepsSourceWhenDestinationExists() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        val f = file(src, "a.txt", "src")
        file(dst, "a.txt", "dst")
        moveEntry(f, dst, ConflictPolicy.SKIP, progress())
        assertTrue(f.exists())
        assertEquals("dst", File(dst, "a.txt").readText())
    }

    @Test fun moveMergesFoldersWithoutLosingSkippedFiles() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        file(src, "d/keep.txt", "src-keep")
        file(src, "d/new.txt", "src-new")
        file(dst, "d/keep.txt", "dst-keep")
        moveEntry(File(src, "d"), dst, ConflictPolicy.SKIP, progress())
        assertEquals("dst-keep", File(dst, "d/keep.txt").readText())
        assertEquals("src-new", File(dst, "d/new.txt").readText())
        assertEquals("src-keep", File(src, "d/keep.txt").readText()) // skipped file must survive
        assertFalse(File(src, "d/new.txt").exists())
    }

    @Test fun cancelledOperationThrows() {
        val src = tmp.newFolder(); val dst = tmp.newFolder()
        val f = file(src, "a.txt", "x")
        try {
            copyEntry(f, dst, ConflictPolicy.KEEP_BOTH, Progress({ true }) {})
            fail("expected OpCancelled")
        } catch (e: OpCancelled) {
            assertFalse(File(dst, "a.txt").exists())
        }
    }

    @Test fun folderCannotBeCopiedIntoItself() {
        val d = tmp.newFolder()
        val sub = File(d, "sub").also { it.mkdirs() }
        assertTrue(isInside(d, sub))
        assertTrue(isInside(d, d))
        assertFalse(isInside(sub, d))
    }

    @Test fun zipRoundTrip() {
        val src = tmp.newFolder(); val out = tmp.newFolder(); val dest = tmp.newFolder()
        file(src, "docs/a.txt", "alpha")
        file(src, "docs/inner/b.txt", "beta")
        file(src, "root.txt", "root")
        val zip = File(out, "t.zip")
        zipEntries(listOf(File(src, "docs"), File(src, "root.txt")), zip, progress())
        unzipTo(zip, dest, progress())
        assertEquals("alpha", File(dest, "docs/a.txt").readText())
        assertEquals("beta", File(dest, "docs/inner/b.txt").readText())
        assertEquals("root", File(dest, "root.txt").readText())
    }

    @Test fun zipSlipIsRejected() {
        val out = tmp.newFolder(); val dest = File(tmp.newFolder(), "dest")
        val zip = File(out, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("../evil.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        try {
            unzipTo(zip, dest, progress())
            fail("expected IOException")
        } catch (e: IOException) {
            assertFalse(File(dest.parentFile, "evil.txt").exists())
        }
    }

    @Test fun hashes() {
        val f = file(tmp.newFolder(), "abc.txt", "abc")
        assertEquals("900150983cd24fb0d6963f7d28e17f72", fileHash(f, "MD5"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", fileHash(f, "SHA-256"))
    }
}
