package com.xiaomanjun.sleepdownschedule.data.local

import com.xiaomanjun.sleepdownschedule.AppDatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN termStartDate TEXT")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN autoCurrentWeek INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN notificationMode TEXT NOT NULL DEFAULT 'STANDARD'")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperUri TEXT")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperBlur REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperBrightness REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN cardColorArgb INTEGER NOT NULL DEFAULT 4293516543")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN cardAlpha REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN weekCardHeightDp REAL")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN homeTextLight INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN followSystemDarkMode INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN darkMode INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN defaultWallpaperStyle TEXT NOT NULL DEFAULT 'KANBAN'")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardBlur REAL NOT NULL DEFAULT 18")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardGlassEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN hideEmptyWeekends INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN dockAlignment TEXT NOT NULL DEFAULT 'LEFT'")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN defaultHomeMode TEXT NOT NULL DEFAULT 'WEEK'")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN liveUpdateActionsEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN liveUpdateChipTextMode TEXT NOT NULL DEFAULT 'AUTO'")
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE schedule_config SET liveUpdateChipTextMode = 'LOCATION' WHERE liveUpdateChipTextMode = 'NORMAL'")
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN classDurationMinutes INTEGER NOT NULL DEFAULT 45")
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN breakDurationMinutes INTEGER NOT NULL DEFAULT 10")
    }
}

private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE schedule_config SET liveUpdateChipTextMode = 'LOCATION' WHERE liveUpdateChipTextMode = 'AUTO'")
    }
}

private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN hideFromRecents INTEGER NOT NULL DEFAULT 0")
    }
}

private fun SupportSQLiteDatabase.hasTable(table: String): Boolean {
    query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
        return cursor.moveToFirst()
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
    if (!hasTable(table)) return false
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}

private fun ensureMultiScheduleSchema(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS schedule_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isActive INTEGER NOT NULL)")
    db.execSQL("INSERT INTO schedule_profiles (id, name, isActive) SELECT 1, '\u9ED8\u8BA4\u8BFE\u8868', 1 WHERE NOT EXISTS (SELECT 1 FROM schedule_profiles)")
    if (!db.hasColumn("courses", "scheduleId")) {
        db.execSQL("ALTER TABLE courses ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
    }
    repairPeriodsForMultiSchedule(db)
    ensureSingleActiveScheduleProfile(db)
}

private fun ensureSingleActiveScheduleProfile(db: SupportSQLiteDatabase) {
    val activeCount = db.query("SELECT COUNT(*) FROM schedule_profiles WHERE isActive = 1").use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    if (activeCount == 1) return
    val selectedId = db.query(
        """
        SELECT p.id
        FROM schedule_profiles p
        LEFT JOIN courses c ON c.scheduleId = p.id
        GROUP BY p.id
        ORDER BY p.isActive DESC, COUNT(c.id) DESC, p.id DESC
        LIMIT 1
        """.trimIndent()
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }
    selectedId?.let { id ->
        db.execSQL("UPDATE schedule_profiles SET isActive = CASE WHEN id = ? THEN 1 ELSE 0 END", arrayOf(id))
    }
}

private fun repairPeriodsForMultiSchedule(db: SupportSQLiteDatabase) {
    if (!db.hasTable("periods")) {
        db.execSQL("CREATE TABLE periods (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
        return
    }
    val hasScheduleId = db.hasColumn("periods", "scheduleId")
    db.execSQL("CREATE TABLE IF NOT EXISTS periods_room_fix (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
    if (hasScheduleId) {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT scheduleId, periodIndex, startTime, endTime FROM periods")
    } else {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT 1, periodIndex, startTime, endTime FROM periods")
    }
    db.execSQL("DROP TABLE periods")
    db.execSQL("ALTER TABLE periods_room_fix RENAME TO periods")
}

private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
    }
}

private val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
    }
}

private val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardFontScale REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
        addWallpaperCropColumns(db)
    }
}

private fun createAgentTables(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_daily_sessions (scheduleId INTEGER NOT NULL, date TEXT NOT NULL, dailyPackJson TEXT NOT NULL, providerId TEXT NOT NULL, model TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, generationStatus TEXT NOT NULL, lastError TEXT, PRIMARY KEY(scheduleId, date))")
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, sessionDate TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)")
}

private val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createAgentTables(db)
    }
}

