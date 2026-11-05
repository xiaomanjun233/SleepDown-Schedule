package com.itos.coui.dialog

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.itos.coui.text.couiAdaptFontSize
import com.itos.coui.text.couiTextStyle
import com.itos.coui.theme.CouiTheme

/** 普通弹窗内部排版角色，不建立独立的公共 Typography 组件。 */
internal enum class DialogTextKind(internal val size: Float, internal val family: String, internal val weight: Int) {
    Title(18f, "sans-serif-semibold", 550),
    Message(13f, "sans-serif", 400),
    Button(16f, "sans-serif-medium", 500),
    Input(15f, "sans-serif", 400),
    Counter(10f, "sans-serif", 400),
}

/** 保留原生字号与行距算法；可选主题仅提供对应角色的字体族。 */
@Composable
internal fun DialogText(modifier: Modifier = Modifier, text: String, kind: DialogTextKind, color: Color,
    onLineCount: (Int) -> Unit = {}, textAlign: TextAlign? = null) {
    val lines = if (kind == DialogTextKind.Message) remember(text) { mutableIntStateOf(1) } else null
    val style = rememberDialogTextStyle(kind)
    BasicText(text, modifier.then(if (kind == DialogTextKind.Title) Modifier.semantics { heading() } else Modifier),
        style = style.copy(color = color,
            textAlign = textAlign ?: if ((lines?.intValue ?: 1) > 1) TextAlign.Start else TextAlign.Center),
        maxLines = if (kind == DialogTextKind.Button) 2 else Int.MAX_VALUE,
        overflow = if (kind == DialogTextKind.Button) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = {
            if (lines != null) lines.intValue = it.lineCount
            onLineCount(it.lineCount)
        })
}

@Composable
internal fun rememberDialogTextStyle(kind: DialogTextKind): TextStyle {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val typography = CouiTheme.typography
    val family = when (kind) {
        DialogTextKind.Title -> typography.dialogTitleFontFamily
        DialogTextKind.Message -> typography.dialogMessageFontFamily
        DialogTextKind.Button -> typography.dialogButtonFontFamily
        DialogTextKind.Input -> typography.dialogInputFontFamily
        DialogTextKind.Counter -> typography.dialogCounterFontFamily
    }
    val typeface = if (family != null) {
        // 订阅异步字体结果，使操作布局测量与实际文字绘制同时更新。
        LocalFontFamilyResolver.current.resolve(family, FontWeight(kind.weight), fontSynthesis = FontSynthesis.None).value as Typeface
    } else remember(kind) { Typeface.create(Typeface.create(kind.family, Typeface.NORMAL), kind.weight, false) }
    return remember(kind, configuration, density, typeface) {
        val pixels = couiTextSize(kind, configuration) { with(density) { it.sp.toPx() } }
        couiTextStyle(typeface, kind.weight, pixels, couiLineSpacing(kind, configuration),
            includeFontPadding = true, density = density)
    }
}

private fun couiTextSize(kind: DialogTextKind, configuration: Configuration, spToPx: (Float) -> Float): Float {
    val pixels = spToPx(kind.size).roundToInt().toFloat()
    return if (kind == DialogTextKind.Button) couiAdaptFontSize(pixels, configuration, 1.35f) else pixels
}

private fun couiLineSpacing(kind: DialogTextKind, configuration: Configuration): Float = when (kind) {
    DialogTextKind.Title -> if (configuration.locales[0].language == "my" && configuration.locales[0].country == "MM")
        1.2f else if (Build.VERSION.SDK_INT >= 35) 1.1429f else 1.15f
    DialogTextKind.Message -> if (Build.VERSION.SDK_INT >= 35) 1.1739f else 1.1f
    DialogTextKind.Button -> if (Build.VERSION.SDK_INT >= 35) 1.1579f else 1.2f
    DialogTextKind.Input -> if (Build.VERSION.SDK_INT >= 35) 1.1321f else 1.1f
    DialogTextKind.Counter -> 1f
}
