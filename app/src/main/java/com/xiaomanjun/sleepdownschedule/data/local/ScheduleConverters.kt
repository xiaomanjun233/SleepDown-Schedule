package com.xiaomanjun.sleepdownschedule.data.local

import com.xiaomanjun.sleepdownschedule.model.*

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ScheduleConverters {
    private val json = Json

    @TypeConverter
    fun intListToString(value: List<Int>): String = json.encodeToString(ListSerializer(Int.serializer()), value)

    @TypeConverter
    fun stringToIntList(value: String): List<Int> = json.decodeFromString(ListSerializer(Int.serializer()), value)

    @TypeConverter
    fun parityToString(value: WeekParity): String = value.name

    @TypeConverter
    fun stringToParity(value: String): WeekParity =
        runCatching { WeekParity.valueOf(value) }.getOrDefault(WeekParity.ALL)

    @TypeConverter
    fun scheduleTermStateToString(value: ScheduleTermState): String = value.name

    @TypeConverter
    fun stringToScheduleTermState(value: String): ScheduleTermState =
        runCatching { ScheduleTermState.valueOf(value) }.getOrDefault(ScheduleTermState.MANUAL)

    @TypeConverter
    fun notificationModeToString(value: NotificationMode): String = value.name

    @TypeConverter
    fun stringToNotificationMode(value: String): NotificationMode =
        runCatching { NotificationMode.valueOf(value) }.getOrDefault(NotificationMode.STANDARD)

    @TypeConverter
    fun liveUpdateChipTextModeToString(value: LiveUpdateChipTextMode): String = value.name

    @TypeConverter
    fun stringToLiveUpdateChipTextMode(value: String): LiveUpdateChipTextMode =
        when (value) {
            // NORMAL is now the user-facing course-name mode. AUTO was the legacy location
            // alias; SHORT is retained only for old rows/backups and behaves as course name.
            "AUTO" -> LiveUpdateChipTextMode.LOCATION
            "SHORT" -> LiveUpdateChipTextMode.NORMAL
            else -> runCatching { LiveUpdateChipTextMode.valueOf(value) }.getOrDefault(LiveUpdateChipTextMode.LOCATION)
        }

    @TypeConverter
    fun defaultWallpaperStyleToString(value: DefaultWallpaperStyle): String = value.name

    @TypeConverter
    fun stringToDefaultWallpaperStyle(value: String): DefaultWallpaperStyle =
        runCatching { DefaultWallpaperStyle.valueOf(value) }.getOrDefault(DefaultWallpaperStyle.KANBAN)

    @TypeConverter
    fun dockAlignmentToString(value: DockAlignment): String = value.name

    @TypeConverter
    fun stringToDockAlignment(value: String): DockAlignment =
        runCatching { DockAlignment.valueOf(value) }.getOrDefault(DockAlignment.CENTER)

    @TypeConverter
    fun homeStartModeToString(value: HomeStartMode): String = value.name

    @TypeConverter
    fun stringToHomeStartMode(value: String): HomeStartMode =
        runCatching { HomeStartMode.valueOf(value) }.getOrDefault(HomeStartMode.WEEK)

    @TypeConverter
    fun courseCardColorModeToString(value: CourseCardColorMode): String = value.name

    @TypeConverter
    fun stringToCourseCardColorMode(value: String): CourseCardColorMode =
        runCatching { CourseCardColorMode.valueOf(value) }.getOrDefault(CourseCardColorMode.SOLID)
}
