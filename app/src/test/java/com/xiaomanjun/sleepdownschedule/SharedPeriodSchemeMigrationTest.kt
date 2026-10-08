package com.xiaomanjun.sleepdownschedule

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.xiaomanjun.sleepdownschedule.data.local.createAppDatabase
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

/** Opens legacy SQLite files through the complete migration and generated Room schema validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SharedPeriodSchemeMigrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "migration.db"
    private var opened: AppDatabase? = null

    @After
    fun tearDown() {
        opened?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun version44OpensWithMergedBindingsPreservedBellsAndRecoveredEmptySchemes() = runBlocking {
        createVersion44 { db ->
            for (id in 7..12) {
                insertConfig(db, id)
                db.execSQL("INSERT INTO schedule_profiles(id,name,isActive) VALUES(?, ?, ?)",
                    arrayOf<Any>(id, "课表$id", if (id == 7) 1 else 0))
            }
            insertScheme(db, 11, 7, "夏季", active = true)
            insertScheme(db, 21, 8, "同名时间的另一个标题", active = true)
            insertScheme(db, 12, 7, "间隔不同", breakMinutes = 15)
            insertScheme(db, 13, 7, "空时间待修复", breakMinutes = 17)
            insertScheme(db, 31, 9, "仅保留元数据的课表", breakMinutes = 19)
            insertScheme(db, 51, 11, "原来的时间", active = true, breakMinutes = 12)
            for (scheme in listOf(11, 21, 12, 51)) {
                db.execSQL("INSERT INTO period_scheme_times(schemeId,periodIndex,startTime,endTime) VALUES(?,1,'08:00','08:45')", arrayOf(scheme))
                db.execSQL("INSERT INTO period_scheme_times(schemeId,periodIndex,startTime,endTime) VALUES(?,2,'14:00','14:45')", arrayOf(scheme))
            }
            for (id in 7..8) {
                db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(1,'08:00','08:45',?)", arrayOf(id))
                db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(2,'14:00','14:45',?)", arrayOf(id))
            }
            db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(1,'10:00','10:45',10)")
            db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(1,'09:00','09:45',11)")
            db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(2,'15:00','15:45',11)")
            // No profile or config: migration must not discard this timeline.
            db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(1,'20:00','20:45',99)")
            db.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId,customStartTime,customEndTime) VALUES(42,'原始课程',1,'[1,2]','[1,3]','ODD',8,'08:02','14:43')")
        }
        val db = createAppDatabase(context, databaseName).also { opened = it }
        // This first access executes the registered migration and generated Room schema validation.
        val configs = db.configDao().getAllConfigs().associateBy { it.id }
        val schemes = db.periodSchemeDao()
        assertEquals(11L, configs.getValue(7).activePeriodSchemeId)
        assertEquals(11L, configs.getValue(8).activePeriodSchemeId)
        assertNull(schemes.getScheme(21))
        assertTrue(schemes.getTimes(21).isEmpty())
        assertEquals("legacy-11", schemes.getScheme(11)?.publicId)
        assertEquals("课表7（夏季）；课表8（同名时间的另一个标题）", schemes.getScheme(11)?.sourceScheduleName)
        assertEquals(15, schemes.getScheme(12)?.breakDurationMinutes)
        assertEquals(listOf("08:00", "14:00"), schemes.getTimes(13).map { it.startTime })
        assertEquals(12, schemes.getTimes(31).size)
        assertEquals(listOf(4, 0, 4, 4), schemes.getScheme(31)!!.let {
            listOf(it.morningPeriodCount, it.noonPeriodCount, it.afternoonPeriodCount, it.eveningPeriodCount)
        })
        assertEquals(31L, configs.getValue(9).activePeriodSchemeId)
        assertEquals("10:00", schemes.getTimes(configs.getValue(10).activePeriodSchemeId!!).single().startTime)
        val recovered = configs.getValue(11).activePeriodSchemeId!!
        assertNotEquals(51L, recovered)
        assertEquals("升级前作息", schemes.getScheme(recovered)?.name)
        assertEquals("09:00", schemes.getTimes(recovered).first().startTime)
        assertEquals("08:00", schemes.getTimes(51).first().startTime)
        assertEquals("09:00", db.configDao().getPeriods(11).first().startTime)
        assertEquals("20:00", schemes.getTimes(schemes.getAllSchemes().single { it.scheduleId == 99 }.id).single().startTime)
        assertNull(configs.getValue(12).activePeriodSchemeId)
        assertFalse(schemes.hasMigration("legacy-preferences-v1"))
        assertEquals("08:02", db.courseDao().getCourses(8).single().customStartTime)
        assertEquals("1,08:00-08:45;2,14:00-14:45", db.courseDao().getCourses(8).single().originalPeriodTimes)
        assertEquals(6, configs.getValue(8).currentWeek)
    }

    @Test fun version45CapturesOriginalClocksAndRetainsCourseMetadataOnReopen() = runBlocking {
        createLegacyDatabase(45) { db ->
            insertConfig(db, 7)
            db.execSQL("INSERT INTO schedule_profiles(id,name,isActive) VALUES(7,'旧课表',1)")
            db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(11,'18:00','18:45',7)")
            db.execSQL("INSERT INTO periods(periodIndex,startTime,endTime,scheduleId) VALUES(12,'18:55','19:40',7)")
            db.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId,customPeriodTimes) VALUES(42,'原始课程',1,'[11,12]','[1,3]','ODD',7,'12,20:01-20:29')")
        }
        val db = createAppDatabase(context, databaseName).also { opened = it }
        val row = db.courseDao().getCourses(7).single()
        assertEquals(listOf(11, 12), row.periods)
        assertEquals("11,18:00-18:45;12,18:55-19:40", row.originalPeriodTimes)
        assertEquals("12,20:01-20:29", row.customPeriodTimes)
        assertEquals(com.xiaomanjun.sleepdownschedule.model.PeriodAlignmentMode.INDEX, db.configDao().getConfig(7)!!.periodAlignmentMode)
        db.close()
        opened = createAppDatabase(context, databaseName)
        assertEquals(row, opened!!.courseDao().getCourses(7).single())
    }

    private fun createVersion44(seed: (SQLiteDatabase) -> Unit) = createLegacyDatabase(44, seed)

    @Test fun version46DefaultsReferenceCardsOffAndPreservesDataAcrossReopen() = runBlocking {
        createLegacyDatabase(46) { db ->
            insertConfig(db, 7)
            db.execSQL("INSERT INTO schedule_profiles(id,name,isActive) VALUES(7,'原课表',1)")
            db.execSQL("INSERT INTO courses(id,name,weekday,periods,weeks,weekParity,scheduleId) VALUES(42,'原课程',1,'[1]','[1,3]','ODD',7)")
        }
        val db = createAppDatabase(context, databaseName).also { opened = it }
        val config = db.configDao().getConfig(7)!!
        assertFalse(config.showNonCurrentWeekCourses)
        assertEquals("原课程", db.courseDao().getCourses(7).single().name)
        db.openHelper.writableDatabase.execSQL("UPDATE schedule_config SET showNonCurrentWeekCourses = 1 WHERE id = 7")
        db.close()
        opened = createAppDatabase(context, databaseName)
        assertTrue(opened!!.configDao().getConfig(7)!!.showNonCurrentWeekCourses)
    }

    private fun createLegacyDatabase(version: Int, seed: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(databaseName)
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        val schemaFile = listOf(File("schemas/com.xiaomanjun.sleepdownschedule.AppDatabase/$version.json"),
            File("app/schemas/com.xiaomanjun.sleepdownschedule.AppDatabase/$version.json")).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            seed(db)
            db.version = version
        }
    }

    private fun insertScheme(db: SQLiteDatabase, id: Int, scheduleId: Int, name: String,
        active: Boolean = false, breakMinutes: Int = 10) {
        db.execSQL("""
            INSERT INTO period_schemes(id,scheduleId,name,mode,isActive,classDurationMinutes,breakDurationMinutes,
                morningStartTime,noonStartTime,afternoonStartTime,eveningStartTime,specialBreaksJson,overridesJson)
            VALUES(?,?,?,'MANUAL',?,45,?,'08:00','12:00','14:00','19:00','{}','{}')
        """.trimIndent(), arrayOf<Any>(id, scheduleId, name, if (active) 1 else 0, breakMinutes))
    }

    private fun insertConfig(db: SQLiteDatabase, id: Int) {
        db.execSQL("""
            INSERT INTO schedule_config(id,totalWeeks,currentWeek,notificationLeadMinutes,autoCurrentWeek,
                notificationsEnabled,notificationMode,wallpaperBlur,wallpaperBrightness,cardColorArgb,cardAlpha,
                courseCardBlur,courseCardGlassEnabled,courseCardFontScale,homeTextLight,followSystemDarkMode,
                darkMode,defaultWallpaperStyle,hideEmptyWeekends,dockAlignment,defaultHomeMode,
                liveUpdateActionsEnabled,liveUpdateChipTextMode,classDurationMinutes,breakDurationMinutes,
                hideFromRecents,autoCheckUpdates,morningPeriodCount,noonPeriodCount,afternoonPeriodCount,eveningPeriodCount)
            VALUES(?,20,6,10,0,1,'STANDARD',0,1,4293516543,1,18,1,1,0,1,0,'NONE',0,'CENTER','WEEK',
                1,'LOCATION',45,10,0,1,1,0,1,0)
        """.trimIndent(), arrayOf(id))
    }
}
