package com.xiaomanjun.sleepdownschedule.feature.course.management

import com.xiaomanjun.sleepdownschedule.app.state.*
import com.xiaomanjun.sleepdownschedule.*

import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal const val CourseManagementCourseIdExtra = "course_management_course_id"
private const val CourseManagementInitialStateTokenExtra = "course_management_initial_state_token"

/**
 * An Activity-local handoff for the already loaded schedule state.
 *
 * A fresh [ScheduleViewModel] deliberately starts with [AppState]'s safe empty value before the
 * Room flow emits. That is fine for an ordinary cold launch, but it made an anchored Activity
 * transition precompose the empty page for two frames and then visibly replace it with the real
 * course page. The bitmap snapshots only own the moving/background layers; this handoff keeps the
 * live destination content stable from its very first composition. Room remains the source of
 * truth and replaces the handoff as soon as its loaded state arrives.
 */
internal object CourseManagementStateHandoffStore {
    private val nextToken = AtomicLong(1L)
    private val states = ConcurrentHashMap<Long, AppState>()

    fun put(state: AppState): Long {
        val token = nextToken.getAndIncrement()
        states[token] = state
        if (states.size > 6) {
            states.keys.sorted().dropLast(6).forEach(states::remove)
        }
        return token
    }

    fun get(token: Long?): AppState? = token?.let(states::get)

    fun remove(token: Long?) {
        token?.let(states::remove)
    }
}

internal fun Intent.putCourseManagementInitialState(state: AppState): Intent = apply {
    putExtra(CourseManagementInitialStateTokenExtra, CourseManagementStateHandoffStore.put(state))
}

internal fun Intent.courseManagementInitialStateTokenOrNull(): Long? =
    getLongExtra(CourseManagementInitialStateTokenExtra, 0L).takeIf { it > 0L }

internal fun stableCourseManagementState(live: AppState, initial: AppState?): AppState =
    if (!live.loaded && initial?.loaded == true) initial else live
