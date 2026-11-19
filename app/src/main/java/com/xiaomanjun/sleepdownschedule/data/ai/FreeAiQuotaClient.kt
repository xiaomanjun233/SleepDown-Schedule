package com.xiaomanjun.sleepdownschedule.data.ai

import android.content.Context
import android.provider.Settings
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.analytics.InstallationAnalytics
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal object FreeAiQuotaCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val validActionIdRegex = Regex("^[a-zA-Z0-9_-]{8,64}$")

    fun normalizeActionId(actionId: String?): String =
        actionId?.trim()?.takeIf(validActionIdRegex::matches) ?: UUID.randomUUID().toString()

    fun encodeConsumeBody(installationId: String, kind: FreeAiQuotaKind, actionId: String): String =
        buildJsonObject {
            put("installationId", installationId)
            put("kind", kind.wireValue)
            put("actionId", normalizeActionId(actionId))
        }.toString()

    fun parseConsumeSuccess(
        body: String,
        fallbackKind: FreeAiQuotaKind,
        headers: Map<String, String?> = emptyMap()
    ): FreeAiQuotaConsumeResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val parsed = parseConsumeObject(root, fallbackKind, headers)
        return parsed ?: throw IllegalStateException("每日免费 AI 额度响应格式无效")
    }

    fun parseConsumeError(
        status: Int,
        body: String,
        fallbackKind: FreeAiQuotaKind,
        headers: Map<String, String?> = emptyMap()
    ): ParsedQuotaError {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val errorObj = root?.get("error") as? JsonObject
        val code = errorObj?.get("code")?.jsonPrimitive?.contentOrNull.orEmpty()
        val message = errorObj?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
        val quotaObj = root?.get("quota") as? JsonObject
        val quotaResult = if (status == 429 && (code == "AI_QUOTA_EXCEEDED" || quotaObj != null)) {
            parseConsumeObject(quotaObj, fallbackKind, headers, defaultAllowed = false)
        } else {
            null
        }
        return ParsedQuotaError(
            status = status,
            code = code,
            message = message,
            quota = quotaResult
        )
    }

    fun parseSnapshot(
        body: String,
        headers: Map<String, String?> = emptyMap()
    ): FreeAiQuotaSnapshot {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IllegalStateException("每日免费 AI 额度状态格式无效")
        val date = root["date"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: FreeAiQuotaTime.currentDate()
        val resetAt = root["resetAt"]?.jsonPrimitive?.longOrNull
            ?: headerLong(headers, "X-SleepDown-Quota-Reset")
            ?: 0L
        val timezone = root["timezone"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: FreeAiQuotaTime.DEFAULT_TIMEZONE
        val chat = parseBucket(
            obj = root["chat"] as? JsonObject,
            fallbackLimit = headerLong(headers, "X-SleepDown-Quota-Chat-Limit"),
            fallbackRemaining = headerLong(headers, "X-SleepDown-Quota-Chat-Remaining")
        ) ?: throw IllegalStateException("缺少对话额度信息")
        val import = parseBucket(
            obj = root["import"] as? JsonObject,
            fallbackLimit = headerLong(headers, "X-SleepDown-Quota-Import-Limit"),
            fallbackRemaining = headerLong(headers, "X-SleepDown-Quota-Import-Remaining")
        ) ?: throw IllegalStateException("缺少导入额度信息")
        return FreeAiQuotaSnapshot(
            date = date,
            resetAt = resetAt,
            timezone = timezone,
            chat = chat,
            import = import
        )
    }

    private fun parseConsumeObject(
        root: JsonObject?,
        fallbackKind: FreeAiQuotaKind,
        headers: Map<String, String?>,
        defaultAllowed: Boolean = true
    ): FreeAiQuotaConsumeResult? {
        val kind = FreeAiQuotaKind.fromWire(
            root?.get("kind")?.jsonPrimitive?.contentOrNull
                ?: headerString(headers, "X-SleepDown-Quota-Kind")
        ) ?: fallbackKind
        val limit = root?.get("limit")?.jsonPrimitive?.longOrNull
            ?: headerLong(headers, "X-SleepDown-Quota-Limit")
            ?: return null
        val used = root?.get("used")?.jsonPrimitive?.longOrNull
            ?: headerLong(headers, "X-SleepDown-Quota-Used")
            ?: 0L
        val remaining = root?.get("remaining")?.jsonPrimitive?.longOrNull
            ?: headerLong(headers, "X-SleepDown-Quota-Remaining")
            ?: FreeAiQuotaBucket.computeRemaining(limit, used)
            ?: 0L
        val resetAt = root?.get("resetAt")?.jsonPrimitive?.longOrNull
            ?: headerLong(headers, "X-SleepDown-Quota-Reset")
            ?: 0L
        val date = root?.get("date")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: FreeAiQuotaTime.currentDate()
        val allowed = root?.get("allowed")?.jsonPrimitive?.booleanOrNull ?: defaultAllowed
        val counted = root?.get("counted")?.jsonPrimitive?.booleanOrNull ?: false
        return FreeAiQuotaConsumeResult(
            allowed = allowed,
            counted = counted,
            kind = kind,
            date = date,
            used = used,
            limit = limit,
            remaining = remaining,
            resetAt = resetAt
        )
    }

    private fun parseBucket(
        obj: JsonObject?,
        fallbackLimit: Long?,
        fallbackRemaining: Long?
    ): FreeAiQuotaBucket? {
        val limit = obj?.get("limit")?.jsonPrimitive?.longOrNull ?: fallbackLimit ?: return null
        val used = obj?.get("used")?.jsonPrimitive?.longOrNull
        val remaining = obj?.get("remaining")?.jsonPrimitive?.longOrNull
            ?: fallbackRemaining
            ?: FreeAiQuotaBucket.computeRemaining(limit, used)
        return FreeAiQuotaBucket(used = used, limit = limit, remaining = remaining)
    }

    private fun headerString(headers: Map<String, String?>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.trim()

    private fun headerLong(headers: Map<String, String?>, name: String): Long? =
        headerString(headers, name)?.toLongOrNull()

    internal data class ParsedQuotaError(
        val status: Int,
        val code: String,
        val message: String,
        val quota: FreeAiQuotaConsumeResult?
    )
}

internal object FreeAiQuotaIdentity {
    private val hexDigitsRegex = Regex("^[0-9a-f]{8,64}$")

    fun resolveSubjectId(context: Context): String {
        val appContext = context.applicationContext
        val rawAndroidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return deriveSubjectUuid(
            androidId = rawAndroidId,
            packageName = appContext.packageName
        ) ?: InstallationAnalytics(appContext).installationId()
    }

    internal fun deriveSubjectUuid(androidId: String?, packageName: String): String? {
        val normalized = androidId?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (!isUsableAndroidId(normalized)) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("sleepdown-free-ai-quota-v1:$packageName:$normalized".toByteArray(Charsets.UTF_8))
        val bytes = digest.copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x50).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        return formatUuid(bytes)
    }

    internal fun isUsableAndroidId(normalized: String): Boolean {
        if (!hexDigitsRegex.matches(normalized)) return false
        if (normalized == "9774d56d682e549c") return false
        if (normalized.toSet().size <= 1) return false
        return true
    }

    private fun formatUuid(bytes: ByteArray): String {
        val hex = buildString(32) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append("0123456789abcdef"[v ushr 4])
                append("0123456789abcdef"[v and 0x0F])
            }
        }
        return buildString(36) {
            append(hex, 0, 8).append('-')
            append(hex, 8, 12).append('-')
            append(hex, 12, 16).append('-')
            append(hex, 16, 20).append('-')
            append(hex, 20, 32)
        }
    }
}

