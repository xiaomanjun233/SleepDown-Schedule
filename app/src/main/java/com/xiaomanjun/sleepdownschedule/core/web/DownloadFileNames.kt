package com.xiaomanjun.sleepdownschedule.core.web

import java.net.URLDecoder

internal fun downloadDispositionFileName(disposition: String?): String? {
    if (disposition == null) return null
    val encoded = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
        .find(disposition)?.groupValues?.get(1)
    val ordinary = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
        .find(disposition)?.groupValues?.get(1)
    val value = (encoded ?: ordinary)?.trim()?.takeIf(String::isNotEmpty) ?: return null
    // A plus sign in a filename is literal; this is not form-url-encoded data.
    return runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
}

internal fun safeDownloadFileName(value: String): String {
    val leaf = value.replace('\\', '/').substringAfterLast('/')
    val cleaned = leaf.map { char ->
        if (char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt() ||
            char in ":*?\"<>|") '_' else char
    }.joinToString("").trim().trim('.')
    if (cleaned.isBlank()) return "download"
    // Leave room for a duplicate-name suffix below the common 255-byte filesystem limit.
    val extension = cleaned.substringAfterLast('.', "").takeIf { it.length in 1..16 }
        ?.let { ".$it" }.orEmpty()
    val stem = if (extension.isEmpty()) cleaned else cleaned.dropLast(extension.length)
    val budget = 220 - extension.toByteArray(Charsets.UTF_8).size
    var end = 0
    var bytes = 0
    while (end < stem.length) {
        val codePoint = stem.codePointAt(end)
        val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
        if (bytes + size > budget) break
        bytes += size
        end += Character.charCount(codePoint)
    }
    return stem.substring(0, end) + extension
}

internal fun numberedDownloadFileName(safeName: String, number: Int): String {
    if (number == 0) return safeName
    val dot = safeName.lastIndexOf('.').takeIf { it > 0 } ?: safeName.length
    return "${safeName.take(dot)} ($number)${safeName.substring(dot)}"
}
