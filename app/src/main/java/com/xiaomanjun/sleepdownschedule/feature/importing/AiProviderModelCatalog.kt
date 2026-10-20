package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import androidx.core.content.edit
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Official account model discovery. The cache contains model metadata only, never credentials. */
internal object AiProviderModelCatalog {
    private const val Preferences = "ai_official_model_catalog_v1"
    private val json = Json { ignoreUnknownKeys = true }

    fun endpoint(profile: AiProviderProfile): String? {
        val base = normalizeAiBaseUrlForProvider(profile.id, profile.baseUrl).trimEnd('/')
        return when {
            profile.id == AiProviderPresets.deepSeek.id && base == AiProviderPresets.deepSeek.baseUrl -> "$base/models"
            profile.id == AiProviderPresets.mimo.id && base == AiProviderPresets.mimo.baseUrl -> "$base/models"
            else -> null
        }
    }

    fun cached(context: Context, profile: AiProviderProfile): List<AiModelOption> {
        if (endpoint(profile) == null) return emptyList()
        val encoded = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getString(profile.id, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AiModelOption>>(encoded) }.getOrDefault(emptyList())
    }

    internal fun save(context: Context, profile: AiProviderProfile, models: List<AiModelOption>) {
        require(endpoint(profile) != null && models.isNotEmpty())
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit {
            putString(profile.id, json.encodeToString(models))
        }
        AiImportSettingsStore.notifyModelCatalogChanged()
    }

    suspend fun refresh(context: Context, settings: AiImportSettings): List<AiModelOption> = withContext(Dispatchers.IO) {
        val endpoint = requireNotNull(endpoint(settings.profile)) { "当前接口没有启用官方模型发现" }
        require(settings.apiKey.isNotBlank()) { "请先填写 API Key，再刷新官方模型" }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setAiAuthHeader(settings.apiKey, settings.profile.authType)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            check(code in 200..299) { "官方模型列表读取失败（HTTP $code），请检查网络与 API Key 后重试" }
            val body = connection.inputStream.bufferedReader().use { it.readAiBoundedText() }
            val models = parseOfficialAiModels(body, settings.profile)
            currentCoroutineContext().ensureActive()
            save(context.applicationContext, settings.profile, models)
            models
        } finally {
            connection.disconnect()
        }
    }

    fun applyCached(context: Context, profile: AiProviderProfile): AiProviderProfile =
        profile.withModelCatalog(cached(context, profile))
}

internal fun AiProviderProfile.withModelCatalog(catalog: List<AiModelOption>): AiProviderProfile {
    if (catalog.isEmpty()) return this
    val selected = catalog.firstOrNull { it.model.equals(defaultModel.trim(), ignoreCase = true) }
    return copy(
        modelCatalog = catalog,
        availableModels = (catalog.map { it.model } + defaultModel).filter(String::isNotBlank).distinct(),
        supportsVision = selected?.supportsImageInput ?: supportsVision,
        capabilities = capabilities.copy(supportsImageInput = selected?.supportsImageInput ?: capabilities.supportsImageInput)
    )
}

internal fun parseOfficialAiModels(body: String, profile: AiProviderProfile): List<AiModelOption> {
    val root = Json.parseToJsonElement(body) as? JsonObject ?: error("官方模型列表格式无法识别")
    val data = root["data"] as? JsonArray ?: error("官方模型列表缺少 data")
    require(data.size <= 500) { "官方模型列表过大，请稍后重试" }
    val known = AiProviderPresets.modelOptions(profile.id)
    fun JsonObject.modalities(key: String) = (get(key) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return data.mapNotNull { raw ->
        val item = raw as? JsonObject ?: return@mapNotNull null
        val id = (item["id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (id.isEmpty()) return@mapNotNull null
        val output = item.modalities("output_modalities")
        val input = item.modalities("input_modalities")
        if (output != null && "text" !in output) return@mapNotNull null
        if (input != null && "text" !in input) return@mapNotNull null
        // MiMo lists ASR/TTS alongside conversational models without modality metadata.
        if (profile.id == AiProviderPresets.mimo.id &&
            Regex("-(asr|tts)(-|$)", RegexOption.IGNORE_CASE).containsMatchIn(id)) return@mapNotNull null
        val preset = known.firstOrNull { it.model == id }
        AiModelOption(
            label = (item["name"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                ?: preset?.label ?: id,
            model = id,
            supportsImageInput = input?.contains("image") ?: preset?.supportsImageInput ?: false,
            supportsResponses = preset?.supportsResponses ?: profile.capabilities.supportsResponses
        )
    }.distinctBy { it.model }.also { require(it.isNotEmpty()) { "官方暂未返回可用的文字对话模型，已保留原列表" } }
}

/** Editing a custom value does not change which concrete model row is selected. */
internal fun aiModelSelectionIndex(models: List<AiModelOption>, selected: String): Int =
    models.indexOfFirst { it.model.equals(selected.trim(), ignoreCase = true) }
        .takeIf { it >= 0 } ?: models.size
