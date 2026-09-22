package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.LocalDetailActivityFloatingOverlayHost
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import android.content.Context
import android.os.Message
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.xiaomanjun.sleepdownschedule.transition.legacy.detailMotionBlurRadiusDp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassCombinedBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


@Composable
fun EduSchoolPickerScreen(
    state: AppState,
    backdrop: Backdrop? = null,
    warehouseGeneration: Int = 0,
    availableAdapters: List<EduAdapter>? = null,
    adapterBadge: (EduAdapter) -> String? = { null },
    onSelect: (EduAdapter) -> Unit
) {
    val context = LocalContext.current
    val adapters = availableAdapters ?: remember(warehouseGeneration) {
        runCatching { ShiguangWarehouse.loadVisibleAdapters(context) }
            .getOrDefault(emptyList())
    }
    var adapterChoices by remember { mutableStateOf<List<EduAdapter>?>(null) }
    var query by remember { mutableStateOf("") }
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
        query = query,
        onQueryChange = { query = it },
        onSelect = { school ->
            val matches = adapters.filter { it.school.id == school.id }
            if (matches.size == 1) {
                onSelect(matches.single())
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
                    style = LiquidAlertActionStyle.Secondary,
                    onClick = { adapterChoices = null }
                ),
                LiquidAlertAction(
                    label = "确认",
                    style = LiquidAlertActionStyle.Primary,
                    enabled = selectedAdapter != null,
                    onClick = {
                        selectedAdapter?.let { adapter ->
                            adapterChoices = null
                            onSelect(adapter)
                        }
                    }
                )
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { adapterChoices = null },
            messageContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
}

@Composable
private fun EduAdapterChoiceCard(
    adapter: EduAdapter,
    badgeText: String?,
    selected: Boolean,
    config: ScheduleConfigEntity,
    onClick: () -> Unit
) {
    val foreground = sleepDownPanelForegroundColor(config)
    val dark = appUsesDarkTheme(config)
    val accent = ComposeColor(0xFF0A84FF)
    val details = buildList {
        add(eduAdapterCategoryLabel(adapter.category))
        adapter.maintainer.takeIf(String::isNotBlank)?.let { add(it) }
        adapter.description.takeIf(String::isNotBlank)?.let { add(it) }
    }.joinToString(" · ")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        shape = RoundedRectangle(
            cornerRadius = SleepDownDesignTokens.CenteredDialog.Corner -
                SleepDownDesignTokens.CenteredDialog.ContentPadding -
                SleepDownDesignTokens.CenteredDialog.AlertTextHorizontalInset,
            style = RoundedCornerStyle.Continuous
        ),
        color = if (selected) {
            accent.copy(alpha = if (dark) 0.24f else 0.13f)
        } else {
            foreground.copy(alpha = if (dark) 0.09f else 0.055f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent.copy(alpha = 0.88f) else foreground.copy(alpha = 0.14f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = adapter.adapterName,
                    color = foreground,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                badgeText?.let {
                    Text(it, color = accent, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp).clip(Capsule())
                            .background(accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        color = foreground.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(Capsule())
                    .background(
                        if (selected) accent else foreground.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(Capsule())
                            .background(ComposeColor.White)
                    )
                }
            }
        }
    }
}

private data class EduSchoolAdapterGroup(
    val school: EduSchool,
    val adapters: List<EduAdapter>
)

@Composable
fun EduSchoolIndexedSelectScreen(
    state: AppState,
    backdrop: Backdrop?,
    adapters: List<EduAdapter>,
    query: String,
    onQueryChange: (String) -> Unit,
    adapterBadge: (EduAdapter) -> String? = { null },
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
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
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
    val indexedSchools = remember(schoolGroups) {
        schoolGroups.filterNot { it in aiEduSchools || it in pinnedSchools }
    }
    val grouped = remember(indexedSchools) {
        indexedSchools
            .groupBy { it.school.initial.ifBlank { "#" }.uppercase() }
            .toSortedMap()
    }
    val letters = remember(grouped) { grouped.keys.toList() }
    val sectionPositions = remember(grouped, aiEduSchools, pinnedSchools) {
        val aiSectionSize = if (aiEduSchools.isEmpty()) 0 else 2
        val pinnedSectionSize = if (pinnedSchools.isEmpty()) 0 else 2
        var index = aiSectionSize + pinnedSectionSize
        buildMap {
            grouped.forEach { (letter, _) ->
                put(letter, index)
                index += 2
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
    val currentRailLetters = rememberUpdatedState(letters)
    val currentRailPositions = rememberUpdatedState(sectionPositions)
    val currentRailTopPadding = rememberUpdatedState(topPadding)
    val currentRailScope = rememberUpdatedState(scope)
    val currentRailHaptic = rememberUpdatedState(haptic)
    val floatingSearchDock: (@Composable () -> Unit)? = if (floatingOverlayHost != null) {
        remember(floatingOverlayHost) {
            @Composable {
                EduSchoolDockViewport { bottomInset ->
                EduSchoolSearchDock(
                    value = currentSearchQuery.value,
                    onValueChange = currentSearchOnChange.value,
                    backdrop = currentSearchBackdrop.value,
                    config = currentSearchConfig.value,
                    imeLift = currentSearchImeLift.value,
                    bottomInset = bottomInset
                )
                // The alphabet rail floats at the same root level as the top bar, as a sibling of
                // the Miuix Scaffold instead of a page child. Its gradient blur therefore keeps a
                // full-window envelope and can no longer be cropped by the card/list subtrees.
                EduAlphabetRailDock(
                    listState = currentRailListState.value,
                    letters = currentRailLetters.value,
                    sectionPositions = currentRailPositions.value,
                    backdrop = currentSearchBackdrop.value,
                    config = currentSearchConfig.value,
                    topPadding = currentRailTopPadding.value,
                    bottomInset = bottomInset,
                    scope = currentRailScope.value,
                    haptic = currentRailHaptic.value
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
            LazyColumn(
                state = listState,
                modifier = Modifier.nestedScroll(keyboardDismissScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding + 12.dp,
                    bottom = DockScrollPadding
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (adapters.isEmpty()) {
                    item { Text("没有找到学校适配资源", color = MaterialTheme.colorScheme.error) }
                } else {
                    if (aiEduSchools.isNotEmpty()) {
                        item(key = "ai-edu-title") { GlassPreferenceCategory("AI教务导入") }
                        item(key = "ai-edu-card") {
                            EduSchoolGroupCard(
                                schools = aiEduSchools,
                                state = state,
                                backdrop = backdrop,
                                adapterBadge = adapterBadge,
                                onSelect = onSelect
                            )
                        }
                    }
                    if (pinnedSchools.isNotEmpty()) {
                        item(key = "general-edu-title") { GlassPreferenceCategory("导入工具") }
                        item(key = "general-edu-card") {
                            EduSchoolGroupCard(
                                schools = pinnedSchools,
                                state = state,
                                backdrop = backdrop,
                                adapterBadge = adapterBadge,
                                onSelect = onSelect
                            )
                        }
                    }
                    grouped.forEach { (letter, list) ->
                        item(key = "section-$letter") { GlassPreferenceCategory(letter) }
                        item(key = "section-card-$letter") {
                            EduSchoolGroupCard(
                                schools = list,
                                state = state,
                                backdrop = backdrop,
                                adapterBadge = adapterBadge,
                                onSelect = onSelect
                            )
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
            EduAlphabetRailDock(
                listState = listState,
                letters = letters,
                sectionPositions = sectionPositions,
                backdrop = overlayBackdrop,
                config = state.config,
                topPadding = topPadding,
                bottomInset = bottomInset,
                scope = scope,
                haptic = haptic
            )
            }
        }
    }
}

@Composable
private fun EduSchoolDockViewport(content: @Composable (Dp) -> Unit) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    // Height can itself change under adjustResize; it must not erase the closed-keyboard baseline.
    var closedBottom by remember(configuration.screenWidthDp, configuration.orientation, density.density) { mutableIntStateOf(0) }
    var currentBottom by remember { mutableIntStateOf(0) }
    val remaining = schoolDockBottomInsetPx(imeBottom, navigationBottom, closedBottom, currentBottom)
    Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        val bottom = (coordinates.positionOnScreen().y + coordinates.size.height).toInt()
        currentBottom = bottom
        if (imeBottom <= navigationBottom) closedBottom = bottom
    }) {
        content(with(density) { remaining.toDp() })
    }
}


@Composable
private fun EduAlphabetRailDock(
    listState: LazyListState,
    letters: List<String>,
    sectionPositions: Map<String, Int>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    topPadding: Dp,
    bottomInset: Dp,
    scope: CoroutineScope,
    haptic: HapticFeedback
) {
    if (letters.isEmpty()) return
    val density = LocalDensity.current
    // Center the rail only in the usable list window: the measured top bar and the entire search
    // dock (including its safe-area/IME lift) are excluded from the centering bounds.
    val alphabetRailBottomExclusion = 44.dp + 18.dp + bottomInset
    var railDragging by remember { mutableStateOf(false) }
    var railPointerIndex by remember { mutableIntStateOf(-1) }
    val railScrolling by remember {
        derivedStateOf { listState.isScrollInProgress || railDragging }
    }
    // Keep the rail visible briefly after the list settles so it does not pop away the moment
    // the finger leaves the screen.
    var showAlphabetRail by remember { mutableStateOf(false) }
    var alphabetRailExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(railScrolling) {
        if (railScrolling) {
            showAlphabetRail = true
            alphabetRailExpanded = true
        } else {
            delay(760)
            // Let the full glass shell perform its fade/motion-blur exit first. Collapse the
            // width only after the visibility transition has left the composition, otherwise the
            // blur would be applied to a 10dp sliver and the rail would look lopsided.
            showAlphabetRail = false
            delay(240)
            alphabetRailExpanded = false
        }
    }
    val visibleSectionIndex by remember(letters, sectionPositions) {
        derivedStateOf {
            if (!listState.canScrollForward) {
                letters.lastIndex.coerceAtLeast(0)
            } else {
                val firstVisible = listState.firstVisibleItemIndex
                letters.indexOfLast { letter ->
                    (sectionPositions[letter] ?: Int.MAX_VALUE) <= firstVisible
                }.coerceAtLeast(0)
            }
        }
    }
    val activeAlphabetIndex = railPointerIndex.takeIf { it >= 0 } ?: visibleSectionIndex
    val alphabetRailWidth by animateDpAsState(
        // Keep the touch target generous while moving the visible letters closer to the physical
        // right edge. The surrounding blur veil remains independent of this content width.
        targetValue = if (alphabetRailExpanded) 46.dp else 10.dp,
        animationSpec = tween(180),
        label = "edu-alphabet-width"
    )
    val alphabetContentAlpha by animateFloatAsState(
        targetValue = if (alphabetRailExpanded) 1f else 0f,
        animationSpec = tween(130),
        label = "edu-alphabet-content"
    )
    val alphabetIndicatorProgress by animateFloatAsState(
        targetValue = activeAlphabetIndex
            .coerceIn(0, letters.lastIndex.coerceAtLeast(0))
            .toFloat(),
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = 560f,
            visibilityThreshold = 0.001f
        ),
        label = "edu-alphabet-indicator"
    )
    // Root-level dock: the whole window is the layout envelope, so the gradient blur can fade
    // out toward the page without being cropped by any card/list/offscreen boundary.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = topPadding,
                    bottom = alphabetRailBottomExclusion
                )
                .graphicsLayer { clip = false }
        ) {
            AnimatedVisibility(
                visible = showAlphabetRail,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 4.dp),
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(240))
            ) {
                val railMotionProgress by transition.animateFloat(
                    transitionSpec = { tween(240) },
                    label = "edu-alphabet-motion"
                ) { state ->
                    if (state == EnterExitState.Visible) 1f else 0f
                }
                val railTint = if (appUsesDarkTheme(config)) {
                    ComposeColor(0xFF111318)
                } else {
                    ComposeColor.White
                }
                Box(
                    modifier = Modifier
                        .width(96.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // The veil owns a fixed 2D envelope. It is measured from the alphabet content
                    // plus top/bottom fade space, but never participates in touch handling or the
                    // rail's 10dp -> 58dp content-width animation.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                        .progressiveBackdropBlur(
                            backdrop = backdrop,
                            tintColor = railTint,
                            blurRadius = 8.dp,
                            tintIntensity = if (appUsesDarkTheme(config)) 0.08f else 0.18f,
                            domain = GlassBackdropDomain.ChromeCombined,
                            direction = ProgressiveBlurDirection.RailThreeWay,
                            topMaskFadeStart = 0.04f,
                            topMaskFadeEnd = 0.96f,
                            topTintFadeStart = 0.12f,
                            topTintFadeEnd = 0.98f,
                            fallbackTintStops = listOf(
                                0f to railTint.copy(alpha = 0.40f),
                                0.58f to railTint.copy(alpha = 0.08f),
                                1f to ComposeColor.Transparent
                            )
                        )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(vertical = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .pointerInput(letters, sectionPositions, haptic) {
                                    awaitPointerEventScope {
                                        var lastIndex = -1
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.firstOrNull { it.pressed }
                                            if (pressed == null) {
                                                railDragging = false
                                                railPointerIndex = -1
                                                lastIndex = -1
                                                continue
                                            }
                                            railDragging = true
                                            val itemHeight = size.height / letters.size.toFloat()
                                            val index = (pressed.position.y / itemHeight).toInt()
                                                .coerceIn(0, letters.lastIndex)
                                            if (index == lastIndex) continue
                                            lastIndex = index
                                            railPointerIndex = index
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            sectionPositions[letters[index]]?.let { position ->
                                                scope.launch { listState.scrollToItem(position) }
                                            }
                                        }
                                    }
                                }
                                .width(alphabetRailWidth)
                                .then(
                                    if (railMotionProgress > 0.001f && railMotionProgress < 0.999f) {
                                        Modifier.graphicsLayer {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                            renderEffect = platformBlurRenderEffect(
                                                detailMotionBlurRadiusDp(railMotionProgress) * density.density
                                            )
                                            clip = false
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Column(
                                modifier = Modifier
                                    .graphicsLayer { alpha = alphabetContentAlpha }
                                    .padding(horizontal = 5.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                letters.forEachIndexed { index, letter ->
                                    val highlight = (1f - abs(
                                        index.toFloat() - alphabetIndicatorProgress
                                    )).coerceIn(0f, 1f)
                                    Text(
                                        letter,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(Capsule())
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                sectionPositions[letter]?.let { position ->
                                                    scope.launch { listState.animateScrollToItem(position) }
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = lerp(
                                            sleepDownPanelForegroundColor(config).copy(
                                                alpha = if (appUsesDarkTheme(config)) 0.52f else 0.68f
                                            ),
                                            ComposeColor(0xFF0A84FF),
                                            highlight
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EduSchoolGroupCard(
    schools: List<EduSchoolAdapterGroup>,
    adapterBadge: (EduAdapter) -> String?,
    state: AppState,
    backdrop: Backdrop?,
    onSelect: (EduSchool) -> Unit
) {
    SettingsGroup(
        backdrop = backdrop,
        config = state.config,
        modifier = Modifier.fillMaxWidth()
    ) {
        schools.forEach { group ->
            SettingsNavigationRow(
                title = group.school.name,
                subtitle = group.adapters.joinToString(" / ") { it.adapterName },
                badgeText = group.adapters.firstNotNullOfOrNull(adapterBadge),
                onClick = { onSelect(group.school) }
            )
        }
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
private val LightEduSearchDockShadow = Shadow(
    radius = 8.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = ComposeColor.Black.copy(alpha = 0.10f)
)

@Composable
private fun EduSchoolSearchDock(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    imeLift: Dp,
    bottomInset: Dp
) {
    val entranceProgress = remember { Animatable(0f) }
    val searchDockBottomPadding = 18.dp
    val searchDockTint = if (appUsesDarkTheme(config)) {
        ComposeColor(0xFF1A1A1D)
    } else {
        ComposeColor.White
    }
    val searchDockGradientHeight = 168.dp + bottomInset

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

    // This is a root-level floating dock. The scrim is a separate sibling underneath the glass
    // controls, so it stays present when the field splits into the action capsule.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(searchDockGradientHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            searchDockTint.copy(alpha = 0f),
                            searchDockTint.copy(alpha = if (appUsesDarkTheme(config)) 0.42f else 0.36f),
                            searchDockTint.copy(alpha = if (appUsesDarkTheme(config)) 0.76f else 0.70f),
                            searchDockTint.copy(alpha = if (appUsesDarkTheme(config)) 0.94f else 0.92f)
                        )
                    )
                )
        )
        SchoolSearchField(
            value = value,
            onValueChange = onValueChange,
            backdrop = backdrop,
            config = config,
            keyboardLift = imeLift,
            bottomOffset = searchDockBottomPadding + bottomInset,
            entranceProgress = entranceProgress.value,
            modifier = Modifier
                // Give the control the whole window as its layout envelope. Only its two visible
                // capsules are positioned near the bottom; no 44/60dp parent can crop the Kyant
                // shadow, refraction or press expansion anymore.
                .fillMaxSize()
                .graphicsLayer { clip = false }
                .zIndex(1f)
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
    val searchBlurRadius = 5.dp
    val searchLensHeight = 14.dp
    val searchLensAmount = 24.dp
    val searchButtonHeight = 44.dp
    val searchSurfaceColor = if (appUsesDarkTheme(config)) {
        // 深色模式：搜索胶囊采用黑色玻璃基底。
        ComposeColor(0xFF16181D).copy(alpha = 0.78f)
    } else {
        ComposeColor.White.copy(alpha = 0.22f)
    }
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
        val actionSlotOffsetX = if (isLargeScreen) {
            (maxWidth - fieldWidth) / 2 + fieldWidth + actionGap - actionRenderOverscan
        } else {
            horizontalInset + fieldWidth + actionGap - actionRenderOverscan
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
        ) {
            Box(
                modifier = Modifier
                    .align(if (isLargeScreen) Alignment.BottomCenter else Alignment.BottomStart)
                    .width(fieldSlotWidth)
                    .height(renderEnvelopeHeight)
                    .offset(
                        x = if (isLargeScreen) 0.dp else horizontalInset - fieldRenderOverscan,
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
                var result = ${validator}(${JSONObject.quote(value)});
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
    request: EduBridgeInteractionRequest?,
    bridge: ShiguangBridgeHost,
    state: AppState,
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
                config = state.config,
                onDismissRequest = {
                    resolveInteraction(request.requestId, "false")
                    onFinished()
                }
            )
        }
        is EduBridgeInteractionRequest.Prompt -> {
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
                        style = LiquidAlertActionStyle.Secondary,
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
                config = state.config,
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
                            config = state.config,
                            modifier = Modifier.fillMaxWidth()
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
                    config = state.config,
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
            val pickerForeground = appPanelForegroundColor(state.config)
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
                config = state.config,
                contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
            ) {
                Text(
                    "请选择一项",
                    modifier = Modifier.fillMaxWidth(),
                    color = pickerForeground.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                top.yukonga.miuix.kmp.basic.NumberPicker(
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
                        .height(138.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SleepDownDesignTokens.Dialog.ActionSpacing)
                ) {
                    QuickSheetLiquidAction(
                        label = "取消",
                        enabled = true,
                        backdrop = backdrop,
                        config = state.config,
                        modifier = Modifier.weight(1f),
                        height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                    ) { closeThen(::cancel) }
                    QuickSheetLiquidAction(
                        label = "完成",
                        enabled = selectedIndex in request.options.indices,
                        backdrop = backdrop,
                        config = state.config,
                        modifier = Modifier.weight(1f),
                        primary = true,
                        height = SleepDownDesignTokens.CenteredDialog.ActionHeight
                    ) { closeThen(::submit) }
                }
            }
        }
    }
}

