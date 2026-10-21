package com.xiaomanjun.sleepdownschedule.feature.schedule.picker

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.deviceScreenCornerRadiusPx
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsPickerValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsTextFieldRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsValueRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsToggleRow
import com.xiaomanjun.sleepdownschedule.feature.schedule.manager.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** One state machine owns every gesture lock and transition in the picker chain. */
sealed interface CustomizeUiState {
    data object Home : CustomizeUiState
    data object ShowingEntryButton : CustomizeUiState
    data object CapturingSnapshots : CustomizeUiState
    data object EnteringPicker : CustomizeUiState
    data object Picker : CustomizeUiState
    data object SwitchingCombination : CustomizeUiState
    data object CreatingCombination : CustomizeUiState
    data object DeletingCombination : CustomizeUiState
    data object EnteringEditor : CustomizeUiState
    data object Editor : CustomizeUiState
    data object ExitingEditor : CustomizeUiState
    data object Applying : CustomizeUiState
    data object ExitingPicker : CustomizeUiState
}

enum class ScheduleShareType { TOKEN, ICS }

data class QuickScheduleDraft(
    val scheduleId: Int,
    val totalWeeks: Int,
    val currentWeek: Int,
    val autoCurrentWeek: Boolean,
    val hideEmptyWeekends: Boolean,
    val termStartDate: String,
    val showNonCurrentWeekCourses: Boolean = false
)

private fun daysInMonth(year: Int, month: Int): Int =
    LocalDate.of(year, month, 1).lengthOfMonth()

@Stable
class SchedulePickerState {
    var phase by mutableStateOf<CustomizeUiState>(CustomizeUiState.Home)
    var selectedScheduleId by mutableStateOf<Int?>(null)
    var currentSnapshot by mutableStateOf<Bitmap?>(null)
    var currentSnapshotScheduleId by mutableStateOf<Int?>(null)
    var transitionFromSnapshot by mutableStateOf<Bitmap?>(null)
    var snapshotCoverBitmap by mutableStateOf<Bitmap?>(null)
    var originalScheduleId by mutableStateOf<Int?>(null)
    val snapshots = mutableStateMapOf<Int, Bitmap>()
    val orderIds = mutableStateListOf<Int>()
    val temporaryIds = mutableStateListOf<Int>()
    val enterProgress = Animatable(0f)
    val chromeProgress = Animatable(0f)
    val pageSpacingProgress = Animatable(0f)
    val cornerProgress = Animatable(0f)
    val realHomeRevealProgress = Animatable(0f)
    var deletingScheduleId by mutableStateOf<Int?>(null)
    var deleteReveal by mutableFloatStateOf(0f)
    var deleteScale by mutableFloatStateOf(1f)
    var deleteAlpha by mutableFloatStateOf(1f)
    var animatedTargetScheduleId by mutableStateOf<Int?>(null)
    var preparingExit by mutableStateOf(false)
    var renamingScheduleId by mutableStateOf<Int?>(null)
    var renameDraft by mutableStateOf("")

    val overlayVisible: Boolean
        get() = phase !is CustomizeUiState.Home && phase !is CustomizeUiState.ShowingEntryButton

    val interactionsLocked: Boolean
        get() = phase !is CustomizeUiState.Picker

    fun reset() {
        phase = CustomizeUiState.Home
        selectedScheduleId = null
        currentSnapshot = null
        currentSnapshotScheduleId = null
        transitionFromSnapshot = null
        snapshotCoverBitmap = null
        originalScheduleId = null
        deletingScheduleId = null
        deleteReveal = 0f
        deleteScale = 1f
        deleteAlpha = 1f
        animatedTargetScheduleId = null
        preparingExit = false
        renamingScheduleId = null
        renameDraft = ""
        orderIds.clear()
        temporaryIds.clear()
    }
}

@Composable
fun QuickScheduleSettingsSheets(
    draft: QuickScheduleDraft?,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onDraftChange: (QuickScheduleDraft) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: (QuickScheduleDraft, () -> Unit) -> Unit,
    suppressDetailedButton: Boolean = false,
    onDetailedSettings: (Int, () -> Rect?, android.view.Window?, ((() -> Unit) -> Unit)) -> Unit
) {
    // One composition lifetime per opening, including the full exit. Keeping the sheet's
    // Animatables and gesture state mounted in the home page would carry them into a reopen;
    // keying form fields on nullable draft also cleared them while that exit was still drawing.
    var openingDraft by remember { mutableStateOf(draft) }
    if (draft != null && draft.scheduleId != openingDraft?.scheduleId) openingDraft = draft
    val opening = openingDraft ?: return
    key(opening.scheduleId) {
        // Already a root popup sibling. Render the sheet here without another Scaffold/DialogLayout
        // registration: its own slide/drag controller owns the full opening and closing lifetime.
        QuickScheduleSettingsSheetSession(
            initialDraft = opening,
            draft = draft,
            config = config,
            backdrop = backdrop,
            onDraftChange = onDraftChange,
            onDismiss = onDismiss,
            onDismissFinished = {
                openingDraft = null
                onDismissFinished()
            },
            onSave = onSave,
            suppressDetailedButton = suppressDetailedButton,
            onDetailedSettings = onDetailedSettings
        )
    }
}

