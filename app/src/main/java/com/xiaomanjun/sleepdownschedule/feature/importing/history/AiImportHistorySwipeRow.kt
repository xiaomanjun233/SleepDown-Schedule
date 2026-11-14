package com.xiaomanjun.sleepdownschedule.feature.importing.history

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardDeletionSelection
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SwipeDeleteRow
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.cardSelectionInteraction
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportHistoryEntry

@Composable
internal fun AiImportHistorySwipeRow(
    entry: AiImportHistoryEntry,
    textColor: Color,
    selection: CardDeletionSelection,
    onStartSelection: () -> Unit,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onDelete: suspend () -> Unit,
    onDeleteFailed: (String) -> Unit,
    onOpen: (Rect) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    SwipeDeleteRow(
        rowKey = entry.id,
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        revealed = revealed,
        onRevealChange = onRevealChange,
        onDelete = onDelete,
        onDeleteFailed = { onDeleteFailed(it.message ?: "无法删除导入记录，请重试") },
        swipeEnabled = !selection.active,
        onBoundsChanged = onBoundsChanged,
    ) { slide, deleting, openOrClose ->
        Box(Modifier.cardSelectionInteraction(selection, entry.id, !deleting, dark, onLongClick = onStartSelection) {
            openOrClose { bounds -> if (bounds.width > 0f) onOpen(bounds) }
        }) {
            AiImportHistoryRowContent(
                entry = entry,
                textColor = textColor,
                modifier = slide,
                checkedForDeletion = if (selection.active) entry.id in selection.selectedKeys else null,
                selectionEnabled = !selection.deleting && !deleting,
            )
        }
    }
}
