package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.domain.schedule.automaticPartSpanMinutes as domainAutomaticPartSpanMinutes
import com.xiaomanjun.sleepdownschedule.domain.schedule.constrainAutomaticPartStarts as domainConstrainAutomaticPartStarts
import com.xiaomanjun.sleepdownschedule.domain.schedule.constrainPeriodTimeSelection as domainConstrainPeriodTimeSelection
import com.xiaomanjun.sleepdownschedule.domain.schedule.decodeOverrides as domainDecodeOverrides
import com.xiaomanjun.sleepdownschedule.domain.schedule.decodeSpecialBreaks as domainDecodeSpecialBreaks
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeOverrides as domainEncodeOverrides
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeSpecialBreaks as domainEncodeSpecialBreaks
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasSameGenerationInputs as domainHasSameGenerationInputs
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasSamePeriodTopology as domainHasSamePeriodTopology
import com.xiaomanjun.sleepdownschedule.domain.schedule.inferPeriodCounts as domainInferPeriodCounts
import com.xiaomanjun.sleepdownschedule.domain.schedule.normalizeAutoSchemeStarts as domainNormalizeAutoSchemeStarts
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseMinuteOfDay as domainParseMinuteOfDay
import com.xiaomanjun.sleepdownschedule.domain.schedule.periodCount as domainPeriodCount
import com.xiaomanjun.sleepdownschedule.domain.schedule.periodRange as domainPeriodRange
import com.xiaomanjun.sleepdownschedule.domain.schedule.periodTimePickerBounds as domainPeriodTimePickerBounds
import com.xiaomanjun.sleepdownschedule.domain.schedule.resolveSchemeTimes as domainResolveSchemeTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.resolveSchemeTimesForSave as domainResolveSchemeTimesForSave
import com.xiaomanjun.sleepdownschedule.domain.schedule.totalPeriodCount as domainTotalPeriodCount
import com.xiaomanjun.sleepdownschedule.domain.schedule.validateResolvedPeriodTimes as domainValidateResolvedPeriodTimes

typealias PeriodDayPart = com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodDayPart
typealias PeriodPartCounts = com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodPartCounts
typealias PeriodTopologyOperation = com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTopologyOperation
typealias PeriodSchemeDraft = com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeDraft
typealias SchedulePeriodSchemesDraft =
    com.xiaomanjun.sleepdownschedule.domain.schedule.SchedulePeriodSchemesDraft
internal typealias PeriodTimePickerBounds =
    com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimePickerBounds
internal typealias PeriodTimeSelection =
    com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimeSelection
internal typealias PeriodTimeSelectionAnchor =
    com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimeSelectionAnchor

internal const val LastMinuteOfDay =
    com.xiaomanjun.sleepdownschedule.domain.schedule.LastMinuteOfDay

internal fun parseMinuteOfDay(value: String?): Int? = domainParseMinuteOfDay(value)

internal fun periodTimePickerBounds(previousEnd: String?, nextStart: String?): PeriodTimePickerBounds =
    domainPeriodTimePickerBounds(previousEnd, nextStart)

internal fun constrainPeriodTimeSelection(
    startMinute: Int,
    endMinute: Int,
    bounds: PeriodTimePickerBounds,
    anchor: PeriodTimeSelectionAnchor = PeriodTimeSelectionAnchor.NONE
): PeriodTimeSelection = domainConstrainPeriodTimeSelection(startMinute, endMinute, bounds, anchor)

internal fun automaticPartSpanMinutes(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft,
    part: PeriodDayPart
): Int = domainAutomaticPartSpanMinutes(config, draft, part)

internal fun constrainAutomaticPartStarts(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft,
    requestedStarts: Map<PeriodDayPart, Int>
): Map<PeriodDayPart, Int> = domainConstrainAutomaticPartStarts(config, draft, requestedStarts)

fun ScheduleConfigEntity.periodCount(part: PeriodDayPart): Int = domainPeriodCount(part)

fun ScheduleConfigEntity.totalPeriodCount(): Int = domainTotalPeriodCount()

fun ScheduleConfigEntity.hasSamePeriodTopology(other: ScheduleConfigEntity): Boolean =
    domainHasSamePeriodTopology(other)

fun PeriodSchemeDraft.hasSameGenerationInputs(other: PeriodSchemeDraft): Boolean =
    domainHasSameGenerationInputs(other)

fun resolveSchemeTimesForSave(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft,
    storedConfig: ScheduleConfigEntity?,
    storedDraft: PeriodSchemeDraft?
): List<PeriodSchemeTimeEntity> =
    domainResolveSchemeTimesForSave(config, draft, storedConfig, storedDraft)

fun ScheduleConfigEntity.periodRange(part: PeriodDayPart): IntRange = domainPeriodRange(part)

fun inferPeriodCounts(periods: List<PeriodEntity>): PeriodPartCounts = domainInferPeriodCounts(periods)

fun resolveSchemeTimes(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft
): List<PeriodSchemeTimeEntity> = domainResolveSchemeTimes(config, draft)

fun normalizeAutoSchemeStarts(
    config: ScheduleConfigEntity,
    draft: PeriodSchemeDraft
): PeriodSchemeDraft = domainNormalizeAutoSchemeStarts(config, draft)

fun encodeSpecialBreaks(value: Map<Int, Int>): String = domainEncodeSpecialBreaks(value)

fun decodeSpecialBreaks(value: String): Map<Int, Int> = domainDecodeSpecialBreaks(value)

fun encodeOverrides(value: Set<Int>): String = domainEncodeOverrides(value)

fun decodeOverrides(value: String): Set<Int> = domainDecodeOverrides(value)

fun validateResolvedPeriodTimes(times: List<PeriodSchemeTimeEntity>): String? =
    domainValidateResolvedPeriodTimes(times)
