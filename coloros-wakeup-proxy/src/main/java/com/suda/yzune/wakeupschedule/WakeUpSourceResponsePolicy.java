package com.suda.yzune.wakeupschedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class WakeUpSourceResponsePolicy {
    private WakeUpSourceResponsePolicy() {
    }

    static boolean isUsable(int code, String data) {
        // An empty course array is a successful answer. Treating [] as a failure keeps the
        // previous non-empty snapshot alive after a course or preview is deleted.
        return code == 0 && data != null && !data.trim().isEmpty();
    }

    static long snapshotValidUntil(boolean course, LocalDate requestedDate, LocalDate today, ZoneId zone) {
        return (course ? requestedDate.plusDays(1) : today.plusDays(8))
                .atStartOfDay(zone).toInstant().toEpochMilli();
    }

    static LocalDate requestedDate(List<String> segments, ZoneId zone, LocalDate today) {
        LocalDate date = today;
        if (segments.size() > 1) {
            String suffix = segments.get(segments.size() - 1);
            try {
                if (suffix.matches("[0-9]{8}")) date = LocalDate.parse(suffix, DateTimeFormatter.BASIC_ISO_DATE);
                else if (suffix.matches("[0-9]{10}")) date = Instant.ofEpochSecond(Long.parseLong(suffix)).atZone(zone).toLocalDate();
                else if (suffix.matches("[0-9]{13}")) date = Instant.ofEpochMilli(Long.parseLong(suffix)).atZone(zone).toLocalDate();
            } catch (RuntimeException invalidDate) { date = today; }
        }
        return !segments.isEmpty() && "next_course_list".equals(segments.get(0)) ? date.plusDays(1) : date;
    }
}
