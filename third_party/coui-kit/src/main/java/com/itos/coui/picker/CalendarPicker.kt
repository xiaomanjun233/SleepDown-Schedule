package com.itos.coui.picker

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import com.itos.coui.theme.CouiIcon as Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.theme.CouiTheme
import com.itos.coui.R
import com.itos.coui.shape.CouiCapsuleShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

/**
 * 日历 App `COUICalendarPicker` 的 Compose 复刻。
 *
 * 头部、月网格、三列年月日滚轮、选中与翻页动画都来自 Calendar 17.3.70；
 * 翻月箭头只改变浏览月份，点选日期或滚动展开后的日期滚轮才提交受控值。
 * 月份浏览不读取业务数据，宿主持有选中日期与范围。
 */
@Composable
fun CalendarPicker(
    modifier: Modifier = Modifier,
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    dark: Boolean = CouiTheme.isDark,
    accent: Color = CouiTheme.colorSchemeFor(dark).accent,
    previousDescription: String = stringResource(R.string.coui_picker_calendar_previous_month),
    nextDescription: String = stringResource(R.string.coui_picker_calendar_next_month),
    changeMonthDescription: String = stringResource(R.string.coui_picker_calendar_change_mode),
    yearLabel: String = stringResource(R.string.coui_picker_calendar_year),
    monthLabel: String = stringResource(R.string.coui_picker_calendar_month),
    dayLabel: String = stringResource(R.string.coui_picker_calendar_day),
    minDate: LocalDate = LocalDate.of(1900, 1, 1),
    maxDate: LocalDate = LocalDate.of(2100, 12, 31),
    today: LocalDate = LocalDate.now(),
    colors: CouiCalendarColors = CouiCalendarDefaults.colors(dark),
) {
    require(minDate <= maxDate && value in minDate..maxDate)
    val locale = LocalConfiguration.current.locales[0]
    val medium = remember { FontFamily(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)) }
    val first = YearMonth.from(minDate)
    val count = (ChronoUnit.MONTHS.between(first, YearMonth.from(maxDate)) + 1).toInt()
    val pager = rememberPagerState(
        initialPage = ChronoUnit.MONTHS.between(first, YearMonth.from(value)).toInt(),
        pageCount = { count },
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var pageAtDown by remember { mutableIntStateOf(pager.currentPage) }
    var headerPage by remember { mutableIntStateOf(pager.currentPage) }
    val pageFling = remember(pager, density) { CouiCalendarFlingBehavior(pager, density) { pageAtDown } }
    var editingMonth by rememberSaveable { mutableStateOf(false) }
    var closingEditor by remember { mutableStateOf(false) }
    var displayEditor by remember { mutableStateOf(false) }
    var returnVersion by remember { mutableIntStateOf(0) }
    var consumedReturn by remember { mutableIntStateOf(0) }
    // 记录请求的来源和目标，宿主尚未接受时不能提前丢失点击来源。
    var pendingGridSelection by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    val selection = remember(scope) { CouiCalendarSelectionMotion(scope, value) }
    DisposableEffect(selection) { onDispose { selection.dispose() } }
    // SideEffect 只在已接受的宿主 value 上转交选择动画；编辑区淡出时保留旧网格。
    SideEffect {
        if (!editingMonth && !displayEditor) {
            val returning = consumedReturn != returnVersion
            if (returning) consumedReturn = returnVersion
            val submitted = pendingGridSelection
            val acceptedClick = submitted != null &&
                submitted.first == selection.selectedDate && submitted.second == value
            if (selection.selectedDate != value || returning) {
                selection.update(value, returning || acceptedClick)
            }
            // 异步宿主回调未接受时保留请求；被接受或被其他值取代后立即清除。
            if (submitted != null && (value != submitted.first || returning)) {
                pendingGridSelection = null
            }
        } else {
            pendingGridSelection = null
        }
    }
    val current = first.plusMonths(headerPage.toLong())
    val foreground = colors.foreground
    val secondary = colors.secondary
    val headerFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale)
    }
    val fullDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    val arrowAlpha = remember { Animatable(1f) }

    fun pageOf(date: LocalDate): Int = ChronoUnit.MONTHS.between(first, YearMonth.from(date)).toInt()

    fun toggleEditor() {
        if (editingMonth) {
            closingEditor = true
            pendingGridSelection = null
            // 未经宿主接受的候选不得影响标题及返回月份。
            headerPage = pageOf(value)
            returnVersion++
            editingMonth = false
        } else {
            pendingGridSelection = null
            closingEditor = false
            editingMonth = true
        }
    }

    LaunchedEffect(editingMonth) {
        if (editingMonth) {
            delay(120)
            displayEditor = true
        } else {
            displayEditor = false
        }
    }
    LaunchedEffect(editingMonth) {
        arrowAlpha.animateTo(
            if (editingMonth) 0f else 1f,
            tween(
                if (editingMonth) 150 else 280,
                easing = if (editingMonth) CalendarEase else CouiCalendarPageEasing,
            ),
        )
    }
    LaunchedEffect(value, minDate, maxDate, editingMonth, displayEditor, closingEditor) {
        if (!editingMonth && !displayEditor) {
            val target = pageOf(value)
            headerPage = target
            if (closingEditor) {
                // 关闭编辑器时须终止旧分页动画，即使旧 currentPage 已等于目标。
                pager.scrollToPage(target)
                closingEditor = false
            } else if (pager.currentPage != target || pager.targetPage != target) {
                pager.animateScrollToPage(target, animationSpec = tween(300, easing = CouiCalendarPageEasing))
            }
        }
    }
    LaunchedEffect(pager.isScrollInProgress) {
        if (!pager.isScrollInProgress) headerPage = pager.currentPage
    }

    Column(modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(32.dp)) {
            CalendarArrow(
                modifier = Modifier.align(Alignment.CenterStart),
                description = previousDescription,
                next = false,
                enabled = !editingMonth && headerPage > 0,
                colors = colors,
                alpha = if (headerPage > 0) arrowAlpha.value else 0f,
            ) {
                scope.launch {
                    val target = (headerPage - 1).coerceAtLeast(0)
                    headerPage = target
                    pager.animateScrollToPage(target, animationSpec = tween(300, easing = CouiCalendarPageEasing))
                }
            }
            val headerMonth = if (editingMonth || closingEditor) YearMonth.from(value) else current
            val titleInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier.align(Alignment.Center)
                    .clickable(interactionSource = titleInteraction, indication = null, role = Role.Button) {
                        toggleEditor()
                    }
                    .semantics { contentDescription = changeMonthDescription },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    headerFormatter.format(headerMonth.atDay(1)),
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = medium,
                        fontWeight = FontWeight.Normal,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        color = if (editingMonth) accent else foreground,
                    ),
                    maxLines = 2,
                )
                Box(
                    Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 原厂 COUIRotateView 默认 rotateType=0：按状态切换两套矢量图标。
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = if (editingMonth) CalendarCollapse else CalendarExpand,
                        contentDescription = null,
                        tint = if (editingMonth) accent else secondary,
                    )
                }
            }
            CalendarArrow(
                modifier = Modifier.align(Alignment.CenterEnd),
                description = nextDescription,
                next = true,
                enabled = !editingMonth && headerPage < count - 1,
                colors = colors,
                alpha = if (headerPage < count - 1) arrowAlpha.value else 0f,
            ) {
                scope.launch {
                    val target = (headerPage + 1).coerceAtMost(count - 1)
                    headerPage = target
                    pager.animateScrollToPage(target, animationSpec = tween(300, easing = CouiCalendarPageEasing))
                }
            }
        }
        AnimatedContent(
            targetState = displayEditor,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                fadeIn(tween(280, easing = LinearEasing)) togetherWith
                    fadeOut(tween(150, easing = CalendarEase))
            },
            label = "年月切换",
        ) { editing ->
            if (editing) {
                Box(Modifier.fillMaxWidth().height(CalendarBodyHeight), contentAlignment = Alignment.Center) {
                    CalendarDateEditor(
                        date = value,
                        minDate = minDate,
                        maxDate = maxDate,
                        locale = locale,
                        yearLabel = yearLabel,
                        monthLabel = monthLabel,
                        dayLabel = dayLabel,
                        dark = dark,
                        colors = colors,
                        onChange = onValueChange,
                    )
                }
            } else {
                Box(Modifier.fillMaxWidth().height(CalendarBodyHeight)) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize().pointerInput(pager) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                pageAtDown = pager.currentPage
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })
                            }
                        },
                        flingBehavior = pageFling,
                    ) { page ->
                        CalendarMonthPage(
                            month = first.plusMonths(page.toLong()),
                            selection = selection,
                            today = today,
                            minDate = minDate,
                            maxDate = maxDate,
                            locale = locale,
                            colors = colors,
                            accent = accent,
                            medium = medium,
                            fullDateFormatter = fullDateFormatter,
                            onValueChange = { date ->
                                pendingGridSelection = value to date
                                onValueChange(date)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarArrow(
    modifier: Modifier = Modifier,
    description: String,
    next: Boolean,
    enabled: Boolean,
    colors: CouiCalendarColors,
    alpha: Float,
    click: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.size(32.dp).clip(CouiCapsuleShape)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = click,
            )
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(24.dp).graphicsLayer { if (next) rotationZ = 180f },
            imageVector = CalendarChevron,
            contentDescription = description,
            tint = colors.foreground,
        )
    }
}

