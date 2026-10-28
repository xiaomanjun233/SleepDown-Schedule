package com.xiaomanjun.sleepdownschedule.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import kotlinx.serialization.json.*

/** Migrates each original row, including malformed payloads, without inventing a course clock. */
internal val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN activeTimetableComboId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE period_scheme_times ADD COLUMN alias TEXT DEFAULT NULL")
        db.execSQL("CREATE TABLE timetable_combos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, baseSchemeId INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX index_timetable_combos_baseSchemeId ON timetable_combos(baseSchemeId)")
        db.execSQL("""CREATE TABLE timetable_date_rules (comboId INTEGER NOT NULL, position INTEGER NOT NULL, schemeId INTEGER NOT NULL,
            startDate TEXT NOT NULL, endDate TEXT NOT NULL, PRIMARY KEY(comboId,position),
            FOREIGN KEY(comboId) REFERENCES timetable_combos(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX index_timetable_date_rules_schemeId ON timetable_date_rules(schemeId)")
        db.execSQL("ALTER TABLE courses RENAME TO courses_legacy49")
        db.execSQL("""CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
            teacher TEXT, location TEXT, weekday INTEGER NOT NULL, note TEXT, customColorArgb INTEGER,
            scheduleId INTEGER NOT NULL, timingMode TEXT NOT NULL, independentPeriodSchemeId INTEGER,
            credit REAL, sourcePeriodTimes TEXT, reviewReason TEXT, legacySourceJson TEXT, paletteIndex INTEGER)""")
        db.execSQL("CREATE INDEX index_courses_scheduleId ON courses(scheduleId)")
        db.execSQL("CREATE INDEX index_courses_independentPeriodSchemeId ON courses(independentPeriodSchemeId)")
        db.execSQL("""CREATE TABLE course_sections (courseId INTEGER NOT NULL, number INTEGER NOT NULL,
            PRIMARY KEY(courseId,number), FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE course_weeks (courseId INTEGER NOT NULL, week INTEGER NOT NULL,
            PRIMARY KEY(courseId,week), FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE course_clock_segments (courseId INTEGER NOT NULL, sequence INTEGER NOT NULL,
            sectionNumber INTEGER, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL,
            PRIMARY KEY(courseId,sequence), FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        val timeAligned = buildSet {
            db.query("SELECT id FROM schedule_config WHERE periodAlignmentMode='TIME'").use { while (it.moveToNext()) add(it.getInt(0)) }
        }
        db.query("SELECT * FROM courses_legacy49 ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                fun string(name: String) = cursor.getColumnIndexOrThrow(name).let { if (cursor.isNull(it)) null else cursor.getString(it) }
                fun long(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                val raw = buildJsonObject { cursor.columnNames.forEachIndexed { index, name ->
                    put(name, if (cursor.isNull(index)) JsonNull else JsonPrimitive(cursor.getString(index)))
                } }.toString()
                fun numbers(name: String): List<Int> = Json.parseToJsonElement(requireNotNull(string(name))).jsonArray
                    .map { requireNotNull(it.jsonPrimitive.intOrNull) }.also { require(it.all { n -> n > 0 }) }
                val id = long("id")
                val basic = CourseEntity(id = id, name = string("name").orEmpty(), teacher = string("teacher"),
                    location = string("location"), weekday = long("weekday").toInt(), periods = emptyList(), weeks = emptyList(),
                    weekParity = WeekParity.ALL, note = string("note"), customColorArgb = string("customColorArgb")?.toLong(),
                    scheduleId = long("scheduleId").toInt(), legacyTimingSourceJson = raw)
                val converted = runCatching {
                    var source = basic.copy(periods = numbers("periods"), weeks = numbers("weeks"),
                        weekParity = WeekParity.valueOf(requireNotNull(string("weekParity"))),
                        customStartTime = string("customStartTime"), customEndTime = string("customEndTime"),
                        customPeriodTimes = string("customPeriodTimes"), originalPeriodTimes = string("originalPeriodTimes"))
                    if (source.scheduleId in timeAligned && source.customStartTime == null && source.customPeriodTimes.isNullOrBlank()) {
                        val original = parseCoursePeriodTimes(source.originalPeriodTimes)
                        require(original.map { it.index } == source.periods.distinct().sorted()) { "原始时间不完整，请确认课程时间" }
                        source = source.copy(customPeriodTimes = source.originalPeriodTimes)
                    }
                    require(source.periods.isNotEmpty() || source.customStartTime != null) { "原始课程没有节次或真实时间" }
                    require(source.weeks.isNotEmpty()) { "原始课程没有有效周次" }
                    source.toStoredTiming()
                }.getOrElse {
                    basic.copy(periods = runCatching { numbers("periods") }.getOrDefault(emptyList()),
                        weeks = runCatching { numbers("weeks") }.getOrDefault(emptyList()),
                        timingReviewReason = "升级前的课程时间需要确认；原始数据已完整保留").toStoredTiming()
                }
                val row = converted.course
                db.execSQL("""INSERT INTO courses(id,name,teacher,location,weekday,note,customColorArgb,scheduleId,
                    timingMode,independentPeriodSchemeId,credit,sourcePeriodTimes,reviewReason,legacySourceJson)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", arrayOf<Any?>(row.id,row.name,row.teacher,row.location,row.weekday,row.note,
                    row.customColorArgb,row.scheduleId,row.timingMode,row.independentPeriodSchemeId,row.credit,row.sourcePeriodTimes,
                    row.reviewReason,row.legacySourceJson))
                converted.sections.forEach { db.execSQL("INSERT INTO course_sections VALUES(?,?)", arrayOf<Any?>(id,it.number)) }
                converted.weeks.forEach { db.execSQL("INSERT INTO course_weeks VALUES(?,?)", arrayOf<Any?>(id,it.week)) }
                converted.clocks.forEach { db.execSQL("INSERT INTO course_clock_segments VALUES(?,?,?,?,?)", arrayOf<Any?>(id,it.sequence,it.sectionNumber,it.startMinute,it.endMinute)) }
            }
        }
        db.execSQL("DROP TABLE courses_legacy49")
        // Materialized periods used to compete with the shared library. Preserve an unbound
        // legacy table as its own template, then expose the binding through a read-only view.
        db.query("SELECT id FROM schedule_config c WHERE NOT EXISTS (SELECT 1 FROM period_scheme_times t WHERE t.schemeId=c.activePeriodSchemeId)").use { configs ->
            while (configs.moveToNext()) {
                val scheduleId = configs.getInt(0)
                db.execSQL("""INSERT INTO period_schemes(scheduleId,name,mode,isActive,classDurationMinutes,breakDurationMinutes,
                    morningStartTime,noonStartTime,afternoonStartTime,eveningStartTime,specialBreaksJson,overridesJson,
                    morningPeriodCount,noonPeriodCount,afternoonPeriodCount,eveningPeriodCount,publicId,sourceScheduleName)
                    SELECT 0,'升级前作息','MANUAL',0,classDurationMinutes,breakDurationMinutes,'08:00','12:00','14:00','19:00','{}','[]',
                    morningPeriodCount,noonPeriodCount,afternoonPeriodCount,eveningPeriodCount,'migration50-'||id,'' FROM schedule_config
                    WHERE id=? AND EXISTS (SELECT 1 FROM periods WHERE scheduleId=?)""", arrayOf<Any?>(scheduleId,scheduleId))
                db.execSQL("""INSERT INTO period_scheme_times(schemeId,periodIndex,startTime,endTime)
                    SELECT s.id,p.periodIndex,p.startTime,p.endTime FROM periods p JOIN period_schemes s ON s.publicId='migration50-'||p.scheduleId
                    WHERE p.scheduleId=?""", arrayOf<Any?>(scheduleId))
                db.execSQL("UPDATE schedule_config SET activePeriodSchemeId=(SELECT id FROM period_schemes WHERE publicId='migration50-'||?) WHERE id=?",
                    arrayOf<Any?>(scheduleId,scheduleId))
            }
        }
        db.execSQL("DROP TABLE periods")
        db.execSQL("CREATE VIEW `periods` AS SELECT t.periodIndex, t.startTime, t.endTime, c.id AS scheduleId, t.alias FROM schedule_config c JOIN period_scheme_times t ON t.schemeId = c.activePeriodSchemeId")
        db.execSQL("UPDATE schedule_config SET periodAlignmentMode='INDEX'")
    }
}
