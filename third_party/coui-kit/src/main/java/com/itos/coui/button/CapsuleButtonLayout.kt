package com.itos.coui.button

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry, content and click semantics have one owner; a material supplies only a modifier. */
@Composable
fun CapsuleButtonLayout(
    modifier: Modifier,
    material: Modifier,
    height: Dp,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    clickTargetEnabled: Boolean = true,
    indication: Indication? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.then(material).then(if (clickTargetEnabled) Modifier.clickable(
            interactionSource, indication, enabled, role = Role.Button, onClick = onClick
        ) else Modifier)
            .then(if (height == Dp.Unspecified) Modifier.heightIn(min = CouiButtonDefaults.minHeight)
                else Modifier.height(height))
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
