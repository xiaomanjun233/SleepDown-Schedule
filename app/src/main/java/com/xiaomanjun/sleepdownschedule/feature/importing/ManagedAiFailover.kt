package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One turn can change route only before output/tool execution begins. */
internal class ManagedAiAttempt {
    var committed: Boolean = false
        private set
    fun commit() { committed = true }
    fun observe(phase: AiImportHttpPhase) {
        if (phase == AiImportHttpPhase.FIRST_EVENT || phase == AiImportHttpPhase.STREAM_END) commit()
    }
}

internal fun Throwable.canFailoverManagedAi(): Boolean {
    val chain = generateSequence(this) { it.cause }.take(16).toList()
    if (chain.any { it is CancellationException || it is InterruptedException ||
            (it is InterruptedIOException && it !is SocketTimeoutException) }) return false
    val response = chain.filterIsInstance<AiServiceResponseException>().firstOrNull()
    if (response != null) return response.httpStatus in setOf(401, 403, 404, 408, 429) ||
        response.httpStatus?.let { it in 500..599 } == true
    return chain.any { it is IOException }
}

/** Payload construction stays inside block so every candidate uses its own model, auth and protocol. */
internal suspend fun <T> runManagedAiCandidates(
    candidates: List<AiImportSettings>,
    onSwitch: (AiImportSettings) -> Unit = {},
    block: suspend (AiImportSettings, ManagedAiAttempt) -> T
): T {
    require(candidates.isNotEmpty()) { "每日免费 AI 暂无可用配置，请刷新配置或使用自己的 API Key。" }
    val routes = candidates.distinctBy { it.managedRouteId ?: it.profile.id }.take(8)
    for ((index, settings) in routes.withIndex()) {
        currentCoroutineContext().ensureActive()
        if (index > 0) onSwitch(settings)
        val attempt = ManagedAiAttempt()
        try {
            return block(settings, attempt)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (attempt.committed || !error.canFailoverManagedAi() || index == routes.lastIndex) throw error
        }
    }
    error("No AI route attempted")
}

internal suspend fun <T> withManagedAiFailover(
    context: Context,
    settings: AiImportSettings,
    requiresVision: Boolean = false,
    onSwitch: (AiImportSettings) -> Unit = {},
    block: suspend (AiImportSettings, ManagedAiAttempt) -> T
): T {
    if (!AiProviderPresets.isManagedFreeId(settings.profile.id)) return block(settings, ManagedAiAttempt())
    val candidates = SleepDownRemoteConfig.managedFreeCandidates(context, settings.profile.reasoningEffort)
        .filter { !requiresVision || AiProviderPresets.supportsImageInput(it.profile) }
    require(candidates.isNotEmpty() || !requiresVision) { "每日免费 AI 暂无可用的图片模型，请切换视觉模型。" }
    return runManagedAiCandidates(candidates, onSwitch, block)
}
