package com.xiaomanjun.sleepdownschedule.feature.importing

internal enum class EduPromptCredentialType { Username, Password }

/** Only credential prompts expose login semantics; codes and ordinary adapter questions do not. */
internal fun eduPromptCredentialType(title: String, message: String): EduPromptCredentialType? {
    fun isCode(text: String) = listOf("验证码", "动态密码", "一次性", "otp", "verification code").any(text::contains)
    fun classify(text: String): EduPromptCredentialType? = when {
        isCode(text) -> null
        listOf("密码", "password", "passwd").any(text::contains) -> EduPromptCredentialType.Password
        listOf("账号", "帐号", "学号", "用户名", "username", "user name", "student id").any(text::contains) -> EduPromptCredentialType.Username
        else -> null
    }
    val heading = title.lowercase()
    if (isCode(heading)) return null
    // A login hint may mention both fields. The field's title is the stronger signal.
    return classify(heading) ?: classify(message.lowercase())
}
