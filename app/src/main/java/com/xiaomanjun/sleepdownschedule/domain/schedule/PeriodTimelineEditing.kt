package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeMode
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

/** A detached edit transaction; no repository writes occur until settings are saved. */
internal data class PeriodTimelineSession(
    val config: ScheduleConfigEntity,
    val draft: SchedulePeriodSchemesDraft,
    val vacancies: List<PeriodTimelineVacancy> = emptyList(),
    // Keep compression reversible across separate gestures and picker confirmations.
    val uncompressedLastMinutes: Map<PeriodDayPart, Int> = emptyMap()
) {
    val active: PeriodSchemeDraft get() = draft.schemes.first { it.scheme.id == draft.activeSchemeId }

    fun updateActive(value: PeriodSchemeDraft) = copy(
        draft = draft.copy(schemes = draft.schemes.map {
            if (it.scheme.id == value.scheme.id) value else it
        })
    )

    /** Compare saved meaning, excluding editor-only materialization and gesture history. */
    fun hasChangesFrom(initial: PeriodTimelineSession): Boolean {
        if (config != initial.config) return true
        val originalCount = initial.config.totalPeriodCount() - initial.draft.topologyOperations.sumOf {
            when (it) {
                is PeriodTopologyOperation.AddAfter -> 1
                is PeriodTopologyOperation.Delete -> -1
            }
        }
        if (netPeriodIdentities(originalCount, draft.topologyOperations) !=
            netPeriodIdentities(originalCount, initial.draft.topologyOperations)) return true
        fun resolvedDraft(session: PeriodTimelineSession) = session.draft.copy(
            schemes = session.draft.schemes.map { it.materializeForTimeline(session.config) },
            topologyOperations = emptyList()
        )
        return resolvedDraft(this) != resolvedDraft(initial)
    }
}

internal fun PeriodSchemeDraft.materializeForTimeline(config: ScheduleConfigEntity) = copy(
    scheme = scheme.copy(mode = PeriodSchemeMode.MANUAL),
    times = resolveSchemeTimes(config, this),
    specialBreaks = emptyMap(),
    overriddenPeriods = emptySet()
).rebaseTimelineAnchors(config)

/** Keep section dividers inside their actual gaps without moving any lesson. */
private fun PeriodSchemeDraft.rebaseTimelineAnchors(config: ScheduleConfigEntity): PeriodSchemeDraft {
    fun start(part: PeriodDayPart, configured: String) = if (config.periodCount(part) > 0) {
        timelineMinuteText(timelinePartAnchorMinute(config, this, part))
    } else configured
    return copy(scheme = scheme.copy(
        morningStartTime = start(PeriodDayPart.MORNING, scheme.morningStartTime),
        noonStartTime = start(PeriodDayPart.NOON, scheme.noonStartTime),
        afternoonStartTime = start(PeriodDayPart.AFTERNOON, scheme.afternoonStartTime),
        eveningStartTime = start(PeriodDayPart.EVENING, scheme.eveningStartTime)
    ))
}

/** A removed lesson keeps an insertion point only for the lifetime of the editor. */
internal data class PeriodTimelineVacancy(
    val id: Int,
    val part: PeriodDayPart,
    val after: Int,
    val removedTimes: Map<Long, PeriodSchemeTimeEntity>,
    val deletedOperationIndex: Int = -1
)

internal fun timelinePartAnchorMinute(config: ScheduleConfigEntity, draft: PeriodSchemeDraft, part: PeriodDayPart): Int {
    val configured = parseMinuteOfDay(when (part) {
        PeriodDayPart.MORNING -> draft.scheme.morningStartTime
        PeriodDayPart.NOON -> draft.scheme.noonStartTime
        PeriodDayPart.AFTERNOON -> draft.scheme.afternoonStartTime
        PeriodDayPart.EVENING -> draft.scheme.eveningStartTime
    }) ?: 0
    val times = resolveSchemeTimes(config, draft).sortedBy { it.periodIndex }
    val after = PeriodDayPart.entries.take(part.ordinal).sumOf { config.periodCount(it) }
    val previous = times.lastOrNull { it.periodIndex <= after }?.endTime?.let(::parseMinuteOfDay) ?: 0
    val first = times.firstOrNull { it.periodIndex in config.periodRange(part) }
        ?.startTime?.let(::parseMinuteOfDay)
    val latest = first ?: timelinePartBoundary(config, draft, part)
    // Imported/manual anchors can be stale on either side of the actual section boundary.
    return configured.coerceIn(minOf(previous, latest), latest)
}

