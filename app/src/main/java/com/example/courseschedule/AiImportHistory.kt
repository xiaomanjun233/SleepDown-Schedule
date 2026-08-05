package com.example.courseschedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AiImportHistoryEntry(
    val id: String,
    val createdAt: Long,
    val title: String,
    val prompt: String,
    val sourceSummary: String,
    val payload: String,
    val context: AiEduImportProgress? = null
)

object AiImportHistoryStore {
    private const val PrefsName = "ai_import_history"
    private const val KeyEntries = "entries"
    private const val KeyRetentionDays = "retention_days"
    private const val ContextDirectory = "ai_import_history"
    const val DefaultRetentionDays = 30
    val retentionOptions = listOf(7, 30, 90, 0)

    fun retentionDays(context: Context): Int =
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getInt(KeyRetentionDays, DefaultRetentionDays)

    fun setRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit().putInt(KeyRetentionDays, days).apply()
        load(context)
    }

    fun record(context: Context, draft: ImportDraft, progress: AiEduImportProgress?) {
        val payload = draftToPayload(draft).toString()
        val existing = load(context)
        val newest = existing.firstOrNull()
        if (newest != null && newest.payload == payload && System.currentTimeMillis() - newest.createdAt < 10_000L) {
            progress?.let { writeContext(context, newest.id, it) }
            return
        }
        val entry = AiImportHistoryEntry(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            title = draft.courses.firstOrNull()?.name?.let { "$it 等 ${draft.courses.size} 门课" }
                ?: "AI 课表导入",
            prompt = progress?.userPrompt.orEmpty().take(500),
            sourceSummary = progress?.attachmentTitle.orEmpty().ifBlank { progress?.routeLabel.orEmpty() }.take(300),
            payload = payload,
            context = progress
        )
        val next = (listOf(entry) + existing).take(10)
        save(context, next)
    }

    fun load(context: Context): List<AiImportHistoryEntry> {
        val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString(KeyEntries, null).orEmpty()
        val cutoff = retentionDays(context).takeIf { it > 0 }
            ?.let { System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it.toLong()) }
        val entries = runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        AiImportHistoryEntry(
                            id = item.optString("id"),
                            createdAt = item.optLong("createdAt"),
                            title = item.optString("title"),
                            prompt = item.optString("prompt"),
                            sourceSummary = item.optString("sourceSummary"),
                            payload = item.optString("payload"),
                            context = readContext(context, item.optString("id"))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).filter { cutoff == null || it.createdAt >= cutoff }.take(10)
        if (entries.size != runCatching { JSONArray(raw.ifBlank { "[]" }).length() }.getOrDefault(0)) save(context, entries)
        return entries
    }

    fun delete(context: Context, id: String) = save(context, load(context).filterNot { it.id == id })

    fun update(
        context: Context,
        id: String,
        draft: ImportDraft,
        progress: AiEduImportProgress
    ) {
        val existing = load(context)
        val current = existing.firstOrNull { it.id == id } ?: return
        val updated = current.copy(
            title = draft.courses.firstOrNull()?.name?.let { "$it 等 ${draft.courses.size} 门课" }
                ?: "AI 课表导入",
            prompt = progress.userPrompt.take(500),
            sourceSummary = progress.attachmentTitle.ifBlank { progress.routeLabel }.take(300),
            payload = draftToPayload(draft).toString(),
            context = progress
        )
        save(context, existing.map { if (it.id == id) updated else it })
    }

    fun updateMatching(
        context: Context,
        previousDraft: ImportDraft,
        revisedDraft: ImportDraft,
        progress: AiEduImportProgress
    ) {
        val previousPayload = draftToPayload(previousDraft).toString()
        val entry = load(context).firstOrNull { it.payload == previousPayload }
        if (entry == null) record(context, revisedDraft, progress)
        else update(context, entry.id, revisedDraft, progress)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit().remove(KeyEntries).apply()
        contextDirectory(context).listFiles()?.forEach(File::delete)
    }

    fun restore(entry: AiImportHistoryEntry, baseConfig: ScheduleConfigEntity): Result<ImportDraft> =
        ScheduleImportParser.parse(entry.payload, baseConfig).map { it.copy(source = ImportDraftSource.AI_EDU) }

    private fun save(context: Context, entries: List<AiImportHistoryEntry>) {
        val array = JSONArray().apply {
            entries.take(10).forEach { entry ->
                put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("createdAt", entry.createdAt)
                        .put("title", entry.title)
                        .put("prompt", entry.prompt)
                        .put("sourceSummary", entry.sourceSummary)
                        .put("payload", entry.payload)
                )
            }
        }
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit().putString(KeyEntries, array.toString()).apply()
        entries.forEach { entry -> entry.context?.let { writeContext(context, entry.id, it) } }
        val retainedIds = entries.mapTo(mutableSetOf()) { it.id }
        contextDirectory(context).listFiles()?.forEach { file ->
            if (file.extension == "json" && file.nameWithoutExtension !in retainedIds) file.delete()
        }
    }

    private fun contextDirectory(context: Context): File =
        File(context.filesDir, ContextDirectory).apply { mkdirs() }

    private fun contextFile(context: Context, id: String): File? =
        id.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
            ?.let { File(contextDirectory(context), "$it.json") }

    private fun writeContext(context: Context, id: String, progress: AiEduImportProgress) {
        val target = contextFile(context, id) ?: return
        runCatching {
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(progressToJson(progress).toString(), Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
                temporary.delete()
            }
        }
    }

    private fun readContext(context: Context, id: String): AiEduImportProgress? =
        contextFile(context, id)
            ?.takeIf(File::isFile)
            ?.let { file -> runCatching { progressFromJson(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull() }
}

private fun progressToJson(progress: AiEduImportProgress): JSONObject = JSONObject()
    .put("schemaVersion", 1)
    .put("steps", JSONArray(progress.steps))
    .put("routeLabel", progress.routeLabel)
    .put("requestPreview", progress.requestPreview)
    .put("pageText", progress.pageText)
    .apply { progress.hasReadablePageText?.let { put("hasReadablePageText", it) } }
    .put("screenshotPreviews", JSONArray().apply {
        progress.screenshotPreviews.forEach { image ->
            put(
                JSONObject()
                    .put("pageIndex", image.pageIndex)
                    .put("mimeType", image.mimeType)
                    .put("base64", image.base64)
            )
        }
    })
    .put("userPrompt", progress.userPrompt)
    .put("attachmentTitle", progress.attachmentTitle)
    .put("requestSent", progress.requestSent)
    .put("reasoningOutput", progress.reasoningOutput)
    .put("aiOutput", progress.aiOutput)
    .put("awaitingConfirmation", progress.awaitingConfirmation)
    .put("confirmActionLabel", progress.confirmActionLabel)
    .put("secondaryConfirmActionLabel", progress.secondaryConfirmActionLabel)
    .put("screenModeActionLabel", progress.screenModeActionLabel)
    .put("cancelActionLabel", progress.cancelActionLabel)
    .put("finished", progress.finished)
    .put("conversationTurns", JSONArray().apply {
        val turns = progress.conversationTurns.ifEmpty {
            listOf(
                AiEduImportConversationTurn(
                    userPrompt = progress.userPrompt,
                    reasoningOutput = progress.reasoningOutput,
                    aiOutput = progress.aiOutput
                )
            )
        }
        turns.forEach { turn ->
            put(
                JSONObject()
                    .put("userPrompt", turn.userPrompt)
                    .put("reasoningOutput", turn.reasoningOutput)
                    .put("aiOutput", turn.aiOutput)
            )
        }
    })
    .apply { progress.error?.let { put("error", it) } }

private fun progressFromJson(root: JSONObject): AiEduImportProgress {
    val stepsJson = root.optJSONArray("steps") ?: JSONArray()
    val imagesJson = root.optJSONArray("screenshotPreviews") ?: JSONArray()
    val turnsJson = root.optJSONArray("conversationTurns") ?: JSONArray()
    return AiEduImportProgress(
        steps = buildList {
            for (index in 0 until stepsJson.length()) add(stepsJson.optString(index))
        },
        routeLabel = root.optString("routeLabel"),
        requestPreview = root.optString("requestPreview"),
        pageText = root.optString("pageText"),
        hasReadablePageText = if (root.has("hasReadablePageText")) root.optBoolean("hasReadablePageText") else null,
        screenshotPreviews = buildList {
            for (index in 0 until imagesJson.length()) {
                val image = imagesJson.optJSONObject(index) ?: continue
                val base64 = image.optString("base64")
                if (base64.isNotBlank()) {
                    add(
                        RenderedPageImage(
                            pageIndex = image.optInt("pageIndex", index),
                            mimeType = image.optString("mimeType", "image/jpeg"),
                            base64 = base64
                        )
                    )
                }
            }
        },
        userPrompt = root.optString("userPrompt", "帮我按规则导入这份课表"),
        attachmentTitle = root.optString("attachmentTitle"),
        requestSent = root.optBoolean("requestSent"),
        reasoningOutput = root.optString("reasoningOutput"),
        aiOutput = root.optString("aiOutput"),
        awaitingConfirmation = root.optBoolean("awaitingConfirmation"),
        confirmActionLabel = root.optString("confirmActionLabel"),
        secondaryConfirmActionLabel = root.optString("secondaryConfirmActionLabel"),
        screenModeActionLabel = root.optString("screenModeActionLabel"),
        cancelActionLabel = root.optString("cancelActionLabel", "返回重抓"),
        finished = root.optBoolean("finished"),
        error = root.optString("error").takeIf { root.has("error") && it.isNotBlank() },
        conversationTurns = buildList {
            for (index in 0 until turnsJson.length()) {
                val turn = turnsJson.optJSONObject(index) ?: continue
                add(
                    AiEduImportConversationTurn(
                        userPrompt = turn.optString("userPrompt"),
                        reasoningOutput = turn.optString("reasoningOutput"),
                        aiOutput = turn.optString("aiOutput")
                    )
                )
            }
        }
    )
}

internal fun draftToPayload(draft: ImportDraft): JSONObject = JSONObject()
    .put("schemaVersion", 1)
    .put(
        "scheduleConfig",
        JSONObject()
            .put("totalWeeks", draft.config.totalWeeks)
            .put("periods", JSONArray().apply {
                draft.periods.sortedBy { it.periodIndex }.forEach { period ->
                    put(JSONObject().put("index", period.periodIndex).put("startTime", period.startTime).put("endTime", period.endTime))
                }
            })
    )
    .put("courses", JSONArray().apply {
        draft.courses.forEach { course ->
            put(
                JSONObject()
                    .put("name", course.name)
                    .put("teacher", course.teacher ?: JSONObject.NULL)
                    .put("location", course.location ?: JSONObject.NULL)
                    .put("weekday", course.weekday)
                    .put("periods", JSONArray(course.periods))
                    .put("weeks", JSONArray(course.weeks))
                    .put("weekParity", course.weekParity.name)
                    .put("note", course.note ?: JSONObject.NULL)
            )
        }
    })
