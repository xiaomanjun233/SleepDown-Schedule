package com.xiaomanjun.sleepdownschedule.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.xiaomanjun.sleepdownschedule.HomeStartMode

enum class HomeMode { Day, Week }
internal fun HomeStartMode.toHomeMode(): HomeMode = when (this) {
    HomeStartMode.DAY,
    HomeStartMode.TWO_DAY -> HomeMode.Day
    HomeStartMode.WEEK -> HomeMode.Week
}
