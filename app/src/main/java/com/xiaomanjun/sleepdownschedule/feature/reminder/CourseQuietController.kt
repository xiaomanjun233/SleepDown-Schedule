package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSettings
import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSoundMode
import kotlinx.serialization.Serializable

@Serializable
internal data class CourseQuietRuntime(
    val originalRingerMode: Int? = null,
    val appliedRingerMode: Int? = null,
    val requestedRingerMode: Int? = null,
    val ownsDoNotDisturb: Boolean = false,
    val originalInterruptionFilter: Int? = null,
    val appliedInterruptionFilter: Int? = null
)

internal interface CourseQuietDevice {
    val hasPolicyAccess: Boolean
    val usesAppRule: Boolean
    val canRestoreGlobalFilter: Boolean
    val ringerMode: Int
    val interruptionFilter: Int
    fun setRingerMode(mode: Int)
    fun setDoNotDisturb(enabled: Boolean, originalFilter: Int?)
    fun restoreInterruptionFilter(filter: Int)
}

/** Persists ownership before changing the system, and restores only sound still owned by us. */
internal fun reconcileCourseQuietState(
    settings: CourseQuietSettings, inClass: Boolean, initial: CourseQuietRuntime,
    device: CourseQuietDevice, persist: (CourseQuietRuntime) -> Unit, onError: (Exception) -> Unit
): CourseQuietRuntime {
    var state = initial
    fun save(next: CourseQuietRuntime) { persist(next); state = next }
    fun attempt(block: () -> Unit) {
        try { block() } catch (error: Exception) { onError(error) }
    }
    val active = inClass && settings.enabled && device.hasPolicyAccess
    val mayRestoreGlobalFilter = initial.appliedInterruptionFilter != null &&
        device.interruptionFilter == initial.appliedInterruptionFilter
    val desiredSound = if (active && settings.soundEnabled) {
        if (settings.soundMode == CourseQuietSoundMode.SILENT) 0 else 1
    } else null
    val originalSound = if (desiredSound != null && state.appliedRingerMode == null) device.ringerMode else null
    // Capture both baselines before either operation; silent mode can itself change legacy DND.
    if (active && device.canRestoreGlobalFilter && state.originalInterruptionFilter == null) attempt {
        save(state.copy(originalInterruptionFilter = device.interruptionFilter))
    }
    if (active && settings.doNotDisturbEnabled && !state.ownsDoNotDisturb) attempt {
        save(state.copy(ownsDoNotDisturb = true))
        try {
            device.setDoNotDisturb(true, state.originalInterruptionFilter)
            save(state.copy(appliedInterruptionFilter = device.interruptionFilter))
        } catch (error: Exception) {
            save(state.copy(ownsDoNotDisturb = false))
            throw error
        }
    }
    if (state.appliedRingerMode != null && (state.requestedRingerMode ?: state.appliedRingerMode) != desiredSound) attempt {
        if (device.ringerMode == state.appliedRingerMode) {
            state.originalRingerMode?.let(device::setRingerMode)
        }
        save(state.copy(originalRingerMode = null, appliedRingerMode = null, requestedRingerMode = null))
    }
    if (desiredSound != null && state.appliedRingerMode == null) attempt {
        val original = originalSound ?: device.ringerMode
        save(state.copy(originalRingerMode = original, appliedRingerMode = desiredSound, requestedRingerMode = desiredSound))
        try {
            device.setRingerMode(desiredSound)
            // Some devices coerce vibration to silent; track the actual value for restoration,
            // while the requested value prevents repeated switching at every refresh.
            save(state.copy(appliedRingerMode = device.ringerMode, appliedInterruptionFilter = device.interruptionFilter))
        } catch (error: Exception) {
            save(state.copy(originalRingerMode = null, appliedRingerMode = null, requestedRingerMode = null))
            throw error
        }
    }
    if (state.ownsDoNotDisturb && (!active || !settings.doNotDisturbEnabled)) attempt {
        device.setDoNotDisturb(false, state.originalInterruptionFilter)
        save(state.copy(ownsDoNotDisturb = false))
    }
    if (!active && !state.ownsDoNotDisturb && state.appliedRingerMode == null &&
        (state.originalInterruptionFilter != null || state.appliedInterruptionFilter != null)) attempt {
        if (mayRestoreGlobalFilter && device.canRestoreGlobalFilter && state.originalInterruptionFilter != null) {
            val original = requireNotNull(state.originalInterruptionFilter)
            if (device.interruptionFilter != original) device.restoreInterruptionFilter(original)
        }
        save(state.copy(originalInterruptionFilter = null, appliedInterruptionFilter = null))
    }
    return state
}