@Composable
private fun CalendarMonthPage(
    month: YearMonth,
    selection: CouiCalendarSelectionMotion,
    today: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    locale: Locale,
    colors: CouiCalendarColors,
    accent: Color,
    medium: FontFamily,
    fullDateFormatter: DateTimeFormatter,
    onValueChange: (LocalDate) -> Unit,
) {
    val firstDay = Calendar.SUNDAY
    val startDay = (firstDay + 5) % 7 + 1
    val start = (month.atDay(1).dayOfWeek.value - startDay + 7) % 7
    val base = month.atDay(1).minusDays(start.toLong())
    val sixWeeks = month.lengthOfMonth() + start > 35
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            repeat(7) { col ->
                val day = base.plusDays(col.toLong())
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    BasicText(
                        day.dayOfWeek.getDisplayName(DateTextStyle.NARROW, locale),
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = medium,
                            fontWeight = FontWeight.Normal,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            textAlign = TextAlign.Center,
                            color = colors.weekday,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        repeat(if (sixWeeks) 6 else 5) { row ->
            Row(Modifier.fillMaxWidth().height(if (sixWeeks) 40.dp else 48.dp)) {
                repeat(7) { col ->
                    val date = base.plusDays((row * 7 + col).toLong())
                    val inMonth = date.monthValue == month.monthValue
                    val withinRange = date in minDate..maxDate
                    CalendarDayCell(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        date = date,
                        inMonth = inMonth,
                        selected = inMonth && date == selection.selectedDate,
                        old = inMonth && date == selection.oldDate,
                        selection = selection,
                        withinRange = withinRange,
                        today = inMonth && date == today,
                        colors = colors,
                        accent = accent,
                        medium = medium,
                        locale = locale,
                        fullDateFormatter = fullDateFormatter,
                    ) { onValueChange(date) }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier = Modifier,
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    old: Boolean,
    selection: CouiCalendarSelectionMotion,
    withinRange: Boolean,
    today: Boolean,
    colors: CouiCalendarColors,
    accent: Color,
    medium: FontFamily,
    locale: Locale,
    fullDateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val foreground = colors.foreground
    val textColor = when {
        selected -> colors.onAccent
        !inMonth -> colors.hint
        today -> accent
        withinRange -> foreground
        else -> foreground.copy(alpha = foreground.alpha * .5f)
    }
    val selectedProgress = if (selected) selection.enter else 1f
    val selectedScale = .8f + .2f * selectedProgress
    Box(
        modifier.selectable(
            selected = selected,
            interactionSource = source,
            indication = null,
            enabled = withinRange,
            role = Role.RadioButton,
            onClick = { if (!selected) onClick() },
        ).semantics { contentDescription = date.format(fullDateFormatter) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).drawBehind {
                val radius = size.minDimension / 2
                if (selected) {
                    // Compose 的 onClick 可能先于 PressInteraction.Release 更新，
                    // 新选中帧不能被残留 pressed 直接跳到 176/255 再回落到 0。
                    val alpha = if (pressed && selectedProgress >= 1f) {
                        176f / 255f
                    } else {
                        (selectedProgress * 255f).toInt() / 255f
                    }
                    drawCircle(accent.copy(alpha = alpha), radius * selectedScale)
                } else {
                    // 原厂 highlighted 与 old selector 互斥，不能叠两层造成瞬时色闪。
                    if (pressed) drawCircle(colors.highlight, radius)
                    else if (old && withinRange && selection.exit > 0f) {
                        val oldAlpha = (selection.exit * 255f).toInt() / 255f
                        drawCircle(accent.copy(alpha = oldAlpha), radius)
                    }
                }
                if (today && !selected) {
                    drawCircle(accent, radius, style = Stroke(1.8.dp.toPx()))
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                String.format(locale, "%d", date.dayOfMonth),
                modifier = if (selected && selectedProgress < 1f) {
                    // 按反馈让文字同圈缩放；原厂 drawText 固定字号，文字不额外做 alpha 动画。
                    Modifier.graphicsLayer {
                        scaleX = selectedScale
                        scaleY = selectedScale
                    }
                } else Modifier,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = medium,
                    fontWeight = FontWeight.Normal,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    textAlign = TextAlign.Center,
                    color = textColor,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CalendarDateEditor(
    date: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    locale: Locale,
    yearLabel: String,
    monthLabel: String,
    dayLabel: String,
    dark: Boolean,
    colors: CouiCalendarColors,
    onChange: (LocalDate) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "yyyyMMMMdd") }
    val order = remember(pattern, layoutDirection) {
        val forward = pattern.toList().filter { it in "yMd" }.distinct()
        if (layoutDirection == LayoutDirection.Rtl) forward.reversed() else forward
    }
    val chinese = locale.language == "zh"
    val months = remember(locale) { DateFormatSymbols.getInstance(locale).shortMonths }
    var pendingDate by remember { mutableStateOf(date) }
    // 每次重组与外部已接受日期对账；同一帧的多列回调先在本地合成。
    SideEffect { if (pendingDate != date) pendingDate = date }
    val year = date.year
    val month = date.monthValue
    val minMonth = if (year == minDate.year) minDate.monthValue else 1
    val maxMonth = if (year == maxDate.year) maxDate.monthValue else 12
    val minDay = if (year == minDate.year && month == minDate.monthValue) minDate.dayOfMonth else 1
    val maxDay = if (year == maxDate.year && month == maxDate.monthValue) {
        maxDate.dayOfMonth
    } else {
        YearMonth.of(year, month).lengthOfMonth()
    }

    fun submit(next: LocalDate) {
        val candidate = next.coerceIn(minDate, maxDate)
        pendingDate = candidate
        onChange(candidate)
    }

    fun submitYear(nextYear: Int) {
        val base = pendingDate
        val y = nextYear.coerceIn(minDate.year, maxDate.year)
        val m = base.monthValue.coerceIn(
            if (y == minDate.year) minDate.monthValue else 1,
            if (y == maxDate.year) maxDate.monthValue else 12,
        )
        val d = base.dayOfMonth.coerceAtMost(YearMonth.of(y, m).lengthOfMonth())
        submit(LocalDate.of(y, m, d))
    }

    fun submitMonth(nextMonth: Int) {
        val base = pendingDate
        val m = nextMonth.coerceIn(1, 12)
        val d = base.dayOfMonth.coerceAtMost(YearMonth.of(base.year, m).lengthOfMonth())
        submit(LocalDate.of(base.year, m, d))
    }

    fun submitDay(nextDay: Int) {
        val base = pendingDate
        submit(base.withDayOfMonth(nextDay.coerceIn(1, base.lengthOfMonth())))
    }

    Box(
        Modifier.fillMaxWidth().height(CouiPickerDefaults.height + CouiPickerDefaults.padding * 2)
            .couiPickerSelectionLines(colors.divider),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = CouiPickerDefaults.padding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (kind in order) {
                    when (kind) {
                        'y' -> WheelPicker(
                            Modifier.width(CouiPickerDefaults.dateYearWidth),
                            year,
                            ::submitYear,
                            minDate.year..maxDate.year,
                            yearLabel,
                            dark,
                            colors = CouiWheelColors(colors.hint, colors.foreground),
                            repeatIntervalMillis = 100,
                            formatter = { if (chinese) "$it$yearLabel" else String.format(locale, "%d", it) },
                        )
                        'M' -> WheelPicker(
                            Modifier.width(CouiPickerDefaults.dateMonthWidth),
                            month,
                            ::submitMonth,
                            minMonth..maxMonth,
                            monthLabel,
                            dark,
                            colors = CouiWheelColors(colors.hint, colors.foreground),
                            wrap = minMonth == 1 && maxMonth == 12,
                            repeatIntervalMillis = 200,
                            formatter = { months[it - 1] },
                        )
                        'd' -> WheelPicker(
                            Modifier.width(CouiPickerDefaults.columnWidth),
                            date.dayOfMonth,
                            ::submitDay,
                            minDay..maxDay,
                            dayLabel,
                            dark,
                            colors = CouiWheelColors(colors.hint, colors.foreground),
                            wrap = minDay == 1 && maxDay == date.lengthOfMonth(),
                            repeatIntervalMillis = 100,
                            formatter = { if (chinese) "$it$dayLabel" else String.format(locale, "%02d", it) },
                        )
                    }
                }
            }
        }
    }
}

private val CalendarBodyHeight = 280.dp
private val CalendarEase = CubicBezierEasing(.33f, 0f, .67f, 1f)

// Calendar 实际引用的 24dp chevron；不拿返回长箭头充当月历箭头。
private val CalendarChevron = ImageVector.Builder("CalendarChevron", 24.dp, 24.dp, 24f, 24f,
    autoMirror = true).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(13.84f, 5.636f); lineTo(7.829f, 11.646f)
        curveTo(7.634f, 11.842f, 7.634f, 12.158f, 7.829f, 12.354f); lineTo(13.84f, 18.364f)
    }
}.build()

// Calendar 17.3.70 的 collapsed 16dp 路径；日历形态向下。
private val CalendarExpand = ImageVector.Builder("CalendarExpand", 16.dp, 16.dp, 16f, 16f).addPath(
    pathData = PathParser().parsePathString(
        "M4.1,5.5h7.8a0.6,0.6 0,0 1,0.6 0.6,0.65 0.65,0 0,1 -0.13,0.38L8.58,11.22a0.76 0.76,0 0,1 -1 0.12l-0.11,-0.12L3.63,6.48a0.61 0.61,0 0,1 0.09 -0.85A0.65 0.65,0 0,1,4.1 5.5Z",
    ).toNodes(),
    fill = SolidColor(Color.Black),
    pathFillType = PathFillType.EvenOdd,
).build()

// Calendar 17.3.70 的 expanded 16dp 路径；滚轮形态向上。
// 该布局未设置 supportRotateType，animated-selector 直接交换路径与颜色。
private val CalendarCollapse = ImageVector.Builder("CalendarCollapse", 16.dp, 16.dp, 16f, 16f).addPath(
    pathData = PathParser().parsePathString(
        "M4.1,10.5h7.8a0.6,0.6 0,0 0,0.6 -0.6,0.65 0.65,0 0,0 -0.13,-0.38L8.58,4.78a0.76,0.76 0,0 0,-1 -0.12l-0.11,0.12L3.63,9.52a0.61,0.61 0,0 0,0.09 0.85A0.65,0.65 0,0 0,4.1 10.5Z",
    ).toNodes(),
    fill = SolidColor(Color.Black),
    pathFillType = PathFillType.EvenOdd,
).build()
