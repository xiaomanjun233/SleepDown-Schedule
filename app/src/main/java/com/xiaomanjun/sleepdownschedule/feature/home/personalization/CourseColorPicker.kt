package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.CourseCardColorMode
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetLiquidAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownPickerDialog
import com.xiaomanjun.sleepdownschedule.core.ui.material.controlAccentColor
import com.xiaomanjun.sleepdownschedule.feature.course.editor.CourseColorPaletteButton
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.ui.DefaultCourseCardPalette
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal enum class CourseColorDialogStage { Picker, Wallpaper }

internal val SolidCourseColorPresets = listOf(
    0xFFD6E9FFL,
    0xFFFFC4D6L,
    0xFFB7E4C7L,
    0xFFFFD166L
)

internal val GradientCourseColorPresets = listOf(
    0xFF79BDF2L,
    0xFFA99AE8L,
    0xFFF09AB6L,
    0xFF71C8A1L
)

/** One affordance for the original wallpaper-driven automatic colourful mode. */
internal val AutomaticColorfulCoursePreview = DefaultCourseCardPalette.take(4)

private fun vividPreviewColor(argb: Long, minimumValue: Float = 0.72f): ComposeColor {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
    hsv[1] = hsv[1].coerceIn(0.34f, 0.82f)
    hsv[2] = hsv[2].coerceIn(minimumValue, 0.96f)
    return ComposeColor(android.graphics.Color.HSVToColor(hsv))
}

private fun tonalPreviewColors(seed: Long): List<ComposeColor> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toInt(), hsv)
    val hue = hsv[0]
    val saturation = hsv[1].coerceAtLeast(0.40f)
    return listOf(
        ComposeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation * 0.34f, 0.98f))),
        ComposeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation * 0.58f, 0.90f))),
        ComposeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation * 0.78f, 0.80f))),
        ComposeColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation.coerceAtMost(0.88f), 0.70f)))
    )
}

@Composable
internal fun CourseColorModeRow(
    title: String,
    iconRes: Int,
    mode: CourseCardColorMode,
    selectedMode: CourseCardColorMode,
    presets: List<List<Long>>,
    selectedPreset: (List<Long>) -> Boolean,
    customSelected: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onPresetSelected: (List<Long>) -> Unit,
    onOpenPalette: () -> Unit
) {
    val foreground = LocalContentColor.current
    val labelColor = if (selectedMode == mode) MaterialTheme.colorScheme.primary else foreground
    @Composable
    fun PresetButton(colors: List<Long>) {
        val selected = selectedMode == mode && selectedPreset(colors)
        val previewColors = when (mode) {
            CourseCardColorMode.SOLID -> colors.map { ComposeColor(it.toInt()) }
            CourseCardColorMode.GRADIENT -> tonalPreviewColors(colors.first())
            CourseCardColorMode.COLORFUL -> colors.map { vividPreviewColor(it) }
        }
        Surface(
            modifier = Modifier.size(32.dp),
            shape = Capsule(),
            color = if (mode == CourseCardColorMode.COLORFUL) {
                ComposeColor.White.copy(alpha = 0.88f)
            } else {
                ComposeColor.Transparent
            },
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            onClick = { onPresetSelected(colors) }
        ) {
            if (mode == CourseCardColorMode.COLORFUL) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // A four-color dot cluster reads instantly as "colorful"; a themed glyph
                    // cannot express the multi-palette idea on its own.
                    Canvas(
                        Modifier
                            .size(19.dp)
                            .semantics { contentDescription = "自动彩色课程卡" }
                    ) {
                        val dotRadius = size.minDimension * 0.165f
                        val gap = size.minDimension * 0.235f
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dotColors = listOf(
                            ComposeColor(0xFFFF5A52),
                            ComposeColor(0xFFFFB300),
                            ComposeColor(0xFF30C851),
                            ComposeColor(0xFF0A84FF)
                        )
                        val dotCenters = listOf(
                            Offset(centerX - gap, centerY - gap),
                            Offset(centerX + gap, centerY - gap),
                            Offset(centerX - gap, centerY + gap),
                            Offset(centerX + gap, centerY + gap)
                        )
                        dotCenters.forEachIndexed { index, center ->
                            drawCircle(color = dotColors[index], radius = dotRadius, center = center)
                        }
                    }
                }
            } else {
                Box(
                    Modifier.background(
                        if (previewColors.size == 1) {
                            Brush.linearGradient(listOf(previewColors.first(), previewColors.first()))
                        } else {
                            Brush.linearGradient(previewColors)
                        }
                    )
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.width(64.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                color = labelColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selectedMode == mode) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                softWrap = false
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mode == CourseCardColorMode.COLORFUL) {
                repeat(5) { index ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when (index) {
                            0 -> PresetButton(presets.first())
                            4 -> CourseColorPaletteButton(
                                backdrop = backdrop,
                                selected = customSelected,
                                onClick = onOpenPalette,
                                size = 32.dp
                            )
                        }
                    }
                }
            } else {
                presets.forEach { colors ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PresetButton(colors)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CourseColorPaletteButton(
                        backdrop = backdrop,
                        selected = customSelected,
                        onClick = onOpenPalette,
                        size = 32.dp
                    )
                }
            }
        }
    }
}

