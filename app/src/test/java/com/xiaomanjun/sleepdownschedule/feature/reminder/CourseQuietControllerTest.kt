package com.xiaomanjun.sleepdownschedule.feature.reminder

import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSettings
import com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSoundMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CourseQuietControllerTest {
    private class Device : CourseQuietDevice {
        override var hasPolicyAccess = true
        override var usesAppRule = true
        override var canRestoreGlobalFilter = false
        var sound = 2
        var manualFilter = 1
        var ruleEnabled = false
        var soundAlsoChangesDnd = false
        var zenMasksRingerMode = false
        var zenMasksMediaVolume = false
        var failRestore = false
        var media = 7
        var failMediaRestore = false
        var ignoreMediaChanges = false
        var coercedMediaVolume: Int? = null
        val operations = mutableListOf<String>()
        override val ringerMode get() = if (zenMasksRingerMode && ruleEnabled) 0 else sound
        override val interruptionFilter get() = if (manualFilter != 1) manualFilter else if (ruleEnabled) 2 else 1
        override val mediaVolume get() = if (zenMasksMediaVolume && ruleEnabled) 0 else media
        override fun setMediaVolume(volume: Int) {
            if (!hasPolicyAccess || (failMediaRestore && volume > 0)) throw SecurityException("revoked")
            operations += "media:$volume"
            if (!ignoreMediaChanges) media = coercedMediaVolume ?: volume
        }
        override fun setRingerMode(mode: Int) {
            if (!hasPolicyAccess || (failRestore && mode == 2)) throw SecurityException("revoked")
            operations += "sound:$mode"
            sound = mode
            if (soundAlsoChangesDnd) manualFilter = if (mode == 0) 3 else 1
        }
        override fun setDoNotDisturb(enabled: Boolean, originalFilter: Int?) {
            if (!hasPolicyAccess) throw SecurityException("revoked")
            operations += "dnd:$enabled"
            if (usesAppRule) ruleEnabled = enabled
            else if (enabled) manualFilter = courseDoNotDisturbFilter(manualFilter)
            else if (manualFilter == 2) manualFilter = originalFilter ?: 1
        }
        override fun restoreInterruptionFilter(filter: Int) { operations += "filter:$filter"; manualFilter = filter }
    }
    @Test fun automaticSoundMutesMediaAndRestoresTheOriginalVolumeAfterRestart() {
        val device = Device()
        refresh(device)
        assertEquals(0, device.sound)
        assertEquals(0, device.media)
        assertEquals(7, state.originalMediaVolume)
        state = Json.decodeFromString<CourseQuietRuntime>(Json.encodeToString(state))
        refresh(device, active = false)
        assertEquals(7, device.media)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun mediaVolumeKeysDuringClassArePreservedAndNotRepeatedlyOverridden() {
        val device = Device()
        refresh(device)
        device.media = 4
        repeat(3) { refresh(device) }
        assertEquals(4, device.media)
        refresh(device, active = false)
        assertEquals(4, device.media)
        assertEquals(1, device.operations.count { it.startsWith("media:") })
    }

    @Test fun dndAloneLeavesMediaAloneAndDisablingSoundRestoresItDuringClass() {
        val device = Device()
        refresh(device, CourseQuietSettings(doNotDisturbEnabled = true))
        assertEquals(7, device.media)
        refresh(device)
        assertEquals(0, device.media)
        refresh(device, CourseQuietSettings(doNotDisturbEnabled = true))
        assertEquals(7, device.media)
        assertTrue(device.ruleEnabled)
    }

    @Test fun vibrationMutesMediaAndSwitchingSoundModeRetainsTheSameVolumeBaseline() {
        val device = Device()
        refresh(device, both.copy(soundMode = CourseQuietSoundMode.VIBRATE))
        assertEquals(1, device.sound)
        assertEquals(0, device.media)
        refresh(device)
        assertEquals(7, state.originalMediaVolume)
        assertEquals(1, device.operations.count { it.startsWith("media:") })
        refresh(device, settings = CourseQuietSettings())
        assertEquals(7, device.media)
    }

    @Test fun failedMediaRestoreKeepsItsBaselineForTheNextAttempt() {
        val device = Device()
        refresh(device)
        device.failMediaRestore = true
        refresh(device, active = false)
        assertEquals(7, state.originalMediaVolume)
        assertEquals(0, state.appliedMediaVolume)
        device.failMediaRestore = false
        refresh(device, active = false)
        assertEquals(7, device.media)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun rejectedMediaAdjustmentIsReportedInsteadOfClaimingTheVolumeWasMuted() {
        val device = Device().apply { ignoreMediaChanges = true }
        refresh(device)
        assertEquals(7, device.media)
        assertNull(state.originalMediaVolume)
        assertNull(state.appliedMediaVolume)
        assertEquals("系统未允许降低媒体音量", errors.single().message)
    }

    @Test fun oldRecoverySnapshotsDecodeWithoutInventingAMediaRestoreValue() {
        val old = Json.decodeFromString<CourseQuietRuntime>(
            """{"originalRingerMode":2,"appliedRingerMode":0,"ownsDoNotDisturb":true}"""
        )
        assertNull(old.originalMediaVolume)
        assertNull(old.appliedMediaVolume)
    }

    @Test fun silentlyRejectedMediaRestoreKeepsItsBaselineUntilTheVolumeIsVerified() {
        val device = Device()
        refresh(device)
        device.ignoreMediaChanges = true
        refresh(device, active = false)
        assertEquals(7, state.originalMediaVolume)
        assertEquals(0, state.appliedMediaVolume)
        assertEquals("系统未能恢复媒体音量", errors.single().message)
        device.ignoreMediaChanges = false
        refresh(device, active = false)
        assertEquals(7, device.media)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun partialMediaAdjustmentRetainsTheObservedVolumeForSafeRestoration() {
        val device = Device().apply { coercedMediaVolume = 3 }
        refresh(device)
        assertEquals(3, device.media)
        assertEquals(7, state.originalMediaVolume)
        assertEquals(3, state.appliedMediaVolume)
        assertEquals("系统未允许降低媒体音量", errors.single().message)
        device.coercedMediaVolume = null
        refresh(device, active = false)
        assertEquals(7, device.media)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun originalMediaVolumeIsCapturedBeforeAnAppDndRuleCanMaskIt() {
        val device = Device().apply { zenMasksMediaVolume = true }
        refresh(device)
        assertEquals(7, state.originalMediaVolume)
        refresh(device, active = false)
        assertEquals(7, device.media)
    }

    private val both = CourseQuietSettings(doNotDisturbEnabled = true, soundEnabled = true)
    private var state = CourseQuietRuntime()
    private val errors = mutableListOf<Exception>()
    private fun refresh(device: Device, settings: CourseQuietSettings = both, active: Boolean = true) {
        state = reconcileCourseQuietState(settings, active, state, device, { state = it }, { errors += it })
    }

    @Test fun restoreOriginalSoundAndLeaveOtherDndRuleUntouched() {
        val device = Device().apply { sound = 1; manualFilter = 3 }
        refresh(device)
        assertTrue(device.ruleEnabled)
        assertEquals(0, device.sound)
        refresh(device, active = false)
        assertEquals(1, device.sound)
        assertEquals(3, device.manualFilter)
        assertFalse(device.ruleEnabled)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun originalSoundIsCapturedBeforeDndCanMaskIt() {
        val device = Device().apply { zenMasksRingerMode = true }
        refresh(device)
        assertEquals(2, state.originalRingerMode)
        refresh(device, active = false)
        assertEquals(2, device.sound)
    }

    @Test fun repeatedRefreshOrAdjacentCourseDoesNotReapplyOrOverwriteBaseline() {
        val device = Device()
        refresh(device)
        val entered = device.operations.toList()
        repeat(3) { refresh(device) }
        assertEquals(entered, device.operations)
        assertEquals(2, state.originalRingerMode)
    }

    @Test fun manualSoundChangeDuringClassIsPreserved() {
        val device = Device()
        refresh(device)
        device.sound = 1
        refresh(device)
        assertEquals(1, device.sound)
        refresh(device, active = false)
        assertEquals(1, device.sound)
        assertEquals(1, device.operations.count { it.startsWith("sound:") })
    }

    @Test fun persistedOwnershipRestoresAfterProcessRestart() {
        val device = Device()
        refresh(device)
        state = Json.decodeFromString<CourseQuietRuntime>(Json.encodeToString(state))
        refresh(device, active = false)
        assertEquals(2, device.sound)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun turningOffSettingsDuringClassRestoresImmediately() {
        val device = Device()
        refresh(device)
        refresh(device, settings = CourseQuietSettings())
        assertEquals(2, device.sound)
        assertFalse(device.ruleEnabled)
    }

    @Test fun soundAndDndCanBeEnabledIndependently() {
        val device = Device()
        refresh(device, CourseQuietSettings(soundEnabled = true, soundMode = CourseQuietSoundMode.VIBRATE))
        assertEquals(1, device.sound)
        assertFalse(device.ruleEnabled)
        refresh(device, CourseQuietSettings(doNotDisturbEnabled = true))
        assertEquals(2, device.sound)
        assertTrue(device.ruleEnabled)
    }

    @Test fun missingPermissionCannotAcquireSystemStateOwnership() {
        val device = Device().apply { hasPolicyAccess = false }
        refresh(device)
        assertTrue(device.operations.isEmpty())
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun revokedPermissionKeepsRecoveryStateUntilAccessReturns() {
        val device = Device()
        refresh(device)
        device.hasPolicyAccess = false
        refresh(device, active = false)
        assertEquals(2, state.originalRingerMode)
        assertTrue(state.ownsDoNotDisturb)
        assertEquals(3, errors.size)
        device.hasPolicyAccess = true
        refresh(device, active = false)
        assertEquals(2, device.sound)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun failedRingerRestoreRemainsRecoverableOnNextCalendarEvent() {
        val device = Device()
        refresh(device)
        device.failRestore = true
        refresh(device, active = false)
        assertEquals(2, state.originalRingerMode)
        assertFalse(state.ownsDoNotDisturb)
        device.failRestore = false
        refresh(device, active = false)
        assertEquals(2, device.sound)
        assertEquals(CourseQuietRuntime(), state)
    }

    @Test fun legacySilentModeRestoresPriorDndInsteadOfForcingAll() {
        val device = Device().apply { canRestoreGlobalFilter = true; soundAlsoChangesDnd = true; manualFilter = 3 }
        refresh(device, CourseQuietSettings(soundEnabled = true))
        refresh(device, CourseQuietSettings(soundEnabled = true), active = false)
        assertEquals(2, device.sound)
        assertEquals(3, device.manualFilter)
    }

    @Test fun legacyDndDoesNotRelaxAnyExistingFilterOnEntryOrExit() {
        for (original in 1..4) {
            state = CourseQuietRuntime()
            val device = Device().apply {
                usesAppRule = false
                canRestoreGlobalFilter = true
                manualFilter = original
            }
            val settings = CourseQuietSettings(doNotDisturbEnabled = true)
            refresh(device, settings)
            assertEquals(if (original == 1) 2 else original, device.manualFilter)
            refresh(device, settings, active = false)
            assertEquals(original, device.manualFilter)
            assertEquals(CourseQuietRuntime(), state)
        }
        assertTrue(errors.isEmpty())
    }

    @Test fun legacyManualDndChangeIsPreservedOnExit() {
        val device = Device().apply { usesAppRule = false; canRestoreGlobalFilter = true }
        refresh(device)
        device.manualFilter = 4
        refresh(device, active = false)
        assertEquals(4, device.manualFilter)
    }
}
