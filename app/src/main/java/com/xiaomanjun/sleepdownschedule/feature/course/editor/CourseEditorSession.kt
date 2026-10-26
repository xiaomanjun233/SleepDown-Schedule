package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import com.xiaomanjun.sleepdownschedule.CourseEntity
import androidx.compose.ui.geometry.Offset

/** One live form and one clock per flight, including flights returning behind a new editor. */
@Stable
internal class CourseEditorSession {
    var request by mutableStateOf<CourseEditorOverlayRequest?>(null)
    var retainedRequest: CourseEditorOverlayRequest? = null
    var renderedCourseId by mutableStateOf<Long?>(null)
    val motion = CourseEditorMotionState()
    val visible: Boolean get() = request != null || motion.phase != CourseEditorOverlayPhase.Idle
}

@Stable
internal class CourseEditorSessions {
    val sessions = mutableStateListOf(CourseEditorSession())
    var active by mutableStateOf(sessions.first())
        private set

    /** A second card cannot itself cause the first interruption. */
    fun canSelect(courseId: Long): Boolean = sessions.none { session ->
        val owner = session.request ?: session.retainedRequest
        owner != null && owner.course.id != courseId && !session.motion.hasInterrupted &&
            (session.motion.phase == CourseEditorOverlayPhase.Preparing ||
                session.motion.phase == CourseEditorOverlayPhase.Opening ||
                session.motion.phase == CourseEditorOverlayPhase.Closing ||
                session.motion.phase == CourseEditorOverlayPhase.Disposing ||
                session.motion.phase == CourseEditorOverlayPhase.Idle && session.retainedRequest != null)
    }

    /** An opening card toggles closed. A returning card must fall through to reopen. */
    fun consumeActiveTap(courseId: Long): Boolean {
        if (active.request?.course?.id == courseId) {
            if (active.motion.phase != CourseEditorOverlayPhase.Open) active.request = null
            return true
        }
        if (active.motion.phase == CourseEditorOverlayPhase.Idle && active.retainedRequest?.course?.id == courseId) {
            active.retainedRequest = null // cancel an initial source read that has not begun motion
            return true
        }
        return false
    }

    fun atOriginalAnchor(position: Offset): CourseEditorSession? {
        fun matches(session: CourseEditorSession) = session.visible &&
            session.retainedRequest?.sourceBoundsInRoot?.contains(position) == true
        return active.takeIf(::matches) ?: sessions.lastOrNull(::matches)
    }

    fun reopen(session: CourseEditorSession): Boolean {
        val retained = session.retainedRequest ?: return false
        if (session !in sessions || !canSelect(retained.course.id)) return false
        active = session
        session.request = retained
        return true
    }

    /** A returning A remains owned when B starts; selecting A again reuses A's live form. */
    fun select(courseId: Long, copyDraft: CourseEntity?): CourseEditorSession? {
        if (!canSelect(courseId)) return null
        val existing = sessions.firstOrNull {
            (it.visible || it.retainedRequest != null) && it.retainedRequest?.course?.id == courseId &&
                it.retainedRequest?.copyDraft == copyDraft
        }
        val previous = active
        active = existing ?: if (!previous.visible) previous else CourseEditorSession().also(sessions::add)
        if (existing != null) active.request = active.retainedRequest
        return active
    }

    fun finish(session: CourseEditorSession) {
        if (session.request != null || session.motion.phase != CourseEditorOverlayPhase.Idle) return
        session.retainedRequest = null
        if (session === active) sessions.lastOrNull { it !== session && it.visible }?.let { active = it }
        if (session !== active) sessions.remove(session)
    }
}

