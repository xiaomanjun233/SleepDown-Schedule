package com.xiaomanjun.sleepdownschedule.feature.home.personalization

import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizeWallpaperBlurSlider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiaomanjun.sleepdownschedule.core.ui.material.PersonalizationPreviewState
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity

/** Composition-owned edits. A committed appearance stays visible until storage catches up. */
@Stable
internal class PersonalizationSession {
    var draft by mutableStateOf<ScheduleConfigEntity?>(null)
        private set
    var pendingCommit by mutableStateOf<ScheduleConfigEntity?>(null)
        private set
    var sliderKey by mutableStateOf<String?>(null)
        private set
    val preview = PersonalizationPreviewState()
    val displayConfig: ScheduleConfigEntity? get() = draft ?: pendingCommit

    fun acknowledge(config: ScheduleConfigEntity) {
        if (config == pendingCommit) pendingCommit = null
    }

    fun selectSchedule(scheduleId: Int) {
        if (draft?.id != scheduleId) draft = null
        if (pendingCommit?.id != scheduleId) pendingCommit = null
    }

    fun commit(
        current: ScheduleConfigEntity,
        candidate: ScheduleConfigEntity,
        changeKey: String,
        clearPreview: Boolean = true
    ): ScheduleConfigEntity {
        val merged = mergePersonalizationCandidate(draft ?: current, candidate, changeKey)
        draft = merged
        pendingCommit = merged
        if (clearPreview && changeKey != PersonalizeWallpaperBlurSlider) preview.clear(changeKey)
        return merged
    }

    fun setSliderActive(key: String, active: Boolean) {
        sliderKey = resolveActivePersonalizationSlider(sliderKey, key, active)
    }

    fun close() {
        sliderKey = null
        draft = null
        preview.clearAll()
        // Closing the panel must not flash the previous stored appearance.
    }
}
