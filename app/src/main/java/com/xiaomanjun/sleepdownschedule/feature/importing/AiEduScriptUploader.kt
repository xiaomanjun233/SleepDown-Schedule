package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.analytics.InstallationAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Background uploader for verified AI-generated edu import scripts (`POST /api/v1/edu-scripts`).
 */
internal object AiEduScriptUploader {
    private const val Tag = "AiEduScriptUploader"
    private const val Path = "/api/v1/edu-scripts"
    const val MaxScriptBytes = 200 * 1024
    const val MaxSchoolLength = 64

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    internal var postRequester: (url: String, body: String) -> Pair<Int, String> = ::defaultPost

    suspend fun upload(
        context: Context,
        school: String,
        pageUrl: String,
        script: String,
        courseCount: Int,
        note: String = "",
        baseUrl: String = BuildConfig.SLEEPDOWN_API_BASE_URL,
        adapterId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanSchool = cleanAiEduSchoolName(school)
            require(cleanSchool.isNotEmpty()) { "缺少学校名称" }
            require(adapterId == null || isSleepDownAiEduAdapterId(adapterId)) { "AI 适配 ID 无效" }
            require(script.isNotBlank()) { "脚本内容为空" }
            require(script.toByteArray(Charsets.UTF_8).size <= MaxScriptBytes) { "脚本内容超过 200 KiB" }
            val installationId = InstallationAnalytics(context.applicationContext).installationId()
            val payload = buildJsonObject {
                put("school", cleanSchool)
                put("page_url", eduScriptUploadPageUrl(pageUrl))
                put("note", buildString {
                    append("维护者：").append(AiEduScriptMaintainer)
                    adapterId?.let { append("\n适配 ID：").append(it) }
                    if (note.isNotBlank()) append('\n').append(note.trim())
                }.take(500))
                put("script", script)
                put("ok", true)
                put("course_count", courseCount)
                put("device_id", installationId)
                put("app_version", BuildConfig.VERSION_NAME)
                put("platform", "android")
            }.toString()
            val endpoint = baseUrl.trimEnd('/') + Path
            val (status, responseText) = postRequester(endpoint, payload)
            val parsed = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull()
            if (status !in 200..299) {
                val serverError = parsed?.extractErrorMessage()
                throw IOException(serverError ?: "上传失败（HTTP $status）")
            }
            (parsed?.get("id") as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.toLongOrNull()?.let { value -> value > 0L } == true }
                ?: throw IOException("服务器未返回有效的脚本编号")
        }
    }

    fun saveAndUploadInBackground(
        context: Context,
        verified: EduVerifiedScript,
        school: String
    ) {
        val cleanSchool = cleanAiEduSchoolName(school)
        if (cleanSchool.isEmpty()) return
        val previous = verified.submission.begin() ?: return
        val proposal = verified.proposal
        val appContext = context.applicationContext
        background.launch {
            var savedName = previous.savedName
            var uploaded = false
            try {
                if (savedName != cleanSchool) {
                    LocalEduAdapterStore.save(appContext, cleanSchool, proposal.url, proposal.source,
                        id = proposal.adapterId, maintainer = AiEduScriptMaintainer)
                    savedName = cleanSchool
                }
                upload(appContext, cleanSchool, proposal.url, proposal.source, verified.courseCount,
                    verified.note, adapterId = proposal.adapterId).getOrThrow()
                uploaded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(Tag, "保存或上传教务脚本失败：${failure.javaClass.simpleName}")
            } finally {
                verified.submission.finish(savedName, uploaded)
            }
            val message = when {
                uploaded -> "教务脚本已保存并上传"
                savedName != null -> "脚本已保存在本机，上传失败"
                else -> "教务脚本保存失败"
            }
            withContext(Dispatchers.Main) { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun JsonObject.extractErrorMessage(): String? {
        val err = this["error"]
        return when (err) {
            is JsonPrimitive -> err.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> (err["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun defaultPost(url: String, body: String): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            status to text
        } finally {
            connection.disconnect()
        }
    }
}

internal fun eduScriptUploadPageUrl(raw: String): String = runCatching {
    val cleaned = cleanLocalEduLoginUrl(raw)
    val uri = URI(cleaned)
    (uri.scheme + "://" + uri.rawAuthority + uri.rawPath).takeIf { it.toByteArray(Charsets.UTF_8).size <= 2_048 }
        ?: (uri.scheme + "://" + uri.rawAuthority + "/")
}.getOrDefault("")
