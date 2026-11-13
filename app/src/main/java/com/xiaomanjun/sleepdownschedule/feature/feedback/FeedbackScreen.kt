package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownFloatingAddButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageStyle
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private enum class FeedbackView(val label: String) { Roadmap("开发进度"), Mine("我的反馈") }

private val FeedbackTabs = FeedbackView.entries.map { CouiChipTabItem(it, it.label) }

private sealed interface FeedbackList {
    data object Loading : FeedbackList
    data class Loaded(val page: FeedbackPage) : FeedbackList
    data class Failed(val message: String) : FeedbackList
}

@Composable
internal fun FeedbackScreen(config: ScheduleConfigEntity, backdrop: Backdrop?) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) { FeedbackClient(context) }
    FeedbackScreen(config, backdrop, client)
}

@Composable
internal fun FeedbackScreen(config: ScheduleConfigEntity, backdrop: Backdrop?, client: FeedbackService) {
    var view by rememberSaveable { mutableStateOf(FeedbackView.Roadmap) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var openedItem by remember { mutableStateOf<FeedbackItem?>(null) }
    val search = rememberCouiSearchBarState(searching)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pageColor = settingsPageBackground(config)
    val dark = appUsesDarkTheme(config)
    val background = rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "feedback-background")
    val bodyBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "feedback-list") {
        drawRect(pageColor)
        drawContent()
    }
    val couiHeader = rememberCouiGlassBackdrop(backdrop ?: background)

    fun setQuery(value: String) {
        if (query == value) return
        query = value
        page = 0
    }

    fun changeSearch(expanded: Boolean) {
        searching = expanded
        if (!expanded) setQuery("")
    }

    fun select(next: FeedbackView) {
        if (view == next) return
        view = next
        page = 0
    }

    BackHandler(enabled = searching) { changeSearch(false) }
    val term = query.trim()
    val list by key(view, page, term, refresh) {
        produceState<FeedbackList>(FeedbackList.Loading, client) {
            if (term.isNotEmpty()) delay(280)
            value = try {
                FeedbackList.Loaded(client.list(own = view == FeedbackView.Mine, page = page, query = term))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                FeedbackList.Failed(feedbackError(error))
            }
        }
    }
    val topPadding = detailContentTopPadding()
    val bottomPadding = detailContentBottomPadding()
    FeedbackEditorHost(config, client, onClosed = { submitted ->
        if (submitted) {
            changeSearch(false)
            view = FeedbackView.Mine
            page = 0
            refresh++
        }
    }) { sourceModifier, openEditor ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropProducer(background).background(pageColor))
            Column(Modifier.fillMaxSize().padding(top = topPadding).imePadding()) {
                SearchBar(
                    state = search, query = query,
                    onQueryChange = { value -> if (value.codePointCount(0, value.length) <= 100) setQuery(value) },
                    onExpandedChange = ::changeSearch,
                    placeholder = "搜索反馈", cancelText = "取消", clearDescription = "清空搜索",
                    dark = dark, backdrop = couiHeader,
                    onSearch = { focus.clearFocus() },
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    ChipTabRow(
                        modifier = Modifier.fillMaxWidth(),
                        items = FeedbackTabs,
                        selectedKey = view,
                        onSelectedKeyChange = { select(it as FeedbackView) },
                        backdrop = couiHeader,
                        dark = dark,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FeedbackView.entries.forEach { tab ->
                            FilterChip(selected = view == tab, onClick = { select(tab) }, label = { Text(tab.label) })
                        }
                    }
                }
                val listState = key(view, page, term) { rememberLazyListState() }
                SleepDownSecondaryPageList(
                    contentTopPadding = 8.dp,
                    contentBottomPadding = bottomPadding + if (searching) 12.dp else 82.dp,
                    modifier = Modifier.weight(1f).glassBackdropProducer(bodyBackdrop),
                    state = listState,
                    style = SleepDownSecondaryPageStyle.Content,
                ) {
                    when (val result = list) {
                        FeedbackList.Loading -> item(key = "feedback-loading") {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(26.dp))
                            }
                        }
                        is FeedbackList.Failed -> item(key = "feedback-error") {
                            FeedbackListMessage(result.message, "重试") { refresh++ }
                        }
                        is FeedbackList.Loaded -> {
                            if (result.page.items.isEmpty()) item(key = "feedback-empty") {
                                val message = when {
                                    term.isNotEmpty() -> "未找到相关反馈"
                                    view == FeedbackView.Mine -> "暂无反馈"
                                    else -> "暂无公开开发进度"
                                }
                                FeedbackListMessage(message, if (page > 0) "返回第一页" else "刷新") {
                                    if (page > 0) page = 0 else refresh++
                                }
                            }
                            items(result.page.items, key = { it.id }) { item ->
                                FeedbackRow(item, config, backdrop, dark) {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                    openedItem = item
                                }
                            }
                            if (result.page.total > FeedbackClient.PageSize || page > 0) item(key = "feedback-pagination") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("上一页") }
                                    TextButton(onClick = { refresh++ }) { Text("刷新") }
                                    TextButton(
                                        onClick = { page++ },
                                        enabled = (page + 1) * FeedbackClient.PageSize < result.page.total,
                                    ) { Text("下一页") }
                                }
                            }
                        }
                    }
                }
            }

            if (!searching) Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp)) {
                FeedbackAddButton(bodyBackdrop, dark, sourceModifier, openEditor)
            }
        }
    }

    openedItem?.let { item ->
        val close = { openedItem = null }
        val detail = buildString {
            append(listOf(item.kindLabel, item.statusLabel, item.releaseVersion).filter(String::isNotBlank).joinToString(" · "))
            append("\n\n").append(item.description)
            if (item.note.isNotBlank()) append("\n\n开发者回复\n").append(item.note)
        }
        LiquidAlertDialog(
            title = item.title,
            message = detail,
            actions = listOf(LiquidAlertAction("关闭", LiquidAlertActionStyle.Primary, onClick = close)),
            config = config,
            onDismissRequest = close,
        )
    }
}

