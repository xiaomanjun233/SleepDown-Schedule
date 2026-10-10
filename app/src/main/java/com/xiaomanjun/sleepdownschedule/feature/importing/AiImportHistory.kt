package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.feature.backup.*

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
    const val DefaultRetentionDays = BackupFormatV1.DEFAULT_AI_IMPORT_HISTORY_RETENTION_DAYS
    val retentionOptions = BackupFormatV1.AI_IMPORT_HISTORY_RETENTION_OPTIONS

    fun retentionDays(context: Context): Int =
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getInt(KeyRetentionDays, DefaultRetentionDays)

    @Synchronized fun setRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit().putInt(KeyRetentionDays, days).apply()
        load(context)
    }

    @Synchronized fun record(context: Context, draft: ImportDraft, progress: AiEduImportProgress?) {
        val existing = load(context)
        val sameTask = progress?.taskId?.takeIf { it.isNotBlank() }?.let { taskId ->
            existing.firstOrNull { it.context?.taskId == taskId }
                ?: existing.firstOrNull { it.context?.let { previous -> sameCheckpointLineage(previous, progress) } == true }
        }
        if (sameTask != null && progress != null) {
            update(context, sameTask.id, draft, progress)
            return
        }
        val selected = selectedHistoryDraft(draft, progress)
        val payload = validatedHistoryPayload(selected)
        val newest = existing.firstOrNull()
        if (newest != null && newest.payload == payload && System.currentTimeMillis() - newest.createdAt < 10_000L) {
            progress?.let { writeContext(context, newest.id, it) }
            return
        }
        val entry = AiImportHistoryEntry(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            title = selected.courses.firstOrNull()?.name?.let { "$it 等 ${selected.courses.size} 门课" }
                ?: "AI 课表导入",
            prompt = progress?.userPrompt.orEmpty().take(500),
            sourceSummary = progress?.attachmentTitle.orEmpty().ifBlank { progress?.routeLabel.orEmpty() }.take(300),
            payload = payload,
            context = progress
        )
        val next = (listOf(entry) + existing).take(10)
        save(context, next)
    }

    @Synchronized fun load(context: Context): List<AiImportHistoryEntry> {
        val loaded = loadInternal(context)
        if (loaded.entries.size != loaded.rawEntryCount) save(context, loaded.entries)
        return loaded.entries
    }

    /** Read-only history path for backup; unlike load(), it never prunes or rewrites preferences. */
    @Synchronized fun loadForBackup(context: Context): List<AiImportHistoryEntry> = loadInternal(context).entries

    /**
     * Restores the non-secret history index after the Room commit. Context assets are already
     * materialized by the restore service; a missing optional context simply remains absent.
     */
    @Synchronized fun applyBackup(
        context: Context,
        entries: List<BackupAiImportHistoryEntry>,
        contextFilesByAssetId: Map<String, File>,
        retentionDays: Int
    ) {
        require(retentionDays in retentionOptions) { "不支持的 AI 导入历史保留天数: $retentionDays" }
        val retained = entries.take(10)
        retained.forEach { entry ->
            entry.contextAssetId?.let { assetId ->
                val file = contextFilesByAssetId[assetId] ?: return@let
                val expected = contextFile(context, entry.id, ensureDirectory = true)
                    ?: error("AI history stable ID 非法: ${entry.id}")
                require(file.canonicalFile == expected.canonicalFile) {
                    "AI history context asset 路径不在目标目录: ${entry.id}"
                }
                require(file.isFile) { "AI history context asset 不存在: ${entry.id}" }
            }
        }
        val array = JSONArray().apply {
            retained.forEach { entry ->
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
        val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        check(
            prefs.edit()
                .putInt(KeyRetentionDays, retentionDays)
                .putString(KeyEntries, array.toString())
                .commit()
        ) {
            "无法提交 AI import history preferences"
        }
    }

    @Synchronized fun cleanupUnreferencedContextFiles(context: Context, retainedIds: Set<String>) {
        contextDirectory(context).listFiles()?.forEach { file ->
            if (file.extension == "json" && file.nameWithoutExtension !in retainedIds) file.delete()
        }
    }

    private fun loadInternal(context: Context): LoadedHistory {
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
        return LoadedHistory(
            entries = entries,
            rawEntryCount = runCatching { JSONArray(raw.ifBlank { "[]" }).length() }.getOrDefault(0)
        )
    }

    @Synchronized fun delete(context: Context, id: String) = save(context, load(context).filterNot { it.id == id })

    @Synchronized fun update(
        context: Context,
        id: String,
        draft: ImportDraft,
        progress: AiEduImportProgress
    ) {
        val existing = load(context)
        val current = existing.firstOrNull { it.id == id } ?: return
        val retainedProgress = newerCheckpointProgress(current.context, progress)
        val selected = selectedHistoryDraft(draft, retainedProgress)
        val updated = current.copy(
            title = selected.courses.firstOrNull()?.name?.let { "$it 等 ${selected.courses.size} 门课" }
                ?: "AI 课表导入",
            prompt = retainedProgress.userPrompt.take(500),
            sourceSummary = retainedProgress.attachmentTitle.ifBlank { retainedProgress.routeLabel }.take(300),
            payload = validatedHistoryPayload(selected),
            context = retainedProgress
        )
        save(context, existing.map { if (it.id == id) updated else it })
    }

    @Synchronized fun updateMatching(
        context: Context,
        previousDraft: ImportDraft,
        revisedDraft: ImportDraft,
        progress: AiEduImportProgress
    ) {
        val previousPayload = draftToPayload(previousDraft).toString()
        val entries = load(context)
        val entry = entries.firstOrNull { progress.taskId.isNotBlank() && it.context?.taskId == progress.taskId }
            ?: entries.firstOrNull { it.context?.let { previous -> sameCheckpointLineage(previous, progress) } == true }
            ?: entries.firstOrNull { it.payload == previousPayload }
        if (entry == null) record(context, revisedDraft, progress)
        else update(context, entry.id, revisedDraft, progress)
    }

    @Synchronized fun clear(context: Context) {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit().remove(KeyEntries).apply()
        contextDirectory(context).listFiles()?.forEach(File::delete)
    }

    fun restore(entry: AiImportHistoryEntry, baseConfig: ScheduleConfigEntity): Result<ImportDraft> =
        entry.context?.let { progress -> progress.checkpoints.firstOrNull { it.id == progress.selectedCheckpointId }
            ?.restore(baseConfig) } ?: ScheduleImportParser.parseStoredDraft(entry.payload, baseConfig)
            .map { it.copy(source = ImportDraftSource.AI_EDU) }

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

    private fun contextFile(context: Context, id: String, ensureDirectory: Boolean = true): File? {
        val safeId = id.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return null
        val directory = File(context.filesDir, ContextDirectory)
        if (ensureDirectory) directory.mkdirs()
        return File(directory, "$safeId.json")
    }

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
        contextFile(context, id, ensureDirectory = false)
            ?.takeIf { it.isFile && it.length() <= MaxAiImportContextBytes }
            ?.let { file -> runCatching { progressFromJson(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull() }

    private data class LoadedHistory(
        val entries: List<AiImportHistoryEntry>,
        val rawEntryCount: Int
    )
}

internal const val MaxAiImportContextBytes = 2 * 1024 * 1024

internal fun sameCheckpointLineage(first: AiEduImportProgress, second: AiEduImportProgress): Boolean =
    first.checkpointLineageId.ifBlank { first.taskId }.let { lineage ->
        lineage.isNotBlank() && lineage == second.checkpointLineageId.ifBlank { second.taskId }
    }

internal fun newerCheckpointProgress(previous: AiEduImportProgress?, incoming: AiEduImportProgress): AiEduImportProgress =
    if (previous != null && ((sameCheckpointLineage(previous, incoming) &&
        previous.checkpointGeneration > incoming.checkpointGeneration) ||
        (previous.taskId == incoming.taskId && previous.checkpointRevision > incoming.checkpointRevision))) previous else incoming

private fun selectedHistoryDraft(fallback: ImportDraft, progress: AiEduImportProgress?): ImportDraft =
    progress?.checkpoints?.firstOrNull { it.id == progress.selectedCheckpointId && it.taskId == progress.taskId }
        ?.restore(fallback.config)?.getOrNull() ?: fallback

private fun validatedHistoryPayload(draft: ImportDraft): String {
    val validated = ScheduleImportParser.validateEditedDraft(draft)
    require(validated.courses.isNotEmpty()) { "没有完整课程，不能保存导入记录" }
    return draftToPayload(validated).toString().also {
        require(it.toByteArray(Charsets.UTF_8).size <= AiImportCheckpoint.MaxPayloadBytes) { "导入记录超过 256 KiB，未保存" }
    }
}

/** New history never stores raw screenshots; old optional image contexts remain readable. */
internal fun progressToJson(progress: AiEduImportProgress): JSONObject {
    val checkpointBounded = boundedCheckpointProgress(progress)
    var bounded = checkpointBounded.copy(
        taskId = progress.taskId.take(128),
        checkpointLineageId = progress.checkpointLineageId.take(128),
        steps = progress.steps.takeLast(100).map { historyText(it, 500) },
        screenshotPreviews = emptyList(),
        routeLabel = historyText(progress.routeLabel, 300),
        requestPreview = historyText(progress.requestPreview, 16_384),
        pageText = historyText(progress.pageText, 32_768),
        userPrompt = historyText(progress.userPrompt, 8_192),
        requestInstructions = historyText(progress.requestInstructions, 16_384),
        attachmentTitle = historyText(progress.attachmentTitle, 1_024),
        reasoningOutput = historyText(progress.reasoningOutput, 32_768),
        aiOutput = historyText(progress.aiOutput, 65_536),
        assistantMessage = historyText(progress.assistantMessage, 8_192),
        liveSummary = historyText(progress.liveSummary, 2_048),
        clarificationQuestions = progress.clarificationQuestions.take(20).map { historyText(it, 2_048) },
        conversationTurns = boundedImportConversationTurns(progress.conversationTurns),
        error = progress.error?.let { historyText(it, 2_048) },
        confirmActionLabel = historyText(progress.confirmActionLabel, 300),
        secondaryConfirmActionLabel = historyText(progress.secondaryConfirmActionLabel, 300),
        screenModeActionLabel = historyText(progress.screenModeActionLabel, 300),
        cancelActionLabel = historyText(progress.cancelActionLabel, 300),
        checkpointNotice = checkpointBounded.checkpointNotice?.let { historyText(it, 1_024) }
    )
    var result = progressJson(bounded)
    while (result.toString().toByteArray(Charsets.UTF_8).size > MaxAiImportContextBytes) {
        bounded = if (bounded.conversationTurns.isNotEmpty()) bounded.copy(conversationTurns = bounded.conversationTurns.drop(1))
        else {
            check(bounded.requestPreview.isNotEmpty() || bounded.pageText.isNotEmpty() ||
                bounded.reasoningOutput.isNotEmpty() || bounded.aiOutput.isNotEmpty() || bounded.requestInstructions.isNotEmpty()) {
                "导入历史上下文超过保存上限"
            }
            bounded.copy(requestPreview = "", pageText = "", reasoningOutput = "", aiOutput = "", requestInstructions = "")
        }
        result = progressJson(bounded)
    }
    return result
}

private fun historyText(value: String, limit: Int): String =
    if (value.length <= limit) value else value.take(limit - 14) + "\n…（历史记录已截断）"

internal fun boundedImportConversationTurns(turns: List<AiEduImportConversationTurn>): List<AiEduImportConversationTurn> =
    turns.takeLast(8).map { it.copy(
        userPrompt = historyText(it.userPrompt, 4_096), reasoningOutput = historyText(it.reasoningOutput, 8_192),
        aiOutput = historyText(it.aiOutput, 16_384), assistantMessage = historyText(it.assistantMessage, 4_096),
        artifactPayload = it.artifactPayload.takeIf { payload ->
            payload.toByteArray(Charsets.UTF_8).size <= AiImportCheckpoint.MaxPayloadBytes
        }.orEmpty(), status = historyText(it.status, 500)) }

private fun progressJson(progress: AiEduImportProgress): JSONObject = JSONObject()
    .put("schemaVersion", 3)
    .put("taskId", progress.taskId)
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
    .put("requestInstructions", progress.requestInstructions)
    .put("awaitingUserInput", progress.awaitingUserInput)
    .put("clarificationQuestions", JSONArray(progress.clarificationQuestions))
    .put("liveSummary", progress.liveSummary)
    .put("attachmentTitle", progress.attachmentTitle)
    .put("requestSent", progress.requestSent)
    .put("reasoningOutput", progress.reasoningOutput)
    .put("aiOutput", progress.aiOutput)
    .put("assistantMessage", progress.assistantMessage)
    .put("awaitingConfirmation", progress.awaitingConfirmation)
    .put("confirmActionLabel", progress.confirmActionLabel)
    .put("secondaryConfirmActionLabel", progress.secondaryConfirmActionLabel)
    .put("screenModeActionLabel", progress.screenModeActionLabel)
    .put("cancelActionLabel", progress.cancelActionLabel)
    .put("finished", progress.finished)
    .put("checkpoints", JSONArray().apply { progress.checkpoints.forEach { put(it.toJson()) } })
    .put("selectedCheckpointId", progress.selectedCheckpointId ?: JSONObject.NULL)
    .put("checkpointSelectionLocked", progress.checkpointSelectionLocked)
    .put("checkpointRevision", progress.checkpointRevision)
    .put("checkpointLineageId", progress.checkpointLineageId)
    .put("checkpointGeneration", progress.checkpointGeneration)
    .apply { progress.checkpointNotice?.let { put("checkpointNotice", it) } }
    .put("conversationTurns", JSONArray().apply {
        progress.conversationTurns.forEach { turn ->
            put(
                JSONObject()
                    .put("userPrompt", turn.userPrompt)
                    .put("reasoningOutput", turn.reasoningOutput)
                    .put("aiOutput", turn.aiOutput)
                    .put("artifactPayload", turn.artifactPayload)
                    .put("status", turn.status)
                    .put("assistantMessage", turn.assistantMessage)
            )
        }
    })
    .apply { progress.error?.let { put("error", it) } }

internal fun progressFromJson(root: JSONObject): AiEduImportProgress {
    val stepsJson = root.optJSONArray("steps") ?: JSONArray()
    val imagesJson = root.optJSONArray("screenshotPreviews") ?: JSONArray()
    val turnsJson = root.optJSONArray("conversationTurns") ?: JSONArray()
    val taskId = root.optString("taskId").take(128)
    val checkpointsJson = root.optJSONArray("checkpoints") ?: JSONArray()
    return boundedCheckpointProgress(AiEduImportProgress(
        taskId = taskId,
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
        requestInstructions = root.optString("requestInstructions"),
        awaitingUserInput = root.optBoolean("awaitingUserInput"),
        clarificationQuestions = root.optJSONArray("clarificationQuestions")?.let { questions ->
            List(questions.length()) { questions.optString(it) }
        }.orEmpty(),
        liveSummary = root.optString("liveSummary"),
        attachmentTitle = root.optString("attachmentTitle"),
        requestSent = root.optBoolean("requestSent"),
        reasoningOutput = root.optString("reasoningOutput"),
        aiOutput = root.optString("aiOutput"),
        assistantMessage = root.optString("assistantMessage"),
        awaitingConfirmation = root.optBoolean("awaitingConfirmation"),
        confirmActionLabel = root.optString("confirmActionLabel"),
        secondaryConfirmActionLabel = root.optString("secondaryConfirmActionLabel"),
        screenModeActionLabel = root.optString("screenModeActionLabel"),
        cancelActionLabel = root.optString("cancelActionLabel", "返回重抓"),
        finished = root.optBoolean("finished"),
        error = root.optString("error").takeIf { root.has("error") && it.isNotBlank() },
        checkpoints = buildList {
            // Parse the selected stage first so a large untrusted history cannot hide it past a cap.
            val selectedId = root.optString("selectedCheckpointId")
            val indexes = (0 until checkpointsJson.length()).toList()
            val selectedIndex = indexes.firstOrNull { checkpointsJson.optJSONObject(it)?.optString("id") == selectedId }
            val inspected = (listOfNotNull(selectedIndex) + indexes.filter { it != selectedIndex }).take(256)
            inspected.sorted().forEach { index -> checkpointsJson.optJSONObject(index)?.let {
                AiImportCheckpoint.fromJson(it, taskId)?.let(::add)
            } }
        },
        selectedCheckpointId = root.optString("selectedCheckpointId").takeUnless { it.isBlank() || it == "null" },
        checkpointSelectionLocked = root.optBoolean("checkpointSelectionLocked"),
        checkpointRevision = root.optLong("checkpointRevision").coerceAtLeast(0),
        checkpointLineageId = root.optString("checkpointLineageId").take(128),
        checkpointGeneration = root.optLong("checkpointGeneration").coerceAtLeast(0),
        checkpointNotice = root.optString("checkpointNotice").takeUnless { it.isBlank() || it == "null" },
        conversationTurns = buildList {
            for (index in 0 until turnsJson.length()) {
                val turn = turnsJson.optJSONObject(index) ?: continue
                add(
                    AiEduImportConversationTurn(
                        userPrompt = turn.optString("userPrompt"),
                        reasoningOutput = turn.optString("reasoningOutput"),
                        aiOutput = turn.optString("aiOutput"),
                        artifactPayload = turn.optString("artifactPayload"),
                        status = turn.optString("status", "已完成"),
                        assistantMessage = turn.optString("assistantMessage")
                    )
                )
            }
        }.let { turns ->
            // Version 1 included the current reply in its history. Version 2 stores only prior turns.
            if (root.optInt("schemaVersion", 1) < 2 && turns.lastOrNull()?.let {
                it.userPrompt == root.optString("userPrompt") && it.aiOutput == root.optString("aiOutput")
            } == true) turns.dropLast(1) else turns
        }
    ))
}

internal fun draftToPayload(draft: ImportDraft): JSONObject = JSONObject()
    .put("schemaVersion", 1)
    .put(
        "scheduleConfig",
        JSONObject()
            .put("totalWeeks", draft.config.totalWeeks)
            .put("periodAlignmentMode", draft.config.periodAlignmentMode.name)
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
                    .put("customStartTime", course.customStartTime ?: JSONObject.NULL)
                    .put("customEndTime", course.customEndTime ?: JSONObject.NULL)
                    .put("customPeriodTimes", course.customPeriodTimes ?: JSONObject.NULL)
                    .put("originalPeriodTimes", course.originalPeriodTimes ?: JSONObject.NULL)
                    .put("customColorArgb", course.customColorArgb ?: JSONObject.NULL)
            )
        }
    })
