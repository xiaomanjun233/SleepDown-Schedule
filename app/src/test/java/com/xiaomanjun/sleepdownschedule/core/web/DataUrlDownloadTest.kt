package com.xiaomanjun.sleepdownschedule.core.web

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DataUrlDownloadTest {
    @Test fun base64PayloadSupportsPercentEncodedPadding() {
        val download = parseDataUrlDownload("data:application/pdf;base64,SGk%3D")
        assertEquals("application/pdf", download.mimeType)
        download.input.use { assertEquals("Hi", it.readBytes().toString(Charsets.UTF_8)) }
    }

    @Test fun ordinaryPayloadKeepsPlusAndArbitraryBytes() {
        val download = parseDataUrlDownload("data:application/octet-stream,%00%FF+%2B")
        download.input.use { assertArrayEquals(byteArrayOf(0, -1, 43, 43), it.readBytes()) }
    }

    @Test fun omittedMimeTypeDefaultsToText() {
        val download = parseDataUrlDownload("data:,课表")
        assertEquals("text/plain", download.mimeType)
        download.input.use { assertEquals("课表", it.readBytes().toString(Charsets.UTF_8)) }
    }

    @Test fun malformedPercentEncodingFailsBeforeCreatingOutput() {
        assertThrows(IllegalArgumentException::class.java) { parseDataUrlDownload("data:,abc%0") }
        assertThrows(IllegalArgumentException::class.java) { parseDataUrlDownload("data:,abc%ZZ") }
    }
}
