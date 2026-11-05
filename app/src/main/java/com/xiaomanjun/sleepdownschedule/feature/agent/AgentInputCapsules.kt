package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.softBlurEnter
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.glass.ui.GlassSurface

/** Sent candidates own three direct icon actions; attachments stay centered above the input. */
@Composable
internal fun AgentInputCapsules(
    inputs: List<AgentMailbox.Input<AgentTaskInput>>, attachment: String?,
    config: ScheduleConfigEntity, backdrop: Backdrop?, foreground: Color,
    onGuide: (AgentMailbox.Input<AgentTaskInput>) -> Unit, onEdit: (AgentMailbox.Input<AgentTaskInput>) -> Unit,
    onRemove: (String) -> Unit, onRemoveAttachment: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Keep outgoing content until its slot has shrunk, so the controls above it move each frame.
    var lastAttachment by remember { mutableStateOf(attachment.orEmpty()) }
    var lastInputs by remember { mutableStateOf(inputs) }
    if (attachment != null) lastAttachment = attachment
    if (inputs.isNotEmpty()) lastInputs = inputs
    val enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 420f), expandFrom = Alignment.Bottom, clip = false)
    val exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 420f), shrinkTowards = Alignment.Bottom, clip = false) + fadeOut(tween(160))
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(attachment != null, enter = enter, exit = exit) {
            GlassSurface(backdrop, config, modifier = Modifier.padding(vertical = 6.dp).softBlurEnter(), nativeMaterial = true, nativeDark = appUsesDarkTheme(config),
                nativeElasticTouchFeedback = true, debugLabel = "AgentAttachmentCapsule") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_image_attachment), null, tint = foreground,
                        modifier = Modifier.padding(start = 13.dp).size(16.dp))
                    Text(lastAttachment, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, color = foreground,
                        modifier = Modifier.widthIn(max = 240.dp).padding(horizontal = 9.dp, vertical = 10.dp))
                    if (onRemoveAttachment != null) Icon(painterResource(R.drawable.ic_close), "移除附件",
                        tint = foreground, modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = attachment != null, onClick = onRemoveAttachment).padding(10.dp))
                }
            }
        }
        AnimatedVisibility(inputs.isNotEmpty(), enter = enter, exit = exit) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            val availableWidth = maxWidth
            LazyRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                items(if (inputs.isNotEmpty()) inputs else lastInputs, key = { it.id }) { input ->
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = spring(dampingRatio = 0.9f, stiffness = 420f), fadeOutSpec = tween(160))
                        .widthIn(max = minOf(340.dp, availableWidth)).softBlurEnter(input.id)) {
                        GlassSurface(backdrop, config, nativeMaterial = true, nativeDark = appUsesDarkTheme(config),
                            nativeElasticTouchFeedback = true, debugLabel = "AgentQueuedInput") {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(if (input.mode == AgentMailbox.Mode.STEER) R.drawable.ic_ai_guide else R.drawable.ic_import_step),
                                    if (input.mode == AgentMailbox.Mode.STEER) "待引导" else "待处理", tint = foreground, modifier = Modifier.size(16.dp))
                                Text(input.value.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = foreground, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Row {
                                    @Composable fun action(icon: Int, label: String, onClick: () -> Unit) {
                                        Icon(painterResource(icon), label, tint = foreground,
                                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                                .clickable(enabled = inputs.any { it.id == input.id }, role = Role.Button, onClick = onClick).padding(10.dp))
                                    }
                                    action(R.drawable.ic_edit, "编辑", { onEdit(input) })
                                    action(R.drawable.ic_delete_history, "删除", { onRemove(input.id) })
                                    action(R.drawable.ic_ai_guide, "引导", { onGuide(input) })
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
