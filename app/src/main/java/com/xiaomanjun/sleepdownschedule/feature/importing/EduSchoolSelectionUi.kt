package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialControls
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceCategory
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalDetailActivityFloatingOverlayHost
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroupPosition
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.glass.ui.appPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.transition.legacy.detailMotionBlurRadiusDp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun EduSchoolPickerScreen(
    state: AppState,
    backdrop: Backdrop? = null,
    warehouseGeneration: Int = 0,
    availableAdapters: List<EduAdapter>? = null,
    adapterBadge: (EduAdapter) -> String? = { null },
    listHeader: (@Composable () -> Unit)? = null,
    onSelect: (EduAdapter) -> Unit
) {
    val context = LocalContext.current
    val localScope = rememberCoroutineScope()
    var localVersion by remember { mutableIntStateOf(0) }
    var localSelection by remember { mutableStateOf<EduAdapter?>(null) }
    var editSource by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var confirmLocalDelete by remember { mutableStateOf(false) }
    fun select(adapter: EduAdapter) {
        if (adapter.isSelfUseAdapter()) { localSelection = adapter; localError = null }
        else onSelect(adapter)
    }
    val adapters = availableAdapters ?: remember(warehouseGeneration, localVersion) {
        runCatching { ShiguangWarehouse.loadVisibleAdapters(context) }
            .getOrDefault(emptyList())
    }
    var adapterChoices by remember { mutableStateOf<List<EduAdapter>?>(null) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, adapters) {
        val keyword = query.trim()
        val sorted = adapters.sortedWith(
            compareBy<EduAdapter> { it.school.initial.ifBlank { "#" } }
                .thenBy { it.school.name }
                .thenBy { it.adapterName }
        )
        if (keyword.isBlank()) sorted else sorted.filter {
            it.displayName.contains(keyword, ignoreCase = true) ||
                    it.school.id.contains(keyword, ignoreCase = true) ||
                    it.adapterId.contains(keyword, ignoreCase = true) ||
                    it.school.name.contains(keyword, ignoreCase = true) ||
                    it.adapterName.contains(keyword, ignoreCase = true) ||
                    it.description.contains(keyword, ignoreCase = true) ||
                    it.maintainer.contains(keyword, ignoreCase = true)
        }
    }
    EduSchoolIndexedSelectScreen(
        state = state,
        backdrop = backdrop,
        adapters = filtered,
        adapterBadge = adapterBadge,
        listHeader = listHeader,
        query = query,
        onQueryChange = { query = it },
        onSelect = { school ->
            val matches = adapters.filter { it.school.id == school.id }
            if (matches.size == 1) {
                select(matches.single())
            } else if (matches.isNotEmpty()) {
                adapterChoices = matches
            }
        }
    )
    adapterChoices?.let { choices ->
        var selectedAdapterId by remember(choices) { mutableStateOf<String?>(null) }
        val selectedAdapter = choices.firstOrNull { it.adapterId == selectedAdapterId }
        LiquidAlertDialog(
            title = choices.first().school.name,
            message = "",
            actions = listOf(
                LiquidAlertAction(
                    label = "取消",
                    style = LiquidAlertActionStyle.Cancel,
                    onClick = { adapterChoices = null }
                ),
                LiquidAlertAction(
                    label = "确认",
                    style = LiquidAlertActionStyle.Primary,
                    enabled = selectedAdapter != null,
                    onClick = {
                        selectedAdapter?.let { adapter ->
                            adapterChoices = null
                            select(adapter)
                        }
                    }
                )
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { adapterChoices = null },
            messageMaxHeight = SleepDownDesignTokens.CenteredDialog.SelectionContentMaxHeight,
            scrollableMessageContent = true,
            isSelector = true,
            messageContent = {
                Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    choices.forEach { adapter ->
                        EduAdapterChoiceCard(
                            adapter = adapter,
                            badgeText = adapterBadge(adapter),
                            selected = adapter.adapterId == selectedAdapterId,
                            config = state.config,
                            onClick = { selectedAdapterId = adapter.adapterId }
                        )
                    }
                }
            }
        )
    }
    localSelection?.let { selected ->
        LiquidAlertDialog(
            title = if (editSource != null) "编辑自用适配" else selected.school.name,
            message = localError ?: if (confirmLocalDelete) "删除此本地适配？不会影响已经导入的课程。"
                else if (editSource != null) "按拾光 Bridge 规范编辑，保存后打开教务页重新测试。"
                else "本地 · 自用适配",
            config = state.config, backdrop = backdrop,
            onDismissRequest = { localSelection = null; editSource = null; confirmLocalDelete = false },
            messageMaxHeight = 420.dp,
            scrollableMessageContent = true,
            messageContent = if (editSource != null) { {
                DialogCapsuleField(value = editSource.orEmpty(), onValueChange = { editSource = it; localError = null },
                    placeholder = "拾光适配脚本", config = state.config, minLines = 10,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp))
            } } else null,
            actions = if (confirmLocalDelete) listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = { confirmLocalDelete = false }),
                LiquidAlertAction("删除", LiquidAlertActionStyle.Primary, onClick = {
                    LocalEduAdapterStore.delete(context, selected); localVersion++; localSelection = null; confirmLocalDelete = false
                })) else if (editSource != null) listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = { editSource = null }),
                LiquidAlertAction("保存并测试", LiquidAlertActionStyle.Primary, dismissOnClick = false, onClick = {
                    localScope.launch {
                        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            LocalEduAdapterStore.save(context, selected.school.name, selected.importUrl, editSource.orEmpty(), selected.adapterId)
                        } }.onSuccess { updated -> localVersion++; localSelection = null; editSource = null; onSelect(updated) }
                            .onFailure { localError = it.message ?: "保存失败" }
                    }
                })) else listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, onClick = { localSelection = null }),
                LiquidAlertAction("编辑", LiquidAlertActionStyle.Secondary, dismissOnClick = false, onClick = {
                    localScope.launch { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        LocalEduAdapterStore.source(context, selected)
                    } }.onSuccess { editSource = it }.onFailure { localError = "脚本读取失败" } }
                }),
                LiquidAlertAction("删除", LiquidAlertActionStyle.Secondary, dismissOnClick = false, onClick = { confirmLocalDelete = true }),
                LiquidAlertAction("打开教务", LiquidAlertActionStyle.Primary, onClick = { localSelection = null; onSelect(selected) }))
        )
    }
}

