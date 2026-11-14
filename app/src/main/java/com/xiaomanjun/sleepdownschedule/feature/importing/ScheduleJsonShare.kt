package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.TimetableCatalog
import java.io.File

/** Shares the same canonical document that ICS embeds; readers import it without AI repair. */
object ScheduleJsonShare {
    fun writeShareFile(
        context: Context,
        scheduleName: String,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        courses: List<CourseEntity>,
        catalog: TimetableCatalog = TimetableCatalog()
    ): File {
        val directory = File(context.cacheDir, "shared_schedules").apply { mkdirs() }
        val safeName = scheduleName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "SleepDown课表" }
        return File(directory, "$safeName.json").apply {
            writeText(buildCanonicalScheduleJson(config, periods, courses, catalog), Charsets.UTF_8)
        }
    }
}
