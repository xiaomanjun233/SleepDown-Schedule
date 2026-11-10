package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentBottomPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.core.web.openSleepDownCustomTab
import com.xiaomanjun.sleepdownschedule.core.web.resolveSleepDownCustomTabsPackage
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater

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
        item(key = "about-github-links") {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AboutGitHubCard("GitHub 主页", "xiaomanjun233", githubIcon,
                    Modifier.weight(1f).fillMaxHeight()) { open(SleepDownDeveloperUrl) }
                AboutGitHubCard("项目仓库", "SleepDown-Schedule", githubIcon,
                    Modifier.weight(1f).fillMaxHeight()) { open(SleepDownSourceUrl) }
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
                        leadingIcon = githubIcon, leadingIconTint = colors.onSurface,
                        onClick = { open(AndroidLiquidGlassUrl) })
                    SettingsDivider()
                    SettingsNavigationRow("MIUIX", "compose-miuix-ui · Popup",
                        leadingIcon = githubIcon, leadingIconTint = colors.onSurface,
                        onClick = { open(MiuixUrl) })
                    SettingsDivider()
                    SettingsNavigationRow("COUI-Kit", "ItosEO · MPL 2.0",
                        leadingIcon = githubIcon, leadingIconTint = colors.onSurface,
                        onClick = { open("https://github.com/ItosEO/COUI-Kit") })
                    SettingsDivider()
                    SettingsNavigationRow("shiguang_warehouse", "XingHeYuZhuan · 教务适配",
                        leadingIcon = githubIcon, leadingIconTint = colors.onSurface,
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
private fun AboutVersionHeader(version: String, updateAvailable: Boolean, expanded: Boolean, onCheck: () -> Unit) {
    val dark = CouiTheme.isDark
    val foreground = if (dark) Color.White else Color(0xFF15243D)
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    // Settings 的 AboutDeviceOtaUpdatePreference 在绑定时覆盖 XML 的首卡尺寸和定位。
    val cardHeight = if (expanded) 240.dp else 328.dp
    val cardRadius = if (expanded) 24.dp else 28.dp
    val titleTop = if (expanded) 66.dp else 104.dp
    val buttonBottom = if (expanded) 20.dp else 40.dp
    Box(
        Modifier.padding(top = 12.dp, bottom = 16.dp).fillMaxWidth()
            .clip(CouiShape(cardRadius, 2.5f)).background(CouiTheme.colorScheme.container),
    ) {
        Image(painterResource(R.drawable.about_sleepdown_hero), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(if (dark)
            listOf(Color.Black.copy(alpha = .36f), Color.Black.copy(alpha = .60f))
            else listOf(Color.White.copy(alpha = .08f), Color.White.copy(alpha = .18f)))))
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
            Row(
                modifier = Modifier.heightIn(min = 40.dp).clip(CouiCapsuleShape)
                    .background(Color.White.copy(alpha = .20f)).couiButtonFeedback(dark, interaction)
                    .clickable(interaction, null, role = Role.Button, onClick = onCheck)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
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

@Composable
private fun AboutGitHubCard(
    title: String, subtitle: String, githubIcon: ImageVector, modifier: Modifier, onClick: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.clip(CouiShape(20.dp, 2.5f)).background(colors.container)
            .couiButtonFeedback(interactionSource = interaction, list = true)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(githubIcon, null, Modifier.size(24.dp), tint = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(title, style = rememberCouiTextStyle(CouiTextKind.PreferenceTitle), color = colors.onSurface)
        Text(subtitle, style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary), color = colors.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
