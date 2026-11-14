package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableCatalog
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Short cloud code hand-off.
 *
 * The app uploads the compact canonical schedule document over HTTPS. The server
 * encrypts it at rest with the deployment master key, keeps it for 15 minutes and
 * returns a ten-character Crockford Base32 capability. The receiver asks the server
 * to decrypt the record and then imports the returned document locally; no AI call
 * is involved. The code is a secret bearer capability while it lives.
 */
internal data class CloudShortCodeCreated(val code: String, val expiresAt: Long) {
    val displayCode: String get() = CloudShortCode.display(code)
}

internal object CloudShortCode {
    const val CodeLength = 10
    const val MaxPayloadBytes = 32 * 1024
    private const val DisplayPrefix = "SD"
    private const val Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val CreatePath = "/api/v1/short-codes"
    private const val ResolvePath = "/api/v1/short-codes/resolve"
    private const val MaxResponseBytes = 64 * 1024
    private val json = Json { ignoreUnknownKeys = true }
    private val embedded = Regex("""(?i)\bSD[-\s]?([0-9A-HJKMNP-TV-Z]{5})[-\s]?([0-9A-HJKMNP-TV-Z]{5})\b""")
    private val labeled = Regex("""(?i)短口令[：:\s]*([0-9A-HJKMNP-TV-Z]{5})[-\s]?([0-9A-HJKMNP-TV-Z]{5})""")
    private val bare = Regex("""(?i)^(?:SD[-\s]?)?([0-9A-HJKMNP-TV-Z]{5})[-\s]?([0-9A-HJKMNP-TV-Z]{5})$""")

    fun buildPayload(
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        courses: List<CourseEntity>,
        catalog: TimetableCatalog = TimetableCatalog()
    ): String {
        val canonical = buildCanonicalScheduleJson(config, periods, courses, catalog)
        return CanonicalScheduleJson.encodeCompact(CanonicalScheduleJson.decode(canonical, config))
    }

    fun create(payload: String, baseUrl: String = BuildConfig.SLEEPDOWN_API_BASE_URL): CloudShortCodeCreated {
        require(payload.isNotBlank()) { "课表内容为空，无法生成短口令" }
        require(payload.toByteArray(Charsets.UTF_8).size <= MaxPayloadBytes) { "课表内容过大，无法生成短口令" }
        val body = buildJsonObject { put("payload", payload) }.toString()
        val root = parseResponse(request("POST", CreatePath, body, baseUrl), "生成")
        val code = root["code"]?.jsonPrimitive?.contentOrNull?.let(::normalize)
            ?: throw IllegalArgumentException("短口令服务返回了无效短口令")
        val expiresAt = root["expiresAt"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("短口令服务返回了无效有效期")
        return CloudShortCodeCreated(code, expiresAt)
    }

    fun resolve(input: String, baseUrl: String = BuildConfig.SLEEPDOWN_API_BASE_URL): String {
        val code = extractCode(input) ?: throw IllegalArgumentException("未找到 SleepDown 短口令")
        val body = buildJsonObject { put("code", code) }.toString()
        val root = parseResponse(request("POST", ResolvePath, body, baseUrl), "读取")
        val payload = root["payload"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("短口令服务返回了空课表")
        if ((root["expiresAt"]?.jsonPrimitive?.longOrNull ?: 0L) <= 0L) {
            throw IllegalArgumentException("短口令服务返回了无效有效期")
        }
        return payload
    }

    fun extractCode(input: String): String? {
        val trimmed = input.trim()
        bare.matchEntire(trimmed)?.let { match ->
            return normalize(match.groupValues[1] + match.groupValues[2])
        }
        embedded.find(trimmed)?.let { match ->
            return normalize(match.groupValues[1] + match.groupValues[2])
        }
        labeled.find(trimmed)?.let { match ->
            return normalize(match.groupValues[1] + match.groupValues[2])
        }
        return null
    }

    fun normalize(raw: String): String? {
        val builder = StringBuilder()
        var invalid = false
        raw.uppercase().forEach { character ->
            when (character) {
                '-', ' ', '\t', '\n', '\r' -> Unit
                'O' -> builder.append('0')
                'I', 'L' -> builder.append('1')
                else -> if (character in Alphabet) builder.append(character) else invalid = true
            }
        }
        if (invalid) return null
        var code = builder.toString()
        if (code.length == CodeLength + DisplayPrefix.length && code.startsWith(DisplayPrefix)) {
            code = code.substring(DisplayPrefix.length)
        }
        return code.takeIf { it.length == CodeLength && it.all { character -> character in Alphabet } }
    }

    fun display(code: String): String = "$DisplayPrefix-${code.take(5)}-${code.drop(5)}"

    private fun parseResponse(text: String, action: String) = runCatching {
        json.parseToJsonElement(text).jsonObject
    }.getOrElse {
        throw IllegalArgumentException("短口令服务返回了无效内容（$action）", it)
    }

    private fun request(method: String, path: String, body: String?, baseUrl: String): String {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-SleepDown-Version-Code", BuildConfig.VERSION_CODE.toString())
            setRequestProperty("X-SleepDown-Version-Name", BuildConfig.VERSION_NAME)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readLimited(MaxResponseBytes) }
                ?.toString(Charsets.UTF_8)
                .orEmpty()
            if (status !in 200..299) throw ShortCodeServiceException(status)
            text
        } catch (error: ShortCodeServiceException) {
            throw IllegalArgumentException(error.userMessage(), error)
        } catch (error: IOException) {
            throw IllegalArgumentException("无法连接短口令服务，请检查网络后重试", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= limit) { "短口令服务返回内容过大" }
        }
        return output.toByteArray()
    }

    private class ShortCodeServiceException(private val status: Int) : Exception() {
        fun userMessage(): String = when (status) {
            404 -> "短口令不存在或已过期，请让分享者重新生成"
            413 -> "课表内容过大，无法生成短口令"
            429 -> "短口令请求过于频繁，请稍后再试"
            503 -> "短口令服务暂不可用，请稍后重试"
            else -> "短口令服务返回错误（$status）"
        }
    }
}
