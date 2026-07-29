package com.example.courseschedule

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import java.time.LocalDate

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
    val termStartDate: String
)

private fun daysInMonth(year: Int, month: Int): Int =
    LocalDate.of(year, month, 1).lengthOfMonth()

@Composable
internal fun quickSheetBackdropModifier(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    blurRadius: androidx.compose.ui.unit.Dp,
    inner: Boolean = false,
    centered: Boolean = false
): Modifier {
    val shape = when {
        inner -> RoundedCornerShape(24.dp)
        centered -> RoundedCornerShape(28.dp)
        else -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }
    val dark = appUsesDarkTheme(config)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || backdrop == null) {
        return Modifier
            .clip(shape)
            .background(
                if (inner) {
                    if (dark) Color(0xFF25272C) else Color(0xFFF0F3F8)
                } else {
                    settingsPageBackground(settingsVisualConfig(config))
                }
            )
    }
    return Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(blurRadius.toPx()) },
        highlight = null,
        shadow = null,
        onDrawSurface = {
            drawRect(
                when {
                    inner && dark -> Color(0xFF252B35).copy(alpha = 0.88f)
                    inner -> Color(0xFFE7EDF7).copy(alpha = 0.86f)
                    dark -> Color(0xFF111318).copy(alpha = 0.74f)
                    else -> Color(0xFFF8FAFD).copy(alpha = 0.72f)
                }
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = if (inner) {
                        if (dark) listOf(Color(0xFF8CB8FF).copy(alpha = 0.12f), Color.Transparent)
                        else listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                    } else {
                        if (dark) listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)
                        else listOf(Color.White.copy(alpha = 0.34f), Color.Transparent)
                    },
                    center = center,
                    radius = max(size.width, size.height) * if (inner) 0.66f else 0.74f
                )
            )
        }
    )
}

