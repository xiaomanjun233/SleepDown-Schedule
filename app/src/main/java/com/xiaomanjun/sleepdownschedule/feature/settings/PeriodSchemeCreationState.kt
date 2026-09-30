package com.xiaomanjun.sleepdownschedule.feature.settings

import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodDayPart

/** Temporary wizard choices; toggling a section off and back on restores its allocation. */
internal data class PeriodSchemeCreationState(
    val counts: Map<PeriodDayPart, Int>,
    val enabledParts: Set<PeriodDayPart> = counts.filterValues { it > 0 }.keys,
    private val previousAllocations: Map<Set<PeriodDayPart>, Map<PeriodDayPart, Int>> = emptyMap(),
    private val previousSplitParts: Set<PeriodDayPart> = if (PeriodDayPart.AFTERNOON in enabledParts) enabledParts
        else enabledParts + setOf(PeriodDayPart.MORNING, PeriodDayPart.AFTERNOON)
) {
    val total: Int get() = enabledParts.sumOf { counts.getValue(it) }

    fun withEnabledParts(parts: Set<PeriodDayPart>): PeriodSchemeCreationState? {
        if (parts == enabledParts) return this
        // Enabling a section reallocates existing periods; only the count picker may add periods.
        if (parts.isEmpty() || parts.size > total) return null
        val restored = previousAllocations[parts]?.takeIf { allocation ->
            parts.sumOf { allocation.getValue(it) } == total
        }
        return copy(
            counts = restored ?: allocateQuickPickerCounts(PeriodDayPart.entries.filter { it in parts }, counts, total),
            enabledParts = parts,
            previousAllocations = previousAllocations + (enabledParts to counts),
            previousSplitParts = if (PeriodDayPart.AFTERNOON in parts) parts else previousSplitParts
        )
    }

    fun withSplitEnabled(enabled: Boolean): PeriodSchemeCreationState? =
        withEnabledParts(if (enabled) previousSplitParts else setOf(PeriodDayPart.MORNING))

    fun withCounts(value: Map<PeriodDayPart, Int>): PeriodSchemeCreationState {
        val selected = value.filterKeys { it in enabledParts }
        if (selected == counts.filterKeys { it in enabledParts }) return this
        // An explicit allocation edit supersedes older toggle snapshots, including at the same total.
        return copy(counts = selected, previousAllocations = emptyMap())
    }
}
