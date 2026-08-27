package com.xiaomanjun.sleepdownschedule.data.repository

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

private data class MultiScheduleSnapshot(
    val courses: List<CourseEntity>,
    val allCourses: List<CourseEntity>,
    val schedules: List<ScheduleProfileEntity>,
    val allConfigs: List<ScheduleConfigEntity>,
    val allPeriods: List<PeriodEntity>
)

class ScheduleRepository(private val database: AppDatabase) {
    private val courseDao = database.courseDao()
    private val configDao = database.configDao()
    private val profileDao = database.scheduleProfileDao()
    private val periodSchemeDao = database.periodSchemeDao()

    suspend fun switchPeriodScheme(scheduleId: Int, schemeId: Long) = database.withTransaction {
        val schemes = periodSchemeDao.getSchemes(scheduleId)
        val target = schemes.firstOrNull { it.id == schemeId } ?: error("作息方案不存在")
        val times = periodSchemeDao.getTimes(target.id)
        require(times.isNotEmpty()) { "作息方案没有节次时间" }
        periodSchemeDao.upsertSchemes(schemes.map { it.copy(isActive = it.id == target.id) })
        configDao.deletePeriods(scheduleId)
        configDao.upsertPeriods(times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) })
    }

    suspend fun renamePeriodScheme(scheduleId: Int, schemeId: Long, name: String) = database.withTransaction {
        val target = periodSchemeDao.getSchemes(scheduleId).firstOrNull { it.id == schemeId }
            ?: error("作息方案不存在")
        periodSchemeDao.upsertScheme(target.copy(name = name.trim().ifBlank { "未命名作息" }))
    }

    suspend fun duplicatePeriodScheme(scheduleId: Int, schemeId: Long, name: String? = null): Long =
        database.withTransaction {
            val source = periodSchemeDao.getSchemes(scheduleId).firstOrNull { it.id == schemeId }
                ?: error("作息方案不存在")
            val newId = periodSchemeDao.upsertScheme(
                source.copy(id = 0, name = name?.trim().orEmpty().ifBlank { "${source.name} 副本" }, isActive = false)
            )
            periodSchemeDao.upsertTimes(periodSchemeDao.getTimes(source.id).map { it.copy(schemeId = newId) })
            newId
        }

    suspend fun deletePeriodScheme(scheduleId: Int, schemeId: Long) = database.withTransaction {
        val schemes = periodSchemeDao.getSchemes(scheduleId)
        require(schemes.size > 1) { "至少需要保留一套作息方案" }
        val removedIndex = schemes.indexOfFirst { it.id == schemeId }
        require(removedIndex >= 0) { "作息方案不存在" }
        val wasActive = schemes[removedIndex].isActive
        periodSchemeDao.deleteTimes(schemeId)
        periodSchemeDao.deleteScheme(schemeId)
        if (wasActive) {
            val remaining = schemes.filterNot { it.id == schemeId }
            val adjacent = remaining[removedIndex.coerceAtMost(remaining.lastIndex)]
            switchPeriodScheme(scheduleId, adjacent.id)
        }
    }

    suspend fun loadPeriodSchemes(scheduleId: Int): SchedulePeriodSchemesDraft = database.withTransaction {
        ensureScheduleData(scheduleId)
        val config = configDao.getConfig(scheduleId) ?: defaultConfig(scheduleId)
        val activePeriods = configDao.getPeriods(scheduleId)
        var schemes = periodSchemeDao.getSchemes(scheduleId)
        if (schemes.isEmpty()) {
            val first = activePeriods.firstOrNull()?.startTime ?: "08:00"
            val schemeId = periodSchemeDao.upsertScheme(
                PeriodSchemeEntity(
                    scheduleId = scheduleId,
                    name = "默认作息",
                    isActive = true,
                    classDurationMinutes = config.classDurationMinutes,
                    breakDurationMinutes = config.breakDurationMinutes,
                    morningStartTime = first,
                    afternoonStartTime = activePeriods.firstOrNull {
                        runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 12..17
                    }?.startTime ?: "14:00",
                    eveningStartTime = activePeriods.firstOrNull {
                        runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) >= 18
                    }?.startTime ?: "19:00"
                )
            )
            periodSchemeDao.upsertTimes(activePeriods.map {
                PeriodSchemeTimeEntity(schemeId, it.periodIndex, it.startTime, it.endTime)
            })
            schemes = periodSchemeDao.getSchemes(scheduleId)
        }
        val drafts = schemes.map { scheme ->
            PeriodSchemeDraft(
                scheme = scheme,
                times = periodSchemeDao.getTimes(scheme.id),
                specialBreaks = decodeSpecialBreaks(scheme.specialBreaksJson),
                overriddenPeriods = decodeOverrides(scheme.overridesJson)
            )
        }
        SchedulePeriodSchemesDraft(drafts, schemes.firstOrNull { it.isActive }?.id ?: schemes.first().id)
    }

    suspend fun saveScheduleDetail(
        config: ScheduleConfigEntity,
        draft: SchedulePeriodSchemesDraft
    ) = database.withTransaction {
        require(draft.schemes.isNotEmpty()) { "至少需要保留一套作息方案" }
        val scheduleId = config.id
        val expectedCount = config.totalPeriodCount()
        require(expectedCount > 0) { "至少需要保留一个节次" }

        val storedConfig = configDao.getConfig(scheduleId)
        val originalPeriods = configDao.getPeriods(scheduleId)
        var courses = courseDao.getCourses(scheduleId)

        val existing = periodSchemeDao.getSchemes(scheduleId)
        val existingDrafts = existing.associate { scheme ->
            scheme.id to PeriodSchemeDraft(
                scheme = scheme,
                times = periodSchemeDao.getTimes(scheme.id),
                specialBreaks = decodeSpecialBreaks(scheme.specialBreaksJson),
                overriddenPeriods = decodeOverrides(scheme.overridesJson)
            )
        }
        val incomingIds = draft.schemes.map { it.scheme.id }.filter { it > 0 }.toSet()
        existing.filter { it.id !in incomingIds }.forEach {
            periodSchemeDao.deleteTimes(it.id)
            periodSchemeDao.deleteScheme(it.id)
        }

        val idMap = mutableMapOf<Long, Long>()
        val saved = draft.schemes.map { item ->
            val sourceId = item.scheme.id
            val entity = item.scheme.copy(
                id = if (sourceId > 0) sourceId else 0,
                scheduleId = scheduleId,
                isActive = sourceId == draft.activeSchemeId,
                specialBreaksJson = encodeSpecialBreaks(item.specialBreaks),
                overridesJson = encodeOverrides(item.overriddenPeriods)
            )
            val storedId = periodSchemeDao.upsertScheme(entity).let { if (entity.id > 0) entity.id else it }
            idMap[sourceId] = storedId
            val incoming = item.copy(scheme = entity.copy(id = storedId))
            val resolved = resolveSchemeTimesForSave(
                config = config,
                draft = incoming,
                storedConfig = storedConfig,
                storedDraft = existingDrafts[sourceId]
            )
            require(resolved.size == expectedCount) { "${entity.name} 的节次数与课表结构不一致" }
            validateResolvedPeriodTimes(resolved)?.let { throw IllegalArgumentException("${entity.name}：$it") }
            periodSchemeDao.deleteTimes(storedId)
            periodSchemeDao.upsertTimes(resolved.map { it.copy(schemeId = storedId) })
            entity.copy(id = storedId) to resolved
        }
        val activeId = idMap[draft.activeSchemeId] ?: draft.activeSchemeId
        periodSchemeDao.upsertSchemes(saved.map { (scheme, _) -> scheme.copy(isActive = scheme.id == activeId) })
        val activeTimes = saved.firstOrNull { it.first.id == activeId }?.second ?: saved.first().second
        if (draft.topologyOperations.isNotEmpty()) {
            courses = courses.map { course ->
                course.copy(periods = remapCoursePeriodsByClockTime(course.periods, originalPeriods, activeTimes))
            }
        }
        configDao.upsertConfig(normalizeConfigForSchedule(config, scheduleId))
        configDao.deletePeriods(scheduleId)
        configDao.upsertPeriods(activeTimes.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) })
        if (courses.isNotEmpty()) courseDao.insertCourses(courses)
    }

    private val multiScheduleState = combine(
        courseDao.observeAllCourses(),
        profileDao.observeProfiles(),
        configDao.observeAllConfigs(),
        configDao.observeAllPeriods()
    ) { allCourses, schedules, allConfigs, allPeriods ->
        val profiles = schedules
        val activeId = profiles.firstOrNull { it.isActive }?.id ?: profiles.firstOrNull()?.id
        MultiScheduleSnapshot(
            courses = if (activeId == null) emptyList() else allCourses.filter { it.scheduleId == activeId },
            allCourses = allCourses,
            schedules = profiles,
            allConfigs = allConfigs,
            allPeriods = allPeriods
        )
    }

    val allSchedulesState = multiScheduleState.map { snapshot ->
        val activeId = snapshot.schedules.firstOrNull { it.isActive }?.id
            ?: snapshot.schedules.firstOrNull()?.id
        val storedConfig = activeId?.let { id -> snapshot.allConfigs.firstOrNull { it.id == id } }
        val config = storedConfig ?: defaultConfig(activeId ?: 1)
        val storedPeriods = activeId?.let { id -> snapshot.allPeriods.filter { it.scheduleId == id } }.orEmpty()
        val periods = storedPeriods.ifEmpty { defaultPeriods(activeId ?: 1) }
        AppState(
            courses = snapshot.courses,
            allCourses = snapshot.allCourses,
            schedules = snapshot.schedules,
            allConfigs = snapshot.allConfigs,
            allPeriods = snapshot.allPeriods,
            config = config,
            periods = periods,
            loaded = activeId != null && storedConfig != null && storedPeriods.isNotEmpty()
        )
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = profileDao.observeActiveProfileId()
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { activeId ->
            combine(
                courseDao.observeCourses(activeId).distinctUntilChanged(),
                configDao.observeConfig(activeId).distinctUntilChanged(),
                configDao.observePeriods(activeId).distinctUntilChanged()
            ) { courses, config, periods ->
                val storedConfig = config?.copy(id = activeId)
                AppState(
                    courses = courses,
                    schedules = emptyList(),
                    config = storedConfig ?: defaultConfig(activeId),
                    periods = periods.ifEmpty { defaultPeriods(activeId) },
                    loaded = storedConfig != null && periods.isNotEmpty()
                )
            }
        }
        .distinctUntilChanged()

    suspend fun ensureDefaults() {
        database.withTransaction {
            if (profileDao.getProfiles().isEmpty()) {
                profileDao.upsertProfile(ScheduleProfileEntity(id = 1, name = "\u9ED8\u8BA4\u8BFE\u8868", isActive = true))
            }
            if (profileDao.getActiveProfile() == null) {
                profileDao.getProfiles().firstOrNull()?.let { profileDao.activateProfile(it.id) }
            }
            profileDao.getProfiles().forEach { profile ->
                ensureScheduleData(profile.id)
            }
        }
    }

    suspend fun addCourse(course: CourseEntity) {
        val scheduleId = activeScheduleId()
        courseDao.insertCourse(normalizeCoursesForSchedule(listOf(course.copy(id = 0)), scheduleId).single())
    }

    suspend fun addCourses(courses: List<CourseEntity>) {
        if (courses.isEmpty()) return
        database.withTransaction {
            val scheduleId = activeScheduleId()
            courseDao.insertCourses(
                normalizeCoursesForSchedule(courses.map { it.copy(id = 0) }, scheduleId)
            )
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun updateCourse(course: CourseEntity) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            requireCurrentCourse(scheduleId, course.id)
            courseDao.updateCourse(normalizeCoursesForSchedule(listOf(course), scheduleId).single())
        }
    }

    suspend fun replaceCourseGroup(originals: List<CourseEntity>, replacements: List<CourseEntity>) {
        require(originals.isNotEmpty()) { "没有可更新的课程" }
        require(replacements.isNotEmpty()) { "请至少选择一个上课星期和周次" }
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val originalIds = originals.map(CourseEntity::id).filter { it > 0 }.distinct().toSet()
            require(originalIds.isNotEmpty()) { "课程记录已失效，请重新打开" }
            val currentIds = courseDao.getCourses(scheduleId).map(CourseEntity::id).toSet()
            require(originalIds.all(currentIds::contains)) { "课程已在其他操作中变更，请重新打开" }
            originalIds.forEach { courseDao.deleteCourse(it) }
            val normalized = normalizeCoursesForSchedule(
                replacements.map { replacement ->
                    replacement.copy(
                        id = replacement.id.takeIf(originalIds::contains) ?: 0,
                        scheduleId = scheduleId
                    )
                },
                scheduleId
            )
            courseDao.insertCourses(normalized)
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val current = requireCurrentCourse(scheduleId, original.id)
            val remainingWeeks = current.weeks.filter { it != targetWeek }
            if (remainingWeeks.isEmpty()) {
                courseDao.deleteCourse(current.id)
            } else {
                courseDao.updateCourse(current.copy(weeks = remainingWeeks))
            }
            val singleWeekCourse = normalizeCoursesForSchedule(listOf(edited.copy(id = 0, weeks = listOf(targetWeek))), scheduleId).single()
            courseDao.getCourses(scheduleId)
                .filter { it.id != current.id && it.weeks.distinct() == listOf(targetWeek) && it.hasSameOccurrenceSlot(singleWeekCourse) }
                .forEach { courseDao.deleteCourse(it.id) }
            courseDao.insertCourse(singleWeekCourse)
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun deleteCourseSingleWeek(course: CourseEntity, targetWeek: Int) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val current = requireCurrentCourse(scheduleId, course.id)
            val remainingWeeks = current.weeks.filter { it != targetWeek }
            if (remainingWeeks.isEmpty()) {
                courseDao.deleteCourse(current.id)
            } else {
                courseDao.updateCourse(current.copy(weeks = remainingWeeks))
            }
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun updateRelatedCourses(original: CourseEntity, edited: CourseEntity) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val current = requireCurrentCourse(scheduleId, original.id)
            val originalName = current.name.trim()
            val related = courseDao.getCourses(scheduleId).filter {
                it.id == current.id || it.name.trim() == originalName
            }.map {
                it.copy(
                    name = edited.name,
                    teacher = edited.teacher,
                    location = edited.location,
                    note = edited.note
                )
            }
            if (related.isNotEmpty()) {
                courseDao.insertCourses(normalizeCoursesForSchedule(related, scheduleId))
            }
        }
    }

    suspend fun deleteCourse(course: CourseEntity) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            requireCurrentCourse(scheduleId, course.id)
            courseDao.deleteCourse(course.id)
        }
    }

    suspend fun deleteCourses(courses: List<CourseEntity>) {
        if (courses.isEmpty()) return
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val currentIds = courseDao.getCourses(scheduleId).map(CourseEntity::id).toSet()
            val ids = courses.map(CourseEntity::id).filter(currentIds::contains).distinct()
            require(ids.isNotEmpty()) { "课程记录已失效，请重新打开" }
            ids.forEach { courseDao.deleteCourse(it) }
        }
    }

    suspend fun deleteCoursesSingleWeek(courses: List<CourseEntity>, targetWeek: Int) {
        if (courses.isEmpty()) return
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val currentById = courseDao.getCourses(scheduleId).associateBy(CourseEntity::id)
            val current = courses.mapNotNull { currentById[it.id] }.distinctBy(CourseEntity::id)
            require(current.isNotEmpty()) { "课程记录已失效，请重新打开" }
            current.forEach { course ->
                val remainingWeeks = course.weeks.filterNot { it == targetWeek }
                if (remainingWeeks.isEmpty()) courseDao.deleteCourse(course.id)
                else courseDao.updateCourse(course.copy(weeks = remainingWeeks))
            }
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun executeAgentPlan(plan: AgentPlan): AgentPlanExecutionResult {
        return runCatching {
            database.withTransaction {
                val scheduleId = activeScheduleId()
                val before = courseDao.getCourses(scheduleId)
                plan.actions.forEach { action ->
                    if (
                        action.type == AgentValidatedActionType.UPDATE ||
                        action.type == AgentValidatedActionType.DELETE
                    ) {
                        val original = action.original
                        val stored = original?.let { candidate ->
                            before.firstOrNull { it.id == candidate.id }
                        }
                        if (stored == null) {
                            throw AgentPlanRejectedException("操作对象不属于当前课表，已拒绝执行")
                        }
                        if (stored != original) {
                            throw AgentPlanRejectedException("课程在确认前已发生变化，请让 AI 基于最新课表重新生成操作")
                        }
                    }
                }
                val preview = previewAgentPlan(
                    before = before,
                    plan = plan,
                    periodDefinitions = configDao.getPeriods(scheduleId)
                )

                plan.actions.forEach { action ->
                    when (action.type) {
                        AgentValidatedActionType.ADD -> action.edited?.let { course ->
                            courseDao.insertCourse(
                                normalizeCoursesForSchedule(
                                    listOf(course.copy(id = 0)),
                                    scheduleId
                                ).single()
                            )
                        }

                        AgentValidatedActionType.UPDATE -> {
                            val original = action.original
                            val edited = action.edited
                            if (original != null && edited != null) {
                                if (action.scope == AgentActionScope.CURRENT_WEEK) {
                                    val remainingWeeks =
                                        original.weeks.filterNot { it == action.targetWeek }
                                    if (remainingWeeks.isEmpty()) {
                                        courseDao.deleteCourse(original.id)
                                    } else {
                                        courseDao.updateCourse(
                                            original.copy(
                                                weeks = remainingWeeks,
                                                scheduleId = scheduleId
                                            )
                                        )
                                    }
                                    courseDao.insertCourse(
                                        normalizeCoursesForSchedule(
                                            listOf(
                                                edited.copy(
                                                    id = 0,
                                                    weeks = listOf(action.targetWeek)
                                                )
                                            ),
                                            scheduleId
                                        ).single()
                                    )
                                } else {
                                    courseDao.updateCourse(
                                        normalizeCoursesForSchedule(
                                            listOf(edited.copy(id = original.id)),
                                            scheduleId
                                        ).single()
                                    )
                                }
                            }
                        }

                        AgentValidatedActionType.DELETE -> action.original?.let { original ->
                            if (action.scope == AgentActionScope.CURRENT_WEEK) {
                                val remainingWeeks =
                                    original.weeks.filterNot { it == action.targetWeek }
                                if (remainingWeeks.isEmpty()) courseDao.deleteCourse(original.id)
                                else courseDao.updateCourse(
                                    original.copy(
                                        weeks = remainingWeeks,
                                        scheduleId = scheduleId
                                    )
                                )
                            } else {
                                courseDao.deleteCourse(original.id)
                            }
                        }

                        AgentValidatedActionType.OPEN_SETTINGS,
                        AgentValidatedActionType.SET_SETTING,
                        AgentValidatedActionType.SET_PERIOD_SETTINGS -> Unit
                    }
                }

                mergeCompatibleCourseFragments(scheduleId)
                val after = courseDao.getCourses(scheduleId)
                if (!verifyAgentPlan(after, plan)) {
                    throw AgentPlanRejectedException("数据库写入后的真实状态与操作计划不一致")
                }
                AgentPlanExecutionResult(
                    success = true,
                    preview = preview,
                    verified = true,
                    message = "操作已完成并验证"
                )
            }
        }.getOrElse { error ->
            AgentPlanExecutionResult(
                success = false,
                preview = null,
                verified = false,
                message = error.message ?: "操作失败，所有修改已回滚"
            )
        }
    }

    suspend fun importDraft(draft: ImportDraft, createNewSchedule: Boolean = false): Int {
        return database.withTransaction {
            val oldActiveId = activeScheduleId()
            val globalConfig = configDao.getConfig(oldActiveId) ?: defaultConfig(oldActiveId)
            val scheduleId = if (createNewSchedule) {
                profileDao.upsertProfile(ScheduleProfileEntity(name = "\u5BFC\u5165\u8BFE\u8868", isActive = false)).toInt().also {
                    profileDao.activateProfile(it)
                }
            } else {
                oldActiveId
            }
            val importedPeriods = normalizePeriodsForSchedule(draft.periods, scheduleId)
            val importedConfig = configWithCountsFromPeriods(draft.config.withGlobalSettingsFrom(globalConfig), importedPeriods)
            configDao.upsertConfig(normalizeConfigForSchedule(importedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(importedPeriods)
            replaceSchemesWithPeriods(scheduleId, importedConfig, importedPeriods, "导入作息")
            courseDao.deleteBySchedule(scheduleId)
            courseDao.insertCourses(normalizeImportedCoursesForSchedule(draft.courses, scheduleId))
            scheduleId
        }
    }

    suspend fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val scheduleId = activeScheduleId()
        database.withTransaction {
            val normalizedPeriods = normalizePeriodsForSchedule(periods, scheduleId)
            val normalizedConfig = configWithCountsFromPeriods(config, normalizedPeriods)
            configDao.upsertConfig(normalizeConfigForSchedule(normalizedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizedPeriods)
            syncActiveSchemeTimes(scheduleId, normalizedConfig, normalizedPeriods)
        }
    }

    suspend fun saveConfigForSchedule(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        database.withTransaction {
            val normalizedPeriods = normalizePeriodsForSchedule(periods, scheduleId)
            val normalizedConfig = configWithCountsFromPeriods(config, normalizedPeriods)
            configDao.upsertConfig(normalizeConfigForSchedule(normalizedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizedPeriods)
            syncActiveSchemeTimes(scheduleId, normalizedConfig, normalizedPeriods)
        }
    }

    suspend fun saveConfigChanges(original: ScheduleConfigEntity, updated: ScheduleConfigEntity) {
        val scheduleId = updated.id
        database.withTransaction {
            val current = configDao.getConfig(scheduleId) ?: return@withTransaction
            val merged = current.withChangesFrom(original, updated)
            configDao.upsertConfig(normalizeConfigForSchedule(merged, scheduleId))
        }
    }

    suspend fun savePersonalizationSnapshot(updated: ScheduleConfigEntity): Boolean {
        val scheduleId = updated.id
        return database.withTransaction {
            val current = configDao.getConfig(scheduleId) ?: return@withTransaction false
            val merged = current.withPersonalizationFrom(updated)
            configDao.upsertConfig(normalizeConfigForSchedule(merged, scheduleId))
            current.wallpaperUri != merged.wallpaperUri
        }
    }

    suspend fun referencedWallpaperUris(): Set<String> = database.withTransaction {
        configDao.getAllConfigs().mapNotNullTo(linkedSetOf()) { it.wallpaperUri }
    }

    suspend fun referencedScheduleIds(): Set<Int> = database.withTransaction {
        profileDao.getProfiles().mapTo(linkedSetOf()) { it.id }
    }

    suspend fun saveGlobalSettings(config: ScheduleConfigEntity) {
        saveGlobalSettingsWith { base -> base.withGlobalSettingsFrom(config) }
    }

    suspend fun saveGlobalSettingsPatches(
        generalSettings: ScheduleConfigEntity?,
        notificationSettings: ScheduleConfigEntity?,
        homeChromeBlurScale: Float? = null
    ) {
        require(
            generalSettings != null ||
                notificationSettings != null ||
                homeChromeBlurScale != null
        ) {
            "至少需要一组 global settings patch"
        }
        saveGlobalSettingsWith { base ->
            var merged = base
            generalSettings?.let { merged = merged.withGeneralSettingsFrom(it) }
            notificationSettings?.let { merged = merged.withNotificationSettingsFrom(it) }
            homeChromeBlurScale?.let { merged = merged.withHomeChromeBlurScale(it) }
            merged
        }
    }

    private suspend fun saveGlobalSettingsWith(
        merge: (ScheduleConfigEntity) -> ScheduleConfigEntity
    ) {
        val activeId = activeScheduleId()
        database.withTransaction {
            val existing = configDao.getAllConfigs()
            val targetIds = (profileDao.getProfiles().map { it.id } + existing.map { it.id } + activeId).distinct()
            targetIds.forEach { id ->
                val base = existing.firstOrNull { it.id == id } ?: defaultConfig(id)
                configDao.upsertConfig(merge(base).copy(id = id))
            }
        }
    }

    suspend fun createSchedule(name: String): Int {
        return database.withTransaction {
            val globalConfig = configDao.getConfig() ?: defaultConfig(activeScheduleId())
            val id = profileDao.upsertProfile(ScheduleProfileEntity(name = name, isActive = false)).toInt()
            configDao.upsertConfig(defaultConfig(id).withGlobalSettingsFrom(globalConfig))
            val periods = defaultPeriods(id)
            configDao.upsertPeriods(periods)
            replaceSchemesWithPeriods(id, defaultConfig(id), periods, "默认作息")
            id
        }
    }

    suspend fun activateSchedule(scheduleId: Int) {
        database.withTransaction {
            val oldActiveId = activeScheduleId()
            val globalConfig = configDao.getConfig(oldActiveId) ?: defaultConfig(oldActiveId)
            ensureScheduleData(scheduleId)
            val targetConfig = configDao.getConfig(scheduleId)
                ?: error("课表配置恢复失败：$scheduleId")
            profileDao.activateProfile(scheduleId)
            configDao.upsertConfig(targetConfig.withGlobalSettingsFrom(globalConfig).copy(id = scheduleId))
        }
    }

    suspend fun renameSchedule(scheduleId: Int, name: String) {
        profileDao.renameProfile(scheduleId, name.ifBlank { "\u672A\u547D\u540D\u8BFE\u8868" })
    }

    suspend fun deleteSchedule(scheduleId: Int) {
        database.withTransaction {
            val profiles = profileDao.getProfiles()
            if (profiles.size <= 1) return@withTransaction
            profileDao.deleteProfile(scheduleId)
            courseDao.deleteBySchedule(scheduleId)
            periodSchemeDao.deleteTimesForSchedule(scheduleId)
            periodSchemeDao.deleteSchemesForSchedule(scheduleId)
            configDao.deletePeriods(scheduleId)
            configDao.deleteConfig(scheduleId)
            val remaining = profiles.filterNot { it.id == scheduleId }
            if (profiles.any { it.id == scheduleId && it.isActive }) {
                remaining.firstOrNull()?.let { profileDao.activateProfile(it.id) }
            }
        }
    }

    suspend fun snapshot(): AppState = database.withTransaction {
        val activeId = activeScheduleId()
        AppState(
            courses = courseDao.getCourses(activeId),
            allCourses = courseDao.getAllCourses(),
            schedules = profileDao.getProfiles().ifEmpty {
                listOf(
                    ScheduleProfileEntity(
                        id = activeId,
                        name = "\u9ED8\u8BA4\u8BFE\u8868",
                        isActive = true
                    )
                )
            },
            allConfigs = emptyList(),
            allPeriods = emptyList(),
            config = configDao.getConfig(activeId) ?: defaultConfig(activeId),
            periods = configDao.getPeriods(activeId).ifEmpty { defaultPeriods(activeId) },
            loaded = true
        )
    }

    /**
     * Coherent current-schedule snapshot for notifications, widgets and previews.
     * These callers never need every schedule's courses, so avoid a full-table read
     * while keeping all related rows pinned to one active schedule transaction.
     */
    suspend fun activeSnapshot(): AppState = database.withTransaction {
        val activeId = activeScheduleId()
        AppState(
            courses = courseDao.getCourses(activeId),
            config = configDao.getConfig(activeId) ?: defaultConfig(activeId),
            periods = configDao.getPeriods(activeId).ifEmpty { defaultPeriods(activeId) },
            loaded = true
        )
    }

    private suspend fun activeScheduleId(): Int {
        return profileDao.getActiveProfile()?.id ?: 1
    }

    private suspend fun requireCurrentCourse(scheduleId: Int, courseId: Long): CourseEntity {
        return courseDao.getCourses(scheduleId).firstOrNull { it.id == courseId }
            ?: throw IllegalStateException("课表已切换或课程已被删除，请返回当前课表后重试")
    }

    /**
     * Reconciles the persisted config, materialized periods and period schemes for
     * one schedule without replacing real user data with defaults. Older builds
     * could leave a non-active schedule without its config or materialized periods
     * while the scheme tables still retained the original timetable.
     */
    private suspend fun ensureScheduleData(scheduleId: Int) {
        var periods = configDao.getPeriods(scheduleId)
        val schemes = periodSchemeDao.getSchemes(scheduleId)
        val activeScheme = schemes.firstOrNull { it.isActive } ?: schemes.firstOrNull()
        var activeTimes = activeScheme?.let { periodSchemeDao.getTimes(it.id) }.orEmpty()

        if (periods.isNotEmpty() && activeTimes.isNotEmpty()) {
            val schemePeriods = activeTimes.map {
                PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId)
            }
            if (!samePeriodTimeline(periods, schemePeriods)) {
                val defaults = defaultPeriods(scheduleId)
                val materializedIsDefault = samePeriodTimeline(periods, defaults)
                val schemeIsDefault = samePeriodTimeline(schemePeriods, defaults)
                if (materializedIsDefault && !schemeIsDefault) {
                    // A legacy/fallback write replaced only the materialized layer.
                    // Recover the remaining customized scheme instead of destroying it.
                    configDao.deletePeriods(scheduleId)
                    configDao.upsertPeriods(schemePeriods)
                    periods = schemePeriods
                }
            }
        }

        if (periods.isEmpty() && activeTimes.isNotEmpty()) {
            periods = activeTimes.map {
                PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId)
            }
            configDao.upsertPeriods(periods)
        }

        if (periods.isEmpty()) {
            periods = defaultPeriods(scheduleId)
            configDao.upsertPeriods(periods)
        }

        val storedConfig = configDao.getConfig(scheduleId)
        val repairedConfig = normalizeConfigForSchedule(
            configWithCountsFromPeriods(
                storedConfig ?: defaultConfig(scheduleId),
                periods
            ),
            scheduleId
        )
        if (storedConfig != repairedConfig) {
            configDao.upsertConfig(repairedConfig.copy(id = scheduleId))
        }

        if (schemes.isEmpty()) {
            replaceSchemesWithPeriods(scheduleId, repairedConfig, periods, "默认作息")
        } else if (activeScheme != null) {
            // Exactly one active scheme is part of the database invariant. Normalize
            // old/corrupt rows here so LIMIT 1 can never select a stale scheme.
            if (schemes.count { it.isActive } != 1 || !activeScheme.isActive) {
                periodSchemeDao.upsertSchemes(schemes.map { it.copy(isActive = it.id == activeScheme.id) })
            }

            val materializedTimes = periods.map {
                PeriodSchemeTimeEntity(activeScheme.id, it.periodIndex, it.startTime, it.endTime)
            }
            if (activeTimes != materializedTimes) {
                // The materialized table is what the home screen, notifications and
                // widgets were actually using before the multi-scheme upgrade. Keep
                // that visible user state authoritative and repair the active scheme.
                periodSchemeDao.deleteTimes(activeScheme.id)
                periodSchemeDao.upsertTimes(materializedTimes)
                activeTimes = materializedTimes
            }

            // A partially written inactive scheme must not later activate as an empty
            // timetable. Preserve its metadata but seed its missing timeline from the
            // currently materialized schedule instead of generating defaults.
            schemes.filter { it.id != activeScheme.id }.forEach { scheme ->
                if (periodSchemeDao.getTimes(scheme.id).isEmpty()) {
                    periodSchemeDao.upsertTimes(activeTimes.map { it.copy(schemeId = scheme.id) })
                }
            }
        }
    }

    private fun samePeriodTimeline(left: List<PeriodEntity>, right: List<PeriodEntity>): Boolean {
        if (left.size != right.size) return false
        return left.sortedBy { it.periodIndex }.zip(right.sortedBy { it.periodIndex }).all { (a, b) ->
            a.periodIndex == b.periodIndex && a.startTime == b.startTime && a.endTime == b.endTime
        }
    }

    private fun normalizeConfigForSchedule(config: ScheduleConfigEntity, scheduleId: Int): ScheduleConfigEntity {
        return config.copy(
            id = scheduleId,
            weekCardHeightDp = config.weekCardHeightDp?.coerceIn(28f, 120f),
            weekCardHeightScale = config.weekCardHeightScale
                .takeIf(Float::isFinite)
                ?.coerceIn(0.72f, 1.45f)
                ?: 1f,
            weekCardCornerProgress = config.weekCardCornerProgress
                .takeIf(Float::isFinite)
                ?.coerceIn(0f, 1f)
                ?: 0.5f
        ).withDerivedScheduleTermState()
    }

    private fun configWithCountsFromPeriods(config: ScheduleConfigEntity, periods: List<PeriodEntity>): ScheduleConfigEntity {
        if (config.totalPeriodCount() == periods.size && periods.isNotEmpty()) return config
        val inferred = inferPeriodCounts(periods)
        return config.copy(
            morningPeriodCount = inferred.morning,
            noonPeriodCount = inferred.noon,
            afternoonPeriodCount = inferred.afternoon,
            eveningPeriodCount = inferred.evening
        )
    }

    private suspend fun replaceSchemesWithPeriods(
        scheduleId: Int,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        name: String
    ) {
        periodSchemeDao.deleteTimesForSchedule(scheduleId)
        periodSchemeDao.deleteSchemesForSchedule(scheduleId)
        val schemeId = periodSchemeDao.upsertScheme(
            PeriodSchemeEntity(
                scheduleId = scheduleId,
                name = name,
                isActive = true,
                classDurationMinutes = config.classDurationMinutes,
                breakDurationMinutes = config.breakDurationMinutes,
                morningStartTime = periods.firstOrNull()?.startTime ?: "08:00",
                noonStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 12..13 }?.startTime ?: "12:00",
                afternoonStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 14..17 }?.startTime ?: "14:00",
                eveningStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) >= 18 }?.startTime ?: "19:00"
            )
        )
        periodSchemeDao.upsertTimes(periods.map { PeriodSchemeTimeEntity(schemeId, it.periodIndex, it.startTime, it.endTime) })
    }

    private suspend fun syncActiveSchemeTimes(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val active = periodSchemeDao.getActiveScheme(scheduleId)
        if (active == null) {
            replaceSchemesWithPeriods(scheduleId, config, periods, "默认作息")
            return
        }
        periodSchemeDao.deleteTimes(active.id)
        periodSchemeDao.upsertTimes(periods.map { PeriodSchemeTimeEntity(active.id, it.periodIndex, it.startTime, it.endTime) })
    }

    private fun normalizePeriodsForSchedule(periods: List<PeriodEntity>, scheduleId: Int): List<PeriodEntity> {
        return periods
            .filter { it.periodIndex > 0 }
            .distinctBy { it.periodIndex }
            .sortedBy { it.periodIndex }
            .map { it.copy(scheduleId = scheduleId) }
    }

    private fun normalizeCoursesForSchedule(courses: List<CourseEntity>, scheduleId: Int): List<CourseEntity> {
        return courses.map {
            val customRange = it.customTimeRangeOrNull()
            it.copy(
                weekday = it.weekday.coerceIn(1, 7),
                periods = it.periods.filter { period -> period > 0 }.distinct().sorted().ifEmpty { listOf(1) },
                weeks = it.weeks.filter { week -> week > 0 }.distinct().sorted().ifEmpty { listOf(1) },
                customStartTime = customRange?.first?.toString(),
                customEndTime = customRange?.second?.toString(),
                scheduleId = scheduleId
            )
        }
    }

    private fun remapCoursePeriodsByClockTime(
        sourceIndices: List<Int>,
        oldTimes: List<PeriodEntity>,
        newTimes: List<PeriodSchemeTimeEntity>
    ): List<Int> {
        if (newTimes.isEmpty()) return sourceIndices
        val oldByIndex = oldTimes.associateBy { it.periodIndex }
        val parsedNew = newTimes.mapNotNull { item ->
            val start = runCatching { java.time.LocalTime.parse(item.startTime) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { java.time.LocalTime.parse(item.endTime) }.getOrNull() ?: return@mapNotNull null
            Triple(item.periodIndex, start, end)
        }
        if (parsedNew.isEmpty()) return sourceIndices.map { it.coerceIn(1, newTimes.size) }.distinct().sorted()
        val mapped = sourceIndices.flatMap { sourceIndex ->
            val old = oldByIndex[sourceIndex]
            val oldStart = old?.startTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            val oldEnd = old?.endTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            if (oldStart == null || oldEnd == null) {
                listOf(parsedNew.minBy { kotlin.math.abs(it.first - sourceIndex) }.first)
            } else {
                val overlaps = parsedNew.filter { (_, start, end) -> start < oldEnd && end > oldStart }
                if (overlaps.isNotEmpty()) overlaps.map { it.first } else {
                    val oldMinute = oldStart.hour * 60 + oldStart.minute
                    listOf(parsedNew.minBy { (_, start, _) ->
                        kotlin.math.abs(start.hour * 60 + start.minute - oldMinute)
                    }.first)
                }
            }
        }
        return mapped.distinct().sorted().ifEmpty { listOf(parsedNew.first().first) }
    }

    private fun normalizeImportedCoursesForSchedule(courses: List<CourseEntity>, scheduleId: Int): List<CourseEntity> {
        return normalizeCoursesForSchedule(courses, scheduleId).map { it.copy(id = 0) }
    }

    private suspend fun mergeCompatibleCourseFragments(scheduleId: Int) {
        val courses = courseDao.getCourses(scheduleId)
            .map { normalizeCoursesForSchedule(listOf(it), scheduleId).single() }
        courses
            .groupBy { it.mergeKey() }
            .values
            .filter { it.size > 1 }
            .forEach { fragments ->
                val ordered = fragments.sortedBy { it.id }
                val keep = ordered.first()
                val mergedWeeks = ordered
                    .flatMap { it.weeks }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                if (mergedWeeks.isNotEmpty() && keep.weeks != mergedWeeks) {
                    courseDao.updateCourse(keep.copy(weeks = mergedWeeks, scheduleId = scheduleId))
                }
                ordered.drop(1).forEach { courseDao.deleteCourse(it.id) }
            }
    }
}

