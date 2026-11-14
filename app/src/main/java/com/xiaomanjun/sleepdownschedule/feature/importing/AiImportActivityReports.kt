package com.xiaomanjun.sleepdownschedule.feature.importing

enum class AiImportReportKind { EXECUTION, MODEL, USER }

internal enum class AiImportWaitPhase { LOCAL_OPERATION, SERVICE_RESPONSE, MODEL_OUTPUT }

/** Completed public reports and actual operations, separate from rapidly changing reasoning. */
data class AiImportReport(val text: String, val kind: AiImportReportKind)

internal const val MaxAiImportReports = 128
internal const val MaxAiImportModelReportChars = 4_096

internal fun normalizeAiImportReport(report: AiImportReport): AiImportReport = report.copy(text =
    if (report.kind != AiImportReportKind.EXECUTION) report.text.replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[\\p{Z}\\t]+"), " ").replace(Regex("[\\p{Cc}&&[^\\n]]|\\p{Cf}"), "")
        .replace(Regex("\\n{3,}"), "\n\n").trim().take(if (report.kind == AiImportReportKind.USER) 6_000 else MaxAiImportModelReportChars)
    else report.text.replace(Regex("[\\s\\p{Z}\\p{Cc}\\p{Cf}]+"), " ").trim().take(180))

internal fun appendAiImportReport(reports: List<AiImportReport>, report: AiImportReport): List<AiImportReport> {
    val normalized = normalizeAiImportReport(report)
    if (normalized.text.isBlank() || normalized.kind != AiImportReportKind.USER && reports.lastOrNull() == normalized) return reports
    val all = reports + normalized
    var excess = (all.size - MaxAiImportReports).coerceAtLeast(0)
    return all.filter { if (excess > 0 && it.kind != AiImportReportKind.USER) { excess--; false } else true }
}

internal fun aiImportElapsedLabel(nowNanos: Long, startedAtNanos: Long): String {
    val seconds = (nowNanos - startedAtNanos).coerceAtLeast(0) / 1_000_000_000L
    val duration = if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分 ${seconds % 60} 秒"
    return "正在导入中 · $duration"
}

/** A single pending fragment may follow the transcript, but committed paragraphs never repeat. */
internal fun remainingAiImportPublicText(text: String, reports: List<AiImportReport>): String {
    var remaining = importPublicProgressText(text).orEmpty()
    reports.filter { it.kind == AiImportReportKind.MODEL }.forEach { report ->
        val committed = normalizeAiImportReport(report).text
        if (committed.isNotBlank() && remaining.startsWith(committed) &&
            (remaining.length == committed.length || remaining[committed.length].isWhitespace())) {
            remaining = remaining.removePrefix(committed).trimStart()
        }
    }
    return remaining
}