internal fun timelinePartBoundary(config: ScheduleConfigEntity, draft: PeriodSchemeDraft, part: PeriodDayPart): Int {
    val next = PeriodDayPart.entries.drop(part.ordinal + 1).firstOrNull { config.periodCount(it) > 0 }
    return next?.let { timelinePartAnchorMinute(config, draft, it) } ?: LastMinuteOfDay
}

internal fun timelineMinuteText(minute: Int): String =
    "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)

/** Existing unusually short lessons are preserved; further shortening uses adjacent break lengths. */
internal fun minimumTimelineLessonMinutes(config: ScheduleConfigEntity, draft: PeriodSchemeDraft, index: Int): Int {
    val times = resolveSchemeTimes(config, draft).sortedBy { it.periodIndex }
    val position = times.indexOfFirst { it.periodIndex == index }
    val lesson = times.getOrNull(position) ?: return 1
    val part = PeriodDayPart.entries.firstOrNull { index in config.periodRange(it) } ?: return 1
    val range = config.periodRange(part)
    val start = parseMinuteOfDay(lesson.startTime) ?: return 1
    val end = parseMinuteOfDay(lesson.endTime) ?: return 1
    val adjacent = listOfNotNull(
        times.getOrNull(position - 1)?.takeIf { it.periodIndex in range }?.endTime?.let(::parseMinuteOfDay)?.let { start - it },
        times.getOrNull(position + 1)?.takeIf { it.periodIndex in range }?.startTime?.let(::parseMinuteOfDay)?.let { it - end }
    ).filter { it > 0 }
    val floor = (adjacent.maxOrNull() ?: draft.scheme.breakDurationMinutes).coerceAtLeast(1)
    return minOf((end - start).coerceAtLeast(1), floor)
}

internal fun timelinePartStartBounds(config: ScheduleConfigEntity, draft: PeriodSchemeDraft, part: PeriodDayPart): IntRange? {
    val times = resolveSchemeTimes(config, draft).sortedBy { it.periodIndex }
    if (validateResolvedPeriodTimes(times) != null) return null
    val range = config.periodRange(part)
    val within = times.filter { it.periodIndex in range }
    if (within.isEmpty()) return null
    // Only the last lesson may be squeezed, down to its adjacent break's duration.
    val minimumSpan = requireNotNull(parseMinuteOfDay(within.last().startTime)) - timelinePartAnchorMinute(config, draft, part) +
        minimumTimelineLessonMinutes(config, draft, within.last().periodIndex)
    val previous = times.lastOrNull { it.periodIndex < range.first }?.endTime?.let(::parseMinuteOfDay) ?: 0
    val next = timelinePartBoundary(config, draft, part)
    return (previous..(next - minimumSpan)).takeUnless { it.isEmpty() }
}

private data class TimelinePartPlacement(val part: PeriodDayPart, val anchor: Int, val span: Int)

private fun timelinePartPlacements(config: ScheduleConfigEntity, draft: PeriodSchemeDraft): List<TimelinePartPlacement>? {
    val times = resolveSchemeTimes(config, draft).sortedBy { it.periodIndex }
    if (validateResolvedPeriodTimes(times) != null) return null
    return PeriodDayPart.entries.filter { config.periodCount(it) > 0 }.map { part ->
        val last = times.lastOrNull { it.periodIndex in config.periodRange(part) } ?: return null
        val anchor = timelinePartAnchorMinute(config, draft, part)
        val end = parseMinuteOfDay(last.endTime) ?: return null
        if (end < anchor) return null
        TimelinePartPlacement(part, anchor, end - anchor)
    }
}

/** The selected section can be placed first; adjacent sections shift only when they overlap. */
internal fun timelineFlexiblePartStartBounds(
    config: ScheduleConfigEntity, draft: PeriodSchemeDraft, part: PeriodDayPart
): IntRange? {
    val placements = timelinePartPlacements(config, draft) ?: return null
    val index = placements.indexOfFirst { it.part == part }
    if (index < 0) return null
    val earliest = placements.take(index).sumOf { it.span }
    val latest = LastMinuteOfDay - placements.drop(index).sumOf { it.span }
    return (earliest..latest).takeUnless { it.isEmpty() }
}

