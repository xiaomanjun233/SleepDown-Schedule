package com.xiaomanjun.sleepdownschedule.data.repository

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.previewPeriodCourseMapping
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasSameContent
import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseAlignmentImpact
import com.xiaomanjun.sleepdownschedule.domain.schedule.captureOriginalPeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.encodeCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.originalArrangement
import com.xiaomanjun.sleepdownschedule.domain.schedule.arrangementForWrite
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangements
import com.xiaomanjun.sleepdownschedule.domain.schedule.previewCourseAlignment
import com.xiaomanjun.sleepdownschedule.domain.schedule.withEffectiveCourseArrangements
import com.xiaomanjun.sleepdownschedule.domain.schedule.hasNetPeriodTopologyChange
import com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig
import com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode
import com.xiaomanjun.sleepdownschedule.model.withImportedCourseCardDefaults
import com.xiaomanjun.sleepdownschedule.domain.course.conflictsWith

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

data class PeriodSchemeSwitchPreview(
    val config: ScheduleConfigEntity,
    val courses: List<CourseEntity>,
    val periods: List<PeriodEntity>,
    val target: PeriodSchemeDraft,
    val mode: PeriodAlignmentMode,
    val impact: CourseAlignmentImpact
)

class ScheduleRepository(private val database: AppDatabase) {
    private val courseDao = database.courseDao()
    private val configDao = database.configDao()
    private val profileDao = database.scheduleProfileDao()
    private val periodSchemeDao = database.periodSchemeDao()

