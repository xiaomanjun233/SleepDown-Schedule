package com.xiaomanjun.sleepdownschedule

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DatePickerDialog
import android.app.Application
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import android.view.HapticFeedbackConstants
import android.view.WindowInsetsController
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import top.yukonga.miuix.kmp.squircle.squircleSurface
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.palette.graphics.Palette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults as MiuixBasicComponentDefaults
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
import java.time.LocalDate
import java.time.LocalTime
import java.io.File
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DialogHeader(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity = defaultConfig()
) {
    LiquidDialogHeader(
        title,
        onCancel,
        backdrop,
        config,
        modifier = modifier,
        buttonBlurRadius = 8.dp,
        onConfirm = onSave
    )
}

@Composable
fun NormalizedDialogHeader(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    LiquidDialogHeader(title, onCancel, backdrop, config, onConfirm = onSave)
}

@Composable
fun NormalizedCourseEditorScreen(
    state: AppState,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onSaveCourses: ((List<CourseEntity>) -> Unit)? = null,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?,
    pickerRenderInRootScaffold: Boolean = true
) {
    val formData = remember(state.config, state.periods, state.courses) {
        CourseEditorFormData(
            config = state.config,
            periods = state.periods,
            courses = state.courses
        )
    }
    NormalizedCourseEditorScreen(
        formData = formData,
        initialCourse = initialCourse,
        onCancel = onCancel,
        onSave = onSave,
        onSaveCourses = onSaveCourses,
        onDelete = onDelete,
        backdrop = backdrop,
        pickerRenderInRootScaffold = pickerRenderInRootScaffold
    )
}

@Immutable
data class CourseEditorFormData(
    val config: ScheduleConfigEntity,
    val periods: List<PeriodEntity>,
    val courses: List<CourseEntity> = emptyList()
)

@OptIn(ExperimentalFoundationApi::class)
data class CourseEditorPagerPresentation(
    val pagerState: PagerState,
    val pageCount: Int,
    val visible: Boolean
)

private data class CourseEditorDraft(
    val name: String,
    val teacher: String,
    val location: String,
    val weekdays: Set<Int>,
    val periodStart: Int,
    val periodEnd: Int,
    val customStartTime: String?,
    val customEndTime: String?,
    val weeks: Set<Int>,
    val parity: WeekParity,
    val note: String
)

internal enum class CourseWeekSelectionMode {
    EVERY,
    ODD,
    EVEN,
    CUSTOM
}

internal fun inferCourseWeekSelectionMode(weeks: Collection<Int>): CourseWeekSelectionMode {
    val sorted = weeks.filter { it > 0 }.distinct().sorted()
    if (sorted.isEmpty()) return CourseWeekSelectionMode.CUSTOM
    if (sorted.zipWithNext().all { (first, second) -> second == first + 1 }) {
        return CourseWeekSelectionMode.EVERY
    }
    val range = sorted.first()..sorted.last()
    if (sorted == range.filter { it % 2 == 1 }) return CourseWeekSelectionMode.ODD
    if (sorted == range.filter { it % 2 == 0 }) return CourseWeekSelectionMode.EVEN
    return CourseWeekSelectionMode.CUSTOM
}

internal fun weeksForCourseWeekSelectionMode(
    mode: CourseWeekSelectionMode,
    currentWeeks: Collection<Int>,
    totalWeeks: Int
): Set<Int> {
    val bounded = currentWeeks.filter { it in 1..totalWeeks }.distinct().sorted()
    val start = bounded.firstOrNull() ?: 1
    val end = bounded.lastOrNull() ?: totalWeeks.coerceAtLeast(1)
    val range = start..end
    return when (mode) {
        CourseWeekSelectionMode.EVERY -> range.toSet()
        CourseWeekSelectionMode.ODD -> range.filterTo(linkedSetOf()) { it % 2 == 1 }
        CourseWeekSelectionMode.EVEN -> range.filterTo(linkedSetOf()) { it % 2 == 0 }
        CourseWeekSelectionMode.CUSTOM -> bounded.toSet()
    }
}

internal fun CourseWeekSelectionMode.toWeekParity(): WeekParity = when (this) {
    CourseWeekSelectionMode.ODD -> WeekParity.ODD
    CourseWeekSelectionMode.EVEN -> WeekParity.EVEN
    CourseWeekSelectionMode.EVERY,
    CourseWeekSelectionMode.CUSTOM -> WeekParity.ALL
}

private fun courseWeekSelectionModeLabel(mode: CourseWeekSelectionMode): String = when (mode) {
    CourseWeekSelectionMode.EVERY -> "每周"
    CourseWeekSelectionMode.ODD -> "单周"
    CourseWeekSelectionMode.EVEN -> "双周"
    CourseWeekSelectionMode.CUSTOM -> "自定义"
}

internal sealed interface CourseEditorPickerRequest {
    val title: String

    data class Wheel(
        override val title: String,
        val labels: List<String>,
        val startIndex: Int,
        val endIndex: Int? = null,
        val onConfirm: (Int, Int?) -> Unit
    ) : CourseEditorPickerRequest

    data class Grid(
        override val title: String,
        val labels: List<String>,
        val selectedIndices: Set<Int>,
        val preferredColumns: Int,
        val onConfirm: (Set<Int>) -> Unit
    ) : CourseEditorPickerRequest

    data class Period(
        override val title: String,
        val labels: List<String>,
        val startIndex: Int,
        val endIndex: Int,
        val endIndexUpperBound: Int,
        val customStartTime: String?,
        val customEndTime: String?,
        val regularStartTime: String?,
        val regularEndTime: String?,
        val onConfirmPeriods: (Int, Int) -> Unit,
        val onConfirmCustomTime: (String, String) -> Unit
    ) : CourseEditorPickerRequest
}

