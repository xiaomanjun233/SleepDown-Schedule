package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.core.graphics.createBitmap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.zip.InflaterInputStream


interface ScheduleFilePreprocessor {
    suspend fun preprocess(file: AiImportFile, config: AiProviderConfig): PreprocessResult
}

internal class DefaultScheduleFilePreprocessor(private val context: Context) : ScheduleFilePreprocessor {
    override suspend fun preprocess(file: AiImportFile, config: AiProviderConfig): PreprocessResult {
        return when {
            file.isIcs -> PreprocessResult.Text(icsTextForAi(file.bytes),
                "已读取 ICS 日历文本，确认后由 AI 整理课程与节次。")
            file.isLocalTextDocument -> {
                val extracted = extractLocalAiDocumentText(file.displayName, file.mimeType, file.bytes)
                    ?: error("无法从 ${file.displayName} 提取文字")
                PreprocessResult.Text(
                    text = extracted.text,
                    routeMessage = "已在本机从${extracted.formatLabel}提取文字，原文件不会上传。"
                )
            }
            file.isPdf -> preprocessPdf(file, config)
            file.isImage -> {
                require(config.inputMode != AiInputMode.TEXT_ONLY) { "当前输入模式为仅本地文本，无法解析图片课表。" }
                require(config.supportsVision) {
                    "当前模型不支持图片输入。请换视觉模型，或上传可提取文字的 PDF、XLSX、CSV、DOCX 等文件。"
                }
                PreprocessResult.Images(
                    images = listOf(file.toRenderedImage(0)),
                    routeMessage = "图片课表将交给视觉模型解析。"
                )
            }
            canSendNativeFile(file, config) -> PreprocessResult.Raw(
                file = file,
                bytes = file.bytes,
                routeMessage = "当前官方接口支持该格式，确认后将直接发送原文件解析。"
            )
            else -> error(
                "暂不支持该文件类型。请使用 PDF、图片、XLSX、CSV、TSV、TXT、Markdown、JSON、XML、HTML、DOCX、PPTX 或 ODS；" +
                    "旧版 XLS/DOC/PPT 请先另存为新版格式或 CSV。"
            )
        }
    }

    private fun preprocessPdf(file: AiImportFile, config: AiProviderConfig): PreprocessResult {
        val extracted = extractPdfTextBestEffort(file.bytes)
        if (isUsefulExtractedScheduleText(extracted)) {
            return PreprocessResult.Text(extracted, "已优先在本机从 PDF 提取有效文字，原 PDF 不会上传。")
        }
        require(config.inputMode != AiInputMode.TEXT_ONLY) {
            "PDF 文本提取结果不足，当前输入模式禁止视觉解析。"
        }
        require(config.supportsVision) {
            "PDF 文本提取结果不足，且当前模型不支持视觉输入，请换视觉模型或上传文本版课表。"
        }
        return PreprocessResult.Images(
            images = renderPdfPageImages(context, file, maxPages = 10),
            routeMessage = "PDF 未提取到足够有效文字，已按页转成图片交给视觉模型解析。"
        )
    }
}

private val NativeResponsesDocumentExtensions = setOf("xls", "doc", "ppt", "rtf", "odt", "odp")

internal fun canSendNativeFile(file: AiImportFile, config: AiProviderConfig): Boolean {
    val extension = file.displayName.substringAfterLast('.', "").lowercase()
    val officialOpenAiResponses = config.providerId in setOf(AiProviderPresets.openAI.id, AiProviderPresets.chatGpt.id) &&
        isOfficialOpenAIBaseUrl(config.baseUrl) &&
        config.endpointStyle == AiEndpointStyle.RESPONSES
    val explicitlyEnabledCompatibleResponses = config.endpointStyle == AiEndpointStyle.RESPONSES &&
        config.inputMode == AiInputMode.RESPONSES_FILE
    return config.supportsFileUpload &&
        config.inputMode != AiInputMode.TEXT_ONLY &&
        extension in NativeResponsesDocumentExtensions &&
        (officialOpenAiResponses || explicitlyEnabledCompatibleResponses)
}

internal fun icsTextForAi(bytes: ByteArray): String {
    val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    require(text.length <= 60_000) { "日历内容过多，请只导出当前学期后再导入，避免遗漏课程。" }
    return text
}

internal fun extractAiImportTextPreview(file: AiImportFile): LocalAiDocumentText? = when {
    file.isIcs -> LocalAiDocumentText(icsTextForAi(file.bytes), "ICS 日历")
    file.isLocalTextDocument -> extractLocalAiDocumentText(file.displayName, file.mimeType, file.bytes)
    file.isPdf -> extractPdfTextBestEffort(file.bytes)
        .takeIf(::isUsefulExtractedScheduleText)
        ?.let { LocalAiDocumentText(it, "PDF") }
    else -> null
}