@Composable
internal fun QuickSheetLiquidAction(
    label: String,
    enabled: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    primary: Boolean = false,
    destructive: Boolean = false,
    modifier: Modifier = Modifier.width(84.dp),
    height: androidx.compose.ui.unit.Dp = 38.dp,
    onClick: () -> Unit
) {
    if (backdrop != null) {
        val dark = appUsesDarkTheme(config)
        val neutralSurface = if (dark) {
            Color(0xFF272C36).copy(alpha = 0.92f)
        } else {
            Color(0xFFF3F6FB).copy(alpha = 0.90f)
        }
        val actionSurfaceColor = when {
            primary -> Color(0xFF0A84FF).copy(alpha = 0.88f)
            destructive -> Color(0xFFFF453A).copy(alpha = 0.88f)
            else -> neutralSurface
        }
        val actionTint = when {
            primary -> Color(0xFF0A84FF)
            destructive -> Color(0xFFFF453A)
            else -> Color.Unspecified
        }
        val actionTextColor = when {
            primary || destructive -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            modifier = modifier,
            height = height,
            blurRadius = 18.dp,
            lensHeight = 8.dp,
            lensAmount = 12.dp,
            tint = actionTint,
            surfaceColor = actionSurfaceColor,
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Text(
                label,
                color = actionTextColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    } else {
        val dark = appUsesDarkTheme(config)
        val background = when {
            destructive -> Color(0xFFFF453A)
            primary -> Color(0xFF0A84FF)
            dark -> Color(0xFF30343D)
            else -> Color(0xFFE8ECF3)
        }
        val btnTextColor = when {
            primary || destructive -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        Box(
            modifier = modifier
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(background.copy(alpha = if (enabled) 0.94f else 0.46f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = btnTextColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

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
    onDetailedSettings: (Int, Rect, ((() -> Unit) -> Unit)) -> Unit
) {
    var retainedDraft by remember { mutableStateOf(draft) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var dateMonth by remember { mutableIntStateOf(LocalDate.now().monthValue) }
    var dateDay by remember { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    var totalWeeksText by remember(draft?.scheduleId) { mutableStateOf(draft?.totalWeeks?.toString().orEmpty()) }
    var currentWeekText by remember(draft?.scheduleId) { mutableStateOf(draft?.currentWeek?.toString().orEmpty()) }
    var detailButtonBounds by remember(draft?.scheduleId) { mutableStateOf<Rect?>(null) }
    var detailLaunching by remember(draft?.scheduleId) { mutableStateOf(false) }

    LaunchedEffect(draft?.scheduleId) {
        if (draft != null) {
            retainedDraft = draft
            totalWeeksText = draft.totalWeeks.toString()
            currentWeekText = draft.currentWeek.toString()
        }
    }

    fun latestDraft(): QuickScheduleDraft? = retainedDraft ?: draft

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
        val value = raw.copy(totalWeeks = total, currentWeek = current)
        saving = true
        onSave(value) {
            saving = false
            onDismiss()
        }
    }

    top.yukonga.miuix.kmp.overlay.OverlayBottomSheet(
        show = draft != null,
        title = "课表设置",
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
        onDismissFinished = {
            retainedDraft = null
            onDismissFinished()
        },
        allowDismiss = !saving,
        backgroundColor = Color.Transparent,
        modifier = Modifier.heightIn(max = 590.dp),
        surfaceModifier = quickSheetBackdropModifier(backdrop, config, blurRadius = 28.dp)
    ) {
        retainedDraft?.let { value ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsGroup(
                    backdrop = backdrop,
                    config = config,
                    modifier = Modifier.fillMaxWidth(),
                    surfaceModifier = quickSheetBackdropModifier(
                        backdrop = backdrop,
                        config = config,
                        blurRadius = 16.dp,
                        inner = true
                    ),
                    surfaceColorOverride = Color.Transparent
                ) {
                    SettingsTextFieldRow(
                        title = "总周数",
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
                    SettingsDivider()
                    SettingsTextFieldRow(
                        title = "当前周",
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
                        enabled = !value.autoCurrentWeek
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "自动计算当前周",
                        subtitle = "学期状态：${scheduleTermStatusDescription(
                            config.copy(
                                totalWeeks = value.totalWeeks,
                                currentWeek = value.currentWeek,
                                autoCurrentWeek = value.autoCurrentWeek,
                                termStartDate = value.termStartDate.ifBlank { null }
                            ), LocalDate.now()
                        )}",
                        checked = value.autoCurrentWeek,
                        backdrop = backdrop,
                        onCheckedChange = { enabled ->
                            latestDraft()?.let { latest ->
                                val nextWeek = resolveScheduleCurrentWeek(
                                    config,
                                    latest.totalWeeks,
                                    latest.currentWeek,
                                    latest.termStartDate,
                                    enabled || latest.autoCurrentWeek
                                )
                                currentWeekText = nextWeek.toString()
                                commitDraft(latest.copy(autoCurrentWeek = enabled, currentWeek = nextWeek))
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "隐藏空周末",
                        subtitle = "周六、周日无课时自动收起",
                        checked = value.hideEmptyWeekends,
                        backdrop = backdrop,
                        onCheckedChange = { checked ->
                            latestDraft()?.let { latest ->
                                commitDraft(latest.copy(hideEmptyWeekends = checked))
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsPickerValueRow(
                        title = "学期开始日期",
                        value = value.termStartDate,
                        onClick = { beginDateSelection(value.termStartDate) }
                    )
                }
                if (!detailLaunching && !suppressDetailedButton) {
                    if (backdrop != null) LiquidButton(
                        onClick = {
                            val raw = latestDraft() ?: return@LiquidButton
                            val bounds = detailButtonBounds ?: return@LiquidButton
                            if (saving || detailLaunching) return@LiquidButton
                            val total = totalWeeksText.toIntOrNull()?.coerceIn(1, 60) ?: raw.totalWeeks
                            val current = currentWeekText.toIntOrNull()?.coerceIn(1, total)
                                ?: raw.currentWeek.coerceIn(1, total)
                            val value = raw.copy(totalWeeks = total, currentWeek = current)
                            detailLaunching = true
                            onDetailedSettings(value.scheduleId, bounds) { afterSaved ->
                                saving = true
                                onSave(value) {
                                    saving = false
                                    detailLaunching = false
                                    afterSaved()
                                }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.localToRoot(Offset.Zero)
                                val size = coordinates.size
                                detailButtonBounds = Rect(
                                    left = position.x,
                                    top = position.y,
                                    right = position.x + size.width,
                                    bottom = position.y + size.height
                                )
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
                                val position = coordinates.localToRoot(Offset.Zero)
                                val size = coordinates.size
                                detailButtonBounds = Rect(
                                    position.x,
                                    position.y,
                                    position.x + size.width,
                                    position.y + size.height
                                )
                            }
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (appUsesDarkTheme(config)) Color(0xFF30343D)
                                else Color(0xFFE8ECF3)
                            )
                            .clickable {
                                val raw = latestDraft() ?: return@clickable
                                val bounds = detailButtonBounds ?: return@clickable
                                if (saving || detailLaunching) return@clickable
                                val total = totalWeeksText.toIntOrNull()?.coerceIn(1, 60) ?: raw.totalWeeks
                                val current = totalWeeksText.toIntOrNull()?.let {
                                    currentWeekText.toIntOrNull()?.coerceIn(1, total)
                                } ?: raw.currentWeek.coerceIn(1, total)
                                val updated = raw.copy(totalWeeks = total, currentWeek = current)
                                detailLaunching = true
                                onDetailedSettings(updated.scheduleId, bounds) { afterSaved ->
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

    top.yukonga.miuix.kmp.overlay.OverlayBottomSheet(
        show = showDatePicker && draft != null,
        title = "选择日期",
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
        modifier = Modifier.heightIn(max = 330.dp),
        surfaceModifier = quickSheetBackdropModifier(backdrop, config, blurRadius = 28.dp)
    ) {
        val maxDay = daysInMonth(dateYear, dateMonth)
        LaunchedEffect(maxDay) {
            if (dateDay > maxDay) dateDay = maxDay
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = dateYear,
                onValueChange = { dateYear = it },
                range = 2000..2100,
                visibleItemCount = 3,
                label = { "${it}年" },
                modifier = Modifier.weight(1.25f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = dateMonth,
                onValueChange = { dateMonth = it },
                range = 1..12,
                visibleItemCount = 3,
                label = { "${it}月" },
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.NumberPicker(
                value = dateDay.coerceAtMost(maxDay),
                onValueChange = { dateDay = it },
                range = 1..maxDay,
                visibleItemCount = 3,
                label = { "${it}日" },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickSettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f), fontSize = 12.sp)
        }
        top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun QuickSettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
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
    dialogBackdrop: Backdrop? = backdrop,
    onPageSelected: (Int) -> Unit,
    onApply: (Int) -> Unit,
    onClose: () -> Unit,
    onBack: (Int) -> Unit,
    onCreate: () -> Unit,
    onShare: (Int, ScheduleShareType) -> Unit,
    onCustomize: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onDeleteRequest: (Int) -> Unit,
    modifier: Modifier = Modifier
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
    val managerBackdrop = rememberLayerBackdrop()
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
        val availableCardHeight = (screenHeight - 256.dp).coerceAtLeast(320.dp)
        val snapshotAspect = pickerState.currentSnapshot
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { it.width.toFloat() / it.height.toFloat() }
            ?: (screenWidth.value / screenHeight.value)
        val cardWidth = minOf(screenWidth * 0.72f, availableCardHeight * snapshotAspect).coerceAtLeast(220.dp)
        val cardHeight = cardWidth / snapshotAspect
        val cardWidthFraction = cardWidth.value / screenWidth.value
        val progress = pickerState.enterProgress.value.coerceIn(0f, 1f)
        val chrome = pickerState.chromeProgress.value.coerceIn(0f, 1f)
        val pageSpacing = (-140 + 130 * pickerState.pageSpacingProgress.value).dp

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
                .layerBackdrop(managerBackdrop)
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer { alpha = chrome },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp).height(92.dp),
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

            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSize = PageSize.Fixed(cardWidth),
                    pageSpacing = pageSpacing,
                    contentPadding = PaddingValues(horizontal = (screenWidth - cardWidth) / 2),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = pagerInputEnabled,
                    // The Room list and our in-memory order can publish on adjacent frames
                    // while a newly-created profile is inserted. Never index the captured list
                    // blindly from Pager's key lookup during that one-frame size transition.
                    key = { page -> orderedProfiles.getOrNull(page)?.id ?: (Int.MIN_VALUE + page) }
                ) { page ->
                    val relativePosition = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                    val pageOffset = abs(relativePosition).coerceAtMost(1.6f)
                    val targetScale = (1f - pageOffset * 0.35f).coerceAtLeast(0.45f)
                    val stackDepth = (1f - pageOffset).coerceIn(0f, 1f)
                    val lowerLayerAmount = pageOffset.coerceIn(0f, 1f)
                    val pivotX = (0.5f - relativePosition / cardWidthFraction).coerceIn(-0.8f, 1.8f)
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
                                shape = RoundedCornerShape(34.dp)
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
                                snapshotFallbackOnly = true,
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
                                        .clip(RoundedCornerShape(34.dp))
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
                                        .clip(RoundedCornerShape(50))
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
                Modifier.fillMaxWidth().height(138.dp),
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
                            .clip(RoundedCornerShape(50))
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
                                        .clip(RoundedCornerShape(50))
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
                                .clip(RoundedCornerShape(50))
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
                        "自定义",
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
                        tint = Color(0xFF007AFF),
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
                val animatedWidth = screenWidth + (targetWidth - screenWidth) * progress
                val animatedHeight = screenHeight + (targetHeight - screenHeight) * progress
                val morphModifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = targetOffsetX * progress, y = targetOffsetY * progress)
                    .width(animatedWidth)
                    .height(animatedHeight)
                    .graphicsLayer {
                        shape = RoundedCornerShape(34.dp * pickerState.cornerProgress.value)
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
        LiquidAlertOverlay(
            title = "删除课表",
            message = "确定要删除「${profile.name}」吗？该课表内的课程会一起删除。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { deleteConfirmTarget = null },
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                    deleteConfirmTarget = null
                    onDeleteRequest(profile.id)
                }
            ),
            backdrop = dialogBackdrop,
            config = allState.allConfigs.firstOrNull { it.id == profile.id } ?: defaultConfig(profile.id),
            onDismissRequest = { deleteConfirmTarget = null },
            modifier = Modifier.zIndex(200f)
        )
    }
    if (showShareOptions) {
        LiquidAlertOverlay(
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
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { showShareOptions = false }
            ),
            backdrop = dialogBackdrop,
            config = allState.allConfigs.firstOrNull { it.id == selectedId } ?: defaultConfig(selectedId),
            onDismissRequest = { showShareOptions = false },
            modifier = Modifier.zIndex(200f)
        )
    }
}

@Composable
private fun NewScheduleCard(enabled: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xFF17171A))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (backdrop != null) {
                LiquidButton(
                    onClick = { if (enabled) onClick() },
                    backdrop = backdrop,
                    modifier = Modifier.size(68.dp),
                    height = 68.dp,
                    blurRadius = 12.dp,
                    lensHeight = 34.dp,
                    lensAmount = 46.dp,
                    chromaticAberration = false,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+", color = glassForegroundColor(config), fontSize = 40.sp, fontWeight = FontWeight.Light)
                }
            } else {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
                }
            }
            Text("新建课表", color = Color.White, fontWeight = FontWeight.Medium)
        }
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
            backdrop = backdrop,
            modifier = Modifier.width(86.dp),
            height = 40.dp,
            blurRadius = 10.dp,
            lensHeight = 26.dp,
            lensAmount = 34.dp,
            tint = if (primary) Color(0xFF007AFF) else Color.Unspecified,
            surfaceColor = if (primary) Color(0xFF007AFF).copy(alpha = 0.46f) else Color.Black.copy(alpha = 0.30f),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Text(label, color = Color.White, modifier = Modifier.width(86.dp).clip(RoundedCornerShape(50)).background(if (primary) Color(0xFF007AFF) else Color.White.copy(alpha = 0.12f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            backdrop = backdrop,
            modifier = modifier,
            height = 52.dp,
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
        Box(modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.5f)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun PickerRoundButton(icon: Int, description: String, enabled: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity, onClick: () -> Unit) {
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            modifier = Modifier.size(42.dp),
            height = 42.dp,
            blurRadius = 10.dp,
            lensHeight = 24.dp,
            lensAmount = 32.dp,
            chromaticAberration = false,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(painterResource(icon), description, tint = glassForegroundColor(config), modifier = Modifier.size(20.dp))
        }
    } else {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PickerTextButton(label: String, enabled: Boolean, backdrop: Backdrop?, config: ScheduleConfigEntity, modifier: Modifier = Modifier.width(176.dp), onClick: () -> Unit) {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnClick by rememberUpdatedState(onClick)
    if (backdrop != null) {
        LiquidButton(
            onClick = { if (latestEnabled) latestOnClick() },
            backdrop = backdrop,
            modifier = modifier,
            height = 52.dp,
            blurRadius = 10.dp,
            lensHeight = 26.dp,
            lensAmount = 34.dp,
            chromaticAberration = false,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.42f), fontWeight = FontWeight.SemiBold)
        }
    } else {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.42f), fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp))
    }
}