internal data class PeriodPickerSelectionBounds(
    val startIndex: Int,
    val endIndex: Int,
    val endIndexUpperBound: Int
)

internal fun boundedPeriodPickerSelection(
    startIndex: Int,
    endIndex: Int,
    requestedEndIndexUpperBound: Int,
    labelsLastIndex: Int
): PeriodPickerSelectionBounds {
    val safeLastIndex = labelsLastIndex.coerceAtLeast(0)
    val safeUpperBound = requestedEndIndexUpperBound.coerceIn(0, safeLastIndex)
    val safeStart = startIndex.coerceIn(0, safeUpperBound)
    return PeriodPickerSelectionBounds(
        startIndex = safeStart,
        endIndex = endIndex.coerceIn(safeStart, safeUpperBound),
        endIndexUpperBound = safeUpperBound
    )
}

@Immutable
internal data class CourseEditorGroup(val courses: List<CourseEntity>) {
    val representative: CourseEntity? get() = courses.minByOrNull { it.id }
}

private data class CourseEditorGroupingKey(
    val scheduleId: Int,
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val periods: List<Int>,
    val customStartTime: String?,
    val customEndTime: String?,
    val customColorArgb: Long?,
    val parity: WeekParity,
    val note: String
)

private fun CourseEntity.editorGroupingKey() = CourseEditorGroupingKey(
    scheduleId = scheduleId,
    name = name.trim(),
    teacher = teacher.orEmpty().trim(),
    location = location.orEmpty().trim(),
    weekday = weekday,
    periods = periods.distinct().sorted(),
    customStartTime = customStartTime,
    customEndTime = customEndTime,
    customColorArgb = customColorArgb,
    parity = weekParity,
    note = note.orEmpty().trim()
)

internal fun buildCourseEditorGroups(
    initialCourse: CourseEntity?,
    courses: List<CourseEntity>
): List<CourseEditorGroup> {
    if (initialCourse == null) return listOf(CourseEditorGroup(emptyList()))
    val related = (courses + initialCourse)
        .filter {
            it.scheduleId == initialCourse.scheduleId &&
                it.name.trim() == initialCourse.name.trim()
        }
        .distinctBy { it.id }
    return related
        .groupBy(CourseEntity::editorGroupingKey)
        .values
        .map { group ->
            CourseEditorGroup(
                group.sortedWith(
                    compareBy<CourseEntity> { it.weekday }
                        .thenBy { it.weeks.minOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.id }
                )
            )
        }
        .sortedWith(
            compareBy<CourseEditorGroup> { it.courses.minOfOrNull(CourseEntity::weekday) ?: 1 }
                .thenBy { it.representative?.periods?.minOrNull() ?: 0 }
                .thenBy { it.representative?.location.orEmpty() }
        )
}

internal fun excludedWeeksInsideCourseRange(course: CourseEntity?): Set<Int> {
    val weeks = course?.weeks?.distinct()?.sorted().orEmpty()
    if (weeks.size < 2) return emptySet()
    return (weeks.first()..weeks.last()).filterNot(weeks.toHashSet()::contains).toSet()
}

internal fun weeksInEditorRange(
    start: Int,
    end: Int,
    excludedWeeks: Set<Int>
): List<Int> {
    if (start > end) return emptyList()
    return (start..end).filterNot(excludedWeeks::contains)
}

internal fun compactWeekSelectionLabel(weeks: List<Int>): String {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return "未选择"
    val ranges = buildList {
        var start = sorted.first()
        var previous = start
        sorted.drop(1).forEach { week ->
            if (week != previous + 1) {
                add(if (start == previous) "$start" else "$start–$previous")
                start = week
            }
            previous = week
        }
        add(if (start == previous) "$start" else "$start–$previous")
    }
    return "第${ranges.joinToString("、")}周"
}

internal fun compactWeekdaySelectionLabel(weekdays: Collection<Int>): String {
    val labels = weekdays.filter { it in 1..7 }.distinct().sorted().map(::weekdayLabel)
    return if (labels.isEmpty()) "未选择" else "周${labels.joinToString("、")}"
}

private fun courseEditorDraft(
    courses: List<CourseEntity>,
    periodValues: List<Int>,
    totalWeeks: Int
) : CourseEditorDraft {
    val course = courses.firstOrNull()
    val storedWeeks = courses.flatMap(CourseEntity::weeks).filter { it in 1..totalWeeks }.toSet()
        .ifEmpty { (1..totalWeeks.coerceAtLeast(1)).toSet() }
    val activeWeeks = when (course?.weekParity ?: WeekParity.ALL) {
        WeekParity.ALL -> storedWeeks
        WeekParity.ODD -> storedWeeks.filterTo(linkedSetOf()) { it % 2 == 1 }
        WeekParity.EVEN -> storedWeeks.filterTo(linkedSetOf()) { it % 2 == 0 }
    }
    val selectionMode = inferCourseWeekSelectionMode(activeWeeks)
    return CourseEditorDraft(
        name = course?.name.orEmpty(),
        teacher = course?.teacher.orEmpty(),
        location = course?.location.orEmpty(),
        weekdays = courses.map(CourseEntity::weekday).filter { it in 1..7 }.toSet().ifEmpty { setOf(1) },
        periodStart = course?.periods?.minOrNull() ?: (periodValues.firstOrNull() ?: 1),
        periodEnd = course?.periods?.maxOrNull() ?: (periodValues.firstOrNull() ?: 1),
        customStartTime = course?.customStartTime,
        customEndTime = course?.customEndTime,
        weeks = activeWeeks,
        parity = selectionMode.toWeekParity(),
        note = course?.note.orEmpty()
    )
}

