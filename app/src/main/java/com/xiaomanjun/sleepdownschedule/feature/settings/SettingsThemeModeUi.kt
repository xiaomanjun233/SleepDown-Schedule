package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
        modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(false, true).forEach { dark ->
            val selected = darkMode == dark
            Column(
                modifier = Modifier.weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(dark) })
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    modifier = Modifier.width(88.dp).aspectRatio(9f / 19.6f)
                )
                Text(
                    text = if (dark) "暗色模式" else "亮色模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}
