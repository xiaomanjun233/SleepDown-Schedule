package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.itos.coui.shape.CouiShape
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.controls.CouiFormTabs
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageStyle
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarTextButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalDetailActivityFloatingOverlayHost
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsEditorProgress
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val FeedbackExpandEasing = CubicBezierEasing(0.28f, 0f, 0.20f, 1f)

@Composable
internal fun FeedbackEditorHost(
    config: ScheduleConfigEntity,
    client: FeedbackService,
    onClosed: (Boolean) -> Unit,
    content: @Composable (Modifier, () -> Unit) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var buttonBounds by remember { mutableStateOf(Rect.Zero) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val progress = remember { Animatable(if (editing) 1f else 0f) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val accent = MaterialTheme.colorScheme.primary
    val pageColor = settingsPageBackground(config)
    val parentToolbar = LocalSettingsEditorProgress.current
    val currentClose by rememberUpdatedState(onClosed)
    val overlay = LocalDetailActivityFloatingOverlayHost.current

    fun open() {
        if (editing) return
        anchor = buttonBounds.takeUnless { it.isEmpty }
        sent = false
        editing = true
    }

    fun close(submitted: Boolean) {
        if (closing) return
        sent = submitted
        keyboard?.hide()
        focus.clearFocus(force = true)
        closing = true
    }

    LaunchedEffect(editing, closing) {
        if (!editing) return@LaunchedEffect
        if (closing || sent) {
            if (anchor == null) progress.snapTo(0f)
            else progress.animateTo(0f, tween(290, easing = FeedbackExpandEasing))
            val submitted = sent
            sent = false
            closing = false
            editing = false
            currentClose(submitted)
        } else if (anchor != null && progress.value < 1f) {
            withFrameNanos { }
            progress.animateTo(1f, tween(400, easing = FeedbackExpandEasing))
        }
    }

    val editorContent: @Composable () -> Unit = {
        FeedbackEditorReveal(config, client, anchor, progress, closing, accent, pageColor, ::close)
    }
    val latestEditorContent = rememberUpdatedState(editorContent)
    val floatingEditor = remember(overlay) {
        @Composable { latestEditorContent.value() }
    }
    if (editing) {
        DisposableEffect(parentToolbar, overlay) {
            val previousToolbar = parentToolbar?.floatValue
            if (overlay == null) parentToolbar?.floatValue = 1f
            else overlay.content = floatingEditor
            onDispose {
                if (overlay == null && previousToolbar != null) parentToolbar?.floatValue = previousToolbar
                if (overlay?.content === floatingEditor) overlay.content = null
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (editing) Modifier.clearAndSetSemantics {} else Modifier)) {
            content(
                Modifier.onGloballyPositioned { buttonBounds = it.boundsInWindow() }
                    .graphicsLayer {
                        // Keep the exact glass button in place while the new surface takes it over.
                        alpha = 1f - (progress.value / 0.18f).coerceIn(0f, 1f)
                    },
                ::open,
            )
        }
        if (editing && overlay == null) editorContent()
    }
}

@Composable
private fun FeedbackEditorReveal(
    config: ScheduleConfigEntity,
    client: FeedbackService,
    anchor: Rect?,
    progress: Animatable<Float, AnimationVector1D>,
    closing: Boolean,
    accent: Color,
    pageColor: Color,
    onClose: (Boolean) -> Unit,
) {
    var hostBounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxSize().zIndex(2f)
        .onGloballyPositioned { hostBounds = it.boundsInWindow(clipBounds = false) }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        val localAnchor = anchor?.translate(-hostBounds.topLeft)
        val reveal = if (localAnchor == null) Modifier else Modifier.drawWithCache {
            val outline = Path()
            onDrawWithContent {
                val fraction = progress.value
                if (fraction >= 1f) {
                    drawContent()
                } else if (fraction > 0f) {
                    val frame = Rect(
                        lerp(localAnchor.left, 0f, fraction),
                        lerp(localAnchor.top, 0f, fraction),
                        lerp(localAnchor.right, size.width, fraction),
                        lerp(localAnchor.bottom, size.height, fraction),
                    )
                    val radius = lerp(localAnchor.height / 2f, 0f, ((fraction - 0.68f) / 0.32f).coerceIn(0f, 1f))
                    outline.rewind()
                    outline.addRoundRect(RoundRect(frame, CornerRadius(radius)))
                    clipPath(outline) {
                        drawRect(lerpColor(accent, pageColor, (fraction / 0.48f).coerceIn(0f, 1f)))
                        this@onDrawWithContent.drawContent()
                    }
                }
            }
        }
        val ready = !closing && (anchor == null || progress.value >= 1f)
        Box(Modifier.fillMaxSize().then(reveal)) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = if (anchor == null) 1f else ((progress.value - 0.22f) / 0.58f).coerceIn(0f, 1f)
                compositingStrategy = if (alpha < 1f) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
            }) {
                CompositionLocalProvider(LocalDetailActivityFloatingOverlayHost provides null) {
                    FeedbackEditor(config, client, ready, onClose)
                }
            }
        }
        if (!ready) Box(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent() }
        })
    }
}

private val FeedbackAreas = listOf("未选择", "课表与课程", "导入课表", "通知与小组件", "界面与动画", "AI 助手", "其他")
private val FeedbackKindLabels = FeedbackKind.entries.map { it.label }

