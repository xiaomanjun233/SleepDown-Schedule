package com.xiaomanjun.sleepdownschedule.platform.coloros

import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop

/** Host owns the query and bottom/IME positioning. Kit owns the original two search capsules. */
@RequiresApi(34)
@Composable
internal fun CouiSchoolSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    dark: Boolean,
    bottomOffset: Dp,
    entranceProgress: Float,
    largeScreen: Boolean,
    modifier: Modifier,
    backdrop: Backdrop,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = rememberCouiSearchBarState(expanded)
    val focusManager = LocalFocusManager.current
    val source = rememberCouiGlassBackdrop(backdrop)
    BackHandler(expanded) { expanded = false }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = if (largeScreen) (maxWidth * .5f).coerceIn(minOf(260.dp, maxWidth), maxWidth) else maxWidth
        SearchBar(
            modifier = Modifier.align(Alignment.BottomCenter).width(width)
                .offset(y = -bottomOffset + 52.dp * (1f - entranceProgress.coerceIn(0f, 1f)))
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = entranceProgress.coerceIn(0f, 1f)
                },
            state = state,
            query = value,
            onQueryChange = onValueChange,
            onExpandedChange = { expanded = it },
            placeholder = "搜索学校或教务工具…",
            cancelText = if (value.isBlank()) "取消" else "搜索",
            clearDescription = "清空搜索",
            dark = dark,
            backdrop = source,
            onSearch = { focusManager.clearFocus() },
            onAction = { if (value.isBlank()) expanded = false },
        )
    }
}
