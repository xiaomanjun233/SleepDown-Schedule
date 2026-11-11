package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogWindowProvider
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.*
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects

/** Missing sampling pixels change only the decoration, never the window or its content. */
@Composable
private fun dialogMaterial(dark: Boolean, backdrop: CouiBackdrop?): CouiDialogMaterial? =
    if (backdrop != null && LocalVisualEffects.current.usesGlowMaterials &&
        !AppMaterialPreferences.isSuperPerformance) CouiDialogDefaults.material(dark) else null

/** 业务只在实际退场后交接；移除一个仍显示的窗口（如模式切换）不触发确认。 */
private class DialogLifetime {
    var closed by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var started by mutableStateOf(false)
    var pending: (() -> Unit)? = null
    fun close(action: () -> Unit) {
        if (closed) return
        pending = action
        closed = true
    }
}

@Composable
private fun DialogCompletion(lifetime: DialogLifetime, onFinished: (() -> Unit)?) {
    val latest = rememberUpdatedState(onFinished)
    LaunchedEffect(lifetime.finished) {
        if (lifetime.finished) {
            lifetime.finished = false
            lifetime.started = false
            val action = lifetime.pending
            lifetime.pending = null
            action?.invoke()
            latest.value?.invoke()
        }
    }
}

@Composable
private fun ControlDialogBody(hostSize: androidx.compose.ui.unit.IntSize, dark: Boolean, content: @Composable () -> Unit) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val glowMaterial = LocalVisualEffects.current.usesGlowMaterials && !AppMaterialPreferences.isSuperPerformance
    CompositionLocalProvider(LocalControlDialogSurface provides true,
        LocalControlDialogHeading provides { title -> DialogHeading(title, CouiDialogDefaults.themeColors(dark)) },
        LocalControlDialogActionBar provides { original, modifier ->
            val converted = couiActions(original.map { it.copy(dismissOnClick = false) }, null, dark)
            val slots = actionSlots(original, converted)
            slots[0]?.let { confirm -> DialogActionBar(confirm, slots[1], slots[2], dark,
                material = glowMaterial,
                modifier = modifier) }
        },
        LocalControlDialogHostSize provides hostSize, LocalControlDialogWindow provides window,
        LocalControlPickerStyle provides ControlPickerStyle(18.sp, FontWeight(550),
            CouiDialogDefaults.buttonHeight, 0.dp, 16.sp, FontWeight.Medium),
        LocalContentColor provides if (dark) Color(0xE6FFFFFF) else Color(0xE6000000)) { content() }
}

@Composable
private fun couiActions(actions: List<LiquidAlertAction>, lifetime: DialogLifetime?, dark: Boolean): List<CouiDialogAction> {
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 1f)
    val base = CouiDialogDefaults.colors(dark)
    return actions.map { action ->
        val colors = if (action.style == LiquidAlertActionStyle.Primary)
            CouiDialogDefaults.primaryActionColors(accent) else if (action.style == LiquidAlertActionStyle.Destructive) CouiDialogActionColors(
                Color(if (dark) 0x33FFFFFF else 0xA6FFFFFF),
                if (action.style == LiquidAlertActionStyle.Destructive) base.destructive else base.action,
                Color(if (dark) 0x1AFFFFFF else 0x14000000), base.disabledAction) else null
        CouiDialogAction(action.label, {
            if (action.enabled) {
                if (action.dismissOnClick && lifetime != null) lifetime.close(action.onClick) else action.onClick()
            }
        }, action.enabled, if (action.style == LiquidAlertActionStyle.Primary)
            CouiDialogActionStyle.Primary else CouiDialogActionStyle.Secondary, colors)
    }
}