internal fun courseEditorOriginalForWeekday(
    originals: List<CourseEntity>,
    weekday: Int,
    selectedWeekdayCount: Int
): CourseEntity? = originals
    .filter { it.weekday == weekday }
    .minByOrNull(CourseEntity::id)
    ?: originals.singleOrNull().takeIf { selectedWeekdayCount == 1 }

private fun CourseEditorDraft.toCourses(
    originals: List<CourseEntity>,
    periodValues: List<Int>
): List<CourseEntity> {
    val originalWeekdays = originals.map(CourseEntity::weekday).toSet()
    val originalWeeks = originals.flatMap(CourseEntity::weeks).toSet()
    val keepOriginalDistribution = weekdays == originalWeekdays && weeks == originalWeeks
    val originalsByWeekday = originals.groupBy(CourseEntity::weekday)
    val periods = periodValues.filter { it in periodStart..periodEnd }
    return weekdays.sorted().mapNotNull { weekday ->
        val weekdayOriginals = originalsByWeekday[weekday].orEmpty()
        val original = courseEditorOriginalForWeekday(originals, weekday, weekdays.size)
        val targetWeeks = if (keepOriginalDistribution && originals.isNotEmpty()) {
            weekdayOriginals.flatMap(CourseEntity::weeks).distinct().sorted()
        } else {
            weeks.distinct().sorted()
        }
        if (targetWeeks.isEmpty()) return@mapNotNull null
        CourseEntity(
            id = original?.id ?: 0,
            name = name.trim(),
            teacher = teacher.trim().ifBlank { null },
            location = location.trim().ifBlank { null },
            weekday = weekday,
            periods = periods,
            weeks = targetWeeks,
            weekParity = parity,
            note = note.trim().ifBlank { null },
            customStartTime = customStartTime,
            customEndTime = customEndTime,
            customColorArgb = original?.customColorArgb ?: originals.firstOrNull()?.customColorArgb,
            scheduleId = original?.scheduleId ?: originals.firstOrNull()?.scheduleId ?: 0
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalizedCourseEditorScreen(
    formData: CourseEditorFormData,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onSaveCourses: ((List<CourseEntity>) -> Unit)? = null,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?,
    onSaveWithOriginal: ((CourseEntity, CourseEntity) -> Unit)? = null,
    onSaveGroup: ((List<CourseEntity>, List<CourseEntity>) -> Unit)? = null,
    onDeleteGroup: ((List<CourseEntity>) -> Unit)? = null,
    pickerRenderInRootScaffold: Boolean = true,
    renderPagerIndicator: Boolean = true,
    onPagerPresentationChange: ((CourseEditorPagerPresentation) -> Unit)? = null
) {
    val config = formData.config
    val editorGroups = remember(initialCourse, formData.courses) {
        buildCourseEditorGroups(initialCourse, formData.courses)
    }
    val periodValues = remember(formData.periods, editorGroups) {
        (formData.periods.map { it.periodIndex } + editorGroups.flatMap { group -> group.courses.flatMap(CourseEntity::periods) })
            .distinct()
            .sorted()
    }
    val initialPage = remember(initialCourse, editorGroups) {
        editorGroups.indexOfFirst { group -> group.courses.any { it.id == initialCourse?.id } }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { editorGroups.size }
    var drafts by remember(editorGroups, periodValues, config.totalWeeks) {
        mutableStateOf(editorGroups.mapIndexed { index, group ->
            index to courseEditorDraft(group.courses, periodValues, config.totalWeeks.coerceAtLeast(1))
        }.toMap())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var pickerRequest by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
    val currentPage = pagerState.currentPage.coerceIn(editorGroups.indices)
    val pagerIndicatorVisible = editorGroups.size > 1 && pickerRequest == null

    SideEffect {
        onPagerPresentationChange?.invoke(
            CourseEditorPagerPresentation(
                pagerState = pagerState,
                pageCount = editorGroups.size,
                visible = pagerIndicatorVisible
            )
        )
    }

    LaunchedEffect(currentPage) { error = null }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            pageSpacing = 10.dp,
            userScrollEnabled = pickerRequest == null,
            key = { page -> editorGroups[page].representative?.id ?: Long.MIN_VALUE }
        ) { page ->
            val group = editorGroups[page]
            val course = group.representative
            val draft = drafts.getValue(page)
            CourseEditorFormPage(
                course = course,
                groupedCourses = group.courses,
                draft = draft,
                onDraftChange = { drafts = drafts + (page to it); error = null },
                periodValues = periodValues,
                periods = formData.periods,
                totalWeeks = config.totalWeeks.coerceAtLeast(1),
                config = config,
                backdrop = backdrop,
                error = error.takeIf { page == currentPage },
                onCancel = onCancel,
                onSave = {
                    val currentDraft = drafts.getValue(page)
                    val edited = currentDraft.toCourses(group.courses, periodValues)
                    when {
                        currentDraft.name.isBlank() -> error = "课程名称不能为空"
                        currentDraft.weekdays.isEmpty() -> error = "请选择星期"
                        edited.firstOrNull()?.periods.isNullOrEmpty() -> error = "请选择节次"
                        currentDraft.weeks.isEmpty() -> error = "请选择周次"
                        group.courses.isNotEmpty() && onSaveGroup != null -> onSaveGroup(group.courses, edited)
                        course != null && edited.size == 1 && onSaveWithOriginal != null -> onSaveWithOriginal(course, edited.single())
                        onSaveCourses != null -> onSaveCourses(edited)
                        edited.size == 1 -> onSave(edited.single())
                        else -> edited.forEach(onSave)
                    }
                },
                onDelete = course?.let {
                    {
                        if (onDeleteGroup != null) onDeleteGroup(group.courses) else onDelete(it)
                    }
                },
                onOpenPicker = { pickerRequest = it },
                pageCount = editorGroups.size
            )
        }
        if (renderPagerIndicator && pagerIndicatorVisible) {
            ProjectPagerIndicator(
                pagerState = pagerState,
                pageCount = editorGroups.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(20f)
                    .padding(bottom = 10.dp)
            )
        }
        pickerRequest?.let { request ->
            CourseEditorPickerOverlay(
                request = request,
                backdrop = backdrop,
                config = config,
                renderInRootScaffold = pickerRenderInRootScaffold,
                onDismiss = { pickerRequest = null }
            )
        }
    }
}

@Composable
private fun CourseEditorFormPage(
    course: CourseEntity?,
    groupedCourses: List<CourseEntity>,
    draft: CourseEditorDraft,
    onDraftChange: (CourseEditorDraft) -> Unit,
    periodValues: List<Int>,
    periods: List<PeriodEntity>,
    totalWeeks: Int,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    error: String?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    pageCount: Int
) {
    // This form lives on the wallpaper-sampling CourseGlassCard. Its complete foreground domain
    // must follow the glass contrast decision; appPanelForegroundColor follows the app theme and
    // produced black row labels on a dark sampled card while values/icons stayed light.
    val editorContentColor = if (backdrop != null) {
        LocalAdaptiveGlass.current.contentColor
    } else {
        glassForegroundColor(config)
    }
    val editorFieldSurface = if (appUsesDarkTheme(config)) {
        ComposeColor(0xFF2C2C2E).copy(alpha = 0.54f)
    } else {
        ComposeColor.White.copy(alpha = 0.70f)
    }
    val editorFieldTextColor = readableOn(editorFieldSurface)
    CompositionLocalProvider(LocalContentColor provides editorContentColor) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            // The title/actions live above the scrolling form. Reserve their full glass band so
            // the first field never slides under a moving header.
            top = 76.dp,
            bottom = when {
                pageCount > 1 -> 52.dp
                course == null -> 32.dp
                else -> 16.dp
            }
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        if (course != null) {
            item(key = "summary", contentType = "summary") {
                Text(
                    listOfNotNull(
                        course.teacher,
                        course.location,
                        compactWeekdaySelectionLabel(groupedCourses.map(CourseEntity::weekday)),
                        if (course.hasCustomTime()) courseTimeLabel(course, periods)
                        else course.periods.takeIf { it.isNotEmpty() }?.let { "第${it.min()}-${it.max()}节" }
                    ).joinToString(" · "),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item(key = "course-name", contentType = "field") {
            DialogCapsuleField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                placeholder = "课程名称",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                fieldTextColor = editorFieldTextColor
            )
        }
        item(key = "teacher", contentType = "field") {
            DialogCapsuleField(
                value = draft.teacher,
                onValueChange = { onDraftChange(draft.copy(teacher = it)) },
                placeholder = "教师",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                fieldTextColor = editorFieldTextColor
            )
        }
        item(key = "location", contentType = "field") {
            DialogCapsuleField(
                value = draft.location,
                onValueChange = { onDraftChange(draft.copy(location = it)) },
                placeholder = "地点",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                fieldTextColor = editorFieldTextColor
            )
        }
        item(key = "weekday", contentType = "picker") {
            DialogSingleWheelSelector(
                title = "星期",
                values = (1..7).toList(),
                selected = draft.weekdays.firstOrNull() ?: 1,
                onSelected = { onDraftChange(draft.copy(weekdays = setOf(it))) },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { "周${weekdayLabel(it)}" }
        }
        item(key = "period-range", contentType = "range-picker") {
            DialogPeriodSelector(
                title = "节次",
                values = periodValues.ifEmpty { listOf(1) },
                periods = periods,
                start = draft.periodStart,
                end = draft.periodEnd,
                customStartTime = draft.customStartTime,
                customEndTime = draft.customEndTime,
                onRangeSelected = { start, end ->
                    onDraftChange(
                        draft.copy(
                            periodStart = start,
                            periodEnd = end,
                            customStartTime = null,
                            customEndTime = null
                        )
                    )
                },
                onCustomTimeSelected = { startTime, endTime ->
                    val parsedStart = runCatching { LocalTime.parse(startTime) }.getOrNull()
                    val parsedEnd = runCatching { LocalTime.parse(endTime) }.getOrNull()
                    val anchors = if (parsedStart != null && parsedEnd != null) {
                        courseAnchorPeriodsForTimeRange(parsedStart, parsedEnd, periods)
                    } else {
                        emptyList()
                    }
                    onDraftChange(
                        draft.copy(
                            periodStart = anchors.minOrNull() ?: draft.periodStart,
                            periodEnd = anchors.maxOrNull() ?: draft.periodEnd,
                            customStartTime = startTime,
                            customEndTime = endTime
                        )
                    )
                },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { "第${it}节" }
        }
        item(key = "week-range", contentType = "range-picker") {
            DialogMultiGridSelector(
                title = "周次",
                values = (1..totalWeeks).toList(),
                selected = draft.weeks,
                displayValue = compactWeekSelectionLabel(draft.weeks.toList()),
                preferredColumns = 5,
                onSelected = {
                    val mode = inferCourseWeekSelectionMode(it)
                    onDraftChange(draft.copy(weeks = it, parity = mode.toWeekParity()))
                },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { it.toString() }
        }
        item(key = "parity", contentType = "picker") {
            val selectedMode = inferCourseWeekSelectionMode(draft.weeks)
            DialogSingleWheelSelector(
                title = "单双周",
                values = CourseWeekSelectionMode.entries,
                selected = selectedMode,
                onSelected = { mode ->
                    val weeks = weeksForCourseWeekSelectionMode(mode, draft.weeks, totalWeeks)
                    onDraftChange(draft.copy(weeks = weeks, parity = mode.toWeekParity()))
                },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { courseWeekSelectionModeLabel(it) }
        }
        item(key = "note", contentType = "field") {
            DialogCapsuleField(
                value = draft.note,
                onValueChange = { onDraftChange(draft.copy(note = it)) },
                placeholder = "备注",
                config = config,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                fieldTextColor = editorFieldTextColor
            )
        }
        if (onDelete != null) {
            item(key = "delete", contentType = "action") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    DialogLiquidButton(
                        backdrop = backdrop,
                        label = "删除课程",
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        role = DialogButtonRole.Cancel,
                        destructiveFilled = true,
                        blurRadius = 8.dp
                    )
                }
            }
        }
        error?.let { message ->
            item(key = "error", contentType = "message") { Text(message, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
    CourseEditorFixedHeader(
        title = if (course == null) "添加单节课" else "编辑单节课",
        backdrop = backdrop,
        config = config,
        onCancel = onCancel,
        onSave = onSave,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(8f)
    )
    }
    }
}

/** Fixed glass header for add/edit forms; only the fields beneath it are scrollable. */
@Composable
private fun CourseEditorFixedHeader(
    title: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = appUsesDarkTheme(config)
    val tint = if (dark) {
        ComposeColor(0xFF111318).copy(alpha = 0.48f)
    } else {
        ComposeColor.White.copy(alpha = 0.42f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        // Fade the header's backdrop into the form rather than drawing a hard horizontal strip.
        ProgressiveBackdropBlur(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            tintColor = tint,
            height = 70.dp,
            blurRadius = 12.dp,
            tintIntensity = 0.16f,
            direction = ProgressiveBlurDirection.TopToBottom,
            topMaskFadeStart = 0.56f,
            topMaskFadeEnd = 1f,
            topTintFadeStart = 0.50f,
            topTintFadeEnd = 1f,
            fallbackTintStops = listOf(
                0f to tint,
                0.60f to tint.copy(alpha = tint.alpha * 0.78f),
                1f to ComposeColor.Transparent
            )
        )
        LiquidDialogHeader(
            title = title,
            onDismiss = onCancel,
            backdrop = backdrop,
            config = config,
            modifier = Modifier.fillMaxSize(),
            buttonBlurRadius = 4.dp,
            onConfirm = onSave
        )
    }
}

@Composable
private fun <T> DialogMultiGridSelector(
    title: String,
    values: List<T>,
    selected: Set<T>,
    displayValue: String,
    preferredColumns: Int,
    onSelected: (Set<T>) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (T) -> String
) {
    if (values.isEmpty()) return
    val selectedIndices = values.indices.filterTo(linkedSetOf()) { values[it] in selected }
    CourseEditorPickerValue(
        title = title,
        value = displayValue,
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Grid(
                    title = "选择$title",
                    labels = values.map(label),
                    selectedIndices = selectedIndices,
                    preferredColumns = preferredColumns,
                    onConfirm = { indices ->
                        onSelected(indices.sorted().mapTo(linkedSetOf()) { values[it] })
                    }
                )
            )
        }
    )
}

@Composable
private fun DialogPeriodSelector(
    title: String,
    values: List<Int>,
    periods: List<PeriodEntity>,
    start: Int,
    end: Int,
    customStartTime: String?,
    customEndTime: String?,
    onRangeSelected: (Int, Int) -> Unit,
    onCustomTimeSelected: (String, String) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (Int) -> String
) {
    val safeValues = remember(values) { values.distinct().sorted().ifEmpty { listOf(1) } }
    val startIndex = safeValues.indexOf(start).takeIf { it >= 0 } ?: 0
    val endIndex = (safeValues.indexOf(end).takeIf { it >= 0 } ?: startIndex).coerceAtLeast(startIndex)
    val customRange = remember(customStartTime, customEndTime) {
        val parsedStart = customStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val parsedEnd = customEndTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (parsedStart != null && parsedEnd != null && parsedEnd.isAfter(parsedStart)) {
            parsedStart to parsedEnd
        } else {
            null
        }
    }
    CourseEditorPickerValue(
        title = title,
        value = customRange?.let { (customStart, customEnd) -> "$customStart - $customEnd" }
            ?: "${label(safeValues[startIndex])} - ${label(safeValues[endIndex])}",
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Period(
                    title = "选择节次",
                    labels = safeValues.map(label),
                    startIndex = startIndex,
                    endIndex = endIndex,
                    endIndexUpperBound = safeValues.lastIndex,
                    customStartTime = customRange?.first?.toString(),
                    customEndTime = customRange?.second?.toString(),
                    regularStartTime = periods.firstOrNull { it.periodIndex == safeValues[startIndex] }?.startTime,
                    regularEndTime = periods.firstOrNull { it.periodIndex == safeValues[endIndex] }?.endTime,
                    onConfirmPeriods = { selectedStart, selectedEnd ->
                        onRangeSelected(safeValues[selectedStart], safeValues[selectedEnd])
                    },
                    onConfirmCustomTime = onCustomTimeSelected
                )
            )
        }
    )
}

@Composable
private fun DialogRangeWheelSelector(
    title: String,
    values: List<Int>,
    start: Int,
    end: Int,
    displayValue: String? = null,
    onRangeSelected: (Int, Int) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (Int) -> String
) {
    val safeValues = remember(values) { values.distinct().sorted().ifEmpty { listOf(1) } }
    val startIndex = safeValues.indexOf(start).takeIf { it >= 0 } ?: 0
    val endIndex = (safeValues.indexOf(end).takeIf { it >= 0 } ?: startIndex).coerceAtLeast(startIndex)
    CourseEditorPickerValue(
        title = title,
        value = displayValue ?: "${label(safeValues[startIndex])} - ${label(safeValues[endIndex])}",
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Wheel(
                    title = "选择$title",
                    labels = safeValues.map(label),
                    startIndex = startIndex,
                    endIndex = endIndex,
                    onConfirm = { selectedStart, selectedEnd ->
                        onRangeSelected(safeValues[selectedStart], safeValues[selectedEnd ?: selectedStart])
                    }
                )
            )
        }
    )
}

@Composable
private fun <T> DialogSingleWheelSelector(
    title: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    onOpenPicker: (CourseEditorPickerRequest) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    label: (T) -> String
) {
    val safeValues = values.ifEmpty { return }
    val selectedIndex = safeValues.indexOf(selected).coerceAtLeast(0)
    CourseEditorPickerValue(
        title = title,
        value = label(safeValues[selectedIndex]),
        backdrop = backdrop,
        config = config,
        onClick = {
            onOpenPicker(
                CourseEditorPickerRequest.Wheel(
                    title = "选择$title",
                    labels = safeValues.map(label),
                    startIndex = selectedIndex,
                    onConfirm = { index, _ -> onSelected(safeValues[index]) }
                )
            )
        }
    )
}

@Composable
private fun CourseEditorPickerValue(
    title: String,
    value: String,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onClick: () -> Unit
) {
    val buttonTextColor = LocalContentColor.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // Match the original selector capsule's 42dp height. The surrounding LazyColumn keeps
            // its original 12dp spacing, so replacing four selectors does not grow the dialog.
            .height(42.dp)
            // Only round the pressed/selection indication emitted by BasicComponent. This row has
            // no persistent capsule background in its resting state.
            .clip(RoundedCornerShape(18.dp))
    ) {
        MiuixBasicComponent(
            title = title,
            titleColor = MiuixBasicComponentDefaults.titleColor(
                color = buttonTextColor,
                disabledColor = buttonTextColor.copy(alpha = 0.38f)
            ),
            modifier = Modifier.fillMaxSize(),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            endActions = {
                Text(
                    value,
                    color = buttonTextColor.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 132.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "打开${title}选择器",
                    tint = buttonTextColor.copy(alpha = 0.62f),
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f }
                )
            },
            onClick = onClick
        )
    }
}

@Composable
internal fun CourseEditorPickerOverlay(
    request: CourseEditorPickerRequest,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    renderInRootScaffold: Boolean,
    onDismiss: () -> Unit
) {
    // This picker owns its theme-tinted sheet, so its foreground follows that surface instead of
    // the wallpaper sampled by the page below it.
    val dark = appUsesDarkTheme(config)
    val pickerContentColor = if (dark) ComposeColor.White else ComposeColor(0xFF111111)
    val wheelRequest = request as? CourseEditorPickerRequest.Wheel
    val gridRequest = request as? CourseEditorPickerRequest.Grid
    val periodRequest = request as? CourseEditorPickerRequest.Period
    val periodBounds = remember(periodRequest) {
        periodRequest?.let {
            boundedPeriodPickerSelection(
                startIndex = it.startIndex,
                endIndex = it.endIndex,
                requestedEndIndexUpperBound = it.endIndexUpperBound,
                labelsLastIndex = it.labels.lastIndex
            )
        }
    }
    var startIndex by remember(request) {
        mutableIntStateOf(
            wheelRequest?.startIndex?.coerceIn(wheelRequest.labels.indices)
                ?: periodBounds?.startIndex
                ?: 0
        )
    }
    var endIndex by remember(request) {
        mutableIntStateOf(
            wheelRequest?.let {
                (it.endIndex ?: it.startIndex).coerceIn(it.labels.indices).coerceAtLeast(startIndex)
            } ?: periodBounds?.endIndex
                ?: 0
        )
    }
    var selectedIndices by remember(request) {
        mutableStateOf(gridRequest?.selectedIndices.orEmpty())
    }
    var customTimeMode by remember(request) {
        mutableStateOf(periodRequest?.customStartTime != null && periodRequest.customEndTime != null)
    }
    val initialCustomStart = remember(request) {
        periodRequest?.customStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: periodRequest?.regularStartTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.of(8, 0)
    }
    val initialCustomEnd = remember(request) {
        periodRequest?.customEndTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: periodRequest?.regularEndTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: initialCustomStart.plusMinutes(45)
    }
    var startHour by remember(request) { mutableIntStateOf(initialCustomStart.hour) }
    var startMinute by remember(request) { mutableIntStateOf(initialCustomStart.minute) }
    var endHour by remember(request) { mutableIntStateOf(initialCustomEnd.hour) }
    var endMinute by remember(request) { mutableIntStateOf(initialCustomEnd.minute) }
    var timeError by remember(request) { mutableStateOf<String?>(null) }
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = true,
        modifier = Modifier.wrapContentHeight(),
        title = request.title,
        titleColor = pickerContentColor,
        onDismissRequest = onDismiss,
        enableWindowDim = false,
        backgroundColor = ComposeColor.Transparent,
        forceCenter = true,
        renderInRootScaffold = renderInRootScaffold,
        surfaceModifier = Modifier.quickSheetBackdropModifier(
            backdrop = backdrop,
            config = config,
            blurRadius = 14.dp,
            centered = true
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides pickerContentColor) {
        Column(
            modifier = Modifier.wrapContentHeight().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (periodRequest != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickSheetLiquidAction(
                        label = "按节次",
                        enabled = true,
                        backdrop = backdrop,
                        config = config,
                        primary = !customTimeMode,
                        modifier = Modifier.weight(1f),
                        height = 42.dp
                    ) {
                        customTimeMode = false
                        timeError = null
                    }
                    QuickSheetLiquidAction(
                        label = "自定义时间",
                        enabled = true,
                        backdrop = backdrop,
                        config = config,
                        primary = customTimeMode,
                        modifier = Modifier.weight(1f),
                        height = 42.dp
                    ) {
                        customTimeMode = true
                        timeError = null
                    }
                }
            }
            if (wheelRequest != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f
                    val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                        color = pickerContentColor,
                        fontSize = if (compact) 23.sp else 28.sp
                    )
                    val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                        selectedTextColor = pickerContentColor,
                        unselectedTextColor = pickerContentColor.copy(alpha = 0.34f),
                        disabledSelectedTextColor = pickerContentColor.copy(alpha = 0.55f),
                        disabledUnselectedTextColor = pickerContentColor.copy(alpha = 0.22f)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
                        top.yukonga.miuix.kmp.basic.NumberPicker(
                            value = startIndex,
                            onValueChange = {
                                startIndex = it
                                if (wheelRequest.endIndex != null && endIndex < it) endIndex = it
                            },
                            range = if (wheelRequest.endIndex != null) 0..endIndex else wheelRequest.labels.indices,
                            visibleItemCount = 3,
                            label = { wheelRequest.labels[it] },
                            colors = pickerColors,
                            textStyle = pickerTextStyle,
                            modifier = Modifier.weight(1f)
                        )
                        if (wheelRequest.endIndex != null) {
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = endIndex,
                                onValueChange = { endIndex = it },
                                range = startIndex..wheelRequest.labels.lastIndex,
                                visibleItemCount = 3,
                                label = { wheelRequest.labels[it] },
                                colors = pickerColors,
                                textStyle = pickerTextStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else if (periodRequest != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 330.dp || LocalDensity.current.fontScale > 1.10f
                    val pickerTextStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title1.copy(
                        color = pickerContentColor,
                        fontSize = if (compact) 19.sp else 24.sp
                    )
                    val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                        selectedTextColor = pickerContentColor,
                        unselectedTextColor = pickerContentColor.copy(alpha = 0.34f),
                        disabledSelectedTextColor = pickerContentColor.copy(alpha = 0.55f),
                        disabledUnselectedTextColor = pickerContentColor.copy(alpha = 0.22f)
                    )
                    if (customTimeMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("开始", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = pickerContentColor.copy(alpha = 0.62f))
                                Text("结束", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = pickerContentColor.copy(alpha = 0.62f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
                            ) {
                            listOf(
                                Triple(startHour, 0..23, { value: Int -> startHour = value; timeError = null }),
                                Triple(startMinute, 0..59, { value: Int -> startMinute = value; timeError = null }),
                                Triple(endHour, 0..23, { value: Int -> endHour = value; timeError = null }),
                                Triple(endMinute, 0..59, { value: Int -> endMinute = value; timeError = null })
                            ).forEachIndexed { index, (value, range, onValueChange) ->
                                top.yukonga.miuix.kmp.basic.NumberPicker(
                                    value = value,
                                    onValueChange = onValueChange,
                                    range = range,
                                    visibleItemCount = 3,
                                    label = { it.toString().padStart(2, '0') + if (index % 2 == 0) "时" else "分" },
                                    colors = pickerColors,
                                    textStyle = pickerTextStyle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
                        ) {
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = startIndex,
                                onValueChange = {
                                    startIndex = it
                                    if (endIndex < it) endIndex = it
                                },
                                range = 0..endIndex,
                                visibleItemCount = 3,
                                label = { periodRequest.labels[it] },
                                colors = pickerColors,
                                textStyle = pickerTextStyle,
                                modifier = Modifier.weight(1f)
                            )
                            top.yukonga.miuix.kmp.basic.NumberPicker(
                                value = endIndex,
                                onValueChange = { endIndex = it },
                                range = startIndex..(periodBounds?.endIndexUpperBound
                                    ?: periodRequest.labels.lastIndex),
                                visibleItemCount = 3,
                                label = { periodRequest.labels[it] },
                                colors = pickerColors,
                                textStyle = pickerTextStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                timeError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (gridRequest != null) {
                CourseEditorSelectionGrid(
                    labels = gridRequest.labels,
                    selectedIndices = selectedIndices,
                    preferredColumns = gridRequest.preferredColumns,
                    contentColor = pickerContentColor,
                    dark = dark,
                    onSelectionChange = { selectedIndices = it }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickSheetLiquidAction("取消", true, backdrop, config, modifier = Modifier.weight(1f), height = 50.dp) { onDismiss() }
                QuickSheetLiquidAction("确定", true, backdrop, config, primary = true, modifier = Modifier.weight(1f), height = 50.dp) {
                    when (request) {
                        is CourseEditorPickerRequest.Wheel -> request.onConfirm(
                            startIndex,
                            endIndex.takeIf { request.endIndex != null }
                        )
                        is CourseEditorPickerRequest.Grid -> request.onConfirm(selectedIndices)
                        is CourseEditorPickerRequest.Period -> {
                            if (customTimeMode) {
                                val start = LocalTime.of(startHour, startMinute)
                                val end = LocalTime.of(endHour, endMinute)
                                if (!end.isAfter(start)) {
                                    timeError = "结束时间必须晚于开始时间"
                                    return@QuickSheetLiquidAction
                                }
                                request.onConfirmCustomTime(start.toString(), end.toString())
                            } else {
                                request.onConfirmPeriods(startIndex, endIndex)
                            }
                        }
                    }
                    onDismiss()
                }
            }
        }
        }
    }
}

@Composable
private fun CourseEditorSelectionGrid(
    labels: List<String>,
    selectedIndices: Set<Int>,
    preferredColumns: Int,
    contentColor: ComposeColor,
    dark: Boolean,
    onSelectionChange: (Set<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val latestSelection by rememberUpdatedState(selectedIndices)
    val latestOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val density = LocalDensity.current
    val gap = 8.dp
    val cellHeight = 48.dp
    BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
        val minimumCellWidth = if (preferredColumns >= 7) 38.dp else 50.dp
        val columns = minOf(
            preferredColumns.coerceAtLeast(1),
            maxOf(3, ((maxWidth + gap) / (minimumCellWidth + gap)).toInt())
        )
        val rowCount = (labels.size + columns - 1) / columns
        val gridHeight = cellHeight * rowCount + gap * (rowCount - 1).coerceAtLeast(0)
        val gapPx = with(density) { gap.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }

        fun toggle(index: Int) {
            val next = latestSelection.toMutableSet()
            if (!next.add(index)) next.remove(index)
            latestOnSelectionChange(next)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .pointerInput(labels, columns, gapPx, cellHeightPx) {
                    var gestureSelection = mutableSetOf<Int>()
                    var selectMode = true
                    var lastIndex = -1

                    fun indexAt(position: Offset): Int? {
                        if (position.x < 0f || position.y < 0f) return null
                        val cellWidth = (size.width - gapPx * (columns - 1)) / columns
                        val columnStride = cellWidth + gapPx
                        val rowStride = cellHeightPx + gapPx
                        val column = (position.x / columnStride).toInt()
                        val row = (position.y / rowStride).toInt()
                        if (column !in 0 until columns || row !in 0 until rowCount) return null
                        if (position.x - column * columnStride > cellWidth) return null
                        if (position.y - row * rowStride > cellHeightPx) return null
                        return (row * columns + column).takeIf(labels.indices::contains)
                    }

                    fun applyDragIndex(index: Int) {
                        if (index == lastIndex) return
                        lastIndex = index
                        if (selectMode) gestureSelection.add(index) else gestureSelection.remove(index)
                        latestOnSelectionChange(gestureSelection.toSet())
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    detectDragGestures(
                        onDragStart = { position ->
                            gestureSelection = latestSelection.toMutableSet()
                            lastIndex = -1
                            indexAt(position)?.let { index ->
                                selectMode = index !in gestureSelection
                                applyDragIndex(index)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            indexAt(change.position)?.let(::applyDragIndex)
                        }
                    )
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                labels.chunked(columns).forEachIndexed { rowIndex, rowLabels ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rowLabels.forEachIndexed { columnIndex, label ->
                            val index = rowIndex * columns + columnIndex
                            val selected = index in selectedIndices
                            CourseEditorSelectionCell(
                                label = label,
                                selected = selected,
                                contentColor = contentColor,
                                dark = dark,
                                modifier = Modifier.weight(1f).height(cellHeight),
                                onClick = { toggle(index) }
                            )
                        }
                        repeat(columns - rowLabels.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseEditorSelectionCell(
    label: String,
    selected: Boolean,
    contentColor: ComposeColor,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val baseColor = when {
        selected -> MaterialTheme.colorScheme.primary
        dark -> ComposeColor.White.copy(alpha = 0.12f)
        else -> ComposeColor.Black.copy(alpha = 0.07f)
    }
    val pressedColor = baseColor.copy(
        red = baseColor.red * 0.78f,
        green = baseColor.green * 0.78f,
        blue = baseColor.blue * 0.78f
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (pressed) pressedColor else baseColor,
        animationSpec = tween(durationMillis = if (pressed) 70 else 150),
        label = "course-editor-cell-press"
    )
    Box(
        modifier = modifier
            .squircleSurface(color = surfaceColor, cornerRadius = 12.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else contentColor.copy(alpha = 0.78f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectPagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dotSpacingPx = with(density) { 15.dp.toPx() }
    val horizontalPaddingPx = with(density) { 12.dp.toPx() }
    val indicatorWidth = with(density) { (24 + (pageCount - 1) * 15).dp }
    Canvas(modifier = modifier.width(indicatorWidth).height(24.dp)) {
        drawRoundRect(
            color = ComposeColor.Black.copy(alpha = 0.28f),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
        )
        repeat(pageCount) { page ->
            drawCircle(
                color = ComposeColor.White.copy(alpha = 0.30f),
                radius = 4.dp.toPx(),
                center = Offset(horizontalPaddingPx + page * dotSpacingPx, size.height / 2f)
            )
        }
        val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (pageCount - 1).toFloat())
        drawCircle(
            color = ComposeColor.White,
            radius = 5.dp.toPx(),
            center = Offset(horizontalPaddingPx + position * dotSpacingPx, size.height / 2f)
        )
    }
}

@Composable
fun MissingCourseScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("请先配置课程表。")
        Button(onClick = onBack) { Text("返回") }
    }
}
