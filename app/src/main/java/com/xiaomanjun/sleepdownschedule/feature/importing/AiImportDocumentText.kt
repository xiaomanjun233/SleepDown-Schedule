package com.xiaomanjun.sleepdownschedule.feature.importing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

internal const val MaxAiExtractedDocumentChars = 60_000

internal enum class AiImportDocumentKind {
    PLAIN_TEXT,
    XLSX,
    DOCX,
    PPTX,
    ODS,
    PDF,
    IMAGE,
    ICS,
    UNKNOWN
}

internal data class LocalAiDocumentText(
    val text: String,
    val formatLabel: String
)

private val PlainTextExtensions = setOf(
    "txt", "csv", "tsv", "md", "markdown", "json", "xml", "html", "htm", "log", "yaml", "yml"
)

private const val XlsxMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val DocxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val PptxMime = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
private const val OdsMime = "application/vnd.oasis.opendocument.spreadsheet"
private const val MaxRelevantZipEntryBytes = 8 * 1024 * 1024
private const val MaxRelevantZipBytes = 32 * 1024 * 1024
private const val MaxZipEntries = 512

internal fun normalizeAiImportMimeType(displayName: String, reportedMimeType: String?): String {
    val reported = reportedMimeType?.substringBefore(';')?.trim().orEmpty()
    if (reported.isNotBlank() && !reported.equals("application/octet-stream", true)) return reported
    return when (displayName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "xlsx" -> XlsxMime
        "docx" -> DocxMime
        "pptx" -> PptxMime
        "ods" -> OdsMime
        "csv" -> "text/csv"
        "tsv" -> "text/tab-separated-values"
        "txt", "md", "markdown", "log" -> "text/plain"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "yaml", "yml" -> "application/yaml"
        "ics" -> "text/calendar"
        else -> reported.ifBlank { "application/octet-stream" }
    }
}

internal fun classifyAiImportDocument(
    displayName: String,
    mimeType: String
): AiImportDocumentKind {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    val mime = mimeType.substringBefore(';').trim().lowercase()
    return when {
        extension == "ics" || mime == "text/calendar" || mime == "application/ics" -> AiImportDocumentKind.ICS
        extension == "pdf" || mime == "application/pdf" -> AiImportDocumentKind.PDF
        mime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif") -> AiImportDocumentKind.IMAGE
        extension == "xlsx" || mime == XlsxMime -> AiImportDocumentKind.XLSX
        extension == "docx" || mime == DocxMime -> AiImportDocumentKind.DOCX
        extension == "pptx" || mime == PptxMime -> AiImportDocumentKind.PPTX
        extension == "ods" || mime == OdsMime -> AiImportDocumentKind.ODS
        extension in PlainTextExtensions || mime.startsWith("text/") || mime in setOf(
            "application/json",
            "application/xml",
            "application/csv",
            "application/x-csv",
            "application/yaml"
        ) -> AiImportDocumentKind.PLAIN_TEXT
        else -> AiImportDocumentKind.UNKNOWN
    }
}

/**
 * Extracts only document text on-device. No network request, temporary file, or persistent copy is made.
 * A strict ZIP expansion budget protects Office/ODS parsing from malformed or zip-bomb inputs.
 */
internal fun extractLocalAiDocumentText(
    displayName: String,
    mimeType: String,
    bytes: ByteArray
): LocalAiDocumentText? {
    val kind = classifyAiImportDocument(displayName, mimeType)
    val rawText = when (kind) {
        AiImportDocumentKind.PLAIN_TEXT -> decodeTextDocument(bytes).let { text ->
            if (displayName.endsWith(".html", true) || displayName.endsWith(".htm", true)) {
                htmlToPlainText(text)
            } else {
                text
            }
        }
        AiImportDocumentKind.XLSX -> extractXlsxText(bytes)
        AiImportDocumentKind.DOCX -> extractDocxText(bytes)
        AiImportDocumentKind.PPTX -> extractPptxText(bytes)
        AiImportDocumentKind.ODS -> extractOdsText(bytes)
        else -> return null
    }
    val normalized = normalizeExtractedDocumentText(rawText)
    require(normalized.any { !it.isWhitespace() }) { "${formatLabel(kind)} 文件中没有可读取文字" }
    return LocalAiDocumentText(normalized, formatLabel(kind))
}

private fun formatLabel(kind: AiImportDocumentKind): String = when (kind) {
    AiImportDocumentKind.PLAIN_TEXT -> "文本"
    AiImportDocumentKind.XLSX -> "XLSX 工作簿"
    AiImportDocumentKind.DOCX -> "DOCX 文档"
    AiImportDocumentKind.PPTX -> "PPTX 演示文稿"
    AiImportDocumentKind.ODS -> "ODS 工作簿"
    AiImportDocumentKind.PDF -> "PDF"
    AiImportDocumentKind.IMAGE -> "图片"
    AiImportDocumentKind.ICS -> "ICS"
    AiImportDocumentKind.UNKNOWN -> "文件"
}

