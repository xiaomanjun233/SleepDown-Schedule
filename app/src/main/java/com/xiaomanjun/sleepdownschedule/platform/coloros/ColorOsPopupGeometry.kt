package com.xiaomanjun.sleepdownschedule.platform.coloros

/** Align with the source edge; flip at the far edge, then respect the native safe window. */
internal fun shortcutMenuLeft(
    anchorLeft: Int, anchorRight: Int, width: Int, safeLeft: Int, safeRight: Int, rtl: Boolean
): Int {
    val start = if (rtl) anchorRight - width else anchorLeft
    val flipped = if (rtl) anchorLeft else anchorRight - width
    val maxLeft = (safeRight - width).coerceAtLeast(safeLeft)
    return (if (start in safeLeft..maxLeft) start else flipped).coerceIn(safeLeft, maxLeft)
}
