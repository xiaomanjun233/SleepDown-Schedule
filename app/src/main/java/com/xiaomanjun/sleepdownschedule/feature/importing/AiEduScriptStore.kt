package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import kotlin.random.Random

internal data class AiEduScriptRecord(
    val id: String,
    val createdAtMs: Long,
    val pageUrl: String,
    val note: String,
    val script: String,
    val ok: Boolean,
    val courseCount: Int,
    val error: String?
) {
    companion object {
        fun create(
            pageUrl: String,
            note: String,
            script: String,
            ok: Boolean,
            courseCount: Int,
            error: String?
        ): AiEduScriptRecord {
            val now = System.currentTimeMillis()
            return AiEduScriptRecord(
                id = "${now.toString(36)}-${Random.nextLong().toString(36)}",
                createdAtMs = now,
                pageUrl = pageUrl,
                note = note,
                script = script,
                ok = ok,
                courseCount = courseCount,
                error = error
            )
        }
    }
}

/**
 * Local history of AI-generated edu import scripts (`ai_edu_scripts.json`, bounded to 60 records).
 */
internal object AiEduScriptStore {
    private const val FileName = "ai_edu_scripts.json"
    private const val MaxRecords = 60
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(context: Context): List<AiEduScriptRecord> = withContext(Dispatchers.IO) {
        readAll(context.applicationContext)
    }

    suspend fun append(context: Context, record: AiEduScriptRecord): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        synchronized(this) {
            runCatching {
                val updated = (readAll(app) + record).takeLast(MaxRecords)
                val payload = buildJsonArray { updated.forEach { add(it.toJson()) } }.toString()
                val file = AtomicFile(File(app.filesDir, FileName))
                val out = file.startWrite()
                try {
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                    throw e
                }
                true
            }.getOrDefault(false)
        }
    }

    private fun readAll(appContext: Context): List<AiEduScriptRecord> = runCatching {
        val target = File(appContext.filesDir, FileName)
        if (!target.isFile) return@runCatching emptyList()
        val text = AtomicFile(target).readFully().toString(Charsets.UTF_8)
        json.parseToJsonElement(text).jsonArray.mapNotNull { it.asRecord() }
    }.getOrDefault(emptyList())

    private fun AiEduScriptRecord.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("createdAtMs", createdAtMs)
        put("pageUrl", pageUrl)
        put("note", note)
        put("script", script)
        put("ok", ok)
        put("courseCount", courseCount)
        error?.let { put("error", it) }
    }

    private fun JsonElement.asRecord(): AiEduScriptRecord? {
        val obj = this as? JsonObject ?: return null
        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val script = (obj["script"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (script.isBlank()) return null
        return AiEduScriptRecord(
            id = id,
            createdAtMs = (obj["createdAtMs"] as? JsonPrimitive)?.longOrNull ?: 0L,
            pageUrl = (obj["pageUrl"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            note = (obj["note"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            script = script,
            ok = (obj["ok"] as? JsonPrimitive)?.booleanOrNull ?: false,
            courseCount = (obj["courseCount"] as? JsonPrimitive)?.intOrNull ?: 0,
            error = (obj["error"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        )
    }
}
