package com.xiaomanjun.sleepdownschedule.core.web

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64

internal data class DataUrlDownload(val mimeType: String, val input: InputStream)

internal fun parseDataUrlDownload(value: String): DataUrlDownload {
    require(value.startsWith("data:", ignoreCase = true)) { "无法处理下载链接" }
    val comma = value.indexOf(',')
    require(comma >= 5) { "无法处理下载链接" }
    val header = value.substring(5, comma).split(';')
    val mimeType = header.first().ifBlank { "text/plain" }
    val payload = decodeDataUrlBytes(value.substring(comma + 1))
    val input = payload.inputStream()
    return DataUrlDownload(mimeType, if (header.any { it.equals("base64", true) }) {
        Base64.getMimeDecoder().wrap(input)
    } else input)
}

private fun decodeDataUrlBytes(value: String): ByteArray {
    val result = ByteArrayOutputStream()
    var offset = 0
    while (offset < value.length) {
        if (value[offset] == '%') {
            require(offset + 2 < value.length) { "下载内容编码不完整" }
            val byte = value.substring(offset + 1, offset + 3).toIntOrNull(16)
            require(byte != null) { "下载内容编码无效" }
            result.write(byte)
            offset += 3
        } else {
            val next = value.indexOf('%', offset).takeIf { it >= 0 } ?: value.length
            result.write(value.substring(offset, next).toByteArray(Charsets.UTF_8))
            offset = next
        }
    }
    return result.toByteArray()
}
