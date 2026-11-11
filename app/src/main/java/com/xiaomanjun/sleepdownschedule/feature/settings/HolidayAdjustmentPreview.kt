package com.xiaomanjun.sleepdownschedule.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.DialogActionBar
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.sheet.ModalBottomSheet
import com.itos.coui.sheet.SheetToolbar
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalCouiLiquidMaterial
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsPopupBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDatePickerRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseScheduleDate
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.time.LocalDate

internal data class HolidayAdjustmentPreview(
    val year: Int,
    val reviews: List<HolidayReview>,
    val alreadyAdded: List<HolidayPlan>,
    val existingDates: Set<String>,
) {
    fun selectedDates(): Set<String> = buildSet {
        reviews.forEach { review ->
            if (review.restSelected) review.plan.restDates.forEach { add(it.toString()) }
            review.makeups.filter { it.selected }.forEach { add(it.date.toString()) }
        }
    }
}

internal data class HolidayReview(
    val plan: HolidayPlan,
    val restSelected: Boolean,
    val makeups: List<MakeupReview>,
)

internal data class MakeupReview(val date: LocalDate, val source: String, val selected: Boolean)

@Composable
internal fun HolidayAdjustmentPreviewSheet(
    visible: Boolean,
    preview: HolidayAdjustmentPreview,
    config: ScheduleConfigEntity,
    selectedCount: Int,
    replacementCount: Int,
    error: String?,
    coursePreview: (String) -> String,
    onRestSelection: (Int, Boolean) -> Unit,
    onMakeupSelection: (Int, Int, Boolean) -> Unit,
    onMakeupSource: (Int, Int, String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val dark = appUsesDarkTheme(config)
    val backdrop = rememberCouiWindowBackdrop()
    val material = if (Build.VERSION.SDK_INT >= 33 && backdrop != null &&
        LocalCouiMaterialEnabled.current && !LocalCouiLiquidMaterial.current
    ) CouiSheetDefaults.material(dark) else null
    ModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        title = "调休预览",
        dismissDescription = "关闭调休预览",
        dark = dark,
        sheetHeight = LocalConfiguration.current.screenHeightDp.dp,
        colors = CouiSheetDefaults.colors(dark).copy(surface = settingsPageBackground(config)),
        material = material,
        backdrop = backdrop,
        isolateContent = false,
    ) {
        val sheetScene = rememberCenteredDialogSceneBackdrop("holiday-adjustment-preview")
        Column(Modifier.fillMaxSize().centeredDialogSceneProducer(sheetScene)) {
            if (Build.VERSION.SDK_INT >= 34) {
                SheetToolbar(title = "调休预览", dark = dark)
            } else {
                Text("调休预览", Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            CompositionLocalProvider(
                LocalCenteredDialogSceneBackdrop provides sheetScene,
                LocalSettingsPopupBackdrop provides sheetScene,
            ) {
                SleepDownSecondaryPageList(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentTopPadding = 4.dp,
                    contentBottomPadding = 16.dp,
                ) {
                    holidayAdjustmentPreviewItems(preview, config, sheetScene, coursePreview,
                        onRestSelection, onMakeupSelection, onMakeupSource)
                }
            }
            val notice = error ?: "将替换 $replacementCount 天已有安排".takeIf { replacementCount > 0 }
            notice?.let {
                Text(it, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            DialogActionBar(
                confirmAction = CouiDialogAction(
                    "采用 $selectedCount 天",
                    onApply,
                    enabled = visible && selectedCount > 0,
                    style = CouiDialogActionStyle.Primary,
                    colors = CouiDialogDefaults.primaryActionColors(MaterialTheme.colorScheme.primary),
                ),
                dismissAction = CouiDialogAction("取消", onDismiss, enabled = visible),
                dark = dark,
                material = material != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun LazyListScope.holidayAdjustmentPreviewItems(
    preview: HolidayAdjustmentPreview,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    coursePreview: (String) -> String,
    onRestSelection: (Int, Boolean) -> Unit,
    onMakeupSelection: (Int, Int, Boolean) -> Unit,
    onMakeupSource: (Int, Int, String) -> Unit,
) {
    item("preview-summary") {
        SettingsGroup(backdrop, config) {
            SettingsInfoRow("${preview.year} 年节假日", "请按学校安排核对补课对应的原课程日期。")
        }
    }
    preview.reviews.forEachIndexed { planIndex, review ->
        item("holiday-$planIndex") {
            GlassPreferenceSection(review.plan.name) {
                SettingsGroup(backdrop, config) {
                    val rests = review.plan.restDates
                    if (rests.isNotEmpty()) {
                        SettingsToggleRow(
                            title = "停课 · ${rests.size} 天",
                            subtitle = adjustmentDateRange(rests),
                            checked = review.restSelected,
                            backdrop = backdrop,
                            onCheckedChange = { onRestSelection(planIndex, it) },
                        )
                    }
                    review.makeups.forEachIndexed { makeupIndex, makeup ->
                        if (rests.isNotEmpty() || makeupIndex > 0) SettingsDivider()
                        val source = parseScheduleDate(makeup.source)
                        SettingsToggleRow(
                            title = "补课 · ${adjustmentDateLabel(makeup.date)}",
                            subtitle = when {
                                source == null -> "待选择原课程日期"
                                makeup.selected -> coursePreview(makeup.source).substringBefore('\n')
                                else -> "补 ${adjustmentDateLabel(source)} 的课"
                            },
                            checked = makeup.selected,
                            backdrop = backdrop,
                            onCheckedChange = { onMakeupSelection(planIndex, makeupIndex, it) },
                        )
                        if (makeup.selected) {
                            SettingsDivider()
                            SettingsDatePickerRow(
                                title = "原课程日期",
                                value = source?.let(::adjustmentDateLabel).orEmpty(),
                                onValueChange = { onMakeupSource(planIndex, makeupIndex, it) },
                                backdrop = backdrop,
                                config = config,
                                initialDate = source ?: makeup.date,
                            )
                        }
                    }
                }
            }
        }
    }
    if (preview.alreadyAdded.isNotEmpty()) {
        item("already-added") {
            GlassPreferenceSection("已添加") {
                SettingsGroup(backdrop, config) {
                    preview.alreadyAdded.forEachIndexed { index, plan ->
                        if (index > 0) SettingsDivider()
                        val dates = (plan.restDates + plan.makeups.map { it.date }).sorted()
                        SettingsInfoRow(plan.name, "${adjustmentDateRange(dates)} · ${dates.size} 天")
                    }
                }
            }
        }
    }
}

private fun adjustmentDateRange(dates: List<LocalDate>): String =
    if (dates.size == 1) adjustmentDateLabel(dates.single())
    else "${adjustmentDateLabel(dates.first())} 至 ${adjustmentDateLabel(dates.last())}"