/** 三操作的推荐项占 neutral 槽以维持原有“推荐、普通、取消”顺序；颜色由语义显式提供。 */
private fun actionSlots(original: List<LiquidAlertAction>, converted: List<CouiDialogAction>): List<CouiDialogAction?> {
    if (original.isEmpty()) return listOf(null, null, null)
    if (original.size == 1) return listOf(converted[0], null, null)
    val cancel = original.indexOfFirst { it.style == LiquidAlertActionStyle.Cancel }
    val primary = original.indexOfFirst { it.style == LiquidAlertActionStyle.Primary }
    if (original.size == 2) {
        val confirm = if (primary >= 0) primary else 1
        return listOf(converted[confirm], converted[1 - confirm], null)
    }
    val neutral = if (primary >= 0) primary else 0
    val remaining = original.indices.filter { it != neutral }
    val dismiss = if (cancel in remaining) cancel else remaining.last()
    val confirm = remaining.first { it != dismiss }
    return listOf(converted[confirm], converted[dismiss], converted[neutral])
}

@Composable
internal fun ControlAlertDialog(request: AlertDialogRequest) {
    val backdrop = rememberCouiWindowBackdrop()
    val lifetime = remember { DialogLifetime() }
    DialogCompletion(lifetime, request.onDismissFinished)
    val visible = request.visible && !lifetime.closed
    val hostSize = LocalControlDialogHostSize.current ?: LocalWindowInfo.current.containerSize
    val actions = couiActions(request.actions, lifetime, request.dark)
    val slots = actionSlots(request.actions, actions)
    val dismiss = {
        if (request.deferDismissUntilHost) request.onDismissRequest()
        else lifetime.close(request.onDismissRequest)
    }
    val material = dialogMaterial(request.dark, backdrop)
    if (slots[0] != null) {
        // 标准 Alert 直接消费上游排版、滚动和动作区，不用宿主 Text 近似其字体与行高。
        com.itos.coui.dialog.Dialog(onDismissRequest = dismiss, confirmAction = checkNotNull(slots[0]),
            dismissAction = slots[1], neutralAction = slots[2],
            title = request.title.takeIf { it.isNotBlank() },
            message = request.message.takeIf { request.messageContent == null && it.isNotBlank() },
            dark = request.dark, colors = CouiDialogDefaults.themeColors(request.dark),
            material = material, backdrop = backdrop, visible = visible,
            onDismissFinished = { lifetime.finished = true }) {
            request.messageContent?.let { body ->
                ControlDialogBody(hostSize, request.dark) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = CouiDialogDefaults.contentPadding)
                        .padding(bottom = CouiDialogDefaults.messageBottom)) { body() }
                }
            }
        }
        return
    }
    ContentDialog(onDismissRequest = dismiss, dark = request.dark,
        colors = CouiDialogDefaults.themeColors(request.dark), placement = CouiDialogPlacement.Bottom,
        material = material,
        backdrop = backdrop, visible = visible,
        onDismissFinished = { lifetime.finished = true },
        title = request.title.takeIf { it.isNotBlank() }, confirmAction = slots[0],
        dismissAction = slots[1], neutralAction = slots[2],
        contentPadding = PaddingValues(0.dp),
        adjustForIme = request.messageContent != null,
        scrollable = request.scrollableMessageContent || request.messageContent == null) {
        ControlDialogBody(hostSize, request.dark) {
            if (request.messageContent != null) request.messageContent.invoke()
            else if (request.message.isNotBlank()) androidx.compose.material3.Text(request.message,
                color = CouiDialogDefaults.colors(request.dark).message,
                modifier = Modifier.heightIn(max = request.messageMaxHeight), style = MaterialTheme.typography.bodyLarge)
        }
    }
    return
}

