package com.itos.coui.theme

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/** SleepDown 兼容边界：旧系统和使用宿主液态材质的页面只消费 COUI 排版。 */
val LocalCouiMaterialEnabled = staticCompositionLocalOf { Build.VERSION.SDK_INT >= 31 }
