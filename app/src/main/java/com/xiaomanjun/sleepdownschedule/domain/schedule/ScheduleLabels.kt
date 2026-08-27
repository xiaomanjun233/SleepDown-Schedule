package com.xiaomanjun.sleepdownschedule.domain.schedule

fun scheduleWeekdayLabel(weekday: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]
