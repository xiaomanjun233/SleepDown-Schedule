package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
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
    onSoftwareUpdate: () -> Unit, onHistory: () -> Unit,
) {
    val context = LocalContext.current
    val width = LocalConfiguration.current.screenWidthDp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorScheme
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: BuildConfig.VERSION_NAME
    }
    val available by GiteeAppUpdater.updateAvailable.collectAsStateWithLifecycle()
    val browser = remember(context) { context.resolveSleepDownCustomTabsPackage() }
    fun open(url: String) = context.openSleepDownCustomTab(url, providerPackage = browser,
        initialWidthPx = with(density) { (width.dp * .68f).roundToPx() }, darkTheme = dark)
    LaunchedEffect(version) { GiteeAppUpdater.restoreCachedStatus(context, version) }

    SleepDownSecondaryPageList(contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = detailContentBottomPadding()) {
        item(key = "about-version-and-update") {
            AboutVersionHeader(version, if (available) "发现新版本" else "检查更新",
                enabled = true, onCheck = onSoftwareUpdate)
        }
        item(key = "about-app-information") {
            SettingsGroup(backdrop, state.config) {
                SettingsNavigationRow("GitHub", "github.com/xiaomanjun233", onClick = { open(SleepDownDeveloperUrl) })
                SettingsDivider()
                SettingsNavigationRow("更新日志", "查看各版本的改进与修复", onClick = onHistory)
            }
        }
        item(key = "about-links") {
            SettingsGroup(backdrop, state.config) {
                SettingsNavigationRow("官方网站", onClick = { open(SleepDownWebsiteUrl) })
                SettingsDivider()
                SettingsNavigationRow("源代码", onClick = { open(SleepDownSourceUrl) })
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
                    SettingsNavigationRow("AndroidLiquidGlass", "Kyant0 · 液态玻璃", onClick = { open(AndroidLiquidGlassUrl) })
                    SettingsDivider()
                    SettingsNavigationRow("MIUIX", "compose-miuix-ui · Popup", onClick = { open(MiuixUrl) })
                    SettingsDivider()
                    SettingsNavigationRow("COUI-Kit", "ItosEO · MPL 2.0", onClick = { open("https://github.com/ItosEO/COUI-Kit") })
                    SettingsDivider()
                    SettingsNavigationRow("shiguang_warehouse", "XingHeYuZhuan · 教务适配", onClick = { open(ShiguangWarehouseUrl) })
                }
            }
        }
        item(key = "about-tagline") {
            Text("把课程，安排得刚刚好", Modifier.fillMaxWidth().padding(vertical = 24.dp),
                style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary), color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

internal const val SleepDownFullName = "SleepDown课程表"
internal const val SleepDownDeveloperUrl = "https://github.com/xiaomanjun233"

@Composable
private fun AboutVersionHeader(version: String, action: String, enabled: Boolean, onCheck: () -> Unit) {
    val dark = CouiTheme.isDark
    val foreground = if (dark) Color.White else Color(0xFF15243D)
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 232.dp)
        .clip(CouiShape(24.dp, 2.5f)).background(CouiTheme.colorScheme.container), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.about_sleepdown_hero), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(if (dark)
            listOf(Color.Black.copy(alpha = .28f), Color.Black.copy(alpha = .48f))
            else listOf(Color.White.copy(alpha = .08f), Color.White.copy(alpha = .18f)))))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SleepDownFullName, style = rememberCouiTextStyle(CouiTextKind.PageTitle).copy(
                fontSize = 28.sp, fontWeight = FontWeight.SemiBold), color = foreground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(version, style = rememberCouiTextStyle(CouiTextKind.PreferenceValue), color = foreground.copy(alpha = .8f))
            Spacer(Modifier.height(32.dp))
            Row(Modifier.heightIn(min = 40.dp).clip(CouiShape(20.dp, 2.5f))
                .background(foreground.copy(alpha = .1f)).couiButtonFeedback(dark, interaction, enabled)
                .clickable(interaction, null, enabled, role = Role.Button, onClick = onCheck)
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(action, style = rememberCouiTextStyle(CouiTextKind.PreferenceValue).copy(fontWeight = FontWeight.Medium),
                    color = foreground.copy(alpha = if (enabled) 1f else .6f))
                Icon(CouiIcons.Next, null, Modifier.size(10.dp, 20.dp), tint = foreground)
            }
        }
    }
}
