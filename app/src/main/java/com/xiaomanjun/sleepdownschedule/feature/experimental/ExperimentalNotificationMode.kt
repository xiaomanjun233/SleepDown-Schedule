package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.content.Context
import android.os.Build
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.feature.coloros.ColorOSCourseExperiment
import com.xiaomanjun.sleepdownschedule.model.NotificationMode

/** The removable vendor layer. Ordinary notification settings remain the shared baseline. */
internal enum class ExperimentalNotificationMode(val label: String) {
    STANDARD("普通通知"),
    LIVE_UPDATE("实时活动"),
    FLUID_CLOUD("流体云"),
    FLUID_CLOUD_LIVE_UPDATE("流体云+实时活动"),
    YOYO_LIVE_UPDATE("YOYO建议+实时活动"),
    SUPER_ISLAND("超级岛")
}

internal object ExperimentalNotificationModes {
    fun available(): List<ExperimentalNotificationMode> {
        val device = ColorOSCourseExperiment.deviceStatus()
        return availableForDevice(
            experimentalBuild = BuildConfig.SLEEPDOWN_EXP_BUILD,
            isHonor = device.isHonor,
            isColorOSFamily = device.isColorOSFamily,
            isXiaomi = XiaomiSuperIsland.isXiaomiDevice(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())
        )
    }

    internal fun availableForDevice(
        experimentalBuild: Boolean,
        isHonor: Boolean,
        isColorOSFamily: Boolean,
        isXiaomi: Boolean
    ): List<ExperimentalNotificationMode> {
        val base = listOf(ExperimentalNotificationMode.STANDARD, ExperimentalNotificationMode.LIVE_UPDATE)
        if (!experimentalBuild) return base
        return when {
            isHonor -> base + ExperimentalNotificationMode.YOYO_LIVE_UPDATE
            isColorOSFamily -> base + listOf(
                ExperimentalNotificationMode.FLUID_CLOUD,
                ExperimentalNotificationMode.FLUID_CLOUD_LIVE_UPDATE
            )
            isXiaomi -> base + ExperimentalNotificationMode.SUPER_ISLAND
            else -> base
        }
    }

    fun selected(context: Context, notificationMode: NotificationMode): ExperimentalNotificationMode {
        if (XiaomiSuperIsland.isEnabled(context)) return ExperimentalNotificationMode.SUPER_ISLAND
        if (ColorOSCourseExperiment.isEnabled(context)) {
            return when {
                ColorOSCourseExperiment.deviceStatus().isHonor -> ExperimentalNotificationMode.YOYO_LIVE_UPDATE
                ColorOSCourseExperiment.allowsParallelLiveUpdate(context) ->
                    ExperimentalNotificationMode.FLUID_CLOUD_LIVE_UPDATE
                else -> ExperimentalNotificationMode.FLUID_CLOUD
            }
        }
        return if (notificationMode == NotificationMode.LIVE_UPDATE) {
            ExperimentalNotificationMode.LIVE_UPDATE
        } else ExperimentalNotificationMode.STANDARD
    }

    /** Returns the existing persisted notification mode to save through the normal settings path. */
    fun activate(context: Context, mode: ExperimentalNotificationMode): NotificationMode {
        require(mode in available()) { "当前设备不支持所选实验功能" }
        if (ColorOSCourseExperiment.isEnabled(context)) ColorOSCourseExperiment.setEnabled(context, false)
        if (XiaomiSuperIsland.isEnabled(context)) XiaomiSuperIsland.setEnabled(context, false)
        return when (mode) {
            ExperimentalNotificationMode.STANDARD -> NotificationMode.STANDARD
            ExperimentalNotificationMode.LIVE_UPDATE -> NotificationMode.LIVE_UPDATE
            ExperimentalNotificationMode.FLUID_CLOUD -> {
                ColorOSCourseExperiment.setParallelLiveUpdate(context, false)
                check(ColorOSCourseExperiment.setEnabled(context, true))
                NotificationMode.STANDARD
            }
            ExperimentalNotificationMode.FLUID_CLOUD_LIVE_UPDATE,
            ExperimentalNotificationMode.YOYO_LIVE_UPDATE -> {
                ColorOSCourseExperiment.setParallelLiveUpdate(context, true)
                check(ColorOSCourseExperiment.setEnabled(context, true))
                NotificationMode.LIVE_UPDATE
            }
            ExperimentalNotificationMode.SUPER_ISLAND -> {
                check(XiaomiSuperIsland.setEnabled(context, true))
                NotificationMode.LIVE_UPDATE
            }
        }
    }
}
