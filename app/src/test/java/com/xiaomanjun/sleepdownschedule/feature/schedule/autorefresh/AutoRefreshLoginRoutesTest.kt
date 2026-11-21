package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.*
import org.junit.Test

class AutoRefreshLoginRoutesTest {
    @Test fun transientAuthenticationUrlsAreNeverSaved() {
        listOf("https://school.test/cas/login", "https://school.test/authserver/login",
            "https://school.test/#/login", "https://school.test/home?ticket=ST-example",
            "https://user@school.test/course", "file:///tmp/course.html").forEach {
            assertFalse(it, AutoRefreshLoginRoutes.isSessionPage(it))
        }
    }

    @Test fun executionStaysOnTheVerifiedOriginEvenAfterRedirects() {
        val saved = "https://school.test/portal/#/course"
        assertTrue(AutoRefreshLoginRoutes.isVerifiedOrigin(saved, "https://school.test/student/timetable"))
        assertFalse(AutoRefreshLoginRoutes.isVerifiedOrigin(saved, "https://auth.school.test/cas/login"))
        assertFalse(AutoRefreshLoginRoutes.isVerifiedOrigin(saved, "https://school.test.evil.test/student/timetable"))
        assertFalse(AutoRefreshLoginRoutes.isVerifiedOrigin(saved, "https://school.test:8443/student/timetable"))
        assertFalse(AutoRefreshLoginRoutes.isVerifiedOrigin(saved, "http://school.test/student/timetable"))
    }
}