@Composable
private fun FeedbackEditor(
    config: ScheduleConfigEntity,
    client: FeedbackService,
    ready: Boolean,
    onClose: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val dark = appUsesDarkTheme(config)
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(FeedbackKind.Bug) }
    var area by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var draftId by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val descriptionText = description.trim()
    val titleText = title.trim()
    val areaPrefix = area?.let { "【$it】\n" }.orEmpty()
    val payloadDescription = areaPrefix + descriptionText
    val titleLength = titleText.codePointCount(0, titleText.length)
    val descriptionLength = descriptionText.codePointCount(0, descriptionText.length)
    val maxDescriptionLength = 2000 - areaPrefix.length
    val enabled = ready && !sending
    val canSubmit = enabled && titleLength <= 80 && descriptionLength in 5..maxDescriptionLength

    fun draftChanged() {
        draftId = ""
        error = ""
    }

    fun submit() {
        if (!canSubmit || sending) return
        val id = draftId.ifBlank { UUID.randomUUID().toString().also { draftId = it } }
        // The server derives a title from the first line. Keep the category out of that title.
        val submittedTitle = if (titleText.isEmpty() && area != null) {
            val firstLine = descriptionText.lineSequence().first().trim()
            val count = firstLine.codePointCount(0, firstLine.length).coerceAtMost(40)
            firstLine.substring(0, firstLine.offsetByCodePoints(0, count))
        } else titleText
        sending = true
        error = ""
        scope.launch {
            try {
                client.submit(id, kind, submittedTitle, payloadDescription)
                Toast.makeText(context, "反馈已提交", Toast.LENGTH_SHORT).show()
                onClose(true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                if (failure is FeedbackRequestException && failure.status == 409) draftId = ""
                error = feedbackError(failure)
            } finally {
                sending = false
            }
        }
    }

    BackHandler { if (enabled) onClose(false) }
    DetailActivityScaffold(
        title = "新增反馈",
        config = config,
        centerTopBarTitle = true,
        onBack = { if (enabled) onClose(false) },
        topBarNavigation = { CouiSettingsToolbarTextButton("取消", enabled) { onClose(false) } },
        topBarActions = {
            CouiSettingsToolbarTextButton(if (sending) "提交中" else "提交", canSubmit, ::submit)
        },
    ) { backdrop ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            SleepDownSecondaryPageList(
                contentTopPadding = detailContentTopPadding() + 10.dp,
                contentBottomPadding = detailContentBottomPadding() + 12.dp,
                modifier = Modifier.widthIn(max = 620.dp).fillMaxSize(),
                style = SleepDownSecondaryPageStyle.Content,
            ) {
                item("feedback-kind") {
                    CouiFormTabs(
                        selectedIndex = kind.ordinal,
                        onSelected = {
                            if (enabled && kind.ordinal != it) {
                                kind = FeedbackKind.entries[it]
                                draftChanged()
                            }
                        },
                        dark = dark, enabled = enabled, backdrop = backdrop, labels = FeedbackKindLabels,
                    )
                }
                item("feedback-fields") {
                    SettingsGroup(backdrop, config) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            FeedbackField(
                                value = title, onValueChange = {
                                    if (it.length <= 160) { title = it; draftChanged() }
                                }, placeholder = "标题（选填）", enabled = enabled, singleLine = true, dark = dark,
                            )
                            if (titleLength > 80) Text("标题最多 80 字", color = MaterialTheme.colorScheme.error)
                            FeedbackField(
                                value = description, onValueChange = {
                                    if (it.length <= 4000) { description = it; draftChanged() }
                                },
                                placeholder = if (kind == FeedbackKind.Bug) "描述遇到的问题和复现步骤" else "说说你希望怎样改进",
                                enabled = enabled, singleLine = false, dark = dark,
                            )
                            Text(
                                if (descriptionLength > maxDescriptionLength) "描述最多 $maxDescriptionLength 字"
                                else "$descriptionLength / $maxDescriptionLength 字 · 至少填写 5 字",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (descriptionLength > maxDescriptionLength) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item("feedback-area") {
                    SettingsGroup(backdrop, config) {
                        SleepDownLiquidDropdownPreference(
                            items = FeedbackAreas, selectedIndex = area?.let(FeedbackAreas::indexOf) ?: 0, title = "涉及功能（选填）",
                            backdrop = backdrop, config = config, enabled = enabled,
                        ) { index ->
                            val selected = FeedbackAreas[index].takeIf { index > 0 }
                            if (selected != area) {
                                area = selected
                                draftChanged()
                            }
                        }
                    }
                }
                item("feedback-message") {
                    Text(
                        error.ifBlank { "原始反馈仅你和管理员可见。" },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean,
    dark: Boolean,
) {
    val foreground = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, singleLine = singleLine,
        minLines = if (singleLine) 1 else 4, maxLines = if (singleLine) 1 else 8,
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        textStyle = style.copy(color = foreground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 48.dp else 128.dp)
            .clip(CouiShape(16.dp))
            .background(if (dark) Color(0xFF28282A) else Color(0xFFF2F3F5))
            .semantics { contentDescription = if (singleLine) "反馈标题" else "反馈描述" },
        decorationBox = { input ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
                if (value.isEmpty()) Text(placeholder, color = foreground.copy(alpha = 0.52f), style = style)
                input()
            }
        },
    )
}