@Composable
private fun FeedbackRow(
    item: FeedbackItem,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val statusColor = when (item.stage) {
        FeedbackStatus.Released -> if (dark) Color(0xFF87DDB0) else Color(0xFF16714B)
        FeedbackStatus.Rejected, FeedbackStatus.Duplicate, FeedbackStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val firstLine = item.description.lineSequence().firstOrNull().orEmpty()
    val preview = if (firstLine.length > 72) firstLine.take(71) + "…" else firstLine
    SettingsGroup(backdrop, config) {
        SettingsNavigationRow(
            title = item.title,
            subtitle = listOf(item.kindLabel, item.releaseVersion, preview).filter(String::isNotBlank).joinToString(" · "),
            badgeText = item.statusLabel,
            badgeColor = statusColor,
            onClick = onClick,
        )
        FeedbackProgress(item.stage, statusColor, dark, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp))
    }
}

@Composable
private fun FeedbackAddButton(backdrop: Backdrop, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier) {
        if (Build.VERSION.SDK_INT >= 34 && LocalVisualEffects.current.usesCouiControls) {
            Fab(
                backdrop = rememberCouiGlassBackdrop(backdrop),
                onClick = onClick, dark = dark,
                material = CouiFabDefaults.translucentMaterial(primary, dark),
            ) {
                Icon(Icons.Rounded.Add, "新增反馈", Modifier.size(24.dp), tint = Color.White)
            }
        } else {
            SleepDownFloatingAddButton(backdrop, "新增反馈", onClick, primary.copy(alpha = 0.76f),
                blurRadius = 8.dp, buttonSize = 56.dp)
        }
    }
}

@Composable
private fun FeedbackListMessage(message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction) { Text(action) }
    }
}

internal fun feedbackError(error: Exception): String =
    (error as? FeedbackRequestException)?.message ?: "暂时无法连接反馈服务，请重试"