private val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN autoCheckUpdates INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val addedCountAssignments = mutableListOf<String>()
        if (!db.hasColumn("schedule_config", "morningPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN morningPeriodCount INTEGER NOT NULL DEFAULT 0")
            addedCountAssignments += "morningPeriodCount = (SELECT COUNT(*) FROM periods p WHERE p.scheduleId = schedule_config.id AND CAST(substr(p.startTime, 1, 2) AS INTEGER) < 12)"
        }
        if (!db.hasColumn("schedule_config", "afternoonPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN afternoonPeriodCount INTEGER NOT NULL DEFAULT 0")
            addedCountAssignments += "afternoonPeriodCount = (SELECT COUNT(*) FROM periods p WHERE p.scheduleId = schedule_config.id AND CAST(substr(p.startTime, 1, 2) AS INTEGER) >= 12 AND CAST(substr(p.startTime, 1, 2) AS INTEGER) < 18)"
        }
        if (!db.hasColumn("schedule_config", "eveningPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN eveningPeriodCount INTEGER NOT NULL DEFAULT 0")
            addedCountAssignments += "eveningPeriodCount = (SELECT COUNT(*) FROM periods p WHERE p.scheduleId = schedule_config.id AND CAST(substr(p.startTime, 1, 2) AS INTEGER) >= 18)"
        }
        if (addedCountAssignments.isNotEmpty()) {
            db.execSQL("UPDATE schedule_config SET ${addedCountAssignments.joinToString()}")
        }
        createPeriodSchemeTables(db)
        db.execSQL("""
            INSERT INTO period_schemes (
              scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes,
              morningStartTime, afternoonStartTime, eveningStartTime, specialBreaksJson, overridesJson
            )
            SELECT c.id, '默认作息', 'MANUAL', 1, c.classDurationMinutes, c.breakDurationMinutes,
              COALESCE((SELECT MIN(startTime) FROM periods p WHERE p.scheduleId=c.id AND CAST(substr(p.startTime,1,2) AS INTEGER)<12), '08:00'),
              COALESCE((SELECT MIN(startTime) FROM periods p WHERE p.scheduleId=c.id AND CAST(substr(p.startTime,1,2) AS INTEGER)>=12 AND CAST(substr(p.startTime,1,2) AS INTEGER)<18), '14:00'),
              COALESCE((SELECT MIN(startTime) FROM periods p WHERE p.scheduleId=c.id AND CAST(substr(p.startTime,1,2) AS INTEGER)>=18), '19:00'),
              '{}', '{}'
            FROM schedule_config c
            WHERE NOT EXISTS (SELECT 1 FROM period_schemes existing WHERE existing.scheduleId = c.id)
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO period_scheme_times (schemeId, periodIndex, startTime, endTime)
            SELECT s.id, p.periodIndex, p.startTime, p.endTime
            FROM period_schemes s JOIN periods p ON p.scheduleId=s.scheduleId
        """.trimIndent())
    }
}
private val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("schedule_config", "noonPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN noonPeriodCount INTEGER NOT NULL DEFAULT 0")
        }
        rebuildPeriodSchemesWithNoonColumn(db)
        // v27 had no independent noon segment. Keep its original afternoon structure intact;
        // interpreting periods by clock hour here used to silently reset existing timetables.
    }
}

private fun rebuildPeriodSchemesWithNoonColumn(db: SupportSQLiteDatabase) {
    createPeriodSchemeTables(db)
    val noonStartTime = if (db.hasColumn("period_schemes", "noonStartTime")) {
        "COALESCE(noonStartTime, '12:00')"
    } else {
        "'12:00'"
    }
    db.execSQL("DROP TABLE IF EXISTS period_schemes_v28")
    db.execSQL(
        """
        CREATE TABLE period_schemes_v28 (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            scheduleId INTEGER NOT NULL,
            name TEXT NOT NULL,
            mode TEXT NOT NULL,
            isActive INTEGER NOT NULL,
            classDurationMinutes INTEGER NOT NULL,
            breakDurationMinutes INTEGER NOT NULL,
            morningStartTime TEXT NOT NULL,
            noonStartTime TEXT NOT NULL,
            afternoonStartTime TEXT NOT NULL,
            eveningStartTime TEXT NOT NULL,
            specialBreaksJson TEXT NOT NULL,
            overridesJson TEXT NOT NULL
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT INTO period_schemes_v28 (
            id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes,
            morningStartTime, noonStartTime, afternoonStartTime, eveningStartTime,
            specialBreaksJson, overridesJson
        )
        SELECT
            id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes,
            morningStartTime, $noonStartTime, afternoonStartTime, eveningStartTime,
            specialBreaksJson, overridesJson
        FROM period_schemes
        """.trimIndent()
    )
    db.execSQL("DROP TABLE period_schemes")
    db.execSQL("ALTER TABLE period_schemes_v28 RENAME TO period_schemes")
}

private val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val addedTermState = !db.hasColumn("schedule_config", "termState")
        if (addedTermState) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN termState TEXT NOT NULL DEFAULT 'MANUAL'")
        }
        if (!addedTermState) return
        val start = "replace(replace(termStartDate, '.', '-'), '/', '-')"
        db.execSQL(
            """
            UPDATE schedule_config
            SET termState = CASE
                WHEN autoCurrentWeek = 0 THEN 'MANUAL'
                WHEN termStartDate IS NULL OR trim(termStartDate) = '' THEN 'INVALID'
                WHEN date($start) IS NULL THEN 'INVALID'
                WHEN date('now', 'localtime') < date($start) THEN 'UPCOMING'
                WHEN date('now', 'localtime') > date(
                    $start,
                    '-' || ((CAST(strftime('%w', $start) AS INTEGER) + 6) % 7) || ' days',
                    '+' || ((CASE WHEN totalWeeks < 1 THEN 1 ELSE totalWeeks END) * 7 - 1) || ' days'
                ) THEN 'ENDED'
                ELSE 'ACTIVE'
            END
            """.trimIndent()
        )
    }
}