@Composable
private fun EduAdapterChoiceCard(
    adapter: EduAdapter,
    badgeText: String?,
    selected: Boolean,
    config: ScheduleConfigEntity,
    onClick: () -> Unit
) {
    val details = buildList {
        add(eduAdapterCategoryLabel(adapter.category))
        adapter.maintainer.takeIf(String::isNotBlank)?.let { add(it) }
        adapter.description.takeIf(String::isNotBlank)?.let { add(it) }
    }.joinToString(" · ")
    LiquidDialogChoiceCard(
        title = adapter.adapterName,
        selected = selected,
        config = config,
        onClick = onClick,
        description = details,
        badgeText = badgeText
    )
}

private data class EduSchoolAdapterGroup(
    val school: EduSchool,
    val adapters: List<EduAdapter>
)

private data class EduSchoolSection(
    val key: String,
    val title: String,
    val schools: List<EduSchoolAdapterGroup>,
    val letter: String? = null,
    val local: Boolean = false
)

@Composable
fun EduSchoolIndexedSelectScreen(
    state: AppState,
    backdrop: Backdrop?,
    adapters: List<EduAdapter>,
    query: String,
    onQueryChange: (String) -> Unit,
    adapterBadge: (EduAdapter) -> String? = { null },
    listHeader: (@Composable () -> Unit)? = null,
    onSelect: (EduSchool) -> Unit
) {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val originalSoftInputMode = window?.attributes?.softInputMode
        if (window != null && originalSoftInputMode != null) {
            // The search dock owns IME displacement, just as in EduSchoolSelectActivityHost.
            // Settings and embedded hosts must not also resize or pan their content.
            window.setSoftInputMode(
                (originalSoftInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }
        onDispose {
            if (window != null && originalSoftInputMode != null) {
                window.setSoftInputMode(originalSoftInputMode)
            }
        }
    }
    val listState = rememberLazyListState()
    val topPadding = detailContentTopPadding()
    val schoolGroups = remember(adapters) {
        adapters
            .groupBy { it.school.id }
            .values
            .map { schoolAdapters ->
                EduSchoolAdapterGroup(
                    school = schoolAdapters.first().school,
                    adapters = schoolAdapters.sortedBy(EduAdapter::adapterName)
                )
            }
    }
    val aiEduSchools = remember(schoolGroups) {
        schoolGroups.filter { group -> group.adapters.any(EduAdapter::isAiEduImportTool) }
    }
    val pinnedSchools = remember(schoolGroups) {
        schoolGroups.filter { group ->
            group !in aiEduSchools && group.adapters.any {
                it.isGeneralEduTool()
            }
        }
            .sortedBy { it.school.id }
    }
    val localSchools = remember(schoolGroups) {
        schoolGroups.filter { group -> group.adapters.any(EduAdapter::isSelfUseAdapter) }
    }
    val indexedSchools = remember(schoolGroups) {
        schoolGroups.filterNot { it in aiEduSchools || it in pinnedSchools || it in localSchools }
    }
    val grouped = remember(indexedSchools) {
        indexedSchools
            .groupBy { it.school.initial.ifBlank { "#" }.uppercase() }
            .toSortedMap()
    }
    val sections = remember(grouped, aiEduSchools, pinnedSchools, localSchools) {
        buildList {
            if (aiEduSchools.isNotEmpty()) add(EduSchoolSection("ai-edu", "AI教务导入", aiEduSchools))
            if (pinnedSchools.isNotEmpty()) add(EduSchoolSection("general-edu", "导入工具", pinnedSchools))
            if (localSchools.isNotEmpty()) add(EduSchoolSection("local-edu", "我的适配 · 本地", localSchools, local = true))
            grouped.forEach { (letter, schools) -> add(EduSchoolSection(letter, letter, schools, letter)) }
        }
    }
    val hasListHeader = listHeader != null
    val sectionPositions = remember(sections, hasListHeader) {
        buildMap {
            var index = if (hasListHeader) 1 else 0
            sections.forEach { section ->
                section.letter?.let { put(it, index) }
                index += 1 + section.schools.size
            }
        }
    }
    val listBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "edu-school-list-content"
    )
    // Floating controls are siblings after the producer. Combining the activity underlay with the
    // list producer lets them sample the title, category labels and cards without sampling either
    // themselves or the keyboard-driven overlay host.
    val overlayBackdrop = if (backdrop != null) {
        rememberGlassCombinedBackdrop(backdrop, listBackdrop)
    } else {
        listBackdrop
    }
    val density = LocalDensity.current
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val listDockClearance = navigationBottom + EduSearchDockBottomPadding + EduSearchDockHeight + 4.dp
    val imeLift = with(density) {
        (WindowInsets.ime.getBottom(density) - WindowInsets.navigationBars.getBottom(density))
            .coerceAtLeast(0)
            .toDp()
    }
    val floatingOverlayHost = LocalDetailActivityFloatingOverlayHost.current
    val currentSearchQuery = rememberUpdatedState(query)
    val currentSearchOnChange = rememberUpdatedState(onQueryChange)
    val currentSearchBackdrop = rememberUpdatedState(overlayBackdrop)
    val currentSearchConfig = rememberUpdatedState(state.config)
    val currentSearchImeLift = rememberUpdatedState(imeLift)
    val currentRailListState = rememberUpdatedState(listState)
    val currentRailPositions = rememberUpdatedState(sectionPositions)
    val currentRailTopPadding = rememberUpdatedState(topPadding)
    val floatingSearchDock: (@Composable () -> Unit)? = if (floatingOverlayHost != null) {
        remember(floatingOverlayHost) {
            @Composable {
                EduSchoolDockViewport { bottomInset ->
                    val controlsModifier = floatingOverlayHost.controlsModifier()
                    EduSchoolSearchDock(
                        value = currentSearchQuery.value,
                        onValueChange = currentSearchOnChange.value,
                        backdrop = currentSearchBackdrop.value,
                        config = currentSearchConfig.value,
                        imeLift = currentSearchImeLift.value,
                        bottomInset = bottomInset,
                        controlsModifier = controlsModifier
                    )
                    EduSchoolFastScroller(
                        listState = currentRailListState.value,
                        sectionPositions = currentRailPositions.value,
                        dark = appUsesDarkTheme(currentSearchConfig.value),
                        topPadding = currentRailTopPadding.value,
                        bottomPadding = bottomInset + EduSearchDockBottomPadding + EduSearchDockHeight,
                        modifier = controlsModifier,
                    )
                }
            }
        }
    } else {
        null
    }
    DisposableEffect(floatingOverlayHost, floatingSearchDock) {
        val host = floatingOverlayHost
        if (host != null && floatingSearchDock != null) {
            host.content = floatingSearchDock
        }
        onDispose {
            if (host != null && host.content === floatingSearchDock) {
                host.content = null
            }
        }
    }
    val rootOverlayMounted = floatingOverlayHost != null &&
        floatingOverlayHost.content === floatingSearchDock
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentImeLift = rememberUpdatedState(imeLift)
    val keyboardDismissRequested = remember { mutableStateOf(false) }
    LaunchedEffect(imeLift) {
        if (imeLift <= 1.dp) {
            keyboardDismissRequested.value = false
        }
    }
    val keyboardDismissScrollConnection = remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            private fun dismissKeyboard() {
                if (!keyboardDismissRequested.value) {
                    keyboardDismissRequested.value = true
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    currentImeLift.value > 1.dp &&
                    source == NestedScrollSource.UserInput &&
                    available.y != 0f
                ) {
                    // The first drag belongs to dismissing the IME. Consume it so the list does
                    // not visibly scroll underneath the keyboard during the dismissal frame.
                    dismissKeyboard()
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentImeLift.value > 1.dp) {
                    dismissKeyboard()
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    // The OS IME inset is the single source of truth for the search dock's vertical motion. The
    // field must rise with the keyboard itself; the split action below derives from that same
    // inset instead of starting a second focus-driven opening animation.
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropProducer(listBackdrop)
        ) {
            SleepDownSecondaryPageList(
                state = listState,
                modifier = Modifier.fillMaxSize().clipToBounds().nestedScroll(keyboardDismissScrollConnection),
                contentTopPadding = topPadding,
                contentBottomPadding = listDockClearance + CouiPageDefaults.categoryTopSpacing,
            ) {
                if (listHeader != null) {
                    item(key = "edu-school-header") { listHeader() }
                }
                if (adapters.isEmpty()) {
                    item { Text("没有找到学校适配资源", color = MaterialTheme.colorScheme.error) }
                } else {
                    sections.forEach { section ->
                        item(key = "school-heading-${section.key}") {
                            GlassPreferenceCategory(section.title)
                        }
                        // Each row has its own bounded hit target even in a tall letter section.
                        itemsIndexed(section.schools, key = { _, group -> "school-${section.key}-${group.school.id}" }) { index, group ->
                            val position = when {
                                section.schools.size == 1 -> SettingsGroupPosition.Whole
                                index == 0 -> SettingsGroupPosition.First
                                index == section.schools.lastIndex -> SettingsGroupPosition.Last
                                else -> SettingsGroupPosition.Middle
                            }
                            SettingsGroup(backdrop, state.config, topSpacing = 0.dp, position = position) {
                                SettingsNavigationRow(
                                    title = group.school.name,
                                    subtitle = group.adapters.joinToString(" / ") { it.adapterName },
                                    badgeText = if (section.local) "本地" else group.adapters.firstNotNullOfOrNull(adapterBadge),
                                    onClick = { onSelect(group.school) }
                                )
                                if (index < section.schools.lastIndex) SettingsDivider()
                            }
                        }
                    }
                }
            }
        }
        // In the detail scaffold this dock is mounted by the root-level sibling host below the
        // Miuix Scaffold. The fallback keeps previews/other callers usable before that host exists.
        if (!rootOverlayMounted) {
            EduSchoolDockViewport { bottomInset ->
                EduSchoolSearchDock(
                    value = query,
                    onValueChange = onQueryChange,
                    backdrop = overlayBackdrop,
                    config = state.config,
                    imeLift = imeLift,
                    bottomInset = bottomInset
                )
                EduSchoolFastScroller(
                    listState = listState,
                    sectionPositions = sectionPositions,
                    dark = appUsesDarkTheme(state.config),
                    topPadding = topPadding,
                    bottomPadding = bottomInset + EduSearchDockBottomPadding + EduSearchDockHeight,
                )
            }
        }
    }
}

