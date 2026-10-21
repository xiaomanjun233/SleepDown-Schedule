package com.xiaomanjun.sleepdownschedule.feature.settings.about

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.identity.currentIconResId
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsNavigationRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsValueRow
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBackdropBlur
import com.xiaomanjun.sleepdownschedule.glass.ui.ProgressiveBlurDirection
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
fun AboutSettingsScreen(state: AppState, backdrop: Backdrop?) {
    val context = LocalContext.current
    val topPadding = detailContentTopPadding()
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME }
            .getOrDefault(BuildConfig.VERSION_NAME)
    }
    fun openProjectPage(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            Toast.makeText(context, "暂时无法打开这个页面", Toast.LENGTH_SHORT).show()
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = DockScrollPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "about-hero") {
            SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(currentIconResId(context, appUsesDarkTheme(state.config))),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedRectangle(18.dp))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "SleepDown 课程表",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "把课程，安排得刚刚好",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "版本 $versionName · 小漫君独立设计与开发",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item(key = "about-capabilities") {
            GlassPreferenceSection("你可以用它做什么") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "今天有重点，一周有全貌",
                        "日视图突出今天和下一节课，周视图完整展开一周安排；不同学期或用途的课表也可以分开管理。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "课表从哪里来，都先让你确认",
                        "可以手动添加，也可以从教务系统、ICS、文本、表格、图片或 PDF 整理课表，导入结果会先给你预览。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "会回答，也会动手，但最后由你做主",
                        "AI 助理可以查询课程和空闲时间；涉及课程或设置修改时，会先说明要改什么，再等你确认。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "你的课表，当然该有你的样子",
                        "壁纸取景、课程卡片、玻璃效果、深色外观、提醒和桌面小组件都可以按需要调整。"
                    )
                }
            }
        }
        item(key = "about-privacy") {
            GlassPreferenceSection("数据与隐私") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "基础数据留在本机",
                        "课表、设置、壁纸取景和助手记忆默认保存在应用自己的空间里，日常查看和编辑不需要上传课程。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "选择文件时，由 Android 系统把关",
                        "应用只会读取你在系统文件选择器中主动选中的文件，不会自行浏览其他文件。"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        "联网功能由你开启",
                        "AI、天气和版本检查只在使用时联网。使用第三方服务前，请留意对方的隐私政策、计费和数据处理方式。"
                    )
                }
            }
        }
        item(key = "about-project") {
            GlassPreferenceSection("项目信息") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsValueRow("开发与维护", "小漫君 / xiaomanjun233")
                    SettingsDivider()
                    SettingsValueRow("当前版本", versionName)
                    SettingsDivider()
                    SettingsValueRow("系统要求", "Android 8.0 及以上")
                    SettingsDivider()
                    SettingsInfoRow(
                        "项目许可",
                        "采用 SleepDown 署名非商业、源码可见许可 1.1；这是源码可见项目，不是 OSI 定义的开源软件。"
                    )
                }
            }
        }
        item(key = "about-links") {
            GlassPreferenceSection("了解与反馈") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        "项目官网",
                        "查看完整功能、截图、隐私说明和下载入口",
                        onClick = { openProjectPage(SleepDownWebsiteUrl) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "查看项目源码",
                        "前往 GitHub 查看代码、版本与开发记录",
                        onClick = { openProjectPage(SleepDownSourceUrl) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "问题反馈",
                        "遇到问题或有建议时，前往 GitHub Issues",
                        onClick = { openProjectPage(SleepDownIssuesUrl) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        "查看完整许可",
                        "了解使用、修改、署名和分发要求",
                        onClick = { openProjectPage(SleepDownLicenseUrl) }
                    )
                }
            }
        }
        item(key = "about-credits") {
            GlassPreferenceSection("项目致谢") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsValueRow("液态玻璃效果", "AndroidLiquidGlass · Kyant")
                    SettingsDivider()
                    SettingsValueRow("设置与界面组件", "MIUIX · compose-miuix-ui")
                    SettingsDivider()
                    SettingsValueRow("教务适配资源", "shiguang_warehouse · XingHeYuZhuan")
                }
            }
        }
    }
}

internal const val SleepDownWebsiteUrl = "https://xiaomanjun233.github.io/SleepDown-Schedule/"
internal const val SleepDownSourceUrl = "https://github.com/xiaomanjun233/SleepDown-Schedule"
internal const val SleepDownReleasesUrl = "$SleepDownSourceUrl/releases"
internal const val SleepDownIssuesUrl = "$SleepDownSourceUrl/issues"
private const val SleepDownLicenseUrl = "$SleepDownSourceUrl/blob/main/LICENSE.md"
internal const val AndroidLiquidGlassUrl = "https://github.com/Kyant0/AndroidLiquidGlass"
internal const val MiuixUrl = "https://github.com/compose-miuix-ui/miuix"
internal const val ShiguangWarehouseUrl = "https://github.com/xingheyuzhuan/shiguang_warehouse"
internal val AboutHeroHeight = 390.dp

