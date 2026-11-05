package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

internal data class EduAdapterSaveProposal(val id: String, val taskId: String, val name: String, val url: String, val source: String)
internal data class EduVerifiedScript(val proposal: EduAdapterSaveProposal, val path: String, val hash: String, val saved: Boolean = false)

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
    @Synchronized fun record(taskId: String, name: String, loginUrl: String, path: String, hash: String, source: String) {
        val label = name.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").trim().take(80).ifBlank { "本地教务" }
        val proposal = EduAdapterSaveProposal(UUID.randomUUID().toString(), taskId, label, cleanLocalEduLoginUrl(loginUrl), source)
        mutable.value = EduVerifiedScript(proposal, normalize(path), hash)
    }
    @Synchronized fun fileChanged(taskId: String, path: String, hash: String) {
        val current = mutable.value ?: return
        if (current.proposal.taskId == taskId && current.path == normalize(path) && current.hash != hash) clear(taskId)
    }
    @Synchronized fun markSaved(id: String) {
        mutable.value?.takeIf { it.proposal.id == id }?.let { mutable.value = it.copy(saved = true) }
    }
    private fun normalize(path: String) = File(path).toPath().normalize().toString()
}

/** Preserve required CAS service/redirect parameters, discard transient credentials and fragments. */
internal fun cleanLocalEduLoginUrl(value: String): String {
    val uri = URI(value.trim())
    require(eduOrigin(uri.toString()) != null) { "请填写有效的教务登录地址" }
    val sensitive = Regex("(?i)password|token|ticket|cookie|secret|authorization|credential|sessionid|^code$|^state$|^sid$")
    val query = uri.rawQuery.orEmpty().split('&').filter { part ->
        part.isNotBlank() && !sensitive.containsMatchIn(URLDecoder.decode(part.substringBefore('='), "UTF-8"))
    }.joinToString("&")
    val path = uri.rawPath.orEmpty().replace(Regex("(?i);jsessionid=[^/;?]*"), "")
    return "${uri.scheme.lowercase()}://${uri.rawAuthority}${path.ifBlank { "/" }}" + if (query.isBlank()) "" else "?$query"
}