@Composable
private fun EduSchoolDockViewport(content: @Composable (Dp) -> Unit) {
    val insets = com.xiaomanjun.sleepdownschedule.core.ui.layout.rememberFloatingInputInsets()
    Box(Modifier.fillMaxSize().then(insets.viewportModifier)) {
        content(insets.bottom)
    }
}


private fun eduAdapterCategoryLabel(category: String): String = when (category.uppercase()) {
    "BACHELOR_AND_ASSOCIATE" -> "本科 / 专科教务"
    "POSTGRADUATE" -> "研究生教务"
    "GENERAL_TOOL" -> "通用工具"
    else -> category.ifBlank { "未注明" }
}

private val EduSearchVerticalOverscan = 18.dp
private val EduSearchHorizontalOverscan = 16.dp
private val EduSearchDockBottomPadding = 12.dp
private val EduSearchDockHeight = com.itos.coui.search.CouiSearchBarDefaults.height
private val LightEduSearchDockShadow = com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidSearchStyle.shadow

@Composable
private fun EduSchoolSearchDock(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    imeLift: Dp,
    bottomInset: Dp,
    controlsModifier: Modifier = Modifier
) {
    val entranceProgress = remember { Animatable(0f) }
    val navigationInset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp().coerceAtMost(bottomInset)
    }
    val dockTint = settingsPageBackground(config)

    LaunchedEffect(Unit) {
        entranceProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 260f,
                visibilityThreshold = 0.001f
            )
        )
    }

    Box(Modifier.fillMaxSize().then(controlsModifier)) {
        // Blur the rows beneath the floating field, as the ColorOS notification shade does.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = navigationInset - bottomInset)
                .fillMaxWidth()
                .height(104.dp + navigationInset)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = entranceProgress.value.coerceIn(0f, 1f)
                }
                .progressiveBackdropBlur(
                    backdrop = backdrop,
                    tintColor = dockTint,
                    blurRadius = 12.dp,
                    tintIntensity = 0.12f,
                    domain = GlassBackdropDomain.ChromeCombined,
                    direction = ProgressiveBlurDirection.BottomToTop,
                    fallbackTintStops = listOf(
                        0f to dockTint.copy(alpha = 0.18f),
                        0.45f to dockTint.copy(alpha = 0.07f),
                        1f to ComposeColor.Transparent
                    )
                )
        )
        SchoolSearchField(
            value = value,
            onValueChange = onValueChange,
            backdrop = backdrop,
            config = config,
            keyboardLift = imeLift,
            bottomOffset = EduSearchDockBottomPadding + bottomInset,
            entranceProgress = entranceProgress.value,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SchoolSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    keyboardLift: Dp = 0.dp,
    bottomOffset: Dp = 0.dp,
    entranceProgress: Float = 1f,
    modifier: Modifier = Modifier
) {
    val isLargeScreen = rememberHomeAdaptiveMetrics().isLargeScreen
    if (LocalHomeMaterialControls.current.SearchField(
            value, onValueChange, appUsesDarkTheme(config), bottomOffset,
            entranceProgress, isLargeScreen, modifier, backdrop
        )) return
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val foreground = sleepDownPanelForegroundColor(config)
    val lightSearchGlass = !appUsesDarkTheme(config)
    var focused by remember { mutableStateOf(false) }
    val fieldInteractionScope = rememberCoroutineScope()
    val fieldInteractiveHighlight = remember(fieldInteractionScope) {
        InteractiveHighlight(animationScope = fieldInteractionScope)
    }
    var editableValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (editableValue.text != value) {
            editableValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            editableValue = editableValue.copy(
                selection = TextRange(editableValue.text.length)
            )
        }
    }
    val density = LocalDensity.current
    // The IME inset remains the only trigger. Keep the eased transition fast enough to follow the
    // keyboard, while still letting the cancel/search capsule finish separating after the lift.
    val keyboardSplitTarget = keyboardLift > 20.dp
    val splitTransition = updateTransition(
        targetState = keyboardSplitTarget,
        label = "edu-search-split"
    )
    val splitProgress by splitTransition.animateFloat(
        transitionSpec = {
                if (targetState) {
                    tween(
                    durationMillis = 400,
                    easing = CubicBezierEasing(0.20f, 0.82f, 0.18f, 1.0f)
                )
            } else {
                tween(
                    durationMillis = 300,
                    easing = CubicBezierEasing(0.32f, 0.0f, 0.18f, 1.0f)
                )
            }
        },
        label = "edu-search-split-progress"
    ) { opened -> if (opened) 1f else 0f }
    val splitMotionBlurModifier = if (splitProgress > 0.001f && splitProgress < 0.999f) {
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = platformMotionBlurRenderEffect(
                detailMotionBlurRadiusDp(splitProgress) * density.density
            )
        }
    } else {
        // A zero-radius RenderEffect still keeps an offscreen texture alive on some devices and
        // exposes its rectangular allocation boundary. Stable open/closed states stay layer-free.
        Modifier
    }
    val searchStyle = com.xiaomanjun.sleepdownschedule.core.ui.controls.LiquidSearchStyle
    val searchBlurRadius = searchStyle.blur
    val searchLensHeight = searchStyle.lensHeight
    val searchLensAmount = searchStyle.lensAmount
    val searchButtonHeight = searchStyle.height
    val searchSurfaceColor = searchStyle.surface(appUsesDarkTheme(config))
    val closeSearch: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    BackHandler(enabled = focused) { closeSearch() }
    val field: @Composable () -> Unit = {
        BasicTextField(
            value = editableValue,
            onValueChange = { next ->
                editableValue = next
                if (next.text != value) onValueChange(next.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = foreground),
            cursorBrush = SolidColor(ComposeColor(0xFF9E9E9E)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { closeSearch() }),
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = foreground.copy(alpha = 0.58f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (editableValue.text.isBlank()) {
                            Text(
                                "搜索学校或教务工具…",
                                color = foreground.copy(alpha = 0.50f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        val horizontalInset = 16.dp
        val pressExpansion = 1.5.dp
        val pressTranslationAllowance = 2.5.dp
        val maxPressOverscan = (horizontalInset - 1.dp).coerceAtLeast(0.dp)
        val showAction = focused || splitProgress > 0.001f
        val actionWidth = 82.dp * splitProgress
        val actionGap = 8.dp * splitProgress
        val visualDockWidth = if (isLargeScreen) {
            (maxWidth * 0.5f).coerceAtLeast(260.dp)
        } else {
            (maxWidth - horizontalInset * 2f).coerceAtLeast(120.dp)
        }
        val fieldWidth = (visualDockWidth - actionWidth - actionGap).coerceAtLeast(120.dp)
        // LiquidButton's press value is a vertical expansion, so a wide capsule grows farther
        // horizontally than 1.5dp. Reserve that real scale range plus the pointer translation.
        val pressScaleFactor = pressExpansion.value / 44f
        val fieldPressOverscan = (
            fieldWidth * pressScaleFactor + pressTranslationAllowance
        ).coerceAtMost(maxPressOverscan)
        val actionPressOverscan = (
            actionWidth * pressScaleFactor + pressTranslationAllowance
        ).coerceAtMost(maxPressOverscan)
        val fieldRenderOverscan = maxOf(fieldPressOverscan, EduSearchHorizontalOverscan)
        val actionRenderOverscan = maxOf(actionPressOverscan, EduSearchHorizontalOverscan)
        val fieldSlotWidth = fieldWidth + fieldRenderOverscan * 2f
        val actionSlotWidth = if (showAction) {
            actionWidth + actionRenderOverscan * 2f
        } else {
            0.dp
        }
        val renderEnvelopeHeight = searchButtonHeight + EduSearchVerticalOverscan * 2f
        val entranceTranslation = 52.dp * (1f - entranceProgress.coerceIn(0f, 1f))
        // The visible 44dp capsule keeps the established bottom position. The additional envelope
        // extends equally above and below it without participating in page/card measurement.
        val renderEnvelopeBottomOffset =
            (bottomOffset - EduSearchVerticalOverscan).coerceAtLeast(0.dp)
        val actionLabel = if (value.isBlank()) "取消" else "搜索"
        // 大屏下整个底部 dock 居中，action slot 相对居中后的 field slot 定位。
        val dockStart = if (isLargeScreen) (maxWidth - visualDockWidth) / 2 else horizontalInset
        val actionSlotOffsetX = dockStart + fieldWidth + actionGap - actionRenderOverscan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(fieldSlotWidth)
                    .height(renderEnvelopeHeight)
                    .offset(
                        x = dockStart - fieldRenderOverscan,
                        y = -renderEnvelopeBottomOffset + entranceTranslation
                    )
                    .graphicsLayer {
                        alpha = entranceProgress.coerceIn(0f, 1f)
                        clip = false
                    }
                    // RenderEffect necessarily allocates an offscreen texture. Applying it to the
                    // oversized slot instead of the 44dp LiquidButton keeps the real glass shadow,
                    // refraction and press expansion inside that texture rather than clipping them.
                    .then(splitMotionBlurModifier),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(fieldWidth)
                        .height(searchButtonHeight)
                ) {
                    if (backdrop != null) {
                        // The glass shell and editable foreground are siblings. Compose's blinking
                        // caret can now redraw without invalidating the backdrop consumer beneath it.
                        LiquidButton(
                            onClick = {},
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxSize(),
                            height = searchButtonHeight,
                            contentPadding = PaddingValues(0.dp),
                            blurRadius = searchBlurRadius,
                            lensHeight = searchLensHeight,
                            lensAmount = searchLensAmount,
                            chromaticAberration = false,
                            surfaceColor = searchSurfaceColor,
                            // Light mode keeps the soft depth shadow; dark mode relies on the dock
                            // tint alone so it does not gain a heavy black perimeter.
                            shadowEnabled = lightSearchGlass,
                            shadowStyle = LightEduSearchDockShadow,
                            highlightEnabled = true,
                            isInteractive = true,
                            clickTargetEnabled = false,
                            clipToBounds = false,
                            pressExpansion = pressExpansion,
                            sharedInteractiveHighlight = fieldInteractiveHighlight
                        ) {}
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(Capsule())
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidButtonVisualTransform(
                                interactiveHighlight = fieldInteractiveHighlight,
                                pressExpansion = pressExpansion
                            )
                            .then(fieldInteractiveHighlight.gestureModifier)
                            .padding(horizontal = 15.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        field()
                    }
                }
            }
            if (showAction) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(actionSlotWidth)
                    .height(renderEnvelopeHeight)
                    .offset(
                            x = actionSlotOffsetX,
                            y = -renderEnvelopeBottomOffset + entranceTranslation
                        )
                        .graphicsLayer {
                            alpha = entranceProgress.coerceIn(0f, 1f)
                            clip = false
                        }
                        .then(splitMotionBlurModifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (backdrop != null) {
                        LiquidButton(
                            onClick = closeSearch,
                            backdrop = backdrop,
                            modifier = Modifier
                                .width(actionWidth)
                                .graphicsLayer { alpha = splitProgress },
                            height = searchButtonHeight,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            blurRadius = searchBlurRadius,
                            lensHeight = searchLensHeight,
                            lensAmount = searchLensAmount,
                            chromaticAberration = false,
                            surfaceColor = searchSurfaceColor,
                            shadowEnabled = lightSearchGlass,
                            shadowStyle = LightEduSearchDockShadow,
                            highlightEnabled = true,
                            clipToBounds = false,
                            pressExpansion = pressExpansion
                        ) {
                            Text(
                                actionLabel,
                                color = foreground,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .width(actionWidth)
                                .height(searchButtonHeight)
                                .graphicsLayer { alpha = splitProgress }
                                .clip(Capsule())
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = closeSearch),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(actionLabel, color = foreground, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun ShiguangBridgeHost.resolveEduBridgeInteraction(requestId: String, valueExpression: String) {
    evaluateJavascript(
        "window._shiguangNativeCallback && window._shiguangNativeCallback(" +
            JSONObject.quote(requestId) + ", true, " + valueExpression + ");"
    )
}

private fun decodeEduBridgeValidationResult(encoded: String?): String? {
    if (encoded.isNullOrBlank() || encoded == "null") return null
    return runCatching {
        val value = JSONArray("[$encoded]").opt(0)
        when (value) {
            null, JSONObject.NULL -> null
            else -> value.toString().takeIf { it.isNotBlank() }
        }
    }.getOrElse { "输入校验失败，请重试" }
}

private fun validateEduBridgePrompt(
    bridge: ShiguangBridgeHost,
    request: EduBridgeInteractionRequest.Prompt,
    value: String,
    onResult: (String?) -> Unit
) {
    val validator = request.validator
    if (validator.isNullOrBlank()) {
        onResult(null)
        return
    }
    val script = """
        (function () {
            try {
                var result = window._shiguangValidatePrompt(${JSONObject.quote(request.requestId)}, ${JSONObject.quote(value)});
                if (result === false || result == null || result === "") return null;
                return String(result);
            } catch (error) {
                return error && error.message ? error.message : String(error);
            }
        })();
    """.trimIndent()
    if (!bridge.evaluateJavascript(script) { onResult(decodeEduBridgeValidationResult(it)) }) {
        onResult("网页已关闭，请返回后重试")
    }
}

@Composable
internal fun EduBridgeInteractionDialog(
    request: EduBridgeInteractionRequest?, bridge: ShiguangBridgeHost, state: AppState,
    backdrop: Backdrop?, onFinished: () -> Unit,
    resolveInteraction: (String, String) -> Unit = bridge::resolveInteraction
) = EduBridgeInteractionDialog(request, bridge, state.config, backdrop, onFinished, resolveInteraction)

@Composable
internal fun EduBridgeInteractionDialog(
    request: EduBridgeInteractionRequest?,
    bridge: ShiguangBridgeHost,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onFinished: () -> Unit,
    resolveInteraction: (String, String) -> Unit = bridge::resolveInteraction
) {
    when (request) {
        null -> Unit
        is EduBridgeInteractionRequest.Alert -> {
            LiquidAlertDialog(
                title = request.title,
                message = request.message,
                actions = listOf(
                    LiquidAlertAction(
                        label = request.confirmText,
                        style = LiquidAlertActionStyle.Primary
                    ) {
                        resolveInteraction(request.requestId, "true")
                        onFinished()
                    }
                ),
                backdrop = backdrop,
                config = config,
                onDismissRequest = {
                    resolveInteraction(request.requestId, "false")
                    onFinished()
                }
            )
        }
        is EduBridgeInteractionRequest.Prompt -> key(request.requestId) {
            val credentialType = remember(request.title, request.message) {
                eduPromptCredentialType(request.title, request.message)
            }
            var value by remember(request.requestId) { mutableStateOf(request.defaultValue) }
            var validationError by remember(request.requestId) { mutableStateOf<String?>(null) }
            fun cancel() {
                resolveInteraction(request.requestId, "null")
                onFinished()
            }
            fun submit() {
                validateEduBridgePrompt(bridge, request, value) { error ->
                    validationError = error
                    if (error == null) {
                        resolveInteraction(request.requestId, JSONObject.quote(value))
                        onFinished()
                    }
                }
            }
            LiquidAlertDialog(
                title = request.title,
                message = request.message,
                actions = listOf(
                    LiquidAlertAction(
                        label = "取消",
                        style = LiquidAlertActionStyle.Cancel,
                        onClick = ::cancel
                    ),
                    LiquidAlertAction(
                        label = "完成",
                        style = LiquidAlertActionStyle.Primary,
                        dismissOnClick = false,
                        onClick = ::submit
                    )
                ),
                backdrop = backdrop,
                config = config,
                onDismissRequest = ::cancel,
                messageContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (request.message.isNotBlank()) {
                            Text(
                                request.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = 0.72f)
                            )
                        }
                        DialogCapsuleField(
                            value = value,
                            onValueChange = {
                                value = it
                                validationError = null
                            },
                            placeholder = request.message.ifBlank { "请输入" },
                            config = config,
                            keyboardType = if (credentialType == EduPromptCredentialType.Password) KeyboardType.Password else KeyboardType.Text,
                            modifier = Modifier.fillMaxWidth().semantics {
                                when (credentialType) {
                                    EduPromptCredentialType.Username -> contentType = ContentType.Username
                                    EduPromptCredentialType.Password -> contentType = ContentType.Password
                                    null -> Unit
                                }
                            }
                        )
                        validationError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
        is EduBridgeInteractionRequest.SingleSelection -> {
            if (request.options.isEmpty()) {
                fun cancel() {
                    resolveInteraction(request.requestId, "null")
                    onFinished()
                }
                LiquidAlertDialog(
                    title = request.title,
                    message = "没有可选项",
                    actions = listOf(
                        LiquidAlertAction(
                            label = "知道了",
                            style = LiquidAlertActionStyle.Primary,
                            onClick = ::cancel
                        )
                    ),
                    backdrop = backdrop,
                    config = config,
                    onDismissRequest = ::cancel
                )
                return
            }
            var selectedIndex by remember(request.requestId) {
                mutableIntStateOf(request.defaultIndex.coerceIn(request.options.indices))
            }
            var visible by remember(request.requestId) { mutableStateOf(true) }
            var completion by remember(request.requestId) { mutableStateOf<(() -> Unit)?>(null) }
            fun cancel() {
                resolveInteraction(request.requestId, "null")
                onFinished()
            }
            fun submit() {
                if (selectedIndex !in request.options.indices) return
                resolveInteraction(request.requestId, selectedIndex.toString())
                onFinished()
            }
            fun closeThen(action: () -> Unit) {
                if (!visible) return
                completion = action
                visible = false
            }
            val pickerForeground = appPanelForegroundColor(config)
            val pickerColors = top.yukonga.miuix.kmp.basic.NumberPickerDefaults.colors(
                selectedTextColor = pickerForeground,
                unselectedTextColor = pickerForeground.copy(alpha = 0.34f),
                disabledSelectedTextColor = pickerForeground.copy(alpha = 0.55f),
                disabledUnselectedTextColor = pickerForeground.copy(alpha = 0.22f)
            )
            SleepDownPickerDialog(
                show = visible,
                title = request.title,
                onDismissRequest = { closeThen(::cancel) },
                onDismissFinished = {
                    val action = completion
                    completion = null
                    action?.invoke()
                },
                backdrop = backdrop,
                config = config,
                contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding),
                actions = listOf(
                    LiquidAlertAction("取消", LiquidAlertActionStyle.Cancel, dismissOnClick = false) {
                        closeThen(::cancel)
                    },
                    LiquidAlertAction("完成", LiquidAlertActionStyle.Primary,
                        enabled = selectedIndex in request.options.indices, dismissOnClick = false) {
                        closeThen(::submit)
                    }
                )
            ) {
                Text(
                    "请选择一项",
                    modifier = Modifier.fillMaxWidth(),
                    color = pickerForeground.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNumberPicker(
                    value = selectedIndex,
                    onValueChange = { selectedIndex = it },
                    range = request.options.indices,
                    visibleItemCount = 3,
                    label = { request.options[it] },
                    colors = pickerColors,
                    textStyle = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3.copy(
                        color = pickerForeground,
                        fontWeight = FontWeight.SemiBold
                    ),
                    itemHeight = 46.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects.current.usesCouiControls)
                            com.itos.coui.picker.CouiPickerDefaults.height + com.itos.coui.picker.CouiPickerDefaults.padding * 2 else 138.dp)
                )

            }
        }
    }
}

