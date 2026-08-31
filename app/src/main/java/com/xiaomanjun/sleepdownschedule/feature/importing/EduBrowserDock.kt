package com.xiaomanjun.sleepdownschedule.feature.importing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.CubicBezierEasing
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.sleepDownPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidCascadingPopup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidMenuItem
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import com.xiaomanjun.sleepdownschedule.glass.ui.platformMotionBlurRenderEffect
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.transition.legacy.detailMotionBlurRadiusDp

private val BrowserDockImeEasing = CubicBezierEasing(0.18f, 0f, 0f, 1f)
private val BrowserDockLeftControlsWidth = 93.dp
private val BrowserDockRightControlsWidth = 82.dp

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
    modifier: Modifier = Modifier
) {
    val foreground = sleepDownPanelForegroundColor(config)
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var addressFocused by remember { mutableStateOf(false) }
    var importMenuVisible by remember { mutableStateOf(false) }
    var moreMenuVisible by remember { mutableStateOf(false) }
    var importAnchor by remember { mutableStateOf(Rect.Zero) }
    var moreAnchor by remember { mutableStateOf(Rect.Zero) }
    val lightDockGlass = !appUsesDarkTheme(config)
    val dockSurfaceColor = if (lightDockGlass) {
        Color.White.copy(alpha = 0.22f)
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
    val addressExpansionProgress = BrowserDockImeEasing.transform(rawImeProgress)
    val sideControlsAlpha = (1f - addressExpansionProgress).coerceIn(0f, 1f)
    val sideControlsBlurPx = detailMotionBlurRadiusDp(addressExpansionProgress) * density.density
    val sideControlsEnabled = addressExpansionProgress < 0.02f
    val sideControlsTravelPx = with(density) { 18.dp.toPx() } * addressExpansionProgress

    Box(modifier = modifier) {
        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            height = 56.dp,
            contentPadding = PaddingValues(0.dp),
            blurRadius = 5.dp,
            lensHeight = 14.dp,
            lensAmount = 24.dp,
            chromaticAberration = false,
            surfaceColor = dockSurfaceColor,
            shadowEnabled = lightDockGlass,
            highlightEnabled = true,
            isInteractive = true,
            highlightRadiusMultiplier = 2.2f,
            clipToBounds = false,
            clickTargetEnabled = false,
            pressExpansion = 1.5.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
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
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .onFocusChanged {
                            addressFocused = it.isFocused
                            if (it.isFocused) {
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
                                address.isBlank() || address == "https://" -> Text(
                                    "输入教务系统网址",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = foreground.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                                !addressFocused -> Text(
                                    address,
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
            panelMinWidth = 174.dp
        )
        SleepDownLiquidCascadingPopup(
            show = moreMenuVisible,
            anchorBounds = moreAnchor,
            items = listOf(
                SleepDownLiquidMenuItem(
                    key = "edu-refresh",
                    text = "刷新",
                    iconRes = R.drawable.ic_refresh,
                    onClick = {
                        moreMenuVisible = false
                        onRefresh()
                    }
                ),
                SleepDownLiquidMenuItem(
                    key = "edu-web-mode",
                    text = if (desktopMode) "切换为手机网页" else "切换为电脑网页",
                    iconRes = R.drawable.ic_web_mode,
                    onClick = {
                        moreMenuVisible = false
                        onToggleDesktopMode()
                    }
                )
            ),
            onDismissRequest = { moreMenuVisible = false },
            backdrop = backdrop,
            config = config,
            panelMinWidth = 194.dp
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
