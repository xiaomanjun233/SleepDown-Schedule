package com.itos.coui.segmentbutton

import android.graphics.BlendMode
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.text.couiAdaptFontSize
import com.itos.coui.text.couiTextStyle
import kotlin.math.roundToInt

object CouiSegmentedControlDefaults {
    val height = 40.dp
    val smallHeight = 32.dp
    internal val padding = 4.dp
    internal val smallPadding = 2.dp
    internal val minWidth = 52.dp
    internal val labelPadding = 12.dp
    internal val switchDistance = 5.dp

    /** TYPE_CONTENT_SEGMENT_BUTTON 与 CAPSULE_6；不包含选中指示器。 */
    fun material(dark: Boolean) = CouiFloatingMaterial(
        firstColor = Color(if (dark) 0x7F7F7F7F else 0x7F333333),
        firstBlendMode = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE,
        secondColor = Color(if (dark) 0x99262626 else 0x99EFEFEF),
        secondBlendMode = BlendMode.LUMINOSITY,
        blurRadius = 25f, strokeType = 1,
        edgeAlpha = if (dark) .8f else .6f,
        innerGlowAlpha = if (dark) .05f else .15f,
        innerGlowScale = if (dark) 1f else 1.15f,
    )

    /** 原厂 couiTextButtonM，不借用通用按钮或警告弹窗的字号。 */
    @Composable
    fun textStyle(): TextStyle {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        return remember(configuration, density) {
            val typeface = Typeface.create(Typeface.create("sans-serif-medium", Typeface.NORMAL), 500, false)
            val pixels = couiAdaptFontSize(with(density) { 14.sp.toPx() }.roundToInt().toFloat(), configuration, 1.35f)
            couiTextStyle(typeface, 500, pixels, if (Build.VERSION.SDK_INT >= 35) 1.2245f else 1.1f,
                includeFontPadding = false, density = density).copy(letterSpacing = 0.sp)
        }
    }
}
