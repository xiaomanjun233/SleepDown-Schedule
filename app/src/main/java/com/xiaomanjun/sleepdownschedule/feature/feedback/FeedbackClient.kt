package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.content.Context
import android.util.Base64
import com.xiaomanjun.sleepdownschedule.BuildConfig
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class FeedbackKind(val wire: String, val label: String) {
    Bug("bug", "问题反馈"),
    Feature("feature", "功能建议"),
}

internal enum class FeedbackStatus(val wire: String, val label: String, val step: Int) {
    Received("received", "已收到", 1),
    Reviewing("reviewing", "评估中", 2),
    Planned("planned", "已排期", 3),
    InProgress("in_progress", "开发中", 4),
    PendingRelease("pending_release", "待发布", 5),
    Released("released", "已发布", 6),
    Rejected("rejected", "暂不采纳", 0),
    Duplicate("duplicate", "重复反馈", 0),
    Unknown("", "状态更新", 0);

    companion object {
        const val StepCount = 6
    }
}

@Serializable
internal data class FeedbackItem(
    val id: String,
    val kind: String,
    val title: String,
    val description: String,
    val status: String,
    val note: String = "",
    val releaseVersion: String = "",
    val createdAt: Long,
    val updatedAt: Long,
) {
    val kindLabel: String get() = FeedbackKind.entries.find { it.wire == kind }?.label ?: "反馈"
    val stage: FeedbackStatus get() = FeedbackStatus.entries.firstOrNull { it.wire == status } ?: FeedbackStatus.Unknown
    val statusLabel: String
        get() = if (stage == FeedbackStatus.InProgress && kind == FeedbackKind.Bug.wire) "修复中" else stage.label
}

@Serializable
internal data class FeedbackPage(val items: List<FeedbackItem>, val total: Int)

@Serializable
private data class FeedbackSubmission(
    val id: String,
    val kind: String,
    val title: String,
    val description: String,
    val appVersion: String = BuildConfig.VERSION_NAME,
)

internal class FeedbackRequestException(message: String, val status: Int? = null) : IOException(message)

internal interface FeedbackService {
    suspend fun list(own: Boolean, page: Int, query: String): FeedbackPage
    suspend fun submit(id: String, kind: FeedbackKind, title: String, description: String): FeedbackItem
}

internal class FeedbackClient(
    private val context: Context,
    baseUrl: String = BuildConfig.SLEEPDOWN_API_BASE_URL,
) : FeedbackService {
    private val endpoint = "${baseUrl.trimEnd('/')}/api/v1/feedback"

    override suspend fun list(own: Boolean, page: Int, query: String): FeedbackPage = withContext(Dispatchers.IO) {
        val route = if (own) "mine" else "roadmap"
        val url = "$endpoint/$route".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PageSize.toString())
            .addQueryParameter("offset", (page * PageSize).toString())
        if (query.isNotBlank()) url.addQueryParameter("q", query)
        val request = Request.Builder().url(url.build())
        if (own) request.header(TokenHeader, token())
        execute<FeedbackPage>(request)
    }

    override suspend fun submit(id: String, kind: FeedbackKind, title: String, description: String) = withContext(Dispatchers.IO) {
        val payload = JsonCodec.encodeToString(FeedbackSubmission(id, kind.wire, title, description))
        execute<FeedbackItem>(Request.Builder().url(endpoint).header(TokenHeader, token())
            .post(payload.toRequestBody(JsonType)))
    }

    private fun token(): String = synchronized(IdentityLock) {
        val storage = context.getSharedPreferences("feedback_identity", Context.MODE_PRIVATE)
        storage.getString("token", null) ?: run {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            val value = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            if (!storage.edit().putString("token", value).commit()) {
                storage.edit().remove("token").apply()
                throw FeedbackRequestException("无法保存反馈凭据，请重试")
            }
            value
        }
    }

    private suspend inline fun <reified T> execute(request: Request.Builder): T =
        suspendCancellableCoroutine { continuation ->
            val call = Http.newCall(request.header("Accept", "application/json").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use { JsonCodec.decodeFromString<T>(readPayload(it)) } }
                    continuation.resumeWith(result)
                }
            })
        }

    private fun readPayload(response: Response): String {
        if (!response.isSuccessful) {
            val message = when (response.code) {
                400 -> "请检查反馈内容后重试"
                409 -> "反馈编号已使用，请重新提交"
                429 -> "操作太频繁，请稍后重试"
                else -> "反馈服务暂不可用，请稍后重试"
            }
            throw FeedbackRequestException(message, response.code)
        }
        val source = response.body?.source() ?: throw IOException("Empty feedback response")
        if (source.request(MaxResponseBytes + 1)) throw IOException("Feedback response is too large")
        return source.readUtf8()
    }

    companion object {
        const val PageSize = 20
        private const val MaxResponseBytes = 512L * 1024L
        private const val TokenHeader = "X-SleepDown-Feedback-Token"
        private val IdentityLock = Any()
        private val JsonType = "application/json; charset=utf-8".toMediaType()
        private val JsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val Http = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}
