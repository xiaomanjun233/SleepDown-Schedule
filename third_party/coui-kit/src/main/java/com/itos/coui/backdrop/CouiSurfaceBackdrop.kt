package com.itos.coui.backdrop

import androidx.compose.runtime.staticCompositionLocalOf

/** 所属面板只含背景材质的同级源；不包含标题、正文、按钮或其子弹层。 */
val LocalCouiSurfaceBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }
