package com.xiaomanjun.sleepdownschedule.feature.importing

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal data class EduNetEntry(
    val id: String,
    val ts: Long,
    val kind: String,
    val method: String,
    val url: String,
    val status: Int,
    val contentType: String,
    val reqBody: String,
    val respBody: String,
    val json: Boolean,
    val keys: List<String>,
    val size: Int,
    val error: String,
    val top: Boolean
)

/**
 * Thread-safe ring buffer of page-initiated fetch/XHR/form requests captured since document-start.
 */
internal class EduNetLogStore {
    private companion object {
        const val MAX_ENTRIES = 120
        const val MAX_TOTAL_CHARS = 2_000_000
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<EduNetEntry>>(emptyList())
    val entries: StateFlow<List<EduNetEntry>> = _entries.asStateFlow()

    fun append(raw: String) {
        if (raw.length > 500_000) return
        val entry = runCatching {
            jsonParser.parseToJsonElement(raw).jsonObject.toEntry()
        }.getOrNull() ?: return
        _entries.update { list ->
            val next = (list + entry).takeLast(MAX_ENTRIES)
            var total = next.sumOf { it.reqBody.length + it.respBody.length }
            var from = 0
            while (from < next.size - 1 && total > MAX_TOTAL_CHARS) {
                total -= next[from].reqBody.length + next[from].respBody.length
                from++
            }
            if (from == 0) next else next.subList(from, next.size).toList()
        }
    }

    fun snapshot(origin: String? = null): List<EduNetEntry> =
        _entries.value.filter { origin == null || eduOrigin(it.url) == origin }

    fun clear() {
        _entries.value = emptyList()
    }

    fun latestTopDocumentTs(origin: String? = null): Long =
        _entries.value.lastOrNull { it.kind == "document" && it.top && (origin == null || eduOrigin(it.url) == origin) }?.ts ?: 0L

    private fun JsonObject.toEntry(): EduNetEntry? {
        val id = str("id").takeIf { it.isNotBlank() } ?: return null
        val keys = (this["keys"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return EduNetEntry(
            id = id,
            ts = this["ts"]?.jsonPrimitive?.longOrNull ?: 0L,
            kind = str("kind").ifBlank { "fetch" },
            method = str("method").ifBlank { "GET" },
            url = str("url"),
            status = this["status"]?.jsonPrimitive?.intOrNull ?: 0,
            contentType = str("contentType").ifBlank { str("type") },
            reqBody = str("reqBody"),
            respBody = str("respBody"),
            json = this["json"]?.jsonPrimitive?.booleanOrNull ?: false,
            keys = keys,
            size = this["size"]?.jsonPrimitive?.intOrNull ?: 0,
            error = str("error"),
            top = this["top"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

internal class EduNetNativeBridge(private val log: EduNetLogStore) {
    @JavascriptInterface
    fun netLog(json: String) {
        log.append(json)
    }
}
