package com.xiaomanjun.sleepdownschedule.feature.importing

/** Only explicitly authored UI messages may escape the credential-operation boundary. */
internal class EduBrowserActionFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun eduBrowserActionFailureMessage(error: Exception): String =
    (error as? EduBrowserActionFailure)?.message ?: "操作未完成，请刷新教务页面后重试"