private val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `widget_appearances` (
                `variant` TEXT NOT NULL,
                `appWidgetId` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 0,
                `wallpaperUri` TEXT,
                `centerX` REAL NOT NULL DEFAULT 0.5,
                `centerY` REAL NOT NULL DEFAULT 0.5,
                `scale` REAL NOT NULL DEFAULT 1,
                `sourceWidth` INTEGER,
                `sourceHeight` INTEGER,
                `blurDp` REAL NOT NULL DEFAULT 6,
                `brightness` REAL NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`variant`, `appWidgetId`)
            )
            """.trimIndent()
        )
    }
}
private fun createPeriodSchemeTables(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS period_schemes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, isActive INTEGER NOT NULL, classDurationMinutes INTEGER NOT NULL, breakDurationMinutes INTEGER NOT NULL, morningStartTime TEXT NOT NULL, afternoonStartTime TEXT NOT NULL, eveningStartTime TEXT NOT NULL, specialBreaksJson TEXT NOT NULL, overridesJson TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS period_scheme_times (schemeId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(schemeId, periodIndex))")
}

private val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `widget_appearances_v31` (
                `variant` TEXT NOT NULL,
                `appWidgetId` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 0,
                `wallpaperUri` TEXT,
                `centerX` REAL NOT NULL DEFAULT 0.5,
                `centerY` REAL NOT NULL DEFAULT 0.5,
                `scale` REAL NOT NULL DEFAULT 1,
                `sourceWidth` INTEGER,
                `sourceHeight` INTEGER,
                `blurDp` REAL NOT NULL DEFAULT 6,
                `brightness` REAL NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`variant`, `appWidgetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `widget_appearances_v31`
            SELECT `variant`, `appWidgetId`, `enabled`, `wallpaperUri`, `centerX`, `centerY`, `scale`,
                   `sourceWidth`, `sourceHeight`, `blurDp`,
                   CASE
                       WHEN `appWidgetId` = 0 AND `updatedAt` = 0 AND `wallpaperUri` IS NULL
                            AND `enabled` = 0 AND ABS(`brightness` - 0.85) < 0.0001 THEN 1
                       ELSE `brightness`
                   END,
                   `updatedAt`
            FROM `widget_appearances`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `widget_appearances`")
        db.execSQL("ALTER TABLE `widget_appearances_v31` RENAME TO `widget_appearances`")
    }
}

private val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `widget_appearances_v32` (
                `variant` TEXT NOT NULL,
                `appWidgetId` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 0,
                `wallpaperUri` TEXT,
                `centerX` REAL NOT NULL DEFAULT 0.5,
                `centerY` REAL NOT NULL DEFAULT 0.5,
                `scale` REAL NOT NULL DEFAULT 1,
                `sourceWidth` INTEGER,
                `sourceHeight` INTEGER,
                `blurDp` REAL NOT NULL DEFAULT 0,
                `brightness` REAL NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`variant`, `appWidgetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `widget_appearances_v32`
            SELECT `variant`, `appWidgetId`, `enabled`, `wallpaperUri`, `centerX`, `centerY`, `scale`,
                   `sourceWidth`, `sourceHeight`,
                   CASE
                       WHEN `appWidgetId` = 0 AND `updatedAt` = 0 AND `wallpaperUri` IS NULL
                            AND `enabled` = 0 AND ABS(`blurDp` - 6) < 0.0001 THEN 0
                       ELSE `blurDp`
                   END,
                   `brightness`, `updatedAt`
            FROM `widget_appearances`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `widget_appearances`")
        db.execSQL("ALTER TABLE `widget_appearances_v32` RENAME TO `widget_appearances`")
    }
}

private val MIGRATION_32_34 = object : Migration(32, 34) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

private val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `courses_v34` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `teacher` TEXT,
                `location` TEXT,
                `weekday` INTEGER NOT NULL,
                `periods` TEXT NOT NULL,
                `weeks` TEXT NOT NULL,
                `weekParity` TEXT NOT NULL,
                `note` TEXT,
                `scheduleId` INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `courses_v34`
                (`id`, `name`, `teacher`, `location`, `weekday`, `periods`, `weeks`, `weekParity`, `note`, `scheduleId`)
            SELECT `id`, `name`, `teacher`, `location`, `weekday`, `periods`, `weeks`, `weekParity`, `note`, `scheduleId`
            FROM `courses`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `courses`")
        db.execSQL("ALTER TABLE `courses_v34` RENAME TO `courses`")
    }
}

private val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN homeChromeBlurScale REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN homeChromeSamplingScale REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("schedule_config", "alternateCardColorArgb")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCardColorArgb INTEGER NOT NULL DEFAULT 4293516543")
        }
        if (!db.hasColumn("schedule_config", "alternateCardAlpha")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCardAlpha REAL NOT NULL DEFAULT 1")
        }
        if (!db.hasColumn("schedule_config", "alternateCourseCardBlur")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCourseCardBlur REAL NOT NULL DEFAULT 18")
        }
        if (!db.hasColumn("schedule_config", "alternateCourseCardFontScale")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCourseCardFontScale REAL NOT NULL DEFAULT 1")
        }
        db.execSQL(
            """
            UPDATE schedule_config
            SET alternateCardColorArgb = cardColorArgb,
                alternateCardAlpha = cardAlpha,
                alternateCourseCardBlur = CASE
                    WHEN courseCardGlassEnabled = 1 THEN 18
                    WHEN courseCardBlur < 10 THEN courseCardBlur
                    ELSE 10
                END,
                alternateCourseCardFontScale = courseCardFontScale
            """.trimIndent()
        )
    }
}

private val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("courses", "customStartTime")) {
            db.execSQL("ALTER TABLE courses ADD COLUMN customStartTime TEXT")
        }
        if (!db.hasColumn("courses", "customEndTime")) {
            db.execSQL("ALTER TABLE courses ADD COLUMN customEndTime TEXT")
        }
        if (!db.hasColumn("courses", "customColorArgb")) {
            db.execSQL("ALTER TABLE courses ADD COLUMN customColorArgb INTEGER")
        }
    }
}

private val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("schedule_config", "courseCardColorMode")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardColorMode TEXT NOT NULL DEFAULT 'SOLID'")
        }
        if (!db.hasColumn("schedule_config", "courseCardPalette")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardPalette TEXT NOT NULL DEFAULT ''")
        }
        if (!db.hasColumn("schedule_config", "alternateCourseCardColorMode")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCourseCardColorMode TEXT NOT NULL DEFAULT 'SOLID'")
        }
        if (!db.hasColumn("schedule_config", "alternateCourseCardPalette")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCourseCardPalette TEXT NOT NULL DEFAULT ''")
        }
        if (!db.hasColumn("schedule_config", "weekCardHeightScale")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN weekCardHeightScale REAL NOT NULL DEFAULT 1")
        }
        if (!db.hasColumn("schedule_config", "weekCardCornerProgress")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN weekCardCornerProgress REAL NOT NULL DEFAULT 0.5")
        }
        db.execSQL(
            """
            UPDATE schedule_config
            SET courseCardColorMode = CASE WHEN cardColorArgb = 0 THEN 'COLORFUL' ELSE 'SOLID' END,
                alternateCourseCardColorMode = CASE WHEN alternateCardColorArgb = 0 THEN 'COLORFUL' ELSE 'SOLID' END,
                weekCardHeightScale = CASE
                    WHEN weekCardHeightDp IS NULL THEN 1
                    ELSE MIN(
                        1.45,
                        MAX(
                            0.72,
                            weekCardHeightDp / CASE
                                WHEN morningPeriodCount + noonPeriodCount + afternoonPeriodCount + eveningPeriodCount >= 10
                                    THEN 72.0
                                ELSE 80.0
                            END
                        )
                    )
                END
            """.trimIndent()
        )
        // Zero used to be the multicolour sentinel. The explicit mode now owns that meaning,
        // leaving cardColorArgb as a real editable seed for every mode.
        db.execSQL("UPDATE schedule_config SET cardColorArgb = 4292274687 WHERE cardColorArgb = 0")
        db.execSQL("UPDATE schedule_config SET alternateCardColorArgb = 4292274687 WHERE alternateCardColorArgb = 0")
    }
}

private val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("schedule_config", "courseCardOutlineLightEnabled")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardOutlineLightEnabled INTEGER NOT NULL DEFAULT 1")
        }
        if (!db.hasColumn("schedule_config", "courseCardRefractionStrength")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardRefractionStrength REAL NOT NULL DEFAULT 0.5")
        }
        if (!db.hasColumn("schedule_config", "courseCardGaussianBlurEnabled")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardGaussianBlurEnabled INTEGER NOT NULL DEFAULT 1")
        }
    }
}

internal val APP_DATABASE_MIGRATIONS: List<Migration> = listOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_34,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36,
    MIGRATION_36_37,
    MIGRATION_37_38,
    MIGRATION_38_39
)

private fun addWallpaperCropColumns(db: SupportSQLiteDatabase) {
    if (!db.hasColumn("schedule_config", "wallpaperPortraitCenterX")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperPortraitCenterX REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperPortraitCenterY")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperPortraitCenterY REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperPortraitScale")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperPortraitScale REAL DEFAULT 1")
    if (!db.hasColumn("schedule_config", "wallpaperLandscapeCenterX")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperLandscapeCenterX REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperLandscapeCenterY")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperLandscapeCenterY REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperLandscapeScale")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperLandscapeScale REAL DEFAULT 1")
    if (!db.hasColumn("schedule_config", "wallpaperSourceWidth")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperSourceWidth INTEGER")
    if (!db.hasColumn("schedule_config", "wallpaperSourceHeight")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperSourceHeight INTEGER")
}

internal fun createAppDatabase(
    context: Context,
    databaseName: String = "course_schedule.db"
): AppDatabase {
    repairDatabaseFileBeforeRoomOpen(context.getDatabasePath(databaseName))
    return Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(*APP_DATABASE_MIGRATIONS.toTypedArray())
        .build()
}

private fun repairDatabaseFileBeforeRoomOpen(path: File) {
    if (!path.exists()) return
    runCatching {
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val needsStructuralRepair = db.version < 26 ||
                !sqliteTableExists(db, "schedule_profiles") ||
                !sqliteTableExists(db, "schedule_config") ||
                !sqliteTableExists(db, "periods") ||
                !sqliteTableExists(db, "courses") ||
                !sqliteTableExists(db, "agent_daily_sessions") ||
                !sqliteTableExists(db, "agent_messages") ||
                !sqliteColumnExists(db, "courses", "scheduleId") ||
                (db.version >= 27 && !sqliteTableExists(db, "period_schemes")) ||
                (db.version >= 27 && !sqliteTableExists(db, "period_scheme_times")) ||
                (db.version >= 27 && !sqliteColumnExists(db, "schedule_config", "morningPeriodCount")) ||
                (db.version >= 27 && !sqliteColumnExists(db, "schedule_config", "afternoonPeriodCount")) ||
                (db.version >= 27 && !sqliteColumnExists(db, "schedule_config", "eveningPeriodCount")) ||
                (db.version >= 28 && !sqliteColumnExists(db, "schedule_config", "noonPeriodCount")) ||
                (db.version >= 28 && !sqliteColumnExists(db, "period_schemes", "noonStartTime")) ||
                (db.version >= 29 && !sqliteColumnExists(db, "schedule_config", "termState")) ||
                (db.version >= 30 && !sqliteTableExists(db, "widget_appearances")) ||
                (db.version >= 37 && !sqliteColumnExists(db, "courses", "customStartTime")) ||
                (db.version >= 37 && !sqliteColumnExists(db, "courses", "customEndTime")) ||
                (db.version >= 37 && !sqliteColumnExists(db, "courses", "customColorArgb")) ||
                (db.version >= 38 && !sqliteColumnExists(db, "schedule_config", "courseCardColorMode")) ||
                (db.version >= 38 && !sqliteColumnExists(db, "schedule_config", "courseCardPalette")) ||
                (db.version >= 38 && !sqliteColumnExists(db, "schedule_config", "alternateCourseCardColorMode")) ||
                (db.version >= 38 && !sqliteColumnExists(db, "schedule_config", "alternateCourseCardPalette")) ||
                (db.version >= 38 && !sqliteColumnExists(db, "schedule_config", "weekCardHeightScale")) ||
                (db.version >= 38 && !sqliteColumnExists(db, "schedule_config", "weekCardCornerProgress")) ||
                !sqliteColumnExists(db, "periods", "scheduleId") ||
                !sqliteColumnExists(db, "schedule_config", "dockAlignment") ||
                !sqliteColumnExists(db, "schedule_config", "notificationMode") ||
                !sqliteColumnExists(db, "schedule_config", "autoCheckUpdates")
            if (needsStructuralRepair) {
                repairSQLiteDatabase(db)
            } else {
                repairActiveScheduleProfiles(db)
            }
        }
    }
}

private fun repairSQLiteDatabase(db: SQLiteDatabase) {
    db.transaction {
        execSQL("CREATE TABLE IF NOT EXISTS schedule_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isActive INTEGER NOT NULL)")
        execSQL("INSERT INTO schedule_profiles (id, name, isActive) SELECT 1, '\u9ED8\u8BA4\u8BFE\u8868', 1 WHERE NOT EXISTS (SELECT 1 FROM schedule_profiles)")
        repairCoursesTable(this)
        repairScheduleConfigTable(this)
        repairPeriodsTable(this)
        repairAgentTables(this)
        repairPeriodSchemeTables(this)
        repairActiveScheduleProfiles(this)
        execSQL("DROP TABLE IF EXISTS room_master_table")
        setVersion(26)
    }
}

private fun repairCoursesTable(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "courses")) {
        db.execSQL("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, teacher TEXT, location TEXT, weekday INTEGER NOT NULL, periods TEXT NOT NULL, weeks TEXT NOT NULL, weekParity TEXT NOT NULL, note TEXT, customStartTime TEXT, customEndTime TEXT, customColorArgb INTEGER, scheduleId INTEGER NOT NULL DEFAULT 1)")
        return
    }
    if (!sqliteColumnExists(db, "courses", "scheduleId")) {
        db.execSQL("ALTER TABLE courses ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
    }
    ensureSqliteColumn(db, "courses", "customStartTime", "TEXT")
    ensureSqliteColumn(db, "courses", "customEndTime", "TEXT")
    ensureSqliteColumn(db, "courses", "customColorArgb", "INTEGER")
}

private fun repairPeriodSchemeTables(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "period_schemes")) {
        db.execSQL(
            "CREATE TABLE period_schemes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, isActive INTEGER NOT NULL, classDurationMinutes INTEGER NOT NULL, breakDurationMinutes INTEGER NOT NULL, morningStartTime TEXT NOT NULL, noonStartTime TEXT NOT NULL, afternoonStartTime TEXT NOT NULL, eveningStartTime TEXT NOT NULL, specialBreaksJson TEXT NOT NULL, overridesJson TEXT NOT NULL)"
        )
    } else if (!sqliteColumnExists(db, "period_schemes", "noonStartTime")) {
        db.execSQL("DROP TABLE IF EXISTS period_schemes_room_fix")
        db.execSQL(
            "CREATE TABLE period_schemes_room_fix (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, isActive INTEGER NOT NULL, classDurationMinutes INTEGER NOT NULL, breakDurationMinutes INTEGER NOT NULL, morningStartTime TEXT NOT NULL, noonStartTime TEXT NOT NULL, afternoonStartTime TEXT NOT NULL, eveningStartTime TEXT NOT NULL, specialBreaksJson TEXT NOT NULL, overridesJson TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO period_schemes_room_fix (id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes, morningStartTime, noonStartTime, afternoonStartTime, eveningStartTime, specialBreaksJson, overridesJson) SELECT id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes, morningStartTime, '12:00', afternoonStartTime, eveningStartTime, specialBreaksJson, overridesJson FROM period_schemes"
        )
        db.execSQL("DROP TABLE period_schemes")
        db.execSQL("ALTER TABLE period_schemes_room_fix RENAME TO period_schemes")
    }
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS period_scheme_times (schemeId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(schemeId, periodIndex))"
    )
}

private fun sqliteTableExists(db: SQLiteDatabase, table: String): Boolean {
    val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
    try {
        return cursor.moveToFirst()
    } finally {
        cursor.close()
    }
}

private fun sqliteColumnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
    if (!sqliteTableExists(db, table)) return false
    val cursor = db.rawQuery("PRAGMA table_info(`$table`)", null)
    try {
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    } finally {
        cursor.close()
    }
    return false
}

private fun ensureSqliteColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
    if (!sqliteColumnExists(db, table, column)) {
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
}

private fun repairScheduleConfigTable(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "schedule_config")) {
        db.execSQL(scheduleConfigCreateSql("schedule_config"))
        return
    }
    ensureSqliteColumn(db, "schedule_config", "termStartDate", "TEXT")
    ensureSqliteColumn(db, "schedule_config", "autoCurrentWeek", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "termState", "TEXT NOT NULL DEFAULT 'MANUAL'")
    ensureSqliteColumn(db, "schedule_config", "notificationsEnabled", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "notificationMode", "TEXT NOT NULL DEFAULT 'STANDARD'")
    ensureSqliteColumn(db, "schedule_config", "wallpaperUri", "TEXT")
    ensureSqliteColumn(db, "schedule_config", "wallpaperBlur", "REAL NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "wallpaperBrightness", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "wallpaperPortraitCenterX", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperPortraitCenterY", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperPortraitScale", "REAL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "wallpaperLandscapeCenterX", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperLandscapeCenterY", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperLandscapeScale", "REAL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "wallpaperSourceWidth", "INTEGER")
    ensureSqliteColumn(db, "schedule_config", "wallpaperSourceHeight", "INTEGER")
    ensureSqliteColumn(db, "schedule_config", "cardColorArgb", "INTEGER NOT NULL DEFAULT 4293516543")
    ensureSqliteColumn(db, "schedule_config", "cardAlpha", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "courseCardBlur", "REAL NOT NULL DEFAULT 18")
    ensureSqliteColumn(db, "schedule_config", "courseCardGlassEnabled", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "courseCardFontScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "courseCardColorMode", "TEXT NOT NULL DEFAULT 'SOLID'")
    ensureSqliteColumn(db, "schedule_config", "courseCardPalette", "TEXT NOT NULL DEFAULT ''")
    ensureSqliteColumn(db, "schedule_config", "alternateCardColorArgb", "INTEGER NOT NULL DEFAULT 4293516543")
    ensureSqliteColumn(db, "schedule_config", "alternateCardAlpha", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "alternateCourseCardBlur", "REAL NOT NULL DEFAULT 18")
    ensureSqliteColumn(db, "schedule_config", "alternateCourseCardFontScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "alternateCourseCardColorMode", "TEXT NOT NULL DEFAULT 'SOLID'")
    ensureSqliteColumn(db, "schedule_config", "alternateCourseCardPalette", "TEXT NOT NULL DEFAULT ''")
    ensureSqliteColumn(db, "schedule_config", "weekCardHeightDp", "REAL")
    ensureSqliteColumn(db, "schedule_config", "weekCardHeightScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "weekCardCornerProgress", "REAL NOT NULL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "homeTextLight", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "homeChromeBlurScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "homeChromeSamplingScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "followSystemDarkMode", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "darkMode", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "defaultWallpaperStyle", "TEXT NOT NULL DEFAULT 'KANBAN'")
    ensureSqliteColumn(db, "schedule_config", "hideEmptyWeekends", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "dockAlignment", "TEXT NOT NULL DEFAULT 'LEFT'")
    ensureSqliteColumn(db, "schedule_config", "defaultHomeMode", "TEXT NOT NULL DEFAULT 'WEEK'")
    ensureSqliteColumn(db, "schedule_config", "liveUpdateActionsEnabled", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "liveUpdateChipTextMode", "TEXT NOT NULL DEFAULT 'LOCATION'")
    ensureSqliteColumn(db, "schedule_config", "classDurationMinutes", "INTEGER NOT NULL DEFAULT 45")
    ensureSqliteColumn(db, "schedule_config", "breakDurationMinutes", "INTEGER NOT NULL DEFAULT 10")
    ensureSqliteColumn(db, "schedule_config", "hideFromRecents", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "autoCheckUpdates", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "morningPeriodCount", "INTEGER NOT NULL DEFAULT 4")
    ensureSqliteColumn(db, "schedule_config", "noonPeriodCount", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "afternoonPeriodCount", "INTEGER NOT NULL DEFAULT 4")
    ensureSqliteColumn(db, "schedule_config", "eveningPeriodCount", "INTEGER NOT NULL DEFAULT 4")
    db.execSQL(scheduleConfigCreateSql("schedule_config_room_fix"))
    db.execSQL(
        """
        INSERT OR REPLACE INTO schedule_config_room_fix (
            id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate, autoCurrentWeek, termState,
            notificationsEnabled, notificationMode, wallpaperUri, wallpaperBlur, wallpaperBrightness,
            wallpaperPortraitCenterX, wallpaperPortraitCenterY, wallpaperPortraitScale,
            wallpaperLandscapeCenterX, wallpaperLandscapeCenterY, wallpaperLandscapeScale,
            wallpaperSourceWidth, wallpaperSourceHeight,
            cardColorArgb, cardAlpha, courseCardBlur, courseCardGlassEnabled, courseCardFontScale,
            courseCardColorMode, courseCardPalette,
            alternateCardColorArgb, alternateCardAlpha, alternateCourseCardBlur, alternateCourseCardFontScale,
            alternateCourseCardColorMode, alternateCourseCardPalette,
            weekCardHeightDp, weekCardHeightScale, weekCardCornerProgress,
            homeTextLight, homeChromeBlurScale, homeChromeSamplingScale,
            followSystemDarkMode, darkMode, defaultWallpaperStyle, hideEmptyWeekends,
            dockAlignment, defaultHomeMode, liveUpdateActionsEnabled, liveUpdateChipTextMode,
            classDurationMinutes, breakDurationMinutes, hideFromRecents, autoCheckUpdates,
            morningPeriodCount, noonPeriodCount, afternoonPeriodCount, eveningPeriodCount
        )
        SELECT
            id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate, autoCurrentWeek, termState,
            notificationsEnabled, notificationMode, wallpaperUri, wallpaperBlur, wallpaperBrightness,
            wallpaperPortraitCenterX, wallpaperPortraitCenterY, wallpaperPortraitScale,
            wallpaperLandscapeCenterX, wallpaperLandscapeCenterY, wallpaperLandscapeScale,
            wallpaperSourceWidth, wallpaperSourceHeight,
            cardColorArgb, cardAlpha, courseCardBlur, courseCardGlassEnabled, courseCardFontScale,
            courseCardColorMode, courseCardPalette,
            alternateCardColorArgb, alternateCardAlpha, alternateCourseCardBlur, alternateCourseCardFontScale,
            alternateCourseCardColorMode, alternateCourseCardPalette,
            weekCardHeightDp, weekCardHeightScale, weekCardCornerProgress,
            homeTextLight, homeChromeBlurScale, homeChromeSamplingScale,
            followSystemDarkMode, darkMode, defaultWallpaperStyle, hideEmptyWeekends,
            dockAlignment, defaultHomeMode, liveUpdateActionsEnabled, liveUpdateChipTextMode,
            classDurationMinutes, breakDurationMinutes, hideFromRecents, autoCheckUpdates,
            morningPeriodCount, noonPeriodCount, afternoonPeriodCount, eveningPeriodCount
        FROM schedule_config
        """.trimIndent()
    )
    db.execSQL("DROP TABLE schedule_config")
    db.execSQL("ALTER TABLE schedule_config_room_fix RENAME TO schedule_config")
}

private fun scheduleConfigCreateSql(table: String): String =
    """
    CREATE TABLE IF NOT EXISTS $table (
        id INTEGER NOT NULL PRIMARY KEY,
        totalWeeks INTEGER NOT NULL,
        currentWeek INTEGER NOT NULL,
        notificationLeadMinutes INTEGER NOT NULL,
        termStartDate TEXT,
        autoCurrentWeek INTEGER NOT NULL,
        termState TEXT NOT NULL DEFAULT 'MANUAL',
        notificationsEnabled INTEGER NOT NULL,
        notificationMode TEXT NOT NULL,
        wallpaperUri TEXT,
        wallpaperBlur REAL NOT NULL,
        wallpaperBrightness REAL NOT NULL,
        wallpaperPortraitCenterX REAL DEFAULT 0.5,
        wallpaperPortraitCenterY REAL DEFAULT 0.5,
        wallpaperPortraitScale REAL DEFAULT 1,
        wallpaperLandscapeCenterX REAL DEFAULT 0.5,
        wallpaperLandscapeCenterY REAL DEFAULT 0.5,
        wallpaperLandscapeScale REAL DEFAULT 1,
        wallpaperSourceWidth INTEGER,
        wallpaperSourceHeight INTEGER,
        cardColorArgb INTEGER NOT NULL,
        cardAlpha REAL NOT NULL,
        courseCardBlur REAL NOT NULL,
        courseCardGlassEnabled INTEGER NOT NULL,
        courseCardFontScale REAL NOT NULL,
        courseCardColorMode TEXT NOT NULL DEFAULT 'SOLID',
        courseCardPalette TEXT NOT NULL DEFAULT '',
        alternateCardColorArgb INTEGER NOT NULL DEFAULT 4293516543,
        alternateCardAlpha REAL NOT NULL DEFAULT 1,
        alternateCourseCardBlur REAL NOT NULL DEFAULT 18,
        alternateCourseCardFontScale REAL NOT NULL DEFAULT 1,
        alternateCourseCardColorMode TEXT NOT NULL DEFAULT 'SOLID',
        alternateCourseCardPalette TEXT NOT NULL DEFAULT '',
        weekCardHeightDp REAL,
        weekCardHeightScale REAL NOT NULL DEFAULT 1,
        weekCardCornerProgress REAL NOT NULL DEFAULT 0.5,
        homeTextLight INTEGER NOT NULL,
        homeChromeBlurScale REAL NOT NULL DEFAULT 1,
        homeChromeSamplingScale REAL NOT NULL DEFAULT 1,
        followSystemDarkMode INTEGER NOT NULL,
        darkMode INTEGER NOT NULL,
        defaultWallpaperStyle TEXT NOT NULL,
        hideEmptyWeekends INTEGER NOT NULL,
        dockAlignment TEXT NOT NULL,
        defaultHomeMode TEXT NOT NULL,
        liveUpdateActionsEnabled INTEGER NOT NULL,
        liveUpdateChipTextMode TEXT NOT NULL,
        classDurationMinutes INTEGER NOT NULL,
        breakDurationMinutes INTEGER NOT NULL,
        hideFromRecents INTEGER NOT NULL,
        autoCheckUpdates INTEGER NOT NULL DEFAULT 1,
        morningPeriodCount INTEGER NOT NULL DEFAULT 4,
        noonPeriodCount INTEGER NOT NULL DEFAULT 0,
        afternoonPeriodCount INTEGER NOT NULL DEFAULT 4,
        eveningPeriodCount INTEGER NOT NULL DEFAULT 4
    )
    """.trimIndent()

private fun repairPeriodsTable(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "periods")) {
        db.execSQL("CREATE TABLE periods (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
        return
    }
    val hasScheduleId = sqliteColumnExists(db, "periods", "scheduleId")
    db.execSQL("CREATE TABLE IF NOT EXISTS periods_room_fix (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
    if (hasScheduleId) {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT scheduleId, periodIndex, startTime, endTime FROM periods")
    } else {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT 1, periodIndex, startTime, endTime FROM periods")
    }
    db.execSQL("DROP TABLE periods")
    db.execSQL("ALTER TABLE periods_room_fix RENAME TO periods")
}

private fun repairAgentTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_daily_sessions (scheduleId INTEGER NOT NULL, date TEXT NOT NULL, dailyPackJson TEXT NOT NULL, providerId TEXT NOT NULL, model TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, generationStatus TEXT NOT NULL, lastError TEXT, PRIMARY KEY(scheduleId, date))")
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, sessionDate TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)")
}

private fun repairActiveScheduleProfiles(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "schedule_profiles")) return
    val profileCount = db.rawQuery("SELECT COUNT(*) FROM schedule_profiles", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    if (profileCount == 0) {
        db.execSQL("INSERT INTO schedule_profiles (id, name, isActive) VALUES (1, '\u9ED8\u8BA4\u8BFE\u8868', 1)")
        return
    }
    val activeCount = db.rawQuery("SELECT COUNT(*) FROM schedule_profiles WHERE isActive = 1", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    if (activeCount == 1) return
    val canRankByCourses = sqliteTableExists(db, "courses") && sqliteColumnExists(db, "courses", "scheduleId")
    val selectionSql = if (canRankByCourses) {
        """
        SELECT p.id
        FROM schedule_profiles p
        LEFT JOIN courses c ON c.scheduleId = p.id
        GROUP BY p.id
        ORDER BY p.isActive DESC, COUNT(c.id) DESC, p.id DESC
        LIMIT 1
        """.trimIndent()
    } else {
        "SELECT id FROM schedule_profiles ORDER BY isActive DESC, id DESC LIMIT 1"
    }
    val selectedId = db.rawQuery(selectionSql, null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else null
    }
    selectedId?.let { id ->
        db.execSQL("UPDATE schedule_profiles SET isActive = CASE WHEN id = ? THEN 1 ELSE 0 END", arrayOf(id))
    }
}
