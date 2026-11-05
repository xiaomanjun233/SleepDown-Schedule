package com.itos.coui.floatingactionbutton

import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.theme.ProvideCouiContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion

@RequiresApi(34)
@Composable
fun Fab(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "FAB 需要显式 backdrop 或 COUI Scaffold 的独立栏位"
    },
    renderer: CouiFloatingRenderer,
    onClick: (() -> Unit)? = null,
    dark: Boolean = CouiTheme.isDark,
    shape: Shape = CouiTopAppBarButtonDefaults.circleShape,
    material: CouiFloatingMaterial? = null,
    shadow: CouiFloatingShadow? = null,
    content: @Composable () -> Unit,
) {
    val contour = LocalCouiContourGlowEnabled.current
    val colors = CouiTheme.colorSchemeFor(dark)
    val resolvedMaterial = material ?: CouiFabDefaults.material(colors.accent, dark)
    val resolvedShadow = shadow ?: CouiFabDefaults.shadow(resolvedMaterial.firstColor, LocalDensity.current.density, contour)
    val contentLayer = rememberGraphicsLayer()
    val motion = rememberCouiTouchMotion(true)
    val press = rememberCouiPressMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = true,
        preset = SpotlightPreset.OpaqueSmall1,
        dark = dark,
    )
    var boundsSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier.size(56.dp).onSizeChanged { boundsSize = it }
            .couiFloatingSurface(backdrop, renderer, resolvedMaterial, resolvedShadow, shape,
                contentLayer, contour) {
                CouiFloatingSurface(
                    Rect(Offset(motion.translationX.value, motion.translationY.value),
                        Size(boundsSize.width.toFloat(), boundsSize.height.toFloat())),
                    motion.scaleX.value + press.scale.value - 1f, motion.scaleY.value + press.scale.value - 1f,
                    cornerWeight = 2f,
                )
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = motion.translationX.value
                translationY = motion.translationY.value
                scaleX = motion.scaleX.value + press.scale.value - 1f
                scaleY = motion.scaleY.value + press.scale.value - 1f
            }.couiTouchMotion(motion, true, cancelOnConsumedMove = false, consumeDrag = true)
                .couiPressMotion(press).then(lightInput)
                .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
                .clip(shape).drawBehind { drawLight?.invoke(this) }
                .couiFabFeedback(dark, interactionSource)
                .then(if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick,
                ) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            ProvideCouiContent(color = if (material == null) colors.onAccent else androidx.compose.ui.graphics.Color.Unspecified,
                content = content)
        }
    }
}
