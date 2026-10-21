package com.xiaomanjun.sleepdownschedule.feature.importing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout

/** Full page in the root navigation surface, without dialog size constraints. */
@Composable
fun ImportPreviewPage(draft: ImportDraft, config: ScheduleConfigEntity, visible: Boolean,
    submitting: Boolean, errorMessage: String?, onBack: () -> Unit,
    onDraftChanged: (ImportDraft) -> Unit, onConfirm: (Boolean) -> Unit,
    onContentMountedChanged: (Boolean) -> Unit = {}) {
    val shown = remember { mutableStateOf(visible) }
    val latestMountedChanged by rememberUpdatedState(onContentMountedChanged)
    SideEffect { shown.value = visible }
    PopupLayout(visible = shown, renderInRootScaffold = true, enableWindowDim = false,
        enableBackHandler = false,
        enterTransition = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 12 },
        exitTransition = fadeOut(tween(260)) + slideOutVertically(tween(300)) { it / 12 }
    ) {
        // PopupLayout retains this content in the root host until its exit animation ends.
        // Report that actual lifetime instead of guessing it from a delay in the caller.
        DisposableEffect(Unit) {
            latestMountedChanged(true)
            onDispose { latestMountedChanged(false) }
        }
        BackHandler(enabled = shown.value) { if (!submitting) onBack() }
        DetailActivityScaffold(title = "导入预览", config = config,
            onBack = { if (!submitting) onBack() }, compactTopBar = true,
            centerCompactTitle = true, compactTitleMatchesSettings = true) { backdrop ->
            Box(Modifier.fillMaxSize().padding(top = detailContentTopPadding()).navigationBarsPadding()) {
                ConfirmScheduleScreen(draft = draft, backdrop = backdrop,
                    submitting = submitting, errorMessage = errorMessage,
                    onCancel = { if (!submitting) onBack() },
                    onDraftChanged = onDraftChanged, onConfirm = onConfirm)
            }
        }
    }
}