@Composable
internal fun AboutGlassPanel(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    longContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedRectangle(28.dp)
    val panelGradient = if (darkTheme) {
        Brush.linearGradient(
            listOf(
                ComposeColor(0xFF244987).copy(alpha = 0.60f),
                ComposeColor(0xFF383C80).copy(alpha = 0.54f),
                ComposeColor(0xFF562D69).copy(alpha = 0.58f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                ComposeColor(0xFFFFF5FA),
                ComposeColor(0xFFFAF3FC),
                ComposeColor(0xFFF3F4FD)
            )
        )
    }
    Column(
        modifier = modifier
            .then(if (longContent) Modifier.drawWithCache {
                val path = androidx.compose.ui.graphics.Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                }
                onDrawWithContent {
                    clipPath(path) { this@onDrawWithContent.drawContent() }
                }
            } else Modifier.clip(shape))
            .background(panelGradient),
        content = content
    )
}

@Composable
internal fun AboutSectionHeading(
    title: String,
    summary: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
internal fun AboutFeatureCard(
    imageRes: Int,
    eyebrow: String,
    title: String
) {
    val imageBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.Content,
        providerId = "about-feature-image"
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wideCard = maxWidth >= 560.dp
        val cardRatio = if (wideCard) 16f / 8.5f else 1.08f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardRatio)
                .squircleClip(28.dp)
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdropProducer(imageBackdrop),
                contentScale = ContentScale.Crop
            )
            ProgressiveBackdropBlur(
                backdrop = imageBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
                tintColor = ComposeColor.Black,
                height = if (wideCard) 108.dp else 120.dp,
                blurRadius = 7.dp,
                tintIntensity = 0.07f,
                direction = ProgressiveBlurDirection.BottomToTop,
                topMaskFadeStart = 0.18f,
                topMaskFadeEnd = 0.96f,
                topTintFadeStart = 0.14f,
                topTintFadeEnd = 0.98f,
                fallbackTintStops = listOf(
                    0f to ComposeColor.Black.copy(alpha = 0.52f),
                    0.56f to ComposeColor.Black.copy(alpha = 0.20f),
                    1f to ComposeColor.Transparent
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to ComposeColor.Black.copy(alpha = 0.03f),
                                0.62f to ComposeColor.Black.copy(alpha = 0.05f),
                                1f to ComposeColor.Black.copy(alpha = 0.50f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = ComposeColor(0xFF8CCBFF)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = ComposeColor.White
                )
            }
        }
    }
}

@Composable
internal fun AboutHero(
    versionName: String,
    iconResId: Int,
    titleBrush: Brush,
    collapseProgress: State<Float>,
    scrollOffsetPx: State<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 44.dp)
            .graphicsLayer {
                val rawProgress = collapseProgress.value.coerceIn(0f, 1f)
                val rawShrinkProgress = (rawProgress / 0.48f).coerceIn(0f, 1f)
                val shrinkProgress = rawShrinkProgress * rawShrinkProgress * (3f - 2f * rawShrinkProgress)
                val rawFadeProgress = ((rawProgress - 0.36f) / 0.16f).coerceIn(0f, 1f)
                val fadeProgress = rawFadeProgress * rawFadeProgress * (3f - 2f * rawFadeProgress)
                val scale = 1f - 0.28f * shrinkProgress
                scaleX = scale
                scaleY = scale
                translationY = scrollOffsetPx.value * 0.52f + 30.dp.toPx() * shrinkProgress
                alpha = 1f - fadeProgress
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedRectangle(30.dp))
        )
        Spacer(Modifier.height(30.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SleepDown 课程表",
                style = MaterialTheme.typography.displaySmall.copy(brush = titleBrush),
                fontSize = if (maxWidth < 320.dp) 30.sp else 36.sp,
                lineHeight = if (maxWidth < 320.dp) 36.sp else 43.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "把课程，安排得刚刚好",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "版本 $versionName  ·  小漫君独立设计与开发",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f)
        )
    }
}

@Composable
internal fun AboutCreditLinkRow(
    author: String,
    repository: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = author,
            modifier = Modifier.weight(0.43f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.weight(0.57f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = repository,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

internal fun aboutPageGradientColors(darkTheme: Boolean): List<ComposeColor> = if (darkTheme) {
    listOf(ComposeColor(0xFF071A43), ComposeColor(0xFF142D70), ComposeColor(0xFF2C174F), ComposeColor(0xFF08102F))
} else {
    listOf(ComposeColor(0xFFDFE3F8), ComposeColor(0xFFE7E0FC), ComposeColor(0xFFF2DDF7),
        ComposeColor(0xFFF9DDEA), ComposeColor(0xFFEDE5FA))
}
