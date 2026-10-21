package com.xiaomanjun.sleepdownschedule.feature.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupRoomSnapshotReaderTest {
    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("period_scheme_library", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        for (id in listOf(7, 8)) {
            database.scheduleProfileDao().upsertProfile(ScheduleProfileEntity(id, "合成课表$id", id == 7))
        }
        for (id in listOf(1, 7, 8)) {
            database.configDao().upsertConfig(defaultConfig(id).copy(
                morningPeriodCount = 1, noonPeriodCount = 0, afternoonPeriodCount = 0, eveningPeriodCount = 0,
                activePeriodSchemeId = if (id == 1) 999 else 11,
                wallpaperUri = if (id == 1) "content://missing/old-wallpaper" else null
            ))
            database.configDao().upsertPeriods(listOf(PeriodEntity(1, "08:00", "08:45", id)))
            database.courseDao().insertCourse(CourseEntity(
                id = id.toLong(), name = "合成课程$id", teacher = "", location = "", weekday = 1,
                periods = listOf(1), weeks = listOf(1, 2), weekParity = WeekParity.ALL, note = "", scheduleId = id
            ))
            database.agentDao().upsertDailySession(AgentDailySessionEntity(
                scheduleId = id, date = "2026-10-12", dailyPackJson = "{}", providerId = "none", model = "",
                createdAt = 1, updatedAt = 1, generationStatus = "READY"
            ))
            database.agentDao().insertMessage(AgentMessageEntity(
                id = id.toLong(), scheduleId = id, sessionDate = "2026-10-12", role = "user",
                content = "合成记录$id", createdAt = 1, status = "READY"
            ))
        }
        for (id in listOf(11L, 12L)) {
            database.periodSchemeDao().upsertScheme(PeriodSchemeEntity(
                id = id, scheduleId = if (id == 12L) 1 else 0, name = "公共作息$id",
                publicId = "scheme-$id", morningPeriodCount = 1
            ))
            database.periodSchemeDao().upsertTimes(listOf(PeriodSchemeTimeEntity(id, 1, "08:00", "08:45")))
        }
        database.widgetAppearanceDao().upsert(WidgetAppearanceEntity(
            variant = "COURSES_LARGE", appWidgetId = WidgetDefaultAppearanceId, enabled = true
        ))
    }

    @After fun tearDown() { database.close() }

    @Test fun readerIncludesInactiveProfilesAndUnreferencedGlobalSchemesWithoutChangingSource() = runBlocking {
        val before = rawSnapshot()
        val exported = BackupRoomSnapshotReader(database).read()

        assertEquals(listOf(7, 8), exported.schedules.map { it.id })
        assertEquals(setOf(7, 8), exported.configs.map { it.id }.toSet())
        assertEquals(setOf(7, 8), exported.periods.map { it.scheduleId }.toSet())
        assertEquals(setOf(7, 8), exported.courses.map { it.scheduleId }.toSet())
        assertEquals(setOf(7, 8), exported.agentDailySessions.map { it.scheduleId }.toSet())
        assertEquals(setOf(7, 8), exported.agentMessages.map { it.scheduleId }.toSet())
        assertEquals(before.periodSchemes, exported.periodSchemes)
        assertEquals(before.periodSchemeTimes, exported.periodSchemeTimes)
        assertEquals(before.widgetAppearances, exported.widgetAppearances)
        assertEquals(before, rawSnapshot())
    }

    @Test fun completeExportServiceRoundTripsCurrentDataDespiteEveryTypeOfLeftoverRow() = runBlocking {
        val before = rawSnapshot()
        val archive = BackupExportService(context, database).export(metadata())
        val decoded = BackupCodec.decode(BackupCodec.encode(archive))

        assertEquals(listOf("合成课表7", "合成课表8"), decoded.data.schedules.map { it.name })
        assertEquals(listOf(true, false), decoded.data.schedules.map { it.isActive })
        assertEquals(2, decoded.data.schedules.sumOf { it.courses.size })
        assertEquals(2, decoded.data.schedules.sumOf { it.periods.size })
        assertEquals(2, decoded.data.schedules.sumOf { it.agentDailySessions.size })
        assertEquals(2, decoded.data.schedules.sumOf { it.agentMessages.size })
        assertEquals(setOf("scheme-11", "scheme-12"), decoded.data.sharedPeriodSchemes!!.map { it.publicId }.toSet())
        assertEquals(1, decoded.data.widgetAppearances.size)
        assertNotNull(decoded.preferences.aiImport)
        assertEquals(archive.preferences, decoded.preferences)
        assertTrue(decoded.assets.isEmpty())
        assertEquals(before, rawSnapshot())
    }

    @Test fun noProfilesDoesNotResurrectOldRowsButKeepsIndependentLibrary() = runBlocking {
        database.scheduleProfileDao().deleteAllProfiles()
        val before = rawSnapshot()
        val snapshot = BackupRoomSnapshotReader(database).read()
        assertTrue(snapshot.schedules.isEmpty())
        assertTrue(snapshot.configs.isEmpty())
        assertTrue(snapshot.periods.isEmpty())
        assertTrue(snapshot.courses.isEmpty())
        assertTrue(snapshot.agentDailySessions.isEmpty())
        assertTrue(snapshot.agentMessages.isEmpty())
        assertEquals(2, snapshot.periodSchemes.size)
        assertEquals(before, rawSnapshot())
    }

    private suspend fun rawSnapshot() = BackupRoomSnapshot(
        database.scheduleProfileDao().getProfiles(), database.configDao().getAllConfigs(),
        database.configDao().getAllPeriods(), database.courseDao().getAllCourses(),
        database.periodSchemeDao().getAllSchemes(), database.periodSchemeDao().getAllTimes(),
        database.agentDao().getAllDailySessions(), database.agentDao().getAllMessages(),
        database.widgetAppearanceDao().getAll()
    )

    private fun metadata() = BackupSourceMetadata("2026-10-12T00:00:00Z", "1.2.7_beta5", 34,
        "com.xiaomanjun.sleepdownschedule", APP_DATABASE_VERSION, "Android")
}
