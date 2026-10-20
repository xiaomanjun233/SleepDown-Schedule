package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.*
import org.junit.Assert.*
import org.junit.Test

class HomeImportHandoffStateTest {
    private fun course(id: Long, scheduleId: Int = 1) = CourseEntity(
        id, "课程", null, null, 1, listOf(1), listOf(1), WeekParity.ALL, null,
        scheduleId = scheduleId
    )

    private fun loaded(scheduleId: Int, vararg courses: CourseEntity): AppState {
        val config = defaultConfig(scheduleId)
        return AppState(
            config = config, courses = courses.toList(), loaded = true,
            schedules = listOf(ScheduleProfileEntity(scheduleId, "课表", true)),
            allConfigs = listOf(config)
        )
    }

    @Test fun overwriteWaitsForReplacedCourseIdsEvenWhenTheCountAlreadyMatches() {
        val old = loaded(1, course(10))
        val handoff = HomeImportHandoffState()
        assertTrue(handoff.beginSubmission(old, old.courses, 1))
        assertFalse(handoff.isTargetReady(1, old, old))
        val replacement = loaded(1, course(20))
        assertTrue(handoff.isTargetReady(1, replacement, replacement))
        // Readiness alone cannot release the old frame while the entry is still retracting.
        assertSame(old, handoff.sourceState)
    }

    @Test fun newScheduleWaitsForBothMetadataAndTheRenderedCourseEmission() {
        val old = loaded(1, course(10))
        val imported = loaded(2, course(20, 2), course(21, 2))
        val handoff = HomeImportHandoffState()
        handoff.beginSubmission(old, old.courses, 2)
        assertFalse(handoff.isTargetReady(2, old, imported))
        assertFalse(handoff.isTargetReady(2, imported.copy(allConfigs = emptyList()), imported))
        assertFalse(handoff.isTargetReady(2, imported, old))
        assertFalse(handoff.isTargetReady(2, imported, imported.copy(loaded = false)))
        assertFalse(handoff.isTargetReady(2, imported, imported.copy(courses = imported.courses.take(1))))
        assertTrue(handoff.isTargetReady(2, imported, imported))
    }

    @Test fun mountedPreviewBlocksTheNewHomeAndSourceReleaseKeepsTheCourseExpectation() {
        val old = loaded(1, course(10))
        val imported = loaded(1, course(20))
        val handoff = HomeImportHandoffState()
        handoff.beginSubmission(old, old.courses, 1)
        handoff.onPreviewMountedChanged(true)
        assertFalse(handoff.isTargetReady(1, imported, imported))
        handoff.onPreviewMountedChanged(false)
        handoff.releaseSource()
        assertNull(handoff.sourceState)
        assertFalse(handoff.isTargetReady(1, old, old))
        assertTrue(handoff.isTargetReady(1, imported, imported))
        assertTrue(handoff.submitting)
        handoff.complete()
        assertFalse(handoff.submitting)
    }

    @Test fun repeatedConfirmationCannotReplaceAnInFlightSubmission() {
        val old = loaded(1, course(10))
        val other = loaded(2, course(20, 2))
        val handoff = HomeImportHandoffState()
        assertTrue(handoff.beginSubmission(old, old.courses, 1))
        assertFalse(handoff.beginSubmission(other, other.courses, 4))
        assertSame(old, handoff.sourceState)
        assertTrue(handoff.isTargetReady(2, other, other))
    }

    @Test fun failureReleasesTheSourceAndAllowsARetryWithFreshExpectations() {
        val old = loaded(1, course(10))
        val retry = loaded(1, course(20))
        val handoff = HomeImportHandoffState()
        handoff.beginSubmission(old, old.courses, 3)
        handoff.submissionFailed()
        assertFalse(handoff.submitting)
        assertNull(handoff.sourceState)
        assertTrue(handoff.beginSubmission(retry, retry.courses, 1))
        assertFalse(handoff.isTargetReady(1, retry, retry))
        val replacement = loaded(1, course(30))
        assertTrue(handoff.isTargetReady(1, replacement, replacement))
    }

    @Test fun aiFinalImportRetainsTheSourceWithoutTakingOverItsSubmissionUi() {
        val old = loaded(1, course(10))
        val handoff = HomeImportHandoffState()
        handoff.retainSource(old, old.courses, 1)
        assertFalse(handoff.submitting)
        assertSame(old, handoff.sourceState)
        assertFalse(handoff.isTargetReady(1, old, old))
        handoff.submissionFailed()
        assertNull(handoff.sourceState)
    }

    @Test fun activityImportWithoutALocalSubmissionStillWaitsForTheTargetSchedule() {
        val handoff = HomeImportHandoffState()
        val old = loaded(1, course(10))
        val imported = loaded(2, course(20, 2))
        assertFalse(handoff.isTargetReady(2, imported, old))
        assertTrue(handoff.isTargetReady(2, imported, imported))
    }

    @Test fun emptyImportAndCancelledPreviewDoNotLeaveAnOldCourseExpectation() {
        val old = loaded(1, course(10))
        val empty = loaded(1)
        val handoff = HomeImportHandoffState()
        handoff.beginSubmission(old, old.courses, 0)
        assertFalse(handoff.isTargetReady(1, old, old))
        assertTrue(handoff.isTargetReady(1, empty, empty))
        handoff.submissionFailed()
        handoff.retainSource(old, old.courses, 5)
        handoff.clearExpectedCourses()
        assertTrue(handoff.isTargetReady(1, old, old))
    }
}
