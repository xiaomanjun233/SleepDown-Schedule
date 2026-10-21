package com.suda.yzune.wakeupschedule;

import org.json.JSONArray;
import org.json.JSONObject;

/** Remove finished registrations even when the source application is asleep. */
final class WakeUpCourseExpiry {
    static String remaining(String json, long now) {
        try {
            JSONArray source = new JSONArray(json);
            JSONArray result = new JSONArray();
            boolean changed = false;
            for (int i = 0; i < source.length(); i++) {
                JSONObject course = source.optJSONObject(i);
                long end = course == null ? 0 : course.optLong("endTimestamp", 0);
                if (end > 0 && end <= now / 1000L) changed = true;
                else result.put(source.get(i));
            }
            return changed ? result.toString() : json;
        } catch (org.json.JSONException invalid) { return json; }
    }

    static long nextEnd(String json, long now) {
        long next = Long.MAX_VALUE;
        try {
            JSONArray source = new JSONArray(json);
            for (int i = 0; i < source.length(); i++) {
                JSONObject course = source.optJSONObject(i);
                long seconds = course == null ? 0 : course.optLong("endTimestamp", 0);
                if (seconds <= 0 || seconds > Long.MAX_VALUE / 1000L) continue;
                long end = seconds * 1000L;
                if (end > now) next = Math.min(next, end);
            }
        } catch (org.json.JSONException invalid) { /* Metadata rows are not course arrays. */ }
        return next;
    }
}
