package com.xiaomanjun.sleepdownschedule.core.ui.interaction

import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun android.view.Window.setStatusBarDarkIcons(darkIcons: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        insetsController?.setSystemBarsAppearance(
            if (darkIcons) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    } else {
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = if (darkIcons) {
            decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }
}
