package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Test

class PeriodTimelineEditingTest {
    private val config = defaultConfig().copy(morningPeriodCount = 2, noonPeriodCount = 0, afternoonPeriodCount = 1, eveningPeriodCount = 0)
    private val original = PeriodSchemeDraft(
        PeriodSchemeEntity(id = 1, scheduleId = 1, name = "原作息", mode = PeriodSchemeMode.MANUAL),
        listOf(
            PeriodSchemeTimeEntity(1, 1, "08:00", "08:45"),
            PeriodSchemeTimeEntity(1, 2, "08:55", "09:40"),
            PeriodSchemeTimeEntity(1, 3, "14:00", "14:45")
        )
    )

    @Test fun stretchingLessonMovesOnlyTheRestOfItsDayPart() {
        val changed = resizeTimelineBlock(config, original, 1, false, 50)
        assertEquals("08:50", changed.times[0].endTime)
        assertEquals("09:00", changed.times[1].startTime)
        assertEquals("09:45", changed.times[1].endTime)
        assertEquals(original.times[2], changed.times[2])
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun breakCannotBeRemovedAndStopsAtOneMinute() {
        val changed = resizeTimelineBlock(config, original, 1, true, 0)
        assertEquals("08:46", changed.times[1].startTime)
        assertEquals("09:31", changed.times[1].endTime)
        assertEquals(original.times[2], changed.times[2])
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun growthStopsAtNextDayPart() {
        val changed = resizeTimelineBlock(config, original, 1, true, 2000)
        assertEquals("14:00", changed.times[1].endTime)
        assertEquals("13:15", changed.times[1].startTime)
        assertEquals(original.times[2], changed.times[2])
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun collisionOnlyShortensTheLastLesson() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val changed = resizeTimelineBlock(config, tight, 1, false, 75)
        assertEquals("09:15", changed.times[0].endTime)
        assertEquals("09:25", changed.times[1].startTime)
        assertEquals("10:00", changed.times[1].endTime)
        assertEquals(tight.times[2], changed.times[2])
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun shiftingPartKeepsGapsAndCompressesOnlyItsLastLesson() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val changed = shiftTimelinePart(config, tight, PeriodDayPart.MORNING, 8 * 60 + 30)
        assertEquals("08:30", changed.times[0].startTime)
        assertEquals("09:15", changed.times[0].endTime)
        assertEquals("09:25", changed.times[1].startTime)
        assertEquals("10:00", changed.times[1].endTime)
        assertEquals("08:30", changed.scheme.morningStartTime)
        assertEquals(tight.times[2], changed.times[2])
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun shiftingStartKeepsTheBreakLengthFloorAndNeverOverlapsPreviousPart() {
        val later = shiftTimelinePart(config, original, PeriodDayPart.MORNING, 23 * 60)
        assertEquals("13:50", later.times[1].startTime)
        assertEquals("14:00", later.times[1].endTime)
        val earlier = shiftTimelinePart(config, original, PeriodDayPart.AFTERNOON, 0)
        assertEquals("09:40", earlier.times[2].startTime)
        assertNull(validateResolvedPeriodTimes(later.times))
        assertNull(validateResolvedPeriodTimes(earlier.times))
    }

    @Test fun draggingBackFromCollisionUsingGestureSnapshotRestoresFinalLesson() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val compressed = resizeTimelineBlock(config, tight, 1, false, 75)
        assertEquals("10:00", compressed.times[1].endTime)
        val restored = resizeTimelineBlock(config, tight, 1, false, 45)
        assertEquals(tight.times, restored.times)
    }

    @Test fun lastLessonNeverWrapsPastMidnightAndKeepsBreakLengthMinimum() {
        val long = resizeTimelineBlock(config, original, 3, false, 2000)
        assertEquals("23:59", long.times.last().endTime)
        val short = resizeTimelineBlock(config, long, 3, false, -30)
        assertEquals("14:10", short.times.last().endTime)
        assertNull(validateResolvedPeriodTimes(long.times))
        assertNull(validateResolvedPeriodTimes(short.times))
    }

    @Test fun lessonCannotShrinkBelowAdjacentBreakDuration() {
        val changed = resizeTimelineBlock(config, original, 1, false, 1)
        assertEquals("08:10", changed.times[0].endTime)
        assertEquals("08:20", changed.times[1].startTime)
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun compressionStopsWhenFinalLessonMatchesTheBreak() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val changed = resizeTimelineBlock(config, tight, 1, false, 500)
        assertEquals("09:40", changed.times[0].endTime)
        assertEquals("09:50", changed.times[1].startTime)
        assertEquals("10:00", changed.times[1].endTime)
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun timePickerAlsoEnforcesMinimumLessonDuration() {
        assertEquals(PeriodTimeSelection(480, 490), constrainPeriodTimeSelection(480, 481,
            periodTimePickerBounds(null, "10:00"), minimumDurationMinutes = 10))
    }

    @Test fun draftEditingLeavesTheOriginalUntouched() {
        val draft = SchedulePeriodSchemesDraft(listOf(original), 1)
        val before = PeriodTimelineSession(config, draft)
        val changed = before.updateActive(resizeTimelineBlock(config, original, 1, false, 70))
        assertEquals("08:45", before.active.times.first().endTime)
        assertEquals("09:10", changed.active.times.first().endTime)
        assertEquals(3, before.config.totalPeriodCount())
    }

    @Test fun removingPeriodUpdatesEverySchemeAndRecordsCourseMapping() {
        val other = original.copy(scheme = original.scheme.copy(id = 2), times = original.times.map { it.copy(schemeId = 2) })
        val before = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original, other), 1))
        val changed = requireNotNull(deleteTimelinePeriod(before, 2))
        assertEquals(1, changed.config.morningPeriodCount)
        assertEquals(listOf(PeriodTopologyOperation.Delete(2)), changed.draft.topologyOperations)
        changed.draft.schemes.forEach {
            assertEquals(listOf(1, 2), it.times.map { time -> time.periodIndex })
            assertEquals("14:00", it.times[1].startTime)
            assertNull(validateResolvedPeriodTimes(it.times))
        }
        assertEquals(3, before.active.times.size)
    }

