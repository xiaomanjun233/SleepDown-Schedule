package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.defaultPeriods
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TimeSlotCourseNavigationTest {
    private val tokens = mutableListOf<Long>()
    private val initial = AppState(config = defaultConfig(2), periods = defaultPeriods(2),
        allConfigs = listOf(defaultConfig(2)), allPeriods = defaultPeriods(2), loaded = true)

    @After fun cleanup() { tokens.forEach(TimeSlotCourseStateHandoff::remove) }

    @Test fun firstFrameKeepsTheSelectedScheduleUntilItsPeriodsArrive() {
        assertSame(initial, stableTimeSlotEditorState(AppState(), initial, 2))
        val partiallyLoaded = AppState(config = defaultConfig(1), allConfigs = initial.allConfigs,
            allPeriods = defaultPeriods(1), loaded = true)
        assertSame(initial, stableTimeSlotEditorState(partiallyLoaded, initial, 2))
        val loaded = partiallyLoaded.copy(allPeriods = defaultPeriods(1) + defaultPeriods(2))
        assertSame(loaded, stableTimeSlotEditorState(loaded, initial, 2))
    }

    @Test fun processRecreationWithoutAnInMemoryHandoffUsesRoomLoadingState() {
        val live = AppState()
        assertSame(live, stableTimeSlotEditorState(live, null, 2))
    }

    @Test fun abandonedHandoffsAreBoundedAndFinishedEditorsReleaseTheirState() {
        repeat(7) { tokens += TimeSlotCourseStateHandoff.put(initial.copy(config = defaultConfig(it + 1))) }
        assertNull(TimeSlotCourseStateHandoff.get(tokens.first()))
        assertSame(initial.periods, TimeSlotCourseStateHandoff.get(tokens.last())!!.state.periods)
        TimeSlotCourseStateHandoff.remove(tokens.last())
        assertNull(TimeSlotCourseStateHandoff.get(tokens.last()))
    }
}