internal fun isUsefulExtractedScheduleText(text: String): Boolean {
    val compact = text.filterNot(Char::isWhitespace)
    if (compact.length < 80) return false
    val readable = compact.count { it.isLetterOrDigit() || it in "，。,:：;；-_/()（）[]【】" }
    if (readable.toFloat() / compact.length.coerceAtLeast(1) < 0.72f) return false
    val normalized = text.lowercase()
    val scheduleSignals = listOf(
        "星期", "周一", "周二", "周三", "周四", "周五", "节次", "课程", "教师", "教室",
        "monday", "tuesday", "wednesday", "thursday", "friday", "course", "teacher", "classroom"
    ).count(normalized::contains)
    return scheduleSignals >= 2
}

internal fun PreprocessResult.toScheduleInput(file: AiImportFile): AiScheduleInput {
    return when (this) {
        is PreprocessResult.Text -> AiScheduleInput.ExtractedText(text, file.displayName)
        is PreprocessResult.Images -> AiScheduleInput.Images(images, file.displayName)
        is PreprocessResult.Raw -> AiScheduleInput.RawFile(
            mimeType = if (file.isPdf) "application/pdf" else file.mimeType,
            fileName = file.displayName,
            bytes = bytes
        )
    }
}


internal fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "读取上限必须大于 0" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        require(total <= maxBytes - count) { "文件不能超过 20MB" }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}

internal fun compressAiImportImage(bytes: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longSide / sample > 1800) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
        bitmap.recycle()
        output.toByteArray()
    }
}

private fun AiImportFile.toRenderedImage(pageIndex: Int): RenderedPageImage {
    return RenderedPageImage(
        pageIndex = pageIndex,
        mimeType = "image/jpeg",
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    )
}

internal fun renderAiImportPreviewImages(
    context: Context,
    file: AiImportFile,
    maxPages: Int = 6
): List<RenderedPageImage> = when {
    file.isImage -> listOf(file.toRenderedImage(0))
    file.isPdf && !isUsefulExtractedScheduleText(extractPdfTextBestEffort(file.bytes)) ->
        renderPdfPageImages(context, file, maxPages)
    else -> emptyList()
}

private fun renderPdfPageImages(context: Context, file: AiImportFile, maxPages: Int = 6): List<RenderedPageImage> {
    val temp = File.createTempFile("sleepdown_ai_pdf_", ".pdf", context.cacheDir)
    return try {
        temp.writeBytes(file.bytes)
        ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount > 0) { "PDF 文件没有可解析页面" }
                val count = minOf(renderer.pageCount, maxPages)
                (0 until count).map { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val scale = 1400f / maxOf(page.width, page.height).coerceAtLeast(1)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bytes = ByteArrayOutputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
                            bitmap.recycle()
                            output.toByteArray()
                        }
                        RenderedPageImage(
                            pageIndex = pageIndex,
                            mimeType = "image/jpeg",
                            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        )
                    }
                }
            }
        }
    } finally {
        temp.delete()
    }
}

internal fun extractPdfTextBestEffort(bytes: ByteArray): String {
    val chunks = mutableListOf<String>()
    val raw = bytes.toString(Charsets.ISO_8859_1)
    chunks += extractPdfTextFromStreamText(raw)
    Regex("stream\\r?\\n(.*?)\\r?\\nendstream", RegexOption.DOT_MATCHES_ALL)
        .findAll(raw)
        .forEach { match ->
            val streamText = match.groupValues[1]
            chunks += extractPdfTextFromStreamText(streamText)
            val streamBytes = streamText.toByteArray(Charsets.ISO_8859_1)
            runCatching {
                InflaterInputStream(ByteArrayInputStream(streamBytes)).bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
            }.getOrNull()?.let { inflated ->
                chunks += extractPdfTextFromStreamText(inflated)
            }
        }
    return chunks.joinToString("\n")
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]+"), " ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n")
        .take(18_000)
}

private fun extractPdfTextFromStreamText(text: String): String {
    val values = mutableListOf<String>()
    Regex("\\((?:\\\\.|[^\\\\)])*\\)\\s*Tj").findAll(text).forEach {
        values += decodePdfLiteralString(it.value.substringBeforeLast(")").removePrefix("("))
    }
    Regex("\\[(.*?)]\\s*TJ", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { array ->
        Regex("\\((?:\\\\.|[^\\\\)])*\\)").findAll(array.groupValues[1]).forEach {
            values += decodePdfLiteralString(it.value.removePrefix("(").removeSuffix(")"))
        }
    }
    return values.joinToString(" ")
}

private fun decodePdfLiteralString(value: String): String {
    val builder = StringBuilder()
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch == '\\' && index + 1 < value.length) {
            val next = value[index + 1]
            builder.append(
                when (next) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    '(', ')', '\\' -> next
                    else -> next
                }
            )
            index += 2
        } else {
            builder.append(ch)
            index++
        }
    }
    return builder.toString()
}

