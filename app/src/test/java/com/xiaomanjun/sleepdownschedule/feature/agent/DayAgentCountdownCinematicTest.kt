package com.xiaomanjun.sleepdownschedule.feature.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class DayAgentCountdownCinematicTest {
    @Test fun startsOnlyAsTheSameCourseCrossesFromFourToThree() {
        assertEquals(DayAgentCinematicEvent.START,
            dayAgentCinematicEvent("course", 4L, null, "course", 3L))
        assertEquals(DayAgentCinematicEvent.NONE,
            dayAgentCinematicEvent(null, null, null, "course", 3L))
        assertEquals(DayAgentCinematicEvent.NONE,
            dayAgentCinematicEvent("other", 4L, null, "course", 3L))
    }

    @Test fun currentCountdownExplodesWhenItsCourseStateEnds() {
        assertEquals(DayAgentCinematicEvent.TICK,
            dayAgentCinematicEvent("course", 3L, "course", "course", 2L))
        assertEquals(DayAgentCinematicEvent.EXPLODE,
            dayAgentCinematicEvent("course", 1L, "course", "next", 600L))
        assertEquals(DayAgentCinematicEvent.EXPLODE,
            dayAgentCinematicEvent("course", 1L, "course", null, null))
    }
}
