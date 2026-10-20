package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWallpaperBlurSlider
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardColorChange
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardAlphaSlider
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.withChangesFrom
import com.xiaomanjun.sleepdownschedule.domain.schedule.withPersonalizationFrom
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeCardColoredTextChange
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.mergePersonalizationCandidate
import com.xiaomanjun.sleepdownschedule.feature.home.personalization.*
import org.junit.Assert.*
import org.junit.Test

class PersonalizationLiveStateTest {
    @Test fun closeKeepsPendingCommitUntilExactStorageAcknowledgement() {
        val session = PersonalizationSession()
        val initial = defaultConfig(7)
        val committed = session.commit(initial, initial.copy(cardColorArgb = 0xFF123456), PersonalizeCardColorChange)
        session.setSliderActive(PersonalizeCardAlphaSlider, true)
        session.preview.update(PersonalizeCardAlphaSlider, initial.copy(cardAlpha = 0.4f))
        session.close()
        assertNull(session.draft)
        assertNull(session.sliderKey)
        assertNull(session.preview.cardAlpha)
        assertEquals(committed, session.displayConfig)
        session.acknowledge(initial)
        assertEquals(committed, session.displayConfig)
        session.acknowledge(committed)
        assertNull(session.displayConfig)
    }

    @Test fun rapidSessionCommitsMergeOnlyTheChangedProperty() {
        val session = PersonalizationSession()
        val initial = defaultConfig(7)
        session.commit(initial, initial.copy(cardColorArgb = 0xFF123456), PersonalizeCardColorChange)
        val committed = session.commit(initial, initial.copy(courseCardColoredTextEnabled = true), PersonalizeCardColoredTextChange)
        assertEquals(0xFF123456, committed.cardColorArgb)
        assertTrue(committed.courseCardColoredTextEnabled)
        assertEquals(committed, session.pendingCommit)
        session.acknowledge(committed)
        assertEquals(committed, session.displayConfig)
    }

    @Test fun switchingSchedulesDropsOnlyConfigsForThePreviousSchedule() {
        val session = PersonalizationSession()
        val initial = defaultConfig(7)
        session.commit(initial, initial.copy(cardAlpha = 0.6f), PersonalizeCardAlphaSlider)
        session.selectSchedule(7)
        assertNotNull(session.displayConfig)
        session.selectSchedule(8)
        assertNull(session.draft)
        assertNull(session.pendingCommit)
    }

    @Test fun lateSliderEndDoesNotEndTheNewSliderAndBlurPreviewSurvivesCommit() {
        val session = PersonalizationSession()
        val initial = defaultConfig()
        session.setSliderActive(PersonalizeCardAlphaSlider, true)
        session.setSliderActive(PersonalizeWallpaperBlurSlider, true)
        session.setSliderActive(PersonalizeCardAlphaSlider, false)
        assertEquals(PersonalizeWallpaperBlurSlider, session.sliderKey)
        session.preview.update(PersonalizeWallpaperBlurSlider, initial.copy(wallpaperBlur = 12f))
        session.commit(initial, initial.copy(wallpaperBlur = 12f), PersonalizeWallpaperBlurSlider)
        assertEquals(12f, session.preview.wallpaperBlur)
        session.preview.clear(PersonalizeWallpaperBlurSlider)
        assertNull(session.preview.wallpaperBlur)
    }

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