internal fun moveTimelinePartWithNeighbours(
    session: PeriodTimelineSession, part: PeriodDayPart, requestedStart: Int
): PeriodTimelineSession {
    val placements = timelinePartPlacements(session.config, session.active) ?: return session
    val index = placements.indexOfFirst { it.part == part }
    val bounds = timelineFlexiblePartStartBounds(session.config, session.active, part) ?: return session
    if (index < 0) return session
    val anchors = placements.map { it.anchor }.toMutableList()
    anchors[index] = requestedStart.coerceIn(bounds)
    for (position in index - 1 downTo 0) {
        anchors[position] = minOf(anchors[position], anchors[position + 1] - placements[position].span)
    }
    for (position in index + 1 until placements.size) {
        anchors[position] = maxOf(anchors[position], anchors[position - 1] + placements[position - 1].span)
    }
    if (placements.indices.all { anchors[it] == placements[it].anchor }) return session

    val shifted = session.active.materializeForTimeline(session.config)
    val offsets = placements.mapIndexed { position, placement -> placement.part to (anchors[position] - placement.anchor) }.toMap()
    val startTimes = placements.mapIndexed { position, placement -> placement.part to timelineMinuteText(anchors[position]) }.toMap()
    val scheme = shifted.scheme.copy(
        morningStartTime = startTimes[PeriodDayPart.MORNING] ?: shifted.scheme.morningStartTime,
        noonStartTime = startTimes[PeriodDayPart.NOON] ?: shifted.scheme.noonStartTime,
        afternoonStartTime = startTimes[PeriodDayPart.AFTERNOON] ?: shifted.scheme.afternoonStartTime,
        eveningStartTime = startTimes[PeriodDayPart.EVENING] ?: shifted.scheme.eveningStartTime
    )
    val times = shifted.times.map { time ->
        val section = placements.first { time.periodIndex in session.config.periodRange(it.part) }.part
        val offset = offsets.getValue(section)
        time.copy(
            startTime = timelineMinuteText(requireNotNull(parseMinuteOfDay(time.startTime)) + offset),
            endTime = timelineMinuteText(requireNotNull(parseMinuteOfDay(time.endTime)) + offset)
        )
    }
    if (validateResolvedPeriodTimes(times) != null) return session
    val movedParts = offsets.filterValues { it != 0 }.keys
    return session.updateActive(shifted.copy(scheme = scheme, times = times))
        .copy(uncompressedLastMinutes = session.uncompressedLastMinutes - movedParts)
}

/** Move a whole day part. At the next boundary only its last lesson is shortened. */
internal fun shiftTimelinePart(config: ScheduleConfigEntity, draft: PeriodSchemeDraft, part: PeriodDayPart, requestedStart: Int,
    uncompressedLastMinutes: Int? = null): PeriodSchemeDraft {
    val bounds = timelinePartStartBounds(config, draft, part) ?: return draft
    val materialized = draft.materializeForTimeline(config)
    val range = config.periodRange(part)
    val lastIndex = materialized.times.last { it.periodIndex in range }.periodIndex
    val boundary = timelinePartBoundary(config, materialized, part)
    val originalStart = timelinePartAnchorMinute(config, materialized, part)
    val start = requestedStart.coerceIn(bounds)
    val delta = start - originalStart
    val text = timelineMinuteText(start)
    val scheme = when (part) {
        PeriodDayPart.MORNING -> materialized.scheme.copy(morningStartTime = text)
        PeriodDayPart.NOON -> materialized.scheme.copy(noonStartTime = text)
        PeriodDayPart.AFTERNOON -> materialized.scheme.copy(afternoonStartTime = text)
        PeriodDayPart.EVENING -> materialized.scheme.copy(eveningStartTime = text)
    }
    return materialized.copy(scheme = scheme, times = materialized.times.map {
        if (it.periodIndex !in range) it else it.copy(
            startTime = timelineMinuteText(requireNotNull(parseMinuteOfDay(it.startTime)) + delta),
            endTime = timelineMinuteText((requireNotNull(parseMinuteOfDay(it.endTime)) + delta).let { end ->
                if (it.periodIndex == lastIndex) minOf(
                    uncompressedLastMinutes?.let { duration -> requireNotNull(parseMinuteOfDay(it.startTime)) + delta + duration } ?: end,
                    boundary
                ) else end
            })
        )
    })
}

