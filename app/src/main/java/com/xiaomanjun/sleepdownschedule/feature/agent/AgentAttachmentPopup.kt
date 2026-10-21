package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidCascadingPopup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidMenuItem
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

/** Shares the model picker's anchored morph, including ownership of the returning trigger. */
@Composable
internal fun AgentAttachmentPopup(
    show: Boolean, imagesEnabled: Boolean, filesEnabled: Boolean,
    config: ScheduleConfigEntity, backdrop: Backdrop?, foreground: Color,
    onDismiss: () -> Unit, onToggle: () -> Unit, onImage: () -> Unit, onFile: () -> Unit
) {
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val actions = buildList {
        if (imagesEnabled) add(AddMenuAction(iconRes = R.drawable.ic_image_attachment, label = "选择图片",
            onClick = { pendingAction = onImage; onDismiss() }))
        if (filesEnabled) add(AddMenuAction(iconRes = R.drawable.ic_ai_import, label = "上传文件",
            onClick = { pendingAction = onFile; onDismiss() }))
    }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var triggerTransferred by remember { mutableStateOf(false) }
    LaunchedEffect(show) { if (show) triggerTransferred = true }
    val iconContent: @Composable () -> Unit = {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_add_course), "添加附件", tint = foreground,
                modifier = Modifier.size(24.dp))
        }
    }
    Box(Modifier.size(40.dp).onGloballyPositioned { anchor = it.boundsInRoot() }) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = if (triggerTransferred) 0f else 1f }
            .clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onToggle)) { iconContent() }
        SleepDownLiquidCascadingPopup(
            show = show && actions.isNotEmpty(), anchorBounds = anchor,
            items = actions.mapIndexed { index, action -> SleepDownLiquidMenuItem(
                key = "attachment_$index", text = action.label, iconRes = action.iconRes, onClick = action.onClick) },
            onDismissRequest = onDismiss, backdrop = backdrop, config = config,
            panelMinWidth = 184.dp,
            morphFromAnchor = true, collapseContent = iconContent,
            alignment = PopupPositionProvider.Align.Start, morphHorizontalInset = 0.dp,
            onDismissFinished = {
                triggerTransferred = false
                val action = pendingAction
                pendingAction = null
                action?.invoke()
            }
        )
    }
}
