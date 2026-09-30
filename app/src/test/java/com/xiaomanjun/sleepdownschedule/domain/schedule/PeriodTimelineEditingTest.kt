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

    @Test fun breakCanBeRemovedAndStopsAtZeroMinute() {
        val changed = resizeTimelineBlock(config, original, 1, true, 0)
        assertEquals("08:45", changed.times[1].startTime)
        assertEquals("09:30", changed.times[1].endTime)
        assertEquals(original.times[2], changed.times[2])
        assertNull(validateResolvedPeriodTimes(changed.times))
    }

    @Test fun removingOneMinuteLeadingGapKeepsStructureOtherSchemesAndSectionAnchor() {
        val eveningConfig = config.copy(afternoonPeriodCount = 0, eveningPeriodCount = 1)
        val evening = original.copy(
            scheme = original.scheme.copy(eveningStartTime = "19:00"),
            times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "19:01", "19:46")
        )
        val other = evening.copy(scheme = evening.scheme.copy(id = 2), times = evening.times.map { it.copy(schemeId = 2) })
        val initial = PeriodTimelineSession(eveningConfig, SchedulePeriodSchemesDraft(listOf(evening, other), 1))
        val changed = resizeTimelineLeadingBreak(initial, PeriodDayPart.EVENING, 0)
        assertEquals("19:00", changed.active.times.last().startTime)
        assertEquals("19:45", changed.active.times.last().endTime)
        assertEquals("19:00", changed.active.scheme.eveningStartTime)
        assertEquals(initial.config, changed.config)
        assertEquals(initial.active.times.take(2), changed.active.times.take(2))
        assertEquals(other, changed.draft.schemes.last())
        assertTrue(changed.draft.topologyOperations.isEmpty())
        assertNull(validateResolvedPeriodTimes(changed.active.times))
        assertEquals(changed.active.times, resizeTimelineLeadingBreak(initial, PeriodDayPart.EVENING, -1).active.times)
    }

    @Test fun deletingAndReaddingLeadingGapRecoversCompressedLessonWithoutMovingNextPart() {
        val tight = original.copy(times = original.times.take(2) + PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"))
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(tight), 1))
        val delayed = resizeTimelineLeadingBreak(initial, PeriodDayPart.MORNING, 30)
        assertEquals("10:00", delayed.active.times[1].endTime)
        val removed = resizeTimelineLeadingBreak(delayed, PeriodDayPart.MORNING, 0)
        assertEquals(tight.times, removed.active.times)
        assertTrue(removed.uncompressedLastMinutes.isEmpty())
        assertEquals(delayed.active.times, resizeTimelineLeadingBreak(removed, PeriodDayPart.MORNING, 30).active.times)
    }

    @Test fun deletingAndReaddingOrdinaryBreakKeepsPeriodNumbersAndOtherSchemes() {
        val other = original.copy(scheme = original.scheme.copy(id = 2), times = original.times.map { it.copy(schemeId = 2) })
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original, other), 1))
        val removed = resizeTimelineBlock(initial, 1, true, 0)
        assertEquals(initial.config, removed.config)
        assertEquals(other, removed.draft.schemes.last())
        assertEquals(listOf(1, 2, 3), removed.active.times.map { it.periodIndex })
        assertTrue(removed.draft.topologyOperations.isEmpty())
        assertEquals(initial.active.times, resizeTimelineBlock(removed, 1, true, 10).active.times)
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

    @Test fun editingLaterSectionFirstPullsOnlyConflictingEarlierSections() {
        val other = original.copy(scheme = original.scheme.copy(id = 2), times = original.times.map { it.copy(schemeId = 2) })
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original, other), 1))
        assertEquals(100..(LastMinuteOfDay - 45), timelineFlexiblePartStartBounds(config, original, PeriodDayPart.AFTERNOON))

        val moved = moveTimelinePartWithNeighbours(initial, PeriodDayPart.AFTERNOON, 9 * 60)
        assertEquals(listOf("07:20", "08:15", "09:00"), moved.active.times.map { it.startTime })
        assertEquals(listOf("08:05", "09:00", "09:45"), moved.active.times.map { it.endTime })
        assertEquals("07:20", moved.active.scheme.morningStartTime)
        assertEquals("09:00", moved.active.scheme.afternoonStartTime)
        assertEquals(other, moved.draft.schemes.last())
        assertNull(validateResolvedPeriodTimes(moved.active.times))
    }

    @Test fun editingEarlierSectionLaterPushesNextWithoutCompressingLessons() {
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original), 1))
        val moved = moveTimelinePartWithNeighbours(initial, PeriodDayPart.MORNING, 14 * 60)
        assertEquals(listOf("14:00", "14:55", "15:40"), moved.active.times.map { it.startTime })
        assertEquals(listOf("14:45", "15:40", "16:25"), moved.active.times.map { it.endTime })
        assertEquals("15:40", moved.active.scheme.afternoonStartTime)
        assertNull(validateResolvedPeriodTimes(moved.active.times))
    }

    @Test fun editingLastSectionFirstMovesOnlyTheEarlierSectionsThatConflict() {
        val fourPartConfig = config.copy(morningPeriodCount = 1, noonPeriodCount = 1, afternoonPeriodCount = 1, eveningPeriodCount = 1)
        val fourPartScheme = original.copy(times = listOf(
            PeriodSchemeTimeEntity(1, 1, "08:00", "08:45"),
            PeriodSchemeTimeEntity(1, 2, "11:00", "11:45"),
            PeriodSchemeTimeEntity(1, 3, "14:00", "14:45"),
            PeriodSchemeTimeEntity(1, 4, "18:00", "18:45")
        ))
        val initial = PeriodTimelineSession(fourPartConfig, SchedulePeriodSchemesDraft(listOf(fourPartScheme), 1))
        val moved = moveTimelinePartWithNeighbours(initial, PeriodDayPart.EVENING, 12 * 60)
        assertEquals(listOf("08:00", "10:30", "11:15", "12:00"), moved.active.times.map { it.startTime })
        assertEquals(listOf("08:45", "11:15", "12:00", "12:45"), moved.active.times.map { it.endTime })
        assertNull(validateResolvedPeriodTimes(moved.active.times))

        val clamped = moveTimelinePartWithNeighbours(initial, PeriodDayPart.MORNING, LastMinuteOfDay)
        assertEquals("20:59", clamped.active.times.first().startTime)
        assertEquals("23:59", clamped.active.times.last().endTime)
        assertNull(validateResolvedPeriodTimes(clamped.active.times))
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
            assertEquals(listOf(PeriodTopologyOperation.Delete(index),
                PeriodTopologyOperation.AddAfter(index - 1, restoredDeletionOperationIndex = 0)), restored.draft.topologyOperations)
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

    @Test fun sectionAnchorsClampToTheActualGapOnBothSides() {
        val staleEarly = original.copy(scheme = original.scheme.copy(afternoonStartTime = "09:00"))
        val staleLate = original.copy(scheme = original.scheme.copy(afternoonStartTime = "18:00"))
        assertEquals(9 * 60 + 40, timelinePartAnchorMinute(config, staleEarly, PeriodDayPart.AFTERNOON))
        assertEquals(14 * 60, timelinePartAnchorMinute(config, staleLate, PeriodDayPart.AFTERNOON))
        assertEquals("09:40", staleEarly.materializeForTimeline(config).scheme.afternoonStartTime)
        assertEquals("14:00", staleLate.materializeForTimeline(config).scheme.afternoonStartTime)
        assertEquals(staleEarly.times, staleEarly.materializeForTimeline(config).times)
        assertEquals("09:25", resizeTimelineBlock(config, staleEarly, 2, false, 30).times[1].endTime)
    }

    @Test fun repartitionRebasesEverySchemesAnchorsAndAllowsEditingTheNewTail() {
        val splitConfig = config.copy(morningPeriodCount = 4, afternoonPeriodCount = 4, eveningPeriodCount = 2)
        val split = original.copy(times = (0..3).map { index ->
            PeriodSchemeTimeEntity(1, index + 1, timelineMinuteText(480 + index * 55), timelineMinuteText(525 + index * 55))
        } + (0..3).map { index ->
            PeriodSchemeTimeEntity(1, index + 5, timelineMinuteText(840 + index * 55), timelineMinuteText(885 + index * 55))
        } + listOf(PeriodSchemeTimeEntity(1, 9, "19:00", "19:45"), PeriodSchemeTimeEntity(1, 10, "19:55", "20:40")))
        val other = split.copy(scheme = split.scheme.copy(id = 2), times = split.times.map {
            it.copy(schemeId = 2, startTime = timelineMinuteText(requireNotNull(parseMinuteOfDay(it.startTime)) + 10),
                endTime = timelineMinuteText(requireNotNull(parseMinuteOfDay(it.endTime)) + 10))
        })
        val initial = PeriodTimelineSession(splitConfig, SchedulePeriodSchemesDraft(listOf(split, other), 1))
        val changed = requireNotNull(resizeTimelineStructure(initial,
            splitConfig.copy(morningPeriodCount = 5, afternoonPeriodCount = 4, eveningPeriodCount = 1)))
        assertEquals(initial.draft.schemes.map { it.times }, changed.draft.schemes.map { it.times })
        assertEquals(listOf("14:45", "14:55"), changed.draft.schemes.map { it.scheme.afternoonStartTime })
        assertEquals(listOf("19:45", "19:55"), changed.draft.schemes.map { it.scheme.eveningStartTime })
        val resized = resizeTimelineBlock(changed, 4, true, 149)
        assertEquals("13:59", resized.active.times[4].startTime)
        assertEquals("14:44", resized.active.times[4].endTime)
        assertTrue(changed.draft.topologyOperations.isEmpty())
        val earlierSplit = requireNotNull(resizeTimelineStructure(
            PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original), 1)),
            config.copy(morningPeriodCount = 1, afternoonPeriodCount = 2)))
        assertEquals(original.times, earlierSplit.active.times)
        assertEquals("08:55", earlierSplit.active.scheme.afternoonStartTime)
    }

    @Test fun zeroGapLessonsRestoreExactlyEvenWhenTheNextSectionLeavesNoSpareMinute() {
        val tightConfig = config.copy(morningPeriodCount = 3)
        listOf("10:15", "14:00").forEach { afternoonStart ->
            val tight = original.copy(scheme = original.scheme.copy(afternoonStartTime = afternoonStart), times = listOf(
                PeriodSchemeTimeEntity(1, 1, "08:00", "08:45"),
                PeriodSchemeTimeEntity(1, 2, "08:45", "09:30"),
                PeriodSchemeTimeEntity(1, 3, "09:30", "10:15"),
                PeriodSchemeTimeEntity(1, 4, afternoonStart, timelineMinuteText(requireNotNull(parseMinuteOfDay(afternoonStart)) + 45))
            ))
            val other = tight.copy(scheme = tight.scheme.copy(id = 2), times = tight.times.map { it.copy(schemeId = 2) })
            val initial = PeriodTimelineSession(tightConfig, SchedulePeriodSchemesDraft(listOf(tight, other), 1))
            (1..4).forEach { index ->
                val removed = requireNotNull(deleteTimelinePeriod(initial, index))
                val restored = requireNotNull(insertTimelinePeriod(removed, removed.vacancies.single().id))
                assertEquals(initial.config, restored.config)
                assertEquals(initial.draft.schemes, restored.draft.schemes)
                assertNull(validateResolvedPeriodTimes(restored.active.times))
            }
            val firstRemoved = requireNotNull(deleteTimelinePeriod(initial, 1))
            val twoRemoved = requireNotNull(deleteTimelinePeriod(firstRemoved, 1))
            listOf(twoRemoved.vacancies, twoRemoved.vacancies.reversed()).forEach { order ->
                var restored = twoRemoved
                order.forEach { restored = requireNotNull(insertTimelinePeriod(restored, it.id)) }
                assertEquals(initial.draft.schemes, restored.draft.schemes)
            }
        }
    }

    @Test fun restorationCannotExceedFortyButExistingOversizedTimelinesRemainEditable() {
        val denseConfig = config.copy(morningPeriodCount = 40, afternoonPeriodCount = 0)
        val dense = original.copy(scheme = original.scheme.copy(classDurationMinutes = 10, breakDurationMinutes = 0),
            times = (1..40).map { PeriodSchemeTimeEntity(1, it,
                timelineMinuteText(480 + (it - 1) * 10), timelineMinuteText(480 + it * 10)) })
        val initial = PeriodTimelineSession(denseConfig, SchedulePeriodSchemesDraft(listOf(dense), 1))
        assertNull(resizeTimelineStructure(initial, denseConfig.copy(morningPeriodCount = 41)))
        val removed = requireNotNull(deleteTimelinePeriod(initial, 2))
        assertEquals(40, requireNotNull(insertTimelinePeriod(removed, removed.vacancies.single().id)).config.totalPeriodCount())
        val appended = requireNotNull(appendTimelinePeriod(removed, PeriodDayPart.MORNING))
        assertEquals(40, appended.config.totalPeriodCount())
        assertNull(insertTimelinePeriod(appended, appended.vacancies.single().id))
        assertNull(appendTimelinePeriod(appended, PeriodDayPart.EVENING))
        val oversized = PeriodTimelineSession(denseConfig.copy(morningPeriodCount = 41),
            SchedulePeriodSchemesDraft(listOf(dense.copy(times = dense.times + PeriodSchemeTimeEntity(1, 41, "14:40", "14:50"))), 1))
        assertEquals(oversized, resizeTimelineStructure(oversized, oversized.config))
        assertEquals(40, requireNotNull(resizeTimelineStructure(oversized, denseConfig)).config.totalPeriodCount())
        assertNull(resizeTimelineStructure(oversized, denseConfig.copy(morningPeriodCount = 42)))
        val edited = resizeTimelineBlock(oversized, 41, false, 5)
        assertEquals(41, edited.config.totalPeriodCount())
        assertEquals("14:45", edited.active.times.last().endTime)
        assertNull(validateResolvedPeriodTimes(edited.active.times))
    }

    @Test fun emptySectionsCanBeReenteredAtTheirConfiguredStartsAcrossSchemes() {
        val other = original.copy(scheme = original.scheme.copy(id = 2, noonStartTime = "12:15"),
            times = original.times.map { it.copy(schemeId = 2) })
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(original, other), 1))
        val added = requireNotNull(appendTimelinePeriod(initial, PeriodDayPart.NOON))
        assertEquals(1, added.config.noonPeriodCount)
        assertEquals(listOf("12:00", "12:15"), added.draft.schemes.map { it.times[2].startTime })
        assertEquals(listOf(PeriodTopologyOperation.AddAfter(2)), added.draft.topologyOperations)
        added.draft.schemes.forEachIndexed { index, scheme ->
            assertEquals(initial.draft.schemes[index].times.take(2), scheme.times.take(2))
            assertEquals(initial.draft.schemes[index].times.last().copy(periodIndex = 4), scheme.times.last())
            assertNull(validateResolvedPeriodTimes(scheme.times))
        }
        val afternoonOnlyConfig = config.copy(morningPeriodCount = 0)
        val afternoonOnly = original.copy(times = listOf(original.times.last().copy(periodIndex = 1)))
        val only = PeriodTimelineSession(afternoonOnlyConfig, SchedulePeriodSchemesDraft(listOf(afternoonOnly), 1))
        val morning = requireNotNull(appendTimelinePeriod(only, PeriodDayPart.MORNING))
        assertEquals("08:00", morning.active.times.first().startTime)
        assertEquals("14:00", morning.active.times.last().startTime)
        val evening = requireNotNull(appendTimelinePeriod(only, PeriodDayPart.EVENING))
        assertEquals("19:00", evening.active.times.last().startTime)
    }

    @Test fun emptySectionInsertionClampsStaleAnchorsAndRejectsAnySchemeWithoutSpace() {
        val early = original.copy(scheme = original.scheme.copy(noonStartTime = "08:00"))
        val late = original.copy(scheme = original.scheme.copy(id = 2, noonStartTime = "18:00"),
            times = original.times.map { it.copy(schemeId = 2) })
        val initial = PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(early, late), 1))
        val added = requireNotNull(appendTimelinePeriod(initial, PeriodDayPart.NOON))
        assertEquals(listOf("09:40", "13:59"), added.draft.schemes.map { it.times[2].startTime })
        assertEquals(listOf("09:40", "13:59"), added.draft.schemes.map { it.scheme.noonStartTime })
        added.draft.schemes.forEach { assertNull(validateResolvedPeriodTimes(it.times)) }
        val full = late.copy(times = late.times.map { if (it.periodIndex == 3) it.copy(startTime = "09:40", endTime = "10:25") else it })
        val blocked = initial.copy(draft = initial.draft.copy(schemes = listOf(early, full)))
        assertNull(appendTimelinePeriod(blocked, PeriodDayPart.NOON))
        assertEquals(0, blocked.config.noonPeriodCount)
        assertEquals(early, blocked.active)
    }

    @Test fun movingAnUnrelatedDividerKeepsCompressionRecovery() {
        val splitConfig = config.copy(eveningPeriodCount = 1)
        val split = original.copy(scheme = original.scheme.copy(afternoonStartTime = "10:00"),
            times = original.times.take(2) + listOf(PeriodSchemeTimeEntity(1, 3, "10:00", "10:45"),
                PeriodSchemeTimeEntity(1, 4, "19:00", "19:45")))
        val initial = PeriodTimelineSession(splitConfig, SchedulePeriodSchemesDraft(listOf(split), 1))
        val compressed = resizeTimelineBlock(initial, 1, false, 75)
        assertEquals(45, compressed.uncompressedLastMinutes[PeriodDayPart.MORNING])
        val moved = moveTimelinePartWithNeighbours(compressed, PeriodDayPart.EVENING, 19 * 60 + 1)
        assertEquals(compressed.uncompressedLastMinutes, moved.uncompressedLastMinutes)
        val restored = resizeTimelineBlock(moved, 1, false, 45)
        assertEquals(initial.active.times.take(3), restored.active.times.take(3))
        assertEquals("19:01", restored.active.times.last().startTime)
        assertTrue(restored.uncompressedLastMinutes.isEmpty())
    }
}
