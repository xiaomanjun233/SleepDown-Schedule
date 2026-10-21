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
    fun migrate44To45SharesIdenticalSchemesAndPreservesEveryDistinctTimeline() {
        helper.createDatabase(TEST_DATABASE, 44).use { database ->
            for (id in 7..13) {
                database.execSQL(legacyConfigInsertSql(40, id))
                database.execSQL("INSERT INTO schedule_profiles (id, name, isActive) VALUES (?, ?, ?)",
                    arrayOf<Any>(id, "课表$id", if (id == 7) 1 else 0))
                database.execSQL("UPDATE schedule_config SET morningPeriodCount=1, noonPeriodCount=0, afternoonPeriodCount=1, eveningPeriodCount=0 WHERE id=?", arrayOf(id))
            }
            database.execSQL("UPDATE schedule_config SET morningPeriodCount=0, noonPeriodCount=1 WHERE id=9")
            database.execSQL("UPDATE schedule_config SET morningPeriodCount=4, afternoonPeriodCount=4, eveningPeriodCount=4 WHERE id=10")

            val standard = listOf(Triple(1, "08:00", "08:45"), Triple(2, "14:00", "14:45"))
            insertScheme44(database, 11, 7, "夏季", standard, active = true,
                specialBreaks = "{\"1\":10,\"2\":15}", overrides = "[1,2]")
            insertScheme44(database, 21, 8, "同样的作息但名称不同", standard, active = true,
                specialBreaks = "{\"2\":15,\"1\":10}", overrides = "[2,1]")
            insertScheme44(database, 12, 7, "未启用的冬季", standard, breakMinutes = 15)
            insertScheme44(database, 13, 7, "自动方案", standard, mode = "AUTO_MATCH")
            insertScheme44(database, 14, 7, "覆盖不同", standard, overrides = "[1]")
            insertScheme44(database, 16, 7, "旧版未写时间的方案", emptyList(), breakMinutes = 17)
            insertScheme44(database, 31, 9, "分段不同", standard, active = true,
                specialBreaks = "{\"1\":10,\"2\":15}", overrides = "[1,2]")
            val inferred = listOf(Triple(1, "08:10", "08:55"), Triple(2, "12:10", "12:55"),
                Triple(3, "15:10", "15:55"), Triple(4, "19:10", "19:55"))
            insertScheme44(database, 41, 10, "没有活动标记的方案", inferred)
            insertScheme44(database, 42, 10, "独立推断未启用方案", listOf(Triple(1, "19:30", "20:15")))
            insertScheme44(database, 51, 11, "保留原始方案", standard, active = true)
            for (id in 7..9) insertPeriods44(database, id, standard)
            insertPeriods44(database, 10, inferred)
            insertPeriods44(database, 11, listOf(Triple(1, "09:00", "09:45"), Triple(2, "15:00", "15:45")))
            insertPeriods44(database, 12, listOf(Triple(1, "10:00", "10:45"), Triple(2, "16:00", "16:45")))
            // A materialized timeline with no config/profile must also survive as a library entry.
            insertPeriods44(database, 14, listOf(Triple(1, "20:00", "20:45")))
            database.execSQL("INSERT INTO courses (id,name,weekday,periods,weeks,weekParity,scheduleId,customStartTime,customEndTime) VALUES (42,'课程不变',2,'[1,2]','[1,3]','ODD',8,'08:03','14:42')")
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, APP_DATABASE_VERSION, true,
            *APP_DATABASE_MIGRATIONS.toTypedArray()).use { database ->
            assertSingleValue(database, "SELECT COUNT(*) FROM period_schemes", 12)
            assertSingleValue(database, "SELECT activePeriodSchemeId FROM schedule_config WHERE id=7", 11)
            assertSingleValue(database, "SELECT activePeriodSchemeId FROM schedule_config WHERE id=8", 11)
            assertSingleValue(database, "SELECT activePeriodSchemeId FROM schedule_config WHERE id=9", 31)
            assertSingleValue(database, "SELECT activePeriodSchemeId FROM schedule_config WHERE id=10", 41)
            assertSingleValue(database, "SELECT COUNT(*) FROM period_schemes WHERE id=21", 0)
            assertSingleValue(database, "SELECT COUNT(*) FROM period_scheme_times WHERE schemeId=21", 0)
            assertSingleText(database, "SELECT publicId FROM period_schemes WHERE id=11", "legacy-11")
            assertSingleText(database, "SELECT name FROM period_schemes WHERE id=11", "夏季")
            assertSingleText(database, "SELECT sourceScheduleName FROM period_schemes WHERE id=11", "课表7（夏季）；课表8（同样的作息但名称不同）")
            assertSingleValue(database, "SELECT COUNT(*) FROM period_schemes WHERE id IN (12,13,14,31,42,51)", 6)
            assertSingleText(database, "SELECT startTime FROM period_scheme_times WHERE schemeId=16 AND periodIndex=1", "08:00")
            assertSingleValue(database, "SELECT morningPeriodCount FROM period_schemes WHERE id=16", 1)
            assertSingleValue(database, "SELECT noonPeriodCount FROM period_schemes WHERE id=11", 0)
            assertSingleValue(database, "SELECT noonPeriodCount FROM period_schemes WHERE id=31", 1)
            for (column in listOf("morningPeriodCount", "noonPeriodCount", "afternoonPeriodCount", "eveningPeriodCount")) {
                assertSingleValue(database, "SELECT $column FROM period_schemes WHERE id=41", 1)
            }
            assertSingleValue(database, "SELECT eveningPeriodCount FROM period_schemes WHERE id=42", 1)
            assertSingleValue(database, "SELECT morningPeriodCount FROM period_schemes WHERE id=42", 0)

            assertSingleText(database, "SELECT startTime FROM period_scheme_times WHERE schemeId=51 AND periodIndex=1", "08:00")
            assertSingleText(database, "SELECT startTime FROM periods WHERE scheduleId=11 AND periodIndex=1", "09:00")
            assertSingleText(database, "SELECT t.startTime FROM period_scheme_times t JOIN schedule_config c ON c.activePeriodSchemeId=t.schemeId WHERE c.id=11 AND t.periodIndex=1", "09:00")
            assertSingleText(database, "SELECT s.name FROM period_schemes s JOIN schedule_config c ON c.activePeriodSchemeId=s.id WHERE c.id=11", "升级前作息")
            assertSingleText(database, "SELECT t.startTime FROM period_scheme_times t JOIN schedule_config c ON c.activePeriodSchemeId=t.schemeId WHERE c.id=12 AND t.periodIndex=1", "10:00")
            assertSingleText(database, "SELECT t.startTime FROM period_scheme_times t JOIN period_schemes s ON s.id=t.schemeId WHERE s.scheduleId=14", "20:00")
            assertSingleValue(database, "SELECT activePeriodSchemeId IS NULL FROM schedule_config WHERE id=13", 1)
            assertSingleValue(database, "SELECT COUNT(*) FROM period_schemes WHERE publicId=''", 0)
            assertSingleValue(database, "SELECT COUNT(*) FROM period_scheme_library_migrations", 0)
            assertSingleText(database, "SELECT customStartTime FROM courses WHERE id=42", "08:03")
            assertSingleValue(database, "SELECT scheduleId FROM courses WHERE id=42", 8)
            assertSingleValue(database, "SELECT currentWeek FROM schedule_config WHERE id=8", 6)
        }
    }

    private fun insertScheme44(
        database: SupportSQLiteDatabase,
        id: Int,
        scheduleId: Int,
        name: String,
        times: List<Triple<Int, String, String>>,
        active: Boolean = false,
        mode: String = "MANUAL",
        breakMinutes: Int = 10,
        specialBreaks: String = "{}",
        overrides: String = "{}"
    ) {
        database.execSQL("""
            INSERT INTO period_schemes (id, scheduleId, name, mode, isActive, classDurationMinutes,
                breakDurationMinutes, morningStartTime, noonStartTime, afternoonStartTime, eveningStartTime,
                specialBreaksJson, overridesJson)
            VALUES (?, ?, ?, ?, ?, 45, ?, '08:00', '12:00', '14:00', '19:00', ?, ?)
        """.trimIndent(), arrayOf<Any>(id, scheduleId, name, mode, if (active) 1 else 0, breakMinutes, specialBreaks, overrides))
        times.forEach { (index, start, end) ->
            database.execSQL("INSERT INTO period_scheme_times (schemeId, periodIndex, startTime, endTime) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(id, index, start, end))
        }
    }

    private fun insertPeriods44(database: SupportSQLiteDatabase, scheduleId: Int, times: List<Triple<Int, String, String>>) {
        times.forEach { (index, start, end) ->
            database.execSQL("INSERT INTO periods (scheduleId, periodIndex, startTime, endTime) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(scheduleId, index, start, end))
        }
    }

    @Test
    fun migrate43To44KeepsOldAlignmentAndDefaultsCurrentCardLayout() {
        helper.createDatabase(TEST_DATABASE, 43).use { database ->
            database.execSQL(legacyConfigInsertSql(40))
            database.execSQL("UPDATE schedule_config SET weekCardTextAlignment='START' WHERE id=7")
        }
        helper.runMigrationsAndValidate(TEST_DATABASE, APP_DATABASE_VERSION, true,
            *APP_DATABASE_MIGRATIONS.toTypedArray()).use { database ->
            assertSingleValue(database, "SELECT currentWeek FROM schedule_config WHERE id=7", 6)
            assertSingleText(database, "SELECT weekCardTextAlignment FROM schedule_config WHERE id=7", "START")
            assertSingleText(database, "SELECT weekCardContentLayout FROM schedule_config WHERE id=7", "CURRENT")
        }
    }

    @Test
    fun migrate42To43KeepsScheduleAndDefaultsWeekCardContent() {
        helper.createDatabase(TEST_DATABASE, 42).use { database ->
            database.execSQL(legacyConfigInsertSql(40))
        }
        helper.runMigrationsAndValidate(TEST_DATABASE, APP_DATABASE_VERSION, true,
            *APP_DATABASE_MIGRATIONS.toTypedArray()).use { database ->
            assertSingleValue(database, "SELECT currentWeek FROM schedule_config WHERE id=7", 6)
            assertSingleValue(database, "SELECT weekCardShowLocation FROM schedule_config WHERE id=7", 1)
            assertSingleValue(database, "SELECT weekCardShowTeacher FROM schedule_config WHERE id=7", 1)
            assertSingleText(database, "SELECT weekCardTextAlignment FROM schedule_config WHERE id=7", "CENTER")
        }
    }

    @Test
    fun migrate41To42PreservesCoursesAndAddsOptionalImportedBellTimes() {
        helper.createDatabase(TEST_DATABASE, 41).use { database ->
            database.execSQL(
                "INSERT INTO courses (id,name,weekday,periods,weeks,weekParity,scheduleId,customStartTime,customEndTime) " +
                    "VALUES (42,'迁移保留',3,'[3,4]','[1,2]','ALL',7,'10:10','11:45')"
            )
        }
        helper.runMigrationsAndValidate(TEST_DATABASE, APP_DATABASE_VERSION, true,
            *APP_DATABASE_MIGRATIONS.toTypedArray()).use { database ->
            assertSingleText(database, "SELECT customStartTime FROM courses WHERE id=42", "10:10")
            assertSingleText(database, "SELECT customEndTime FROM courses WHERE id=42", "11:45")
            database.query("SELECT customPeriodTimes FROM courses WHERE id=42").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
        }
    }

    @Test
    fun migrate40To41AddsEmptyAdjustmentsAndPreservesSchedule() {
        helper.createDatabase(TEST_DATABASE, 40).use { database ->
            database.execSQL(legacyConfigInsertSql(40))
            database.execSQL("INSERT INTO courses (id,name,weekday,periods,weeks,weekParity,scheduleId) VALUES (42,'迁移保留',2,'[1,2]','[1,3]','ODD',7)")
        }
        helper.runMigrationsAndValidate(TEST_DATABASE, APP_DATABASE_VERSION, true,
            *APP_DATABASE_MIGRATIONS.toTypedArray()).use { database ->
            assertSingleText(database, "SELECT scheduleAdjustmentsJson FROM schedule_config WHERE id=7", "")
            assertSingleValue(database, "SELECT currentWeek FROM schedule_config WHERE id=7", 6)
            assertSingleText(database, "SELECT weeks FROM courses WHERE id=42", "[1,3]")
        }
    }

    @Test
    fun migrate39To40DefaultsColoredTextOffAndPreservesCourses() {
        helper.createDatabase(TEST_DATABASE, 39).use { database ->
            database.execSQL("""
                INSERT INTO courses (id, name, weekday, periods, weeks, weekParity, scheduleId)
                VALUES (42, '迁移保留', 2, '[1,2]', '[1,3]', 'ODD', 7)
            """.trimIndent())
            database.execSQL("""
                INSERT INTO schedule_config (
                    id, totalWeeks, currentWeek, notificationLeadMinutes,
                    autoCurrentWeek, notificationsEnabled, notificationMode,
                    wallpaperBlur, wallpaperBrightness, cardColorArgb, cardAlpha,
                    courseCardBlur, courseCardGlassEnabled, courseCardFontScale,
                    homeTextLight, followSystemDarkMode, darkMode, defaultWallpaperStyle,
                    hideEmptyWeekends, dockAlignment, defaultHomeMode,
                    liveUpdateActionsEnabled, liveUpdateChipTextMode,
                    classDurationMinutes, breakDurationMinutes, hideFromRecents, autoCheckUpdates
                ) VALUES (
                    7, 20, 3, 10, 0, 1, 'STANDARD', 0, 1, 4281558681, 0.7,
                    18, 1, 1, 0, 1, 0, 'NONE', 0, 'CENTER', 'WEEK', 1, 'LOCATION', 45, 10, 0, 1
                )
            """.trimIndent())
        }
        helper.runMigrationsAndValidate(
            TEST_DATABASE, APP_DATABASE_VERSION, true, *APP_DATABASE_MIGRATIONS.toTypedArray()
        ).use { database ->
            assertSingleValue(database, "SELECT courseCardColoredTextEnabled FROM schedule_config WHERE id=7", 0)
            assertSingleText(database, "SELECT name FROM courses WHERE id=42", "迁移保留")
            assertSingleText(database, "SELECT weeks FROM courses WHERE id=42", "[1,3]")
            database.execSQL("UPDATE schedule_config SET courseCardColoredTextEnabled=1 WHERE id=7")
            assertSingleValue(database, "SELECT courseCardColoredTextEnabled FROM schedule_config WHERE id=7", 1)
            assertSingleFloat(database, "SELECT cardAlpha FROM schedule_config WHERE id=7", 0.7f)
        }
    }

    @Test
    fun migrate38To39AddsCourseCardMaterialControls() {
        helper.createDatabase(TEST_DATABASE, 38).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            APP_DATABASE_VERSION,
            true,
            *APP_DATABASE_MIGRATIONS.toTypedArray()
        ).close()
    }

    @Test
    fun migrate36To37PreservesCoursesAndAddsExactTimeAndColorColumns() {
        helper.createDatabase(TEST_DATABASE, 36).use { database ->
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
            assertSingleText(database, "SELECT name FROM courses WHERE id = 42", "数据结构")
            assertSingleValue(database, "SELECT scheduleId FROM courses WHERE id = 42", 7)
            database.query(
                "SELECT customStartTime, customEndTime, customColorArgb FROM courses WHERE id = 42"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
            }
        }
    }

    @Test
    fun migrate37To38ConvertsLegacyMulticolorAndAbsoluteWeekHeight() {
        helper.createDatabase(TEST_DATABASE, 37).use { database ->
            database.execSQL(
                """
                INSERT INTO schedule_config (
                    id, totalWeeks, currentWeek, notificationLeadMinutes,
                    autoCurrentWeek, notificationsEnabled, notificationMode,
                    wallpaperBlur, wallpaperBrightness, cardColorArgb, cardAlpha,
                    courseCardBlur, courseCardGlassEnabled, courseCardFontScale,
                    alternateCardColorArgb, weekCardHeightDp, homeTextLight,
                    followSystemDarkMode, darkMode, defaultWallpaperStyle,
                    hideEmptyWeekends, dockAlignment, defaultHomeMode,
                    liveUpdateActionsEnabled, liveUpdateChipTextMode,
                    classDurationMinutes, breakDurationMinutes,
                    morningPeriodCount, noonPeriodCount, afternoonPeriodCount, eveningPeriodCount,
                    hideFromRecents, autoCheckUpdates
                ) VALUES (
                    7, 20, 6, 15,
                    0, 1, 'STANDARD',
                    0, 1, 0, 1,
                    10, 1, 1,
                    0, 54, 0,
                    1, 0, 'NONE',
                    0, 'CENTER', 'WEEK',
                    1, 'LOCATION',
                    45, 10,
                    4, 0, 4, 4,
                    0, 1
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
            assertSingleText(
                database,
                "SELECT courseCardColorMode FROM schedule_config WHERE id = 7",
                "COLORFUL"
            )
            assertSingleText(
                database,
                "SELECT alternateCourseCardColorMode FROM schedule_config WHERE id = 7",
                "COLORFUL"
            )
            assertSingleValue(
                database,
                "SELECT cardColorArgb FROM schedule_config WHERE id = 7",
                0xFFD6E9FF.toInt()
            )
            assertSingleFloat(
                database,
                "SELECT weekCardHeightScale FROM schedule_config WHERE id = 7",
                0.75f
            )
            assertSingleFloat(
                database,
                "SELECT weekCardCornerProgress FROM schedule_config WHERE id = 7",
                0.5f
            )
        }
    }

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
                val active = requireNotNull(database.periodSchemeDao().getActiveScheme(7))

                assertEquals(active.id, config.activePeriodSchemeId)

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
                val active = requireNotNull(database.periodSchemeDao().getActiveScheme(7))

                assertEquals(active.id, config.activePeriodSchemeId)

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

    private fun legacyConfigInsertSql(version: Int, scheduleId: Int = 7): String {
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
                $scheduleId, 20, 6, 15, '2026-02-23',
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

    private fun assertSingleFloat(database: SupportSQLiteDatabase, sql: String, expected: Float) {
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(expected, cursor.getFloat(0), 0.0001f)
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-v32-v34-test"
    }
}
