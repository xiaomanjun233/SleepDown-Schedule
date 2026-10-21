package com.xiaomanjun.sleepdownschedule.feature.importing

/** The host may already resize, pan, or do both before our floating dock is placed. */
internal fun schoolDockBottomInsetPx(
    imeBottom: Int,
    navigationBottom: Int,
    closedBottom: Int,
    currentBottom: Int
): Int = com.xiaomanjun.sleepdownschedule.core.ui.layout.floatingInputBottomInsetPx(
    imeBottom, navigationBottom, closedBottom, currentBottom
)