private fun CourseEntity.hasSameOccurrenceSlot(other: CourseEntity): Boolean {
    return weekday == other.weekday &&
        periods.distinct().sorted() == other.periods.distinct().sorted() &&
        name.trim() == other.name.trim() &&
        teacher.orEmpty().trim() == other.teacher.orEmpty().trim() &&
        location.orEmpty().trim() == other.location.orEmpty().trim() &&
        note.orEmpty().trim() == other.note.orEmpty().trim() &&
        customStartTime == other.customStartTime &&
        customEndTime == other.customEndTime &&
        customColorArgb == other.customColorArgb &&
        weekParity == other.weekParity &&
        scheduleId == other.scheduleId
}

private data class CourseMergeKey(
    val scheduleId: Int,
    val name: String,
    val teacher: String,
    val location: String,
    val note: String,
    val weekday: Int,
    val periods: List<Int>,
    val customStartTime: String?,
    val customEndTime: String?,
    val customColorArgb: Long?,
    val weekParity: WeekParity
)

private fun CourseEntity.mergeKey(): CourseMergeKey {
    return CourseMergeKey(
        scheduleId = scheduleId,
        name = name.trim(),
        teacher = teacher.orEmpty().trim(),
        location = location.orEmpty().trim(),
        note = note.orEmpty().trim(),
        weekday = weekday,
        periods = periods.distinct().sorted(),
        customStartTime = customStartTime,
        customEndTime = customEndTime,
        customColorArgb = customColorArgb,
        weekParity = weekParity
    )
}

private fun ScheduleConfigEntity.withGlobalSettingsFrom(global: ScheduleConfigEntity): ScheduleConfigEntity {
    return copy(
        followSystemDarkMode = global.followSystemDarkMode,
        darkMode = global.darkMode,
        dockAlignment = global.dockAlignment,
        defaultWallpaperStyle = global.defaultWallpaperStyle,
        defaultHomeMode = global.defaultHomeMode,
        liveUpdateActionsEnabled = global.liveUpdateActionsEnabled,
        homeChromeBlurScale = global.homeChromeBlurScale,
        homeChromeSamplingScale = global.homeChromeSamplingScale,
        hideFromRecents = global.hideFromRecents,
        autoCheckUpdates = global.autoCheckUpdates,
        notificationLeadMinutes = global.notificationLeadMinutes,
        notificationsEnabled = global.notificationsEnabled,
        notificationMode = global.notificationMode,
        liveUpdateChipTextMode = global.liveUpdateChipTextMode
    )
}
