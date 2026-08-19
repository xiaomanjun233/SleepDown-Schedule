package com.xiaomanjun.sleepdownschedule

enum class DistributionChannel {
    GITHUB,
    STORE
}

object AppDistribution {
    val channel: DistributionChannel = when (BuildConfig.DISTRIBUTION_CHANNEL) {
        "github" -> DistributionChannel.GITHUB
        "store" -> DistributionChannel.STORE
        else -> error("Unknown SleepDown distribution channel: ${BuildConfig.DISTRIBUTION_CHANNEL}")
    }

    val supportsSelfUpdate: Boolean
        get() = channel == DistributionChannel.GITHUB
}
