package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.ImportDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

internal data class EduAdapterSaveProposal(val id: String, val taskId: String, val name: String, val url: String, val source: String) {
    val adapterId: String get() = "SLEEPDOWN_AI_${id.replace("-", "")}"
}

internal const val AiEduScriptMaintainer = "SleepDown课程表AI"
private val AiEduAdapterId = Regex("SLEEPDOWN_AI_[0-9a-f]{32}")

internal fun isSleepDownAiEduAdapterId(id: String): Boolean = AiEduAdapterId.matches(id)

internal fun publishedAiEduAdapter(adapters: List<EduAdapter>, id: String): EduAdapter? =
    if (isSleepDownAiEduAdapterId(id)) adapters.singleOrNull { it.isAiDockAdapter() && it.adapterId == id } else null

internal data class EduScriptSaveState(val savedName: String? = null, val saving: Boolean = false, val uploaded: Boolean = false)

/** Shared by the preview and the post-import confirmation for this exact script. */
internal class EduScriptSubmission {
    private val mutable = MutableStateFlow(EduScriptSaveState())
    val state = mutable.asStateFlow()

    fun begin(): EduScriptSaveState? {
        val previous = mutable.value
        if (previous.saving || previous.uploaded) return null
        return previous.takeIf { mutable.compareAndSet(it, it.copy(saving = true)) }
    }

    fun finish(savedName: String?, uploaded: Boolean) {
        mutable.value = EduScriptSaveState(savedName, uploaded = uploaded)
    }
}

internal data class EduVerifiedScript(
    val proposal: EduAdapterSaveProposal,
    val path: String,
    val hash: String,
    val note: String = "",
    val courseCount: Int = 0,
    val draft: ImportDraft? = null,
    val submission: EduScriptSubmission = EduScriptSubmission()
) {
    fun matchesDraft(candidate: ImportDraft): Boolean =
        draft?.let { CanonicalScheduleJson.encode(it) == CanonicalScheduleJson.encode(candidate) } == true

    fun schoolNameForAutomaticSave(): String =
        submission.state.value.savedName ?: proposal.name.ifBlank { URI(proposal.url).host }
}

internal object EduAdapterSaveProposals {
    private val mutable = MutableStateFlow<EduAdapterSaveProposal?>(null)
    val current = mutable.asStateFlow()
    fun offer(value: EduAdapterSaveProposal) { mutable.value = value }
    fun clear(id: String) { mutable.compareAndSet(mutable.value?.takeIf { it.id == id }, null) }
}

/** Immutable successful source survives task-file cleanup, but an edit must be run again. */
internal object EduVerifiedScripts {
    private val mutable = MutableStateFlow<EduVerifiedScript?>(null)
    val current = mutable.asStateFlow()
    @Synchronized fun bindTask(taskId: String) {
        if (mutable.value?.proposal?.taskId != taskId) {
            mutable.value?.proposal?.let { EduAdapterSaveProposals.clear(it.id) }
            mutable.value = null
        }
    }
    @Synchronized fun clear(taskId: String) {
        mutable.value?.takeIf { it.proposal.taskId == taskId }?.let {
            EduAdapterSaveProposals.clear(it.proposal.id)
            mutable.value = null
        }
    }
    @Synchronized fun record(
        taskId: String,
        name: String,
        loginUrl: String,
        path: String,
        hash: String,
        source: String,
        note: String = "",
        courseCount: Int = 0,
        draft: ImportDraft? = null
    ) {
        val label = cleanAiEduSchoolName(name)
        val proposal = EduAdapterSaveProposal(UUID.randomUUID().toString(), taskId, label, cleanLocalEduLoginUrl(loginUrl), source)
        val normalizedPath = if (path.isBlank()) "" else normalize(path)
        mutable.value = EduVerifiedScript(
            proposal = proposal,
            path = normalizedPath,
            hash = hash,
            note = note,
            courseCount = courseCount,
            draft = draft
        )
    }
    @Synchronized fun fileChanged(taskId: String, path: String, hash: String) {
        val current = mutable.value ?: return
        if (current.proposal.taskId == taskId && current.path.isNotBlank() && current.path == normalize(path) && current.hash != hash) clear(taskId)
    }
    private fun normalize(path: String) = File(path).toPath().normalize().toString()
}

internal fun cleanAiEduSchoolName(value: String): String =
    value.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").trim().take(AiEduScriptUploader.MaxSchoolLength)

/** Preserve required CAS service/redirect parameters, discard transient credentials and fragments. */
internal fun cleanLocalEduLoginUrl(value: String): String {
    val uri = URI(value.trim())
    require(eduOrigin(uri.toString()) != null) { "请填写有效的教务登录地址" }
    val sensitive = Regex("(?i)password|token|ticket|cookie|secret|authorization|credential|sessionid|^code$|^state$|^sid$")
    val query = uri.rawQuery.orEmpty().split('&').filter { part ->
        part.isNotBlank() && !sensitive.containsMatchIn(URLDecoder.decode(part.substringBefore('='), "UTF-8"))
    }.joinToString("&")
    val path = uri.rawPath.orEmpty().replace(Regex("(?i);jsessionid=[^/;?]*"), "")
    val fragment = uri.rawFragment?.takeIf { it.startsWith("/") }?.let { "#$it" }.orEmpty()
    return "${uri.scheme.lowercase()}://${uri.rawAuthority}${path.ifBlank { "/" }}" +
        (if (query.isBlank()) "" else "?$query") + fragment
}
