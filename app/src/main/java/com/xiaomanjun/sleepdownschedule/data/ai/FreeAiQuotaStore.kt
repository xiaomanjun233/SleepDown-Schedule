package com.xiaomanjun.sleepdownschedule.data.ai

import android.content.Context
import androidx.core.content.edit
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.RemoteAiQuotaPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object FreeAiQuotaReducer {
    fun normalizeForDate(
        existing: FreeAiQuotaState?,
        nowSeconds: Long = System.currentTimeMillis() / 1_000L
    ): FreeAiQuotaState? {
        existing ?: return null
        val today = FreeAiQuotaTime.currentDate(nowSeconds)
        val resetPassed = existing.resetAt != null && nowSeconds >= existing.resetAt
        if (existing.date == today && !resetPassed) return existing
        return FreeAiQuotaState(
            date = today,
            resetAt = null,
            timezone = existing.timezone,
            chat = existing.chat?.resetForNewDay(),
            import = existing.import?.resetForNewDay(),
            syncedFromServer = false
        )
    }

    fun applyBootstrapPolicy(
        existing: FreeAiQuotaState?,
        policy: RemoteAiQuotaPolicy?,
        nowSeconds: Long = System.currentTimeMillis() / 1_000L
    ): FreeAiQuotaState? {
        if (policy == null) return normalizeForDate(existing, nowSeconds)
        val base = normalizeForDate(existing, nowSeconds)
        val today = base?.date ?: FreeAiQuotaTime.currentDate(nowSeconds)
        val timezone = policy.timezone.ifBlank { base?.timezone ?: FreeAiQuotaTime.DEFAULT_TIMEZONE }
        return FreeAiQuotaState(
            date = today,
            resetAt = base?.resetAt,
            timezone = timezone,
            chat = mergeBucketLimit(base?.chat, policy.chatDailyLimit),
            import = mergeBucketLimit(base?.import, policy.importDailyLimit),
            syncedFromServer = base?.syncedFromServer == true
        )
    }

    fun applyConsumeResult(
        existing: FreeAiQuotaState?,
        result: FreeAiQuotaConsumeResult
    ): FreeAiQuotaState {
        val base = existing?.takeIf { it.date == result.date }
        val updatedBucket = FreeAiQuotaBucket(
            used = result.used,
            limit = result.limit,
            remaining = result.remaining
        )
        return FreeAiQuotaState(
            date = result.date,
            resetAt = result.resetAt,
            timezone = base?.timezone ?: FreeAiQuotaTime.DEFAULT_TIMEZONE,
            chat = if (result.kind == FreeAiQuotaKind.CHAT) updatedBucket else base?.chat,
            import = if (result.kind == FreeAiQuotaKind.IMPORT) updatedBucket else base?.import,
            syncedFromServer = true
        )
    }

    fun applySnapshot(snapshot: FreeAiQuotaSnapshot): FreeAiQuotaState =
        FreeAiQuotaState(
            date = snapshot.date,
            resetAt = snapshot.resetAt,
            timezone = snapshot.timezone.ifBlank { FreeAiQuotaTime.DEFAULT_TIMEZONE },
            chat = snapshot.chat,
            import = snapshot.import,
            syncedFromServer = true
        )

    private fun FreeAiQuotaBucket.resetForNewDay(): FreeAiQuotaBucket = FreeAiQuotaBucket(
        used = 0L,
        limit = limit,
        remaining = FreeAiQuotaBucket.computeRemaining(limit, 0L)
    )

    private fun mergeBucketLimit(existing: FreeAiQuotaBucket?, limit: Long): FreeAiQuotaBucket {
        val used = existing?.used
        return FreeAiQuotaBucket(
            used = used,
            limit = limit,
            remaining = FreeAiQuotaBucket.computeRemaining(limit, used)
        )
    }
}

object FreeAiQuotaStore {
    private const val PrefName = "sleepdown_free_ai_quota"
    private const val KeyStateJson = "quota_state_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutableState = MutableStateFlow<FreeAiQuotaState?>(null)
    val state: StateFlow<FreeAiQuotaState?> = mutableState.asStateFlow()

    @Synchronized
    fun current(context: Context, nowSeconds: Long = System.currentTimeMillis() / 1_000L): FreeAiQuotaState? {
        val loaded = mutableState.value ?: readFromPrefs(context)
        val normalized = FreeAiQuotaReducer.normalizeForDate(loaded, nowSeconds)
        if (normalized != mutableState.value) {
            mutableState.value = normalized
            if (normalized != null && normalized != loaded) {
                writeToPrefs(context, normalized)
            }
        }
        return normalized
    }

    @Synchronized
    fun syncBootstrapPolicy(
        context: Context,
        policy: RemoteAiQuotaPolicy?,
        serverTimeSeconds: Long? = null
    ): FreeAiQuotaState? {
        val nowSeconds = serverTimeSeconds ?: (System.currentTimeMillis() / 1_000L)
        val existing = mutableState.value ?: readFromPrefs(context)
        val updated = FreeAiQuotaReducer.applyBootstrapPolicy(existing, policy, nowSeconds)
        if (updated != mutableState.value) {
            mutableState.value = updated
            if (updated != null) writeToPrefs(context, updated)
        }
        return updated
    }

    @Synchronized
    fun recordConsumeResult(context: Context, result: FreeAiQuotaConsumeResult): FreeAiQuotaState {
        val existing = mutableState.value ?: readFromPrefs(context)
        val updated = FreeAiQuotaReducer.applyConsumeResult(existing, result)
        mutableState.value = updated
        writeToPrefs(context, updated)
        return updated
    }

    @Synchronized
    fun recordSnapshot(context: Context, snapshot: FreeAiQuotaSnapshot): FreeAiQuotaState {
        val updated = FreeAiQuotaReducer.applySnapshot(snapshot)
        mutableState.value = updated
        writeToPrefs(context, updated)
        return updated
    }

    private fun readFromPrefs(context: Context): FreeAiQuotaState? {
        val encoded = context.applicationContext
            .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            .getString(KeyStateJson, null)
            ?: return null
        return runCatching { json.decodeFromString<FreeAiQuotaState>(encoded) }.getOrNull()
    }

    private fun writeToPrefs(context: Context, state: FreeAiQuotaState) {
        context.applicationContext
            .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            .edit { putString(KeyStateJson, json.encodeToString(state)) }
    }
}
