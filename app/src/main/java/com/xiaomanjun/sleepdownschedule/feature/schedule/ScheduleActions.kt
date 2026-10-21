package com.xiaomanjun.sleepdownschedule.feature.schedule

import android.content.Context
import android.util.Log
import com.xiaomanjun.sleepdownschedule.core.wallpaper.cleanupUnreferencedScheduleWallpapers
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Schedule commands shared by home and the schedule screens, in their owner's existing scope. */
internal class ScheduleActions(
    private val context: Context,
    private val repository: ScheduleRepository,
    private val scope: CoroutineScope,
    private val requestRefresh: () -> Unit,
    private val onMessage: (String) -> Unit = {}
) {
    fun createSchedule(name: String = "新课表", activate: Boolean = true, onCreated: ((Int) -> Unit)? = null) = scope.launch {
        Log.d("ScheduleManager", "viewModel.createSchedule name=$name")
        val scheduleId = repository.createSchedule(name)
        Log.d("ScheduleManager", "repository.createSchedule created id=$scheduleId")
        if (activate) repository.activateSchedule(scheduleId)
        requestRefresh()
        onCreated?.invoke(scheduleId)
    }

    fun activateSchedule(scheduleId: Int, finish: (() -> Unit)? = null) = scope.launch {
        Log.d("ScheduleManager", "viewModel.activateSchedule id=$scheduleId")
        repository.activateSchedule(scheduleId)
        requestRefresh()
        finish?.invoke()
    }

    fun renameSchedule(scheduleId: Int, name: String) = scope.launch {
        Log.d("ScheduleManager", "viewModel.renameSchedule id=$scheduleId name=$name")
        repository.renameSchedule(scheduleId, name)
    }

    fun deleteSchedule(scheduleId: Int) = scope.launch {
        Log.d("ScheduleManager", "viewModel.deleteSchedule id=$scheduleId")
        repository.deleteSchedule(scheduleId)
        withContext(Dispatchers.IO) {
            cleanupUnreferencedScheduleWallpapers(context, repository.referencedWallpaperUris())
        }
        requestRefresh()
    }

    fun saveScheduleAdjustments(scheduleId: Int, original: String, updated: String,
        onSuccess: (() -> Unit)? = null, onFailure: (() -> Unit)? = null) = scope.launch {
        try {
            repository.saveScheduleAdjustments(scheduleId, original, updated)
            requestRefresh()
            onMessage("调休安排已保存")
            onSuccess?.invoke()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w("ScheduleActions", "Schedule adjustments rejected", error)
            onMessage(error.message ?: "课程操作失败，请重试")
            onFailure?.invoke()
        }
    }
}