    suspend fun previewPeriodSchemeSwitch(scheduleId: Int, schemeId: Long,
        mode: PeriodAlignmentMode = PeriodAlignmentMode.INDEX): PeriodSchemeSwitchPreview = database.withTransaction {
        val config = configDao.getConfig(scheduleId) ?: error("课表不存在")
        val target = periodSchemeDao.getScheme(schemeId) ?: error("作息方案不存在")
        val draft = readSchemeDraft(target)
        require(draft.times.isNotEmpty()) { "作息方案没有节次时间" }
        validateResolvedPeriodTimes(draft.times)?.let { throw IllegalArgumentException(it) }
        val courses = courseDao.getCourses(scheduleId)
        val periods = configDao.getPeriods(scheduleId)
        val nextConfig = schemeConfig(config, target).copy(periodAlignmentMode = mode)
        PeriodSchemeSwitchPreview(config, courses, periods, draft, mode,
            previewCourseAlignment(courses, config, periods, nextConfig,
                draft.times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) }))
    }

    suspend fun switchPeriodScheme(scheduleId: Int, schemeId: Long,
        mode: PeriodAlignmentMode = PeriodAlignmentMode.INDEX,
        expected: PeriodSchemeSwitchPreview? = null) = database.withTransaction {
        val preview = previewPeriodSchemeSwitch(scheduleId, schemeId, mode)
        require(expected == null || (preview.config == expected.config && preview.courses == expected.courses &&
            preview.periods == expected.periods && preview.target == expected.target && mode == expected.mode)) {
            "课程或作息已有变化，请重新确认切换"
        }
        require(preview.impact.conflicts.isEmpty()) { "新作息下有课程时间冲突，请先调整课程" }
        if (preview.config.activePeriodSchemeId == schemeId && preview.config.periodAlignmentMode == mode) return@withTransaction
        bindPublicScheme(scheduleId, preview.target.scheme, preview.target.times,
            preview.config.copy(periodAlignmentMode = mode))
    }

    private suspend fun duplicatePublicSchemeRecord(schemeId: Long, name: String? = null): Long =
        database.withTransaction {
            val source = periodSchemeDao.getScheme(schemeId) ?: error("作息方案不存在")
            val newId = periodSchemeDao.upsertScheme(source.copy(id = 0, scheduleId = 0, isActive = false,
                publicId = java.util.UUID.randomUUID().toString(),
                name = name?.trim().orEmpty().ifBlank { "${source.name.take(57)} 副本" }))
            periodSchemeDao.upsertTimes(periodSchemeDao.getTimes(source.id).map { it.copy(schemeId = newId) })
            newId
        }

    suspend fun loadPeriodSchemes(scheduleId: Int): SchedulePeriodSchemesDraft = database.withTransaction {
        ensureScheduleData(scheduleId)
        val scheme = periodSchemeDao.getActiveScheme(scheduleId) ?: error("课表未选择作息")
        val draft = readSchemeDraft(scheme)
        SchedulePeriodSchemesDraft(listOf(draft), scheme.id, originalSchemes = listOf(draft),
            expectedUsages = schemeUsages(scheme.id), originalActiveSchemeId = scheme.id)
    }

    private suspend fun readSchemeDraft(scheme: PeriodSchemeEntity) = PeriodSchemeDraft(
        scheme, periodSchemeDao.getTimes(scheme.id), decodeSpecialBreaks(scheme.specialBreaksJson),
        decodeOverrides(scheme.overridesJson))

    private suspend fun schemeUsages(schemeId: Long): List<com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeUsageSnapshot> =
        configDao.getAllConfigs().filter { it.activePeriodSchemeId == schemeId }.sortedBy { it.id }.map {
            com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeUsageSnapshot(it,
                courseDao.getCourses(it.id).sortedBy { course -> course.id }, configDao.getPeriods(it.id))
        }

    private suspend fun publicSnapshot(scheme: PeriodSchemeEntity): com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme {
        val config = com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig(defaultConfig(), scheme)
        return com.xiaomanjun.sleepdownschedule.domain.schedule.savePeriodSchemeSnapshot(
            scheme.publicId, scheme.name, config, readSchemeDraft(scheme)).copy(
            sources = scheme.sourceScheduleName.takeIf { it.isNotBlank() }?.let {
                listOf(com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeSource(it, scheme.name))
            }.orEmpty(), createdInLibrary = scheme.sourceScheduleName.isBlank(),
            usages = schemeUsages(scheme.id), storedDraft = readSchemeDraft(scheme))
    }

    suspend fun publicPeriodSchemes(): List<com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme> =
        database.withTransaction { periodSchemeDao.getAllSchemes().map { publicSnapshot(it) } }

    suspend fun migrateLegacyPeriodSchemeLibrary(context: android.content.Context) = database.withTransaction {
        val source = "legacy-preferences-v1"
        if (periodSchemeDao.hasMigration(source)) return@withTransaction
        val legacy = PeriodSchemeLibraryStore.load(context)
        for (snapshot in legacy) {
            val existing = publicPeriodSchemes()
            if (existing.any { it.hasSameContent(snapshot) }) continue
            val publicId = snapshot.id.takeIf { id -> existing.none { it.id == id } }
                ?: java.util.UUID.randomUUID().toString()
            val session = com.xiaomanjun.sleepdownschedule.domain.schedule.savedPeriodSchemeSession(snapshot, defaultConfig())
            savePublicScheme(session.config, session.active.copy(scheme = session.active.scheme.copy(
                id = 0, publicId = publicId,
                sourceScheduleName = snapshot.sources.joinToString("；") { "${it.scheduleName}（${it.schemeName}）" })),
                null, emptyList(), null)
        }
        // Same commit as imported records: a later deletion can never rediscover stale preferences.
        periodSchemeDao.insertMigration(com.xiaomanjun.sleepdownschedule.model.PeriodSchemeLibraryMigrationEntity(source))
    }

    internal suspend fun savePublicPeriodScheme(
        original: com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme?,
        edited: com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimelineSession,
        publicId: String
    ): com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme = database.withTransaction {
        val existing = original?.let { periodSchemeDao.getSchemeByPublicId(it.id) ?: error("作息已被删除，请重新打开") }
        if (existing != null) {
            val expected = requireNotNull(original)
            val current = publicSnapshot(existing)
            require(current.name == expected.name && current.hasSameContent(expected)) { "作息已有其他修改，请重新打开" }
            require(expected.usages == current.usages) { "引用此作息的课表或课程已有变化，请重新打开后确认" }
        }
        val item = edited.active.copy(scheme = edited.active.scheme.copy(
            id = existing?.id ?: 0, publicId = existing?.publicId ?: publicId,
            sourceScheduleName = existing?.sourceScheduleName.orEmpty()))
        val saved = savePublicScheme(edited.config, item, existing?.let { readSchemeDraft(it) },
            edited.draft.topologyOperations, original?.usages)
        publicSnapshot(saved)
    }

    suspend fun duplicatePublicPeriodScheme(publicId: String): com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme =
        database.withTransaction {
            val source = periodSchemeDao.getSchemeByPublicId(publicId) ?: error("作息方案不存在")
            val id = duplicatePublicSchemeRecord(source.id)
            publicSnapshot(requireNotNull(periodSchemeDao.getScheme(id)))
        }

    suspend fun deletePublicPeriodScheme(publicId: String) = database.withTransaction {
        val target = periodSchemeDao.getSchemeByPublicId(publicId) ?: error("作息方案不存在")
        val references = configDao.getAllConfigs().filter { it.activePeriodSchemeId == target.id }
        require(references.isEmpty()) { "仍有 ${references.size} 张课表使用此作息，请先为这些课表选择其他作息" }
        periodSchemeDao.deleteTimes(target.id)
        periodSchemeDao.deleteScheme(target.id)
    }

    /** One transaction owns the public record, every referring table and its compatibility projection. */
    suspend fun saveScheduleDetail(
        config: ScheduleConfigEntity,
        draft: SchedulePeriodSchemesDraft,
        expectedCourses: List<CourseEntity>? = null,
        expectedPeriods: List<PeriodEntity>? = null
    ) = database.withTransaction {
        ensureScheduleData(config.id)
        val storedConfig = configDao.getConfig(config.id) ?: error("课表不存在")
        require(draft.originalActiveSchemeId == null || storedConfig.activePeriodSchemeId == draft.originalActiveSchemeId) {
            "课表已切换到其他作息，请重新打开设置"
        }
        val currentCourses = courseDao.getCourses(config.id)
        val currentPeriods = configDao.getPeriods(config.id)
        require((expectedCourses == null || currentCourses.sortedBy { it.id } == expectedCourses.map { it.originalArrangement() }.sortedBy { it.id }) &&
            (expectedPeriods == null || currentPeriods == expectedPeriods.sortedBy { it.periodIndex })) {
            "课程或作息在确认期间发生变化，请重新打开设置并确认后再保存"
        }
        val active = draft.schemes.firstOrNull { it.scheme.id == draft.activeSchemeId } ?: error("请选择作息")
        val existing = active.scheme.id.takeIf { it > 0 }?.let { periodSchemeDao.getScheme(it) ?: error("作息已删除") }
        val before = existing?.let { readSchemeDraft(it) }
        val expected = draft.originalSchemes.firstOrNull { it.scheme.id == active.scheme.id }
        require(expected == null || before == expected) { "公共作息已有其他修改，请重新打开" }
        val switching = existing != null && storedConfig.activePeriodSchemeId != existing.id
        val saved = if (switching) {
            require(draft.topologyOperations.isEmpty()) { "切换作息不能同时修改节次，请先保存或放弃节次编辑" }
            // Selecting an existing record cannot overwrite another table's public scheme.
            require(expected != null && active == expected) { "请先切换并保存，再编辑所选公共作息" }
            requireNotNull(existing)
        } else {
            val resolved = resolveSchemeTimesForSave(config, active,
                before?.let { com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig(config, it.scheme) }, before)
            val unchanged = before != null && active.scheme.name == before.scheme.name &&
                com.xiaomanjun.sleepdownschedule.domain.schedule.savePeriodSchemeSnapshot("compare", active.scheme.name,
                    config, active.copy(times = resolved)).hasSameContent(
                    com.xiaomanjun.sleepdownschedule.domain.schedule.savePeriodSchemeSnapshot("compare", before.scheme.name,
                        com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig(config, before.scheme), before)) &&
                !com.xiaomanjun.sleepdownschedule.domain.schedule.hasNetPeriodTopologyChange(currentPeriods.size, draft.topologyOperations)
            if (unchanged) requireNotNull(existing) else {
                if (before != null && before.times.size != resolved.size) require(draft.topologyOperations.isNotEmpty()) {
                    "缺少节次编辑记录，请重新打开作息编辑"
                }
                val mapped = coursesAfterSchemeEdit(currentCourses, storedConfig, config, currentPeriods, resolved, draft.topologyOperations)
                // Detail settings always edit this table only. The copy is itself a public scheme.
                val copy = active.copy(scheme = active.scheme.copy(id = 0, publicId = java.util.UUID.randomUUID().toString(),
                    name = if (before != null && active.scheme.name == before.scheme.name) "${active.scheme.name.take(57)} 副本" else active.scheme.name),
                    times = resolved)
                val created = savePublicScheme(config, copy, null, emptyList(), null)
                if (mapped != currentCourses) courseDao.insertCourses(mapped)
                created
            }
        }
        bindPublicScheme(config.id, saved, periodSchemeDao.getTimes(saved.id), config)
    }

    private suspend fun savePublicScheme(
        config: ScheduleConfigEntity, item: PeriodSchemeDraft, before: PeriodSchemeDraft?,
        operations: List<com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTopologyOperation>,
        expectedUsages: List<com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeUsageSnapshot>?
    ): PeriodSchemeEntity {
        val name = item.scheme.name.trim()
        require(name.isNotBlank() && name.length <= 60) { "作息名称须为 1 至 60 个字符" }
        val count = config.totalPeriodCount()
        require(count in 1..48 && listOf(config.morningPeriodCount, config.noonPeriodCount,
            config.afternoonPeriodCount, config.eveningPeriodCount).all { it >= 0 }) { "节次数无效" }
        val entity = item.scheme.copy(id = before?.scheme?.id ?: 0, scheduleId = 0, isActive = false,
            publicId = before?.scheme?.publicId ?: item.scheme.publicId.ifBlank { java.util.UUID.randomUUID().toString() },
            name = name, morningPeriodCount = config.morningPeriodCount, noonPeriodCount = config.noonPeriodCount,
            afternoonPeriodCount = config.afternoonPeriodCount, eveningPeriodCount = config.eveningPeriodCount,
            specialBreaksJson = encodeSpecialBreaks(item.specialBreaks), overridesJson = encodeOverrides(item.overriddenPeriods))
        val times = if (before == null && item.times.isNotEmpty()) item.times.sortedBy { it.periodIndex }
            else resolveSchemeTimesForSave(config, item,
                before?.let { com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig(config, it.scheme) }, before)
        require(times.size == count) { "节次数与时间线不一致" }
        validateResolvedPeriodTimes(times)?.let { throw IllegalArgumentException(it) }
        val usages = before?.let { schemeUsages(it.scheme.id) }.orEmpty()
        require(expectedUsages == null || expectedUsages == usages) { "引用此作息的课表或课程已有变化，请重新打开后确认" }
        if (before != null && before.times.size != times.size) {
            require(operations.isNotEmpty()) { "缺少节次编辑记录，请重新打开作息编辑" }
        }
        val remapped = usages.map { usage ->
            usage to coursesAfterSchemeEdit(usage.courses, usage.config, schemeConfig(usage.config, entity),
                usage.periods, times, operations)
        }
        val id = periodSchemeDao.upsertScheme(entity).let { if (entity.id > 0) entity.id else it }
        val saved = entity.copy(id = id)
        periodSchemeDao.deleteTimes(id)
        periodSchemeDao.upsertTimes(times.map { it.copy(schemeId = id) })
        remapped.forEach { (usage, courses) ->
            configDao.upsertConfig(com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig(usage.config, saved))
            projectPeriods(usage.config.id, times)
            if (courses != usage.courses) courseDao.insertCourses(courses)
        }
        return saved
    }

    /** Switching may hide lessons; editing lesson structure still needs a lossless identity mapping. */
    private fun coursesAfterSchemeEdit(courses: List<CourseEntity>, beforeConfig: ScheduleConfigEntity,
        afterConfig: ScheduleConfigEntity, oldPeriods: List<PeriodEntity>, times: List<PeriodSchemeTimeEntity>,
        operations: List<PeriodTopologyOperation>): List<CourseEntity> {
        val effectiveBefore = projectCourseArrangements(courses, beforeConfig, oldPeriods)
        val mapped = if (hasNetPeriodTopologyChange(oldPeriods.size, operations)) {
            require(courses.all { course -> course.periods.all { index -> oldPeriods.any { it.periodIndex == index } } }) {
                "课程还有当前作息未显示的原始节次，请切回完整作息后再编辑节次结构"
            }
            if (beforeConfig.periodAlignmentMode == PeriodAlignmentMode.TIME) {
                previewPeriodCourseMapping(effectiveBefore, oldPeriods, times, operations)
                courses
            } else previewPeriodCourseMapping(courses, oldPeriods, times, operations).courses.mapIndexed { i, course ->
                val old = courses[i]
                val renumber = old.periods.distinct().sorted().zip(course.periods.distinct().sorted()).toMap()
                course.copy(originalPeriodTimes = encodeCoursePeriodTimes(parseCoursePeriodTimes(old.originalPeriodTimes)
                    .map { it.copy(index = renumber.getValue(it.index)) }) ?: old.originalPeriodTimes)
            }
        } else courses
        val nextPeriods = times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, beforeConfig.id) }
        validateNewTimingConflicts(effectiveBefore, projectCourseArrangements(mapped, afterConfig, nextPeriods), oldPeriods, times)
        return mapped
    }

    private suspend fun projectPeriods(scheduleId: Int, times: List<PeriodSchemeTimeEntity>) {
        configDao.deletePeriods(scheduleId)
        configDao.upsertPeriods(times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) })
    }

    private fun validateNewTimingConflicts(before: List<CourseEntity>, after: List<CourseEntity>,
        oldPeriods: List<PeriodEntity>, times: List<PeriodSchemeTimeEntity>) {
        val newPeriods = times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime) }
        after.indices.forEach { i -> (i + 1 until after.size).forEach { j ->
            require(after[i].weeks.none { week ->
                after[i].conflictsWith(after[j], week, newPeriods) &&
                    !before[i].conflictsWith(before[j], week, oldPeriods)
            }) { "${after[i].name} 与 ${after[j].name} 在新作息下时间冲突，请先调整课程" }
        } }
    }

    private suspend fun bindPublicScheme(scheduleId: Int, scheme: PeriodSchemeEntity,
        times: List<PeriodSchemeTimeEntity>, requestedConfig: ScheduleConfigEntity? = null) {
        require(times.isNotEmpty()) { "作息方案没有节次时间" }
        validateResolvedPeriodTimes(times)?.let { throw IllegalArgumentException(it) }
        val courses = courseDao.getCourses(scheduleId)
        val storedConfig = configDao.getConfig(scheduleId) ?: error("课表不存在")
        val config = requestedConfig ?: storedConfig
        val next = normalizeConfigForSchedule(schemeConfig(config, scheme), scheduleId)
        val oldPeriods = configDao.getPeriods(scheduleId)
        val nextPeriods = times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) }
        validateNewTimingConflicts(projectCourseArrangements(courses, storedConfig, oldPeriods),
            projectCourseArrangements(courses, next, nextPeriods), oldPeriods, times)
        if (next != storedConfig) configDao.upsertConfig(next)
        if (oldPeriods != nextPeriods) projectPeriods(scheduleId, times)
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
        ).withEffectiveCourseArrangements()
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
                ).withEffectiveCourseArrangements()
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

    /** Validate the chosen empty slot again inside the same transaction that creates the copy. */
    suspend fun copyCourses(courses: List<CourseEntity>) {
        require(courses.isNotEmpty()) { "没有可复制的课程" }
        val expectedScheduleId = courses.map { it.scheduleId }.distinct().single()
        database.withTransaction {
            require(activeScheduleId() == expectedScheduleId) { "课表已切换，请重新选择复制位置" }
            val normalized = normalizeCoursesForSchedule(courses.map { it.copy(id = 0) }, expectedScheduleId)
            val config = configDao.getConfig(expectedScheduleId) ?: error("课表不存在")
            val periods = configDao.getPeriods(expectedScheduleId)
            val conflicts = com.xiaomanjun.sleepdownschedule.domain.course.conflictWeeksForAddedCourses(
                projectCourseArrangements(normalized, config, periods),
                projectCourseArrangements(courseDao.getCourses(expectedScheduleId), config, periods), periods
            )
            require(conflicts.isEmpty()) { "目标位置已有课程，请重新选择复制位置" }
            courseDao.insertCourses(normalized)
            mergeCompatibleCourseFragments(expectedScheduleId)
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
            val normalized = normalizeCoursesForSchedule(
                replacements.map { replacement ->
                    replacement.copy(
                        id = replacement.id.takeIf(originalIds::contains) ?: 0,
                        scheduleId = scheduleId
                    )
                },
                scheduleId
            )
            originalIds.forEach { courseDao.deleteCourse(it) }
            courseDao.insertCourses(normalized)
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val current = requireCurrentCourse(scheduleId, original.id)
            require(targetWeek in current.weeks && parityMatches(current.weekParity, targetWeek)) {
                "所选周次没有这条课程，请重新打开"
            }
            val remainingWeeks = current.weeks.filter { it != targetWeek }
            if (remainingWeeks.isEmpty()) {
                courseDao.deleteCourse(current.id)
            } else {
                courseDao.updateCourse(current.copy(weeks = remainingWeeks))
            }
            val singleWeekCourse = normalizeCoursesForSchedule(
                listOf(edited.copy(id = 0, weeks = listOf(targetWeek), weekParity = WeekParity.ALL)), scheduleId
            ).single()
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
            require(targetWeek in current.weeks && parityMatches(current.weekParity, targetWeek)) {
                "所选周次没有这条课程，请重新打开"
            }
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
            require(current.all { targetWeek in it.weeks && parityMatches(it.weekParity, targetWeek) }) {
                "所选周次没有这条课程，请重新打开"
            }
            current.forEach { course ->
                val remainingWeeks = course.weeks.filterNot { it == targetWeek }
                if (remainingWeeks.isEmpty()) courseDao.deleteCourse(course.id)
                else courseDao.updateCourse(course.copy(weeks = remainingWeeks))
            }
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun commitAgentSettingPlan(
        before: AppState,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        schemes: SchedulePeriodSchemesDraft?,
        name: String?,
        courseActions: List<AgentValidatedAction>
    ): Pair<AgentPlanExecutionResult, AppState> = database.withTransaction {
        val current = snapshot()
        require(current.config == before.config && current.periods == before.periods && current.courses == before.courses) {
            "课表在确认期间发生变化，请重新生成计划"
        }
        // The explicit plan owns course migrations. Temporarily detach its rows inside this same
        // transaction so detail COW cannot remap them a second time or reject a planned deletion.
        // Any later plan failure rolls the complete transaction back, including the public copy.
        if (courseActions.isNotEmpty()) courseDao.deleteBySchedule(config.id)
        schemes?.let { saveScheduleDetail(config, it) }
            ?: saveConfigForSchedule(config.id, config, periods)
        if (courseActions.isNotEmpty()) courseDao.insertCourses(current.courses.map { it.originalArrangement() })
        name?.let { renameSchedule(config.id, it) }
        val result = if (courseActions.isEmpty()) AgentPlanExecutionResult(true, null, true, "设置已保存")
            else executeAgentPlan(AgentPlan(courseActions)).also { check(it.success && it.verified) { it.message } }
        val stored = snapshot()
        val indexes = stored.periods.map { it.periodIndex }.toSet()
        require(stored.courses.all { course ->
            course.originalArrangement().periods.isNotEmpty() && course.periods.all { it in indexes } &&
                course.weeks.all { it in 1..stored.config.totalWeeks }
        }) { "新设置会使课程周次或节次越界，请在同一计划中调整受影响课程" }
        val oldById = before.courses.associateBy { it.id }
        val retained = stored.courses.filter { it.id in oldById }
        validateNewTimingConflicts(retained.map { oldById.getValue(it.id) }, retained, before.periods,
            stored.periods.map { PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime) })
        result to stored
    }

    suspend fun restoreAgentSettingPlan(
        expected: AppState, before: AppState, schemes: SchedulePeriodSchemesDraft, name: String?
    ) = database.withTransaction {
        val current = snapshot()
        require(current.config == expected.config && current.periods == expected.periods && current.courses == expected.courses) {
            "课表已有后续修改，无法覆盖撤销"
        }
        // Undo re-selects the previous public identity; it never rewrites that shared record.
        courseDao.deleteBySchedule(before.config.id)
        saveScheduleDetail(before.config, schemes.copy(topologyOperations = emptyList(),
            originalActiveSchemeId = current.config.activePeriodSchemeId))
        courseDao.insertCourses(before.courses.map { it.originalArrangement() })
        name?.let { renameSchedule(before.config.id, it) }
    }

    suspend fun executeAgentPlan(plan: AgentPlan): AgentPlanExecutionResult {
        return runCatching {
            database.withTransaction {
                val scheduleId = activeScheduleId()
                val before = courseDao.getCourses(scheduleId)
                require(plan.actions.all { it.sourceScheduleId == null || it.sourceScheduleId == scheduleId }) {
                    "当前课表已切换，请基于新课表重新生成后续修改"
                }
                plan.actions.forEach { action ->
                    if (
                        action.type == AgentValidatedActionType.UPDATE ||
                        action.type == AgentValidatedActionType.REPLACE ||
                        action.type == AgentValidatedActionType.DELETE
                    ) {
                        val original = action.original
                        val stored = original?.let { candidate ->
                            before.firstOrNull { it.id == candidate.id }
                        }
                        if (stored == null) {
                            throw AgentPlanRejectedException("操作对象不属于当前课表，已拒绝执行")
                        }
                        if (stored != original?.originalArrangement()) {
                            throw AgentPlanRejectedException("课程在确认前已发生变化，请让 AI 基于最新课表重新生成操作")
                        }
                        if (action.scope != AgentActionScope.ALL_WEEKS) require(action.sourceWeekSet().let { weeks ->
                            weeks.isNotEmpty() && weeks.all { it in stored.weeks && parityMatches(stored.weekParity, it) }
                        }) { "所选周次没有这门课程，请重新读取开课周次" }
                    }
                }
                require(!agentActionsHaveOverlappingCourseScopes(plan.actions)) {
                    "同一课程的同一周有多份修改，请合并后再执行"
                }
                val config = configDao.getConfig(scheduleId) ?: error("课表配置不存在")
                val periodDefinitions = configDao.getPeriods(scheduleId)
                val periodIndexes = periodDefinitions.map { it.periodIndex }.toSet()
                require(plan.actions.mapNotNull { it.scopedEditedCourse() }.all { course ->
                    val previous = before.firstOrNull { it.id == course.id }
                    course.weekday in 1..7 && course.periods.isNotEmpty() &&
                        (course.periods.all { it in periodIndexes } || course.periods == previous?.periods || course.hasCustomTime()) &&
                        course.weeks.isNotEmpty() && course.weeks.all { it in 1..config.totalWeeks } &&
                        course.weeks.any { parityMatches(course.weekParity, it) }
                }) { "课程星期、节次或周次无效，请修正完整计划" }
                val preview = previewAgentPlan(
                    before = projectCourseArrangements(before, config, periodDefinitions),
                    plan = plan,
                    periodDefinitions = periodDefinitions
                )

                plan.actions.filter { it.original != null }.groupBy { it.original!!.id }.forEach { (id, actions) ->
                    val original = before.first { it.id == id }
                    actions.forEach { action ->
                        require(action.sourcePeriodSet().isNotEmpty() && action.sourcePeriodSet().all { it in original.periods }) {
                            "所选节次不属于原课程，请重新生成计划"
                        }
                    }
                    val fragments = agentCourseFragments(original, actions, periodDefinitions)
                    if (fragments.isEmpty()) courseDao.deleteCourse(id)
                    else {
                        // Fragment clocks were prepared against their logical source selection.
                        // Reuse the physical ID only after normalization, so retained lessons are
                        // not mistaken for an explicit edit of the old whole-course arrangement.
                        val normalized = normalizeCoursesForSchedule(fragments.map { it.copy(id = 0) },
                            scheduleId, periodDefinitions)
                        courseDao.updateCourse(normalized.first().copy(id = id))
                        normalized.drop(1).forEach { fragment ->
                            courseDao.insertCourse(fragment)
                        }
                    }
                }
                plan.actions.filter { it.type == AgentValidatedActionType.ADD }.forEach { action ->
                    action.edited?.let { course ->
                        courseDao.insertCourse(normalizeCoursesForSchedule(listOf(course.copy(id = 0)), scheduleId, periodDefinitions).single())
                    }
                }
                mergeCompatibleCourseFragments(scheduleId)
                val after = courseDao.getCourses(scheduleId)
                if (!verifyAgentPlan(after, plan, before, periodDefinitions)) {
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
            val importedConfig = configWithCountsFromPeriods(
                draft.config.withImportedCourseCardDefaults().withGlobalSettingsFrom(globalConfig), importedPeriods)
            configDao.upsertConfig(normalizeConfigForSchedule(importedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(importedPeriods)
            replaceSchemesWithPeriods(scheduleId, importedConfig, importedPeriods, "导入作息", reuseIdentical = true)
            courseDao.deleteBySchedule(scheduleId)
            val importedCourses = normalizeImportedCoursesForSchedule(draft.courses, scheduleId)
            projectCourseArrangements(importedCourses, importedConfig, importedPeriods)
            courseDao.insertCourses(importedCourses)
            scheduleId
        }
    }

    /** Replaces one explicitly bound schedule without changing which schedule is active. */
    suspend fun importDraftForSchedule(scheduleId: Int, draft: ImportDraft) {
        database.withTransaction {
            require(profileDao.getProfiles().any { it.id == scheduleId }) { "自动刷新绑定的课表已不存在" }
            val currentConfig = configDao.getConfig(scheduleId) ?: error("自动刷新绑定的课表配置已不存在")
            val currentPeriods = configDao.getPeriods(scheduleId)
            val refreshed = preserveTimingForAutoRefresh(currentConfig, currentPeriods, draft)
            val activeId = activeScheduleId()
            val globalConfig = configDao.getConfig(activeId)
                ?: currentConfig
            configDao.upsertConfig(normalizeConfigForSchedule(
                refreshed.config.withGlobalSettingsFrom(globalConfig), scheduleId
            ))
            courseDao.deleteBySchedule(scheduleId)
            val importedCourses = normalizeImportedCoursesForSchedule(refreshed.courses, scheduleId)
            projectCourseArrangements(importedCourses, refreshed.config, currentPeriods)
            courseDao.insertCourses(importedCourses)
        }
    }

    suspend fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val scheduleId = activeScheduleId()
        saveConfigForSchedule(scheduleId, config, periods)
    }

    suspend fun saveConfigForSchedule(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        database.withTransaction {
            ensureScheduleData(scheduleId)
            val current = configDao.getConfig(scheduleId) ?: error("课表不存在")
            val normalizedPeriods = normalizePeriodsForSchedule(periods, scheduleId)
            val normalizedConfig = configWithCountsFromPeriods(config, normalizedPeriods)
                .copy(id = scheduleId, activePeriodSchemeId = current.activePeriodSchemeId)
            val times = normalizedPeriods.map { PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime) }
            validateResolvedPeriodTimes(times)?.let { throw IllegalArgumentException(it) }
            val courses = courseDao.getCourses(scheduleId)
            val oldPeriods = configDao.getPeriods(scheduleId)
            require(normalizedPeriods.map { it.periodIndex } == oldPeriods.map { it.periodIndex }) {
                "节次结构修改请使用作息编辑入口"
            }
            coursesAfterSchemeEdit(courses, current, normalizedConfig, oldPeriods, times, emptyList())
            configDao.upsertConfig(normalizeConfigForSchedule(normalizedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizedPeriods)
            syncActiveSchemeTimes(scheduleId, normalizedConfig, normalizedPeriods)
        }
    }

    suspend fun saveScheduleAdjustments(scheduleId: Int, original: String, updated: String) = database.withTransaction {
        val current = requireNotNull(configDao.getConfig(scheduleId)) { "课表已不存在，请重新打开调休安排" }
        fun canonical(value: String) = com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments(
            com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments(value))
        require(canonical(current.scheduleAdjustmentsJson) == canonical(original)) {
            "调休安排已有其他修改，请重新打开后编辑"
        }
        configDao.upsertConfig(current.copy(scheduleAdjustmentsJson = canonical(updated)))
    }

    suspend fun saveConfigChanges(original: ScheduleConfigEntity, updated: ScheduleConfigEntity) {
        val scheduleId = updated.id
        database.withTransaction {
            val current = configDao.getConfig(scheduleId) ?: return@withTransaction
            val merged = current.withChangesFrom(original, updated)
            saveConfigForSchedule(scheduleId, merged, configDao.getPeriods(scheduleId))
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

    suspend fun createSchedule(name: String, publicSchemeId: Long? = null): Int {
        return database.withTransaction {
            val globalConfig = configDao.getConfig() ?: defaultConfig(activeScheduleId())
            val id = profileDao.upsertProfile(ScheduleProfileEntity(name = name, isActive = false)).toInt()
            val config = defaultConfig(id).copy(autoCurrentWeek = true).withGlobalSettingsFrom(globalConfig)
            configDao.upsertConfig(config)
            if (publicSchemeId != null) {
                val selected = periodSchemeDao.getScheme(publicSchemeId) ?: error("作息方案不存在")
                bindPublicScheme(id, selected, periodSchemeDao.getTimes(selected.id))
            } else {
                val periods = defaultPeriods(id)
                configDao.upsertPeriods(periods)
                replaceSchemesWithPeriods(id, config, periods, "默认作息")
            }
            id
        }
    }

    suspend fun activateSchedule(scheduleId: Int) {
        database.withTransaction {
            require(profileDao.getProfiles().any { it.id == scheduleId }) { "目标课表已不存在" }
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
            require(profiles.size > 1) { "至少需要保留一个课表" }
            require(profiles.any { it.id == scheduleId }) { "目标课表已不存在" }
            profileDao.deleteProfile(scheduleId)
            courseDao.deleteBySchedule(scheduleId)
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
            allConfigs = configDao.getAllConfigs(),
            allPeriods = configDao.getAllPeriods(),
            config = configDao.getConfig(activeId) ?: defaultConfig(activeId),
            periods = configDao.getPeriods(activeId).ifEmpty { defaultPeriods(activeId) },
            loaded = true
        ).withEffectiveCourseArrangements()
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
        ).withEffectiveCourseArrangements()
    }

    suspend fun scheduleSnapshot(scheduleId: Int): AppState = database.withTransaction {
        require(profileDao.getProfiles().any { it.id == scheduleId }) { "自动刷新绑定的课表已不存在" }
        AppState(
            courses = courseDao.getCourses(scheduleId),
            config = configDao.getConfig(scheduleId) ?: defaultConfig(scheduleId),
            periods = configDao.getPeriods(scheduleId).ifEmpty { defaultPeriods(scheduleId) },
            loaded = true
        ).withEffectiveCourseArrangements()
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
        val config = configDao.getConfig(scheduleId)
        val scheme = config?.activePeriodSchemeId?.let { periodSchemeDao.getScheme(it) }
        if (scheme != null) {
            val times = periodSchemeDao.getTimes(scheme.id)
            require(times.isNotEmpty()) { "公共作息时间缺失，请从备份恢复" }
            val projected = com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig(requireNotNull(config), scheme)
            if (projected != config) configDao.upsertConfig(projected)
            val periods = times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) }
            if (configDao.getPeriods(scheduleId) != periods) projectPeriods(scheduleId, times)
            return
        }
        // Only an unbound legacy/new table may seed a public record from materialized periods.
        val periods = configDao.getPeriods(scheduleId).ifEmpty { defaultPeriods(scheduleId) }
        val repaired = normalizeConfigForSchedule(configWithCountsFromPeriods(config ?: defaultConfig(scheduleId), periods), scheduleId)
        configDao.upsertConfig(repaired)
        configDao.upsertPeriods(periods)
        replaceSchemesWithPeriods(scheduleId, repaired, periods, "默认作息")
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
            scheduleAdjustmentsJson = com.xiaomanjun.sleepdownschedule.domain.schedule.encodeScheduleAdjustments(
                com.xiaomanjun.sleepdownschedule.domain.schedule.decodeScheduleAdjustments(config.scheduleAdjustmentsJson)
            ),
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
        name: String,
        reuseIdentical: Boolean = false
    ) {
        val entity = PeriodSchemeEntity(
                scheduleId = 0,
                publicId = java.util.UUID.randomUUID().toString(),
                sourceScheduleName = profileDao.getProfiles().firstOrNull { it.id == scheduleId }?.name.orEmpty(),
                name = name,
                isActive = false,
                morningPeriodCount = config.morningPeriodCount,
                noonPeriodCount = config.noonPeriodCount,
                afternoonPeriodCount = config.afternoonPeriodCount,
                eveningPeriodCount = config.eveningPeriodCount,
                classDurationMinutes = config.classDurationMinutes,
                breakDurationMinutes = config.breakDurationMinutes,
                morningStartTime = periods.firstOrNull()?.startTime ?: "08:00",
                noonStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 12..13 }?.startTime ?: "12:00",
                afternoonStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 14..17 }?.startTime ?: "14:00",
                eveningStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) >= 18 }?.startTime ?: "19:00"
            )
        val candidateTimes = periods.map { PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime) }
        validateResolvedPeriodTimes(candidateTimes)?.let { throw IllegalArgumentException(it) }
        val matching = if (reuseIdentical) {
            val snapshot = com.xiaomanjun.sleepdownschedule.domain.schedule.savePeriodSchemeSnapshot(
                entity.publicId, entity.name, config, PeriodSchemeDraft(entity, candidateTimes))
            publicPeriodSchemes().firstOrNull { it.hasSameContent(snapshot) }
        } else null
        val schemeId = matching?.roomId ?: periodSchemeDao.upsertScheme(entity)
        if (matching == null) periodSchemeDao.upsertTimes(periods.map { PeriodSchemeTimeEntity(schemeId, it.periodIndex, it.startTime, it.endTime) })
        configDao.upsertConfig(normalizeConfigForSchedule(config.copy(activePeriodSchemeId = schemeId), scheduleId))
    }

    private suspend fun syncActiveSchemeTimes(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val active = periodSchemeDao.getActiveScheme(scheduleId)
        if (active == null) {
            replaceSchemesWithPeriods(scheduleId, config, periods, "默认作息")
            return
        }
        val currentTimes = periodSchemeDao.getTimes(active.id)
        val candidate = periods.map { PeriodSchemeTimeEntity(active.id, it.periodIndex, it.startTime, it.endTime) }
        val projected = com.xiaomanjun.sleepdownschedule.domain.schedule.schemeConfig(config, active)
        if (currentTimes == candidate && config.morningPeriodCount == projected.morningPeriodCount &&
            config.noonPeriodCount == projected.noonPeriodCount && config.afternoonPeriodCount == projected.afternoonPeriodCount &&
            config.eveningPeriodCount == projected.eveningPeriodCount && config.classDurationMinutes == projected.classDurationMinutes &&
            config.breakDurationMinutes == projected.breakDurationMinutes) {
            configDao.upsertConfig(config.copy(activePeriodSchemeId = active.id))
            return
        }
        replaceSchemesWithPeriods(scheduleId, config, periods, "${active.name.take(57)} 副本")
    }

    private fun normalizePeriodsForSchedule(periods: List<PeriodEntity>, scheduleId: Int): List<PeriodEntity> {
        return periods
            .filter { it.periodIndex > 0 }
            .distinctBy { it.periodIndex }
            .sortedBy { it.periodIndex }
            .map { it.copy(scheduleId = scheduleId) }
    }

    private suspend fun normalizeCoursesForSchedule(courses: List<CourseEntity>, scheduleId: Int,
        sourcePeriods: List<PeriodEntity>? = null): List<CourseEntity> {
        val previous = courseDao.getCourses(scheduleId).associateBy { it.id }
        val definitions = sourcePeriods ?: configDao.getPeriods(scheduleId)
        return courses.map { incoming ->
            val it = incoming.arrangementForWrite()
            val clock = com.xiaomanjun.sleepdownschedule.domain.schedule.normalizeCourseClock(
                it.customStartTime, it.customEndTime, it.customPeriodTimes, it.periods
            )
            captureOriginalPeriodTimes(it.copy(
                weekday = it.weekday.coerceIn(1, 7),
                periods = it.periods.filter { period -> period > 0 }.distinct().sorted().ifEmpty { listOf(1) },
                weeks = it.weeks.filter { week -> week > 0 }.distinct().sorted().ifEmpty { listOf(1) },
                customStartTime = clock.start,
                customEndTime = clock.end,
                customPeriodTimes = clock.periodTimes,
                scheduleId = scheduleId
            ), definitions, previous[it.id])
        }
    }

    private suspend fun normalizeImportedCoursesForSchedule(courses: List<CourseEntity>, scheduleId: Int): List<CourseEntity> {
        return normalizeCoursesForSchedule(courses.map { it.copy(id = 0, arrangementProjection = null) }, scheduleId)
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
        customPeriodTimes == other.customPeriodTimes &&
        originalPeriodTimes == other.originalPeriodTimes &&
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
    val customPeriodTimes: String?,
    val originalPeriodTimes: String?,
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
        customPeriodTimes = customPeriodTimes,
        originalPeriodTimes = originalPeriodTimes,
        customColorArgb = customColorArgb,
        weekParity = weekParity
    )
}

internal fun preserveTimingForAutoRefresh(
    currentConfig: ScheduleConfigEntity,
    currentPeriods: List<PeriodEntity>,
    fetched: ImportDraft
): ImportDraft {
    require(currentPeriods.isNotEmpty()) { "当前课表没有作息节次，请手动导入并核对作息" }
    return fetched.copy(
        courses = fetched.courses.map { captureOriginalPeriodTimes(it.copy(originalPeriodTimes = null), fetched.periods) },
        config = currentConfig.copy(
            totalWeeks = fetched.config.totalWeeks,
            currentWeek = currentConfig.currentWeek.coerceIn(1, fetched.config.totalWeeks),
            termStartDate = fetched.config.termStartDate ?: currentConfig.termStartDate
        ),
        periods = currentPeriods
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
