package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WebDavAutomationTest {
    private val old = WebDavEntry("old.sleepdown", 10, "Wed, 07 Oct 2026 10:00:00 GMT", "\"1\"")
    private val newest = WebDavEntry("new.sleepdown", 10, "Thu, 08 Oct 2026 10:00:00 GMT", "\"2\"")
    @Test fun onlyNewerForeignBackupsWithValidServerTimesAreSuggested() {
        val state = WebDavAutomationState(lastUploadAt = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli(),
            lastUploadedName = "own.sleepdown")
        val own = newest.copy(name = "own.sleepdown")
        val unknown = newest.copy(name = "unknown.sleepdown", modified = "not a date")
        assertEquals(newest, newestRestoreCandidate(listOf(old, newest, own, unknown), state))
        assertNull(newestRestoreCandidate(listOf(old, own, unknown), state))
    }
    @Test fun acknowledgedBackupDoesNotPromptAgainButANewerReplacementDoes() {
        val state = WebDavAutomationState(acknowledged = newest.changeKey(), lastReviewedAt = newest.modifiedMillis()!!)
        assertNull(newestRestoreCandidate(listOf(old, newest), state))
        val replacement = newest.copy(modified = "Thu, 08 Oct 2026 11:00:00 GMT", etag = "\"3\"")
        assertEquals(replacement, newestRestoreCandidate(listOf(old, replacement), state))
    }
}
