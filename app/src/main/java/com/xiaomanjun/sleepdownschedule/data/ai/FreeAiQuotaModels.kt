package com.xiaomanjun.sleepdownschedule.data.ai

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

enum class FreeAiQuotaKind(val wireValue: String, val displayLabel: String) {
    CHAT("chat", "对话"),
    IMPORT("import", "导入");

    companion object {
        fun fromWire(value: String?): FreeAiQuotaKind? = when (value?.trim()?.lowercase()) {
            "chat" -> CHAT
            "import" -> IMPORT
            else -> null
        }
    }
}

@Serializable
data class FreeAiQuotaBucket(
    val used: Long? = null,
    val limit: Long,
    val remaining: Long? = null
) {
    val isUnlimited: Boolean get() = limit < 0L
    val isPaused: Boolean get() = limit == 0L
    val isExhausted: Boolean get() = limit == 0L || (limit > 0L && remaining != null && remaining <= 0L)
    val isLow: Boolean
        get() = limit > 0L && remaining != null && remaining > 0L &&
            (remaining <= 3L || remaining.toFloat() / limit.toFloat() <= 0.2f)

    fun remainingFraction(): Float = when {
        limit < 0L -> 1f
        limit == 0L -> 0f
        remaining != null -> (remaining.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        else -> 1f
    }

    fun formatCompact(): String = when {
        limit < 0L -> "不限次"
        limit == 0L -> "暂未开放"
        remaining != null -> "余 $remaining/$limit 次"
        else -> "限 $limit 次/日"
    }

    fun formatDetailed(): String = when {
        limit < 0L -> "不限次数"
        limit == 0L -> "暂未开放（0 次/日）"
        used != null && remaining != null -> "已用 $used / $limit 次 · 剩余 $remaining 次"
        else -> "每日上限 $limit 次（使用或刷新后显示已用次数）"
    }

    companion object {
        fun computeRemaining(limit: Long, used: Long?): Long? = when {
            limit < 0L -> -1L
            limit == 0L -> 0L
            used == null -> null
            else -> (limit - used).coerceAtLeast(0L)
        }
    }
}

@Serializable
data class FreeAiQuotaState(
    val date: String,
    val resetAt: Long? = null,
    val timezone: String = FreeAiQuotaTime.DEFAULT_TIMEZONE,
    val chat: FreeAiQuotaBucket? = null,
    val import: FreeAiQuotaBucket? = null,
    val syncedFromServer: Boolean = false
) {
    fun bucket(kind: FreeAiQuotaKind): FreeAiQuotaBucket? = when (kind) {
        FreeAiQuotaKind.CHAT -> chat
        FreeAiQuotaKind.IMPORT -> import
    }

    fun isExhausted(kind: FreeAiQuotaKind): Boolean = bucket(kind)?.isExhausted == true

    fun compactSummary(kind: FreeAiQuotaKind): String? {
        val bucket = bucket(kind) ?: return null
        return when {
            bucket.isUnlimited -> "免费 AI · 不限次"
            bucket.isPaused -> "免费 AI ${kind.displayLabel}暂未开放"
            bucket.isExhausted -> "今日免费${kind.displayLabel}已用完"
            else -> "免费 AI · ${kind.displayLabel}${bucket.formatCompact()}"
        }
    }
}

data class FreeAiQuotaConsumeResult(
    val allowed: Boolean,
    val counted: Boolean,
    val kind: FreeAiQuotaKind,
    val date: String,
    val used: Long,
    val limit: Long,
    val remaining: Long,
    val resetAt: Long
)

data class FreeAiQuotaSnapshot(
    val date: String,
    val resetAt: Long,
    val timezone: String,
    val chat: FreeAiQuotaBucket,
    val import: FreeAiQuotaBucket
)

class FreeAiQuotaExceededException(
    message: String,
    val kind: FreeAiQuotaKind,
    val used: Long,
    val limit: Long,
    val remaining: Long,
    val resetAt: Long
) : IllegalStateException(message)

class FreeAiVersionBlockedException(
    message: String
) : IllegalStateException(message)

fun Throwable.isFreeAiQuotaExceeded(): Boolean =
    generateSequence(this) { it.cause }.take(16).any { it is FreeAiQuotaExceededException }

fun Throwable.isFreeAiVersionBlocked(): Boolean =
    generateSequence(this) { it.cause }.take(16).any { it is FreeAiVersionBlockedException }

internal object FreeAiQuotaTime {
    const val DEFAULT_TIMEZONE = "Asia/Shanghai"
    private val shanghaiZone: ZoneId = ZoneId.of(DEFAULT_TIMEZONE)

    fun currentDate(nowSeconds: Long = System.currentTimeMillis() / 1_000L): String =
        Instant.ofEpochSecond(nowSeconds).atZone(shanghaiZone).toLocalDate().toString()
}