@Composable
internal fun ControlPickerDialog(request: PickerDialogRequest) {
    val backdrop = rememberCouiWindowBackdrop()
    val lifetime = remember { DialogLifetime() }
    if (request.show) lifetime.started = true
    DialogCompletion(lifetime, request.onFinished)
    if (!request.show && !lifetime.started) return
    val hostSize = LocalControlDialogHostSize.current ?: LocalWindowInfo.current.containerSize
    // Picker 的确认/取消由宿主修改 show；不能在手势返回时抢先销毁尚有未保存内容的窗口。
    val actions = couiActions(request.actions.orEmpty().map { it.copy(dismissOnClick = false) }, lifetime, request.dark)
    val slots = actionSlots(request.actions.orEmpty(), actions)
    val material = dialogMaterial(request.dark, backdrop)
    // 与 DemoPickerDialog 一致：直接使用 Dialog 的标题、正文槽与固定操作区。
    if (slots[0] != null && request.titleAction == null && request.bottomActions == null && !request.adjustForIme) {
        com.itos.coui.dialog.Dialog(onDismissRequest = request.onDismiss, visible = request.show,
            title = request.title, message = request.message, confirmAction = checkNotNull(slots[0]),
            dismissAction = slots[1], neutralAction = slots[2],
            dark = request.dark, material = material, backdrop = backdrop,
            colors = CouiDialogDefaults.themeColors(request.dark),
            onDismissFinished = { lifetime.finished = true }) {
            ControlDialogBody(hostSize, request.dark, request.content)
        }
        return
    }
    ContentDialog(onDismissRequest = request.onDismiss, dark = request.dark,
        colors = CouiDialogDefaults.themeColors(request.dark), material = material,
        backdrop = backdrop, visible = request.show, placement = CouiDialogPlacement.Bottom,
        onDismissFinished = { lifetime.finished = true },
        title = request.title, titleAction = request.titleAction,
        confirmAction = slots[0], dismissAction = slots[1], neutralAction = slots[2],
        contentPadding = PaddingValues(0.dp),
        scrollable = request.scrollable, adjustForIme = request.adjustForIme, footer = request.bottomActions?.let { footer -> {
            ControlDialogBody(hostSize, request.dark) {
                footer()
            }
        } }) {
        ControlDialogBody(hostSize, request.dark) {
            if (request.message.isNullOrBlank()) request.content()
            else Column {
                androidx.compose.material3.Text(
                    request.message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CouiDialogDefaults.contentPadding)
                        .padding(bottom = CouiDialogDefaults.messageBottom),
                    style = MaterialTheme.typography.bodyLarge,
                )
                request.content()
            }
        }
    }
    return
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun ControlNameDialog(title: String, initialName: String, dark: Boolean, maxLength: Int?,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit, onDraftChange: (String) -> Unit) {
    val backdrop = rememberCouiWindowBackdrop()
    val editor = rememberTextFieldState(initialName)
    val draftChange = rememberUpdatedState(onDraftChange)
    LaunchedEffect(editor) {
        snapshotFlow { editor.text.toString() }.collect { draftChange.value(it) }
    }
    val lifetime = remember { DialogLifetime() }
    DialogCompletion(lifetime, null)
    val transformation = remember(maxLength) { maxLength?.let { limit -> InputTransformation {
        val filter = android.text.InputFilter.LengthFilter(limit)
        val original = android.text.SpannedString(originalText)
        for (index in changes.changeCount - 1 downTo 0) {
            val range = changes.getRange(index)
            val previous = changes.getOriginalRange(index)
            val filtered = filter.filter(asCharSequence(), range.start, range.end, original, previous.start, previous.end)
            if (filtered != null) replace(range.start, range.end, filtered.toString())
        }
    } } }
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 1f)
    InputDialog(state = editor, onDismissRequest = { lifetime.close(onDismiss) }, title = title,
        dark = dark, accent = accent, placeholder = "课表名称", clearContentDescription = "清空课表名称",
        confirmAction = CouiDialogAction("完成", { lifetime.close { onConfirm(editor.text.toString().trim()) } },
            style = CouiDialogActionStyle.Primary, colors = CouiDialogDefaults.primaryActionColors(accent)),
        dismissAction = CouiDialogAction("取消", { lifetime.close(onDismiss) }),
        colors = CouiDialogDefaults.themeColors(dark), material = dialogMaterial(dark, backdrop),
        backdrop = backdrop, visible = !lifetime.closed, inputTransformation = transformation,
        onDismissFinished = { lifetime.finished = true })
    return
}
