package com.xiaomanjun.sleepdownschedule.feature.feedback

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.itos.coui.chip.ChipTabButton
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.icons.CouiIcons
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.AddMenuAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.GlowOverflowMenuButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardDeleteBottomSpace
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardDeletionSelection
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardSelectionCheck
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardSelectionChrome
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownFloatingAddButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.cardSelectionInteraction
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.findActivity
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsArrowPreference
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsBasicComponent
import com.kyant.shapes.Capsule
import androidx.compose.ui.draw.clip
import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.LiveTransitionButton
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionLaunchResult
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteCatalog
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.liveTransitionBoundsInActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val FeedbackEditorDarkExtra = "feedback_editor_dark"
internal const val FeedbackEditorAccentExtra = "feedback_editor_accent"

private enum class FeedbackView(
    val label: String,
    val own: Boolean = false,
    val kind: FeedbackKind? = null,
    val status: FeedbackStatus? = null,
) {
    All("全部"),
    Feature("功能建议", kind = FeedbackKind.Feature),
    Bug("问题反馈", kind = FeedbackKind.Bug),
    Mine("我的反馈", own = true),
    InProgress("开发中", status = FeedbackStatus.InProgress),
    PendingRelease("待发布", status = FeedbackStatus.PendingRelease),
    Released("已完成", status = FeedbackStatus.Released),
    Rejected("拒绝", status = FeedbackStatus.Rejected),
}

private const val HiddenFeedbackCategories = "hidden_categories"
private const val FeedbackCategoriesDescription = "选择展示的分类"
private val FeedbackFabSize = 56.dp

private sealed interface FeedbackList {
    data object Loading : FeedbackList
    data class Loaded(val page: FeedbackPage) : FeedbackList
    data class Failed(val message: String) : FeedbackList
}

@Composable
internal fun FeedbackScreen(
    config: ScheduleConfigEntity,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) { FeedbackClient(context) }
    FeedbackScreen(config, client, onBack)
}