internal fun timelineFirstLessonStartBounds(config: ScheduleConfigEntity, draft: PeriodSchemeDraft, part: PeriodDayPart): IntRange? {
    val anchorBounds = timelinePartStartBounds(config, draft, part) ?: return null
    val first = resolveSchemeTimes(config, draft).firstOrNull { it.periodIndex in config.periodRange(part) } ?: return null
    val original = requireNotNull(parseMinuteOfDay(first.startTime))
    val anchor = timelinePartAnchorMinute(config, draft, part)
    val minimum = maxOf(anchor, anchorBounds.first)
    return minimum..maxOf(minimum, anchorBounds.last + original - anchor)
}

/** Delay the first lesson and its successors without moving the configured section divider. */
internal fun shiftTimelineFirstLesson(config: ScheduleConfigEntity, draft: PeriodSchemeDraft, part: PeriodDayPart, requestedStart: Int,
    uncompressedLastMinutes: Int? = null): PeriodSchemeDraft {
    val bounds = timelineFirstLessonStartBounds(config, draft, part) ?: return draft
    val materialized = draft.materializeForTimeline(config)
    val range = config.periodRange(part)
    val within = materialized.times.filter { it.periodIndex in range }
    val delta = requestedStart.coerceIn(bounds) - requireNotNull(parseMinuteOfDay(within.first().startTime))
    val boundary = timelinePartBoundary(config, materialized, part)
    return materialized.copy(times = materialized.times.map {
        if (it.periodIndex !in range) it else it.copy(
            startTime = timelineMinuteText(requireNotNull(parseMinuteOfDay(it.startTime)) + delta),
            endTime = timelineMinuteText((requireNotNull(parseMinuteOfDay(it.endTime)) + delta).let { end ->
                if (it.periodIndex == within.last().periodIndex) minOf(
                    uncompressedLastMinutes?.let { duration -> requireNotNull(parseMinuteOfDay(it.startTime)) + delta + duration } ?: end,
                    boundary
                ) else end
            })
        )
    })
}

/** Resize one lesson or the break after it. Later day parts retain their own start. */
internal fun resizeTimelineBlock(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft,
    periodIndex: Int,
    isBreak: Boolean,
    requestedMinutes: Int,
    uncompressedLastMinutes: Int? = null
): PeriodSchemeDraft {
    val materialized = draft.materializeForTimeline(config)
    val times = materialized.times.sortedBy { it.periodIndex }
    val position = times.indexOfFirst { it.periodIndex == periodIndex }
    val current = times.getOrNull(position) ?: return draft
    val part = PeriodDayPart.entries.firstOrNull { periodIndex in config.periodRange(it) } ?: return draft
    val range = config.periodRange(part)
    val next = times.getOrNull(position + 1)
    if (isBreak && (next == null || next.periodIndex !in range)) return draft
    if (validateResolvedPeriodTimes(times) != null) return draft
    val start = requireNotNull(parseMinuteOfDay(current.startTime))
    val end = requireNotNull(parseMinuteOfDay(current.endTime))
    val oldDuration = if (isBreak) requireNotNull(parseMinuteOfDay(next!!.startTime)) - end else end - start
    val last = times.last { it.periodIndex in range }
    val boundary = timelinePartBoundary(config, materialized, part)
    val lastStart = requireNotNull(parseMinuteOfDay(last.startTime))
    val lastDuration = requireNotNull(parseMinuteOfDay(last.endTime)) - lastStart
    // A zero-minute break keeps two lessons back to back, so it is a valid target.
    val minimum = if (isBreak) 0 else minimumTimelineLessonMinutes(config, materialized, periodIndex)
    val lastMinimum = minimumTimelineLessonMinutes(config, materialized, last.periodIndex)
    fun fits(duration: Int): Boolean {
        if (last.periodIndex == periodIndex && !isBreak) return start + duration <= boundary
        val floor = if (isBreak && next?.periodIndex == last.periodIndex) {
            minOf(lastDuration, duration.coerceAtLeast(1))
        } else lastMinimum
        return lastStart + duration - oldDuration + floor <= boundary
    }
    var duration = requestedMinutes.coerceIn(minimum, LastMinuteOfDay)
    if (!fits(minimum)) return draft
    if (!fits(duration)) {
        var low = minimum
        var high = duration
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (fits(mid)) low = mid else high = mid - 1
        }
        duration = low
    }
    val delta = duration - oldDuration
    return materialized.copy(times = times.map { time ->
        when {
            time.periodIndex == periodIndex && !isBreak -> time.copy(endTime = timelineMinuteText(end + delta))
            time.periodIndex > periodIndex && time.periodIndex in range -> time.copy(
                startTime = timelineMinuteText(requireNotNull(parseMinuteOfDay(time.startTime)) + delta),
                endTime = timelineMinuteText((requireNotNull(parseMinuteOfDay(time.endTime)) + delta).let {
                    if (time.periodIndex == last.periodIndex) minOf(
                        lastStart + delta + (uncompressedLastMinutes ?: lastDuration), boundary
                    ) else it
                })
            )
            else -> time
        }
    })
}

