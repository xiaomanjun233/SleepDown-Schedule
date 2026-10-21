package com.xiaomanjun.sleepdownschedule.core.ui.interaction

internal fun dockImeCompensationPx(imeBottomPx: Int, systemBottomPx: Int): Int =
    (imeBottomPx - systemBottomPx).coerceAtLeast(0)
