package com.xiaomanjun.sleepdownschedule.feature.settings.about

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.feature.update.AppUpdateDialog
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.couiUpdateCardMaterial
import com.xiaomanjun.sleepdownschedule.glass.couiUpdateCardOutline
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ImmersiveSoftwareUpdateContent(
    version: String,
    installedVersion: String,
    dialog: AppUpdateDialog?,
    releaseNotes: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
    downloadPercent: Int?,
    showVersionNotes: Boolean,
) {
    val top = detailContentTopPadding()
    val bottom = detailContentBottomPadding() + 4.dp
    val wallpaper = if (Build.VERSION.SDK_INT >= 31 && !AppMaterialPreferences.isSuperPerformance) {
        rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "software-update-wallpaper")
    } else null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        val titleArea = (viewport * .52f - top).coerceAtLeast(120.dp)
        val producer = if (wallpaper != null) Modifier.glassBackdropProducer(wallpaper) else Modifier
        Box(Modifier.matchParentSize().then(producer).background(SoftwareUpdateBackground)) {
            Image(painterResource(R.drawable.software_update_wallpaper), null, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color(0x2612152D),
                .50f to Color.Transparent,
                1f to Color(0x4012152D),
            )))
        }
        SharedTransitionLayout(Modifier.matchParentSize()) {
            AnimatedContent(
                targetState = showVersionNotes,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(240, delayMillis = 55)) togetherWith fadeOut(tween(165)) },
                label = "software-update-mode",
            ) { showingNotes ->
                val cardBounds = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("software-update-card"),
                    animatedVisibilityScope = this,
                    boundsTransform = { _, _ -> tween(400) },
                    renderInOverlayDuringTransition = false,
                )
                if (showingNotes) {
                    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = bottom)) {
                        SoftwareUpdateVersionNotes(installedVersion, wallpaper, cardBounds)
                    }
                } else Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = viewport).padding(top = top, bottom = bottom),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                        SoftwareUpdateStatusCard(version, dialog, releaseNotes, primary, secondary, downloadPercent, wallpaper, cardBounds)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoftwareUpdateStatusCard(
    version: String,
    dialog: AppUpdateDialog?,
    releaseNotes: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
    downloadPercent: Int?,
    wallpaper: Backdrop?,
    modifier: Modifier,
) {
    SoftwareUpdateCard(wallpaper, modifier.animateContentSize(tween(285))) {
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
                if (!releaseNotes.isNullOrBlank()) {
                    Text(releaseNotes.trim(), Modifier.padding(top = 12.dp).heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()), color = Color.White.copy(alpha = .84f),
                        style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary))
                }
                val downloading = dialog is AppUpdateDialog.Downloading
                if (primary != null || downloading) {
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val label = primary?.first ?: downloadPercent?.let { "正在下载 · $it%" } ?: "正在下载"
                        val width = if (releaseNotes != null) 296.dp else Dp.Infinity
                        SoftwareUpdateAction(label, width, primary?.second, downloadPercent, lightSurface = true)
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
internal fun SoftwareUpdateVersionNotes(version: String, wallpaper: Backdrop?, modifier: Modifier = Modifier) {
    val sections = rememberBundledReleaseNotes(version)
    var expanded by rememberSaveable { mutableStateOf(true) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(240), label = "update-notes-arrow")
    val headingStyle = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 19.sp, lineHeight = 26.sp)
    val bodyStyle = rememberCouiTextStyle(CouiTextKind.PreferenceValue).copy(fontSize = 15.sp, lineHeight = 23.sp)
    SoftwareUpdateCard(wallpaper, modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("软件版本", color = Color.White, style = headingStyle)
            Text(version, Modifier.padding(top = 5.dp), color = Color.White.copy(alpha = .9f), style = bodyStyle)
            Box(Modifier.padding(top = 18.dp, bottom = 14.dp).fillMaxWidth().height(.5.dp)
                .background(Color.White.copy(alpha = .16f)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("更新说明", color = Color.White, style = headingStyle)
                CouiCapsuleButton(
                    modifier = Modifier.width(36.dp), height = 36.dp,
                    onClick = { expanded = !expanded }, dark = true, colors = SoftwareUpdateToolbarColors,
                    contentPadding = PaddingValues(0.dp), pressFeedback = true,
                ) {
                    Icon(Icons.Rounded.ExpandMore, if (expanded) "收起更新说明" else "展开更新说明",
                        Modifier.size(23.dp).rotate(arrowRotation), tint = Color.White)
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(310), expandFrom = Alignment.Top) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(265), shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
            ) {
                Column(Modifier.padding(top = 7.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (sections.isEmpty()) {
                        Text("此版本暂无内置更新说明。", color = Color.White.copy(alpha = .87f), style = bodyStyle)
                    }
                    sections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(section.title, color = Color.White.copy(alpha = .78f), style = headingStyle.copy(fontSize = 17.sp))
                            section.entries.forEach { entry ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("•", color = Color.White.copy(alpha = .87f), style = bodyStyle)
                                    Text(entry, Modifier.weight(1f), color = Color.White.copy(alpha = .87f), style = bodyStyle)
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
private fun SoftwareUpdateCard(wallpaper: Backdrop?, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = CouiShape(24.dp)
    val material = if (Build.VERSION.SDK_INT >= 31 && wallpaper != null) couiUpdateCardMaterial(wallpaper, shape)
        else Modifier
    val panelTint = if (wallpaper != null) Color(0x97151627) else Color(0xF0222337)
    Box(modifier.fillMaxWidth().clip(shape)) {
        if (wallpaper != null) Box(Modifier.matchParentSize().then(material))
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
