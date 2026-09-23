package com.xiaomanjun.sleepdownschedule.feature.importing

/** The host may already resize, pan, or do both before our floating dock is placed. */
internal fun schoolDockBottomInsetPx(
    imeBottom: Int,
    navigationBottom: Int,
    closedBottom: Int,
    currentBottom: Int
): Int {
    if (imeBottom <= navigationBottom) return navigationBottom
    val applied = if (closedBottom > 0 && currentBottom > 0) {
        (closedBottom - currentBottom).coerceAtLeast(0)
    } else 0
    return (imeBottom - applied).coerceAtLeast(0)
}