    @Test fun repartitionAlonePreservesExistingTimesAndHasNoDeletionOperations() {
        val before = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original), 1))
        val changed = requireNotNull(resizeTimelineStructure(before, config.copy(morningPeriodCount = 3, afternoonPeriodCount = 0)))
        assertEquals(original.times, changed.active.times)
        assertTrue(changed.draft.topologyOperations.isEmpty())
    }

    @Test fun firstLessonMovesFollowersButLeavesSectionAnchorAndNextPartUntouched() {
        val changed = shiftTimelineFirstLesson(config, original, PeriodDayPart.MORNING, 8 * 60 + 20)
        assertEquals("08:00", changed.scheme.morningStartTime)
        assertEquals("08:20", changed.times[0].startTime)
        assertEquals("09:15", changed.times[1].startTime)
        assertEquals(original.times[2], changed.times[2])
        val earlier = shiftTimelineFirstLesson(config, changed, PeriodDayPart.MORNING, 0)
        assertEquals("08:00", earlier.times.first().startTime)
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun firstLessonCollisionCompressesOnlyFinalLesson() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val changed = shiftTimelineFirstLesson(config, tight, PeriodDayPart.MORNING, 8 * 60 + 30)
        assertEquals("08:30", changed.times[0].startTime)
        assertEquals("09:15", changed.times[0].endTime)
        assertEquals("09:25", changed.times[1].startTime)
        assertEquals("10:00", changed.times[1].endTime)
        assertEquals("08:00", changed.scheme.morningStartTime)
    }

    @Test fun movingDividerPreservesTheFirstLessonsOffset() {
        val delayed = shiftTimelineFirstLesson(config, original, PeriodDayPart.MORNING, 500)
        val moved = shiftTimelinePart(config, delayed, PeriodDayPart.MORNING, 510)
        assertEquals("08:30", moved.scheme.morningStartTime)
        assertEquals("08:50", moved.times.first().startTime)
        assertEquals("09:45", moved.times[1].startTime)
    }

    @Test fun resizeCannotConsumeSpaceBeforeDelayedNextPartFirstLesson() {
        val delayed = shiftTimelineFirstLesson(config, original, PeriodDayPart.AFTERNOON, 15 * 60)
        val changed = resizeTimelineBlock(config, delayed, 1, false, 1000)
        assertEquals("14:00", changed.times[1].endTime)
        assertEquals("15:00", changed.times[2].startTime)
    }

    @Test fun deleteAndAddRestoresFirstMiddleLastAndEmptyPartAcrossSchemes() {
        val expandedConfig = config.copy(morningPeriodCount = 3)
        val expanded = original.copy(times = listOf(
            PeriodSchemeTimeEntity(1, 1, "08:00", "08:45"),
            PeriodSchemeTimeEntity(1, 2, "08:55", "09:40"),
            PeriodSchemeTimeEntity(1, 3, "09:50", "10:35"),
            PeriodSchemeTimeEntity(1, 4, "14:00", "14:45")
        ))
        val other = expanded.copy(scheme = expanded.scheme.copy(id = 2), times = expanded.times.map { it.copy(schemeId = 2) })
        val initial = PeriodTimelineSession(expandedConfig, SchedulePeriodSchemesDraft(listOf(expanded, other), 1))
        (1..4).forEach { index ->
            val removed = requireNotNull(deleteTimelinePeriod(initial, index))
            val restored = requireNotNull(insertTimelinePeriod(removed, removed.vacancies.single().id))
            assertEquals(initial.config, restored.config)
            assertEquals(initial.draft.schemes, restored.draft.schemes)
            assertTrue(restored.vacancies.isEmpty())
            assertEquals(listOf(PeriodTopologyOperation.Delete(index), PeriodTopologyOperation.AddAfter(index - 1)), restored.draft.topologyOperations)
        }
    }

    @Test fun consecutiveDeletionsCanBeAddedBackInEitherOrder() {
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original), 1))
        val firstRemoved = requireNotNull(deleteTimelinePeriod(initial, 1))
        val allMorningRemoved = requireNotNull(deleteTimelinePeriod(firstRemoved, 1))
        listOf(allMorningRemoved.vacancies, allMorningRemoved.vacancies.reversed()).forEach { order ->
            var current = allMorningRemoved
            order.forEach { current = requireNotNull(insertTimelinePeriod(current, it.id)) }
            assertEquals(original.times, current.active.times)
            assertEquals(config, current.config)
        }
    }

    @Test fun addingAfterResizingKeepsBreaksAndDoesNotMoveNextSection() {
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original), 1))
        val removed = requireNotNull(deleteTimelinePeriod(initial, 2))
        val adjusted = removed.updateActive(resizeTimelineBlock(removed.config, removed.active, 1, false, 60))
        val restored = requireNotNull(insertTimelinePeriod(adjusted, adjusted.vacancies.single().id))
        assertEquals("09:01", restored.active.times[1].startTime)
        assertEquals("09:46", restored.active.times[1].endTime)
        assertEquals(original.times[2], restored.active.times[2])
        assertNull(validateResolvedPeriodTimes(restored.active.times))
    }

    @Test fun editingLegacyAutomaticSchemeFreezesTheResolvedTimeline() {
        val automatic = original.copy(scheme = original.scheme.copy(mode = PeriodSchemeMode.AUTO_MATCH))
        val changed = resizeTimelineBlock(config, automatic, 1, false, 47)
        assertEquals(PeriodSchemeMode.MANUAL, changed.scheme.mode)
        assertEquals(changed.times, resolveSchemeTimes(config, changed))
        assertEquals("08:47", changed.times[0].endTime)
    }

    @Test fun separateResizesRestoreCompressedDurationAndFollowers() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(tight), 1))
        val stretched = resizeTimelineBlock(initial, 1, false, 90)
        assertEquals("10:00", stretched.active.times[1].endTime)
        val partlyRestored = resizeTimelineBlock(stretched, 1, false, 75)
        assertEquals("09:25", partlyRestored.active.times[1].startTime)
        assertEquals("10:00", partlyRestored.active.times[1].endTime)
        val restored = resizeTimelineBlock(partlyRestored, 1, false, 45)
        assertEquals(tight.times, restored.active.times)
        assertTrue(restored.uncompressedLastMinutes.isEmpty())
    }

    @Test fun separateBreakResizesRestoreCompressedFinalLesson() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(tight), 1))
        val stretched = resizeTimelineBlock(initial, 1, true, 40)
        assertEquals("10:00", stretched.active.times[1].endTime)
        assertEquals(tight.times, resizeTimelineBlock(stretched, 1, true, 10).active.times)
    }

    @Test fun explicitFinalLessonResizeReplacesItsRecoveryTarget() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(tight), 1))
        val stretched = resizeTimelineBlock(initial, 1, false, 75)
        val shortenedLast = resizeTimelineBlock(stretched, 2, false, 20)
        val restored = resizeTimelineBlock(shortenedLast, 1, false, 45)
        assertEquals("08:55", restored.active.times[1].startTime)
        assertEquals("09:15", restored.active.times[1].endTime)
    }

    @Test fun separateSectionAndFirstStartChangesAlsoRecoverTheLastDuration() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(tight), 1))
        val shifted = shiftTimelinePart(initial, PeriodDayPart.MORNING, 510)
        assertEquals(tight.times, shiftTimelinePart(shifted, PeriodDayPart.MORNING, 480).active.times)
        val delayed = shiftTimelineFirstLesson(initial, PeriodDayPart.MORNING, 510)
        assertEquals(tight.times, shiftTimelineFirstLesson(delayed, PeriodDayPart.MORNING, 480).active.times)
    }

    @Test fun freeSpaceAllowsANewLessonAndCreatesTheMissingBreakAcrossSchemes() {
        val other = original.copy(scheme = original.scheme.copy(id = 2), times = original.times.map { it.copy(schemeId = 2) })
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original, other), 1))
        val added = requireNotNull(appendTimelinePeriod(initial, PeriodDayPart.MORNING))
        assertEquals(3, added.config.morningPeriodCount)
        assertEquals(listOf(PeriodTopologyOperation.AddAfter(2)), added.draft.topologyOperations)
        added.draft.schemes.forEach { scheme ->
            assertEquals("09:50", scheme.times[2].startTime)
            assertEquals("10:35", scheme.times[2].endTime)
            assertEquals("14:00", scheme.times[3].startTime)
            assertNull(validateResolvedPeriodTimes(scheme.times))
        }
        assertEquals(original.times, initial.active.times)
    }

    @Test fun shortFreeSpaceKeepsBothANonzeroBreakAndLessonBeforeTheBoundary() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "09:47", "10:32"))
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(tight), 1))
        val added = requireNotNull(appendTimelinePeriod(initial, PeriodDayPart.MORNING))
        assertEquals("09:43", added.active.times[2].startTime)
        assertEquals("09:47", added.active.times[2].endTime)
        assertNull(appendTimelinePeriod(added, PeriodDayPart.MORNING))
        assertNull(validateResolvedPeriodTimes(added.active.times))
    }

    @Test fun insufficientSpaceInAnySchemeRejectsInsertionWithoutPartialChanges() {
        val other = original.copy(scheme = original.scheme.copy(id = 2), times = original.times.map { it.copy(schemeId = 2) }
            .map { if (it.periodIndex == 3) it.copy(startTime = "09:41", endTime = "10:26") else it })
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original, other), 1))
        assertNull(appendTimelinePeriod(initial, PeriodDayPart.MORNING))
        assertEquals(original.times, initial.active.times)
        assertTrue(initial.draft.topologyOperations.isEmpty())
    }

    @Test fun appendingUsesTheSectionAnchorEvenWhenTheNextFirstLessonIsDelayed() {
        val delayed = shiftTimelineFirstLesson(config, original, PeriodDayPart.AFTERNOON, 900)
        var current = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(delayed), 1))
        while (true) { current = appendTimelinePeriod(current, PeriodDayPart.MORNING) ?: break }
        val lastMorning = current.active.times.last { it.periodIndex in current.config.periodRange(PeriodDayPart.MORNING) }
        assertEquals("14:00", lastMorning.endTime)
        assertEquals("15:00", current.active.times.last().startTime)
    }

    @Test fun removedTailCanBeAddedBackWithAShorterLessonWhenSpaceRemains() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(tight), 1))
        val removed = requireNotNull(deleteTimelinePeriod(initial, 2))
        val stretched = resizeTimelineBlock(removed, 1, false, 115)
        val restored = requireNotNull(insertTimelinePeriod(stretched, stretched.vacancies.single().id))
        assertEquals("09:55", restored.active.times[0].endTime)
        assertEquals("09:56", restored.active.times[1].startTime)
        assertEquals("10:00", restored.active.times[1].endTime)
        assertEquals(tight.times[2], restored.active.times[2])
        assertNull(validateResolvedPeriodTimes(restored.active.times))
        val filled = resizeTimelineBlock(removed, 1, false, 120)
        assertNull(insertTimelinePeriod(filled, filled.vacancies.single().id))
    }
}
