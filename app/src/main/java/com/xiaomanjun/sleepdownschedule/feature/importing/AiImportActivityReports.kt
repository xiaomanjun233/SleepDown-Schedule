package com.xiaomanjun.sleepdownschedule.feature.importing

enum class AiImportReportKind { EXECUTION, MODEL }

internal enum class AiImportWaitPhase { LOCAL_OPERATION, SERVICE_RESPONSE, MODEL_OUTPUT }

/** Completed public reports and actual operations, separate from rapidly changing reasoning. */
data class AiImportReport(val text: String, val kind: AiImportReportKind)

internal fun appendAiImportReport(reports: List<AiImportReport>, report: AiImportReport): List<AiImportReport> {
    val normalized = report.copy(text = report.text.replace(Regex("[\\s\\p{Z}\\p{Cc}\\p{Cf}]+"), " ").trim().take(180))
    if (normalized.text.isBlank() || reports.lastOrNull() == normalized) return reports
    return (reports + normalized).takeLast(12)
}

/** Real elapsed silence, not inferred percentage or an invented work milestone. */
internal fun aiImportWaitLabel(nowNanos: Long, lastActivityNanos: Long, phase: AiImportWaitPhase): String? {
    val seconds = ((nowNanos - lastActivityNanos).coerceAtLeast(0) / 1_000_000_000L)
    if (seconds < 3) return null
    val duration = if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分 ${seconds % 60} 秒"
    return when (phase) {
        AiImportWaitPhase.LOCAL_OPERATION -> "当前步骤已持续 $duration"
        AiImportWaitPhase.SERVICE_RESPONSE -> "等待 AI 服务响应 · $duration"
        AiImportWaitPhase.MODEL_OUTPUT -> "等待新的模型输出 · $duration"
    }
}
