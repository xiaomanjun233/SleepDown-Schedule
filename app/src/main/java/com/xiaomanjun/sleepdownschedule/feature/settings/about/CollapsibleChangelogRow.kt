package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.itos.coui.layout.CouiPageDefaults
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsBasicComponent

private val changelogReleaseDates = mapOf(
    "2.0.4" to "2026-11-25",
    "2.0.3" to "2026-11-21",
    "2.0.2" to "2026-11-19",
    "2.0.1" to "2026-11-16",
    "2.0" to "2026-11-14",
    "1.2.7.1" to "2026-10-20",
    "1.2.7-r1" to "2026-10-19",
    "1.2.7" to "2026-10-15",
    "1.2.6" to "2026-09-25",
    "1.2.5" to "2026-09-14",
    "1.2.3" to "2026-09-01",
    "1.2.2" to "2026-08-29",
    "1.2.1" to "2026-08-28",
    "1.2.0" to "2026-08-19",
    "1.1.5" to "2026-08-11",
    "1.1.4" to "2026-08-08",
    "1.1.3" to "2026-08-08",
    "1.1.2" to "2026-08-05",
    "1.1.1" to "2026-08-02",
    "1.1.0" to "2026-07-30",
    "1.0.9" to "2026-07-26",
    "1.0.8" to "2026-07-22",
    "1.0.7" to "2026-07-22",
    "1.0.6" to "2026-07-20",
    "1.0.5" to "2026-07-19",
    "1.0.4" to "2026-07-18",
    "1.0.3" to "2026-07-18",
    "1.0.2" to "2026-07-16",
    "1.0.1" to "2026-07-15"
)

@Composable
internal fun CollapsibleChangelogRow(version: String, body: String) {
    val isCurrentVersion = version == BuildConfig.VERSION_NAME || version == "下一版本（开发中）"
    val releaseDate = changelogReleaseDates[version]
    val entries = remember(body) {
        val rawEntries = if ('\n' in body) {
            body.lineSequence()
        } else {
            body.splitToSequence('；')
        }
        rawEntries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { entry ->
                if (entry.lastOrNull() in setOf('。', '！', '？', '!', '?', '…')) {
                    entry
                } else {
                    "$entry。"
                }
            }
            .toList()
    }
    var expanded by rememberSaveable(version) { mutableStateOf(isCurrentVersion) }
    val gentleExpansionEasing = remember {
        CubicBezierEasing(0.20f, 0f, 0f, 1f)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(280, easing = gentleExpansionEasing),
        label = "changelog-arrow-$version"
    )
    val details: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.fillMaxWidth().padding(horizontal = CouiPageDefaults.rowPadding),
            enter = expandVertically(
                animationSpec = tween(420, easing = gentleExpansionEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(280, delayMillis = 60)),
            exit = shrinkVertically(
                animationSpec = tween(320, easing = gentleExpansionEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(220))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = CouiPageDefaults.contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            entry,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        SettingsBasicComponent(
            title = version,
            endActions = {
                releaseDate?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = if (expanded) "折叠 $version" else "展开 $version",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = arrowRotation),
                )
            },
            onClick = { expanded = !expanded },
        )
        details()
    }
}
