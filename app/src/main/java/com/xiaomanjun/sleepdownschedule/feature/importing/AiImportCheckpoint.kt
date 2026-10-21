package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.Collections
import java.util.UUID

enum class AiImportCheckpointKind { AI, MANUAL_EDIT, BASELINE }

/** A locally validated, detached artifact. No mutable draft or attachment is retained here. */
class AiImportCheckpoint private constructor(
    val id: String,
    val taskId: String,
    val createdAt: Long,
    val label: String,
    val kind: AiImportCheckpointKind,
    val payload: String,
    val courseCount: Int,
    private val baseConfig: ScheduleConfigEntity
) {
    fun restore(baseConfig: ScheduleConfigEntity = this.baseConfig): Result<ImportDraft> =
        parseCheckpointDraft(payload, baseConfig)

    internal fun forTask(taskId: String, baseConfig: ScheduleConfigEntity? = null): AiImportCheckpoint = AiImportCheckpoint(
        id, taskId, createdAt, label, kind, payload, courseCount, baseConfig ?: this.baseConfig)

    internal fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("taskId", taskId).put("createdAt", createdAt)
        .put("label", label).put("kind", kind.name).put("payload", payload)

    internal val retainedBytes: Int get() = toJson().toString().toByteArray(Charsets.UTF_8).size

    override fun equals(other: Any?): Boolean = other is AiImportCheckpoint &&
        id == other.id && taskId == other.taskId && createdAt == other.createdAt &&
        label == other.label && kind == other.kind && payload == other.payload

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val MaxRetainedCount = 32
        const val MaxRetainedBytes = 1024 * 1024
        const val MaxPayloadBytes = 256 * 1024
        internal const val LimitMessage = "已达到阶段保存上限（32 个或 1 MiB）。已有阶段和当前选择已保留，请从现有阶段继续预览，或新建导入。"

        internal fun create(
            taskId: String,
            draft: ImportDraft,
            label: String,
            kind: AiImportCheckpointKind = AiImportCheckpointKind.AI,
            id: String = UUID.randomUUID().toString(),
            createdAt: Long = System.currentTimeMillis()
        ): AiImportCheckpoint {
            require(taskId.isNotBlank() && taskId.length <= 128) { "导入任务无效" }
            require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "阶段标识无效" }
            val validated = ScheduleImportParser.validateEditedDraft(draft)
            require(validated.courses.isNotEmpty()) { "课表没有完整课程，不能保存为阶段" }
            val payload = draftToPayload(validated).toString()
            require(payload.toByteArray(Charsets.UTF_8).size <= MaxPayloadBytes) { "单个阶段超过 256 KiB，未保存；已有选择不变" }
            return AiImportCheckpoint(id, taskId, createdAt, label.take(120), kind, payload,
                validated.courses.size, validated.config)
        }

        internal fun fromJson(item: JSONObject, taskId: String): AiImportCheckpoint? = runCatching {
            require(item.optString("taskId", taskId) == taskId) { "阶段不属于当前任务" }
            val draft = parseCheckpointDraft(item.getString("payload"), defaultConfig()).getOrThrow()
            create(taskId, draft, item.optString("label", "已校验结果"),
                AiImportCheckpointKind.valueOf(item.optString("kind", "AI")),
                item.getString("id"), item.optLong("createdAt"))
        }.getOrNull()
    }
}

private val checkpointJson = Json { ignoreUnknownKeys = true }

/** Never run the AI normalization/placeholder fallback on manually edited or persisted JSON. */
internal fun parseCheckpointDraft(input: String, baseConfig: ScheduleConfigEntity): Result<ImportDraft> = runCatching {
    require(input.toByteArray(Charsets.UTF_8).size <= AiImportCheckpoint.MaxPayloadBytes) { "阶段 JSON 超过 256 KiB" }
    val payload = checkpointJson.decodeFromString<ScheduleImportPayload>(input)
    require(payload.courses.isNotEmpty()) { "课表没有完整课程，不能保存为阶段" }
    ScheduleImportParser.parseStoredDraft(input, baseConfig).getOrThrow().copy(source = ImportDraftSource.AI_EDU)
}

internal fun immutableCheckpoints(items: List<AiImportCheckpoint>): List<AiImportCheckpoint> =
    Collections.unmodifiableList(ArrayList(items))

/** Call only for a new explicit history selection/edit, never for an automatic save retry. */
fun forkImportCheckpointHistory(progress: AiEduImportProgress): AiEduImportProgress =
    AiEduImportProgressSession.forkHistoryCheckpointProgress(progress)

