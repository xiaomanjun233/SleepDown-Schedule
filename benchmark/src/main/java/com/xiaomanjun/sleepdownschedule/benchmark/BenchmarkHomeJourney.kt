package com.xiaomanjun.sleepdownschedule.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope

private const val BenchmarkLauncherAlias =
    "com.xiaomanjun.sleepdownschedule.LauncherFollow"
internal const val BenchmarkIdleTimeoutMillis = 1_500L

internal data class FreeformBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun MacrobenchmarkScope.startBenchmarkHome() {
    grantBenchmarkRuntimePermissions()
    pressHome()
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(PACKAGE_NAME, BenchmarkLauncherAlias)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    )
}

internal fun MacrobenchmarkScope.startBenchmarkHomeWithWallpaper() {
    configureBenchmarkWallpaper()
    startBenchmarkHome()
}

internal fun MacrobenchmarkScope.startBenchmarkHomeFreeform(bounds: FreeformBounds) {
    grantBenchmarkRuntimePermissions()
    pressHome()
    val launchResult = device.executeShellCommand(
        "am start -W --windowingMode 5 " +
            "-a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER " +
            "-f 0x10008000 " +
            "-n $PACKAGE_NAME/$BenchmarkLauncherAlias"
    )
    check("Error:" !in launchResult) {
        "Unable to launch benchmark target in freeform mode: $launchResult"
    }

    val stackList = device.executeShellCommand("am stack list")
    val taskId = Regex(
        "taskId=(\\d+): ${Regex.escape(PACKAGE_NAME)}/"
    ).find(stackList)?.groupValues?.get(1)
    checkNotNull(taskId) {
        "Unable to find freeform task for $PACKAGE_NAME in: $stackList"
    }

    val resizeResult = device.executeShellCommand(
        "am task resize $taskId ${bounds.left} ${bounds.top} " +
            "${bounds.right} ${bounds.bottom}"
    )
    check("Error:" !in resizeResult) {
        "Unable to resize benchmark task $taskId: $resizeResult"
    }
    device.waitForIdle(BenchmarkIdleTimeoutMillis)
}

internal fun MacrobenchmarkScope.startBenchmarkHomeFreeformWithWallpaper(bounds: FreeformBounds) {
    configureBenchmarkWallpaper()
    startBenchmarkHomeFreeform(bounds)
}

private fun MacrobenchmarkScope.configureBenchmarkWallpaper() {
    val output = device.executeShellCommand(
        "am broadcast -W " +
            "-a com.xiaomanjun.sleepdownschedule.benchmark.CONFIGURE_WALLPAPER " +
            "-n $PACKAGE_NAME/com.xiaomanjun.sleepdownschedule.BenchmarkWallpaperReceiver"
    )
    check("Broadcast completed" in output) {
        "Unable to configure benchmark wallpaper: $output"
    }
}

private fun MacrobenchmarkScope.grantBenchmarkRuntimePermissions() {
    device.executeShellCommand(
        "pm grant $PACKAGE_NAME android.permission.POST_NOTIFICATIONS"
    )
}