private fun decodeTextDocument(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    return when {
        bytes.startsWithBytes(0xEF, 0xBB, 0xBF) -> bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        bytes.startsWithBytes(0xFF, 0xFE) -> bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16LE)
        bytes.startsWithBytes(0xFE, 0xFF) -> bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16BE)
        else -> decodeStrictUtf8(bytes) ?: bytes.toString(Charset.forName("GB18030"))
    }
}

private fun ByteArray.startsWithBytes(vararg values: Int): Boolean =
    size >= values.size && values.indices.all { this[it].toInt() and 0xFF == values[it] }

private fun decodeStrictUtf8(bytes: ByteArray): String? = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

private fun htmlToPlainText(html: String): String = html
    .replace(Regex("(?is)<(script|style)\\b.*?</\\1>"), " ")
    .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>|</li>|</h[1-6]>"), "\n")
    .replace(Regex("(?i)</td>|</th>"), "\t")
    .replace(Regex("(?s)<[^>]+>"), " ")
    .let(::decodeXmlEntities)

private fun extractXlsxText(bytes: ByteArray): String {
    val entries = readRelevantZipEntries(bytes) { name ->
        name == "xl/sharedStrings.xml" ||
            name == "xl/workbook.xml" ||
            (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
    }
    val sheets = entries.entries
        .filter { (name, _) -> name.startsWith("xl/worksheets/") && name.endsWith(".xml") }
        .sortedBy { naturalXmlIndex(it.key) }
    require(sheets.isNotEmpty()) { "XLSX 中没有可读取的工作表" }
    val sharedStrings = entries["xl/sharedStrings.xml"]
        ?.toString(StandardCharsets.UTF_8)
        ?.let(::parseSharedStrings)
        .orEmpty()
    val names = entries["xl/workbook.xml"]
        ?.toString(StandardCharsets.UTF_8)
        ?.let(::parseWorkbookSheetNames)
        .orEmpty()
    return buildString {
        sheets.take(24).forEachIndexed { index, (_, xmlBytes) ->
            if (isNotEmpty()) appendLine()
            appendLine("【${names.getOrNull(index).orEmpty().ifBlank { "工作表 ${index + 1}" }}】")
            append(parseWorksheet(xmlBytes.toString(StandardCharsets.UTF_8), sharedStrings))
        }
    }
}

private fun parseSharedStrings(xml: String): List<String> =
    Regex("(?is)<si\\b[^>]*>(.*?)</si>").findAll(xml).map { match ->
        extractXmlTextNodes(match.groupValues[1]).joinToString("")
    }.toList()

private fun parseWorkbookSheetNames(xml: String): List<String> =
    Regex("(?is)<sheet\\b([^>]*)/?>").findAll(xml).mapNotNull { match ->
        Regex("(?i)\\bname\\s*=\\s*['\"]([^'\"]*)['\"]")
            .find(match.groupValues[1])
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeXmlEntities)
    }.toList()

private fun parseWorksheet(xml: String, sharedStrings: List<String>): String = buildString {
    Regex("(?is)<row\\b[^>]*>(.*?)</row>").findAll(xml).forEach { rowMatch ->
        val row = mutableListOf<String>()
        var sequentialColumn = 0
        Regex("(?is)<c\\b([^>]*)>(.*?)</c>").findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
            val attributes = cellMatch.groupValues[1]
            val body = cellMatch.groupValues[2]
            val coordinate = Regex("(?i)\\br\\s*=\\s*['\"]([A-Z]+)\\d+['\"]")
                .find(attributes)?.groupValues?.getOrNull(1)
            val column = coordinate?.let(::spreadsheetColumnIndex) ?: sequentialColumn
            while (row.size < column.coerceAtMost(127)) row += ""
            val type = Regex("(?i)\\bt\\s*=\\s*['\"]([^'\"]+)['\"]")
                .find(attributes)?.groupValues?.getOrNull(1).orEmpty()
            val value = when (type) {
                "inlineStr" -> extractXmlTextNodes(body).joinToString("")
                "s" -> Regex("(?is)<v\\b[^>]*>(.*?)</v>").find(body)?.groupValues?.getOrNull(1)
                    ?.trim()?.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
                else -> Regex("(?is)<v\\b[^>]*>(.*?)</v>").find(body)?.groupValues?.getOrNull(1)
                    ?.let(::decodeXmlEntities).orEmpty()
            }.trim()
            if (column < 128) {
                while (row.size <= column) row += ""
                row[column] = value
            }
            sequentialColumn = column + 1
        }
        val trimmed = row.dropLastWhile(String::isBlank)
        if (trimmed.any(String::isNotBlank)) appendLine(trimmed.joinToString("\t"))
        if (length >= MaxAiExtractedDocumentChars) return@buildString
    }
}

