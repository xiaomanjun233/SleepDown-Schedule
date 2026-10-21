package com.xiaomanjun.sleepdownschedule.feature.home.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.get
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.LocalStartupPhase
import com.xiaomanjun.sleepdownschedule.core.performance.StartupPhase
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.LocalEditingCourseId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.LocalSharedTransitionScope
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.sleepDownPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.core.ui.text.CourseCardText
import com.xiaomanjun.sleepdownschedule.core.ui.text.courseTextColorForPage
import com.xiaomanjun.sleepdownschedule.courseTimeLabel
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.domain.schedule.captureOriginalPeriodTimes
import com.xiaomanjun.sleepdownschedule.domain.schedule.isHiddenByPeriodAlignment
import com.xiaomanjun.sleepdownschedule.domain.schedule.projectCourseArrangement
import com.xiaomanjun.sleepdownschedule.feature.agent.homeCountdownShockwave
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseEditorDayAppearance
import com.xiaomanjun.sleepdownschedule.feature.course.editor.LocalCourseEditorFlightRegistry
import com.xiaomanjun.sleepdownschedule.feature.home.CourseAdjustmentBadge
import com.xiaomanjun.sleepdownschedule.feature.home.LocalAdjustedCourseEditor
import com.xiaomanjun.sleepdownschedule.feature.home.courseAdjustmentBadgeHeight
import com.xiaomanjun.sleepdownschedule.feature.home.courseBadgeContentInset
import com.xiaomanjun.sleepdownschedule.feature.home.courseBadgeCornerAnchor
import com.xiaomanjun.sleepdownschedule.feature.home.homeSwitchGroup
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.CourseBoundsSource
import com.xiaomanjun.sleepdownschedule.feature.home.overlay.courseRemovalMotion
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import com.xiaomanjun.sleepdownschedule.glass.ui.MutedCourseLightColor
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardBaseColor
import com.xiaomanjun.sleepdownschedule.glass.ui.courseCardUsesAssignments
import com.xiaomanjun.sleepdownschedule.hasCustomTime
import com.xiaomanjun.sleepdownschedule.parityLabel
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import java.time.LocalDate

@Composable
internal fun homeCourseTextColor(
    config: ScheduleConfigEntity,
    course: CourseEntity,
    pageForeground: ComposeColor,
    muted: Boolean
): ComposeColor {
    if (muted || !config.courseCardColoredTextEnabled) return pageForeground
    val seed = courseCardBaseColor(config, course)
    val hasWallpaper = config.hasAnyWallpaper()
    return remember(seed, hasWallpaper, pageForeground) {
        courseTextColorForPage(
            seed = seed,
            hasWallpaper = hasWallpaper,
            lightText = pageForeground.luminance() >= 0.5f
        )
    }
}

