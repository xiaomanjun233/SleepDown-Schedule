package com.xiaomanjun.sleepdownschedule.transition

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.BuildConfig

internal object TransitionFeatureGate {
    fun allowsSharedViewKit(route: TransitionRouteSpec): Boolean =
        BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES &&
            route.nativePolicy == TransitionNativePolicy.SharedViewKitAllowlisted &&
            ((BuildConfig.DEBUG && BuildConfig.SLEEPDOWN_SHARED_VIEW_KIT_TEST) ||
                TransitionFeaturePolicy.allowsSharedViewKit(route,
                    SleepDownRemoteConfig.state.value.bootstrap?.transitions))
}

internal object TransitionFeaturePolicy {
    fun allowsSharedViewKit(route: TransitionRouteSpec, config: RemoteTransitionConfig?): Boolean =
        route.nativePolicy == TransitionNativePolicy.SharedViewKitAllowlisted && config?.sharedViewKitEnabled == true &&
            route.id.wireName in config.sharedViewKitRouteAllowlist
}
