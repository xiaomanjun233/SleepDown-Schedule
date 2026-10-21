package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*
import java.util.UUID

/** One normalized import graph for both published per-schedule archives and shared schemes. */
internal data class BackupSchemeGraph(
    val schemes: List<BackupPeriodScheme>,
    val activeIds: Map<String, String>
)

internal fun backupDerivedSchemeId(key: String): String =
    "${BackupStableId.SCHEME_PREFIX}_${UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))}"

internal fun completeBackupSchemeIds(existing: Map<String, Long>, stableIds: List<String>): Map<String, Long> {
    val result = existing.toMutableMap()
    var next = existing.values.maxOrNull() ?: 0L
    stableIds.forEach { if (it !in result) result[it] = ++next }
    return result
}

internal fun backupSchemeGraph(archive: DecodedBackupArchive): BackupSchemeGraph {
    val shared = archive.data.sharedPeriodSchemes
    val result = shared.orEmpty().toMutableList()
    val activeIds = linkedMapOf<String, String>()
    archive.data.schedules.forEach { schedule ->
        if (shared != null && schedule.activePeriodSchemeId != null) {
            require(shared.any { it.id == schedule.activePeriodSchemeId }) { "课表引用了不存在的共享作息" }
            activeIds[schedule.id] = schedule.activePeriodSchemeId
        } else {
            val legacy = if (shared == null) schedule.periodSchemes else emptyList()
            val originalSchemes = legacy.ifEmpty {
                if (schedule.periods.isEmpty()) emptyList() else listOf(BackupPeriodScheme(
                    id = backupDerivedSchemeId("legacy-periods:${schedule.id}"),
                    name = "导入作息", mode = PeriodSchemeMode.MANUAL.name, isActive = true,
                    classDurationMinutes = schedule.config.classDurationMinutes,
                    breakDurationMinutes = schedule.config.breakDurationMinutes,
                    morningStartTime = schedule.periods.first().startTime,
                    noonStartTime = "12:00", afternoonStartTime = "14:00", eveningStartTime = "19:00",
                    specialBreaksJson = "{}", overridesJson = "{}",
                    times = schedule.periods.map { BackupPeriodSchemeTime(it.periodIndex, it.startTime, it.endTime) }
                ))
            }
            val originalActive = originalSchemes.firstOrNull { it.isActive } ?: originalSchemes.firstOrNull()
            val visibleTimes = schedule.periods.map { BackupPeriodSchemeTime(it.periodIndex, it.startTime, it.endTime) }
            val visibleSnapshot = originalActive?.takeIf {
                visibleTimes.isNotEmpty() && it.times.isNotEmpty() && it.times.sortedBy { time -> time.periodIndex } != visibleTimes.sortedBy { time -> time.periodIndex }
            }?.copy(id = backupDerivedSchemeId("legacy-visible:${schedule.id}"), publicId = "",
                name = "${originalActive.name.take(48)}（当前课表）", mode = PeriodSchemeMode.MANUAL.name,
                specialBreaksJson = "{}", overridesJson = "{}", times = visibleTimes)
            val schemes = originalSchemes + listOfNotNull(visibleSnapshot)
            val selected = visibleSnapshot ?: originalActive
            selected?.let { activeIds[schedule.id] = it.id }
            result += schemes.map { scheme ->
                val times = scheme.times.ifEmpty {
                    schedule.periods.map { BackupPeriodSchemeTime(it.periodIndex, it.startTime, it.endTime) }
                }
                val counts = listOf(schedule.config.morningPeriodCount, schedule.config.noonPeriodCount,
                    schedule.config.afternoonPeriodCount, schedule.config.eveningPeriodCount)
                val inferred = inferPeriodCounts(times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime) })
                scheme.copy(publicId = scheme.publicId.ifBlank { scheme.id }, sourceScheduleName = schedule.name,
                    morningPeriodCount = if (counts.sum() == times.size) counts[0] else inferred.morning,
                    noonPeriodCount = if (counts.sum() == times.size) counts[1] else inferred.noon,
                    afternoonPeriodCount = if (counts.sum() == times.size) counts[2] else inferred.afternoon,
                    eveningPeriodCount = if (counts.sum() == times.size) counts[3] else inferred.evening,
                    times = times)
            }
        }
    }
    // This was the only library representation in older releases. New archives are authoritative
    // in Room; their compatibility preference copy must never resurrect a deleted global entry.
    if (shared == null) archive.preferences.savedPeriodSchemes.orEmpty().forEach { saved ->
        result += BackupPeriodScheme(
            id = backupDerivedSchemeId("legacy-library:${saved.id}"), publicId = saved.id,
            name = saved.name, mode = saved.mode.name, isActive = false,
            sourceScheduleName = saved.sources.firstOrNull()?.scheduleName.orEmpty(),
            morningPeriodCount = saved.morningPeriodCount, noonPeriodCount = saved.noonPeriodCount,
            afternoonPeriodCount = saved.afternoonPeriodCount, eveningPeriodCount = saved.eveningPeriodCount,
            classDurationMinutes = saved.classDurationMinutes, breakDurationMinutes = saved.breakDurationMinutes,
            morningStartTime = saved.morningStartTime, noonStartTime = saved.noonStartTime,
            afternoonStartTime = saved.afternoonStartTime, eveningStartTime = saved.eveningStartTime,
            specialBreaksJson = saved.specialBreaksJson, overridesJson = saved.overridesJson,
            times = saved.times.map { BackupPeriodSchemeTime(it.periodIndex, it.startTime, it.endTime) }
        )
    }
    require(result.map { it.id }.distinct().size == result.size) { "共享作息编号重复" }
    return BackupSchemeGraph(result, activeIds)
}

