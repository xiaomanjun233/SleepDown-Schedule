package com.xiaomanjun.sleepdownschedule.transition

import com.xiaomanjun.sleepdownschedule.SleepDownRemoteConfig

internal object TransitionFeatureGate {
    fun allowsOplus(route: TransitionRouteSpec): Boolean {
        val config = SleepDownRemoteConfig.state.value.bootstrap?.transitions
        return TransitionFeaturePolicy.allowsOplus(route, config)
    }
}

internal object TransitionFeaturePolicy {
    fun allowsOplus(
        route: TransitionRouteSpec,
        config: com.xiaomanjun.sleepdownschedule.RemoteTransitionConfig?
    ): Boolean = route.nativePolicy == TransitionNativePolicy.OplusAllowlisted &&
        config?.oplusViewSeamlessEnabled == true &&
        route.id.wireName in config.oplusRouteAllowlist
}
