package com.xiaomanjun.sleepdownschedule.transition

import android.content.Intent

private const val TransitionRouteExtra =
    "com.xiaomanjun.sleepdownschedule.transition.ROUTE"
private const val TransitionSessionExtra =
    "com.xiaomanjun.sleepdownschedule.transition.SESSION"

internal fun Intent.putTransitionIdentity(session: TransitionSession): Intent = apply {
    putExtra(TransitionRouteExtra, session.routeId.wireName)
    putExtra(TransitionSessionExtra, session.id.value)
}

fun Intent.transitionRouteIdOrNull(): TransitionRouteId? =
    TransitionRouteId.fromWireName(getStringExtra(TransitionRouteExtra))

fun Intent.transitionSessionIdOrNull(): TransitionSessionId? =
    getStringExtra(TransitionSessionExtra)?.takeIf(String::isNotBlank)?.let(::TransitionSessionId)
