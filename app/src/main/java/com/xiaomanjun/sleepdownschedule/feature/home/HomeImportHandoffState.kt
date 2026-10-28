package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.CourseEntity

/** Composition-owned import handoff. Persistence and entry/reveal motion stay with their owners. */
@Stable
internal class HomeImportHandoffState {
    var submitting by mutableStateOf(false)
        private set
    var sourceState by mutableStateOf<AppState?>(null)
        private set
    var previewMounted by mutableStateOf(false)
        private set

    private data class ExpectedCourses(val previousIds: Set<Long>, val count: Int)
    private var expectedCourses by mutableStateOf<ExpectedCourses?>(null)

    fun beginSubmission(source: AppState, previousCourses: List<CourseEntity>, courseCount: Int): Boolean {
        if (submitting) return false
        submitting = true
        retainSource(source, previousCourses, courseCount)
        return true
    }

    // AI's final request already owns submission; it only needs the same visual/data handoff.
    fun retainSource(source: AppState, previousCourses: List<CourseEntity>, courseCount: Int) {
        sourceState = source
        expectedCourses = ExpectedCourses(previousCourses.map { it.id }.toSet(), courseCount)
    }

    fun onPreviewMountedChanged(mounted: Boolean) {
        previewMounted = mounted
    }

    fun clearExpectedCourses() {
        expectedCourses = null
    }

    fun submissionFailed() {
        submitting = false
        sourceState = null
        clearExpectedCourses()
    }

    // Called only after both the import entry and its preview have unmounted.
    fun releaseSource() {
        sourceState = null
    }

    fun isTargetReady(scheduleId: Int, latest: AppState, rendered: AppState): Boolean {
        val expected = expectedCourses
        return !previewMounted && rendered.loaded &&
            latest.schedules.any { it.id == scheduleId } &&
            latest.allConfigs.any { it.id == scheduleId } &&
            rendered.config.id == scheduleId &&
            (expected == null || (rendered.courses.size == expected.count &&
                rendered.courses.none { it.id in expected.previousIds }))
    }

    fun complete() {
        submitting = false
        clearExpectedCourses()
    }
}