private fun spreadsheetColumnIndex(letters: String): Int {
    var result = 0
    letters.uppercase().forEach { char ->
        if (char !in 'A'..'Z') return@forEach
        result = result * 26 + (char - 'A' + 1)
    }
    return (result - 1).coerceAtLeast(0)
}

private fun extractDocxText(bytes: ByteArray): String {
    val document = readRelevantZipEntries(bytes) { it == "word/document.xml" }["word/document.xml"]
        ?: error("DOCX 中缺少正文内容")
    return document.toString(StandardCharsets.UTF_8)
        .replace(Regex("(?i)<w:tab\\b[^>]*/>"), "\t")
        .replace(Regex("(?i)</w:tc>"), "\t")
        .replace(Regex("(?i)</w:tr>|</w:p>"), "\n")
        .let(::xmlMarkupToText)
}

private fun extractPptxText(bytes: ByteArray): String {
    val slides = readRelevantZipEntries(bytes) { name ->
        name.startsWith("ppt/slides/slide") && name.endsWith(".xml")
    }.entries.sortedBy { naturalXmlIndex(it.key) }
    require(slides.isNotEmpty()) { "PPTX 中没有可读取的幻灯片" }
    return buildString {
        slides.take(80).forEachIndexed { index, (_, slide) ->
            if (isNotEmpty()) appendLine()
            appendLine("【幻灯片 ${index + 1}】")
            append(
                slide.toString(StandardCharsets.UTF_8)
                    .replace(Regex("(?i)<a:br\\b[^>]*/>|</a:p>"), "\n")
                    .let(::xmlMarkupToText)
            )
        }
    }
}

private fun extractOdsText(bytes: ByteArray): String {
    val content = readRelevantZipEntries(bytes) { it == "content.xml" }["content.xml"]
        ?: error("ODS 中缺少工作表内容")
    return content.toString(StandardCharsets.UTF_8)
        .replace(Regex("(?i)</table:table-cell>"), "\t")
        .replace(Regex("(?i)</table:table-row>|</text:p>"), "\n")
        .let(::xmlMarkupToText)
}

private fun xmlMarkupToText(xml: String): String {
    val withText = Regex("(?is)<(?:[A-Za-z0-9_-]+:)?t\\b[^>]*>(.*?)</(?:[A-Za-z0-9_-]+:)?t>")
        .replace(xml) { decodeXmlEntities(it.groupValues[1]) }
    return decodeXmlEntities(withText.replace(Regex("(?s)<[^>]+>"), ""))
}

private fun extractXmlTextNodes(fragment: String): List<String> =
    Regex("(?is)<(?:[A-Za-z0-9_-]+:)?t\\b[^>]*>(.*?)</(?:[A-Za-z0-9_-]+:)?t>")
        .findAll(fragment)
        .map { decodeXmlEntities(it.groupValues[1]) }
        .toList()

private fun decodeXmlEntities(value: String): String {
    val numericDecoded = Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(value) { match ->
        val raw = match.groupValues[1]
        val codePoint = if (raw.startsWith('x', true)) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
        codePoint?.takeIf(Character::isValidCodePoint)?.let(StringBuilder()::appendCodePoint)?.toString()
            ?: match.value
    }
    return numericDecoded
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

private fun normalizeExtractedDocumentText(value: String): String = value
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
    .lines()
    .map { it.trimEnd() }
    .joinToString("\n")
    .replace(Regex("\n{4,}"), "\n\n\n")
    .trim()
    .take(MaxAiExtractedDocumentChars)

private fun readRelevantZipEntries(
    bytes: ByteArray,
    include: (String) -> Boolean
): Map<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    var entryCount = 0
    var relevantBytes = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount++
            require(entryCount <= MaxZipEntries) { "文档内部文件数量异常，已停止解析" }
            val normalizedName = entry.name.replace('\\', '/').removePrefix("/")
            if (!entry.isDirectory && include(normalizedName)) {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entryBytes = 0
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    entryBytes += count
                    relevantBytes += count
                    require(entryBytes <= MaxRelevantZipEntryBytes && relevantBytes <= MaxRelevantZipBytes) {
                        "文档展开后过大，已停止解析"
                    }
                    output.write(buffer, 0, count)
                }
                result[normalizedName] = output.toByteArray()
            }
            zip.closeEntry()
        }
    }
    return result
}

private fun naturalXmlIndex(path: String): Int =
    Regex("(\\d+)(?=\\.xml$)").find(path)?.value?.toIntOrNull() ?: Int.MAX_VALUE
