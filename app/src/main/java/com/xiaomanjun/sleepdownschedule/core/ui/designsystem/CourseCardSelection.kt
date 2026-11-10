package com.xiaomanjun.sleepdownschedule.core.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.checkbox.CouiCheckboxDefaults
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.text.CouiTextKind
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiMaterialEnabled
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBar
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalSettingsEditorProgress
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val CourseManagementCardCornerRadius = 20.dp
internal val CourseManagementCardShape = RoundedRectangle(CourseManagementCardCornerRadius)

@Stable
internal class CourseCardSelection {
    private var keys by mutableStateOf<Set<String>?>(null)
    val active: Boolean get() = keys != null
    val selectedKeys: Set<String> get() = keys.orEmpty()
    var deleting by mutableStateOf(false)

    fun start(key: String) {
        keys = setOf(key)
    }

    fun toggle(key: String) {
        val current = keys ?: return
        if (!deleting) keys = if (key in current) current - key else current + key
    }

    fun selectAll(available: Set<String>) {
        if (active && !deleting) keys = if (selectedKeys.containsAll(available)) emptySet() else available
    }

    fun cancel() {
        if (!deleting) keys = null
    }
}

@Composable
internal fun Modifier.courseCardInteraction(
    selection: CourseCardSelection,
    key: String,
    enabled: Boolean,
    dark: Boolean,
    onLongClick: () -> Unit = { selection.start(key) },
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val interactive = enabled && !selection.deleting
    val feedback = couiButtonFeedback(dark, interaction, interactive)
    return if (selection.active) {
        feedback.toggleable(key in selection.selectedKeys, interaction, null, interactive, Role.Checkbox) {
            selection.toggle(key)
        }
    } else {
        feedback.combinedClickable(
            interactionSource = interaction, indication = null, enabled = interactive,
            role = Role.Button, onLongClickLabel = "选择课程", onLongClick = onLongClick, onClick = onClick
        )
    }
}

internal val CourseCardDeleteBottomSpace = 80.dp

@Composable
internal fun CourseCardSelectionCheck(checked: Boolean, enabled: Boolean, dark: Boolean) {
    Checkbox(checked = checked, enabled = enabled,
        colors = CouiCheckboxDefaults.colors(MaterialTheme.colorScheme.primary, dark))
}

/** Draws the editing chrome after the list that supplies the material. */
@Composable
internal fun <T> BoxScope.CourseCardSelectionChrome(
    selection: CourseCardSelection,
    cards: Map<String, T>,
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    confirmationTitle: String,
    confirmationMessage: (Int) -> String,
    onDelete: suspend (List<T>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pending by remember(selection) { mutableStateOf<List<T>?>(null) }
    var error by remember(selection) { mutableStateOf<String?>(null) }
    val selected = selection.selectedKeys.mapNotNull(cards::get)
    val canAct = !selection.deleting && pending == null
    val colors = CouiTheme.colorScheme
    val editorProgress = LocalSettingsEditorProgress.current
    val material = LocalCouiMaterialEnabled.current
    fun cancel() {
        if (canAct) {
            error = null
            selection.cancel()
        }
    }

    if (selection.active) {
        DisposableEffect(editorProgress) {
            val previousProgress = editorProgress?.floatValue
            editorProgress?.floatValue = 1f
            onDispose { if (previousProgress != null) editorProgress?.floatValue = previousProgress }
        }
        BackHandler(onBack = ::cancel)
        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter)
                .then(if (material) Modifier else Modifier.background(colors.background)),
            title = {
                com.itos.coui.text.Text(
                    text = if (selected.isEmpty()) "选择课程" else "已选 ${selected.size} 项",
                    kind = CouiTextKind.Toolbar, maxLines = 1
                )
            },
            navigationIcon = { SelectionTextButton("取消", canAct, ::cancel) },
            actions = {
                SelectionTextButton(
                    if (cards.isNotEmpty() && selected.size == cards.size) "取消全选" else "全选",
                    canAct && cards.isNotEmpty()
                ) { selection.selectAll(cards.keys) }
            },
            colors = CouiTopAppBarDefaults.colors(backgroundColor = colors.background),
            backdrop = rememberCouiGlassBackdrop(backdrop)
        )
    }
    AnimatedVisibility(
        visible = selection.active,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            error?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            val enabled = selected.isNotEmpty() && canAct
            val ink = if (enabled) MaterialTheme.colorScheme.error else colors.onSurfaceVariant
            LiquidButton(
                onClick = { error = null; pending = selected },
                backdrop = backdrop,
                modifier = Modifier.width(152.dp),
                enabled = enabled,
                preserveMaterialWhenDisabled = true,
                nativePrimaryColor = Color.Unspecified,
                surfaceColor = colors.background.copy(alpha = 0.86f)
            ) {
                Icon(CouiIcons.Delete, null, Modifier.size(20.dp), tint = ink)
                Spacer(Modifier.width(6.dp))
                Text(if (selection.deleting) "正在删除" else "删除", color = ink)
            }
        }
    }
    pending?.let { originals ->
        LiquidAlertDialog(
            title = confirmationTitle, message = confirmationMessage(originals.size),
            backdrop = backdrop, config = config, onDismissRequest = { pending = null },
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel) { pending = null },
                LiquidAlertAction("删除", LiquidAlertActionStyle.Destructive) {
                    pending = null
                    selection.deleting = true
                    scope.launch {
                        try {
                            onDelete(originals)
                            selection.deleting = false
                            selection.cancel()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "删除失败，请重试"
                        } finally {
                            selection.deleting = false
                        }
                    }
                }
            )
        )
    }
}

@Composable
private fun SelectionTextButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val accent = CouiTheme.colorScheme.accentOnSurface
    Box(
        Modifier.heightIn(min = 48.dp).clip(Capsule())
            .couiButtonFeedback(CouiTheme.isDark, interaction, enabled)
            .clickable(interaction, null, enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        com.itos.coui.text.Text(text = label, kind = CouiTextKind.Toolbar,
            color = accent.copy(alpha = if (enabled) 1f else 0.4f), maxLines = 1)
    }
}