/** Titles/provenance/IDs do not alter bells. Every editable timing input participates. */
internal fun sameBackupSchemeContent(
    first: PeriodSchemeEntity, firstTimes: List<PeriodSchemeTimeEntity>,
    second: PeriodSchemeEntity, secondTimes: List<PeriodSchemeTimeEntity>
): Boolean = first.mode == second.mode &&
    first.morningPeriodCount == second.morningPeriodCount && first.noonPeriodCount == second.noonPeriodCount &&
    first.afternoonPeriodCount == second.afternoonPeriodCount && first.eveningPeriodCount == second.eveningPeriodCount &&
    first.classDurationMinutes == second.classDurationMinutes && first.breakDurationMinutes == second.breakDurationMinutes &&
    first.morningStartTime == second.morningStartTime && first.noonStartTime == second.noonStartTime &&
    first.afternoonStartTime == second.afternoonStartTime && first.eveningStartTime == second.eveningStartTime &&
    decodeSpecialBreaks(first.specialBreaksJson) == decodeSpecialBreaks(second.specialBreaksJson) &&
    decodeOverrides(first.overridesJson) == decodeOverrides(second.overridesJson) &&
    firstTimes.sortedBy { it.periodIndex }.map { Triple(it.periodIndex, it.startTime, it.endTime) } ==
    secondTimes.sortedBy { it.periodIndex }.map { Triple(it.periodIndex, it.startTime, it.endTime) }

internal data class BackupSchemeMerge(
    val additions: List<PeriodSchemeEntity>,
    val times: List<PeriodSchemeTimeEntity>,
    val ids: Map<Long, Long>
)

/** Pure merge plan, evaluated against the latest library inside the Room transaction. */
internal fun mergeBackupSchemeRows(
    current: List<PeriodSchemeEntity>, currentTimes: List<PeriodSchemeTimeEntity>,
    incoming: List<PeriodSchemeEntity>, incomingTimes: List<PeriodSchemeTimeEntity>, operationId: String,
    deduplicateLegacyContent: Boolean = true
): BackupSchemeMerge {
    val existing = current.toMutableList()
    val allTimes = currentTimes.groupBy { it.schemeId }.toMutableMap()
    val importedTimes = incomingTimes.groupBy { it.schemeId }
    val ids = linkedMapOf<Long, Long>()
    val additions = mutableListOf<PeriodSchemeEntity>()
    val addedTimes = mutableListOf<PeriodSchemeTimeEntity>()
    var nextId = (current.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(incoming.maxOfOrNull { it.id } ?: 0L)
    incoming.forEach { item ->
        val times = importedTimes[item.id].orEmpty()
        val identical = existing.firstOrNull {
            (deduplicateLegacyContent || it.publicId == item.publicId) &&
                sameBackupSchemeContent(it, allTimes[it.id].orEmpty(), item, times)
        }
        if (identical != null) ids[item.id] = identical.id else {
            val id = if (existing.none { it.id == item.id }) item.id else ++nextId
            var publicId = item.publicId
            var attempt = 0
            while (true) {
                val collision = existing.firstOrNull { it.publicId == publicId } ?: break
                // An interrupted restore may already have committed this deterministic fork.
                if (sameBackupSchemeContent(collision, allTimes[collision.id].orEmpty(), item, times)) {
                    ids[item.id] = collision.id
                    return@forEach
                }
                publicId = UUID.nameUUIDFromBytes("restore:$operationId:${item.publicId}:${attempt++}"
                    .toByteArray(Charsets.UTF_8)).toString()
            }
            val stored = item.copy(id = id, publicId = publicId)
            val storedTimes = times.map { it.copy(schemeId = id) }
            existing += stored
            allTimes[id] = storedTimes
            additions += stored
            addedTimes += storedTimes
            ids[item.id] = id
        }
    }
    return BackupSchemeMerge(additions, addedTimes, ids)
}
