package com.itos.coui.text

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.itos.coui.theme.CouiTheme
import kotlin.math.roundToInt

/** COUI 语义排版预设；宿主可通过 rememberCouiTextStyle 用于 Compose 文本与编辑框。 */
enum class CouiTextKind(internal val size: Float, internal val family: String, internal val weight: Int) {
    PreferenceTitle(16f, "sans-serif-medium", 500),
    PreferenceSummary(13f, "sans-serif", 400),
    Category(12f, "sans-serif", 400),
    Toolbar(18f, "sans-serif-medium", 550),
    PageTitle(24f, "sans-serif-semibold", 600),
    SectionTitle(18f, "sans-serif-medium", 600),
    Body(15f, "sans-serif", 400),
    HomeTitle(40f, "sans-serif-semibold", 700),
    Search(15f, "sans-serif", 400),
    SearchCancel(14f, "sans-serif-medium", 500),
    PreferenceValue(14f, "sans-serif", 400),
    GroupTitle(12f, "sans-serif-medium", 500),
    Stepper(18f, "sans-serif-medium", 500),
    Progress(14f, "sans-serif-medium", 500),
    Description(12f, "sans-serif", 400),
}

/** 系统资源决定排版；正文与说明默认起始对齐，行数和溢出交给 BasicText 处理。 */
@Composable
fun Text(modifier: Modifier = Modifier, text: String, kind: CouiTextKind, color: Color = Color.Unspecified,
    onLineCount: (Int) -> Unit = {}, textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
    val style = rememberCouiTextStyle(kind)
    BasicText(text, modifier,
        style = style.copy(color = color.takeOrElse { CouiTheme.contentColor },
            textAlign = textAlign ?: when (kind) {
                CouiTextKind.PreferenceTitle, CouiTextKind.PreferenceSummary,
                CouiTextKind.Body, CouiTextKind.Description -> TextAlign.Start
                else -> TextAlign.Center
            }),
        onTextLayout = { onLineCount(it.lineCount) }, maxLines = maxLines, overflow = overflow)
}

@Composable
fun rememberCouiTextStyle(kind: CouiTextKind): TextStyle {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(kind, configuration, density) {
        val family = Typeface.create(kind.family, Typeface.NORMAL)
        val typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(family, kind.weight, false)
            else Typeface.create(family, if (kind.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        val fixedDp = kind == CouiTextKind.Toolbar || kind == CouiTextKind.PageTitle || kind == CouiTextKind.PreferenceValue
        val pixels = if (fixedDp) kind.size * density.density else couiTextSize(kind, configuration) { with(density) { it.sp.toPx() } }
        couiTextStyle(typeface, kind.weight, pixels, couiLineSpacing(kind),
            includeFontPadding = kind != CouiTextKind.Toolbar && kind != CouiTextKind.PageTitle && kind != CouiTextKind.HomeTitle,
            density = density)
    }
}

private fun couiTextSize(kind: CouiTextKind, configuration: Configuration, spToPx: (Float) -> Float): Float {
    val pixels = spToPx(kind.size).roundToInt().toFloat()
    if (kind == CouiTextKind.SearchCancel) {
        val base = (pixels / configuration.fontScale).roundToInt()
        return base * if (configuration.fontScale < 1.15f) 1f else 1.15f
    }
    return pixels
}

private fun couiLineSpacing(kind: CouiTextKind): Float = when (kind) {
    CouiTextKind.Category -> 1f
    CouiTextKind.GroupTitle -> if (Build.VERSION.SDK_INT >= 35) 1.143f else 1f
    CouiTextKind.Description -> if (Build.VERSION.SDK_INT >= 35) 1.143f else 1f
    CouiTextKind.Stepper -> if (Build.VERSION.SDK_INT >= 35) 1.1429f else 1.15f
    CouiTextKind.Progress -> if (Build.VERSION.SDK_INT >= 35) 1.2245f else 1.1f
    CouiTextKind.PreferenceTitle -> if (Build.VERSION.SDK_INT >= 35) 1.158f else 1.05f
    CouiTextKind.Toolbar -> if (Build.VERSION.SDK_INT >= 35) 1.1429f else 1.15f
    CouiTextKind.PreferenceSummary -> if (Build.VERSION.SDK_INT >= 35) 1.1739f else 1.1f
    CouiTextKind.PageTitle -> 1f
    CouiTextKind.SectionTitle -> if (Build.VERSION.SDK_INT >= 35) 1.1429f else 1.15f
    CouiTextKind.Body -> 1.1321f
    CouiTextKind.HomeTitle -> 1f
    CouiTextKind.Search -> if (Build.VERSION.SDK_INT >= 35) 1.1321f else 1.1f
    CouiTextKind.SearchCancel -> if (Build.VERSION.SDK_INT >= 35) 1.2245f else 1.1f
    CouiTextKind.PreferenceValue -> if (Build.VERSION.SDK_INT >= 35) 1.2245f else 1.1f
}
