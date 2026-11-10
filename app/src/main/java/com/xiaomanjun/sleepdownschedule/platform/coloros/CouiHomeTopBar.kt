package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.topappbar.TopAppBarButton
import com.itos.coui.theme.CouiTheme
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.homeActionIconPainter
import com.xiaomanjun.sleepdownschedule.glass.LocalHomeToolbarBackdrop

/** Set only inside the home top bar, so ordinary page icon buttons keep their existing route. */
internal val LocalCouiHomeTopBarActive = staticCompositionLocalOf { false }

/** 系统 Toolbar 按钮；保留宿主 42dp 尺寸与锚点，只消费下层顶栏背景。 */
@RequiresApi(34)
@Composable
internal fun CouiHomeIconButton(
    iconRes: Int,
    contentDescription: String,
    dark: Boolean,
    interactive: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val scheme = CouiTheme.colorSchemeFor(dark)
    val content = if (selected) MaterialTheme.colorScheme.primary else scheme.onSurface
    val defaults = CouiTopAppBarButtonDefaults.style(dark)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val style = defaults.copy(
        material = { capsule ->
            CouiTopAppBarButtonDefaults.systemMaterial(dark, capsule).copy(blurRadius = 20f)
        },
        shadow = { capsule -> CouiTopAppBarButtonDefaults.shadow(dark, capsule, density,
            defaults.contourGlowEnabled, alphaScale = 1f) },
        contentColor = content,
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        TopAppBarButton(
            modifier = Modifier.size(42.dp),
            onClick = onClick,
            enabled = enabled && interactive,
            style = style,
            backdrop = checkNotNull(LocalHomeToolbarBackdrop.current),
        ) {
            Icon(
                homeActionIconPainter(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = if (enabled && interactive) content else content.copy(alpha = content.alpha * .38f),
            )
        }
    }
}
