package com.example.courseschedule

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
