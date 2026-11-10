package com.xiaomanjun.sleepdownschedule.feature.importing

import android.graphics.BlendMode
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.segmentbutton.CouiSegmentedControlDefaults
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBackdropBlur
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBlurDirection

private val ImportTabLabels = listOf("常规导入", "教务系统导入")
// SecurityPermission's privacy and security dashboard overrides the light segment track.
private val LightImportSegmentTrack = Color(0xFFE2E3E4)

@Composable
internal fun UnifiedImportTabs(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    dark: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val track = if (dark) CouiSegmentedControlDefaults.containerColor(true) else LightImportSegmentTrack
    if (Build.VERSION.SDK_INT >= 34) {
        val material = if (backdrop != null && !AppMaterialPreferences.isSuperPerformance) {
            rememberCouiGlassBackdrop(backdrop)
        } else null
        val lightTrackMaterial = remember {
            CouiSegmentedControlDefaults.material(false).copy(
                secondColor = LightImportSegmentTrack.copy(alpha = 0.92f),
                secondBlendMode = BlendMode.SRC_OVER,
            )
        }
        SegmentedControl(
            modifier = modifier.fillMaxWidth(), labels = ImportTabLabels, selectedIndex = selectedIndex,
            onSelected = onSelected, dark = dark, enabled = enabled, backdrop = material,
            commitOnRelease = true, flingOnRelease = true,
            containerColor = track, containerMaterial = lightTrackMaterial.takeUnless { dark },
        )
        return
    }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val indicator by animateFloatAsState(
        dragPosition ?: selectedIndex.toFloat(),
        animationSpec = if (dragPosition == null) spring() else snap(),
        label = "import-tab",
    )
    val select by rememberUpdatedState(onSelected)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val decay = rememberSplineBasedDecay<Float>()
    BoxWithConstraints(
        modifier.fillMaxWidth().height(CouiSegmentedControlDefaults.height)
            .clip(CouiCapsuleShape).background(track)
            .selectableGroup()
            .pointerInput(enabled, rtl, decay) {
                if (!enabled) return@pointerInput
                val tracker = VelocityTracker()
                var lastMove = 0L
                var position = 0f
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        if (dragPosition != null) {
                            val speed = if (SystemClock.uptimeMillis() - lastMove < 40)
                                tracker.calculateVelocity().x else 0f
                            val end = decay.calculateTargetValue(position, speed)
                            select(if ((end < size.width / 2f) != rtl) 0 else 1)
                        }
                        dragPosition = null
                    },
                    onDragCancel = { dragPosition = null },
                ) { change, _ ->
                    change.consume()
                    tracker.addPointerInputChange(change)
                    position = change.position.x
                    lastMove = change.uptimeMillis
                    val fraction = (change.position.x * 2f / size.width.coerceAtLeast(1) - 0.5f).coerceIn(0f, 1f)
                    dragPosition = if (rtl) 1f - fraction else fraction
                }
            }
            .padding(4.dp)
    ) {
        val halfWidth = maxWidth / 2
        Box(
            Modifier.offset(x = halfWidth * indicator).width(halfWidth).fillMaxHeight()
                .background(CouiSegmentedControlDefaults.indicatorColor(dark), CouiCapsuleShape)
        )
        Row(Modifier.fillMaxSize()) {
            ImportTabLabels.forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = selectedIndex == index, enabled = enabled, role = Role.Tab,
                        onClick = { onSelected(index) }
                    ).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun InlineRegularImportForm(
    config: ScheduleConfigEntity,
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    resolving: Boolean,
    hasPreview: Boolean,
    message: String?,
    hasError: Boolean,
    onParse: () -> Unit,
    onUpload: () -> Unit,
    header: @Composable () -> Unit,
    preview: @Composable (header: @Composable () -> Unit) -> Unit
) {
    val dark = appUsesDarkTheme(config)
    val foreground = if (dark) Color.White else Color(0xFF161616)
    val inputContent by rememberUpdatedState<@Composable () -> Unit> {
        Column(
            Modifier.fillMaxWidth().clip(CouiShape(24.dp))
                .background(if (dark) Color(0xFF1C1C1E) else Color.White)
                .padding(start = 16.dp, top = 15.dp, end = 12.dp, bottom = 8.dp)
        ) {
            BasicTextField(
                value = text, onValueChange = onTextChange, enabled = enabled, minLines = 4, maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = foreground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                    .semantics { contentDescription = "导入口令" },
                decorationBox = { input ->
                    Box {
                        if (text.isEmpty()) {
                            Text("粘贴课表口令或输入课表内容", color = foreground.copy(alpha = 0.48f),
                                style = MaterialTheme.typography.bodyLarge)
                        }
                        input()
                    }
                }
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (text.isNotBlank() && !hasPreview) {
                    TextButton(onClick = onParse, enabled = enabled) { Text("预览课表") }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onUpload, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Box(
                        Modifier.size(36.dp).background(
                            if (dark) Color(0xFF303032) else Color(0xFFF0F1F3), CouiCapsuleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AttachFile, "上传文件", Modifier.size(21.dp),
                            tint = foreground.copy(alpha = if (enabled) 0.88f else 0.4f))
                    }
                }
            }
        }
    }
    val input = remember { movableContentOf { inputContent() } }
    val form: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            header()
            input()
        }
    }
    val scroll = rememberScrollState()
    val navigationInset = regularImportNavigationInset()
    val showBottomBlur = navigationInset > 0.dp && (hasPreview || scroll.canScrollForward || scroll.canScrollBackward)
    val pageColor = settingsPageBackground(config)
    val contentBackdrop = if (showBottomBlur && Build.VERSION.SDK_INT >= 33 && !AppMaterialPreferences.isSuperPerformance)
        rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "regular-import-bottom") else null
    Box(Modifier.fillMaxSize().imePadding()) {
        val producer = if (contentBackdrop != null) Modifier.glassBackdropProducer(contentBackdrop) else Modifier
        Box(Modifier.matchParentSize().then(producer).background(pageColor)) {
            if (hasPreview) preview(form)
            else Column(Modifier.fillMaxSize().couiVerticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, bottom = navigationInset)) {
                form()
                Text(
                    message ?: if (resolving) "正在读取导入内容…" else "支持 SleepDown、WakeUp、星链口令，也可以上传课表文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasError) MaterialTheme.colorScheme.error else foreground.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                )
            }
        }
        if (showBottomBlur) {
            ProgressiveBackdropBlur(
                backdrop = contentBackdrop, modifier = Modifier.align(Alignment.BottomCenter),
                tintColor = pageColor, height = navigationInset + 36.dp, blurRadius = 12.dp,
                direction = ProgressiveBlurDirection.BottomToTop,
                fallbackTintStops = listOf(0f to pageColor, .45f to pageColor.copy(alpha = .65f), 1f to Color.Transparent),
            )
        }
    }
}

@Composable
internal fun regularImportNavigationInset() = with(LocalDensity.current) {
    (WindowInsets.navigationBars.getBottom(this) - WindowInsets.ime.getBottom(this)).coerceAtLeast(0).toDp()
}
