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

    @Test fun classStartReopensTheSameTextTemplateWithStaticStatus() {
        val now = start + 10 * 60_000L
        val root = JSONObject(XiaomiSuperIsland.parameters(
            course, course.statusAt(now), "35分钟", now
        )).getJSONObject("param_v2")
        val island = root.getJSONObject("param_island").getJSONObject("bigIslandArea")
        val hint = root.getJSONObject("hintInfo")

        assertTrue(root.getBoolean("enableFloat"))
        assertEquals("reopen", root.getString("reopen"))
        assertEquals(2, island.getInt("templateNo"))
        assertEquals("已上课", island.getJSONObject("textInfo").getString("title"))
        assertEquals("现在", hint.getString("content"))
        assertEquals("已上课", hint.getString("title"))
        assertEquals(0, hint.getJSONObject("timerInfo").getInt("timerType"))
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
}
