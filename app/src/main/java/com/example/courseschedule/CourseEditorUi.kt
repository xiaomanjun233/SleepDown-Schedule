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
fun CourseEditorScreen(
    state: AppState,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?
) {
    var name by remember(initialCourse) { mutableStateOf(initialCourse?.name.orEmpty()) }
    var teacher by remember(initialCourse) { mutableStateOf(initialCourse?.teacher.orEmpty()) }
    var location by remember(initialCourse) { mutableStateOf(initialCourse?.location.orEmpty()) }
    var weekday by remember(initialCourse) { mutableStateOf(initialCourse?.weekday ?: 1) }
    val rawPeriodValues = state.periods.map { it.periodIndex }
    val coursePeriodValues = initialCourse?.periods.orEmpty()
    val periodValues = (rawPeriodValues + coursePeriodValues).distinct().sorted()
    var periodStart by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.minOrNull() ?: (periodValues.firstOrNull() ?: 1)) }
    var periodEnd by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.maxOrNull() ?: periodStart) }
    var weekStart by remember(initialCourse, state.config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.minOrNull() ?: 1) }
    var weekEnd by remember(initialCourse, state.config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.maxOrNull() ?: state.config.totalWeeks) }
    var parity by remember(initialCourse) { mutableStateOf(initialCourse?.weekParity ?: WeekParity.ALL) }
    var note by remember(initialCourse) { mutableStateOf(initialCourse?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var periodInputValid by remember(initialCourse, periodValues) { mutableStateOf(true) }
    var weekInputValid by remember(initialCourse, state.config.totalWeeks) { mutableStateOf(true) }
    val selectedPeriods = if (periodStart <= periodEnd) periodValues.filter { it in periodStart..periodEnd } else emptyList()
    val selectedWeeks = if (weekStart <= weekEnd) (weekStart..weekEnd).toList() else emptyList()
    val dialogTextColor = glassForegroundColor(state.config)

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header", contentType = "header") {
            NormalizedDialogHeader(
                title = if (initialCourse == null) "添加单节课" else "编辑单节课",
                onCancel = onCancel,
                backdrop = backdrop,
                config = state.config,
                onSave = {
                    when {
                        name.isBlank() -> error = "课程名称不能为空"
                        !periodInputValid -> error = "请先修正节次范围"
                        !weekInputValid -> error = "请先修正周次范围"
                        selectedPeriods.isEmpty() -> error = "请选择节次"
                        selectedWeeks.isEmpty() -> error = "请选择周次"
                        else -> onSave(
                            CourseEntity(
                                id = initialCourse?.id ?: 0,
                                name = name.trim(),
                                teacher = teacher.ifBlank { null },
                                location = location.ifBlank { null },
                                weekday = weekday,
                                periods = selectedPeriods,
                                weeks = selectedWeeks,
                                weekParity = parity,
                                note = note.ifBlank { null },
                                scheduleId = initialCourse?.scheduleId ?: 0
                            )
                        )
                    }
                }
            )
        }
        item { OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        item { OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        item { OutlinedTextField(location, { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        item { WheelPicker("星期", (1..7).toList(), weekday, { weekday = it }) { "周" + weekdayLabel(it) } }
        item(key = "period-range", contentType = "range-picker") {
            RangeWheelPicker(
                title = "节次",
                values = periodValues,
                start = periodStart,
                end = periodEnd,
                onStart = { periodStart = it },
                onEnd = { periodEnd = it },
                enforceOrderedInput = true,
                onInputValidChange = { periodInputValid = it }
            ) { "第" + it + "节" }
        }
        item(key = "week-range", contentType = "range-picker") {
            RangeWheelPicker(
                title = "周次",
                values = (1..state.config.totalWeeks).toList(),
                start = weekStart,
                end = weekEnd,
                onStart = { weekStart = it },
                onEnd = { weekEnd = it },
                enforceOrderedInput = true,
                onInputValidChange = { weekInputValid = it },
                invalidRangeMessage = "当前结束周早于开始周"
            ) { "第" + it + "周" }
        }
        item { WheelPicker("单双周", WeekParity.entries, parity, { parity = it }) { parityLabel(it) } }
        item { OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = dialogTextColor)) }
        if (initialCourse != null) {
            item { LiquidMenuButton(backdrop, "删除课程", destructive = true, onClick = { onDelete(initialCourse) }) }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
fun DialogHeader(title: String, onCancel: () -> Unit, onSave: () -> Unit, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig()) {
    LiquidDialogHeader(title, onCancel, backdrop, config, onConfirm = onSave)
}

@Composable
fun DialogScaffold(title: String, onCancel: () -> Unit, backdrop: Backdrop? = null, config: ScheduleConfigEntity = defaultConfig(), content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LiquidDialogHeader(title, onCancel, backdrop, config)
        content()
    }
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
fun NormalizedDialogScaffold(
    title: String,
    onCancel: () -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    contentColor: ComposeColor = glassForegroundColor(config),
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LiquidDialogHeader(title, onCancel, backdrop, config)
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun NormalizedCourseEditorScreen(
    state: AppState,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?
) {
    val formData = remember(state.config, state.periods) {
        CourseEditorFormData(
            config = state.config,
            periods = state.periods
        )
    }
    NormalizedCourseEditorScreen(
        formData = formData,
        initialCourse = initialCourse,
        onCancel = onCancel,
        onSave = onSave,
        onDelete = onDelete,
        backdrop = backdrop
    )
}

@Immutable
data class CourseEditorFormData(
    val config: ScheduleConfigEntity,
    val periods: List<PeriodEntity>
)

@Composable
fun NormalizedCourseEditorScreen(
    formData: CourseEditorFormData,
    initialCourse: CourseEntity?,
    onCancel: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    backdrop: Backdrop?
) {
    val config = formData.config
    var name by remember(initialCourse) { mutableStateOf(initialCourse?.name.orEmpty()) }
    var teacher by remember(initialCourse) { mutableStateOf(initialCourse?.teacher.orEmpty()) }
    var location by remember(initialCourse) { mutableStateOf(initialCourse?.location.orEmpty()) }
    var weekday by remember(initialCourse) { mutableIntStateOf(initialCourse?.weekday ?: 1) }
    val rawPeriodValues = remember(formData.periods) { formData.periods.map { it.periodIndex } }
    val coursePeriodValues = initialCourse?.periods.orEmpty()
    val periodValues = (rawPeriodValues + coursePeriodValues).distinct().sorted()
    var periodStart by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.minOrNull() ?: (periodValues.firstOrNull() ?: 1)) }
    var periodEnd by remember(initialCourse, periodValues) { mutableIntStateOf(initialCourse?.periods?.maxOrNull() ?: periodStart) }
    var weekStart by remember(initialCourse, config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.minOrNull() ?: 1) }
    var weekEnd by remember(initialCourse, config.totalWeeks) { mutableIntStateOf(initialCourse?.weeks?.maxOrNull() ?: config.totalWeeks) }
    var parity by remember(initialCourse) { mutableStateOf(initialCourse?.weekParity ?: WeekParity.ALL) }
    var note by remember(initialCourse) { mutableStateOf(initialCourse?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var periodInputValid by remember(initialCourse, periodValues) { mutableStateOf(true) }
    var weekInputValid by remember(initialCourse, config.totalWeeks) { mutableStateOf(true) }
    val selectedPeriods = if (periodStart <= periodEnd) periodValues.filter { it in periodStart..periodEnd } else emptyList()
    val selectedWeeks = if (weekStart <= weekEnd) (weekStart..weekEnd).toList() else emptyList()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header", contentType = "header") {
            DialogHeader(
                title = if (initialCourse == null) "\u6DFB\u52A0\u5355\u8282\u8BFE" else "\u7F16\u8F91\u5355\u8282\u8BFE",
                onCancel = onCancel,
                backdrop = backdrop,
                config = config,
                onSave = {
                    when {
                        name.isBlank() -> error = "\u8BFE\u7A0B\u540D\u79F0\u4E0D\u80FD\u4E3A\u7A7A"
                        !periodInputValid -> error = "\u8BF7\u5148\u4FEE\u6B63\u8282\u6B21\u8303\u56F4"
                        !weekInputValid -> error = "\u8BF7\u5148\u4FEE\u6B63\u5468\u6B21\u8303\u56F4"
                        selectedPeriods.isEmpty() -> error = "\u8BF7\u9009\u62E9\u8282\u6B21"
                        selectedWeeks.isEmpty() -> error = "\u8BF7\u9009\u62E9\u5468\u6B21"
                        else -> onSave(
                            CourseEntity(
                                id = initialCourse?.id ?: 0,
                                name = name.trim(),
                                teacher = teacher.ifBlank { null },
                                location = location.ifBlank { null },
                                weekday = weekday,
                                periods = selectedPeriods,
                                weeks = selectedWeeks,
                                weekParity = parity,
                                note = note.ifBlank { null },
                                scheduleId = initialCourse?.scheduleId ?: 0
                            )
                        )
                    }
                }
            )
        }
        item(key = "course-name", contentType = "field") { DialogCapsuleField(name, { name = it }, "\u8BFE\u7A0B\u540D\u79F0", config, Modifier.fillMaxWidth()) }
        item(key = "teacher", contentType = "field") { DialogCapsuleField(teacher, { teacher = it }, "\u6559\u5E08", config, Modifier.fillMaxWidth()) }
        item(key = "location", contentType = "field") { DialogCapsuleField(location, { location = it }, "\u5730\u70B9", config, Modifier.fillMaxWidth()) }
        item(key = "weekday", contentType = "picker") { WheelPicker("\u661F\u671F", (1..7).toList(), weekday, { weekday = it }, backdrop, config) { "\u5468" + weekdayLabel(it) } }
        item(key = "period-range", contentType = "range-picker") {
            DialogRangePicker(
                title = "\u8282\u6B21",
                values = periodValues,
                start = periodStart,
                end = periodEnd,
                onStart = { periodStart = it },
                onEnd = { periodEnd = it },
                backdrop = backdrop,
                config = config,
                enforceOrderedInput = true,
                onInputValidChange = { periodInputValid = it }
            ) { "\u7B2C" + it + "\u8282" }
        }
        item(key = "week-range", contentType = "range-picker") {
            DialogRangePicker(
                title = "\u5468\u6B21",
                values = remember(config.totalWeeks) { (1..config.totalWeeks).toList() },
                start = weekStart,
                end = weekEnd,
                onStart = { weekStart = it },
                onEnd = { weekEnd = it },
                backdrop = backdrop,
                config = config,
                enforceOrderedInput = true,
                onInputValidChange = { weekInputValid = it },
                invalidRangeMessage = "\u5F53\u524D\u7ED3\u675F\u5468\u65E9\u4E8E\u5F00\u59CB\u5468"
            ) { "\u7B2C" + it + "\u5468" }
        }
        item(key = "parity", contentType = "picker") { WheelPicker("\u5355\u53CC\u5468", WeekParity.entries, parity, { parity = it }, backdrop, config) { parityLabel(it) } }
        item(key = "note", contentType = "field") { DialogCapsuleField(note, { note = it }, "\u5907\u6CE8", config, Modifier.fillMaxWidth()) }
        if (initialCourse != null) {
            item(key = "delete", contentType = "action") { DialogLiquidButton(backdrop, "\u5220\u9664\u8BFE\u7A0B", { onDelete(initialCourse) }, role = DialogButtonRole.Cancel) }
        }
        error?.let { message ->
            item(key = "error", contentType = "message") { Text(message, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun <T> WheelPicker(
    title: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity = defaultConfig(),
    label: (T) -> String
) {
    DialogOptionPicker(title, values, selected, onSelected, label, backdrop, config)
}

@Composable
fun RangeWheelPicker(
    title: String,
    values: List<Int>,
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    backdrop: Backdrop? = null,
    config: ScheduleConfigEntity = defaultConfig(),
    enforceOrderedInput: Boolean = false,
    onInputValidChange: (Boolean) -> Unit = {},
    invalidRangeMessage: String = "当前结束节早于开始节",
    label: (Int) -> String
) {
    var startText by remember(start) { mutableStateOf(start.toString()) }
    var endText by remember(end) { mutableStateOf(end.toString()) }
    fun clamp(value: String): Int? {
        val parsed = value.toIntOrNull() ?: return null
        return parsed.coerceIn(values.firstOrNull() ?: parsed, values.lastOrNull() ?: parsed)
    }
    val draftStart = clamp(startText)
    val draftEnd = clamp(endText)
    val inputComplete = draftStart != null && draftEnd != null
    val orderedInvalid = enforceOrderedInput && draftStart != null && draftEnd != null && draftEnd < draftStart
    val inputValid = inputComplete && !orderedInvalid
    LaunchedEffect(inputValid) { onInputValidChange(inputValid) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = startText,
                onValueChange = {
                    startText = it.filter(Char::isDigit)
                    clamp(startText)?.let(onStart)
                },
                label = { Text("开始") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endText,
                onValueChange = {
                    endText = it.filter(Char::isDigit)
                    clamp(endText)?.let(onEnd)
                },
                label = { Text("结束") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        if (orderedInvalid) {
            Text(invalidRangeMessage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        } else {
            val previewStart = draftStart ?: start
            val previewEnd = draftEnd ?: end
            Text("${label(minOf(previewStart, previewEnd))} - ${label(maxOf(previewStart, previewEnd))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun DialogRangePicker(
    title: String,
    values: List<Int>,
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    enforceOrderedInput: Boolean = false,
    onInputValidChange: (Boolean) -> Unit = {},
    invalidRangeMessage: String = "\u5F53\u524D\u7ED3\u675F\u8282\u65E9\u4E8E\u5F00\u59CB\u8282",
    label: (Int) -> String
) {
    var startText by remember(start) { mutableStateOf(start.toString()) }
    var endText by remember(end) { mutableStateOf(end.toString()) }
    fun clamp(value: String): Int? {
        val parsed = value.toIntOrNull() ?: return null
        return parsed.coerceIn(values.firstOrNull() ?: parsed, values.lastOrNull() ?: parsed)
    }
    val draftStart = clamp(startText)
    val draftEnd = clamp(endText)
    val inputComplete = draftStart != null && draftEnd != null
    val orderedInvalid = enforceOrderedInput && draftStart != null && draftEnd != null && draftEnd < draftStart
    val inputValid = inputComplete && !orderedInvalid
    LaunchedEffect(inputValid) { onInputValidChange(inputValid) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DialogCapsuleField(
                value = startText,
                onValueChange = {
                    startText = it.filter(Char::isDigit)
                    clamp(startText)?.let(onStart)
                },
                placeholder = "\u5F00\u59CB",
                config = config,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            DialogCapsuleField(
                value = endText,
                onValueChange = {
                    endText = it.filter(Char::isDigit)
                    clamp(endText)?.let(onEnd)
                },
                placeholder = "\u7ED3\u675F",
                config = config,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
        }
        if (orderedInvalid) {
            Text(
                invalidRangeMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val previewStart = draftStart ?: start
            val previewEnd = draftEnd ?: end
            Text(
                "${label(minOf(previewStart, previewEnd))} - ${label(maxOf(previewStart, previewEnd))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MultiWheelPicker(title: String, values: List<Int>, selected: Set<Int>, onSelected: (Set<Int>) -> Unit, label: (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(values.size) { index ->
                val value = values[index]
                val active = value in selected
                Text(
                    label(value),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else ComposeColor.Transparent)
                        .clickable { onSelected(if (active) selected - value else selected + value) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun MissingCourseScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("请先配置课程表。")
        Button(onClick = onBack) { Text("返回") }
    }
}