@Composable
fun DayTimelineCourse(course: CourseEntity, currentWeek: Int, periods: List<PeriodEntity>, cardColor: ComposeColor, backdrop: Backdrop?, config: ScheduleConfigEntity, onCourseClick: (CourseEntity, Int, Rect?) -> Unit, simultaneousCount: Int = 1, tabletFontScale: Float = 1f, readOnly: Boolean = false, muted: Boolean = false, occurrenceDate: LocalDate? = null, completed: Boolean = false) {
    val adjustedEditor = LocalAdjustedCourseEditor.current
    val subdued = muted || completed
    val pageForeground = homeForegroundColor(config)
    val foreground = homeCourseTextColor(config, course, pageForeground, subdued)
    Column(modifier = Modifier.homeSwitchGroup().homeCountdownShockwave(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CourseGlassCard(
            backdrop = backdrop,
            config = config,
            course = course,
            modifier = Modifier.wrapContentWidth(),
            shape = Capsule(),
            expandedOutlineLight = true,
            muted = subdued
        ) {
            CourseCardText(
                buildString {
                    append(courseTimeLabel(course, periods))
                    if (simultaneousCount > 1) append(" · 同时${simultaneousCount}门")
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                coloredText = !subdued && config.courseCardColoredTextEnabled,
                fontWeight = if (!subdued && config.courseCardColoredTextEnabled) FontWeight.Bold else null
            )
        }
        CourseCard(course, periods, showTime = false, showWeeks = false, cardColor = cardColor, backdrop = backdrop, config = config,
            onClick = { sourceBounds ->
                if (readOnly) occurrenceDate?.let { adjustedEditor?.invoke(course.id, it, sourceBounds) }
                else onCourseClick(course, currentWeek, sourceBounds)
            }, tabletFontScale = tabletFontScale, displayedWeek = currentWeek, muted = subdued,
            adjustmentLabel = if (readOnly) { if (muted) "停" else "补" } else null)
    }
}

internal val DayCourseCardCornerRadius = 24.dp

@Composable
internal fun DayCourseCardTextContent(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    showTime: Boolean,
    showWeeks: Boolean,
    textColor: ComposeColor,
    tabletFontScale: Float,
    config: ScheduleConfigEntity,
    muted: Boolean = false
) {
    val coloredText = !muted && config.courseCardColoredTextEnabled
    val renderedTextColor = homeCourseTextColor(config, course, textColor, muted)
    val coloredWeight = if (coloredText) FontWeight.Bold else null
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val safeTabletScale = tabletFontScale.coerceAtLeast(1f)
        val titleStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = MaterialTheme.typography.titleMedium.fontSize * safeTabletScale,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight * safeTabletScale
        )
        val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = MaterialTheme.typography.bodyMedium.fontSize * safeTabletScale,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * safeTabletScale
        )
        CourseCardText(course.name, style = titleStyle, color = renderedTextColor, coloredText = coloredText, fontWeight = coloredWeight)
        if (showTime) {
            CourseCardText(
                courseHomeTimeDetail(course, periods),
                coloredText = coloredText,
                style = bodyStyle,
                color = renderedTextColor.copy(alpha = 0.86f),
                fontWeight = coloredWeight
            )
        }
        if (!course.location.isNullOrBlank()) {
            CourseCardText("地点：" + course.location, style = bodyStyle, color = renderedTextColor.copy(alpha = 0.86f), coloredText = coloredText, fontWeight = if (coloredText) FontWeight.SemiBold else FontWeight.Normal)
        }
        if (!course.teacher.isNullOrBlank()) {
            CourseCardText("教师：" + course.teacher, style = bodyStyle, color = renderedTextColor.copy(alpha = 0.86f), coloredText = coloredText, fontWeight = if (coloredText) FontWeight.SemiBold else FontWeight.Normal)
        }
        if (showWeeks) {
            CourseCardText(
                "周次：" + course.weeks.joinToString(",") + " · " + parityLabel(course.weekParity),
                coloredText = coloredText,
                style = bodyStyle,
                color = renderedTextColor.copy(alpha = 0.86f),
                fontWeight = coloredWeight
            )
        }
        if (!course.note.isNullOrBlank()) {
            CourseCardText("备注：" + course.note, style = bodyStyle, color = renderedTextColor.copy(alpha = 0.86f), coloredText = coloredText, fontWeight = coloredWeight)
        }
    }
}

@Composable
internal fun DayCourseCardContent(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    showTime: Boolean,
    showWeeks: Boolean,
    tabletFontScale: Float,
    config: ScheduleConfigEntity,
    muted: Boolean,
    adjustmentLabel: String?
) {
    val badgeInset = if (adjustmentLabel != null) {
        (courseBadgeContentInset(with(LocalDensity.current) { courseAdjustmentBadgeHeight() }, DayCourseCardCornerRadius) - 16.dp)
            .coerceAtLeast(0.dp)
    } else 0.dp
    Box(Modifier.padding(bottom = badgeInset)) {
        DayCourseCardTextContent(
            course = course, periods = periods, showTime = showTime, showWeeks = showWeeks,
            textColor = homeForegroundColor(config), tabletFontScale = tabletFontScale,
            config = config, muted = muted
        )
    }
}

