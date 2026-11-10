package com.xiaomanjun.sleepdownschedule.feature.settings.about

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.couiTransparentButtonMaterial
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop

/** 关于页保留版本入口，更新和历史日志交给独立的全屏页面。 */
@Composable
internal fun ColorOsAboutSettingsScreen(
    state: AppState, backdrop: Backdrop?, onDonate: () -> Unit, onPrivacyPolicy: () -> Unit,
    onSoftwareUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorScheme
    val githubIcon = ImageVector.vectorResource(R.drawable.ic_github)
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: BuildConfig.VERSION_NAME
    }
    val available by GiteeAppUpdater.updateAvailable.collectAsStateWithLifecycle()
    val browser = remember(context) { context.resolveSleepDownCustomTabsPackage() }
    fun open(url: String) = context.openSleepDownCustomTab(url, providerPackage = browser,
        initialWidthPx = with(density) { (configuration.screenWidthDp.dp * .68f).roundToPx() }, darkTheme = dark)
    LaunchedEffect(version) { GiteeAppUpdater.restoreCachedStatus(context, version) }

    SleepDownSecondaryPageList(contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = detailContentBottomPadding(), horizontalPadding = 16.dp) {
        item(key = "about-version-and-update") {
            AboutVersionHeader(version, available, expanded = configuration.smallestScreenWidthDp >= 600,
                onCheck = onSoftwareUpdate)
        }
        item(key = "about-github") {
            SettingsGroup(backdrop, state.config) {
                AboutGitHubRow("关于开发者", "xiaomanjun233", githubIcon) { open(SleepDownDeveloperUrl) }
                SettingsDivider()
                AboutGitHubRow("项目仓库", "SleepDown-Schedule", githubIcon) { open(SleepDownSourceUrl) }
            }
        }
        item(key = "about-links") {
            SettingsGroup(backdrop, state.config) {
                SettingsNavigationRow("官方网站", onClick = { open(SleepDownWebsiteUrl) })
                SettingsDivider()
                SettingsNavigationRow("问题反馈", onClick = { open(SleepDownIssuesUrl) })
                SettingsDivider()
                SettingsNavigationRow("隐私政策", onClick = onPrivacyPolicy)
                SettingsDivider()
                SettingsNavigationRow("开源许可", onClick = { open("$SleepDownSourceUrl/blob/main/LICENSE.md") })
                SettingsDivider()
                SettingsNavigationRow("支持开发者", onClick = onDonate)
            }
        }
        item(key = "about-open-source") {
            GlassPreferenceSection("项目致谢") {
                SettingsGroup(backdrop, state.config) {
                    SettingsNavigationRow("AndroidLiquidGlass", "Kyant0 · 液态玻璃",
                        leadingIcon = githubIcon, leadingIconTint = Color(0xFF0A84FF), minHeight = 52.dp,
                        onClick = { open(AndroidLiquidGlassUrl) })
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow("MIUIX", "compose-miuix-ui · Popup",
                        leadingIcon = githubIcon, leadingIconTint = Color(0xFFFF9500), minHeight = 52.dp,
                        onClick = { open(MiuixUrl) })
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow("COUI-Kit", "ItosEO · MPL 2.0",
                        leadingIcon = githubIcon, leadingIconTint = Color(0xFF5856D6), minHeight = 52.dp,
                        onClick = { open("https://github.com/ItosEO/COUI-Kit") })
                    SettingsDivider(startIndent = 56.dp)
                    SettingsNavigationRow("shiguang_warehouse", "XingHeYuZhuan · 教务适配",
                        leadingIcon = githubIcon, leadingIconTint = Color(0xFF34C759), minHeight = 52.dp,
                        onClick = { open(ShiguangWarehouseUrl) })
                }
            }
        }
        item(key = "about-tagline") {
            Text("把课程，安排得刚刚好", Modifier.fillMaxWidth().padding(vertical = 24.dp),
                style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary), color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

internal const val SleepDownFullName = "SleepDown课程表"
internal const val SleepDownDeveloperUrl = "https://github.com/xiaomanjun233"

@Composable
internal fun VersionCardArtwork(modifier: Modifier) {
    Box(modifier) {
        Image(
            painterResource(R.drawable.software_update_wallpaper), null, Modifier.matchParentSize(),
            alignment = BiasAlignment(0f, .24f), contentScale = ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(Color(0x6612152D)))
    }
}

@Composable
private fun AboutVersionHeader(version: String, updateAvailable: Boolean, expanded: Boolean, onCheck: () -> Unit) {
    val foreground = Color.White
    val density = LocalDensity.current
    val artwork = if (Build.VERSION.SDK_INT >= 31 && !AppMaterialPreferences.isSuperPerformance) {
        rememberGlassLayerBackdrop(GlassBackdropDomain.Background, "about-version-artwork")
    } else null
    val artworkProducer = if (artwork != null) Modifier.glassBackdropProducer(artwork) else Modifier
    val buttonMaterial = if (Build.VERSION.SDK_INT >= 31 && artwork != null) {
        couiTransparentButtonMaterial(artwork, CouiCapsuleShape)
    } else Modifier
    val cardHeight = if (expanded) 240.dp else 328.dp
    // Settings 17 将视频卡 XML 的 XX-Large 24dp 映射为 34dp、权重 3 的平滑轮廓。
    val cardShape = CouiShape(34.dp, 3f)
    val titleTop = if (expanded) 66.dp else 104.dp
    val buttonBottom = if (expanded) 20.dp else 40.dp
    Box(
        Modifier.padding(top = 12.dp).fillMaxWidth()
            .clip(cardShape).background(CouiTheme.colorScheme.container),
    ) {
        VersionCardArtwork(Modifier.matchParentSize().then(artworkProducer))
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = cardHeight)
                .padding(start = 16.dp, top = titleTop, end = 16.dp, bottom = buttonBottom),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-10).dp),
            ) {
                Box(Modifier.widthIn(max = 220.dp).fillMaxWidth().heightIn(min = 60.dp),
                    contentAlignment = Alignment.Center) {
                    Text(SleepDownFullName, color = foreground, textAlign = TextAlign.Center,
                        style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(fontSize = 26.sp, lineHeight = 32.sp))
                }
                Text(version, color = foreground.copy(alpha = .85f), textAlign = TextAlign.Center,
                    style = rememberCouiTextStyle(CouiTextKind.PreferenceTitle).copy(
                        fontSize = with(density) { 16.dp.toSp() }, lineHeight = with(density) { 22.dp.toSp() }))
            }
            CouiCapsuleButton(
                onClick = onCheck,
                dark = true,
                colors = CouiButtonDefaults.secondaryColors(dark = true, material = true).copy(
                    surface = if (artwork != null) Color.Transparent else Color.White.copy(alpha = .20f),
                ),
                height = 40.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = CouiCapsuleShape,
                backgroundModifier = buttonMaterial,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (updateAvailable) "发现新版本" else "检查更新", color = foreground, textAlign = TextAlign.Center,
                        style = rememberCouiTextStyle(CouiTextKind.Progress).copy(
                            fontSize = with(density) { 14.dp.toSp() }, lineHeight = with(density) { 20.dp.toSp() }))
                    if (updateAvailable) {
                        Icon(Icons.Rounded.NorthEast, null, Modifier.size(12.dp), tint = foreground)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutGitHubRow(
    title: String, subtitle: String, githubIcon: ImageVector, onClick: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    SettingsBasicComponent(
        onClick = onClick,
        endActions = { SettingsForwardIndicator(Modifier.padding(start = 4.dp)) },
    ) {
        Text(title, style = rememberCouiTextStyle(CouiTextKind.PreferenceTitle),
            color = colors.onSurface.copy(alpha = .9f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(subtitle, Modifier.weight(1f, fill = false),
                style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary), color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(githubIcon, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
        }
    }
}
