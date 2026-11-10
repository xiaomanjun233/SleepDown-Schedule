package com.itos.coui.menu

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntRect

/** 内容容器提供自己的可见区域；稳定读取函数避免把逐帧边界广播给整棵内容树。 */
internal val LocalCouiMenuVisibleBounds = staticCompositionLocalOf<(() -> IntRect?)?> { null }
