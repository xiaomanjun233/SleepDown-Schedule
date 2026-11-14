package com.xiaomanjun.sleepdownschedule.feature.settings.about

import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.feature.update.AppUpdateDialog
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeReleaseInfo
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.couiUpdateCardMaterial
import com.xiaomanjun.sleepdownschedule.glass.couiUpdateCardOutline
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import kotlin.math.roundToInt

@Composable
internal fun ImmersiveSoftwareUpdateContent(
    version: String,
    dialog: AppUpdateDialog?,
    release: GiteeReleaseInfo?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
    showVersionNotes: Boolean,
    footerClearance: Dp,
) {
    val top = detailContentTopPadding()
    val bottom = detailContentBottomPadding() + 4.dp + footerClearance
    val wallpaper = if (Build.VERSION.SDK_INT >= 31 && !AppMaterialPreferences.isSuperPerformance) {
        rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "software-update-wallpaper")
    } else null
    val statusScroll = rememberScrollState()
    val notesScroll = rememberScrollState()
    val statusProgress = animateFloatAsState(if (showVersionNotes) 0f else 1f, tween(360), label = "update-card-position")
    val placement = remember {
        object : Arrangement.Vertical {
            override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
                outPositions[0] = 0
                if (sizes.size == 2) {
                    val space = (totalSize - sizes[0] - sizes[1]).coerceAtLeast(0)
                    outPositions[1] = sizes[0] + (space * statusProgress.value).roundToInt()
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        val titleArea = (viewport * .52f - top).coerceAtLeast(120.dp)
        val producer = if (wallpaper != null) Modifier.glassBackdropProducer(wallpaper) else Modifier
        val density = LocalDensity.current
        val wallpaperEffect = if (Build.VERSION.SDK_INT >= 31 && wallpaper != null && viewport > 0.dp) {
            remember(viewport, density) {
                with(density) { softwareUpdateWallpaperBlur(viewport.toPx(), 26.dp.toPx()) }
            }
        } else null
        Box(Modifier.matchParentSize().then(producer).background(SoftwareUpdateBackground)) {
            val wallpaperModifier = if (wallpaperEffect == null) Modifier
                else Modifier.graphicsLayer { renderEffect = wallpaperEffect }
            Image(painterResource(R.drawable.software_update_wallpaper), null,
                Modifier.matchParentSize().then(wallpaperModifier),
                contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color(0x2612152D),
                .50f to Color.Transparent,
                1f to Color(0x4012152D),
            )))
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(if (showVersionNotes) notesScroll else statusScroll)
                .heightIn(min = viewport).padding(top = top, bottom = bottom),
            verticalArrangement = placement,
        ) {
            AnimatedVisibility(
                visible = !showVersionNotes,
                enter = expandVertically(tween(360), expandFrom = Alignment.Top) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(360), shrinkTowards = Alignment.Top) + fadeOut(tween(165)),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = titleArea).padding(horizontal = 24.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(version.substringBefore('_').substringBefore('-'), color = Color.White.copy(alpha = .9f),
                            textAlign = TextAlign.Center,
                            style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 22.sp, lineHeight = 28.sp))
                        Text(SleepDownFullName, color = Color.White, textAlign = TextAlign.Center,
                            style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 30.sp, lineHeight = 36.sp))
                    }
                }
            }
            SoftwareUpdateCard(
                wallpaper, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                sampleWallpaper = !showVersionNotes,
            ) {
                AnimatedContent(
                    targetState = showVersionNotes,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(230, delayMillis = 65)) togetherWith fadeOut(tween(155)))
                            .using(SizeTransform { _, _ -> tween(360) })
                    },
                    label = "software-update-card-content",
                ) { showingNotes ->
                    if (showingNotes) SoftwareUpdateVersionNotes(version, release?.notes,
                        downloadError = (dialog as? AppUpdateDialog.Error)?.message.takeIf { release != null })
                    else SoftwareUpdateStatus(version, dialog, primary, secondary)
                }
            }
        }
        if (showVersionNotes) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 8.dp).drawWithCache {
                val fade = Brush.verticalGradient(
                    0f to SoftwareUpdateBackground,
                    .82f to SoftwareUpdateBackground.copy(alpha = .93f),
                    1f to Color.Transparent,
                )
                onDrawBehind {
                    if (notesScroll.value > 0) drawRect(fade)
                }
            })
        }
    }
}

@androidx.annotation.RequiresApi(31)
private fun softwareUpdateWallpaperBlur(height: Float, radius: Float): androidx.compose.ui.graphics.RenderEffect {
    val mask = LinearGradient(0f, height * .52f, 0f, height,
        intArrayOf(0x00FFFFFF, 0x38FFFFFF, 0x9AFFFFFF.toInt(), -1),
        floatArrayOf(0f, .28f, .68f, 1f), Shader.TileMode.CLAMP)
    val blurred = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
    val fadedBlur = RenderEffect.createBlendModeEffect(blurred,
        RenderEffect.createShaderEffect(mask), BlendMode.DST_IN)
    return RenderEffect.createBlendModeEffect(RenderEffect.createOffsetEffect(0f, 0f),
        fadedBlur, BlendMode.SRC_OVER).asComposeRenderEffect()
}

