package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiShape
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.controls.CouiFormTabs
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageStyle
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarTextButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val FeedbackAreas = listOf("未选择", "课表与课程", "导入课表", "通知与小组件", "界面与动画", "AI 助手", "其他")
private val FeedbackKindLabels = FeedbackKind.entries.map { it.label }

@Composable
internal fun FeedbackEditor(
    config: ScheduleConfigEntity,
    client: FeedbackService,
    onClose: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val dark = appUsesDarkTheme(config)
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(FeedbackKind.Bug) }
    var area by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var draftId by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val descriptionText = description.trim()
    val titleText = title.trim()
    val contactText = contact.trim()
    val areaPrefix = area?.let { "【$it】\n" }.orEmpty()
    val titleLength = titleText.codePointCount(0, titleText.length)
    val descriptionLength = descriptionText.codePointCount(0, descriptionText.length)
    val maxDescriptionLength = 2000 - areaPrefix.length
    val enabled = !sending && !finished
    val canSubmit = enabled && titleLength <= 80 && descriptionLength in 5..maxDescriptionLength

    fun draftChanged() {
        draftId = ""
        error = ""
    }

    fun close() {
        if (!enabled) return
        finished = true
        onClose(false)
    }

    fun submit() {
        if (!canSubmit) return
        val id = draftId.ifBlank { UUID.randomUUID().toString().also { draftId = it } }
        // Keep the optional category out of a title derived from the description.
        val submittedTitle = if (titleText.isEmpty() && area != null) {
            val firstLine = descriptionText.lineSequence().first().trim()
            val count = firstLine.codePointCount(0, firstLine.length).coerceAtMost(40)
            firstLine.substring(0, firstLine.offsetByCodePoints(0, count))
        } else titleText
        sending = true
        error = ""
        scope.launch {
            try {
                client.submit(id, kind, submittedTitle, areaPrefix + descriptionText, contactText)
                finished = true
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

    BackHandler(onBack = ::close)
    DetailActivityScaffold(
        title = "新增反馈",
        config = config,
        centerTopBarTitle = true,
        onBack = ::close,
        topBarNavigation = { CouiSettingsToolbarTextButton("取消", enabled, ::close) },
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
                item("feedback-title") {
                    Column {
                        FeedbackField(title, {
                            if (it.length <= 160) { title = it; draftChanged() }
                        }, "反馈标题", "标题（选填）", enabled, dark, ImeAction.Next)
                        if (titleLength > 80) Text(
                            "标题最多 80 字", modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item("feedback-description") {
                    Column {
                        FeedbackField(description, {
                            if (it.length <= 4000) { description = it; draftChanged() }
                        }, "反馈描述",
                            if (kind == FeedbackKind.Bug) "描述遇到的问题和复现步骤" else "说说你希望怎样改进",
                            enabled, dark, singleLine = false,
                        )
                        Text(
                            if (descriptionLength > maxDescriptionLength) "描述最多 $maxDescriptionLength 字"
                            else "$descriptionLength / $maxDescriptionLength 字 · 至少填写 5 字",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (descriptionLength > maxDescriptionLength) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                item("feedback-contact") {
                    FeedbackField(contact, {
                        if (it.codePointCount(0, it.length) <= 160) { contact = it; draftChanged() }
                    }, "联系方式", "联系方式（选填）", enabled, dark, ImeAction.Done)
                }
                item("feedback-message") {
                    Text(
                        error.ifBlank { "原始反馈和联系方式仅你和管理员可见。" },
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
    label: String,
    placeholder: String,
    enabled: Boolean,
    dark: Boolean,
    imeAction: ImeAction = ImeAction.Default,
    singleLine: Boolean = true,
) {
    val foreground = if (dark) Color.White else Color(0xFF161616)
    val style = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, singleLine = singleLine,
        minLines = if (singleLine) 1 else 4, maxLines = if (singleLine) 1 else 8,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        textStyle = style.copy(color = foreground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 48.dp else 128.dp)
            .clip(CouiShape(24.dp))
            .background(if (dark) Color(0xFF1C1C1E) else Color.White)
            .semantics { contentDescription = label },
        decorationBox = { input ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp)) {
                if (value.isEmpty()) Text(placeholder, color = foreground.copy(alpha = 0.48f), style = style)
                input()
            }
        },
    )
}
