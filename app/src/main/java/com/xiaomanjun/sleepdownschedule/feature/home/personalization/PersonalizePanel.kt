package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.WeekCardContentLayout
import com.xiaomanjun.sleepdownschedule.WeekCardTextAlignment
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidControlSlider
import com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidControlToggle
import com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidMenuButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode
import com.xiaomanjun.sleepdownschedule.core.ui.material.controlAccentColor
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.core.wallpaper.wallpaperBlurDp
import com.xiaomanjun.sleepdownschedule.courseCardBlurMaximum
import com.xiaomanjun.sleepdownschedule.feature.home.day.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.feature.home.day.homeForegroundColor
import com.xiaomanjun.sleepdownschedule.feature.home.weekCardHeightScaleFromSlider
import com.xiaomanjun.sleepdownschedule.feature.home.weekCardHeightSliderFromScale
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.ui.DefaultCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.FlatCourseCardMinimumAlpha
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassDialogSurface
import com.xiaomanjun.sleepdownschedule.glass.ui.LocalCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.MulticolorCourseCardArgb
import com.xiaomanjun.sleepdownschedule.glass.ui.decodeCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.encodeCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.glassUsesLightStyle
import com.xiaomanjun.sleepdownschedule.model.HomeMode
import com.xiaomanjun.sleepdownschedule.model.effectiveWeekCardTextAlignment
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizePanel(
    modifier: Modifier = Modifier,
    drawSurface: Boolean = true,
    rowEntranceActive: Boolean = true,
    state: AppState,
    backdrop: Backdrop?,
    mode: HomeMode,
    weekCardHeightScale: Float,
    weekCardHeightScaleFloor: Float,
    onWeekCardHeightScale: (Float) -> Unit,
    onWeekCardHeightScalePreview: (Float) -> Unit,
    onPickWallpaper: () -> Unit,
    onUpdateConfig: (String, ScheduleConfigEntity) -> Unit,
    onPreviewConfig: (String, ScheduleConfigEntity) -> Unit,
    previewSliderKey: String?,
    previewProgress: Float,
    onSliderPreviewActiveChange: (String, Boolean) -> Unit
) {
    val rowReveal = remember { Animatable(0f) }
    val rowDensity = LocalDensity.current
    val rowEasing = remember { CubicBezierEasing(0.22f, 0f, 0.30f, 1f) }
    val weekContentRows = if (mode == HomeMode.Week) 3 else 0
    val nativeMaterialMode = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode.current
    val nativeMaterialActive = nativeMaterialMode?.enabled == true && !AppMaterialPreferences.isSuperPerformance
    val nativeMaterialRows = if (nativeMaterialMode == null) 0 else if (nativeMaterialActive) 3 else 1
    val rowEntranceDurationMillis = 610 + (weekContentRows + nativeMaterialRows) * 15
    LaunchedEffect(rowEntranceActive, rowEntranceDurationMillis) {
        if (rowEntranceActive) {
            withFrameNanos { }
            rowReveal.animateTo(1f, tween(rowEntranceDurationMillis, easing = LinearEasing))
        }
    }
    fun Modifier.rowEntrance(index: Int): Modifier = graphicsLayer {
        val t = ((rowReveal.value * rowEntranceDurationMillis - index * 15f) / 340f).coerceIn(0f, 1f)
        val progress = rowEasing.transform(t)
        alpha = progress
        scaleX = 0.86f + 0.14f * progress
        scaleY = scaleX
        translationY = (1f - progress) * with(rowDensity) { 32.dp.toPx() }
    }
    val inheritedCoursePalette = LocalCourseCardPalette.current
    var showCourseColorDialog by remember { mutableStateOf(false) }
    var courseColorDialogMode by remember { mutableStateOf(CourseCardColorMode.SOLID) }
    var courseColorDialogStage by remember { mutableStateOf(CourseColorDialogStage.Picker) }
    var courseColorEditorColors by remember { mutableStateOf(listOf(0xFFD6E9FFL)) }
    var courseColorEditorIndex by remember { mutableIntStateOf(0) }

    fun openCourseColorDialog(colorMode: CourseCardColorMode) {
        val seed = state.config.cardColorArgb
            .takeUnless { it == MulticolorCourseCardArgb }
            ?: 0xFFD6E9FFL
        val initial = when (colorMode) {
            CourseCardColorMode.SOLID,
            CourseCardColorMode.GRADIENT -> listOf(seed)
            CourseCardColorMode.COLORFUL -> {
                val configured = decodeCourseCardPalette(state.config.courseCardPalette)
                    .ifEmpty { inheritedCoursePalette }
                    .ifEmpty { DefaultCourseCardPalette }
                    .take(4)
                buildList {
                    addAll(configured)
                    DefaultCourseCardPalette.forEach { fallback ->
                        if (size < 4 && fallback !in this) add(fallback)
                    }
                }.take(4)
            }
        }
        courseColorDialogMode = colorMode
        courseColorDialogStage = CourseColorDialogStage.Picker
        courseColorEditorColors = initial
        courseColorEditorIndex = 0
        showCourseColorDialog = true
    }

    fun updateCourseColorEditor(index: Int, color: Long) {
        val next = courseColorEditorColors.toMutableList()
        while (next.size <= index) {
            next += DefaultCourseCardPalette[next.size % DefaultCourseCardPalette.size]
        }
        next[index] = color and 0xFFFFFFFFL
        courseColorEditorColors = next
    }

    fun commitCourseColorEditor() {
        val primary = courseColorEditorColors.firstOrNull() ?: 0xFFD6E9FFL
        val candidate = when (courseColorDialogMode) {
            CourseCardColorMode.SOLID,
            CourseCardColorMode.GRADIENT -> state.config.copy(
                cardColorArgb = primary,
                courseCardColorMode = courseColorDialogMode
            )
            CourseCardColorMode.COLORFUL -> state.config.copy(
                cardColorArgb = primary,
                courseCardColorMode = CourseCardColorMode.COLORFUL,
                courseCardPalette = encodeCourseCardPalette(courseColorEditorColors)
            )
        }
        onUpdateConfig(PersonalizeCardColorChange, candidate)
        showCourseColorDialog = false
    }

    var activeTouchSliderKey by remember { mutableStateOf<String?>(null) }
    fun updateSliderTouchOwner(key: String, active: Boolean) {
        activeTouchSliderKey = resolveActivePersonalizationSlider(
            currentKey = activeTouchSliderKey,
            eventKey = key,
            active = active
        )
    }
    @Composable
    fun PersonalizeSection(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    ComposeColor.Black.copy(
                        alpha = 0.10f * (1f - previewProgress.coerceIn(0f, 1f))
                    ),
                    shape = RoundedRectangle(18.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
    @Composable
    fun PersonalizeSectionDivider(index: Int) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 6.dp)
                .rowEntrance(index)
                .personalizePreviewVisibility(previewSliderKey, previewProgress),
            thickness = 1.dp,
            color = LocalContentColor.current.copy(alpha = 0.14f)
        )
    }
    @Composable
    fun PersonalizeGroupTitle(title: String, index: Int, section: Boolean = false) {
        Box(Modifier.fillMaxWidth().padding(top = if (section) 2.dp else 6.dp, bottom = 2.dp).rowEntrance(index)
            .personalizePreviewVisibility(previewSliderKey, previewProgress), contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = if (section) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
    @Composable
    fun PanelContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonalizeSection {
                Row(
                    modifier = Modifier.rowEntrance(0)
                        .fillMaxWidth().heightIn(min = 48.dp)
                        .personalizePreviewVisibility(previewSliderKey, previewProgress),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("首页壁纸", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiquidMenuButton(
                            backdrop,
                            "\u9009\u62E9\u58C1\u7EB8",
                            onClick = onPickWallpaper,
                            textColorOverride = ComposeColor.White,
                            surfaceColorOverride = controlAccentColor(ComposeColor(0xFF0A84FF)).copy(alpha = 0.72f)
                        )
                        if (state.config.wallpaperUri != null) {
                            LiquidMenuButton(
                                backdrop,
                                "\u6E05\u9664",
                                onClick = {
                                    onUpdateConfig(
                                        PersonalizeWallpaperContentChange,
                                        state.config.copy(
                                            wallpaperUri = null,
                                            wallpaperPortraitCenterX = 0.5f,
                                            wallpaperPortraitCenterY = 0.5f,
                                            wallpaperPortraitScale = 1f,
                                            wallpaperLandscapeCenterX = 0.5f,
                                            wallpaperLandscapeCenterY = 0.5f,
                                            wallpaperLandscapeScale = 1f,
                                            wallpaperSourceWidth = null,
                                            wallpaperSourceHeight = null,
                                            homeTextLight = false
                                        )
                                    )
                                },
                                textColorOverride = ComposeColor.White,
                                surfaceColorOverride = ComposeColor(0xFFFF453A).copy(alpha = 0.66f)
                            )
                        }
                    }
                }
                WallpaperBlurControl(
                    modifier = Modifier.rowEntrance(1),
                    blurDp = state.config.wallpaperBlur,
                    backdrop = backdrop,
                    onCommit = { percent ->
                        onUpdateConfig(
                            PersonalizeWallpaperBlurSlider,
                            state.config.copy(wallpaperBlur = wallpaperBlurDp(percent))
                        )
                    },
                    onPreviewValueChange = { percent ->
                        onPreviewConfig(
                            PersonalizeWallpaperBlurSlider,
                            state.config.copy(wallpaperBlur = wallpaperBlurDp(percent))
                        )
                    },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    onTouchActiveChange = {
                        updateSliderTouchOwner(PersonalizeWallpaperBlurSlider, it)
                    }
                )
                PersonalizeValueSlider(
                    sliderKey = PersonalizeWallpaperBrightnessSlider,
                    modifier = Modifier.rowEntrance(2),
                    value = state.config.wallpaperBrightness.coerceIn(0.35f, 1f),
                    valueRange = 0.35f..1f,
                    backdrop = backdrop,
                    label = { "壁纸亮度 ${(it * 100).toInt()}%" },
                    onCommit = {
                        onUpdateConfig(
                            PersonalizeWallpaperBrightnessSlider,
                            state.config.copy(wallpaperBrightness = it)
                        )
                    },
                    onPreviewValueChange = {
                        onPreviewConfig(
                            PersonalizeWallpaperBrightnessSlider,
                            state.config.copy(wallpaperBrightness = it)
                        )
                    },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    snapValue = 1f,
                    onTouchActiveChange = {
                        updateSliderTouchOwner(PersonalizeWallpaperBrightnessSlider, it)
                    }
                )
            }
            PersonalizeSection {
                PersonalizeGroupTitle("尺寸与字体", 3)
                if (mode == HomeMode.Week) {
                    Text(
                        text = "周视图行高",
                        modifier = Modifier.rowEntrance(3)
                            .fillMaxWidth()
                            .personalizePreviewVisibility(previewSliderKey, previewProgress),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(
                        modifier = Modifier.rowEntrance(4).personalizePreviewVisibility(
                            previewSliderKey,
                            previewProgress,
                            PersonalizeWeekHeightSlider
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Box {
                            LiquidControlSlider(
                                value = weekCardHeightSliderFromScale(
                                    scale = weekCardHeightScale,
                                    minimumScale = weekCardHeightScaleFloor
                                ),
                                compactThumb = true,
                                onValueChange = {
                                    onWeekCardHeightScale(
                                        weekCardHeightScaleFromSlider(
                                            progress = it,
                                            minimumScale = weekCardHeightScaleFloor
                                        )
                                    )
                                },
                                valueRange = 0f..1f,
                                backdrop = backdrop,
                                onLiveValueChange = {
                                    onWeekCardHeightScalePreview(
                                        weekCardHeightScaleFromSlider(
                                            progress = it,
                                            minimumScale = weekCardHeightScaleFloor
                                        )
                                    )
                                },
                                snapValue = 0.5f,
                                onSliderTouchActiveChange = { active ->
                                    updateSliderTouchOwner(PersonalizeWeekHeightSlider, active)
                                    onSliderPreviewActiveChange(PersonalizeWeekHeightSlider, active)
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-2).dp)
                        ) {
                            Text(
                                "紧凑",
                                modifier = Modifier.align(Alignment.CenterStart),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "自适应",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "宽松",
                                modifier = Modifier.align(Alignment.CenterEnd),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (mode == HomeMode.Week) {
                    PersonalizeValueSlider(
                        sliderKey = PersonalizeWeekCornerSlider,
                        modifier = Modifier.rowEntrance(5),
                        value = state.config.weekCardCornerProgress.coerceIn(0f, 1f),
                        valueRange = 0f..1f,
                        backdrop = backdrop,
                        label = { "课程卡片圆角" },
                        onCommit = {
                            onUpdateConfig(
                                PersonalizeWeekCornerSlider,
                                state.config.copy(weekCardCornerProgress = it)
                            )
                        },
                        onPreviewValueChange = {
                            onPreviewConfig(
                                PersonalizeWeekCornerSlider,
                                state.config.copy(weekCardCornerProgress = it)
                            )
                        },
                        previewSliderKey = previewSliderKey,
                        previewProgress = previewProgress,
                        onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                        snapValue = 0.5f,
                        onTouchActiveChange = {
                            updateSliderTouchOwner(PersonalizeWeekCornerSlider, it)
                        }
                    )
                }
                PersonalizeValueSlider(
                    sliderKey = PersonalizeCardFontSlider,
                    modifier = Modifier.rowEntrance(6),
                    value = state.config.courseCardFontScale,
                    valueRange = 0.80f..1.35f,
                    backdrop = backdrop,
                    label = { "课程卡片字体 ${(it * 100).toInt()}%" },
                    onCommit = {
                        onUpdateConfig(
                            PersonalizeCardFontSlider,
                            state.config.copy(courseCardFontScale = it)
                        )
                    },
                    onPreviewValueChange = {
                        onPreviewConfig(
                            PersonalizeCardFontSlider,
                            state.config.copy(courseCardFontScale = it)
                        )
                    },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    snapValue = 1f,
                    onTouchActiveChange = {
                        updateSliderTouchOwner(PersonalizeCardFontSlider, it)
                    }
                )
                PersonalizeSectionDivider(7)
                PersonalizeGroupTitle("内容", 7)
                Row(
                    modifier = Modifier.rowEntrance(8)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .personalizePreviewVisibility(previewSliderKey, previewProgress),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("彩色文字", style = MaterialTheme.typography.bodyMedium)
                    LiquidControlToggle(
                        checked = state.config.courseCardColoredTextEnabled,
                        compact = true,
                        onCheckedChange = {
                            onUpdateConfig(
                                PersonalizeCardColoredTextChange,
                                state.config.copy(courseCardColoredTextEnabled = it)
                            )
                        },
                        backdrop = backdrop
                    )
                }
                if (mode == HomeMode.Week) {
                    Row(
                        Modifier.rowEntrance(9).fillMaxWidth().heightIn(min = 48.dp)
                            .personalizePreviewVisibility(previewSliderKey, previewProgress),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("显示上课地点", style = MaterialTheme.typography.bodyMedium)
                        LiquidControlToggle(
                            checked = state.config.weekCardShowLocation,
                            compact = true,
                            onCheckedChange = {
                                onUpdateConfig(PersonalizeWeekLocationChange,
                                    state.config.copy(weekCardShowLocation = it))
                            },
                            backdrop = backdrop
                        )
                    }
                    Row(
                        Modifier.rowEntrance(10).fillMaxWidth().heightIn(min = 48.dp)
                            .personalizePreviewVisibility(previewSliderKey, previewProgress),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("显示教师", style = MaterialTheme.typography.bodyMedium)
                        LiquidControlToggle(
                            checked = state.config.weekCardShowTeacher,
                            compact = true,
                            onCheckedChange = {
                                onUpdateConfig(PersonalizeWeekTeacherChange,
                                    state.config.copy(weekCardShowTeacher = it))
                            },
                            backdrop = backdrop
                        )
                    }
                    val textLayouts = listOf(
                        WeekCardContentLayout.CURRENT to WeekCardTextAlignment.CENTER,
                        WeekCardContentLayout.TOP to WeekCardTextAlignment.START,
                        WeekCardContentLayout.TOP to WeekCardTextAlignment.CENTER,
                        WeekCardContentLayout.MIDDLE to WeekCardTextAlignment.START,
                        WeekCardContentLayout.MIDDLE to WeekCardTextAlignment.CENTER
                    )
                    val rowTextColor = LocalContentColor.current
                    val rowTextStyle = MaterialTheme.typography.bodyMedium
                    val rowMaxHeight = maxOf(48.dp, with(rowDensity) { rowTextStyle.lineHeight.toDp() } + 12.dp)
                    MiuixTheme(colors = MiuixTheme.colorScheme.copy(
                        onBackground = rowTextColor,
                        onSurfaceVariantActions = rowTextColor.copy(alpha = 0.72f)
                    )) {
                        SleepDownLiquidDropdownPreference(
                            items = listOf("默认", "顶部居左", "顶部居中", "中部居左", "中部居中"),
                            selectedIndex = if (state.config.weekCardContentLayout == WeekCardContentLayout.CURRENT) 0
                                else 1 + (if (state.config.weekCardContentLayout in listOf(
                                WeekCardContentLayout.MIDDLE, WeekCardContentLayout.CENTERED)) 2 else 0) +
                                (if (state.config.effectiveWeekCardTextAlignment() == WeekCardTextAlignment.CENTER) 1 else 0),
                            title = "内容对齐",
                            modifier = Modifier.rowEntrance(11).fillMaxWidth()
                                .heightIn(max = rowMaxHeight)
                                .personalizePreviewVisibility(previewSliderKey, previewProgress),
                            insideMargin = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
                            maxHeight = 260.dp,
                            compactTextStyle = rowTextStyle,
                            showAnchorPressFeedback = false,
                            backdrop = backdrop,
                            config = state.config,
                            onSelectedIndexChange = { index ->
                                textLayouts.getOrNull(index)?.let { (layout, alignment) ->
                                    onUpdateConfig(PersonalizeWeekLayoutChange,
                                        state.config.copy(weekCardContentLayout = layout,
                                            weekCardTextAlignment = alignment))
                                }
                            }
                        )
                    }
                }
                PersonalizeSectionDivider(9 + weekContentRows)
                PersonalizeGroupTitle("颜色", 9 + weekContentRows)
                CourseColorModeRow(
                    title = "纯色",
                    iconRes = R.drawable.ic_color_solid,
                    mode = CourseCardColorMode.SOLID,
                    selectedMode = state.config.courseCardColorMode,
                    presets = SolidCourseColorPresets.map { listOf(it) },
                    selectedPreset = { it.firstOrNull() == state.config.cardColorArgb },
                    customSelected = state.config.courseCardColorMode == CourseCardColorMode.SOLID &&
                        SolidCourseColorPresets.none { it == state.config.cardColorArgb },
                    backdrop = backdrop,
                    modifier = Modifier.rowEntrance(10 + weekContentRows).personalizePreviewVisibility(previewSliderKey, previewProgress),
                    onPresetSelected = { colors ->
                        onUpdateConfig(
                            PersonalizeCardColorChange,
                            state.config.copy(
                                cardColorArgb = colors.first(),
                                courseCardColorMode = CourseCardColorMode.SOLID
                            )
                        )
                    },
                    onOpenPalette = { openCourseColorDialog(CourseCardColorMode.SOLID) }
                )
                CourseColorModeRow(
                    title = "渐变",
                    iconRes = R.drawable.ic_color_gradient,
                    mode = CourseCardColorMode.GRADIENT,
                    selectedMode = state.config.courseCardColorMode,
                    presets = GradientCourseColorPresets.map { listOf(it) },
                    selectedPreset = { it.firstOrNull() == state.config.cardColorArgb },
                    customSelected = state.config.courseCardColorMode == CourseCardColorMode.GRADIENT &&
                        GradientCourseColorPresets.none { it == state.config.cardColorArgb },
                    backdrop = backdrop,
                    modifier = Modifier.rowEntrance(11 + weekContentRows).personalizePreviewVisibility(previewSliderKey, previewProgress),
                    onPresetSelected = { colors ->
                        onUpdateConfig(
                            PersonalizeCardColorChange,
                            state.config.copy(
                                cardColorArgb = colors.first(),
                                courseCardColorMode = CourseCardColorMode.GRADIENT
                            )
                        )
                    },
                    onOpenPalette = { openCourseColorDialog(CourseCardColorMode.GRADIENT) }
                )
                CourseColorModeRow(
                    title = "彩色",
                    iconRes = R.drawable.ic_color_colorful,
                    mode = CourseCardColorMode.COLORFUL,
                    selectedMode = state.config.courseCardColorMode,
                    presets = listOf(AutomaticColorfulCoursePreview),
                    selectedPreset = { state.config.courseCardPalette.isBlank() },
                    customSelected = state.config.courseCardColorMode == CourseCardColorMode.COLORFUL &&
                        state.config.courseCardPalette.isNotBlank(),
                    backdrop = backdrop,
                    modifier = Modifier.rowEntrance(12 + weekContentRows).personalizePreviewVisibility(previewSliderKey, previewProgress),
                    onPresetSelected = {
                        onUpdateConfig(
                            PersonalizeCardColorChange,
                            state.config.copy(
                                courseCardColorMode = CourseCardColorMode.COLORFUL,
                                courseCardPalette = ""
                            )
                        )
                    },
                    onOpenPalette = { openCourseColorDialog(CourseCardColorMode.COLORFUL) }
                )
                PersonalizeSectionDivider(13 + weekContentRows)
                PersonalizeGroupTitle("材质", 13 + weekContentRows)
                val superPerformance = AppMaterialPreferences.isSuperPerformance
                val glassLocked = (!state.config.hasAnyWallpaper() && !nativeMaterialActive) || superPerformance
                nativeMaterialMode?.let { material ->
                    Row(
                        modifier = Modifier.rowEntrance(14 + weekContentRows).fillMaxWidth().heightIn(min = 48.dp)
                            .personalizePreviewVisibility(previewSliderKey, previewProgress),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ColorOS 17 凝光视效", style = MaterialTheme.typography.bodyMedium)
                        LiquidControlToggle(checked = material.enabled, compact = true,
                            onCheckedChange = material.onEnabledChange, enabled = !superPerformance, backdrop = backdrop)
                    }
                    if (nativeMaterialActive) {
                        PersonalizeValueSlider(
                            sliderKey = PersonalizeNativeCardTintSlider,
                            modifier = Modifier.rowEntrance(15 + weekContentRows),
                            value = material.tintStrength, valueRange = 0f..1f, backdrop = backdrop,
                            label = { "课程着色强度 ${(it * 100).toInt()}%" },
                            onCommit = { material.onTintStrengthChange(it, true) },
                            onPreviewValueChange = { material.onTintStrengthChange(it, false) },
                            previewSliderKey = previewSliderKey, previewProgress = previewProgress,
                            onSliderPreviewActiveChange = { key, active ->
                                onSliderPreviewActiveChange(key, active)
                                if (!active) material.onPreviewFinished()
                            }, snapValue = 0.5f,
                            onTouchActiveChange = { updateSliderTouchOwner(PersonalizeNativeCardTintSlider, it) }
                        )
                        PersonalizeValueSlider(
                            sliderKey = PersonalizeNativeCardBlurSlider,
                            modifier = Modifier.rowEntrance(16 + weekContentRows),
                            value = material.blurPercent, valueRange = 0f..100f, backdrop = backdrop,
                            label = { "凝光模糊 ${it.toInt()}%" },
                            onCommit = { material.onBlurPercentChange(it, true) },
                            onPreviewValueChange = { material.onBlurPercentChange(it, false) },
                            previewSliderKey = previewSliderKey, previewProgress = previewProgress,
                            onSliderPreviewActiveChange = { key, active ->
                                onSliderPreviewActiveChange(key, active)
                                if (!active) material.onPreviewFinished()
                            }, snapValue = 50f,
                            onTouchActiveChange = { updateSliderTouchOwner(PersonalizeNativeCardBlurSlider, it) }
                        )
                    }
                }
                if (!nativeMaterialActive) Row(
                    modifier = Modifier.rowEntrance(14 + weekContentRows + nativeMaterialRows)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .personalizePreviewVisibility(previewSliderKey, previewProgress),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("课程卡片液态玻璃", style = MaterialTheme.typography.bodyMedium)
                    LiquidControlToggle(
                        checked = state.config.courseCardGlassEnabled && !glassLocked,
                        compact = true,
                        onCheckedChange = {
                            onUpdateConfig(
                                PersonalizeCardGlassChange,
                                state.config.copy(courseCardGlassEnabled = it)
                            )
                        },
                        enabled = !glassLocked,
                        backdrop = backdrop
                    )
                }
                if (glassLocked) {
                    Text(
                        if (superPerformance) "超级性能模式下不可用：液态玻璃、折射、轮廓光与高斯模糊。退出后恢复原参数。"
                        else "设置壁纸以启用课程卡片液态玻璃效果",
                        style = MaterialTheme.typography.bodySmall,
                        color = personalizePanelForegroundColor(state.config).copy(alpha = 0.60f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 6.dp)
                            .personalizePreviewVisibility(previewSliderKey, previewProgress)
                    )
                }
                if (!nativeMaterialActive && state.config.courseCardGlassEnabled && !glassLocked) {
                    Row(
                        modifier = Modifier.rowEntrance(15 + weekContentRows + nativeMaterialRows)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .personalizePreviewVisibility(previewSliderKey, previewProgress),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("质感轮廓光", style = MaterialTheme.typography.bodyMedium)
                        LiquidControlToggle(
                            checked = state.config.courseCardOutlineLightEnabled,
                            compact = true,
                            onCheckedChange = {
                                onUpdateConfig(
                                    PersonalizeCardOutlineLightChange,
                                    state.config.copy(courseCardOutlineLightEnabled = it)
                                )
                            },
                            backdrop = backdrop
                        )
                    }
                } else if (!nativeMaterialActive) {
                    Row(
                        modifier = Modifier.rowEntrance(15 + weekContentRows + nativeMaterialRows)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .personalizePreviewVisibility(previewSliderKey, previewProgress),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("课程卡片高斯模糊", style = MaterialTheme.typography.bodyMedium)
                        LiquidControlToggle(
                            checked = state.config.courseCardGaussianBlurEnabled && !glassLocked,
                            compact = true,
                            onCheckedChange = {
                                onUpdateConfig(
                                    PersonalizeCardGaussianBlurChange,
                                    state.config.copy(courseCardGaussianBlurEnabled = it)
                                )
                            },
                            enabled = !glassLocked,
                            backdrop = backdrop
                        )
                    }
                }
                val minimumCardAlpha = if (glassLocked) FlatCourseCardMinimumAlpha else 0f
                val alphaLabel = when {
                    !glassLocked &&
                        state.config.courseCardGlassEnabled &&
                        state.config.courseCardOutlineLightEnabled -> "轮廓光强度"
                    !glassLocked && state.config.courseCardGlassEnabled -> "课程卡片着色强度"
                    else -> "课程卡片不透明度"
                }
                if (!superPerformance && !nativeMaterialActive) PersonalizeValueSlider(
                    sliderKey = PersonalizeCardAlphaSlider,
                    modifier = Modifier.rowEntrance(16 + weekContentRows + nativeMaterialRows),
                    value = state.config.cardAlpha.coerceIn(minimumCardAlpha, 1f),
                    valueRange = minimumCardAlpha..1f,
                    backdrop = backdrop,
                    label = { "$alphaLabel ${(it * 100).toInt()}%" },
                    onCommit = {
                        onUpdateConfig(
                            PersonalizeCardAlphaSlider,
                            state.config.copy(cardAlpha = it)
                        )
                    },
                    onPreviewValueChange = {
                        onPreviewConfig(
                            PersonalizeCardAlphaSlider,
                            state.config.copy(cardAlpha = it)
                        )
                    },
                    previewSliderKey = previewSliderKey,
                    previewProgress = previewProgress,
                    onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                    snapValue = 0.5f,
                    onTouchActiveChange = {
                        updateSliderTouchOwner(PersonalizeCardAlphaSlider, it)
                    }
                )
                if (
                    !nativeMaterialActive && (superPerformance || ((state.config.courseCardGlassEnabled || state.config.courseCardGaussianBlurEnabled) &&
                    !glassLocked))
                ) {
                    val maxCourseCardBlur = courseCardBlurMaximum(state.config.courseCardGlassEnabled)
                    PersonalizeValueSlider(
                        sliderKey = PersonalizeCardBlurSlider,
                        modifier = Modifier.rowEntrance(17 + weekContentRows + nativeMaterialRows),
                        value = state.config.courseCardBlur.coerceIn(0f, maxCourseCardBlur) /
                            maxCourseCardBlur * 100f,
                        valueRange = 0f..100f,
                        backdrop = backdrop,
                        label = { if (superPerformance) "课程卡片不透明度 ${(AppMaterialPreferences.policy.opacity(it, 100f) * 100).toInt()}%" else "课程卡片模糊 ${it.toInt()}%" },
                        onCommit = {
                            onUpdateConfig(
                                PersonalizeCardBlurSlider,
                                state.config.copy(courseCardBlur = it / 100f * maxCourseCardBlur)
                            )
                        },
                        onPreviewValueChange = {
                            onPreviewConfig(
                                PersonalizeCardBlurSlider,
                                state.config.copy(courseCardBlur = it / 100f * maxCourseCardBlur)
                            )
                        },
                        previewSliderKey = previewSliderKey,
                        previewProgress = previewProgress,
                        onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                        snapValue = 35f,
                        onTouchActiveChange = {
                            updateSliderTouchOwner(PersonalizeCardBlurSlider, it)
                        }
                    )
                }
                if (!nativeMaterialActive && state.config.courseCardGlassEnabled && !glassLocked &&
                    !com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences.isPerformance) {
                    PersonalizeValueSlider(
                        sliderKey = PersonalizeCardRefractionSlider,
                        modifier = Modifier.rowEntrance(18 + weekContentRows + nativeMaterialRows),
                        value = state.config.courseCardRefractionStrength.coerceIn(0f, 1f),
                        valueRange = 0f..1f,
                        backdrop = backdrop,
                        label = { "折射强度 ${(it * 100f).toInt()}%" },
                        onCommit = {
                            onUpdateConfig(
                                PersonalizeCardRefractionSlider,
                                state.config.copy(courseCardRefractionStrength = it)
                            )
                        },
                        onPreviewValueChange = {
                            onPreviewConfig(
                                PersonalizeCardRefractionSlider,
                                state.config.copy(courseCardRefractionStrength = it)
                            )
                        },
                        previewSliderKey = previewSliderKey,
                        previewProgress = previewProgress,
                        onSliderPreviewActiveChange = onSliderPreviewActiveChange,
                        snapValue = 0.5f,
                        onTouchActiveChange = {
                            updateSliderTouchOwner(PersonalizeCardRefractionSlider, it)
                        }
                    )
                }
            }
        }
    }
    val scrollState = rememberScrollState()
    val contentModifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState, enabled = activeTouchSliderKey == null)
    val panelTextColor = personalizePanelForegroundColor(state.config)
    if (!drawSurface) {
        Box(modifier = modifier) {
            CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                PanelContent(contentModifier)
            }
        }
    } else if (backdrop != null) {
        val lightGlass = glassUsesLightStyle(state.config)
        LiquidPanel(
            backdrop = backdrop,
            modifier = modifier,
            surfaceColor = if (lightGlass) ComposeColor.White.copy(alpha = 0.18f) else ComposeColor(0xFF121212).copy(alpha = 0.30f)
        ) {
            CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                PanelContent(contentModifier)
            }
        }
    } else {
        GlassDialogSurface(
            backdrop = null,
            config = state.config,
            modifier = modifier
        ) {
            CompositionLocalProvider(LocalContentColor provides panelTextColor) {
                PanelContent(contentModifier)
            }
        }
    }
    CourseColorEditorDialog(
        show = showCourseColorDialog,
        stage = courseColorDialogStage,
        mode = courseColorDialogMode,
        colors = courseColorEditorColors,
        selectedIndex = courseColorEditorIndex,
        backdrop = backdrop,
        config = state.config,
        onDismiss = { showCourseColorDialog = false },
        onStageChange = { courseColorDialogStage = it },
        onSelectedIndexChange = { courseColorEditorIndex = it },
        onColorChange = ::updateCourseColorEditor,
        onConfirm = ::commitCourseColorEditor
    )
}

@Composable
private fun personalizePanelForegroundColor(config: ScheduleConfigEntity): ComposeColor {
    return homeForegroundColor(config)
}