@Composable
private fun SoftwareUpdateStatus(
    version: String,
    dialog: AppUpdateDialog?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
) {
    Box(Modifier.fillMaxWidth().animateContentSize(tween(285))) {
        if (dialog == AppUpdateDialog.Checking) {
            SoftwareUpdateChecking(Color.White, Modifier.fillMaxWidth())
        } else {
            val (heading, summary) = when (dialog) {
                is AppUpdateDialog.UpToDate -> "已是最新版本" to version
                is AppUpdateDialog.Available -> "发现新版本" to version
                is AppUpdateDialog.Downloading -> "正在下载" to version
                is AppUpdateDialog.Error -> "检查更新失败" to dialog.message
                is AppUpdateDialog.NoApk -> "本次发布未附带安装包" to dialog.release.name
                AppUpdateDialog.InstallPermissionRequired -> "安装包已下载" to "允许安装此来源的应用后继续。"
                else -> "软件版本" to version
            }
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(heading, color = Color.White,
                    style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 20.sp, lineHeight = 28.sp))
                Text(summary, Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = .9f),
                    style = rememberCouiTextStyle(CouiTextKind.PreferenceValue))
                if (primary != null || secondary != null) {
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        primary?.let { (label, action) ->
                            SoftwareUpdateAction(label, action, Modifier.fillMaxWidth(), lightSurface = true)
                        }
                        secondary?.let { (label, action) ->
                            SoftwareUpdatePanelButton(label, action)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SoftwareUpdateVersionNotes(
    version: String, releaseNotes: String? = null, lightFrame: Boolean = false, downloadError: String? = null,
) {
    val sections = if (releaseNotes != null) remember(releaseNotes) { parseReleaseNotes(releaseNotes.lines()) }
        else rememberBundledReleaseNotes(version)
    var expanded by rememberSaveable(version) { mutableStateOf(true) }
    val colors = CouiTheme.colorScheme
    val foreground = if (lightFrame) colors.onSurface else Color.White
    val body = if (lightFrame) colors.onSurface.copy(alpha = .84f) else Color.White.copy(alpha = .87f)
    val secondary = if (lightFrame) colors.onSurfaceVariant else Color.White.copy(alpha = .78f)
    val divider = if (lightFrame) colors.outlineVariant else Color.White.copy(alpha = .16f)
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(240), label = "update-notes-arrow")
    val headingStyle = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 19.sp, lineHeight = 26.sp)
    val bodyStyle = rememberCouiTextStyle(CouiTextKind.PreferenceValue).copy(fontSize = 15.sp, lineHeight = 23.sp)
    Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Text("软件版本", color = foreground, style = headingStyle)
        Text(version, Modifier.padding(top = 5.dp), color = body, style = bodyStyle)
        if (downloadError != null) Text(downloadError, Modifier.padding(top = 8.dp), color = body, style = bodyStyle)
        Box(Modifier.padding(top = 18.dp, bottom = 14.dp).fillMaxWidth().height(.5.dp)
            .background(divider))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("更新说明", color = foreground, style = headingStyle)
            CouiCapsuleButton(
                modifier = Modifier.width(36.dp), height = 36.dp,
                onClick = { expanded = !expanded }, dark = !lightFrame,
                colors = if (lightFrame) CouiButtonDefaults.secondaryColors(dark = false) else SoftwareUpdateToolbarColors,
                contentPadding = PaddingValues(0.dp), pressFeedback = true,
            ) {
                Icon(Icons.Rounded.ExpandMore, if (expanded) "收起更新说明" else "展开更新说明",
                    Modifier.size(23.dp).rotate(arrowRotation), tint = foreground)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(310), expandFrom = Alignment.Top) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(265), shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
        ) {
            Column(Modifier.padding(top = 7.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (sections.isEmpty()) {
                    Text("此版本暂无更新说明。", color = body, style = bodyStyle)
                }
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (section.title.isNotEmpty()) {
                            Text(section.title, color = secondary, style = headingStyle.copy(fontSize = 17.sp))
                        }
                        section.entries.forEach { entry ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("•", color = body, style = bodyStyle)
                                Text(entry, Modifier.weight(1f), color = body, style = bodyStyle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SoftwareUpdateCard(
    wallpaper: Backdrop?,
    modifier: Modifier,
    sampleWallpaper: Boolean = true,
    lightFrame: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = CouiShape(24.dp)
    if (lightFrame) {
        val colors = CouiTheme.colorScheme
        Box(modifier.fillMaxWidth().clip(shape).background(colors.surface).border(0.5.dp, colors.outlineVariant, shape),
            content = content)
        return
    }
    // The tall notes use the already blurred wallpaper under the card tint.
    val material = if (Build.VERSION.SDK_INT >= 31 && wallpaper != null && sampleWallpaper) couiUpdateCardMaterial(wallpaper, shape)
        else Modifier
    val panelTint = if (wallpaper != null) Color(0x97151627) else Color(0xF0222337)
    Box(modifier.fillMaxWidth().clip(shape)) {
        if (material != Modifier) Box(Modifier.matchParentSize().then(material))
        Box(Modifier.matchParentSize().background(panelTint).then(couiUpdateCardOutline(shape)))
        content()
    }
}

@Composable
private fun SoftwareUpdatePanelButton(label: String, onClick: () -> Unit) {
    CouiCapsuleButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick, dark = true, height = 44.dp,
        colors = CouiButtonDefaults.secondaryColors(dark = true, material = true).copy(
            surface = Color.White.copy(alpha = .16f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        pressFeedback = true,
    ) {
        Text(label, color = Color.White, style = rememberCouiTextStyle(CouiTextKind.Button))
    }
}
