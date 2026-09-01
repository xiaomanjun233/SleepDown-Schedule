package com.xiaomanjun.sleepdownschedule.feature.importing

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.liquidButtonVisualTransform
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.shadow.Shadow
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.sleepDownPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidCascadingPopup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidMenuItem
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.platformMotionBlurRenderEffect
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.transition.legacy.detailMotionBlurRadiusDp

private val BrowserDockImeEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val BrowserDockCompactOpenEasing = CubicBezierEasing(0.18f, 0.78f, 0.18f, 1f)
private val BrowserDockCompactCloseEasing = CubicBezierEasing(0.30f, 0.06f, 0.20f, 1f)
private val BrowserDockLeftControlsWidth = 93.dp
private val BrowserDockRightControlsWidth = 82.dp
private val BrowserDockExpandedHeight = 56.dp
private val BrowserDockCompactHeight = 44.dp
private val BrowserDockExpandedHorizontalInset = 14.dp
private val BrowserDockCompactHorizontalInset = 36.dp
private val BrowserDockMotionOverscan = 18.dp
private val BrowserDockKeyboardAttachOffset = 12.dp
private val LightBrowserDockShadow = Shadow(
    radius = 6.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.Black.copy(alpha = 0.10f)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EduBrowserDock(
    config: ScheduleConfigEntity,
    backdrop: Backdrop,
    address: String,
    onAddressChange: (String) -> Unit,
    onGo: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    originalImportAvailable: Boolean,
    aiImportRunning: Boolean,
    onOriginalImport: () -> Unit,
    onAiImport: () -> Unit,
    desktopMode: Boolean,
    onRefresh: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    historyEntries: List<EduLoginHistoryEntry> = emptyList(),
    historyExpanded: Boolean = false,
    onHistoryExpandedChange: (Boolean) -> Unit = {},
    onHistorySelected: (EduLoginHistoryEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val foreground = sleepDownPanelForegroundColor(config)
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dockInteractionScope = rememberCoroutineScope()
    val dockInteractiveHighlight = remember(dockInteractionScope) {
        InteractiveHighlight(
            animationScope = dockInteractionScope,
            radius = { size -> size.minDimension * 2.2f }
        )
    }
    var addressFocused by remember { mutableStateOf(false) }
    var addressFieldValue by remember {
        mutableStateOf(TextFieldValue(address, selection = TextRange(address.length)))
    }
    LaunchedEffect(address) {
        if (addressFieldValue.text != address) {
            addressFieldValue = TextFieldValue(address, selection = TextRange(address.length))
        }
    }
    LaunchedEffect(addressFocused) {
        if (addressFocused) {
            addressFieldValue = addressFieldValue.copy(
                selection = TextRange(addressFieldValue.text.length)
            )
        }
    }
    var importMenuVisible by remember { mutableStateOf(false) }
    var moreMenuVisible by remember { mutableStateOf(false) }
    var importAnchor by remember { mutableStateOf(Rect.Zero) }
    var moreAnchor by remember { mutableStateOf(Rect.Zero) }
    val lightDockGlass = !appUsesDarkTheme(config)
    val dockSurfaceColor = if (lightDockGlass) {
        Color.White.copy(alpha = 0.30f)
    } else {
        Color(0xFF16181D).copy(alpha = 0.78f)
    }
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeSourceBottom = WindowInsets.imeAnimationSource.getBottom(density)
    val imeTargetBottom = WindowInsets.imeAnimationTarget.getBottom(density)
    val imeTravel = (maxOf(imeBottom, imeSourceBottom, imeTargetBottom) - navigationBottom)
        .coerceAtLeast(0)
    val rawImeProgress = if (imeTravel == 0) {
        0f
    } else {
        ((imeBottom - navigationBottom).coerceAtLeast(0).toFloat() / imeTravel).coerceIn(0f, 1f)
    }
    val addressExpansionProgress = if (addressFocused) {
        BrowserDockImeEasing.transform(rawImeProgress)
    } else {
        0f
    }
    val imeOpenTarget = (imeTargetBottom - navigationBottom) >
        with(density) { 20.dp.roundToPx() }
    val visibleHistoryEntries = remember(historyEntries) { historyEntries.take(3) }
    val historyOpenTarget = historyExpanded &&
        visibleHistoryEntries.isNotEmpty() &&
        !addressFocused &&
        !imeOpenTarget
    val historyExpansionProgress by animateFloatAsState(
        targetValue = if (historyOpenTarget) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = 390f,
            visibilityThreshold = 0.001f
        ),
        label = "edu-browser-history-expand"
    )
    val historyPanelHeight = 44.dp + 48.dp * visibleHistoryEntries.size
    // A field inside the WebView can open the IME as soon as the page loads. Preserve the
    // requested history state and suppress only its current layout through historyOpenTarget;
    // it can then finish expanding when that page keyboard closes. Focusing the Dock's own
    // address field still closes history explicitly in onFocusChanged below.
    val compactForPageInput by animateFloatAsState(
        targetValue = if (imeOpenTarget && !addressFocused) 1f else 0f,
        animationSpec = if (imeOpenTarget && !addressFocused) {
            tween(durationMillis = 360, easing = BrowserDockCompactOpenEasing)
        } else {
            tween(durationMillis = 280, easing = BrowserDockCompactCloseEasing)
        },
        label = "edu-browser-page-input-compact"
    )
    val controlsCollapseProgress = maxOf(addressExpansionProgress, compactForPageInput)
    val sideControlsAlpha = (1f - controlsCollapseProgress).coerceIn(0f, 1f)
    val sideControlsBlurPx = detailMotionBlurRadiusDp(controlsCollapseProgress) * density.density
    val sideControlsEnabled = controlsCollapseProgress < 0.02f
    val sideControlsTravelPx = with(density) { 18.dp.toPx() } * controlsCollapseProgress
    val compactAddressLabel = remember(addressFieldValue.text) {
        runCatching { Uri.parse(addressFieldValue.text).host }
            .getOrNull()
            ?.removePrefix("www.")
            ?.takeIf(String::isNotBlank)
            ?: "教务系统"
    }
    val dockMotionBlurPx = detailMotionBlurRadiusDp(compactForPageInput) * density.density
    val dockMotionModifier = if (
        compactForPageInput > 0.001f &&
        compactForPageInput < 0.999f
    ) {
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = platformMotionBlurRenderEffect(dockMotionBlurPx)
            clip = false
        }
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BrowserDockExpandedHeight + historyPanelHeight * historyExpansionProgress),
        contentAlignment = Alignment.Center
    ) {
        val dockHorizontalInset = lerp(
            BrowserDockExpandedHorizontalInset,
            BrowserDockCompactHorizontalInset,
            compactForPageInput
        )
        val dockWidth = (maxWidth - dockHorizontalInset * 2f).coerceAtLeast(0.dp)
        val dockHeight = lerp(
            BrowserDockExpandedHeight,
            BrowserDockCompactHeight,
            compactForPageInput
        )
        val dockTotalHeight = dockHeight + historyPanelHeight * historyExpansionProgress
        val dockCorner = dockHeight / 2f

        Box(
            modifier = Modifier
                .requiredWidth(dockWidth + BrowserDockMotionOverscan * 2f)
                .requiredHeight(dockTotalHeight + BrowserDockMotionOverscan * 2f)
                .offset(y = BrowserDockKeyboardAttachOffset * compactForPageInput)
                .then(dockMotionModifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(dockWidth)
                    .height(dockTotalHeight)
            ) {
                // Keep the blinking editable caret outside the backdrop consumer. Otherwise every
                // caret blink invalidates the complete liquid-glass surface on some renderers.
                LiquidButton(
                    onClick = {},
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize(),
                    height = dockTotalHeight,
                    contentPadding = PaddingValues(0.dp),
                    blurRadius = 5.dp,
                    lensHeight = 14.dp,
                    lensAmount = 24.dp,
                    chromaticAberration = false,
                    surfaceColor = dockSurfaceColor,
                    shadowEnabled = lightDockGlass,
                    shadowStyle = LightBrowserDockShadow,
                    highlightEnabled = true,
                    isInteractive = true,
                    highlightRadiusMultiplier = 2.2f,
                    shape = RoundedCornerShape(
                        topStart = dockCorner,
                        topEnd = dockCorner,
                        bottomStart = dockCorner,
                        bottomEnd = dockCorner
                    ),
                    clipToBounds = false,
                    clickTargetEnabled = false,
                    pressExpansion = 1.5.dp,
                    sharedInteractiveHighlight = dockInteractiveHighlight
                ) {}

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidButtonVisualTransform(
                        interactiveHighlight = dockInteractiveHighlight,
                        pressExpansion = 1.5.dp
                    )
                    .then(dockInteractiveHighlight.gestureModifier)
            ) {
                if (visibleHistoryEntries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(historyPanelHeight * historyExpansionProgress)
                            .clipToBounds()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(historyPanelHeight)
                                .graphicsLayer {
                                    alpha = historyExpansionProgress.coerceIn(0f, 1f)
                                    translationY = (1f - historyExpansionProgress) * 12.dp.toPx()
                                }
                                .padding(start = 16.dp, end = 12.dp, top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "最近使用的网址",
                                    modifier = Modifier.weight(1f),
                                    color = foreground,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "收起",
                                    color = foreground.copy(alpha = 0.62f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onHistoryExpandedChange(false) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            visibleHistoryEntries.forEach { entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(
                                            interactionSource = remember(entry.id) { MutableInteractionSource() },
                                            indication = null
                                        ) { onHistorySelected(entry) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = entry.title,
                                        color = foreground,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.url,
                                        color = foreground.copy(alpha = 0.58f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(dockHeight)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                Box(
                    modifier = Modifier
                        .width(BrowserDockLeftControlsWidth * sideControlsAlpha)
                        .height(40.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .width(BrowserDockLeftControlsWidth)
                            .height(40.dp)
                            .graphicsLayer {
                                translationX = -sideControlsTravelPx
                                alpha = sideControlsAlpha
                                compositingStrategy = if (sideControlsBlurPx > 0.01f) {
                                    CompositingStrategy.Offscreen
                                } else {
                                    CompositingStrategy.Auto
                                }
                                renderEffect = platformMotionBlurRenderEffect(sideControlsBlurPx)
                                clip = false
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        EduBrowserDockIcon(
                            iconRes = R.drawable.ic_arrow_back,
                            contentDescription = "网页后退",
                            foreground = foreground,
                            enabled = canGoBack && sideControlsEnabled,
                            onClick = onBack
                        )
                        EduBrowserDockIcon(
                            iconRes = R.drawable.ic_arrow_back,
                            contentDescription = "网页前进",
                            foreground = foreground,
                            enabled = canGoForward && sideControlsEnabled,
                            flipHorizontal = true,
                            onClick = onForward
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(24.dp)
                                .background(foreground.copy(alpha = 0.16f))
                        )
                    }
                }
                BasicTextField(
                    value = addressFieldValue,
                    onValueChange = { next ->
                        addressFieldValue = next
                        if (next.text != address) onAddressChange(next.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .graphicsLayer {
                            alpha = (1f - compactForPageInput).coerceIn(0f, 1f)
                        }
                        .onFocusChanged {
                            addressFocused = it.isFocused
                            if (it.isFocused) {
                                onHistoryExpandedChange(false)
                                importMenuVisible = false
                                moreMenuVisible = false
                            }
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (addressFocused) foreground else Color.Transparent
                    ),
                    cursorBrush = SolidColor(Color(0xFF168CFF)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            onGo()
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    ),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            when {
                                addressFieldValue.text.isBlank() || addressFieldValue.text == "https://" -> Text(
                                    "输入教务系统网址",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = foreground.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                                !addressFocused -> Text(
                                    addressFieldValue.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = foreground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .width(BrowserDockRightControlsWidth * sideControlsAlpha)
                        .height(40.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .width(BrowserDockRightControlsWidth)
                            .height(40.dp)
                            .graphicsLayer {
                                translationX = sideControlsTravelPx
                                alpha = sideControlsAlpha
                                compositingStrategy = if (sideControlsBlurPx > 0.01f) {
                                    CompositingStrategy.Offscreen
                                } else {
                                    CompositingStrategy.Auto
                                }
                                renderEffect = platformMotionBlurRenderEffect(sideControlsBlurPx)
                                clip = false
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        EduBrowserDockIcon(
                            iconRes = R.drawable.ic_school_import,
                            contentDescription = "导入",
                            foreground = foreground,
                            enabled = sideControlsEnabled,
                            modifier = Modifier.onGloballyPositioned { importAnchor = it.boundsInRoot() },
                            onClick = {
                                moreMenuVisible = false
                                importMenuVisible = !importMenuVisible
                            }
                        )
                        EduBrowserDockIcon(
                            iconRes = R.drawable.ic_more_horizontal,
                            contentDescription = "更多浏览器操作",
                            foreground = foreground,
                            enabled = sideControlsEnabled,
                            modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInRoot() },
                            onClick = {
                                importMenuVisible = false
                                moreMenuVisible = !moreMenuVisible
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = compactForPageInput.coerceIn(0f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "安全连接",
                    tint = foreground.copy(alpha = 0.72f),
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = compactAddressLabel,
                    color = foreground,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        }
        }

        SleepDownLiquidCascadingPopup(
            show = importMenuVisible,
            anchorBounds = importAnchor,
            items = buildList {
                if (originalImportAvailable) {
                    add(SleepDownLiquidMenuItem(
                        key = "edu-original-import",
                        text = "常规教务导入",
                        iconRes = R.drawable.ic_school_import,
                        onClick = {
                            importMenuVisible = false
                            onOriginalImport()
                        }
                    ))
                }
                add(SleepDownLiquidMenuItem(
                    key = "edu-ai-import",
                    text = "AI 导入",
                    iconRes = R.drawable.ic_ai_import,
                    enabled = !aiImportRunning,
                    onClick = {
                        importMenuVisible = false
                        onAiImport()
                    }
                ))
            },
            onDismissRequest = { importMenuVisible = false },
            backdrop = backdrop,
            config = config,
            panelMinWidth = 174.dp,
            horizontalSafeInset = BrowserDockExpandedHorizontalInset
        )
        SleepDownLiquidCascadingPopup(
            show = moreMenuVisible,
            anchorBounds = moreAnchor,
            items = buildList {
                if (visibleHistoryEntries.isNotEmpty()) {
                    add(SleepDownLiquidMenuItem(
                        key = "edu-history",
                        text = "最近使用的网址",
                        iconRes = R.drawable.ic_history,
                        onClick = {
                            moreMenuVisible = false
                            onHistoryExpandedChange(true)
                        }
                    ))
                }
                add(SleepDownLiquidMenuItem(
                    key = "edu-refresh",
                    text = "刷新",
                    iconRes = R.drawable.ic_refresh,
                    onClick = {
                        moreMenuVisible = false
                        onRefresh()
                    }
                ))
                add(SleepDownLiquidMenuItem(
                    key = "edu-web-mode",
                    text = if (desktopMode) "切换为手机网页" else "切换为电脑网页",
                    iconRes = R.drawable.ic_web_mode,
                    onClick = {
                        moreMenuVisible = false
                        onToggleDesktopMode()
                    }
                ))
            },
            onDismissRequest = { moreMenuVisible = false },
            backdrop = backdrop,
            config = config,
            panelMinWidth = 194.dp,
            horizontalSafeInset = BrowserDockExpandedHorizontalInset
        )
    }
}

@Composable
private fun EduBrowserDockIcon(
    iconRes: Int,
    contentDescription: String,
    foreground: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    flipHorizontal: Boolean = false
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foreground.copy(alpha = if (enabled) 1f else 0.36f),
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer(scaleX = if (flipHorizontal) -1f else 1f)
        )
    }
}
