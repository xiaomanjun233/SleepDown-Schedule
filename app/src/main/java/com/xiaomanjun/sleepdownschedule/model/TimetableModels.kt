package com.xiaomanjun.sleepdownschedule.model

import androidx.room.*

@Entity(tableName = "timetable_combos", indices = [Index("baseSchemeId")])
data class TimetableComboEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val baseSchemeId: Long)

@Entity(tableName = "timetable_date_rules", primaryKeys = ["comboId", "position"],
    foreignKeys = [ForeignKey(entity = TimetableComboEntity::class, parentColumns = ["id"], childColumns = ["comboId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("schemeId")])
data class TimetableDateRuleEntity(val comboId: Long, val position: Int, val schemeId: Long, val startDate: String, val endDate: String)

/** An immutable catalog travels with a schedule snapshot; consumers never query Room while rendering. */
data class TimetableCatalog(
    val schemes: List<PeriodSchemeEntity> = emptyList(),
    val times: List<PeriodSchemeTimeEntity> = emptyList(),
    val combos: List<TimetableComboEntity> = emptyList(),
    val rules: List<TimetableDateRuleEntity> = emptyList()
)

data class ImportedTimetable(
    val name: String, val periods: List<PeriodEntity>, val startDate: String? = null, val endDate: String? = null,
    val classDuration: Int = 45, val breakDuration: Int = 10
)

data class ImportedTimetablePlan(
    val comboName: String? = null,
    val dateTimetables: List<ImportedTimetable> = emptyList(),
    /** Independent course references use -(index + 1) until the import transaction assigns Room IDs. */
    val independentTimetables: List<ImportedTimetable> = emptyList()
)