/** Pure reducers also serve history screens, without changing an unrelated running session. */
fun selectImportCheckpoint(
    progress: AiEduImportProgress,
    checkpointId: String,
    baseConfig: ScheduleConfigEntity
): Result<Pair<AiEduImportProgress, ImportDraft>> = runCatching {
    val checkpoint = progress.checkpoints.firstOrNull { it.id == checkpointId && it.taskId == progress.taskId }
        ?: error("这个阶段不属于当前导入任务")
    val draft = checkpoint.restore(baseConfig).getOrThrow()
    progress.copy(selectedCheckpointId = checkpoint.id, checkpointSelectionLocked = true,
        checkpointRevision = progress.checkpointRevision + 1) to draft
}

fun editImportCheckpoint(
    progress: AiEduImportProgress,
    checkpointId: String,
    json: String,
    baseConfig: ScheduleConfigEntity
): Result<Pair<AiEduImportProgress, AiImportCheckpoint>> = runCatching {
    require(progress.checkpoints.any { it.id == checkpointId && it.taskId == progress.taskId }) {
        "这个阶段不属于当前导入任务"
    }
    val draft = parseCheckpointDraft(json, baseConfig).getOrThrow()
    appendImportCheckpoint(progress, draft, "手动编辑", AiImportCheckpointKind.MANUAL_EDIT).getOrThrow()
}

/** A user edit always creates a new immutable version, including a deliberate no-op edit. */
internal fun appendImportCheckpoint(
    progress: AiEduImportProgress,
    draft: ImportDraft,
    label: String,
    kind: AiImportCheckpointKind
): Result<Pair<AiEduImportProgress, AiImportCheckpoint>> = runCatching {
    val checkpoint = AiImportCheckpoint.create(progress.taskId, draft, label, kind)
    val manual = kind == AiImportCheckpointKind.MANUAL_EDIT
    val duplicate = if (manual) null else progress.checkpoints.lastOrNull {
        it.taskId == progress.taskId && it.payload == checkpoint.payload
    }
    if (duplicate != null) {
        return@runCatching progress.copy(selectedCheckpointId = if (progress.checkpointSelectionLocked)
            progress.selectedCheckpointId else duplicate.id,
            checkpointRevision = progress.checkpointRevision + 1) to duplicate
    }
    require(progress.checkpoints.size < AiImportCheckpoint.MaxRetainedCount &&
        progress.checkpoints.sumOf { it.retainedBytes }.toLong() + checkpoint.retainedBytes <= AiImportCheckpoint.MaxRetainedBytes) {
        AiImportCheckpoint.LimitMessage
    }
    progress.copy(
        checkpoints = immutableCheckpoints(progress.checkpoints + checkpoint),
        selectedCheckpointId = if (manual || !progress.checkpointSelectionLocked) checkpoint.id else progress.selectedCheckpointId,
        checkpointSelectionLocked = manual || progress.checkpointSelectionLocked,
        checkpointNotice = null,
        checkpointRevision = progress.checkpointRevision + 1
    ) to checkpoint
}

/** Seed legacy history only once, retaining its existing result and all valid modern stages. */
fun ensureImportCheckpoint(progress: AiEduImportProgress, draft: ImportDraft): AiEduImportProgress {
    if (progress.checkpoints.isNotEmpty()) return progress
    val taskId = progress.taskId.ifBlank { "history-${UUID.randomUUID()}" }
    val scoped = progress.copy(taskId = taskId, checkpointLineageId = progress.checkpointLineageId.ifBlank { taskId })
    return appendImportCheckpoint(scoped, draft, "已保存结果", AiImportCheckpointKind.BASELINE)
        .getOrElse { return scoped.copy(checkpointNotice = it.message) }.first
}

/** Corrupt/oversized history is bounded, always reserving space for a valid selected stage first. */
internal fun boundedCheckpointProgress(progress: AiEduImportProgress): AiEduImportProgress {
    val candidates = progress.checkpoints.filter { it.taskId == progress.taskId }.distinctBy { it.id }
    val chosen = candidates.firstOrNull { it.id == progress.selectedCheckpointId }
    val accepted = mutableListOf<AiImportCheckpoint>()
    var bytes = 0L
    (listOfNotNull(chosen) + candidates.filter { it.id != chosen?.id }).forEach { checkpoint ->
        val size = checkpoint.retainedBytes
        if (accepted.size < AiImportCheckpoint.MaxRetainedCount && bytes + size <= AiImportCheckpoint.MaxRetainedBytes) {
            accepted += checkpoint
            bytes += size
        }
    }
    val ids = accepted.mapTo(HashSet()) { it.id }
    val retained = candidates.filter { it.id in ids }
    val selected = chosen?.id?.takeIf { it in ids } ?: retained.lastOrNull()?.id
    return progress.copy(checkpoints = immutableCheckpoints(retained), selectedCheckpointId = selected,
        checkpointSelectionLocked = progress.checkpointSelectionLocked && chosen != null && chosen.id == selected,
        checkpointNotice = if (retained.size != progress.checkpoints.size) AiImportCheckpoint.LimitMessage else progress.checkpointNotice)
}
