package com.xiaomanjun.sleepdownschedule.core.web

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun untrustedNamesStayInsideTheDownloadDirectory() {
        val directory = temporary.newFolder("downloads")
        val name = downloadDispositionFileName("attachment; filename*=UTF-8''..%2F..%2Foutside.pdf")!!
        val target = writeLegacyDownload(directory, name) { it.write(byteArrayOf(1, 2)) }
        assertEquals(directory.canonicalFile, target.parentFile.canonicalFile)
        assertEquals("outside.pdf", target.name)
        assertFalse(File(temporary.root, "outside.pdf").exists())
    }

    @Test fun existingDownloadIsNeverOverwritten() {
        val directory = temporary.newFolder()
        val original = File(directory, "课表.pdf").apply { writeText("existing") }
        val duplicate = writeLegacyDownload(directory, "课表.pdf") { it.write("new".toByteArray()) }
        assertEquals("existing", original.readText())
        assertEquals("课表 (1).pdf", duplicate.name)
        assertEquals("new", duplicate.readText())
    }

    @Test fun failedWriteRemovesOnlyTheNewPartialDownload() {
        val directory = temporary.newFolder()
        val original = File(directory, "course.pdf").apply { writeText("existing") }
        assertThrows(IOException::class.java) {
            writeLegacyDownload(directory, "course.pdf") {
                it.write(byteArrayOf(42))
                throw IOException("interrupted")
            }
        }
        assertEquals(listOf(original.name), directory.listFiles()!!.map(File::getName))
        assertEquals("existing", original.readText())
    }

    @Test fun filenamesKeepLiteralPlusAndUnicodeWithinFilesystemLimits() {
        assertEquals("A+B.pdf", downloadDispositionFileName("attachment; filename=\"A+B.pdf\""))
        val safe = safeDownloadFileName("课表😀".repeat(100) + ".pdf")
        assertTrue(safe.toByteArray(Charsets.UTF_8).size <= 220)
        assertTrue(safe.endsWith(".pdf"))
        assertFalse(safe.contains('\uFFFD'))
    }

    @Test fun emptyAndWindowsPathNamesCannotBecomePaths() {
        assertEquals("download", safeDownloadFileName("../.."))
        assertEquals("course.pdf", safeDownloadFileName("C:\\private\\course.pdf"))
        assertEquals("a_b.pdf", safeDownloadFileName("a\u0000b.pdf"))
    }
}
