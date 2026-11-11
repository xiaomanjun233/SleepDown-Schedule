package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiTheme
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SwipeDeleteCorners
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SwipeDeleteRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceCategory
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.model.ScheduleAdjustment
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import java.time.LocalDate

private val AdjustmentRowRadius = 24.dp
private val AdjustmentSeparation = 7.dp

@Stable
internal class AdjustmentListSwipeState {
    var revealedDate by mutableStateOf<String?>(null)
        private set
    private val slides = mutableStateMapOf<String, Float>()

    fun reveal(date: String, open: Boolean) {
        if (open) revealedDate = date
        else if (revealedDate == date) revealedDate = null
    }

    fun update(date: String, progress: Float) {
        if (progress == 0f) slides.remove(date)
        else slides[date] = progress
    }

    fun progress(date: String?): Float = date?.let { slides[it] } ?: 0f

    fun offset(index: Int, positions: Map<String, Int>): Float {
        var displacement = 0f
        slides.forEach { (date, progress) ->
            val target = positions[date] ?: return@forEach
            displacement += (index.compareTo(target)) * progress
        }
        return displacement.coerceIn(-1f, 1f)
    }
}

internal fun LazyListScope.scheduleAdjustmentItems(
    entries: List<ScheduleAdjustment>,
    config: ScheduleConfigEntity,
    swipes: AdjustmentListSwipeState,
    enabled: Boolean,
    departingDate: String?,
    onEdit: (ScheduleAdjustment) -> Unit,
    confirmDelete: suspend (ScheduleAdjustment) -> Boolean,
    onDelete: (ScheduleAdjustment) -> Unit,
    onRowDisposed: (String) -> Unit,
    onDeleteFailed: (Exception) -> Unit,
) {
    item("adjustment-list-heading") {
        GlassPreferenceCategory(if (entries.isEmpty()) "调休安排" else "调休安排 · ${entries.size} 天")
    }
    if (entries.isEmpty()) {
        item("adjustment-empty") {
            SettingsGroup(backdrop = null, config = config) { SettingsInfoRow("暂无调休安排", "") }
        }
        return
    }

    val positions = entries.mapIndexed { index, entry -> entry.date to index }.toMap()
    itemsIndexed(entries, key = { _, entry -> entry.date }) { index, entry ->
        val previousDate = entries.getOrNull(index - 1)?.date
        val nextDate = entries.getOrNull(index + 1)?.date
        val color = CouiTheme.colorScheme.container
        DisposableEffect(entry.date) {
            onDispose {
                swipes.reveal(entry.date, false)
                swipes.update(entry.date, 0f)
                onRowDisposed(entry.date)
            }
        }
        Column(Modifier.fillMaxWidth().graphicsLayer {
            // Move only the two sections. The dragged row keeps its vertical touch target.
            translationY = AdjustmentSeparation.toPx() * swipes.offset(index, positions)
        }) {
            SwipeDeleteRow(
                rowKey = entry.date,
                surfaceColor = color,
                revealed = swipes.revealedDate == entry.date,
                onRevealChange = { swipes.reveal(entry.date, it) },
                swipeEnabled = enabled || entry.date == departingDate,
                slideContent = false,
                confirmDelete = { confirmDelete(entry) },
                onDelete = { onDelete(entry) },
                onDeleteFailed = onDeleteFailed,
                onSlideProgress = { swipes.update(entry.date, it) },
                rowCorners = { separation ->
                    SwipeDeleteCorners(
                        top = AdjustmentRowRadius * (if (previousDate == null) 1f
                            else maxOf(separation, swipes.progress(previousDate))),
                        bottom = AdjustmentRowRadius * (if (nextDate == null) 1f
                            else maxOf(separation, swipes.progress(nextDate))),
                    )
                },
            ) { slide, blocked, openOrClose ->
                Box(slide) {
                    val source = entry.sourceDate?.let(LocalDate::parse)
                    val subtitle = listOfNotNull(
                        source?.let { "补 ${adjustmentDateLabel(it)} 的课" },
                        entry.label.takeIf { it.isNotBlank() },
                        "全天占位".takeIf { entry.allDayPlaceholder },
                    ).joinToString(" · ")
                    SettingsNavigationRow(
                        title = "${adjustmentDateLabel(LocalDate.parse(entry.date))} · ${if (source == null) "停课" else "补课"}",
                        subtitle = subtitle.ifBlank { null },
                        minHeight = 68.dp,
                        enabled = enabled && !blocked,
                        onClick = { openOrClose { onEdit(entry) } },
                    )
                }
            }
            if (nextDate != null) {
                Box(Modifier.graphicsLayer {
                    alpha = 1f - maxOf(swipes.progress(entry.date), swipes.progress(nextDate))
                }.background(color)) {
                    SettingsDivider()
                }
            }
        }
    }
    item("adjustment-list-hint") {
        GlassPreferenceCategory("点按修改，侧滑删除", Modifier.padding(top = AdjustmentSeparation))
    }
}
