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

    @Test fun preClassUsesIndependentFieldsAndNativeCountdownTimer() {
        val now = start - 10 * 60_000L
        val root = JSONObject(XiaomiSuperIsland.parameters(
            course, course.statusAt(now), "10分钟",
            XiaomiIslandFields(XiaomiIslandField.LOCATION, XiaomiIslandField.COUNTDOWN),
            now
        )).getJSONObject("param_v2")
        val island = root.getJSONObject("param_island").getJSONObject("bigIslandArea")

        assertTrue(root.getBoolean("enableFloat"))
        assertFalse(root.getBoolean("islandFirstFloat"))
        assertEquals("教学楼 A101", island.getJSONObject("imageTextInfoLeft")
            .getJSONObject("textInfo").getString("title"))
        assertEquals(start, island.getJSONObject("sameWidthDigitInfo")
            .getJSONObject("timerInfo").getLong("timerWhen"))
        assertEquals("", island.getJSONObject("textInfo").getString("title"))
    }

    @Test fun inClassUsesEndTimeAndStillHonorsBothFields() {
        val now = start + 10 * 60_000L
        val root = JSONObject(XiaomiSuperIsland.parameters(
            course, course.statusAt(now), "35分钟",
            XiaomiIslandFields(XiaomiIslandField.COUNTDOWN, XiaomiIslandField.COURSE_NAME),
            now
        )).getJSONObject("param_v2")
        val island = root.getJSONObject("param_island").getJSONObject("bigIslandArea")

        assertFalse(root.getBoolean("enableFloat"))
        assertTrue(root.getBoolean("islandFirstFloat"))
        assertEquals("35分钟", island.getJSONObject("imageTextInfoLeft")
            .getJSONObject("textInfo").getString("title"))
        assertEquals("高等数学", island.getJSONObject("textInfo").getString("title"))
        assertEquals(end, root.getJSONObject("hintInfo").getJSONObject("timerInfo").getLong("timerWhen"))
        assertEquals("距离下课", root.getJSONObject("hintInfo").getString("content"))
    }
}
