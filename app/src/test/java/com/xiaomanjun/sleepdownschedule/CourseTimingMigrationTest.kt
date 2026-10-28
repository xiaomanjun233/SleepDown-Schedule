package com.xiaomanjun.sleepdownschedule

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.data.local.createAppDatabase
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseTimeLabel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class CourseTimingMigrationTest {
    // Keep native SQLite journal paths below Windows MAX_PATH in long Robolectric test names.
    private val backupDirectory by lazy { File(System.getProperty("java.io.tmpdir"),"db-upgrade-${java.util.UUID.randomUUID().toString().take(8)}").apply { mkdirs() } }
    private val context by lazy { object : android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getNoBackupFilesDir() = backupDirectory
    } }
    private val name = "course-timing-49.db"
    private var opened: AppDatabase? = null
    @After fun close() {
        opened?.close(); context.deleteDatabase(name)
        File(backupDirectory,"database-upgrades").also { directory -> directory.listFiles()?.forEach { it.delete() };directory.delete() }
        backupDirectory.delete()
    }

    private fun legacy(seed: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = listOf(File("schemas/com.xiaomanjun.sleepdownschedule.AppDatabase/49.json"),
            File("app/schemas/com.xiaomanjun.sleepdownschedule.AppDatabase/49.json")).first { it.exists() }
        val entities = JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            db.execSQL("INSERT INTO schedule_profiles(id,name,isActive) VALUES(7,'原课表',1)")
            db.execSQL("""INSERT INTO schedule_config(id,totalWeeks,currentWeek,notificationLeadMinutes,autoCurrentWeek,
                notificationsEnabled,notificationMode,wallpaperBlur,wallpaperBrightness,cardColorArgb,cardAlpha,
                courseCardBlur,courseCardGlassEnabled,courseCardFontScale,homeTextLight,followSystemDarkMode,
                darkMode,defaultWallpaperStyle,hideEmptyWeekends,dockAlignment,defaultHomeMode,
                liveUpdateActionsEnabled,liveUpdateChipTextMode,classDurationMinutes,breakDurationMinutes,
                hideFromRecents,autoCheckUpdates,morningPeriodCount,noonPeriodCount,afternoonPeriodCount,eveningPeriodCount)
                VALUES(7,20,1,10,0,1,'STANDARD',0,1,4293516543,1,18,1,1,0,1,0,'NONE',0,'CENTER','WEEK',1,'LOCATION',45,10,0,1,2,0,0,0)""")
            db.execSQL("INSERT INTO periods VALUES(1,'08:00','08:45',7)")
            db.execSQL("INSERT INTO periods VALUES(2,'08:55','09:40',7)")
            seed(db)
            db.version=49
        }
    }

    @Test fun migrationNormalizesCoursesPreservesIdsAndRetainsConsistentLegacyBackup() = runBlocking {
        legacy { db ->
            db.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId) VALUES(42,'普通课',1,'[1,2]','[1,2,3,4]','ODD',7)")
            db.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId,customStartTime,customEndTime) VALUES(43,'B楼实验',2,'[1,2]','[1]','ALL',7,'08:20','09:50')")
            db.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId) VALUES(44,'待确认课',3,'not-json','[1]','ALL',7)")
        }
        val db = createAppDatabase(context,name).also { opened=it }
        val courses = db.courseDao().getCourses(7).associateBy { it.id }
        assertEquals(setOf(42L,43L,44L),courses.keys)
        assertEquals(listOf(1,3),courses.getValue(42).weeks)
        assertEquals("08:20",courses.getValue(43).customStartTime)
        assertNotNull(courses.getValue(44).timingReviewReason)
        assertTrue(courses.getValue(44).legacyTimingSourceJson!!.contains("not-json"))
        db.openHelper.writableDatabase.query("SELECT type FROM sqlite_master WHERE name='periods'").use { assertTrue(it.moveToFirst()); assertEquals("view",it.getString(0)) }
        val backup = File(context.noBackupFilesDir,"database-upgrades").listFiles()!!.filter { it.name.startsWith(name+"-v49-") }.maxBy { it.lastModified() }
        SQLiteDatabase.openDatabase(backup.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(49,it.version)
            it.rawQuery("SELECT weeks FROM courses WHERE id=42",null).use { row -> assertTrue(row.moveToFirst()); assertEquals("[1,2,3,4]",row.getString(0)) }
        }
        val scheme = db.configDao().getConfig(7)!!.activePeriodSchemeId!!
        db.openHelper.writableDatabase.execSQL("UPDATE period_scheme_times SET startTime='08:10' WHERE schemeId=? AND periodIndex=1",arrayOf(scheme))
        val state = ScheduleRepository(db).activeSnapshot()
        assertEquals("08:10 - 09:40",courseTimeLabel(state.courses.single { it.id==42L },state.periods))
        assertEquals("08:20 - 09:50",courseTimeLabel(state.courses.single { it.id==43L },state.periods))
        val archive=com.xiaomanjun.sleepdownschedule.feature.backup.BackupExportService(context,db).export(
            com.xiaomanjun.sleepdownschedule.feature.backup.BackupSourceMetadata("2026-10-27T00:00:00Z","test",1,"com.xiaomanjun.sleepdownschedule",50,"Android"))
        val restored=com.xiaomanjun.sleepdownschedule.feature.backup.BackupCodec.decode(
            com.xiaomanjun.sleepdownschedule.feature.backup.BackupCodec.encode(archive))
        val pending=restored.data.schedules.single().courses.single { it.name=="待确认课" }
        assertNotNull(pending.timingReviewReason)
        assertTrue(pending.legacyTimingSourceJson!!.contains("not-json"))
    }

    @Test fun committedWalRowsAreIncludedInThePreUpgradeBackup() = runBlocking {
        legacy { }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath,null,SQLiteDatabase.OPEN_READWRITE).use { writer ->
            writer.enableWriteAheadLogging()
            writer.rawQuery("PRAGMA wal_autocheckpoint=0",null).use { it.moveToFirst() }
            writer.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId) VALUES(60,'WAL课程',1,'[1]','[1]','ALL',7)")
            assertEquals(49,writer.version)
            com.xiaomanjun.sleepdownschedule.data.local.backupBeforeTimingUpgrade(context,context.getDatabasePath(name))
            val backup=File(backupDirectory,"database-upgrades").listFiles()!!.single { it.name.endsWith(".db") }
            SQLiteDatabase.openDatabase(backup.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use { snapshot ->
                snapshot.rawQuery("SELECT name FROM courses WHERE id=60",null).use { row -> assertTrue(row.moveToFirst());assertEquals("WAL课程",row.getString(0)) }
            }
        }
    }

    @Test fun oldTimeAlignmentFreezesItsOriginalClockInsteadOfTheOverlappingGrid() = runBlocking {
        legacy { db ->
            db.execSQL("UPDATE schedule_config SET periodAlignmentMode='TIME' WHERE id=7")
            db.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId,originalPeriodTimes) VALUES(42,'旧时间课',1,'[1,2]','[1]','ALL',7,'1,08:20-09:05;2,09:15-10:00')")
        }
        val db = createAppDatabase(context,name).also { opened=it }
        val state = ScheduleRepository(db).activeSnapshot()
        assertEquals("08:20 - 10:00",courseTimeLabel(state.courses.single(),state.periods))
    }
}
