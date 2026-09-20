package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.app.ui.PersonalizeCardColoredTextChange
import com.xiaomanjun.sleepdownschedule.app.ui.mergePersonalizationCandidate
import com.xiaomanjun.sleepdownschedule.domain.schedule.withChangesFrom
import com.xiaomanjun.sleepdownschedule.domain.schedule.withPersonalizationFrom
import org.junit.Assert.*
import org.junit.Test

class PersonalizationLiveStateTest {
    @Test fun colorEditsRefreshFrozenConfigWhileCourseInputsStayFrozen() {
        val original = AppState(config = defaultConfig(7))
        val retained = RetainedHomeValue(original)
        retained.update(original, frozen = true)
        val backgroundUpdate = original.copy(courses = listOf(CourseEntity(
            name = "异步更新", teacher = null, location = null, weekday = 2,
            periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null
        )))
        val colorDraft = original.config.copy(cardColorArgb = 0xFF337799)
        val frame = retained.update(backgroundUpdate, frozen = true).withPersonalizationConfig(colorDraft)
        assertEquals(colorDraft, frame.config)
        assertSame(original.courses, frame.courses)
        assertEquals(backgroundUpdate.courses, retained.update(backgroundUpdate, frozen = false).courses)
    }

    @Test fun rapidColorAndToggleChangesUseLatestDraftWithoutWaitingForPersistence() {
        val initial = defaultConfig()
        val latest = initial.copy(cardColorArgb = 0xFF446688)
        val merged = mergePersonalizationCandidate(
            latest, initial.copy(courseCardColoredTextEnabled = true), PersonalizeCardColoredTextChange
        )
        assertEquals(latest.cardColorArgb, merged.cardColorArgb)
        assertTrue(AppState(config = initial).withPersonalizationConfig(merged).config.courseCardColoredTextEnabled)
    }

    @Test fun otherSchedulesAndUnchangedDraftsDoNotReplaceTheFrame() {
        val original = AppState(config = defaultConfig(7))
        assertSame(original, original.withPersonalizationConfig(defaultConfig(8)))
        assertSame(original, original.withPersonalizationConfig(original.config.copy()))
        assertSame(original, original.withPersonalizationConfig(null))
    }

    @Test fun personalizationAndConcurrentGeneralEditsPreserveTheToggle() {
        val original = defaultConfig()
        val enabled = original.copy(courseCardColoredTextEnabled = true)
        assertTrue(original.withPersonalizationFrom(enabled).courseCardColoredTextEnabled)
        val merged = enabled.withChangesFrom(original, original.copy(darkMode = true))
        assertTrue(merged.courseCardColoredTextEnabled)
        assertTrue(merged.darkMode)
        assertFalse(enabled.withPersonalizationFrom(original).courseCardColoredTextEnabled)
    }
}
