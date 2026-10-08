package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SavedPeriodTime(val periodIndex: Int, val startTime: String, val endTime: String)

@Serializable
data class PeriodSchemeSource(val scheduleName: String, val schemeName: String)

/** Portable snapshot of a public bell timetable; Room owns the live identity and references. */
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
    val times: List<SavedPeriodTime>,
    val sources: List<PeriodSchemeSource> = emptyList(),
    val alternateNames: List<String> = emptyList(),
    val createdInLibrary: Boolean = false,
    val mode: PeriodSchemeMode = PeriodSchemeMode.MANUAL,
    val specialBreaksJson: String = "{}",
    val overridesJson: String = "{}",
    @kotlinx.serialization.Transient val roomId: Long = 0,
    @kotlinx.serialization.Transient val usages: List<PeriodSchemeUsageSnapshot>? = null,
    @kotlinx.serialization.Transient val storedDraft: PeriodSchemeDraft? = null
) {
    fun validate() {
        require(id.isNotBlank() && id.length <= 100) { "作息库编号无效" }
        require(name.isNotBlank() && name.length <= 60) { "作息名称须为 1 至 60 个字符" }
        require(alternateNames.all { it.isNotBlank() && it.length <= 60 }) { "作息原名称无效" }
        require(sources.all { it.scheduleName.isNotBlank() && it.schemeName.isNotBlank() && it.schemeName.length <= 60 }) {
            "作息来源无效"
        }
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

/** Identity follows the editable timetable, not its title, owner or import ID. */
fun SavedPeriodScheme.hasSameContent(other: SavedPeriodScheme): Boolean {
    fun SavedPeriodScheme.content() = listOf(
        listOf(morningPeriodCount, noonPeriodCount, afternoonPeriodCount, eveningPeriodCount),
        classDurationMinutes, breakDurationMinutes, mode,
        decodeSpecialBreaks(specialBreaksJson), decodeOverrides(overridesJson),
        listOf(morningStartTime, noonStartTime, afternoonStartTime, eveningStartTime),
        times.sortedBy { it.periodIndex }.map { listOf(it.periodIndex, it.startTime, it.endTime) }
    )
    return content() == other.content()
}

private fun mergePeriodSchemeMetadata(first: SavedPeriodScheme, incoming: SavedPeriodScheme,
    preferIncoming: Boolean = false): SavedPeriodScheme {
    val chosen = if (preferIncoming) incoming else first
    return chosen.copy(
        sources = (first.sources + incoming.sources).distinct(),
        alternateNames = (first.alternateNames + incoming.alternateNames + first.name + incoming.name)
            .distinct().filterNot { it == chosen.name },
        createdInLibrary = first.createdInLibrary || incoming.createdInLibrary
    )
}

fun normalizePeriodSchemeLibrary(schemes: List<SavedPeriodScheme>, preferredId: String? = null): List<SavedPeriodScheme> {
    require(schemes.map { it.id }.distinct().size == schemes.size) { "作息库编号重复" }
    val merged = mutableListOf<SavedPeriodScheme>()
    schemes.forEach { incoming ->
        incoming.validate()
        val index = merged.indexOfFirst { it.hasSameContent(incoming) }
        if (index < 0) merged += incoming
        else merged[index] = mergePeriodSchemeMetadata(merged[index], incoming, incoming.id == preferredId)
    }
    return merged
}

/** Upgrade old one-time imports without recreating deleted entries or replacing edited bells. */
fun migrateSchedulePeriodSchemes(current: List<SavedPeriodScheme>, snapshots: List<SavedPeriodScheme>,
    scheduleName: String, previouslyImported: Boolean): List<SavedPeriodScheme> {
    val result = current.toMutableList()
    snapshots.forEach { snapshot ->
        val incoming = snapshot.copy(sources = listOf(PeriodSchemeSource(scheduleName, snapshot.name)))
        val byId = result.indexOfFirst { it.id == incoming.id }
        val index = if (byId >= 0 && (previouslyImported || result[byId].hasSameContent(incoming))) byId
            else result.indexOfFirst { it.hasSameContent(incoming) }
        if (index >= 0) result[index] = mergePeriodSchemeMetadata(result[index], incoming)
        else if (!previouslyImported) result += if (byId >= 0) incoming.copy(id = UUID.randomUUID().toString()) else incoming
    }
    return normalizePeriodSchemeLibrary(result)
}

/** Restore merges content; an ID collision with different bells must retain both versions. */
fun mergeRestoredPeriodSchemes(current: List<SavedPeriodScheme>, incoming: List<SavedPeriodScheme>): List<SavedPeriodScheme> {
    normalizePeriodSchemeLibrary(incoming) // Validate before changing anything.
    val result = current.toMutableList()
    incoming.forEach { scheme ->
        val sameId = result.indexOfFirst { it.id == scheme.id }
        if (sameId >= 0 && result[sameId].hasSameContent(scheme)) {
            result[sameId] = mergePeriodSchemeMetadata(result[sameId], scheme)
        } else {
            result += if (sameId >= 0) scheme.copy(id = UUID.randomUUID().toString()) else scheme
        }
    }
    return normalizePeriodSchemeLibrary(result)
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
    times = librarySchemeTimes(config, draft).map { SavedPeriodTime(it.periodIndex, it.startTime, it.endTime) },
    mode = draft.scheme.mode, specialBreaksJson = encodeSpecialBreaks(draft.specialBreaks),
    overridesJson = encodeOverrides(draft.overriddenPeriods), roomId = draft.scheme.id
).also { it.validate() }

data class AppliedPeriodScheme(val config: ScheduleConfigEntity, val draft: SchedulePeriodSchemesDraft)

/** Opens a library item in the same timeline editor without touching a schedule's stored data. */
internal fun savedPeriodSchemeSession(saved: SavedPeriodScheme, base: ScheduleConfigEntity): PeriodTimelineSession {
    saved.validate()
    val config = base.copy(morningPeriodCount = saved.morningPeriodCount, noonPeriodCount = saved.noonPeriodCount,
        afternoonPeriodCount = saved.afternoonPeriodCount, eveningPeriodCount = saved.eveningPeriodCount,
        classDurationMinutes = saved.classDurationMinutes, breakDurationMinutes = saved.breakDurationMinutes)
    val scheme = PeriodSchemeEntity(id = saved.roomId.takeIf { it > 0 } ?: -1, publicId = saved.id,
        scheduleId = 0, name = saved.name, isActive = false, mode = saved.mode,
        morningPeriodCount = saved.morningPeriodCount, noonPeriodCount = saved.noonPeriodCount,
        afternoonPeriodCount = saved.afternoonPeriodCount, eveningPeriodCount = saved.eveningPeriodCount,
        specialBreaksJson = saved.specialBreaksJson, overridesJson = saved.overridesJson,
        classDurationMinutes = saved.classDurationMinutes, breakDurationMinutes = saved.breakDurationMinutes,
        morningStartTime = saved.morningStartTime, noonStartTime = saved.noonStartTime,
        afternoonStartTime = saved.afternoonStartTime, eveningStartTime = saved.eveningStartTime)
    val item = saved.storedDraft ?: PeriodSchemeDraft(scheme,
        saved.times.map { PeriodSchemeTimeEntity(scheme.id, it.periodIndex, it.startTime, it.endTime) },
        decodeSpecialBreaks(saved.specialBreaksJson), decodeOverrides(saved.overridesJson))
    return PeriodTimelineSession(config, SchedulePeriodSchemesDraft(listOf(item), scheme.id,
        originalSchemes = listOf(item), expectedUsages = saved.usages))
}

fun applySavedPeriodScheme(
    saved: SavedPeriodScheme, config: ScheduleConfigEntity, draft: SchedulePeriodSchemesDraft
): AppliedPeriodScheme {
    saved.validate()
    require(saved.roomId > 0) { "请重新读取公共作息后再选择" }
    val originalCount = draft.originalSchemes.firstOrNull { it.scheme.id == draft.activeSchemeId }?.times?.size
        ?: config.totalPeriodCount()
    require(!hasNetPeriodTopologyChange(originalCount, draft.topologyOperations)) { "请先保存节次修改，再切换作息" }
    val session = savedPeriodSchemeSession(saved, config)
    return AppliedPeriodScheme(session.config.copy(activePeriodSchemeId = saved.roomId), session.draft.copy(
        originalActiveSchemeId = draft.originalActiveSchemeId ?: config.activePeriodSchemeId))
}

/** Compatibility config fields are a one-way projection of the selected public scheme. */
internal fun schemeConfig(base: ScheduleConfigEntity, scheme: PeriodSchemeEntity): ScheduleConfigEntity = base.copy(
    activePeriodSchemeId = scheme.id,
    morningPeriodCount = scheme.morningPeriodCount, noonPeriodCount = scheme.noonPeriodCount,
    afternoonPeriodCount = scheme.afternoonPeriodCount, eveningPeriodCount = scheme.eveningPeriodCount,
    classDurationMinutes = scheme.classDurationMinutes, breakDurationMinutes = scheme.breakDurationMinutes
)

private fun librarySchemeTimes(config: ScheduleConfigEntity, draft: PeriodSchemeDraft): List<PeriodSchemeTimeEntity> =
    // A stored automatic timeline is user content too; snapshot its bells without regenerating it.
    if (draft.scheme.mode == PeriodSchemeMode.AUTO_MATCH && draft.times.isEmpty()) resolveSchemeTimes(config, draft)
    else draft.times.sortedBy { it.periodIndex }
