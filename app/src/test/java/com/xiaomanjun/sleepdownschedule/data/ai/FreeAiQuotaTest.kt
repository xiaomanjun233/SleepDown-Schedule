package com.xiaomanjun.sleepdownschedule.data.ai

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.RemoteAiQuotaPolicy
import com.xiaomanjun.sleepdownschedule.feature.agent.freeAiChatCardHint
import com.xiaomanjun.sleepdownschedule.feature.importing.canFailoverManagedAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiQuotaTest {
    @Test
    fun parseConsumeSuccessDecodesJsonAndHeaderFallback() {
        val body = """{"allowed":true,"counted":true,"kind":"chat","date":"2026-11-17","used":1,"limit":20,"remaining":19,"resetAt":1794931200}"""
        val parsed = FreeAiQuotaCodec.parseConsumeSuccess(body, FreeAiQuotaKind.CHAT)
        assertTrue(parsed.allowed)
        assertTrue(parsed.counted)
        assertEquals(FreeAiQuotaKind.CHAT, parsed.kind)
        assertEquals("2026-11-17", parsed.date)
        assertEquals(1L, parsed.used)
        assertEquals(20L, parsed.limit)
        assertEquals(19L, parsed.remaining)
        assertEquals(1794931200L, parsed.resetAt)

        val headerOnly = FreeAiQuotaCodec.parseConsumeSuccess(
            body = """{"allowed":true}""",
            fallbackKind = FreeAiQuotaKind.IMPORT,
            headers = mapOf(
                "X-SleepDown-Quota-Kind" to "import",
                "X-SleepDown-Quota-Limit" to "5",
                "X-SleepDown-Quota-Used" to "2",
                "X-SleepDown-Quota-Remaining" to "3",
                "X-SleepDown-Quota-Reset" to "1794931200"
            )
        )
        assertEquals(FreeAiQuotaKind.IMPORT, headerOnly.kind)
        assertEquals(5L, headerOnly.limit)
        assertEquals(2L, headerOnly.used)
        assertEquals(3L, headerOnly.remaining)
    }

    @Test
    fun parseConsumeErrorHandlesQuotaExceededRateLimitedAndVersionBlocked() {
        val exceededBody = """{"error":{"code":"AI_QUOTA_EXCEEDED","message":"今日免费 AI 对话次数已达上限（20/20），请明天再试或使用自己的 API Key。","requestId":"req-1"},"quota":{"allowed":false,"counted":false,"kind":"chat","date":"2026-11-17","used":20,"limit":20,"remaining":0,"resetAt":1794931200}}"""
        val exceeded = FreeAiQuotaCodec.parseConsumeError(429, exceededBody, FreeAiQuotaKind.CHAT)
        assertEquals("AI_QUOTA_EXCEEDED", exceeded.code)
        assertNotNull(exceeded.quota)
        assertFalse(exceeded.quota!!.allowed)
        assertEquals(0L, exceeded.quota!!.remaining)

        val rateLimitedBody = """{"error":{"code":"RATE_LIMITED","message":"请求太频繁，请稍后重试"}}"""
        val rateLimited = FreeAiQuotaCodec.parseConsumeError(429, rateLimitedBody, FreeAiQuotaKind.CHAT)
        assertEquals("RATE_LIMITED", rateLimited.code)
        assertNull(rateLimited.quota)

        val blockedBody = """{"error":{"code":"AI_VERSION_BLOCKED","message":"请更新到最新版本"}}"""
        val blocked = FreeAiQuotaCodec.parseConsumeError(403, blockedBody, FreeAiQuotaKind.IMPORT)
        assertEquals("AI_VERSION_BLOCKED", blocked.code)
        assertEquals("请更新到最新版本", blocked.message)
    }

    @Test
    fun parseSnapshotDecodesBothBuckets() {
        val body = """{"date":"2026-11-17","resetAt":1794931200,"timezone":"Asia/Shanghai","chat":{"used":3,"limit":20,"remaining":17},"import":{"used":5,"limit":5,"remaining":0}}"""
        val snapshot = FreeAiQuotaCodec.parseSnapshot(body)
        assertEquals("2026-11-17", snapshot.date)
        assertEquals(1794931200L, snapshot.resetAt)
        assertEquals(17L, snapshot.chat.remaining)
        assertFalse(snapshot.chat.isExhausted)
        assertEquals(0L, snapshot.import.remaining)
        assertTrue(snapshot.import.isExhausted)
    }

    @Test
    fun reducerPreservesUsageOnLiveLimitUpdateAndResetsAfterMidnight() {
        // 2026-11-17 14:00:00 Asia/Shanghai = 2026-11-17 06:00:00 UTC
        val middaySeconds = 1794895200L
        val expectedDate = FreeAiQuotaTime.currentDate(middaySeconds)
        val initial = FreeAiQuotaReducer.applyBootstrapPolicy(
            existing = null,
            policy = RemoteAiQuotaPolicy(chatDailyLimit = 20L, importDailyLimit = 5L),
            nowSeconds = middaySeconds
        )!!
        assertEquals(expectedDate, initial.date)
        assertEquals("每日上限 20 次（使用或刷新后显示已用次数）", initial.chat?.formatDetailed())

        val afterUse = FreeAiQuotaReducer.applyConsumeResult(
            existing = initial,
            result = FreeAiQuotaConsumeResult(
                allowed = true,
                counted = true,
                kind = FreeAiQuotaKind.CHAT,
                date = expectedDate,
                used = 4L,
                limit = 20L,
                remaining = 16L,
                resetAt = middaySeconds + 36_000L
            )
        )
        assertEquals(16L, afterUse.chat?.remaining)

        val afterLimitRaise = FreeAiQuotaReducer.applyBootstrapPolicy(
            existing = afterUse,
            policy = RemoteAiQuotaPolicy(chatDailyLimit = 30L, importDailyLimit = -1L),
            nowSeconds = middaySeconds + 60L
        )!!
        assertEquals(4L, afterLimitRaise.chat?.used)
        assertEquals(26L, afterLimitRaise.chat?.remaining)
        assertTrue(afterLimitRaise.import?.isUnlimited == true)
        assertFalse(afterLimitRaise.import?.isExhausted == true)

        // Next day past resetAt
        val rolledOver = FreeAiQuotaReducer.normalizeForDate(
            existing = afterLimitRaise,
            nowSeconds = middaySeconds + 36_001L
        )!!
        assertEquals(0L, rolledOver.chat?.used)
        assertEquals(30L, rolledOver.chat?.remaining)
        assertFalse(rolledOver.syncedFromServer)
    }

    @Test
    fun quotaAndVersionErrorsNeverTriggerManagedFailover() {
        val quotaError = FreeAiQuotaExceededException("已达上限", FreeAiQuotaKind.CHAT, 20, 20, 0, 1794931200)
        val blockedError = FreeAiVersionBlockedException("版本不支持")
        assertFalse(quotaError.canFailoverManagedAi())
        assertFalse(IllegalStateException("wrapped", quotaError).canFailoverManagedAi())
        assertFalse(blockedError.canFailoverManagedAi())
        assertTrue(quotaError.isFreeAiQuotaExceeded())
        assertTrue(blockedError.isFreeAiVersionBlocked())
    }

    @Test
    fun compactCardHintsReflectPausedExhaustedAndLowQuota() {
        assertEquals("免费 AI 对话暂未开放 · 点击配置 Key", freeAiChatCardHint(FreeAiQuotaBucket(0, 0, 0)))
        assertEquals("今日免费对话已用完 · 点击配置 Key", freeAiChatCardHint(FreeAiQuotaBucket(20, 20, 0)))
        assertNull(freeAiChatCardHint(FreeAiQuotaBucket(17, 20, 3)))
        assertNull(freeAiChatCardHint(FreeAiQuotaBucket(10, 20, 10)))
        assertNull(freeAiChatCardHint(FreeAiQuotaBucket(100, -1, -1)))
    }

    @Test
    fun bucketProgressFractionAndLowWarningFollowLimits() {
        val unlimited = FreeAiQuotaBucket(used = 10, limit = -1, remaining = -1)
        assertEquals(1f, unlimited.remainingFraction(), 0.0001f)
        assertFalse(unlimited.isLow)

        val paused = FreeAiQuotaBucket(used = 0, limit = 0, remaining = 0)
        assertEquals(0f, paused.remainingFraction(), 0.0001f)
        assertTrue(paused.isExhausted)

        val healthy = FreeAiQuotaBucket(used = 5, limit = 20, remaining = 15)
        assertEquals(0.75f, healthy.remainingFraction(), 0.0001f)
        assertFalse(healthy.isLow)

        val low = FreeAiQuotaBucket(used = 17, limit = 20, remaining = 3)
        assertEquals(0.15f, low.remainingFraction(), 0.0001f)
        assertTrue(low.isLow)
    }

    @Test
    fun quotaSubjectDerivesDeterministicUuidAndRejectsInvalidAndroidIds() {
        val first = FreeAiQuotaIdentity.deriveSubjectUuid(
            androidId = "A1B2C3D4E5F60718",
            packageName = "com.xiaomanjun.sleepdownschedule"
        )
        val second = FreeAiQuotaIdentity.deriveSubjectUuid(
            androidId = "a1b2c3d4e5f60718",
            packageName = "com.xiaomanjun.sleepdownschedule"
        )
        val otherPkg = FreeAiQuotaIdentity.deriveSubjectUuid(
            androidId = "a1b2c3d4e5f60718",
            packageName = "com.example.other"
        )
        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(36, first!!.length)
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(first))
        assertFalse(first == otherPkg)
        assertFalse(first.contains("a1b2c3d4e5f60718", ignoreCase = true))

        assertNull(FreeAiQuotaIdentity.deriveSubjectUuid(null, "com.xiaomanjun.sleepdownschedule"))
        assertNull(FreeAiQuotaIdentity.deriveSubjectUuid("", "com.xiaomanjun.sleepdownschedule"))
        assertNull(FreeAiQuotaIdentity.deriveSubjectUuid("9774d56d682e549c", "com.xiaomanjun.sleepdownschedule"))
        assertNull(FreeAiQuotaIdentity.deriveSubjectUuid("0000000000000000", "com.xiaomanjun.sleepdownschedule"))
    }

    @Test
    fun ringColorTransitionsFromGreenThroughYellowToRed() {
        val full = com.xiaomanjun.sleepdownschedule.core.ui.controls.conicQuotaRingColor(1f, isExhausted = false, isUnlimited = false, darkSurface = true)
        val half = com.xiaomanjun.sleepdownschedule.core.ui.controls.conicQuotaRingColor(0.5f, isExhausted = false, isUnlimited = false, darkSurface = true)
        val empty = com.xiaomanjun.sleepdownschedule.core.ui.controls.conicQuotaRingColor(0f, isExhausted = true, isUnlimited = false, darkSurface = true)
        assertTrue(full.green > full.red)
        assertTrue(half.red > 0.8f && half.green > 0.7f && half.blue < 0.2f)
        assertTrue(empty.red > empty.green)
    }
}
