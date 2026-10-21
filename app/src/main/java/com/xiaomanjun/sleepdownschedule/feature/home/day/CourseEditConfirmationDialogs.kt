package com.xiaomanjun.sleepdownschedule.feature.home.day

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.core.graphics.get
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.effectiveCurrentWeek
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorHasOccurrence
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorOccurrenceLabel
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorScopeDescription
import com.xiaomanjun.sleepdownschedule.feature.course.editor.courseEditorSingleDestinationWeek

@Composable
fun ApplyCourseEditDialog(
    original: CourseEntity,
    edited: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSingle: () -> Unit,
    onAll: () -> Unit,
    onCancel: () -> Unit,
    targetWeek: Int = effectiveCurrentWeek(config),
    originals: List<CourseEntity> = listOf(original),
    editedCourses: List<CourseEntity> = listOf(edited),
    singleAllowed: Boolean = courseEditorHasOccurrence(original, targetWeek)
) {
    LiquidAlertDialog(
        title = "应用修改",
        message = buildString {
            append("“${original.name}”\n")
            if (singleAllowed) {
                val destination = courseEditorSingleDestinationWeek(original, edited, targetWeek) ?: targetWeek
                append("仅本次：${courseEditorOccurrenceLabel(original, targetWeek, config)}\n")
                append("改为：${courseEditorOccurrenceLabel(edited, destination, config)}。其他周次保持原安排。\n\n")
            }
            append("应用全部将修改以下原排课：\n${courseEditorScopeDescription(originals, config)}")
            append("\n\n保存后的排课：\n${courseEditorScopeDescription(editedCourses, config)}")
        },
        actions = buildList {
            if (singleAllowed) add(LiquidAlertAction("仅本次", LiquidAlertActionStyle.Primary, onClick = onSingle))
            add(LiquidAlertAction("应用全部", LiquidAlertActionStyle.Secondary, onClick = onAll))
            add(LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onCancel))
        },
        backdrop = backdrop,
        config = config,
        onDismissRequest = onCancel
    )
}

@Composable
fun CourseConflictRetentionDialog(
    course: CourseEntity,
    conflictWeeks: List<Int>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onKeepTemporarily: () -> Unit,
    onReturn: () -> Unit,
    retentionMessage: String? = null
) {
    val weekSummary = conflictWeeks.take(4).joinToString("、") { "第${it}周" } +
        if (conflictWeeks.size > 4) "等${conflictWeeks.size}周" else ""
    LiquidAlertDialog(
        title = "检测到课程冲突",
        message = retentionMessage
            ?: "“${course.name}”在${weekSummary}与其他课程重合。可以暂时保留；保存后会跳到首个冲突周，点课程上的“冲突”即可移到最近空位。",
        actions = listOf(
            LiquidAlertAction("暂时保留", LiquidAlertActionStyle.Primary, onClick = onKeepTemporarily),
            LiquidAlertAction("返回修改", LiquidAlertActionStyle.Cancel, onClick = onReturn)
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = onReturn
    )
}

@Composable
fun ApplyCourseDeleteDialog(
    course: CourseEntity,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onSingle: () -> Unit,
    onAll: () -> Unit,
    onCancel: () -> Unit,
    targetWeek: Int = effectiveCurrentWeek(config),
    courses: List<CourseEntity> = listOf(course)
) {
    LiquidAlertDialog(
        title = "删除课程",
        message = buildString {
            append("“${course.name}”\n")
            if (courseEditorHasOccurrence(course, targetWeek)) {
                append("仅本次：${courseEditorOccurrenceLabel(course, targetWeek, config)}。\n\n")
            }
            append("删除全部将删除以下排课：\n${courseEditorScopeDescription(courses, config)}")
        },
        actions = buildList {
            if (courseEditorHasOccurrence(course, targetWeek)) {
                add(LiquidAlertAction("仅删除本次", LiquidAlertActionStyle.Destructive, onClick = onSingle))
            }
            add(LiquidAlertAction("删除全部", LiquidAlertActionStyle.Destructive, onClick = onAll))
            add(LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = onCancel))
        },
        backdrop = backdrop,
        config = config,
        onDismissRequest = onCancel
    )
}
