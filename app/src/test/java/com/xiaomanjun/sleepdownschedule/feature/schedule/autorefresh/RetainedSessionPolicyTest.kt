package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.*
import org.junit.Test
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool

class RetainedSessionPolicyTest {
    private val adapter = EduAdapter(
        EduSchool("school", "学校", "school"), "adapter", "教务", "BACHELOR_AND_ASSOCIATE",
        "fixture.js", "https://school.example/", "", ""
    )
    private val profile = AutoRefreshScheduleProfile(
        schoolId = "school", schoolName = "学校", adapterId = "adapter", adapterName = "教务",
        username = "", password = "", scheduleId = 1
    )

    @Test fun existingApiProfilesKeepTheirSchedulingBehavior() {
        assertFalse(profile.sessionOnly)
        assertFalse(profile.automaticRefreshEnabled)
        assertTrue(profile.copy(automatic = true).automaticRefreshEnabled)
    }

    @Test fun retainedOnlyProfilesNeverScheduleAnAutomaticImport() {
        assertFalse(profile.copy(sessionOnly = true).automaticRefreshEnabled)
        assertFalse(profile.copy(sessionOnly = true, automatic = true).automaticRefreshEnabled)
    }

    @Test fun cookielessAspNetPagesRetainTheCompleteAddressWithoutCookieOrStorage() {
        listOf(
            "https://school.example/(abcdefghijklmnopqrstuvwx)/xs_main.aspx?xh=fixture",
            "https://school.example/(S(abcdefghijklmnopqrstuvwx))/xs_main.aspx?xh=fixture",
            "https://school.example/%28S%28abcdefghijklmnopqrstuvwx%29%29/xskbcx.aspx?gnmkdm=fixture"
        ).forEach { url ->
            val saved = retainedEduSessionProfile(adapter, 1, emptyList(), url, null, true, null)
            assertEquals(url, saved.authenticatedUrl)
            assertTrue(saved.cookies.isEmpty())
            assertNull(saved.webStorage)
            assertTrue(saved.desktopMode)
            assertTrue(saved.sessionOnly)
            assertFalse(saved.automaticRefreshEnabled)
        }
    }

    @Test fun nativeBrowserStorageDoesNotRequireApiAllowlistedTokens() {
        val url = "https://school.example/student/#/home"
        val saved = retainedEduSessionProfile(adapter, 1, emptyList(), url, null, false, null)
        assertEquals(url, saved.authenticatedUrl)
        assertTrue(saved.sessionOnly)
    }

    @Test fun sameAccountKeepsAvatarButNeverPersistsLegacyPasswordOrAutomaticSetting() {
        val cookie = AutoRefreshCookie("https://school.example/", "fixture=fixture")
        val previous = profile.copy(avatarPath = "/fixture/avatar.png", password = "fixture", automatic = true)
        val saved = retainedEduSessionProfile(
            adapter, 1, listOf(cookie), "https://school.example/student", null, false, previous
        )
        assertEquals(previous.avatarPath, saved.avatarPath)
        assertEquals(listOf(cookie), saved.cookies)
        assertEquals("", saved.password)
        assertFalse(saved.automatic)
        assertFalse(saved.automaticRefreshEnabled)
    }

    @Test fun differentSchoolOrScheduleDoesNotInheritPersonalData() {
        listOf(profile.copy(schoolId = "other"), profile.copy(scheduleId = 2)).forEach { previous ->
            val saved = retainedEduSessionProfile(
                adapter, 1, emptyList(), "https://school.example/student", null, false,
                previous.copy(avatarPath = "/fixture/other.png", lastRefreshAt = 42)
            )
            assertNull(saved.avatarPath)
            assertEquals(0L, saved.lastRefreshAt)
        }
    }

    @Test fun loginPagesAndNonWebAddressesAreStillRejected() {
        listOf(
            "", "about:blank", "javascript:alert(1)", "file:///fixture",
            "https://user@school.example/student", "https://school.example/default2.aspx",
            "https://school.example/(abcdefghijklmnopqrstuvwx)/default2.aspx",
            "https://school.example/login.aspx", "https://school.example/#/login"
        ).forEach { url ->
            assertThrows(url, IllegalArgumentException::class.java) {
                retainedEduSessionProfile(adapter, 1, emptyList(), url, null, false, null)
            }
        }
    }
}
