package com.almasawa.files

import com.almasawa.files.data.files.FileItem
import com.almasawa.files.data.files.SortBy
import com.almasawa.files.data.files.fileComparator
import com.almasawa.files.data.files.isValidName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOpsTest {
    private fun f(name: String, dir: Boolean = false, size: Long = 0, mod: Long = 0) =
        FileItem("/x/$name", name, dir, size, mod, 0)

    @Test fun foldersComeFirst() {
        val l = listOf(f("b.txt"), f("a", dir = true)).sortedWith(fileComparator(SortBy.NAME, true))
        assertEquals("a", l[0].name)
    }

    @Test fun foldersStayFirstWhenDescending() {
        val l = listOf(f("z.txt"), f("a", dir = true), f("b.txt")).sortedWith(fileComparator(SortBy.NAME, false))
        assertEquals(listOf("a", "z.txt", "b.txt"), l.map { it.name })
    }

    @Test fun sortBySizeDescending() {
        val l = listOf(f("s", size = 1), f("l", size = 9), f("m", size = 5))
            .sortedWith(fileComparator(SortBy.SIZE, false))
        assertEquals(listOf("l", "m", "s"), l.map { it.name })
    }

    @Test fun sortByTypeThenName() {
        val l = listOf(f("b.txt"), f("a.zip"), f("a.txt")).sortedWith(fileComparator(SortBy.TYPE, true))
        assertEquals(listOf("a.txt", "b.txt", "a.zip"), l.map { it.name })
    }

    @Test fun nameValidation() {
        assertTrue(isValidName("تقرير 1.txt"))
        assertTrue(isValidName("a b.txt"))
        assertFalse(isValidName(""))
        assertFalse(isValidName("   "))
        assertFalse(isValidName("a/b"))
        assertFalse(isValidName(".."))
        assertFalse(isValidName("x".repeat(256)))
    }
}