private fun editTimelinePart(
    session: PeriodTimelineSession, part: PeriodDayPart, explicitlyEditedLesson: Int? = null,
    edit: (Int?) -> PeriodSchemeDraft
): PeriodTimelineSession {
    val last = resolveSchemeTimes(session.config, session.active).lastOrNull { it.periodIndex in session.config.periodRange(part) }
        ?: return session
    val desired = if (explicitlyEditedLesson == last.periodIndex) null else session.uncompressedLastMinutes[part]
        ?: (requireNotNull(parseMinuteOfDay(last.endTime)) - requireNotNull(parseMinuteOfDay(last.startTime)))
    val changed = edit(desired)
    val final = changed.times.first { it.periodIndex == last.periodIndex }
    val actual = requireNotNull(parseMinuteOfDay(final.endTime)) - requireNotNull(parseMinuteOfDay(final.startTime))
    return session.updateActive(changed).copy(uncompressedLastMinutes =
        if (desired != null && actual < desired) session.uncompressedLastMinutes + (part to desired)
        else session.uncompressedLastMinutes - part)
}

internal fun resizeTimelineBlock(session: PeriodTimelineSession, index: Int, isBreak: Boolean, minutes: Int): PeriodTimelineSession {
    val part = PeriodDayPart.entries.firstOrNull { index in session.config.periodRange(it) } ?: return session
    return editTimelinePart(session, part, if (isBreak) null else index) { desired ->
        resizeTimelineBlock(session.config, session.active, index, isBreak, minutes, desired)
    }
}

internal fun shiftTimelineFirstLesson(session: PeriodTimelineSession, part: PeriodDayPart, start: Int): PeriodTimelineSession =
    editTimelinePart(session, part) { desired -> shiftTimelineFirstLesson(session.config, session.active, part, start, desired) }

/** Removing a leading gap moves lessons to the section anchor without changing their numbering. */
internal fun resizeTimelineLeadingBreak(session: PeriodTimelineSession, part: PeriodDayPart, minutes: Int): PeriodTimelineSession =
    shiftTimelineFirstLesson(session, part,
        timelinePartAnchorMinute(session.config, session.active, part) + minutes.coerceAtLeast(0))

internal fun shiftTimelinePart(session: PeriodTimelineSession, part: PeriodDayPart, start: Int): PeriodTimelineSession =
    editTimelinePart(session, part) { desired -> shiftTimelinePart(session.config, session.active, part, start, desired) }

/** Use free time in a section, including an empty section; existing lessons keep their clocks. */
internal fun appendTimelinePeriod(session: PeriodTimelineSession, part: PeriodDayPart): PeriodTimelineSession? {
    if (session.config.totalPeriodCount() >= 40) return null
    val empty = session.config.periodCount(part) == 0
    val after = PeriodDayPart.entries.take(part.ordinal + 1).sumOf { session.config.periodCount(it) }
    val config = session.config.withTimelineCount(part, session.config.periodCount(part) + 1)
    val schemes = session.draft.schemes.map { source ->
        val draft = source.materializeForTimeline(session.config)
        if (validateResolvedPeriodTimes(draft.times) != null) return null
        val boundary = timelinePartBoundary(session.config, draft, part)
        val previousEnd = draft.times.lastOrNull { it.periodIndex <= after }?.endTime?.let(::parseMinuteOfDay) ?: 0
        if (boundary - previousEnd < if (empty) 1 else 2) return null
        val end = if (empty) timelinePartAnchorMinute(session.config, draft, part).coerceIn(previousEnd, boundary - 1)
            else previousEnd
        val available = boundary - end
        val gap = if (empty) 0 else draft.scheme.breakDurationMinutes.coerceAtLeast(1).coerceAtMost(available / 2)
        val duration = draft.scheme.classDurationMinutes.coerceAtLeast(maxOf(1, gap)).coerceAtMost(available - gap)
        draft.copy(times = (draft.times.map {
            if (it.periodIndex > after) it.copy(periodIndex = it.periodIndex + 1) else it
        } + PeriodSchemeTimeEntity(draft.scheme.id, after + 1, timelineMinuteText(end + gap),
            timelineMinuteText(end + gap + duration))).sortedBy { it.periodIndex })
            .rebaseTimelineAnchors(config)
            .takeIf { validateResolvedPeriodTimes(it.times) == null } ?: return null
    }
    return session.copy(config = config,
        draft = session.draft.copy(schemes = schemes,
            topologyOperations = session.draft.topologyOperations + PeriodTopologyOperation.AddAfter(after)),
        vacancies = session.vacancies.map { if (it.part == part && it.after >= session.config.periodCount(part)) it.copy(after = it.after + 1) else it },
        uncompressedLastMinutes = session.uncompressedLastMinutes - part)
}