@Composable
private fun QuickScheduleSettingsSheetSession(
    initialDraft: QuickScheduleDraft,
    draft: QuickScheduleDraft?,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onDraftChange: (QuickScheduleDraft) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: (QuickScheduleDraft, () -> Unit) -> Unit,
    suppressDetailedButton: Boolean,
    onDetailedSettings: (Int, () -> Rect?, android.view.Window?, ((() -> Unit) -> Unit)) -> Unit
) {
    var retainedDraft by remember { mutableStateOf(initialDraft) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var dateMonth by remember { mutableIntStateOf(LocalDate.now().monthValue) }
    var dateDay by remember { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    var totalWeeksText by remember { mutableStateOf(initialDraft.totalWeeks.toString()) }
    var currentWeekText by remember { mutableStateOf(initialDraft.currentWeek.toString()) }
    var detailButtonAnchor by remember { mutableStateOf<QuickSheetDetailAnchor?>(null) }
    var detailLaunching by remember { mutableStateOf(false) }
    val homeView = androidx.compose.ui.platform.LocalView.current
    val sheetDiagnostics = remember { android.util.Log.isLoggable("SleepDownSheet", android.util.Log.DEBUG) }
    val sheetDraws = remember { intArrayOf(0) }
    val density = LocalDensity.current
    val nativeSheet = LocalNativeAlertRenderer.current != null
    val panelGap = 12.dp
    val panelBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding().coerceAtLeast(panelGap)
    // A floating panel nests inside the physical screen corner, sharing its arc centre.
    val panelRadius = (with(density) { deviceScreenCornerRadiusPx().toDp() } - panelGap).coerceAtLeast(16.dp)
    val panelShape = RoundedRectangle(panelRadius)

    fun latestDraft(): QuickScheduleDraft? = retainedDraft

    fun commitDraft(next: QuickScheduleDraft) {
        // Update the sheet-owned source of truth before notifying its parent. Otherwise another
        // control clicked before the parent's next composition can copy an older draft and undo
        // the preceding date/toggle change.
        retainedDraft = next
        onDraftChange(next)
    }

    fun beginDateSelection(value: String) {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
        dateYear = date.year
        dateMonth = date.monthValue
        dateDay = date.dayOfMonth
        showDatePicker = true
    }

    fun saveAndDismiss() {
        val raw = latestDraft() ?: return
        if (saving) return
        val total = totalWeeksText.toIntOrNull()?.coerceIn(1, 60) ?: raw.totalWeeks
        val manual = currentWeekText.toIntOrNull()?.coerceIn(1, total) ?: raw.currentWeek.coerceIn(1, total)
        val current = resolveScheduleCurrentWeek(config, total, manual, raw.termStartDate, raw.autoCurrentWeek)
        val value = raw.copy(totalWeeks = total, currentWeek = current, autoCurrentWeek = true)
        saving = true
        onSave(value) {
            saving = false
            onDismiss()
        }
    }

    SleepDownFloatingSheet(
        show = draft != null,
        title = "课表设置",
        config = config,
        renderInPlace = true,
        cornerRadius = if (nativeSheet) panelRadius else top.yukonga.miuix.kmp.layout.BottomSheetDefaults.cornerRadius,
        startAction = {
            QuickSheetLiquidAction(
                label = "取消",
                enabled = !saving,
                backdrop = backdrop,
                config = config,
                onClick = ::saveAndDismiss
            )
        },
        endAction = {
            QuickSheetLiquidAction(
                label = if (saving) "保存中" else "完成",
                enabled = !saving,
                backdrop = backdrop,
                config = config,
                primary = true,
                onClick = ::saveAndDismiss
            )
        },
        onDismissRequest = { if (!saving) saveAndDismiss() },
        onDismissFinished = onDismissFinished,
        allowDismiss = !saving,
        backgroundColor = Color.Transparent,
        modifier = if (nativeSheet) Modifier.padding(start = panelGap, end = panelGap, bottom = panelBottom) else Modifier,
        surfaceModifier = Modifier.quickSheetBackdropModifier(backdrop, config, blurRadius = 28.dp),
        contentModifier = Modifier.heightIn(max = 590.dp).semantics { testTag = "quick_schedule_sheet" }
            .then(if (sheetDiagnostics) Modifier.onGloballyPositioned { coords ->
                android.util.Log.i("SleepDownSheet", "layout size=${coords.size} root=${coords.boundsInRoot()} window=${coords.positionInWindow()}")
            }.drawWithContent {
                if (sheetDraws[0]++ < 3) {
                    @Suppress("DEPRECATION")
                    val matrix = drawContext.canvas.nativeCanvas.matrix
                    android.util.Log.i("SleepDownSheet", "draw size=$size matrix=$matrix")
                }
                drawContent()
            } else Modifier),
        surfaceContent = if (!nativeSheet) null else { surface ->
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetDialogMaterial(surface, panelShape, backdrop, config)
        }
    ) {
        val sourceWindow = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeDialogWindow.current
        val contentView = androidx.compose.ui.platform.LocalView.current
        retainedDraft.let { value ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (LocalNativeDialogSurface.current) Modifier.verticalScroll(rememberScrollState()) else Modifier.padding(bottom = 18.dp)),
                verticalArrangement = Arrangement.spacedBy(if (LocalNativeDialogSurface.current) 10.dp else 14.dp)
            ) {
                QuickScheduleSettingsGroup(backdrop, config) {
                    SettingsTextFieldRow(
                        title = "总周数",
                        compact = true,
                        value = totalWeeksText,
                        onValueChange = { input ->
                            totalWeeksText = input.filter(Char::isDigit).take(2)
                            totalWeeksText.toIntOrNull()?.coerceIn(1, 60)?.let { total ->
                                val latest = latestDraft() ?: return@let
                                val nextWeek = resolveScheduleCurrentWeek(
                                    config,
                                    total,
                                    latest.currentWeek.coerceAtMost(total),
                                    latest.termStartDate,
                                    latest.autoCurrentWeek
                                )
                                currentWeekText = nextWeek.toString()
                                commitDraft(latest.copy(totalWeeks = total, currentWeek = nextWeek))
                            }
                        },
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                    SettingsTextFieldRow(
                        title = "当前周",
                        compact = true,
                        value = currentWeekText,
                        onValueChange = { input ->
                            currentWeekText = input.filter(Char::isDigit).take(2)
                            latestDraft()?.let { latest ->
                                currentWeekText.toIntOrNull()
                                    ?.coerceIn(1, latest.totalWeeks.coerceAtLeast(1))
                                    ?.let { week -> commitDraft(latest.copy(currentWeek = week)) }
                            }
                        },
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        enabled = false
                    )
                    SettingsValueRow(
                        "学期状态", scheduleTermStatusDescription(
                            config.copy(
                                totalWeeks = value.totalWeeks,
                                currentWeek = value.currentWeek,
                                autoCurrentWeek = true,
                                termStartDate = value.termStartDate.ifBlank { null }
                            ), LocalDate.now()
                        ), compact = true
                    )
                    SettingsToggleRow(
                        title = "隐藏空周末",
                        compact = true,
                        subtitle = "周六、周日无课时自动收起",
                        checked = value.hideEmptyWeekends,
                        backdrop = backdrop,
                        onCheckedChange = { checked ->
                            latestDraft()?.let { latest ->
                                commitDraft(latest.copy(hideEmptyWeekends = checked))
                            }
                        }
                    )
                    SettingsToggleRow(
                        title = "显示非本周课程",
                        compact = true,
                        subtitle = "以灰色显示，与本周课程重叠时隐藏",
                        checked = value.showNonCurrentWeekCourses,
                        backdrop = backdrop,
                        onCheckedChange = { checked ->
                            latestDraft()?.let { commitDraft(it.copy(showNonCurrentWeekCourses = checked)) }
                        }
                    )
                    SettingsPickerValueRow(
                        title = "学期开始日期",
                        compact = true,
                        valueBelowTitle = true,
                        value = value.termStartDate,
                        onClick = { beginDateSelection(value.termStartDate) }
                    )
                }
                if (!suppressDetailedButton) {
                    if (backdrop != null) LiquidButton(
                        onClick = {
                            val raw = latestDraft() ?: return@LiquidButton
                            val anchor = detailButtonAnchor ?: return@LiquidButton
                            if (saving || detailLaunching) return@LiquidButton
                            val total = totalWeeksText.toIntOrNull()?.coerceIn(1, 60) ?: raw.totalWeeks
                            val current = currentWeekText.toIntOrNull()?.coerceIn(1, total)
                                ?: raw.currentWeek.coerceIn(1, total)
                            val value = raw.copy(totalWeeks = total, currentWeek = current)
                            detailLaunching = true
                            onDetailedSettings(value.scheduleId, anchor::resolve, sourceWindow) { afterSaved ->
                                saving = true
                                onSave(value) {
                                    saving = false
                                    detailLaunching = false
                                    afterSaved()
                                }
                            }
                        },
                        backdrop = backdrop,
                        enabled = !saving && !detailLaunching,
                        preserveMaterialWhenDisabled = true,
                        nativeDarkOverride = appUsesDarkTheme(config),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                detailButtonAnchor = QuickSheetDetailAnchor(coordinates, contentView, homeView)
                            },
                        height = 52.dp,
                        blurRadius = 12.dp,
                        lensHeight = 30.dp,
                        lensAmount = 42.dp,
                        surfaceColor = if (appUsesDarkTheme(config)) {
                            Color(0xFF272C36).copy(alpha = 0.86f)
                        } else {
                            Color(0xFFF3F6FB).copy(alpha = 0.84f)
                        },
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        Text("详细设置", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    } else Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onGloballyPositioned { coordinates ->
                                detailButtonAnchor = QuickSheetDetailAnchor(coordinates, contentView, homeView)
                            }
                            .clip(Capsule())
                            .background(
                                if (appUsesDarkTheme(config)) Color(0xFF30343D)
                                else Color(0xFFE8ECF3)
                            )
                            .clickable(enabled = !saving && !detailLaunching) {
                                val raw = latestDraft() ?: return@clickable
                                val anchor = detailButtonAnchor ?: return@clickable
                                if (saving || detailLaunching) return@clickable
                                val total = totalWeeksText.toIntOrNull()?.coerceIn(1, 60) ?: raw.totalWeeks
                                val current = totalWeeksText.toIntOrNull()?.let {
                                    currentWeekText.toIntOrNull()?.coerceIn(1, total)
                                } ?: raw.currentWeek.coerceIn(1, total)
                                val updated = raw.copy(totalWeeks = total, currentWeek = current)
                                detailLaunching = true
                                onDetailedSettings(updated.scheduleId, anchor::resolve, sourceWindow) { afterSaved ->
                                    saving = true
                                    onSave(updated) {
                                        saving = false
                                        detailLaunching = false
                                        afterSaved()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("详细设置", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                } else Spacer(Modifier.fillMaxWidth().height(52.dp))
            }
        }
    }

    SleepDownFloatingSheet(
        show = showDatePicker && draft != null,
        title = "选择日期",
        config = config,
        renderInPlace = true,
        cornerRadius = if (nativeSheet) panelRadius else top.yukonga.miuix.kmp.layout.BottomSheetDefaults.cornerRadius,
        startAction = {
            QuickSheetLiquidAction(
                label = "取消",
                enabled = true,
                backdrop = backdrop,
                config = config,
                onClick = { showDatePicker = false }
            )
        },
        endAction = {
            QuickSheetLiquidAction(
                label = "确定",
                enabled = true,
                backdrop = backdrop,
                config = config,
                primary = true,
                onClick = {
                    val safeDay = dateDay.coerceAtMost(daysInMonth(dateYear, dateMonth))
                    latestDraft()?.let {
                        val nextDate = "%04d-%02d-%02d".format(dateYear, dateMonth, safeDay)
                        val nextWeek = resolveScheduleCurrentWeek(
                            config,
                            it.totalWeeks,
                            it.currentWeek,
                            nextDate,
                            it.autoCurrentWeek
                        )
                        currentWeekText = nextWeek.toString()
                        commitDraft(it.copy(termStartDate = nextDate, currentWeek = nextWeek))
                    }
                    showDatePicker = false
                }
            )
        },
        onDismissRequest = { showDatePicker = false },
        backgroundColor = Color.Transparent,
        modifier = if (nativeSheet) Modifier.padding(start = panelGap, end = panelGap, bottom = panelBottom) else Modifier,
        surfaceModifier = Modifier.quickSheetBackdropModifier(backdrop, config, blurRadius = 28.dp),
        contentModifier = Modifier.heightIn(max = 330.dp),
        surfaceContent = if (!nativeSheet) null else { surface ->
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.QuickSheetDialogMaterial(surface, panelShape, backdrop, config)
        }
    ) {
        val maxDay = daysInMonth(dateYear, dateMonth)
        LaunchedEffect(maxDay) {
            if (dateDay > maxDay) dateDay = maxDay
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = dateYear,
                onValueChange = { dateYear = it },
                range = 2000..2100,
                visibleItemCount = 3,
                label = { "${it}年" },
                textStyle = if (LocalNativeDialogSurface.current) top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title2
                    else top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1,
                modifier = Modifier.weight(1.25f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = dateMonth,
                onValueChange = { dateMonth = it },
                range = 1..12,
                visibleItemCount = 3,
                label = { "${it}月" },
                textStyle = if (LocalNativeDialogSurface.current) top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title2
                    else top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1,
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = dateDay.coerceAtMost(maxDay),
                onValueChange = { dateDay = it },
                range = 1..maxDay,
                visibleItemCount = 3,
                label = { "${it}日" },
                textStyle = if (LocalNativeDialogSurface.current) top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title2
                    else top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickScheduleSettingsGroup(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    if (LocalNativeDialogSurface.current) {
        Column(Modifier.fillMaxWidth(), content = content)
    } else {
        SettingsGroup(
            backdrop = backdrop,
            config = config,
            modifier = Modifier.fillMaxWidth(),
            surfaceModifier = Modifier.quickSheetBackdropModifier(backdrop, config, 16.dp, inner = true),
            surfaceColorOverride = Color.Transparent,
            content = content
        )
    }
}

@Composable
fun rememberSchedulePickerState(): SchedulePickerState = remember { SchedulePickerState() }

fun AppState.forSchedule(scheduleId: Int): AppState {
    val targetConfig = allConfigs.firstOrNull { it.id == scheduleId } ?: defaultConfig(scheduleId)
    val targetPeriods = allPeriods.filter { it.scheduleId == scheduleId }.ifEmpty { defaultPeriods(scheduleId) }
    return copy(
        courses = allCourses.filter { it.scheduleId == scheduleId },
        config = targetConfig,
        periods = targetPeriods,
        loaded = true
    )
}

@Composable
fun SchedulePickerOverlay(
    pickerState: SchedulePickerState,
    allState: AppState,
    backdrop: Backdrop?,
    onPageSelected: (Int) -> Unit,
    onApply: (Int) -> Unit,
    onClose: () -> Unit,
    onBack: (Int) -> Unit,
    onCreate: () -> Unit,
    onShare: (Int, ScheduleShareType) -> Unit,
    onCustomize: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onDeleteRequest: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dialogBackdrop: Backdrop? = backdrop,
    contentStartInset: Dp = 0.dp,
    contentEndInset: Dp = 0.dp,
    contentTopInset: Dp = 0.dp
) {
    if (!pickerState.overlayVisible) return

    val orderedProfiles = pickerState.orderIds.mapNotNull { id -> allState.schedules.firstOrNull { it.id == id } }
    val selectedId = pickerState.selectedScheduleId ?: orderedProfiles.firstOrNull()?.id ?: return
    val selectedConfig = allState.allConfigs.firstOrNull { it.id == selectedId } ?: defaultConfig(selectedId)
    val initialPage = orderedProfiles.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { orderedProfiles.size }
    val visualProfile = orderedProfiles.getOrNull(pagerState.currentPage)
        ?: orderedProfiles.firstOrNull { it.id == selectedId }
    val visualConfig = visualProfile?.id
        ?.let { id -> allState.allConfigs.firstOrNull { it.id == id } ?: defaultConfig(id) }
        ?: selectedConfig
    val scope = rememberCoroutineScope()
    val managerBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.PickerScene,
        providerId = "schedule-picker-manager"
    )
    var deleteConfirmTarget by remember { mutableStateOf<ScheduleProfileEntity?>(null) }
    var showShareOptions by remember { mutableStateOf(false) }
    var targetCardBounds by remember { mutableStateOf<Rect?>(null) }
    val renameFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val renameEditing = pickerState.renamingScheduleId != null
    val phaseInputEnabled = !pickerState.interactionsLocked
    val pagerInputEnabled = phaseInputEnabled && pickerState.deletingScheduleId == null && !renameEditing

    fun cancelRename() {
        pickerState.renamingScheduleId = null
        pickerState.renameDraft = ""
    }

    fun commitRename() {
        val id = pickerState.renamingScheduleId ?: return
        val name = pickerState.renameDraft.trim()
        if (name.isNotEmpty()) onRename(id, name)
        cancelRename()
    }

    LaunchedEffect(pickerState.renamingScheduleId) {
        if (pickerState.renamingScheduleId != null) {
            withFrameNanos { }
            withFrameNanos { }
            renameFocusRequester.requestFocus()
            softwareKeyboardController?.show()
        }
    }

    fun cancelDeleteReveal(): Boolean {
        if (pickerState.deletingScheduleId == null) return false
        pickerState.deletingScheduleId = null
        pickerState.deleteReveal = 0f
        return true
    }

    LaunchedEffect(pagerState, pagerInputEnabled, orderedProfiles) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (pagerInputEnabled) orderedProfiles.getOrNull(page)?.id?.let(onPageSelected)
            }
    }
    LaunchedEffect(selectedId, orderedProfiles.map { it.id }, pickerState.animatedTargetScheduleId) {
        val targetPage = orderedProfiles.indexOfFirst { it.id == selectedId }
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            if (pickerState.animatedTargetScheduleId == selectedId) {
                pagerState.animateScrollToPage(
                    targetPage,
                    animationSpec = tween(
                        durationMillis = 520,
                        easing = CubicBezierEasing(0.2f, 0.72f, 0.2f, 1f)
                    )
                )
                pickerState.animatedTargetScheduleId = null
            } else {
                pagerState.scrollToPage(targetPage)
            }
        } else if (targetPage >= 0 && pickerState.animatedTargetScheduleId == selectedId) {
            pickerState.animatedTargetScheduleId = null
        }
    }

    // Keep the picker in the Activity's root composition. A platform Popup is a separate window:
    // the MIUIX sheet was consequently inserted below it, became invisible, and still consumed
    // every touch. Keeping both overlays in one root also makes the sheet backdrop sample the
    // complete picker without crossing a window boundary.
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        val density = LocalDensity.current
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val landscape = screenWidth > screenHeight
        val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
        val rightPanelWidth = (screenWidth - contentStartInset - contentEndInset - 24.dp).coerceAtLeast(1.dp)
        val pagerWidth = if (landscape) rightPanelWidth else screenWidth
        val availableCardHeight = if (landscape) {
            (screenHeight - contentTopInset - safeInsets.calculateTopPadding() - safeInsets.calculateBottomPadding() - 176.dp)
                .coerceAtLeast(1.dp)
        } else (screenHeight - 256.dp).coerceAtLeast(320.dp)
        // Landscape snapshots include both current viewport captures and persisted portrait
        // previews. Card geometry belongs to this viewport, never to the selected bitmap.
        val snapshotAspect = if (landscape) {
            (screenWidth - contentStartInset - contentEndInset).value /
                (screenHeight - contentTopInset).value.coerceAtLeast(1f)
        } else pickerState.currentSnapshot
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { it.width.toFloat() / it.height.toFloat() }
            ?: (screenWidth.value / screenHeight.value)
        val cardWidth = if (landscape) minOf(pagerWidth * 0.72f, availableCardHeight * snapshotAspect)
            else minOf(screenWidth * 0.72f, availableCardHeight * snapshotAspect).coerceAtLeast(220.dp)
        val cardHeight = cardWidth / snapshotAspect
        val cardWidthFraction = cardWidth.value / pagerWidth.value
        val progress = pickerState.enterProgress.value.coerceIn(0f, 1f)
        val chrome = pickerState.chromeProgress.value.coerceIn(0f, 1f)
        val pageSpacing = if (landscape) -cardWidth * 0.64f
            else (-140 + 130 * pickerState.pageSpacingProgress.value).dp

        val isExiting = pickerState.phase is CustomizeUiState.Applying ||
            pickerState.phase is CustomizeUiState.ExitingPicker
        val backdropAlpha = if (isExiting) {
            // Keep the real home completely covered until the Morph nearly fills the window.
            (progress * 4f).coerceIn(0f, 1f)
        } else {
            progress
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
                .glassBackdropProducer(managerBackdrop)
                .pointerInput(phaseInputEnabled, pickerState.deletingScheduleId) {
                    detectTapGestures(onTap = {
                        if (pickerState.phase is CustomizeUiState.Picker) {
                            cancelDeleteReveal()
                        }
                    })
                }
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = if (landscape) contentTopInset else 0.dp)
                .then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                    else Modifier.statusBarsPadding().navigationBarsPadding())
                .graphicsLayer {
                    alpha = chrome
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .then(if (landscape) Modifier.align(Alignment.End)
                        .padding(end = contentEndInset + 12.dp).width(rightPanelWidth) else Modifier)
                    .padding(horizontal = 18.dp).height(if (landscape) 52.dp else 92.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PickerHeaderButton("取消", phaseInputEnabled, managerBackdrop, visualConfig) {
                    if (renameEditing) cancelRename() else if (!cancelDeleteReveal()) onClose()
                }
                Spacer(Modifier.weight(1f))
                if (renameEditing) {
                    BasicTextField(
                        value = pickerState.renameDraft,
                        onValueChange = { pickerState.renameDraft = it.take(24) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitRename() }),
                        modifier = Modifier.width(150.dp).focusRequester(renameFocusRequester)
                    )
                } else {
                    Text(
                        text = visualProfile?.name.orEmpty(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = pagerInputEnabled) {
                            visualProfile?.let {
                                pickerState.renamingScheduleId = it.id
                                pickerState.renameDraft = it.name
                            }
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                PickerHeaderButton(
                    label = if (renameEditing) "完成" else "应用",
                    enabled = if (renameEditing) phaseInputEnabled else pagerInputEnabled,
                    backdrop = managerBackdrop,
                    config = visualConfig,
                    primary = true
                ) { if (renameEditing) commitRename() else onApply(selectedId) }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                // The centered card stays in the right pane, while the pager's viewport spans
                // the whole window so preceding cards can pass behind the floating sidebar.
                val endPadding = if (landscape) (rightPanelWidth - cardWidth) / 2 + contentEndInset + 12.dp
                    else (maxWidth - cardWidth) / 2
                val startPadding = (maxWidth - cardWidth - endPadding).coerceAtLeast(0.dp)
                val behindSidebarPages = if (landscape) {
                    ceil(startPadding.value / (cardWidth + pageSpacing).value.coerceAtLeast(1f)).toInt()
                        .coerceAtMost(orderedProfiles.lastIndex.coerceAtLeast(0))
                } else 1
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSize = PageSize.Fixed(cardWidth),
                    pageSpacing = pageSpacing,
                    contentPadding = PaddingValues(start = startPadding, end = endPadding),
                    beyondViewportPageCount = behindSidebarPages,
                    userScrollEnabled = pagerInputEnabled,
                    // The Room list and our in-memory order can publish on adjacent frames
                    // while a newly-created profile is inserted. Never index the captured list
                    // blindly from Pager's key lookup during that one-frame size transition.
                    key = { page -> orderedProfiles.getOrNull(page)?.id ?: (Int.MIN_VALUE + page) }
                ) { page ->
                    val relativePosition = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                    val pageOffset = abs(relativePosition).coerceAtMost(1.6f)
                    val targetScale = if (landscape) (1f - pageOffset * 0.12f).coerceAtLeast(0.78f)
                        else (1f - pageOffset * 0.35f).coerceAtLeast(0.45f)
                    val stackDepth = (1f - pageOffset).coerceIn(0f, 1f)
                    val lowerLayerAmount = pageOffset.coerceIn(0f, 1f)
                    val pivotX = if (landscape) 0.5f
                        else (0.5f - relativePosition / cardWidthFraction).coerceIn(-0.8f, 1.8f)
                    val profile = orderedProfiles.getOrNull(page)
                    val deleting = profile?.id == pickerState.deletingScheduleId
                    Box(
                        Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .then(
                                if (profile?.id == selectedId) {
                                    Modifier.onGloballyPositioned { targetCardBounds = it.boundsInRoot() }
                                } else {
                                    Modifier
                                }
                            )
                            .zIndex(10f - pageOffset)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(pivotX, 0.5f)
                                val deletionScale = if (deleting) pickerState.deleteScale else 1f
                                scaleX = targetScale * deletionScale
                                scaleY = targetScale * deletionScale
                                alpha = (if (deleting) pickerState.deleteAlpha else 1f) *
                                    (1f - 0.06f * lowerLayerAmount)
                                shape = RoundedRectangle(34.dp)
                                clip = false
                                shadowElevation = with(density) { (6.dp + 18.dp * stackDepth).toPx() }
                                ambientShadowColor = Color.Black.copy(alpha = 0.18f + 0.10f * stackDepth)
                                spotShadowColor = Color.Black.copy(alpha = 0.24f + 0.12f * stackDepth)
                            }
                    ) {
                        if (profile != null) {
                            val config = allState.allConfigs.firstOrNull { it.id == profile.id } ?: defaultConfig(profile.id)
                            val courses = allState.allCourses.filter { it.scheduleId == profile.id }
                            val periods = allState.allPeriods.filter { it.scheduleId == profile.id }.ifEmpty { defaultPeriods(profile.id) }
                            val snapshot = if (
                                profile.id == selectedId &&
                                pickerState.currentSnapshotScheduleId == profile.id
                            ) {
                                pickerState.currentSnapshot
                            } else {
                                pickerState.snapshots[profile.id]
                            }
                            // Keep the card node stable while a newly captured bitmap replaces
                            // its fallback. Crossfading the whole card duplicated the expensive
                            // glass/card tree for several frames and produced a visible flash.
                            ScheduleCarouselCard(
                                profile = profile,
                                config = config,
                                courses = courses,
                                periods = periods,
                                backdrop = managerBackdrop,
                                snapshot = snapshot,
                                selected = profile.id == allState.schedules.firstOrNull { it.isActive }?.id,
                                isCentered = page == pagerState.currentPage && pagerInputEnabled,
                                deleteReveal = if (profile.id == pickerState.deletingScheduleId) pickerState.deleteReveal else 0f,
                                snapshotFallbackOnly = !landscape,
                                modifier = Modifier.fillMaxSize(),
                                onTap = {
                                    if (pagerInputEnabled) {
                                        if (page == pagerState.currentPage && profile.id == selectedId) {
                                            onApply(profile.id)
                                        } else {
                                            pickerState.animatedTargetScheduleId = profile.id
                                            onPageSelected(profile.id)
                                        }
                                    }
                                },
                                onLongPress = {
                                    if (pagerInputEnabled && orderedProfiles.size > 1) {
                                        pickerState.deletingScheduleId = profile.id
                                        pickerState.deleteReveal = 1f
                                    }
                                },
                                onDeleteReveal = { pickerState.deleteReveal = it },
                                onDeleteClick = { deleteConfirmTarget = profile }
                            )
                            if (lowerLayerAmount > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedRectangle(34.dp))
                                        .background(Color(0xFF7A7A80).copy(alpha = 0.055f * lowerLayerAmount))
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = profile.id == selectedId && pickerState.preparingExit,
                                enter = androidx.compose.animation.fadeIn(tween(120)),
                                exit = androidx.compose.animation.fadeOut(tween(100)),
                                modifier = Modifier.align(Alignment.Center).zIndex(30f)
                            ) {
                                Box(
                                    Modifier
                                        .size(54.dp)
                                        .clip(Capsule())
                                        .background(Color.Black.copy(alpha = 0.38f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                                        color = Color.White,
                                        size = 28.dp,
                                        strokeWidth = 2.5.dp,
                                        orbitingDotSize = 2.5.dp
                                    )
                                }
                            }
                        }
                    }
                }

            }

            Column(
                Modifier
                    .then(if (landscape) Modifier.align(Alignment.End)
                        .padding(end = contentEndInset + 12.dp).width(rightPanelWidth) else Modifier)
                    .fillMaxWidth().height(if (landscape) 108.dp else 138.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val dotSize = 7.dp
                val dotSpacing = 9.dp
                val indicatorPadding = 10.dp
                val indicatorWidth = indicatorPadding * 2 +
                    dotSize * orderedProfiles.size +
                    dotSpacing * (orderedProfiles.size - 1).coerceAtLeast(0)
                val continuousPage = (
                    pagerState.currentPage + pagerState.currentPageOffsetFraction
                    ).coerceIn(0f, (orderedProfiles.size - 1).coerceAtLeast(0).toFloat())
                Box(
                    Modifier
                        .height(38.dp)
                        .offset(y = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(indicatorWidth)
                            .height(25.dp)
                            .clip(Capsule())
                            .background(Color.White.copy(alpha = 0.13f))
                    ) {
                        Row(
                            Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = indicatorPadding),
                            horizontalArrangement = Arrangement.spacedBy(dotSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            orderedProfiles.forEachIndexed { index, _ ->
                                Box(
                                    Modifier
                                        .size(dotSize)
                                        .clip(Capsule())
                                        .background(Color.Gray.copy(alpha = 0.72f))
                                        .clickable(enabled = pagerInputEnabled) {
                                            scope.launch { pagerState.animateScrollToPage(index) }
                                        }
                                )
                            }
                        }
                        val step = dotSize + dotSpacing
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = indicatorPadding - 1.dp + step * continuousPage)
                                .size(9.dp)
                                .clip(Capsule())
                                .background(Color.White)
                        )
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    val compact = maxWidth < 360.dp
                    val customizeWidth = if (compact) 160.dp else 176.dp
                    val buttonSize = if (compact) 48.dp else 52.dp
                    val sideOffset = customizeWidth / 2 + if (compact) 52.dp else 58.dp
                    PickerIconLiquidButton(
                        icon = R.drawable.ic_share_schedule,
                        description = "分享课表",
                        enabled = pagerInputEnabled,
                        backdrop = managerBackdrop,
                        config = visualConfig,
                        tint = Color(0xFF34C759),
                        modifier = Modifier.align(Alignment.Center).offset(x = -sideOffset).size(buttonSize)
                    ) { showShareOptions = true }
                    PickerTextButton(
                        "课表设置",
                        pagerInputEnabled,
                        managerBackdrop,
                        visualConfig,
                        modifier = Modifier.width(customizeWidth)
                    ) {
                        onCustomize(selectedId)
                    }
                    PickerIconLiquidButton(
                        icon = R.drawable.ic_add_course,
                        description = "新建课表",
                        enabled = pagerInputEnabled,
                        backdrop = managerBackdrop,
                        config = visualConfig,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).offset(x = sideOffset).size(buttonSize)
                    ) { onCreate() }
                }
            }
        }

        // The morph and the pager share the measured destination rectangle. The pager card
        // stays at its final geometry underneath; only this bitmap owns enter/exit geometry.
        val morphVisible = pickerState.phase is CustomizeUiState.EnteringPicker ||
            (!pickerState.preparingExit && (
                pickerState.phase is CustomizeUiState.Applying ||
                    pickerState.phase is CustomizeUiState.ExitingPicker
                ))
        if (morphVisible) {
            pickerState.currentSnapshot
                ?.takeIf {
                    isExiting || pickerState.currentSnapshotScheduleId == selectedId
                }
                ?.let { bitmap ->
                val bounds = targetCardBounds
                val targetWidth = bounds?.let { with(density) { it.width.toDp() } } ?: cardWidth
                val targetHeight = bounds?.let { with(density) { it.height.toDp() } } ?: cardHeight
                val targetOffsetX = bounds?.let {
                    with(density) { (it.center.x - constraints.maxWidth / 2f).toDp() }
                } ?: 0.dp
                val targetOffsetY = bounds?.let {
                    with(density) { (it.center.y - constraints.maxHeight / 2f).toDp() }
                } ?: (-13).dp
                val sourceWidth = if (landscape) screenWidth - contentStartInset - contentEndInset else screenWidth
                val sourceHeight = if (landscape) screenHeight - contentTopInset else screenHeight
                val sourceOffsetX = if (landscape) (contentStartInset - contentEndInset) / 2 else 0.dp
                val sourceOffsetY = if (landscape) contentTopInset / 2 else 0.dp
                val animatedWidth = sourceWidth + (targetWidth - sourceWidth) * progress
                val animatedHeight = sourceHeight + (targetHeight - sourceHeight) * progress
                val morphModifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = sourceOffsetX + (targetOffsetX - sourceOffsetX) * progress,
                        y = sourceOffsetY + (targetOffsetY - sourceOffsetY) * progress)
                    .width(animatedWidth)
                    .height(animatedHeight)
                    .graphicsLayer {
                        shape = RoundedRectangle(34.dp * pickerState.cornerProgress.value)
                        clip = true
                    }
                    .zIndex(50f)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = morphModifier.graphicsLayer {
                        val targetBlend = if (pickerState.transitionFromSnapshot != null && isExiting) {
                            ((0.82f - progress) / 0.52f).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        alpha = (1f - targetBlend) * (1f - pickerState.realHomeRevealProgress.value)
                    }
                )
                pickerState.transitionFromSnapshot?.let { targetBitmap ->
                    val targetBlend = if (isExiting) {
                        ((0.82f - progress) / 0.52f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    if (targetBlend > 0f) {
                        Image(
                            bitmap = targetBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = morphModifier.graphicsLayer {
                                alpha = targetBlend * (1f - pickerState.realHomeRevealProgress.value)
                            }
                        )
                    }
                }
            }
        }

    }

    deleteConfirmTarget?.let { profile ->
        LiquidAlertDialog(
            title = "删除课表",
            message = "确定要删除「${profile.name}」吗？该课表内的课程会一起删除。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { deleteConfirmTarget = null },
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                    deleteConfirmTarget = null
                    onDeleteRequest(profile.id)
                }
            ),
            backdrop = dialogBackdrop,
            config = allState.allConfigs.firstOrNull { it.id == profile.id } ?: defaultConfig(profile.id),
            onDismissRequest = { deleteConfirmTarget = null }
        )
    }
    if (showShareOptions) {
        LiquidAlertDialog(
            title = "分享课表",
            message = "选择分享 SleepDown 课表口令，或导出可被日历应用识别的 ICS 文件。",
            actions = listOf(
                LiquidAlertAction("分享课表口令", LiquidAlertActionStyle.Primary) {
                    showShareOptions = false
                    onShare(selectedId, ScheduleShareType.TOKEN)
                },
                LiquidAlertAction("分享 ICS 文件", LiquidAlertActionStyle.Secondary) {
                    showShareOptions = false
                    onShare(selectedId, ScheduleShareType.ICS)
                },
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { showShareOptions = false }
            ),
            backdrop = dialogBackdrop,
            config = allState.allConfigs.firstOrNull { it.id == selectedId } ?: defaultConfig(selectedId),
            onDismissRequest = { showShareOptions = false }
        )
    }
}

@Composable
private fun PickerHeaderButton(
    label: String,
    enabled: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnClick by rememberUpdatedState(onClick)
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (latestEnabled) latestOnClick() },
            enabled = enabled,
            preserveMaterialWhenDisabled = true,
            backdrop = backdrop,
            modifier = Modifier.width(86.dp),
            height = 40.dp,
            nativeDarkOverride = true,
            blurRadius = 10.dp,
            lensHeight = 26.dp,
            lensAmount = 34.dp,
            tint = if (primary) MaterialTheme.colorScheme.primary else Color.Unspecified,
            surfaceColor = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = 0.46f) else Color.Black.copy(alpha = 0.30f),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Text(label, color = Color.White, modifier = Modifier.width(86.dp).clip(Capsule()).background(if (primary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun PickerIconLiquidButton(
    icon: Int,
    description: String,
    enabled: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnClick by rememberUpdatedState(onClick)
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (latestEnabled) latestOnClick() },
            enabled = enabled,
            preserveMaterialWhenDisabled = true,
            backdrop = backdrop,
            modifier = modifier,
            height = 52.dp,
            nativeDarkOverride = true,
            tint = tint,
            surfaceColor = tint.copy(alpha = 0.22f),
            blurRadius = 10.dp,
            lensHeight = 30.dp,
            lensAmount = 44.dp,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(23.dp))
        }
    } else {
        Box(modifier.clip(Capsule()).background(tint.copy(alpha = 0.5f)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun PickerTextButton(label: String, enabled: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnClick by rememberUpdatedState(onClick)
    val buttonModifier = if (modifier == Modifier) Modifier.width(176.dp) else modifier
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (latestEnabled) latestOnClick() },
            enabled = enabled,
            preserveMaterialWhenDisabled = true,
            backdrop = backdrop,
            modifier = buttonModifier,
            height = 52.dp,
            // This overlay deliberately uses white text in both application themes.
            nativeDarkOverride = true,
            blurRadius = 10.dp,
            lensHeight = 26.dp,
            lensAmount = 34.dp,
            chromaticAberration = false,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.42f), fontWeight = FontWeight.Medium, modifier = buttonModifier.clip(Capsule()).background(Color.White.copy(alpha = 0.12f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp))
    }
}