@Composable
internal fun CourseColorEditorDialog(
    show: Boolean,
    stage: CourseColorDialogStage,
    mode: CourseCardColorMode,
    colors: List<Long>,
    selectedIndex: Int,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onDismiss: () -> Unit,
    onStageChange: (CourseColorDialogStage) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    onColorChange: (Int, Long) -> Unit,
    onConfirm: () -> Unit
) {
    val foreground = appPanelForegroundColor(config)
    SleepDownPickerDialog(
        show = show,
        title = if (stage == CourseColorDialogStage.Picker) "选择颜色" else "从壁纸取色",
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        config = config,
        contentSpacing = 14.dp,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        blurRadius = 10.dp,
        contentTransitionKey = stage,
        titleAction = {
            if (stage == CourseColorDialogStage.Picker) {
                IconButton(
                    onClick = { onStageChange(CourseColorDialogStage.Wallpaper) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_color_eyedropper),
                        contentDescription = "从壁纸吸色",
                        tint = foreground,
                        modifier = Modifier.size(21.dp)
                    )
                }
            } else {
                // Keep the title row's geometry stable while its content crossfades.
                Spacer(Modifier.size(38.dp))
            }
        },
        contentForState = { displayedStage ->
            if (displayedStage == CourseColorDialogStage.Picker) {
                if (mode == CourseCardColorMode.COLORFUL && colors.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                    ) {
                        items(colors.size) { index ->
                            val color = colors[index]
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = Capsule(),
                                color = ComposeColor(color.toInt()),
                                border = BorderStroke(
                                    if (index == selectedIndex) 3.dp else 1.dp,
                                    if (index == selectedIndex) MaterialTheme.colorScheme.primary
                                    else foreground.copy(alpha = 0.34f)
                                ),
                                onClick = { onSelectedIndexChange(index) }
                            ) {}
                        }
                        item {
                            val addColor: () -> Unit = {
                                val addIndex = colors.size
                                onColorChange(
                                    addIndex,
                                    DefaultCourseCardPalette[addIndex % DefaultCourseCardPalette.size]
                                )
                                onSelectedIndexChange(addIndex)
                            }
                            // 外层 padding 与 clip=false 保证按压放大不被列表/弹窗裁切
                            Box(
                                // 与色块保持同尺寸(34dp)让加号对齐；按压放大外扩交给
                                // LazyRow 的 contentPadding 与 clip=false 处理，
                                // 项内 padding 会导致加号相对色块错位。
                                modifier = Modifier
                                    .size(34.dp)
                                    .graphicsLayer { clip = false },
                                contentAlignment = Alignment.Center
                            ) {
                                if (backdrop != null) {
                                    LiquidButton(
                                        onClick = addColor,
                                        backdrop = backdrop,
                                        modifier = Modifier.size(34.dp),
                                        height = 34.dp,
                                        contentPadding = PaddingValues(0.dp),
                                        surfaceColor = controlAccentColor(ComposeColor(0xFF0A84FF)),
                                        blurRadius = 7.dp,
                                        lensHeight = 11.dp,
                                        lensAmount = 15.dp,
                                        pressExpansion = 3.dp
                                    ) {
                                        Icon(
                                            Icons.Rounded.Add,
                                            contentDescription = "添加颜色",
                                            tint = ComposeColor.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier.size(34.dp),
                                        shape = Capsule(),
                                        color = controlAccentColor(ComposeColor(0xFF0A84FF)),
                                        onClick = addColor
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Add,
                                                contentDescription = "添加颜色",
                                                tint = ComposeColor.White,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                top.yukonga.miuix.kmp.basic.ColorPalette(
                    color = ComposeColor(colors.getOrElse(selectedIndex) { colors.firstOrNull() ?: 0xFFD6E9FFL }.toInt()),
                    onColorChanged = { color ->
                        onColorChange(selectedIndex, color.copy(alpha = 1f).toArgb().toLong() and 0xFFFFFFFFL)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    rows = 7,
                    hueColumns = 12,
                    includeGrayColumn = true,
                    showPreview = true,
                    cornerRadius = 16.dp,
                    indicatorRadius = 10.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickSheetLiquidAction(
                        label = "取消",
                        enabled = true,
                        backdrop = backdrop,
                        config = config,
                        modifier = Modifier.weight(1f),
                        height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
                        onClick = onDismiss
                    )
                    QuickSheetLiquidAction(
                        label = "确认",
                        enabled = colors.isNotEmpty(),
                        backdrop = backdrop,
                        config = config,
                        modifier = Modifier.weight(1f),
                        primary = true,
                        height = SleepDownDesignTokens.CenteredDialog.ActionHeight,
                        onClick = onConfirm
                    )
                }
            } else {
                WallpaperPaletteSampler(
                    mode = mode,
                    colors = colors,
                    config = config,
                    onColorChange = onColorChange,
                    onCancel = { onStageChange(CourseColorDialogStage.Picker) },
                    onConfirm = onConfirm,
                    backdrop = backdrop
                )
            }
        },
        content = {}
    )
}
