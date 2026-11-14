package com.itos.coui.button

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/** An action that becomes a COUI progress capsule while its work runs. */
@Composable
fun CouiProgressButton(
    label: String,
    onClick: (() -> Unit)?,
    dark: Boolean,
    colors: CouiButtonColors,
    trackContentColor: Color,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    textKind: CouiTextKind = CouiTextKind.Progress,
) {
    val padding = PaddingValues(horizontal = CouiButtonDefaults.horizontalPadding, vertical = 8.dp)
    if (onClick != null) {
        CouiCapsuleButton(modifier, onClick, dark, colors = colors,
            style = CouiButtonStyle.Primary, shape = CouiCapsuleShape,
            height = Dp.Unspecified, contentPadding = padding, pressFeedback = true) {
            Text(text = label, kind = textKind, color = colors.content)
        }
        return
    }

    val fraction = progress?.coerceIn(0f, 1f)
    val sweep = if (fraction == null) {
        rememberInfiniteTransition(label = "COUI download sweep").animateFloat(
            initialValue = -0.26f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "COUI indeterminate progress",
        )
    } else null
    val progressModifier = fraction?.let { Modifier.progressSemantics(it) } ?: Modifier.progressSemantics()
    Box(modifier.heightIn(min = CouiButtonDefaults.minHeight)
        .clip(CouiCapsuleShape).then(progressModifier)
        .couiButtonSurface(dark, enabled = false, surfaceColor = colors.disabledSurface,
            style = CouiButtonStyle.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(Modifier.padding(padding), label, textKind, color = trackContentColor)
        Box(Modifier.matchParentSize().drawWithContent {
            val start = sweep?.value ?: 0f
            val end = fraction ?: (start + 0.26f)
            val rtl = layoutDirection == LayoutDirection.Rtl
            clipRect(
                left = size.width * (if (rtl) 1f - end else start).coerceIn(0f, 1f),
                right = size.width * (if (rtl) 1f - start else end).coerceIn(0f, 1f),
            ) { this@drawWithContent.drawContent() }
        }.background(colors.surface), contentAlignment = Alignment.Center) {
            Text(Modifier.padding(padding).clearAndSetSemantics {}, label, textKind, color = colors.content)
        }
    }
}