internal fun deleteTimelinePeriod(session: PeriodTimelineSession, index: Int, keepVacancy: Boolean = true): PeriodTimelineSession? {
    if (session.config.totalPeriodCount() <= 1) return null
    val part = PeriodDayPart.entries.firstOrNull { index in session.config.periodRange(it) } ?: return null
    val after = index - session.config.periodRange(part).first
    val config = session.config.withTimelineCount(part, session.config.periodCount(part) - 1)
    val materialized = session.draft.schemes.map { it.materializeForTimeline(session.config) }
    val schemes = materialized.map { deletePeriodFromSchemeDraft(it, index, config) ?: return null }
    val vacancy = PeriodTimelineVacancy(
        (session.vacancies.maxOfOrNull { it.id } ?: 0) + 1, part, after,
        materialized.associate { it.scheme.id to it.times.first { time -> time.periodIndex == index } },
        deletedOperationIndex = session.draft.topologyOperations.size
    )
    val vacancies = session.vacancies.map {
        if (it.part == part && it.after > after) it.copy(after = it.after - 1) else it
    } + if (keepVacancy) listOf(vacancy) else emptyList()
    return session.copy(config = config, draft = session.draft.copy(
        schemes = schemes,
        topologyOperations = session.draft.topologyOperations + PeriodTopologyOperation.Delete(index)
    ), vacancies = vacancies, uncompressedLastMinutes = session.uncompressedLastMinutes - part)
}

