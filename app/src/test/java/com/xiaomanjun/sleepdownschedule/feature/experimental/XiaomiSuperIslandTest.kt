package com.xiaomanjun.sleepdownschedule.feature.experimental

import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayload
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateSegment
import com.xiaomanjun.sleepdownschedule.model.LiveUpdateChipTextMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiSuperIslandTest {
    @Test fun effectiveAllowRuleDoesNotSilentlySkipTheTemporaryBypass() {
        assertTrue(isKnownIslandFirewallRule(1))
        assertTrue(isKnownIslandFirewallRule(0))
        assertTrue(isKnownIslandFirewallRule(2))
        assertFalse(isKnownIslandFirewallRule(null))
        assertFalse(isKnownIslandFirewallRule(-1))
        assertFalse(isKnownIslandFirewallRule(3))
    }
    private val start = 1_800_000_000_000L
    private val end = start + 45 * 60_000L
    private val course = LiveUpdatePayload(
        name = "高等数学",
        timeText = "08:00 - 08:45",
        location = "教学楼 A101",
        showActions = true,
        muteKey = "test",
        muteUntil = end.toString(),
        chipTextMode = LiveUpdateChipTextMode.COUNTDOWN,
        segments = listOf(LiveUpdateSegment(start, end)),
        duringClassEnabled = true
    )

    @Test fun preClassUsesNexioTextTemplateAndNativeCountdown() {
        val now = start - 10 * 60_000L
        val root = JSONObject(XiaomiSuperIsland.parameters(
            course, course.statusAt(now), "10分钟", now
        )).getJSONObject("param_v2")
        val island = root.getJSONObject("param_island").getJSONObject("bigIslandArea")

        assertTrue(root.getBoolean("enableFloat"))
        assertFalse(root.has("islandFirstFloat"))
        assertEquals(2, island.getInt("templateNo"))
        assertEquals("高等数学", island.getJSONObject("imageTextInfoLeft")
            .getJSONObject("textInfo").getString("title"))
        assertEquals("教学楼 A101", island.getJSONObject("textInfo").getString("title"))
        assertEquals(start, root.getJSONObject("hintInfo").getJSONObject("timerInfo")
            .getLong("timerWhen"))
    }

    @Test fun classStartCreatesNativeCountdownEvenWithoutPreClassIsland() {
        val now = start + 10 * 60_000L
        val root = JSONObject(XiaomiSuperIsland.parameters(
            course, course.statusAt(now), "35分钟", now,
            options = XiaomiSuperIsland.Options(right = 2)
        )).getJSONObject("param_v2")
        val island = root.getJSONObject("param_island").getJSONObject("bigIslandArea")
        val hint = root.getJSONObject("hintInfo")

        assertFalse(root.getBoolean("enableFloat"))
        assertTrue(root.getBoolean("islandFirstFloat"))
        assertEquals("reopen", root.getString("reopen"))
        assertEquals(2, island.getInt("templateNo"))
        assertEquals("下课", island.getJSONObject("sameWidthDigitInfo").getString("content"))
        assertEquals(end, island.getJSONObject("sameWidthDigitInfo")
            .getJSONObject("timerInfo").getLong("timerWhen"))
        assertEquals("距离下课", hint.getString("content"))
        assertEquals("开启勿扰", hint.getJSONObject("actionInfo").getString("actionTitle"))
        assertEquals(XiaomiSuperIsland.DndActionKey,
            hint.getJSONObject("actionInfo").getString("action"))
        assertFalse(hint.getJSONObject("actionInfo").has("actionIntent"))
        assertEquals(end, hint.getJSONObject("timerInfo").getLong("timerWhen"))
        assertEquals(-1, hint.getJSONObject("timerInfo").getInt("timerType"))
    }

    @Test fun breakAndResumedClassPointToTheirOwnNextBoundary() {
        val firstEnd = start + 45 * 60_000L
        val secondStart = firstEnd + 10 * 60_000L
        val secondEnd = secondStart + 45 * 60_000L
        val payload = course.copy(
            segments = listOf(LiveUpdateSegment(start, firstEnd), LiveUpdateSegment(secondStart, secondEnd)),
            expiresAtMillis = secondEnd
        )
        val options = XiaomiSuperIsland.Options(left = 2, right = 2)
        val beforeBreak = JSONObject(XiaomiSuperIsland.parameters(
            payload, payload.statusAt(firstEnd - 60_000L), "1分钟", firstEnd - 60_000L,
            options = options
        )).getJSONObject("param_v2")
        val inBreak = JSONObject(XiaomiSuperIsland.parameters(
            payload, payload.statusAt(firstEnd), "10分钟", firstEnd, options = options
        )).getJSONObject("param_v2")
        val afterBreak = JSONObject(XiaomiSuperIsland.parameters(
            payload, payload.statusAt(secondStart), "45分钟", secondStart, options = options
        )).getJSONObject("param_v2")

        assertEquals("课间", beforeBreak.getJSONObject("param_island")
            .getJSONObject("bigIslandArea").getJSONObject("sameWidthDigitInfo").getString("content"))
        assertEquals(firstEnd, beforeBreak.getJSONObject("hintInfo")
            .getJSONObject("timerInfo").getLong("timerWhen"))
        assertEquals("距上课", inBreak.getJSONObject("param_island")
            .getJSONObject("bigIslandArea").getJSONObject("imageTextInfoLeft")
            .getJSONObject("textInfo").getString("title"))
        assertEquals(secondStart, inBreak.getJSONObject("hintInfo")
            .getJSONObject("timerInfo").getLong("timerWhen"))
        assertEquals(secondEnd, afterBreak.getJSONObject("hintInfo")
            .getJSONObject("timerInfo").getLong("timerWhen"))
    }

    @Test fun timerEndsWhenTheCourseHasExpired() {
        val now = end + 1L
        val root = JSONObject(XiaomiSuperIsland.parameters(
            course, course.statusAt(now), "已下课", now
        )).getJSONObject("param_v2")
        val island = root.getJSONObject("param_island").getJSONObject("bigIslandArea")

        assertEquals(2, island.getInt("templateNo"))
        assertEquals(0, root.getJSONObject("hintInfo").getJSONObject("timerInfo").getInt("timerType"))
    }

    @Test fun islandLifetimeCoversLongClass() {
        val longEnd = start + 100 * 60_000L
        val payload = course.copy(
            segments = listOf(LiveUpdateSegment(start, longEnd)),
            expiresAtMillis = longEnd
        )
        val now = start + 10 * 60_000L
        val root = JSONObject(XiaomiSuperIsland.parameters(
            payload, payload.statusAt(now), "90分钟", now
        )).getJSONObject("param_v2")
        assertEquals(90 * 60, root.getJSONObject("param_island").getInt("islandTimeout"))
    }
}
