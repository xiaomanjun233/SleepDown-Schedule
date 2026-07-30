package com.example.courseschedule

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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
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

private data class CourseEditorDraft(
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val weekStart: Int,
    val weekEnd: Int,
    val excludedWeeks: Set<Int>,
    val parity: WeekParity,
    val note: String
)

private data class CourseEditorPickerRequest(
    val title: String,
    val labels: List<String>,
    val startIndex: Int,
    val endIndex: Int? = null,
    val onConfirm: (Int, Int?) -> Unit
)

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

private fun courseEditorDraft(
    course: CourseEntity?,
    periodValues: List<Int>,
    totalWeeks: Int
) = CourseEditorDraft(
        name = course?.name.orEmpty(),
        teacher = course?.teacher.orEmpty(),
        location = course?.location.orEmpty(),
        weekday = course?.weekday ?: 1,
        periodStart = course?.periods?.minOrNull() ?: (periodValues.firstOrNull() ?: 1),
        periodEnd = course?.periods?.maxOrNull() ?: (periodValues.firstOrNull() ?: 1),
        weekStart = course?.weeks?.minOrNull() ?: 1,
        weekEnd = course?.weeks?.maxOrNull() ?: totalWeeks.coerceAtLeast(1),
        excludedWeeks = excludedWeeksInsideCourseRange(course),
        parity = course?.weekParity ?: WeekParity.ALL,
        note = course?.note.orEmpty()
    )

