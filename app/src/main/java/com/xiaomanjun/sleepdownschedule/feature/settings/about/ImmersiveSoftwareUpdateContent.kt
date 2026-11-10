package com.xiaomanjun.sleepdownschedule.feature.settings.about

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop

@Composable
internal fun ImmersiveSoftwareUpdateContent(
    version: String,
    dialog: AppUpdateDialog?,
    releaseNotes: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
    downloadPercent: Int?,
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
        Box(Modifier.matchParentSize().then(producer).background(Color(0xFF12152D))) {
            Image(painterResource(R.drawable.software_update_wallpaper), null, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color(0x2612152D),
                .50f to Color.Transparent,
                1f to Color(0x4012152D),
            )))
        }
        Column(
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
                SoftwareUpdateStatusCard(version, dialog, releaseNotes, primary, secondary, downloadPercent, wallpaper)
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
) {
    val shape = CouiShape(24.dp, 2.85f)
    val material = if (Build.VERSION.SDK_INT >= 31 && wallpaper != null) {
        couiUpdateCardMaterial(wallpaper, shape)
    } else Modifier
    val panelTint = if (wallpaper != null) Color(0x97151627) else Color(0xEB222337)
    Box(Modifier.fillMaxWidth().clip(shape).border(0.75.dp, Color.White.copy(alpha = .27f), shape)) {
        if (wallpaper != null) Box(Modifier.matchParentSize().then(material))
        Box(Modifier.matchParentSize().background(panelTint))
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
private fun SoftwareUpdatePanelButton(label: String, onClick: () -> Unit) {
    CouiCapsuleButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick, dark = true, height = 44.dp,
        colors = CouiButtonDefaults.secondaryColors(dark = true, material = true).copy(
            surface = Color.White.copy(alpha = .16f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, style = rememberCouiTextStyle(CouiTextKind.Button))
    }
}