@Composable
internal fun FeedbackScreen(
    config: ScheduleConfigEntity,
    client: FeedbackService,
    onBack: (() -> Unit)? = null,
) {
    var view by rememberSaveable { mutableStateOf(FeedbackView.All) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val selection = remember { CardDeletionSelection("选择反馈") }
    var launching by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("feedback_display", Context.MODE_PRIVATE) }
    var hiddenCategories by remember(preferences) {
        mutableStateOf(preferences.getStringSet(HiddenFeedbackCategories, emptySet()).orEmpty().toSet())
    }
    val visibleCategories = FeedbackView.entries.filter { it == FeedbackView.All || it.name !in hiddenCategories }
    val visibleTabs = remember(visibleCategories) { visibleCategories.map { CouiChipTabItem(it, it.label) } }
    val activeView = view.takeIf { it in visibleCategories } ?: FeedbackView.All
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val search = rememberCouiSearchBarState(searching)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dark = appUsesDarkTheme(config)
    val bodyBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "feedback-list")

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

    fun toggleCategory(category: FeedbackView) {
        val key = category.name
        hiddenCategories = if (key in hiddenCategories) hiddenCategories - key else hiddenCategories + key
        preferences.edit { putStringSet(HiddenFeedbackCategories, hiddenCategories) }
        if (view == category && key in hiddenCategories) select(FeedbackView.All)
    }

    val editor = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        launching = false
        if (result.resultCode == Activity.RESULT_OK) {
            changeSearch(false)
            if (FeedbackView.Mine in visibleCategories) view = FeedbackView.Mine else view = FeedbackView.All
            page = 0
            refresh++
        }
    }
    BackHandler(enabled = searching && !selection.active) { changeSearch(false) }
    val term = query.trim()
    val list by key(activeView, page, term, refresh) {
        produceState<FeedbackList>(FeedbackList.Loading, client) {
            if (term.isNotEmpty()) delay(280)
            value = try {
                FeedbackList.Loaded(client.list(activeView.own, page, term, activeView.kind, activeView.status))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                FeedbackList.Failed(feedbackError(error))
            }
        }
    }
    fun refreshAfterDeletion(count: Int) {
        val remaining = (list as? FeedbackList.Loaded)?.page?.total?.minus(count)
        if (page > 0 && remaining != null && remaining <= page * FeedbackClient.PageSize) page--
        refresh++
    }
    val details = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        launching = false
        if (result.resultCode == Activity.RESULT_OK) refreshAfterDeletion(1)
    }
    fun openDetails(item: FeedbackItem, accent: Color) {
        val owner = activity ?: return
        if (launching) return
        launching = true
        focus.clearFocus()
        keyboard?.hide()
        val route = TransitionRouteCatalog.get(TransitionRouteId.FeedbackToDetail)
        val destination = Intent().setClassName(owner.packageName, route.destinationClassName)
            .putFeedbackDetailItem(item)
            .putExtra(FeedbackEditorDarkExtra, dark)
            .putExtra(FeedbackEditorAccentExtra, accent.toArgb())
        val result = ActivityTransitionCoordinator.openImmediate(owner, route.id, destination) { details.launch(it) }
        if (result is TransitionLaunchResult.Failed) {
            launching = false
            Toast.makeText(context, "无法打开反馈详情", Toast.LENGTH_SHORT).show()
        }
    }

    val listState = key(activeView, page, term) { rememberLazyListState() }
    val loadedItems = (list as? FeedbackList.Loaded)?.page?.items.orEmpty()
    val ownedItems = remember(loadedItems, activeView.own) {
        loadedItems.filter { activeView.own || it.owned }.associateBy(FeedbackItem::id)
    }
    CouiSettingsPageScaffold(
        title = "问题反馈",
        config = config,
        showBack = onBack != null,
        onBack = { if (searching) changeSearch(false) else onBack?.invoke() },
        topBarExtra = extra@ { backdrop ->
            if (selection.active) return@extra
            val accent = MaterialTheme.colorScheme.primary
            val categoryMenu: @Composable () -> Unit = {
                val actions = FeedbackView.entries.filter { it != FeedbackView.All }.map { category ->
                    AddMenuAction(label = category.label, checked = category in visibleCategories) {
                        toggleCategory(category)
                    }
                }
                GlowOverflowMenuButton(dark, interactive = true, actions, Modifier, keepOpenOnSelection = true) { open ->
                    if (Build.VERSION.SDK_INT >= 34) {
                        ChipTabButton(
                            onClick = open, contentDescription = FeedbackCategoriesDescription,
                            backdrop = backdrop, dark = dark,
                        ) {
                            Icon(CouiIcons.Components, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = open) {
                            Icon(CouiIcons.Components, FeedbackCategoriesDescription)
                        }
                    }
                }
            }
            Column {
                SearchBar(
                    state = search, query = query,
                    onQueryChange = { value -> if (value.codePointCount(0, value.length) <= 100) setQuery(value) },
                    onExpandedChange = ::changeSearch,
                    placeholder = "搜索反馈", cancelText = "取消", clearDescription = "清空搜索",
                    dark = dark, backdrop = backdrop,
                    onSearch = { focus.clearFocus() },
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    ChipTabRow(
                        modifier = Modifier.fillMaxWidth(),
                        items = visibleTabs,
                        selectedKey = activeView,
                        onSelectedKeyChange = { select(it as FeedbackView) },
                        backdrop = backdrop,
                        dark = dark,
                        leadingAction = categoryMenu,
                        selectedContentColor = accent,
                        selectedTint = accent.copy(alpha = if (dark) 0.22f else 0.13f),
                    )
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        categoryMenu()
                        val colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.14f), selectedLabelColor = accent,
                        )
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp).selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            visibleCategories.forEach { tab ->
                                FilterChip(activeView == tab, { select(tab) }, { Text(tab.label) }, colors = colors)
                            }
                        }
                    }
                }
            }
        },
    ) { padding, backdrop ->
        val accent = MaterialTheme.colorScheme.primary
        val pageColor = settingsPageBackground(config)
        Box(Modifier.fillMaxSize()) {
            SleepDownSecondaryPageList(
                contentTopPadding = padding.calculateTopPadding() + 8.dp,
                contentBottomPadding = padding.calculateBottomPadding() +
                    if (selection.active) CardDeleteBottomSpace else if (searching) 12.dp else 88.dp,
                modifier = Modifier.fillMaxSize().glassBackdropProducer(bodyBackdrop).background(pageColor),
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
                                activeView == FeedbackView.All -> "暂无公开反馈"
                                else -> "此分类暂无反馈"
                            }
                            FeedbackListMessage(message, if (page > 0) "返回第一页" else "刷新") {
                                if (page > 0) page = 0 else refresh++
                            }
                        }
                        items(result.page.items, key = { it.id }) { item ->
                            val ownsItem = item.id in ownedItems
                            FeedbackRow(item, config, backdrop, dark, selection, ownsItem, enabled = !launching,
                                onSelect = {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                    selection.start(item.id)
                                },
                            ) {
                                openDetails(item.copy(owned = ownsItem), accent)
                            }
                        }
                        if (!selection.active && (result.page.total > FeedbackClient.PageSize || page > 0)) item(key = "feedback-pagination") {
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

            if (!searching && !selection.active) {
                Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp)) {
                    LiveTransitionButton(cornerRadius = FeedbackFabSize / 2) { source ->
                        FeedbackAddButton(bodyBackdrop, dark, accent) {
                            if (activity == null || launching) return@FeedbackAddButton
                            launching = true
                            scope.launch {
                                var started = false
                                try {
                                    val route = TransitionRouteCatalog.get(TransitionRouteId.FeedbackToEditor)
                                    val intent = Intent().setClassName(activity.packageName, route.destinationClassName)
                                        .putExtra(FeedbackEditorDarkExtra, dark)
                                        .putExtra(FeedbackEditorAccentExtra, accent.toArgb())
                                    val anchor = source?.liveTransitionBoundsInActivity(activity)?.let { bounds ->
                                        TransitionAnchorFrame(bounds, minOf(bounds.width, bounds.height) / 2f, bitmap = null)
                                    }
                                    fun returnAnchor(frame: TransitionAnchorFrame?) = TransitionAnchorProvider {
                                        source?.liveTransitionBoundsInActivity(activity, coveredWindow = true)?.let {
                                            frame?.copy(boundsInWindow = it)
                                        }
                                    }
                                    val result = ActivityTransitionCoordinator.open(
                                        activity, route.id, intent,
                                        TransitionPayload(
                                            openingAnchor = anchor,
                                            returnAnchorProvider = returnAnchor(anchor),
                                            backgroundColor = pageColor.toArgb(),
                                            openingSourceView = source,
                                            floatingAddSource = true,
                                        ),
                                        launchActivity = { destination, options ->
                                            if (options != null) destination.putExtra(
                                                ActivityResultContracts.StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE, options,
                                            )
                                            editor.launch(destination)
                                        },
                                    )
                                    started = result !is TransitionLaunchResult.Failed
                                    if (!started) Toast.makeText(context, "无法打开新增反馈", Toast.LENGTH_SHORT).show()
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开新增反馈", Toast.LENGTH_SHORT).show()
                                } finally {
                                    if (!started) launching = false
                                }
                            }
                        }
                    }
                }
            }
            CardSelectionChrome(
                selection = selection,
                cards = ownedItems,
                config = config,
                backdrop = bodyBackdrop,
                confirmationTitle = "删除反馈？",
                confirmationMessage = { count -> "将删除这 $count 条反馈。已公开的条目也会从列表移除。" },
                onDelete = { items ->
                    var deleted = 0
                    try {
                        for (item in items) {
                            client.delete(item.id)
                            deleted++
                        }
                        Toast.makeText(context, "反馈已删除", Toast.LENGTH_SHORT).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        val message = feedbackError(error)
                        throw FeedbackRequestException(if (deleted == 0) message else "已删除 $deleted 条，$message")
                    } finally {
                        if (deleted > 0) refreshAfterDeletion(deleted)
                    }
                },
            )
        }
    }
}