private fun CourseEditorDraft.toCourse(
    original: CourseEntity?,
    periodValues: List<Int>
) = CourseEntity(
    id = original?.id ?: 0,
    name = name.trim(),
    teacher = teacher.ifBlank { null },
    location = location.ifBlank { null },
    weekday = weekday,
    periods = periodValues.filter { it in periodStart..periodEnd },
    weeks = weeksInEditorRange(weekStart, weekEnd, excludedWeeks),
    weekParity = parity,
    note = note.ifBlank { null },
    scheduleId = original?.scheduleId ?: 0
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalizedCourseEditorScreen(
    formData: CourseEditorFormData,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?,
    onSaveWithOriginal: ((CourseEntity, CourseEntity) -> Unit)? = null,
    pickerRenderInRootScaffold: Boolean = true
) {
    val config = formData.config
    val relatedCourses = remember(initialCourse, formData.courses) {
        if (initialCourse == null) emptyList() else {
            (formData.courses + initialCourse)
                .filter { it.scheduleId == initialCourse.scheduleId && it.name.trim() == initialCourse.name.trim() }
                .distinctBy { it.id }
                .sortedWith(compareBy<CourseEntity> { it.weekday }.thenBy { it.periods.minOrNull() ?: 0 }.thenBy { it.location.orEmpty() })
        }
    }
    val editorCourses: List<CourseEntity?> = remember(initialCourse, relatedCourses) {
        if (relatedCourses.isEmpty()) listOf(initialCourse) else relatedCourses
    }
    val periodValues = remember(formData.periods, editorCourses) {
        (formData.periods.map { it.periodIndex } + editorCourses.filterNotNull().flatMap { it.periods }).distinct().sorted()
    }
    val initialPage = remember(initialCourse, editorCourses) {
        editorCourses.indexOfFirst { it?.id == initialCourse?.id }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { editorCourses.size }
    var drafts by remember(editorCourses, periodValues, config.totalWeeks) {
        mutableStateOf(editorCourses.associate { course ->
            (course?.id ?: Long.MIN_VALUE) to courseEditorDraft(course, periodValues, config.totalWeeks)
        })
    }
    var error by remember { mutableStateOf<String?>(null) }
    var pickerRequest by remember { mutableStateOf<CourseEditorPickerRequest?>(null) }
    val currentPage = pagerState.currentPage.coerceIn(editorCourses.indices)

    LaunchedEffect(currentPage) { error = null }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            pageSpacing = 10.dp,
            userScrollEnabled = pickerRequest == null,
            key = { page -> editorCourses[page]?.id ?: Long.MIN_VALUE }
        ) { page ->
            val course = editorCourses[page]
            val key = course?.id ?: Long.MIN_VALUE
            val draft = drafts.getValue(key)
            CourseEditorFormPage(
                course = course,
                draft = draft,
                onDraftChange = { drafts = drafts + (key to it); error = null },
                periodValues = periodValues,
                totalWeeks = config.totalWeeks.coerceAtLeast(1),
                config = config,
                backdrop = backdrop,
                error = error.takeIf { page == currentPage },
                onCancel = onCancel,
                onSave = {
                    val edited = drafts.getValue(key).toCourse(course, periodValues)
                    when {
                        edited.name.isBlank() -> error = "课程名称不能为空"
                        edited.periods.isEmpty() -> error = "请选择节次"
                        edited.weeks.isEmpty() -> error = "请选择周次"
                        course != null && onSaveWithOriginal != null -> onSaveWithOriginal(course, edited)
                        else -> onSave(edited)
                    }
                },
                onDelete = course?.let { { onDelete(it) } },
                onOpenPicker = { pickerRequest = it },
                pageCount = editorCourses.size
            )
        }
        if (editorCourses.size > 1 && pickerRequest == null) {
            ProjectPagerIndicator(
                pagerState = pagerState,
                pageCount = editorCourses.size,
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
    draft: CourseEditorDraft,
    onDraftChange: (CourseEditorDraft) -> Unit,
    periodValues: List<Int>,
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
    val editorContentColor = glassForegroundColor(config)
    CompositionLocalProvider(LocalContentColor provides editorContentColor) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (pageCount > 1) 52.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header", contentType = "header") {
            Box(Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp)) {
                DialogHeader(
                    title = if (course == null) "添加单节课" else "编辑单节课",
                    onCancel = onCancel,
                    backdrop = backdrop,
                    config = config,
                    onSave = onSave,
                    modifier = if (course != null) Modifier.height(54.dp) else Modifier
                )
            }
        }
        if (course != null) {
            item(key = "summary", contentType = "summary") {
                Text(
                    listOfNotNull(course.teacher, course.location, "周${weekdayLabel(course.weekday)}", course.periods.takeIf { it.isNotEmpty() }?.let { "第${it.min()}-${it.max()}节" }).joinToString(" · "),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item(key = "course-name", contentType = "field") { DialogCapsuleField(draft.name, { onDraftChange(draft.copy(name = it)) }, "课程名称", config, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item(key = "teacher", contentType = "field") { DialogCapsuleField(draft.teacher, { onDraftChange(draft.copy(teacher = it)) }, "教师", config, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item(key = "location", contentType = "field") { DialogCapsuleField(draft.location, { onDraftChange(draft.copy(location = it)) }, "地点", config, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item(key = "weekday", contentType = "picker") {
            DialogSingleWheelSelector(
                title = "星期",
                values = (1..7).toList(),
                selected = draft.weekday,
                onSelected = { onDraftChange(draft.copy(weekday = it)) },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { "周" + weekdayLabel(it) }
        }
        item(key = "period-range", contentType = "range-picker") {
            DialogRangeWheelSelector(
                title = "节次",
                values = periodValues.ifEmpty { listOf(1) },
                start = draft.periodStart,
                end = draft.periodEnd,
                onRangeSelected = { start, end -> onDraftChange(draft.copy(periodStart = start, periodEnd = end)) },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { "第${it}节" }
        }
        item(key = "week-range", contentType = "range-picker") {
            val selectedWeeks = weeksInEditorRange(
                draft.weekStart,
                draft.weekEnd,
                draft.excludedWeeks
            )
            DialogRangeWheelSelector(
                title = "周次",
                values = (1..totalWeeks).toList(),
                start = draft.weekStart,
                end = draft.weekEnd,
                displayValue = compactWeekSelectionLabel(selectedWeeks),
                onRangeSelected = { start, end -> onDraftChange(draft.copy(weekStart = start, weekEnd = end)) },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { "第${it}周" }
        }
        item(key = "parity", contentType = "picker") {
            DialogSingleWheelSelector(
                title = "单双周",
                values = WeekParity.entries,
                selected = draft.parity,
                onSelected = { onDraftChange(draft.copy(parity = it)) },
                onOpenPicker = onOpenPicker,
                backdrop = backdrop,
                config = config
            ) { parityLabel(it) }
        }
        item(key = "note", contentType = "field") { DialogCapsuleField(draft.note, { onDraftChange(draft.copy(note = it)) }, "备注", config, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
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
    }
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
                CourseEditorPickerRequest(
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
                CourseEditorPickerRequest(
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
    val dark = appUsesDarkTheme(config)
    val buttonSurface = if (dark) ComposeColor.Black.copy(alpha = 0.46f) else ComposeColor.White.copy(alpha = 0.62f)
    val buttonTextColor = if (dark) ComposeColor.White else ComposeColor(0xFF111111)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (backdrop != null) {
            LiquidButton(
                onClick = onClick,
                backdrop = backdrop,
                modifier = Modifier.wrapContentWidth(),
                height = 42.dp,
                surfaceColor = buttonSurface,
                contentPadding = PaddingValues(horizontal = 15.dp),
                blurRadius = 8.dp,
                lensHeight = 16.dp,
                lensAmount = 24.dp,
                chromaticAberration = false
            ) {
                Text(value, color = buttonTextColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        } else {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(50.dp))
                    .background(buttonSurface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(value, color = buttonTextColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun CourseEditorPickerOverlay(
    request: CourseEditorPickerRequest,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    renderInRootScaffold: Boolean,
    onDismiss: () -> Unit
) {
    val dark = appUsesDarkTheme(config)
    val pickerContentColor = if (dark) ComposeColor.White else ComposeColor(0xFF111111)
    var startIndex by remember(request) { mutableIntStateOf(request.startIndex.coerceIn(request.labels.indices)) }
    var endIndex by remember(request) {
        mutableIntStateOf((request.endIndex ?: request.startIndex).coerceIn(request.labels.indices).coerceAtLeast(startIndex))
    }
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = true,
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
            blurRadius = 28.dp,
            centered = true
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides pickerContentColor) {
        Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        onValueChange = { startIndex = it; if (request.endIndex != null && endIndex < it) endIndex = it },
                        range = request.labels.indices,
                        visibleItemCount = 3,
                        label = { request.labels[it] },
                        colors = pickerColors,
                        textStyle = pickerTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                    if (request.endIndex != null) {
                        top.yukonga.miuix.kmp.basic.NumberPicker(
                            value = endIndex,
                            onValueChange = { endIndex = it; if (startIndex > it) startIndex = it },
                            range = request.labels.indices,
                            visibleItemCount = 3,
                            label = { request.labels[it] },
                            colors = pickerColors,
                            textStyle = pickerTextStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickSheetLiquidAction("取消", true, backdrop, config, modifier = Modifier.weight(1f), height = 50.dp) { onDismiss() }
                QuickSheetLiquidAction("确定", true, backdrop, config, primary = true, modifier = Modifier.weight(1f), height = 50.dp) {
                    request.onConfirm(startIndex, endIndex.takeIf { request.endIndex != null })
                    onDismiss()
                }
            }
        }
        }
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