/** Fill a removed slot while preserving other sections and updating every scheme atomically. */
internal fun insertTimelinePeriod(session: PeriodTimelineSession, vacancyId: Int): PeriodTimelineSession? {
    if (session.config.totalPeriodCount() >= 40) return null
    val vacancy = session.vacancies.firstOrNull { it.id == vacancyId } ?: return null
    val part = vacancy.part
    val after = PeriodDayPart.entries.take(part.ordinal).sumOf { session.config.periodCount(it) } + vacancy.after
    val config = session.config.withTimelineCount(part, session.config.periodCount(part) + 1)
    val schemes = session.draft.schemes.map { source ->
        val draft = source.materializeForTimeline(session.config)
        val removed = vacancy.removedTimes.getValue(draft.scheme.id)
        val range = session.config.periodRange(part)
        val before = draft.times.lastOrNull { it.periodIndex <= after && it.periodIndex in range }
        val following = draft.times.filter { it.periodIndex > after && it.periodIndex in range }
        val boundary = timelinePartBoundary(session.config, draft, part)
        val savedStart = requireNotNull(parseMinuteOfDay(removed.startTime))
        val duration = requireNotNull(parseMinuteOfDay(removed.endTime)) - savedStart
        val minimumStart = before?.endTime?.let(::parseMinuteOfDay)?.let { it + if (it == savedStart) 0 else 1 }
            ?: timelinePartAnchorMinute(session.config, draft, part)
        var start = maxOf(savedStart, minimumStart)
        var end = start + duration
        if (following.isEmpty() && end > boundary) {
            val previousEnd = before?.endTime?.let(::parseMinuteOfDay) ?: minimumStart
            val available = boundary - previousEnd
            if (available < if (before == null) 1 else 2) return null
            val gap = (start - previousEnd).coerceAtLeast(if (before == null) 0 else 1)
                .coerceAtMost(if (before == null) available - 1 else available / 2)
            start = previousEnd + gap
            end = minOf(start + duration, boundary)
        }
        if (end > boundary) return null
        val shift = following.firstOrNull()?.startTime?.let(::parseMinuteOfDay)?.let {
            if (end <= it) 0 else end + 1 - it
        } ?: 0
        val last = following.lastOrNull()
        if (last != null && requireNotNull(parseMinuteOfDay(last.startTime)) + shift +
            minimumTimelineLessonMinutes(session.config, draft, last.periodIndex) > boundary) return null
        val times = draft.times.map { time ->
            val inFollowing = time.periodIndex > after && time.periodIndex in range
            time.copy(
                periodIndex = if (time.periodIndex > after) time.periodIndex + 1 else time.periodIndex,
                startTime = if (inFollowing) timelineMinuteText(requireNotNull(parseMinuteOfDay(time.startTime)) + shift) else time.startTime,
                endTime = if (inFollowing) timelineMinuteText((requireNotNull(parseMinuteOfDay(time.endTime)) + shift).let {
                    if (time.periodIndex == last?.periodIndex) minOf(it, boundary) else it
                }) else time.endTime
            )
        } + removed.copy(periodIndex = after + 1, startTime = timelineMinuteText(start), endTime = timelineMinuteText(end))
        draft.copy(times = times.sortedBy { it.periodIndex }).takeIf { validateResolvedPeriodTimes(it.times) == null } ?: return null
    }
    return session.copy(config = config, draft = session.draft.copy(
        schemes = schemes, topologyOperations = session.draft.topologyOperations + PeriodTopologyOperation.AddAfter(
            after, restoredDeletionOperationIndex = vacancy.deletedOperationIndex.takeIf { it >= 0 }
        )
    ), vacancies = session.vacancies.filterNot { it.id == vacancyId }.map {
        if (it.part == part && (it.after > vacancy.after || it.after == vacancy.after &&
                requireNotNull(parseMinuteOfDay(it.removedTimes.getValue(session.draft.activeSchemeId).startTime)) >=
                requireNotNull(parseMinuteOfDay(vacancy.removedTimes.getValue(session.draft.activeSchemeId).startTime))))
            it.copy(after = it.after + 1) else it
    }, uncompressedLastMinutes = session.uncompressedLastMinutes - part)
}

internal fun ScheduleConfigEntity.withTimelineCount(part: PeriodDayPart, count: Int) = when (part) {
    PeriodDayPart.MORNING -> copy(morningPeriodCount = count)
    PeriodDayPart.NOON -> copy(noonPeriodCount = count)
    PeriodDayPart.AFTERNOON -> copy(afternoonPeriodCount = count)
    PeriodDayPart.EVENING -> copy(eveningPeriodCount = count)
}

/** Resize the shared structure at its tail, retaining explicit course-remapping operations. */
internal fun resizeTimelineStructure(
    session: PeriodTimelineSession,
    target: ScheduleConfigEntity
): PeriodTimelineSession? {
    if (target.totalPeriodCount() > maxOf(40, session.config.totalPeriodCount())) return null
    var current = if (session.config.hasSamePeriodTopology(target)) session else session.copy(
        draft = session.draft.copy(schemes = session.draft.schemes.map { it.materializeForTimeline(session.config) })
    )
    while (current.config.totalPeriodCount() > target.totalPeriodCount()) {
        current = deleteTimelinePeriod(current, current.config.totalPeriodCount(), keepVacancy = false) ?: return null
    }
    while (current.config.totalPeriodCount() < target.totalPeriodCount()) {
        val after = current.config.totalPeriodCount()
        val part = PeriodDayPart.entries.last { current.config.periodCount(it) > 0 }
        val config = current.config.withTimelineCount(part, current.config.periodCount(part) + 1)
        val schemes = current.draft.schemes.map {
            insertPeriodIntoSchemeDraft(it.materializeForTimeline(current.config), after, config) ?: return null
        }
        current = PeriodTimelineSession(config, current.draft.copy(
            schemes = schemes,
            topologyOperations = current.draft.topologyOperations + PeriodTopologyOperation.AddAfter(after)
        ))
    }
    return if (current.config.hasSamePeriodTopology(target)) current.copy(config = target) else current.copy(
        config = target,
        draft = current.draft.copy(schemes = current.draft.schemes.map { it.rebaseTimelineAnchors(target) })
    )
}
