package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.serialization.Serializable

@Serializable
data class SavedPeriodTime(val periodIndex: Int, val startTime: String, val endTime: String)

/** A standalone bell timetable. Applying it makes a local copy in the destination schedule. */
@Serializable
data class SavedPeriodScheme(
    val id: String,
    val name: String,
    val morningPeriodCount: Int,
    val noonPeriodCount: Int,
    val afternoonPeriodCount: Int,
    val eveningPeriodCount: Int,
    val classDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val morningStartTime: String,
    val noonStartTime: String,
    val afternoonStartTime: String,
    val eveningStartTime: String,
    val times: List<SavedPeriodTime>
) {
    fun validate() {
        require(id.isNotBlank() && id.length <= 100) { "作息库编号无效" }
        require(name.isNotBlank() && name.length <= 60) { "作息名称须为 1 至 60 个字符" }
        val counts = listOf(morningPeriodCount, noonPeriodCount, afternoonPeriodCount, eveningPeriodCount)
        require(counts.all { it in 0..48 } && counts.sum() in 1..48) { "作息库节次数无效" }
        require(times.size == counts.sum()) { "作息库节次数与时间线不一致" }
        require(classDurationMinutes in 1..300 && breakDurationMinutes in 0..300) { "作息库时长无效" }
        require(listOf(morningStartTime, noonStartTime, afternoonStartTime, eveningStartTime)
            .all { parseMinuteOfDay(it) != null }) { "作息库分段时间无效" }
        validateResolvedPeriodTimes(times.map {
            PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime)
        })?.let { throw IllegalArgumentException(it) }
    }
}

fun savePeriodSchemeSnapshot(
    id: String, name: String, config: ScheduleConfigEntity, draft: PeriodSchemeDraft
): SavedPeriodScheme = SavedPeriodScheme(
    id = id, name = name.trim(),
    morningPeriodCount = config.morningPeriodCount, noonPeriodCount = config.noonPeriodCount,
    afternoonPeriodCount = config.afternoonPeriodCount, eveningPeriodCount = config.eveningPeriodCount,
    classDurationMinutes = draft.scheme.classDurationMinutes,
    breakDurationMinutes = draft.scheme.breakDurationMinutes,
    morningStartTime = draft.scheme.morningStartTime, noonStartTime = draft.scheme.noonStartTime,
    afternoonStartTime = draft.scheme.afternoonStartTime, eveningStartTime = draft.scheme.eveningStartTime,
    times = librarySchemeTimes(config, draft).map { SavedPeriodTime(it.periodIndex, it.startTime, it.endTime) }
).also { it.validate() }

data class AppliedPeriodScheme(val config: ScheduleConfigEntity, val draft: SchedulePeriodSchemesDraft)

/** Opens a library item in the same timeline editor without touching a schedule's stored data. */
internal fun savedPeriodSchemeSession(saved: SavedPeriodScheme, base: ScheduleConfigEntity): PeriodTimelineSession {
    saved.validate()
    val config = base.copy(morningPeriodCount = saved.morningPeriodCount, noonPeriodCount = saved.noonPeriodCount,
        afternoonPeriodCount = saved.afternoonPeriodCount, eveningPeriodCount = saved.eveningPeriodCount,
        classDurationMinutes = saved.classDurationMinutes, breakDurationMinutes = saved.breakDurationMinutes)
    val scheme = PeriodSchemeEntity(id = -1, scheduleId = base.id, name = saved.name, isActive = true,
        classDurationMinutes = saved.classDurationMinutes, breakDurationMinutes = saved.breakDurationMinutes,
        morningStartTime = saved.morningStartTime, noonStartTime = saved.noonStartTime,
        afternoonStartTime = saved.afternoonStartTime, eveningStartTime = saved.eveningStartTime)
    return PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(PeriodSchemeDraft(scheme,
        saved.times.map { PeriodSchemeTimeEntity(scheme.id, it.periodIndex, it.startTime, it.endTime) })), scheme.id))
}

fun applySavedPeriodScheme(
    saved: SavedPeriodScheme, config: ScheduleConfigEntity, draft: SchedulePeriodSchemesDraft
): AppliedPeriodScheme {
    saved.validate()
    require(saved.times.size == config.totalPeriodCount()) {
        "此作息有 ${saved.times.size} 节，当前课表有 ${config.totalPeriodCount()} 节。请先在详细节次编辑中调整节数后再套用。"
    }
    val id = (draft.schemes.minOfOrNull { it.scheme.id } ?: 0L).coerceAtMost(0L) - 1L
    val scheme = PeriodSchemeEntity(
        id = id, scheduleId = config.id, name = saved.name, mode = PeriodSchemeMode.MANUAL,
        isActive = true, classDurationMinutes = saved.classDurationMinutes,
        breakDurationMinutes = saved.breakDurationMinutes, morningStartTime = saved.morningStartTime,
        noonStartTime = saved.noonStartTime, afternoonStartTime = saved.afternoonStartTime,
        eveningStartTime = saved.eveningStartTime
    )
    // Materialize all existing schemes before changing day-part boundaries. Their bells stay intact.
    val existing = draft.schemes.map {
        it.copy(scheme = it.scheme.copy(mode = PeriodSchemeMode.MANUAL, isActive = false),
            times = librarySchemeTimes(config, it), specialBreaks = emptyMap(), overriddenPeriods = emptySet())
    }
    val matching = existing.firstOrNull {
        savePeriodSchemeSnapshot(saved.id, it.scheme.name, config.copy(
            morningPeriodCount = saved.morningPeriodCount, noonPeriodCount = saved.noonPeriodCount,
            afternoonPeriodCount = saved.afternoonPeriodCount, eveningPeriodCount = saved.eveningPeriodCount), it) == saved
    }
    return AppliedPeriodScheme(
        config.copy(morningPeriodCount = saved.morningPeriodCount, noonPeriodCount = saved.noonPeriodCount,
            afternoonPeriodCount = saved.afternoonPeriodCount, eveningPeriodCount = saved.eveningPeriodCount),
        draft.copy(schemes = if (matching != null) existing else existing + PeriodSchemeDraft(scheme, saved.times.map {
            PeriodSchemeTimeEntity(id, it.periodIndex, it.startTime, it.endTime)
        }), activeSchemeId = matching?.scheme?.id ?: id)
    )
}

private fun librarySchemeTimes(config: ScheduleConfigEntity, draft: PeriodSchemeDraft): List<PeriodSchemeTimeEntity> =
    // A stored automatic timeline is user content too; snapshot its bells without regenerating it.
    if (draft.scheme.mode == PeriodSchemeMode.AUTO_MATCH && draft.times.isEmpty()) resolveSchemeTimes(config, draft)
    else draft.times.sortedBy { it.periodIndex }
