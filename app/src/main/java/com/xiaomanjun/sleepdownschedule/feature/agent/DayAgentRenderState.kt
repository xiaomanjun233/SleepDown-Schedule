package com.xiaomanjun.sleepdownschedule.feature.agent

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.semantics
import com.xiaomanjun.sleepdownschedule.AppState
import java.time.LocalDate
import java.util.LinkedHashMap

internal val LocalAgentBackgroundCaptureMask = staticCompositionLocalOf<() -> Boolean> { { false } }

internal data class AgentSourceHandoffTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float
)

/**
 * The day page is disposed while the week page is entering. Keep the expensive, immutable Agent
 * projection outside that short-lived composition so switching back does not scan and sort the
 * whole timetable inside the transition frame budget again.
 */
internal object DayAgentRenderCache {
    private data class FactsKey(
        val scheduleId: Int,
        val date: LocalDate,
        val coursesHash: Int,
        val periodsHash: Int,
        val configHash: Int,
        val scheduleName: String?,
        val weatherHash: Int,
        val schedulesHash: Int
    )

    private val facts = object : LinkedHashMap<FactsKey, DayAgentFacts>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FactsKey, DayAgentFacts>?): Boolean =
            size > 6
    }
    private var lastCleanupDate: LocalDate? = null

    @Synchronized
    fun facts(
        state: AppState,
        date: LocalDate,
        weather: AgentWeatherSnapshot?,
        scheduleName: String?,
        context: android.content.Context
    ): DayAgentFacts {
        val scheduleSummaries = state.schedules.map { profile ->
            AgentScheduleSummary(
                id = profile.id,
                name = profile.name,
                isActive = profile.isActive || profile.id == state.config.id
            )
        }
        val key = FactsKey(
            scheduleId = state.config.id,
            date = date,
            coursesHash = state.courses.hashCode(),
            periodsHash = state.periods.hashCode(),
            configHash = 31 * state.config.hashCode() + AgentSettingRegistry.snapshot(state.config, scheduleName, context, date).hashCode(),
            scheduleName = scheduleName,
            weatherHash = weather.hashCode(),
            schedulesHash = scheduleSummaries.hashCode()
        )
        return facts.getOrPut(key) {
            buildDayAgentFacts(
                state.courses,
                state.periods,
                state.config,
                date,
                weather,
                scheduleName,
                settingContext = context.applicationContext,
                schedules = scheduleSummaries
            )
        }
    }

    @Synchronized
    fun shouldCleanup(date: LocalDate): Boolean {
        if (lastCleanupDate == date) return false
        lastCleanupDate = date
        return true
    }
}

@Stable
class DayAgentBackgroundMotionState {
    val progress = Animatable(0f)
    // Same idea as the course editor: the background depth (scale + blur) runs on a slower,
    // longer timeline than the card morph so it keeps settling after the card has opened or
    // closed, reading as inertial pull on the home surface. Here the surface recedes (1 -> 0.92)
    // instead of pushing in, matching the existing depth semantics.
    val backgroundZoom = Animatable(1f)
}

// Range and durations for the inertial background zoom. The zoom shares one curve with the
// blur so the two never drift apart, exactly like CourseEditorContainerOverlay. The surface
// pushes in (1 -> 1.08) as the agent opens — same direction and magnitude as the course editor.
internal const val DayAgentBackgroundZoomRestScale = 1.08f
internal const val DayAgentBackgroundZoomOpenDurationMillis = 620
internal const val DayAgentBackgroundZoomCloseDurationMillis = 620

@Composable
internal fun rememberDayAgentBackgroundMotionState(): DayAgentBackgroundMotionState =
    remember { DayAgentBackgroundMotionState() }
