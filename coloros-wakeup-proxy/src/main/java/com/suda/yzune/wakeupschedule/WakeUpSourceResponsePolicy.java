package com.suda.yzune.wakeupschedule;

final class WakeUpSourceResponsePolicy {
    private WakeUpSourceResponsePolicy() {
    }

    static boolean isUsable(int code, String data) {
        // An empty course array is a successful answer. Treating [] as a failure keeps the
        // previous non-empty snapshot alive after a course or preview is deleted.
        return code == 0 && data != null && !data.trim().isEmpty();
    }
}
