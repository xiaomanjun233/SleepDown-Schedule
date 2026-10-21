package com.suda.yzune.wakeupschedule;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeUpCourseExpiryTest {
    @Test public void removesOnlyFinishedRegistrationsAtTheirExactBoundary() {
        String data = "[{\"id\":1,\"endTimestamp\":100},{\"id\":2,\"endTimestamp\":200}]";
        assertEquals(data, WakeUpCourseExpiry.remaining(data, 99999L));
        assertEquals("[{\"id\":2,\"endTimestamp\":200}]", WakeUpCourseExpiry.remaining(data, 100000L));
        assertEquals("[]", WakeUpCourseExpiry.remaining(data, 200000L));
        assertEquals(200000L, WakeUpCourseExpiry.nextEnd(data, 100000L));
        assertEquals(Long.MAX_VALUE, WakeUpCourseExpiry.nextEnd(data, 200000L));
    }

    @Test public void expiredPreviewCannotResurrectAFinishedBaseCourse() {
        WakeUpSnapshotStore.Entry entry = new WakeUpSnapshotStore.Entry("[{\"endTimestamp\":300}]",
                "[{\"endTimestamp\":100}]", "Asia/Shanghai", 0, 400000, 200000);
        assertEquals("[{\"endTimestamp\":300}]", entry.dataAt(199999));
        assertEquals("[]", entry.dataAt(200000));
        assertEquals("{\"has_init\":true}", WakeUpCourseExpiry.remaining("{\"has_init\":true}", 100000));
    }
}
