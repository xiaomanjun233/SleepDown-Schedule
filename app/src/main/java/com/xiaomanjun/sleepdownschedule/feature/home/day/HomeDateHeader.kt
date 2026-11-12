package com.xiaomanjun.sleepdownschedule.feature.home.day

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.get
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassHighlightAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassInnerShadowAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassLensAmount
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeHeaderGlassLensHeight
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSelectedAccentColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.HomeLightGlassSurfaceColor
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeBlur
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeChromeGlassSurfaceAlpha
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.excludeParentPullGesture
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekHeaderWeather
import com.xiaomanjun.sleepdownschedule.feature.home.week.HomeHeaderMotionMillis
import com.xiaomanjun.sleepdownschedule.feature.home.week.homeHeaderTextTransform
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassLens
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassPill
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalAdaptiveGlass
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import com.xiaomanjun.sleepdownschedule.toChineseWeekday
import com.xiaomanjun.sleepdownschedule.weekdayLabel
import java.time.LocalDate

private data class HomeTitleFrame(
    val mode: HomeMode,
    val caption: String,
    val dateLabel: String
)

@Composable
internal fun HomeDateTitle(
    state: AppState,
    mode: HomeMode,
    displayDate: LocalDate,
    displayWeek: Int,
    dateRangeEnd: LocalDate?,
    beforeScheduleTerm: Boolean,
    afterScheduleTerm: Boolean,
    onReturnToday: () -> Unit
) {
    val color = homeForegroundColor(state.config)
    val caption = when {
        beforeScheduleTerm -> "当前暂未开学"
        afterScheduleTerm -> "学期已结束"
        else -> "第${displayWeek}周"
    }
    val dateLabel = if (dateRangeEnd == null) {
        "${displayDate.monthValue}月${displayDate.dayOfMonth}日 周${weekdayLabel(displayDate.dayOfWeek.toChineseWeekday())}"
    } else {
        val endMonth = if (dateRangeEnd.month != displayDate.month) "${dateRangeEnd.monthValue}月" else ""
        "${displayDate.monthValue}月${displayDate.dayOfMonth}日–$endMonth${dateRangeEnd.dayOfMonth}日"
    }
    val title = updateTransition(HomeTitleFrame(mode, caption, dateLabel), label = "home-title")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = if (mode == HomeMode.Day) Modifier.excludeParentPullGesture()
            .clickable(interactionSource = interaction, indication = null, onClick = onReturnToday) else Modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            title.HomeTitleLine(HomeTitleFrame::caption, Modifier.weight(1f, fill = false)) { text ->
                HomeReadableText(
                    text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.Medium, color = color.copy(alpha = 0.68f),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                )
            }
            title.AnimatedVisibility(
                visible = { it.mode == HomeMode.Week },
                enter = fadeIn(tween(HomeHeaderMotionMillis)),
                exit = fadeOut(tween(HomeHeaderMotionMillis))
            ) {
                WeekHeaderWeather(color.copy(alpha = 0.68f))
            }
        }
        title.HomeTitleLine(HomeTitleFrame::dateLabel) { text ->
            HomeReadableText(
                text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 19.sp,
                    lineHeight = 21.sp
                ),
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The title rows share a transition; unchanged strings keep their node when the other row moves. */
@Composable
private fun Transition<HomeTitleFrame>.HomeTitleLine(
    label: (HomeTitleFrame) -> String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit
) {
    AnimatedContent(
        modifier = modifier,
        contentKey = label,
        contentAlignment = Alignment.CenterStart,
        transitionSpec = { homeHeaderTextTransform(TransformOrigin(0f, 0.5f)) }
    ) { frame ->
        Box(Modifier.padding(vertical = 2.dp)) { content(label(frame)) }
    }
}

@Composable
fun HomeModeSwitch(backdrop: Backdrop?, config: ScheduleConfigEntity, mode: HomeMode, onModeChange: (HomeMode) -> Unit) {
    val lightGlass = LocalAdaptiveGlass.current.lightGlass
    if (backdrop != null) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .width(108.dp)
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(width = 116.dp, height = 52.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { mode.ordinal },
                    onTabSelected = { index -> HomeMode.entries.getOrNull(index)?.let(onModeChange) },
                    backdrop = backdrop,
                    tabsCount = 2,
                    modifier = Modifier.size(width = 108.dp, height = 44.dp),
                    containerHeight = 44.dp,
                    indicatorHeight = 36.dp,
                    horizontalPadding = 4.dp,
                    blurRadius = homeChromeBlur(HomeHeaderGlassBlur, config),
                    containerAlpha = homeChromeGlassSurfaceAlpha(lightGlass),
                    lensHeight = HomeHeaderGlassLensHeight,
                    lensAmount = HomeHeaderGlassLensAmount,
                    indicatorWidthOverflow = 4.dp,
                    indicatorHeightOverflow = 2.dp,
                    indicatorLensHeight = HomeHeaderGlassLensHeight,
                    indicatorLensAmount = HomeHeaderGlassLensAmount,
                    officialHighlightAlpha = HomeHeaderGlassHighlightAlpha,
                    officialShadowAlpha = 0f,
                    officialInnerShadowAlpha = HomeHeaderGlassInnerShadowAlpha,
                    containerShadowEnabled = false,
                    indicatorShadowEnabled = false,
                    indicatorInnerShadowEnabled = false,
                    chromaticAberrationEnabled = true,
                    isLightThemeOverride = lightGlass,
                    lightContainerColor = HomeLightGlassSurfaceColor,
                    lightAccentColor = HomeLightGlassSelectedAccentColor,
                    useOfficialGlassParameters = true
                ) {
                    LiquidBottomTab(onClick = { onModeChange(HomeMode.Day) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_day_view), contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("\u65E5", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    LiquidBottomTab(onClick = { onModeChange(HomeMode.Week) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_week_view), contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("\u5468", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    } else {
        GlassPill(backdrop = null, config = config, modifier = Modifier.padding(end = 12.dp).height(44.dp).padding(4.dp)) {
            Row(Modifier.width(104.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeModePill(null, config, R.drawable.ic_day_view, "\u65E5") { onModeChange(HomeMode.Day) }
                HomeModePill(null, config, R.drawable.ic_week_view, "\u5468") { onModeChange(HomeMode.Week) }
            }
        }
    }
}

@Composable
fun HomeModePill(backdrop: Backdrop?, config: ScheduleConfigEntity, iconRes: Int, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .clip(Capsule())
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(34.dp)
            .width(52.dp),
        contentAlignment = Alignment.Center
    ) {
        if (pressed) {
            GlassLens(
                backdrop = backdrop,
                config = config,
            pressProgress = 1f,
            modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
