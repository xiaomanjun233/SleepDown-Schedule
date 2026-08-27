package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.domain.schedule.switchCourseCardGlassMode as applyCourseCardGlassMode
import com.xiaomanjun.sleepdownschedule.domain.schedule.withChangesFrom as applyChangedScheduleConfig
import com.xiaomanjun.sleepdownschedule.domain.schedule.withGeneralSettingsFrom as applyGeneralSettings
import com.xiaomanjun.sleepdownschedule.domain.schedule.withHomeChromeBlurScale as applyHomeChromeBlurScale
import com.xiaomanjun.sleepdownschedule.domain.schedule.withNotificationSettingsFrom as applyNotificationSettings
import com.xiaomanjun.sleepdownschedule.domain.schedule.withPersonalizationFrom as applyPersonalization

import android.content.Context
import androidx.room.migration.Migration

/**
 * Source-compatibility surface for the pre-refactor root package.
 *
 * Implementations live in model/data/domain packages. Keeping these aliases and forwarding
 * functions lets Android entrypoints migrate independently without changing persistence formats,
 * reflection-visible component names, or a large number of call sites in one risky batch.
 */
typealias WeekParity = com.xiaomanjun.sleepdownschedule.model.WeekParity
typealias NotificationMode = com.xiaomanjun.sleepdownschedule.model.NotificationMode
typealias DefaultWallpaperStyle = com.xiaomanjun.sleepdownschedule.model.DefaultWallpaperStyle
typealias DockAlignment = com.xiaomanjun.sleepdownschedule.model.DockAlignment
typealias HomeStartMode = com.xiaomanjun.sleepdownschedule.model.HomeStartMode
typealias LiveUpdateChipTextMode = com.xiaomanjun.sleepdownschedule.model.LiveUpdateChipTextMode
typealias PeriodSchemeMode = com.xiaomanjun.sleepdownschedule.model.PeriodSchemeMode
typealias ScheduleTermState = com.xiaomanjun.sleepdownschedule.model.ScheduleTermState
typealias CourseCardColorMode = com.xiaomanjun.sleepdownschedule.model.CourseCardColorMode

typealias CourseEntity = com.xiaomanjun.sleepdownschedule.model.CourseEntity
typealias ScheduleProfileEntity = com.xiaomanjun.sleepdownschedule.model.ScheduleProfileEntity
typealias ScheduleConfigEntity = com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
typealias PeriodEntity = com.xiaomanjun.sleepdownschedule.model.PeriodEntity
typealias PeriodSchemeEntity = com.xiaomanjun.sleepdownschedule.model.PeriodSchemeEntity
typealias PeriodSchemeTimeEntity = com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
typealias AgentDailySessionEntity = com.xiaomanjun.sleepdownschedule.model.AgentDailySessionEntity
typealias AgentMessageEntity = com.xiaomanjun.sleepdownschedule.model.AgentMessageEntity
typealias AppState = com.xiaomanjun.sleepdownschedule.model.AppState
typealias ImportDraftSource = com.xiaomanjun.sleepdownschedule.model.ImportDraftSource
typealias ImportDraft = com.xiaomanjun.sleepdownschedule.model.ImportDraft

typealias ScheduleConverters = com.xiaomanjun.sleepdownschedule.data.local.ScheduleConverters
typealias CourseDao = com.xiaomanjun.sleepdownschedule.data.local.CourseDao
typealias ConfigDao = com.xiaomanjun.sleepdownschedule.data.local.ConfigDao
typealias PeriodSchemeDao = com.xiaomanjun.sleepdownschedule.data.local.PeriodSchemeDao
typealias ScheduleProfileDao = com.xiaomanjun.sleepdownschedule.data.local.ScheduleProfileDao
typealias AgentDao = com.xiaomanjun.sleepdownschedule.data.local.AgentDao
typealias ScheduleRepository = com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository

internal const val DefaultHomeChromeBlurScale =
    com.xiaomanjun.sleepdownschedule.model.DefaultHomeChromeBlurScale
internal const val MinHomeChromeBlurScale =
    com.xiaomanjun.sleepdownschedule.model.MinHomeChromeBlurScale
internal const val MaxHomeChromeBlurScale =
    com.xiaomanjun.sleepdownschedule.model.MaxHomeChromeBlurScale
internal const val DefaultHomeChromeSamplingScale =
    com.xiaomanjun.sleepdownschedule.model.DefaultHomeChromeSamplingScale
internal const val LiquidCourseCardBlurMax =
    com.xiaomanjun.sleepdownschedule.model.LiquidCourseCardBlurMax
internal const val SimpleCourseCardBlurMax =
    com.xiaomanjun.sleepdownschedule.model.SimpleCourseCardBlurMax

internal val APP_DATABASE_MIGRATIONS: List<Migration>
    get() = com.xiaomanjun.sleepdownschedule.data.local.APP_DATABASE_MIGRATIONS

internal fun createAppDatabase(
    context: Context,
    databaseName: String = "course_schedule.db"
): AppDatabase = com.xiaomanjun.sleepdownschedule.data.local.createAppDatabase(context, databaseName)

internal fun courseCardBlurMaximum(glassEnabled: Boolean): Float =
    com.xiaomanjun.sleepdownschedule.model.courseCardBlurMaximum(glassEnabled)

internal fun normalizedHomeChromeBlurScale(value: Float): Float =
    com.xiaomanjun.sleepdownschedule.model.normalizedHomeChromeBlurScale(value)

internal fun ScheduleConfigEntity.withGeneralSettingsFrom(
    draft: ScheduleConfigEntity
): ScheduleConfigEntity = applyGeneralSettings(draft)

internal fun ScheduleConfigEntity.withHomeChromeBlurScale(value: Float): ScheduleConfigEntity =
    applyHomeChromeBlurScale(value)

internal fun ScheduleConfigEntity.withNotificationSettingsFrom(
    draft: ScheduleConfigEntity
): ScheduleConfigEntity = applyNotificationSettings(draft)

internal fun ScheduleConfigEntity.withChangesFrom(
    original: ScheduleConfigEntity,
    updated: ScheduleConfigEntity
): ScheduleConfigEntity = applyChangedScheduleConfig(original, updated)

internal fun ScheduleConfigEntity.withPersonalizationFrom(
    updated: ScheduleConfigEntity
): ScheduleConfigEntity = applyPersonalization(updated)

internal fun ScheduleConfigEntity.switchCourseCardGlassMode(enabled: Boolean): ScheduleConfigEntity =
    applyCourseCardGlassMode(enabled)

fun defaultConfig(id: Int = 1): ScheduleConfigEntity =
    com.xiaomanjun.sleepdownschedule.model.defaultConfig(id)

fun defaultPeriods(scheduleId: Int = 1): List<PeriodEntity> =
    com.xiaomanjun.sleepdownschedule.model.defaultPeriods(scheduleId)
