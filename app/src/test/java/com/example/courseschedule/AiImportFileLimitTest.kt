package com.example.courseschedule

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AiImportFileLimitTest {
    @Test
    fun streamAtLimitIsReadWithoutTruncation() {
        val expected = ByteArray(32 * 1024) { (it % 251).toByte() }

        val actual = ByteArrayInputStream(expected).readBytesWithLimit(expected.size)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun streamLargerThanLimitFailsBeforeAccumulatingTheWholeFile() {
        val limit = 8 * 1024
        val input = ByteArrayInputStream(ByteArray(limit + 1))

        try {
            input.readBytesWithLimit(limit)
            fail("Expected an oversized file to be rejected")
        } catch (error: IllegalArgumentException) {
            assertEquals("文件不能超过 20MB", error.message)
        }
    }
}
