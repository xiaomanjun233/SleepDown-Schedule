package com.xiaomanjun.sleepdownschedule.feature.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.ImportDraft

sealed interface HomeDialog {
    data object ImportSchedule : HomeDialog
    data object EduImport : HomeDialog
    data class ConfirmImport(val draft: ImportDraft, val returnDialog: HomeDialog? = ImportSchedule,
        val errorMessage: String? = null) : HomeDialog
    data class EditWallpaper(val uri: Uri, val entrySnapshot: Bitmap?) : HomeDialog
    data class EditCourse(val course: CourseEntity?, val targetWeek: Int? = null) : HomeDialog
    data class ApplyCourseEdit(
        val original: CourseEntity,
        val edited: CourseEntity,
        val targetWeek: Int,
        val originals: List<CourseEntity> = listOf(original),
        val editedCourses: List<CourseEntity> = listOf(edited)
    ) : HomeDialog
    data class ConfirmCourseConflicts(
        val original: CourseEntity,
        val edited: CourseEntity,
        val targetWeek: Int,
        val conflictWeeks: List<Int>,
        val singleWeekOnly: Boolean = false,
        val returnEdit: ApplyCourseEdit? = null
    ) : HomeDialog
    data class ApplyCourseDelete(val course: CourseEntity, val targetWeek: Int) : HomeDialog
}