@Composable
private fun FeedbackRow(
    item: FeedbackItem,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    dark: Boolean,
    selection: CardDeletionSelection,
    owned: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
) {
    val statusColor = feedbackStatusColor(item, dark)
    val firstLine = item.description.lineSequence().firstOrNull().orEmpty()
    val preview = if (firstLine.length > 72) firstLine.take(71) + "…" else firstLine
    val summary = listOf(item.kindLabel, item.releaseVersion, preview).filter(String::isNotBlank).joinToString(" · ")
    SettingsGroup(backdrop, config) {
        Column(Modifier.fillMaxWidth()
            .cardSelectionInteraction(
                selection, item.id, enabled, dark, onLongClick = onSelect, selectable = owned,
                listFeedback = true, onClick = onClick,
            )) {
            if (selection.active) {
                SettingsBasicComponent(title = item.title, summary = summary, endActions = {
                    FeedbackStatusBadge(item, statusColor)
                    if (owned) {
                        CardSelectionCheck(item.id in selection.selectedKeys, enabled && !selection.deleting, dark)
                    }
                })
            } else {
                SettingsArrowPreference(title = item.title, summary = summary,
                    endActions = { FeedbackStatusBadge(item, statusColor) })
            }
            FeedbackProgress(item.stage, statusColor, dark, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp))
        }
    }
}

@Composable
internal fun feedbackStatusColor(item: FeedbackItem, dark: Boolean): Color =
    when (item.stage) {
        FeedbackStatus.Released -> if (dark) Color(0xFF87DDB0) else Color(0xFF16714B)
        FeedbackStatus.Rejected, FeedbackStatus.Duplicate, FeedbackStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
internal fun FeedbackStatusBadge(item: FeedbackItem, color: Color) {
    Text(item.statusLabel, color = color, style = MaterialTheme.typography.labelMedium,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(Capsule()).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp))
}

@Composable
private fun FeedbackAddButton(backdrop: Backdrop, dark: Boolean, accent: Color, onClick: () -> Unit) {
    if (Build.VERSION.SDK_INT >= 34 && LocalVisualEffects.current.usesCouiControls) {
        Fab(
            backdrop = rememberCouiGlassBackdrop(backdrop),
            onClick = onClick, dark = dark,
            material = CouiFabDefaults.translucentMaterial(accent, dark),
        ) {
            Icon(Icons.Rounded.Add, "新增反馈", Modifier.size(24.dp), tint = Color.White)
        }
    } else {
        SleepDownFloatingAddButton(backdrop, "新增反馈", onClick, accent.copy(alpha = 0.76f),
            blurRadius = 8.dp, buttonSize = FeedbackFabSize)
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
