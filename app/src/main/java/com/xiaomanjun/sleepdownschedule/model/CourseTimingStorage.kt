package com.xiaomanjun.sleepdownschedule.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** The stored course has exactly one timing mode. Grid coordinates never enter these rows. */
@Entity(tableName = "courses", indices = [Index("scheduleId"), Index("independentPeriodSchemeId")])
data class StoredCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String?,
    val location: String?,
    val weekday: Int,
    val note: String?,
    val customColorArgb: Long?,
    val scheduleId: Int,
    val timingMode: String,
    val independentPeriodSchemeId: Long?,
    val credit: Float?,
    val sourcePeriodTimes: String?,
    val reviewReason: String?,
    val legacySourceJson: String?,
    val paletteIndex: Int? = null
)

@Entity(tableName = "course_sections", primaryKeys = ["courseId", "number"], foreignKeys = [
    ForeignKey(entity = StoredCourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)
])
data class CourseSectionEntity(val courseId: Long, val number: Int)

@Entity(tableName = "course_weeks", primaryKeys = ["courseId", "week"], foreignKeys = [
    ForeignKey(entity = StoredCourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)
])
data class CourseWeekEntity(val courseId: Long, val week: Int)

/** In CUSTOM mode these are the complete actual intervals; otherwise they override named lessons. */
@Entity(tableName = "course_clock_segments", primaryKeys = ["courseId", "sequence"], foreignKeys = [
    ForeignKey(entity = StoredCourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)
])
data class CourseClockSegmentEntity(
    val courseId: Long,
    val sequence: Int,
    val sectionNumber: Int?,
    val startMinute: Int,
    val endMinute: Int
)

data class StoredCourseWithTiming(
    @Embedded val course: StoredCourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId") val sections: List<CourseSectionEntity>,
    @Relation(parentColumn = "id", entityColumn = "courseId") val weeks: List<CourseWeekEntity>,
    @Relation(parentColumn = "id", entityColumn = "courseId") val clocks: List<CourseClockSegmentEntity>
)
