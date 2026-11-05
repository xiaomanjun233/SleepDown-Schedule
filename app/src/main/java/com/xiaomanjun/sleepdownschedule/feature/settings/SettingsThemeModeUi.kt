package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.itos.coui.radiobutton.RadioButton
import androidx.compose.foundation.text.BasicText
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomanjun.sleepdownschedule.HomeStartMode
import com.xiaomanjun.sleepdownschedule.R

/** SVG artwork is bundled as native vectors; each option remains one accessible radio target. */
@Composable
internal fun SettingsThemeModeOptions(
    darkMode: Boolean,
    defaultHomeMode: HomeStartMode,
    onSelected: (Boolean) -> Unit
) {
    val week = defaultHomeMode == HomeStartMode.WEEK
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup()
            .padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 25.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(false, true).forEach { dark ->
            val selected = darkMode == dark
            Column(
                modifier = Modifier.weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(dark) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val preview = when {
                    week && dark -> R.drawable.theme_preview_week_dark
                    week -> R.drawable.theme_preview_week_light
                    dark -> R.drawable.theme_preview_day_dark
                    else -> R.drawable.theme_preview_day_light
                }
                Image(
                    painter = painterResource(preview),
                    contentDescription = null,
                    modifier = Modifier.widthIn(max = 70.dp).fillMaxWidth().aspectRatio(210f / 425f),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(Modifier.height(24.dp))
                BasicText(
                    text = if (dark) "暗色模式" else "亮色模式",
                    style = rememberCouiTextStyle(CouiTextKind.PreferenceValue).copy(
                        fontWeight = FontWeight.Medium,
                        color = if (selected) CouiTheme.colorScheme.accent else CouiTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(Modifier.height(10.dp))
                RadioButton(selected = selected)
            }
        }
    }
}
