package com.xiaomanjun.sleepdownschedule.data.ai

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreeAiQuotaGate {
    suspend fun ensureAvailableIfManagedFree(
        context: Context,
        isManagedFree: Boolean,
        kind: FreeAiQuotaKind
    ): FreeAiQuotaState? {
        if (!isManagedFree) return null
        val appContext = context.applicationContext
        val current = FreeAiQuotaStore.current(appContext)
        val state = if (current == null || !current.syncedFromServer) {
            withContext(Dispatchers.IO) {
                runCatching { FreeAiQuotaClient().fetchSnapshot(appContext) }
                    .map { FreeAiQuotaStore.current(appContext) }
                    .getOrNull() ?: current
            }
        } else {
            current
        }
        val bucket = state?.bucket(kind)
        if (bucket != null && bucket.isExhausted) {
            val client = FreeAiQuotaClient()
            throw FreeAiQuotaExceededException(
                message = client.defaultQuotaExceededMessage(kind, bucket.limit),
                kind = kind,
                used = bucket.used ?: bucket.limit,
                limit = bucket.limit,
                remaining = bucket.remaining ?: 0L,
                resetAt = state.resetAt ?: 0L
            )
        }
        return state
    }

    suspend fun consumeIfManagedFree(
        context: Context,
        isManagedFree: Boolean,
        kind: FreeAiQuotaKind,
        actionId: String = UUID.randomUUID().toString()
    ): FreeAiQuotaConsumeResult? {
        if (!isManagedFree) return null
        return withContext(Dispatchers.IO) {
            FreeAiQuotaClient().consume(context, kind, actionId)
        }
    }

    suspend fun refreshSnapshotOnDemand(context: Context): FreeAiQuotaSnapshot =
        withContext(Dispatchers.IO) {
            FreeAiQuotaClient().fetchSnapshot(context)
        }
}
