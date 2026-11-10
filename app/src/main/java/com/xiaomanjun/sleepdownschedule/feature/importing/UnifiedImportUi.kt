package com.xiaomanjun.sleepdownschedule.feature.importing

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.segmentbutton.CouiSegmentedControlDefaults
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop

private val ImportTabLabels = listOf("常规导入", "教务系统导入")

@Composable
internal fun UnifiedImportTabs(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    dark: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    if (Build.VERSION.SDK_INT >= 34) {
        val material = if (backdrop != null && !AppMaterialPreferences.isSuperPerformance) {
            rememberCouiGlassBackdrop(backdrop)
        } else null
        SegmentedControl(
            modifier = modifier, labels = ImportTabLabels, selectedIndex = selectedIndex,
            onSelected = onSelected, dark = dark, enabled = enabled, backdrop = material,
        )
        return
    }
    val indicator by animateFloatAsState(selectedIndex.toFloat(), label = "import-tab")
    val select by rememberUpdatedState(onSelected)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(
        modifier.width(192.dp).height(CouiSegmentedControlDefaults.height)
            .clip(CouiCapsuleShape).background(CouiSegmentedControlDefaults.containerColor(dark))
            .selectableGroup()
            .pointerInput(enabled, rtl) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    select(if ((change.position.x < size.width / 2f) != rtl) 0 else 1)
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
    preview: @Composable () -> Unit
) {
    val dark = appUsesDarkTheme(config)
    val foreground = if (dark) Color.White else Color(0xFF161616)
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(CouiShape(24.dp))
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
        if (!hasPreview) {
            (message ?: "支持 SleepDown、WakeUp、星链口令，也可以上传课表文件。").let {
                Text(
                    if (resolving && message == null) "正在读取导入内容…" else it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasError) MaterialTheme.colorScheme.error else foreground.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { preview() }
    }
}