class FreeAiQuotaClient(
    private val apiBaseUrl: String = BuildConfig.SLEEPDOWN_API_BASE_URL
) {
    fun consume(
        context: Context,
        kind: FreeAiQuotaKind,
        actionId: String
    ): FreeAiQuotaConsumeResult {
        val appContext = context.applicationContext
        val subjectId = FreeAiQuotaIdentity.resolveSubjectId(appContext)
        val payload = FreeAiQuotaCodec.encodeConsumeBody(subjectId, kind, actionId)
        val connection = openConnection(
            path = "/api/v1/ai/quota/consume",
            method = "POST",
            installationId = subjectId
        )
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val headers = extractQuotaHeaders(connection)
            if (status in 200..299) {
                val body = connection.inputStream.use { it.readBytesLimited(MaxQuotaResponseBytes) }
                    .toString(Charsets.UTF_8)
                val result = FreeAiQuotaCodec.parseConsumeSuccess(body, kind, headers)
                FreeAiQuotaStore.recordConsumeResult(appContext, result)
                if (!result.allowed) {
                    throw FreeAiQuotaExceededException(
                        message = defaultQuotaExceededMessage(kind, result.limit),
                        kind = result.kind,
                        used = result.used,
                        limit = result.limit,
                        remaining = result.remaining,
                        resetAt = result.resetAt
                    )
                }
                return result
            }
            val errorBody = (connection.errorStream ?: connection.inputStream)
                ?.use { it.readBytesLimited(MaxQuotaResponseBytes) }
                ?.toString(Charsets.UTF_8)
                .orEmpty()
            val parsedError = FreeAiQuotaCodec.parseConsumeError(status, errorBody, kind, headers)
            if (status == 403 && parsedError.code == "AI_VERSION_BLOCKED") {
                throw FreeAiVersionBlockedException(
                    parsedError.message.ifBlank { "当前应用版本暂不支持每日免费 AI，请更新应用。" }
                )
            }
            if (status == 429) {
                parsedError.quota?.let { quota ->
                    FreeAiQuotaStore.recordConsumeResult(appContext, quota)
                    throw FreeAiQuotaExceededException(
                        message = parsedError.message.ifBlank { defaultQuotaExceededMessage(kind, quota.limit) },
                        kind = quota.kind,
                        used = quota.used,
                        limit = quota.limit,
                        remaining = quota.remaining,
                        resetAt = quota.resetAt
                    )
                }
                throw IllegalStateException(parsedError.message.ifBlank { "请求太频繁，请稍后重试" })
            }
            val detail = parsedError.message.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            throw IllegalStateException("每日免费 AI 额度校验失败 ($status)$detail")
        } catch (error: IOException) {
            throw IllegalStateException("无法连接每日免费 AI 额度服务，请检查网络后重试", error)
        } finally {
            connection.disconnect()
        }
    }

    fun fetchSnapshot(context: Context): FreeAiQuotaSnapshot {
        val appContext = context.applicationContext
        val subjectId = FreeAiQuotaIdentity.resolveSubjectId(appContext)
        val connection = openConnection(
            path = "/api/v1/ai/quota",
            method = "GET",
            installationId = subjectId
        )
        try {
            val status = connection.responseCode
            val headers = extractQuotaHeaders(connection)
            if (status in 200..299) {
                val body = connection.inputStream.use { it.readBytesLimited(MaxQuotaResponseBytes) }
                    .toString(Charsets.UTF_8)
                return FreeAiQuotaCodec.parseSnapshot(body, headers).also { snapshot ->
                    FreeAiQuotaStore.recordSnapshot(appContext, snapshot)
                }
            }
            val errorBody = connection.errorStream
                ?.use { it.readBytesLimited(MaxQuotaResponseBytes) }
                ?.toString(Charsets.UTF_8)
                .orEmpty()
            val parsedError = FreeAiQuotaCodec.parseConsumeError(status, errorBody, FreeAiQuotaKind.CHAT, headers)
            val detail = parsedError.message.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            throw IllegalStateException("获取每日免费 AI 额度失败 ($status)$detail")
        } catch (error: IOException) {
            throw IllegalStateException("无法获取每日免费 AI 额度，请检查网络后重试", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        path: String,
        method: String,
        installationId: String
    ): HttpURLConnection {
        val base = apiBaseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "未配置每日免费 AI 服务端地址" }
        return (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            useCaches = false
            connectTimeout = QuotaTimeoutMs
            readTimeout = QuotaTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-SleepDown-Version-Code", BuildConfig.VERSION_CODE.toString())
            setRequestProperty("X-SleepDown-Version-Name", BuildConfig.VERSION_NAME)
            setRequestProperty("X-SleepDown-Installation-Id", installationId)
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
    }

    private fun extractQuotaHeaders(connection: HttpURLConnection): Map<String, String?> = mapOf(
        "X-SleepDown-Quota-Kind" to connection.getHeaderField("X-SleepDown-Quota-Kind"),
        "X-SleepDown-Quota-Limit" to connection.getHeaderField("X-SleepDown-Quota-Limit"),
        "X-SleepDown-Quota-Used" to connection.getHeaderField("X-SleepDown-Quota-Used"),
        "X-SleepDown-Quota-Remaining" to connection.getHeaderField("X-SleepDown-Quota-Remaining"),
        "X-SleepDown-Quota-Reset" to connection.getHeaderField("X-SleepDown-Quota-Reset"),
        "X-SleepDown-Quota-Chat-Limit" to connection.getHeaderField("X-SleepDown-Quota-Chat-Limit"),
        "X-SleepDown-Quota-Chat-Remaining" to connection.getHeaderField("X-SleepDown-Quota-Chat-Remaining"),
        "X-SleepDown-Quota-Import-Limit" to connection.getHeaderField("X-SleepDown-Quota-Import-Limit"),
        "X-SleepDown-Quota-Import-Remaining" to connection.getHeaderField("X-SleepDown-Quota-Import-Remaining")
    )

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "额度服务响应过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    internal fun defaultQuotaExceededMessage(kind: FreeAiQuotaKind, limit: Long): String =
        if (limit <= 0L) {
            "每日免费 AI ${kind.displayLabel}暂未开放，请使用自己的 API Key。"
        } else {
            "今日免费 AI ${kind.displayLabel}次数已达上限（$limit/$limit），请明天再试或使用自己的 API Key。"
        }

    private companion object {
        const val QuotaTimeoutMs = 8_000
        const val MaxQuotaResponseBytes = 16 * 1024
    }
}
