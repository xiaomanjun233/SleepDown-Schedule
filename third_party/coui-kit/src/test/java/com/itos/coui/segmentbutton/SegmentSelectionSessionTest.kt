package com.itos.coui.segmentbutton

import org.junit.Assert.*
import org.junit.Test

class SegmentSelectionSessionTest {
    @Test fun cancelledNavigationDragNeverCommitsItsPreview() {
        val values = mutableListOf<Int>()
        val session = SegmentSelectionSession(0)
        session.begin(0)
        session.select(2, true, values::add)
        session.finish(true, 0, true, values::add)
        assertTrue(values.isEmpty())
        assertFalse(session.touching)
        assertEquals(0, session.preview)
    }

    @Test fun releaseCommitsOnceAndDraggingBackToTheAcceptedTabDoesNotNavigate() {
        val values = mutableListOf<Int>()
        val session = SegmentSelectionSession(0)
        session.begin(0); session.select(2, true, values::add)
        session.finish(false, 0, true, values::add)
        session.finish(false, 0, true, values::add)
        assertEquals(listOf(2), values)
        session.begin(2); session.select(0, true, values::add); session.select(2, true, values::add)
        session.finish(false, 2, true, values::add)
        assertEquals(listOf(2), values)
    }

    @Test fun immediateSelectionDoesNotEchoTheSameItemAndCancelDoesNotUndoAcceptedValues() {
        val values = mutableListOf<Int>()
        val session = SegmentSelectionSession(0)
        session.begin(0)
        session.select(1, false, values::add); session.select(1, false, values::add)
        session.finish(true, 1, false, values::add)
        assertEquals(listOf(1), values)
        assertEquals(1, session.preview)
    }
}
