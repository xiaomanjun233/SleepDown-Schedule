package com.xiaomanjun.sleepdownschedule.feature.home

import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

/** Explicit appearance edits may refresh a frozen scene without replacing its course inputs. */
internal fun AppState.withPersonalizationConfig(candidate: ScheduleConfigEntity?): AppState =
    if (candidate != null && candidate.id == config.id && candidate != config) {
        copy(config = candidate)
    } else {
        this
    }
