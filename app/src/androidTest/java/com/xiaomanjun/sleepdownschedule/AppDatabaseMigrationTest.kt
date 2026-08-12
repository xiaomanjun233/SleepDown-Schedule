package com.xiaomanjun.sleepdownschedule

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate32To34PreservesUserScheduleData() {
        helper.createDatabase(TEST_DATABASE, 32).use { database ->
            database.execSQL(
                """
                INSERT INTO schedule_profiles (id, name, isActive)
                VALUES (7, '保留课表', 1)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO schedule_config (
                    id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate,
                    autoCurrentWeek, termState, notificationsEnabled, notificationMode,
                    wallpaperUri, wallpaperBlur, wallpaperBrightness,
                    wallpaperPortraitCenterX, wallpaperPortraitCenterY, wallpaperPortraitScale,
                    wallpaperLandscapeCenterX, wallpaperLandscapeCenterY, wallpaperLandscapeScale,
                    wallpaperSourceWidth, wallpaperSourceHeight, cardColorArgb, cardAlpha,
                    courseCardBlur, courseCardGlassEnabled, courseCardFontScale, weekCardHeightDp,
                    homeTextLight, followSystemDarkMode, darkMode, defaultWallpaperStyle,
                    hideEmptyWeekends, dockAlignment, defaultHomeMode, liveUpdateActionsEnabled,
                    liveUpdateChipTextMode, classDurationMinutes, breakDurationMinutes,
                    hideFromRecents, autoCheckUpdates, morningPeriodCount, noonPeriodCount,
                    afternoonPeriodCount, eveningPeriodCount
                ) VALUES (
                    7, 20, 6, 15, '2026-02-23',
                    1, 'ACTIVE', 1, 'STANDARD',
                    NULL, 0, 1,
                    0.5, 0.5, 1,
                    0.5, 0.5, 1,
                    NULL, NULL, 4293516543, 1,
                    18, 1, 1, NULL,
                    0, 1, 0, 'KANBAN',
                    0, 'LEFT', 'WEEK', 1,
                    'LOCATION', 45, 10,
                    0, 1, 4, 0,
                    4, 4
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO courses (
                    id, name, teacher, location, weekday, periods, weeks,
                    weekParity, note, scheduleId
                ) VALUES (
                    42, '数据结构', '张老师', 'A101', 2, '[1,2]', '[1,2,3]',
                    'ALL', '升级后保留', 7
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            APP_DATABASE_VERSION,
            true,
            *APP_DATABASE_MIGRATIONS.toTypedArray()
        ).use { database ->
            assertSingleValue(database, "SELECT COUNT(*) FROM courses WHERE id = 42", 1)
            assertSingleValue(database, "SELECT scheduleId FROM courses WHERE id = 42", 7)
            assertSingleValue(database, "SELECT currentWeek FROM schedule_config WHERE id = 7", 6)
            assertSingleText(database, "SELECT note FROM courses WHERE id = 42", "升级后保留")
        }
    }

    @Test
    fun migrate26To34PreservesTimelineAndBuildsPeriodScheme() = runLegacyMigrationTest(26)

    @Test
    fun migrate27To34PreservesTimelineAndAddsNoonColumn() = runLegacyMigrationTest(27)

    @Test
    fun repairPartial28SchemaPreservesExistingNoonTopology() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val databaseName = "repair-partial-v28-v34-test"
            context.deleteDatabase(databaseName)
            createLegacyDatabase(context, databaseName, 27)
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(databaseName).absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { database ->
                database.execSQL(
                    "ALTER TABLE schedule_config ADD COLUMN noonPeriodCount INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE schedule_config SET morningPeriodCount = 1, noonPeriodCount = 1, afternoonPeriodCount = 1, eveningPeriodCount = 1 WHERE id = 7"
                )
                database.version = 28
            }

            val database = createAppDatabase(context, databaseName)
            try {
                val config = database.configDao().getConfig(7)!!
                val active = database.periodSchemeDao().getSchemes(7).single { it.isActive }

                assertEquals(1, config.morningPeriodCount)
                assertEquals(1, config.noonPeriodCount)
                assertEquals(1, config.afternoonPeriodCount)
                assertEquals(1, config.eveningPeriodCount)
                assertEquals("12:00", active.noonStartTime)
                assertEquals("升级后保留", database.courseDao().getAllCourses().single { it.id == 42L }.note)
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    private fun runLegacyMigrationTest(version: Int) {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val databaseName = "migration-v$version-v34-test"
            context.deleteDatabase(databaseName)
            createLegacyDatabase(context, databaseName, version)
            val database = createAppDatabase(context, databaseName)

            try {
                val config = database.configDao().getConfig(7)!!
                val periods = database.configDao().getPeriods(7)
                val schemes = database.periodSchemeDao().getSchemes(7)
                val active = schemes.single { it.isActive }

                assertEquals(6, config.currentWeek)
                assertEquals(1, config.morningPeriodCount)
                assertEquals(0, config.noonPeriodCount)
                assertEquals(2, config.afternoonPeriodCount)
                assertEquals(1, config.eveningPeriodCount)
                assertEquals(listOf("08:00", "12:30", "15:00", "19:00"), periods.map { it.startTime })
                assertEquals("12:00", active.noonStartTime)
                assertEquals(periods.map { it.periodIndex }, database.periodSchemeDao().getTimes(active.id).map { it.periodIndex })
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    private fun createLegacyDatabase(context: Context, name: String, version: Int) {
        require(version == 26 || version == 27)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL(
                "CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, teacher TEXT, location TEXT, weekday INTEGER NOT NULL, periods TEXT NOT NULL, weeks TEXT NOT NULL, weekParity TEXT NOT NULL, note TEXT, scheduleId INTEGER NOT NULL DEFAULT 1)"
            )
            database.execSQL(
                "CREATE TABLE schedule_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isActive INTEGER NOT NULL)"
            )
            database.execSQL(legacyScheduleConfigSql(version))
            database.execSQL(
                "CREATE TABLE periods (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))"
            )
            database.execSQL(
                "CREATE TABLE agent_daily_sessions (scheduleId INTEGER NOT NULL, date TEXT NOT NULL, dailyPackJson TEXT NOT NULL, providerId TEXT NOT NULL, model TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, generationStatus TEXT NOT NULL, lastError TEXT, PRIMARY KEY(scheduleId, date))"
            )
            database.execSQL(
                "CREATE TABLE agent_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, sessionDate TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)"
            )
            if (version == 27) {
                database.execSQL(
                    "CREATE TABLE period_schemes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, isActive INTEGER NOT NULL, classDurationMinutes INTEGER NOT NULL, breakDurationMinutes INTEGER NOT NULL, morningStartTime TEXT NOT NULL, afternoonStartTime TEXT NOT NULL, eveningStartTime TEXT NOT NULL, specialBreaksJson TEXT NOT NULL, overridesJson TEXT NOT NULL)"
                )
                database.execSQL(
                    "CREATE TABLE period_scheme_times (schemeId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(schemeId, periodIndex))"
                )
            }

            database.execSQL("INSERT INTO schedule_profiles (id, name, isActive) VALUES (7, '旧版课表', 1)")
            database.execSQL(legacyConfigInsertSql(version))
            listOf(
                Triple(1, "08:00", "08:45"),
                Triple(2, "12:30", "13:15"),
                Triple(3, "15:00", "15:45"),
                Triple(4, "19:00", "19:45")
            ).forEach { (index, start, end) ->
                database.execSQL(
                    "INSERT INTO periods (scheduleId, periodIndex, startTime, endTime) VALUES (7, ?, ?, ?)",
                    arrayOf<Any>(index, start, end)
                )
            }
            database.execSQL(
                "INSERT INTO courses (id, name, teacher, location, weekday, periods, weeks, weekParity, note, scheduleId) VALUES (42, '数据结构', '张老师', 'A101', 2, '[1,2]', '[1,2,3]', 'ALL', '升级后保留', 7)"
            )
            if (version == 27) {
                database.execSQL(
                    "INSERT INTO period_schemes (id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes, morningStartTime, afternoonStartTime, eveningStartTime, specialBreaksJson, overridesJson) VALUES (11, 7, '旧版作息', 'MANUAL', 1, 45, 10, '08:00', '12:30', '19:00', '{}', '{}')"
                )
                database.execSQL(
                    "INSERT INTO period_scheme_times (schemeId, periodIndex, startTime, endTime) SELECT 11, periodIndex, startTime, endTime FROM periods WHERE scheduleId = 7"
                )
            }
            database.version = version
        }
    }

    private fun legacyScheduleConfigSql(version: Int): String {
        val periodColumns = if (version == 27) {
            ", morningPeriodCount INTEGER NOT NULL DEFAULT 0, afternoonPeriodCount INTEGER NOT NULL DEFAULT 0, eveningPeriodCount INTEGER NOT NULL DEFAULT 0"
        } else {
            ""
        }
        return """
            CREATE TABLE schedule_config (
                id INTEGER NOT NULL PRIMARY KEY,
                totalWeeks INTEGER NOT NULL,
                currentWeek INTEGER NOT NULL,
                notificationLeadMinutes INTEGER NOT NULL,
                termStartDate TEXT,
                autoCurrentWeek INTEGER NOT NULL DEFAULT 0,
                notificationsEnabled INTEGER NOT NULL DEFAULT 1,
                notificationMode TEXT NOT NULL DEFAULT 'STANDARD',
                wallpaperUri TEXT,
                wallpaperBlur REAL NOT NULL DEFAULT 0,
                wallpaperBrightness REAL NOT NULL DEFAULT 1,
                wallpaperPortraitCenterX REAL DEFAULT 0.5,
                wallpaperPortraitCenterY REAL DEFAULT 0.5,
                wallpaperPortraitScale REAL DEFAULT 1,
                wallpaperLandscapeCenterX REAL DEFAULT 0.5,
                wallpaperLandscapeCenterY REAL DEFAULT 0.5,
                wallpaperLandscapeScale REAL DEFAULT 1,
                wallpaperSourceWidth INTEGER,
                wallpaperSourceHeight INTEGER,
                cardColorArgb INTEGER NOT NULL DEFAULT 4293516543,
                cardAlpha REAL NOT NULL DEFAULT 1,
                courseCardBlur REAL NOT NULL DEFAULT 18,
                courseCardGlassEnabled INTEGER NOT NULL DEFAULT 1,
                courseCardFontScale REAL NOT NULL DEFAULT 1,
                weekCardHeightDp REAL,
                homeTextLight INTEGER NOT NULL DEFAULT 0,
                followSystemDarkMode INTEGER NOT NULL DEFAULT 1,
                darkMode INTEGER NOT NULL DEFAULT 0,
                defaultWallpaperStyle TEXT NOT NULL DEFAULT 'KANBAN',
                hideEmptyWeekends INTEGER NOT NULL DEFAULT 0,
                dockAlignment TEXT NOT NULL DEFAULT 'LEFT',
                defaultHomeMode TEXT NOT NULL DEFAULT 'WEEK',
                liveUpdateActionsEnabled INTEGER NOT NULL DEFAULT 1,
                liveUpdateChipTextMode TEXT NOT NULL DEFAULT 'LOCATION',
                classDurationMinutes INTEGER NOT NULL DEFAULT 45,
                breakDurationMinutes INTEGER NOT NULL DEFAULT 10,
                hideFromRecents INTEGER NOT NULL DEFAULT 0,
                autoCheckUpdates INTEGER NOT NULL DEFAULT 1
                $periodColumns
            )
        """.trimIndent()
    }

    private fun legacyConfigInsertSql(version: Int): String {
        val periodColumns = if (version == 27) {
            ", morningPeriodCount, afternoonPeriodCount, eveningPeriodCount"
        } else {
            ""
        }
        val periodValues = if (version == 27) ", 1, 2, 1" else ""
        return """
            INSERT INTO schedule_config (
                id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate,
                autoCurrentWeek, notificationsEnabled, notificationMode,
                wallpaperBlur, wallpaperBrightness, cardColorArgb, cardAlpha,
                courseCardBlur, courseCardGlassEnabled, courseCardFontScale,
                homeTextLight, followSystemDarkMode, darkMode, defaultWallpaperStyle,
                hideEmptyWeekends, dockAlignment, defaultHomeMode, liveUpdateActionsEnabled,
                liveUpdateChipTextMode, classDurationMinutes, breakDurationMinutes,
                hideFromRecents, autoCheckUpdates$periodColumns
            ) VALUES (
                7, 20, 6, 15, '2026-02-23',
                1, 1, 'STANDARD',
                0, 1, 4293516543, 1,
                18, 1, 1,
                0, 1, 0, 'KANBAN',
                0, 'LEFT', 'WEEK', 1,
                'LOCATION', 45, 10,
                0, 1$periodValues
            )
        """.trimIndent()
    }

    private fun assertSingleValue(database: SupportSQLiteDatabase, sql: String, expected: Int) {
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private fun assertSingleText(database: SupportSQLiteDatabase, sql: String, expected: String) {
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-v32-v34-test"
    }
}
