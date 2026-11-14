package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MaxAiImportFiles = 20
internal const val MaxAiImportBatchBytes = 40 * 1024 * 1024

internal suspend fun loadAiImportFiles(context: Context, uris: List<Uri>): List<AiImportFile> {
    val selected = uris.distinct()
    require(selected.size in 1..MaxAiImportFiles) { "每次请选择 1–$MaxAiImportFiles 个文件" }
    var bytes = 0L
    return selected.map { uri ->
        loadAiImportFile(context, uri).getOrThrow().also {
            bytes += it.bytes.size
            require(bytes <= MaxAiImportBatchBytes) { "这组文件总大小不能超过 40MB，请分批选择" }
        }
    }
}

internal data class AiImportBatchPreview(val text: String, val images: List<RenderedPageImage>) {
    fun input() = if (images.isEmpty()) AiScheduleInput.ExtractedText(text, "多文件课表")
        else AiScheduleInput.Images(images, "多文件课表", text)
}

internal suspend fun prepareAiImportBatch(context: Context, files: List<AiImportFile>): AiImportBatchPreview =
    withContext(Dispatchers.IO) {
        require(files.size in 1..MaxAiImportFiles) { "每次请选择 1–$MaxAiImportFiles 个文件" }
        require(files.sumOf { it.bytes.size.toLong() } <= MaxAiImportBatchBytes) { "这组文件总大小不能超过 40MB" }
        val images = mutableListOf<RenderedPageImage>()
        val text = buildString {
            appendLine("材料共 ${files.size} 个，按选择顺序编号。READ_IMPORT_MATERIAL 的 file=1..${files.size} 读取对应原文件，page 为该文件页码；file=0 读取目录。VIEW_IMPORT_DETAIL 用同一 file 编号按原页裁切。下面只提供初始概览，必须通过材料工具补查未展示的页面与文字。")
            files.forEachIndexed { index, file ->
                require(file.bytes.size <= MaxAiImportFileBytes) { "${file.displayName} 超过 20MB" }
                val localText = extractAiImportTextPreview(file, context)?.text
                val previews = renderAiImportPreviewImages(context, file, maxPages = 1)
                require(localText != null || previews.isNotEmpty()) { "无法在本机读取 ${file.displayName}，请另存为可提取文字的文档或图片" }
                appendLine("\n[文件 ${index + 1}] ${file.displayName}")
                if (previews.isNotEmpty()) appendLine("下附第 ${images.size + 1} 张图片是本文件第 1 页概览，其余页使用材料工具核对。")
                images += previews.map { it.copy(pageIndex = images.size) }
                localText?.let {
                    val budget = 48_000 / files.size
                    appendLine(it.take(budget))
                    if (it.length > budget) appendLine("（文字未展示完，请按 file=${index + 1} 和 offset 继续读取。）")
                }
            }
        }
        AiImportBatchPreview(text, images)
    }
