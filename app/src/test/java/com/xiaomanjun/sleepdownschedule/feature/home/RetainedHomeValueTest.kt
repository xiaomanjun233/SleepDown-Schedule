package com.xiaomanjun.sleepdownschedule.feature.home

import org.junit.Assert.assertSame
import org.junit.Test

class RetainedHomeValueTest {
    @Test
    fun freezesTheCurrentFrameWithoutCopyingIt() {
        val initial = Any()
        val opening = Any()
        val retained = RetainedHomeValue(initial)
        assertSame(opening, retained.update(opening, frozen = true))
        repeat(60) { assertSame(opening, retained.update(Any(), frozen = true)) }
    }

    @Test
    fun resumesWithOnlyTheLatestInputAndCanFreezeAgain() {
        val opening = Any()
        val retained = RetainedHomeValue(opening)
        retained.update(opening, frozen = true)
        retained.update(Any(), frozen = true)
        val latest = Any()
        assertSame(latest, retained.update(latest, frozen = false))
        val nextOpening = Any()
        assertSame(nextOpening, retained.update(nextOpening, frozen = true))
        assertSame(nextOpening, retained.update(Any(), frozen = true))
    }

    @Test
    fun livePreviewAndCourseLandingMayUpdateExistingContent() {
        val retained = RetainedHomeValue(Any())
        retained.update(Any(), frozen = true)
        val preview = Any()
        assertSame(preview, retained.update(preview, frozen = false))
        val landedCourse = Any()
        assertSame(landedCourse, retained.update(landedCourse, frozen = false))
    }
}
