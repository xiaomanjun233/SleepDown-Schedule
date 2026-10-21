package com.suda.yzune.wakeupschedule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class WakeUpSnapshotStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private WakeUpSnapshotStore.Entry entry(String data) {
        return new WakeUpSnapshotStore.Entry(data, "[]", "Asia/Shanghai", 100, 1000, 500);
    }

    @Test public void processRestartPreservesSnapshotsAndExpiresPreviewSeparately() throws Exception {
        File file = new File(folder.getRoot(), "snapshots");
        new WakeUpSnapshotStore(file).replace(Map.of("course|2026-09-21", entry("[preview]")));
        WakeUpSnapshotStore restarted = new WakeUpSnapshotStore(file);
        assertEquals("[preview]", restarted.get("course|2026-09-21", 200, "Asia/Shanghai").dataAt(200));
        assertEquals("[]", restarted.get("course|2026-09-21", 600, "Asia/Shanghai").dataAt(600));
        assertNull(restarted.get("course|2026-09-21", 1000, "Asia/Shanghai"));
        assertNull(restarted.get("course|2026-09-21", 99, "Asia/Shanghai"));
        assertNull(restarted.get("course|2026-09-21", 200, "UTC"));
    }

    @Test public void deletionAndSwitchReplaceAllRowsAndRejectOldInFlightRead() throws Exception {
        File file = new File(folder.getRoot(), "snapshots");
        WakeUpSnapshotStore store = new WakeUpSnapshotStore(file);
        store.replace(Map.of("course|2026-09-21", entry("[old]"), "course|2026-09-22", entry("[tomorrow]")));
        long staleGeneration = store.generation();
        store.replace(Map.of("course|2026-09-21", entry("[]")));
        assertFalse(store.put("course|2026-09-21", entry("[old]"), staleGeneration));
        WakeUpSnapshotStore restarted = new WakeUpSnapshotStore(file);
        assertEquals("[]", restarted.get("course|2026-09-21", 200, "Asia/Shanghai").dataAt(200));
        assertNull(restarted.get("course|2026-09-22", 200, "Asia/Shanghai"));
    }

    @Test public void todayTomorrowAndTimestampPathsResolveToSameDatedKey() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        assertEquals(today.plusDays(1), WakeUpSourceResponsePolicy.requestedDate(List.of("next_course_list"), zone, today));
        assertEquals(today.plusDays(1), WakeUpSourceResponsePolicy.requestedDate(List.of("course_list", "20260922"), zone, today));
        String millis = Long.toString(today.atStartOfDay(zone).toInstant().toEpochMilli());
        assertEquals(today, WakeUpSourceResponsePolicy.requestedDate(List.of("course_list", millis), zone, today));
        assertEquals(today, WakeUpSourceResponsePolicy.requestedDate(List.of("course_list", "20261399"), zone, today));
    }

    @Test public void metadataPullSurvivesMidnightWhileDatedCoursesExpireSeparately() throws Exception {
        File file = new File(folder.getRoot(), "snapshots");
        LocalDate today = LocalDate.of(2026, 9, 21);
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long created = today.atStartOfDay(zone).toInstant().toEpochMilli();
        WakeUpSnapshotStore store = new WakeUpSnapshotStore(file);
        store.replace(Map.of(
                "has_init", new WakeUpSnapshotStore.Entry("{}", "{}", zone.getId(), created,
                        WakeUpSourceResponsePolicy.snapshotValidUntil(false, today, today, zone), 0),
                "course|today", new WakeUpSnapshotStore.Entry("[]", "[]", zone.getId(), created,
                        WakeUpSourceResponsePolicy.snapshotValidUntil(true, today, today, zone), 0),
                "course|tomorrow", new WakeUpSnapshotStore.Entry("[tomorrow]", "[tomorrow]", zone.getId(), created,
                        WakeUpSourceResponsePolicy.snapshotValidUntil(true, today.plusDays(1), today, zone), 0)));
        WakeUpSnapshotStore restarted = new WakeUpSnapshotStore(file);
        long nextMorning = today.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli();
        assertNotNull(restarted.get("has_init", nextMorning, zone.getId()));
        assertNull(restarted.get("course|today", nextMorning, zone.getId()));
        assertEquals("[tomorrow]", restarted.get("course|tomorrow", nextMorning, zone.getId()).dataAt(nextMorning));
    }
}
