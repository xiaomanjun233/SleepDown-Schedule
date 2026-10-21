package com.xiaomanjun.sleepdownschedule.domain.schedule

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val ScheduleTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun parseScheduleTime(value: String): LocalTime = LocalTime.parse(value, ScheduleTimeFormatter)