@Composable
fun CourseCard(course: CourseEntity, periods: List<PeriodEntity>, showTime: Boolean = true, showWeeks: Boolean = true, cardColor: ComposeColor = MaterialTheme.colorScheme.surfaceVariant, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig(), onClick: ((Rect?) -> Unit)? = null, enableSharedTransition: Boolean = true, tabletFontScale: Float = 1f, displayedWeek: Int? = null, muted: Boolean = false, adjustmentLabel: String? = null) {
    val resolvedCardColor = if (muted) MutedCourseLightColor else if (courseCardUsesAssignments(config)) courseCardBaseColor(config, course) else cardColor
    val ownBounds = remember(course.id) { arrayOfNulls<Rect>(1) }
    val visibleBounds = remember(course.id) { arrayOfNulls<Rect>(1) }
    val flightRegistry = LocalCourseEditorFlightRegistry.current
    val editId = LocalEditingCourseId.current
    val startupPhase = LocalStartupPhase.current
    val sharedScope = if (startupPhase == StartupPhase.FullQuality && enableSharedTransition && course.id > 0L) LocalSharedTransitionScope.current else null
    val boundsModifier = Modifier
        .onGloballyPositioned { coordinates ->
            // boundsInRoot intersects LazyColumn's clip. Morph the complete card, including
            // a partially scrolled title, rather than stretching its visible fragment.
            val topLeft = coordinates.localToRoot(Offset.Zero)
            val bottomRight = coordinates.localToRoot(
                Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
            )
            ownBounds[0] = Rect(topLeft, bottomRight)
            visibleBounds[0] = coordinates.boundsInRoot()
        }
    CourseBoundsSource(
        courseId = course.id,
        visible = editId != course.id,
        sharedScope = sharedScope,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedRectangle(DayCourseCardCornerRadius)
    ) { sharedModifier ->
    Box(
        modifier = sharedModifier.then(boundsModifier).then(
            if (displayedWeek != null) Modifier.courseRemovalMotion(course, displayedWeek, resolvedCardColor) else Modifier
        )
    ) {
        CourseGlassCard(
            backdrop = backdrop,
            config = config,
            course = course,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedRectangle(DayCourseCardCornerRadius),
            // Lazy rows are detached/reused while scrolling and paging. Share the wallpaper
            // blur, but record each visible row's current sample on draw instead of retaining
            // its position-dependent display list across those lifecycle transitions.
            expandedOutlineLight = true,
            muted = muted,
            onClick = if (onClick != null) ({
                val bounds = ownBounds[0]
                val visible = visibleBounds[0]
                if (bounds != null && visible != null) flightRegistry?.captureSource(
                    bounds, visible,
                    CourseEditorDayAppearance(course, periods, showTime, showWeeks, tabletFontScale, muted,
                        adjustmentLabel, cornerRadius = DayCourseCardCornerRadius, backdrop = backdrop)
                )
                onClick(bounds)
            }) else null
        ) {
            DayCourseCardContent(
                course = course,
                periods = periods,
                showTime = showTime,
                showWeeks = showWeeks,
                tabletFontScale = tabletFontScale,
                config = config,
                muted = muted,
                adjustmentLabel = adjustmentLabel
            )
        }
        adjustmentLabel?.let {
            CourseAdjustmentBadge(it, backdrop, config,
                Modifier.align(Alignment.BottomEnd).courseBadgeCornerAnchor(DayCourseCardCornerRadius).zIndex(7f))
        }
    }
    }
}

@Composable
fun ImportPreviewCourseCard(
    course: CourseEntity,
    periods: List<PeriodEntity>,
    config: ScheduleConfigEntity = defaultConfig()
) {
    val effective = remember(course, periods, config.periodAlignmentMode) {
        projectCourseArrangement(captureOriginalPeriodTimes(course, periods), config, periods)
    }
    // Preview lives on the settings page, without the timetable's wallpaper. Resolve one
    // page ink, just like the live cards; individual seed colors must not invert labels.
    val visualConfig = settingsVisualConfig(config)
    val textColor = homeCourseTextColor(visualConfig, course, sleepDownPanelForegroundColor(visualConfig), false)
    val coloredText = visualConfig.courseCardColoredTextEnabled
    CourseGlassCard(
        backdrop = null,
        config = visualConfig,
        course = course,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedRectangle(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CourseCardText(course.name, style = MaterialTheme.typography.titleMedium, color = textColor, coloredText = coloredText)
            CourseCardText(
                text = "周${weekdayLabel(course.weekday)} · " +
                    if (effective.isHiddenByPeriodAlignment()) "暂不显示 · 原始第 ${course.periods.joinToString("、")} 节"
                    else courseHomeTimeDetail(effective, periods),
                color = textColor, coloredText = coloredText
            )
            if (!course.location.isNullOrBlank()) CourseCardText("地点：" + course.location, color = textColor, coloredText = coloredText)
            if (!course.teacher.isNullOrBlank()) CourseCardText("教师：" + course.teacher, color = textColor, coloredText = coloredText)
            CourseCardText("周次：" + course.weeks.joinToString(",") + " · " + parityLabel(course.weekParity), color = textColor, coloredText = coloredText)
            if (!course.note.isNullOrBlank()) CourseCardText("备注：" + course.note, color = textColor, coloredText = coloredText)
        }
    }
}

private fun courseHomeTimeDetail(course: CourseEntity, periods: List<PeriodEntity>): String =
    if (course.hasCustomTime()) {
        courseTimeLabel(course, periods)
    } else {
        courseTimeLabel(course, periods) + " · 第 " + course.periods.joinToString(",") + " 节"
    }
