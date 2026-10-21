package com.suda.yzune.wakeupschedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Republishes the persisted export when Android starts us after boot or an update. */
public final class WakeUpRestoreReceiver extends BroadcastReceiver {
    static final String EXPIRE = "com.suda.yzune.wakeupschedule.action.EXPIRE_COURSES";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !EXPIRE.equals(action)) return;
        // Provider initialization restores the snapshot before onReceive. Notify once;
        // the system can query it without starting an Activity or a permanent service.
        context.getContentResolver().call(android.net.Uri.parse("content://com.suda.yzune.wakeupschedule.provider"),
                "expire", null, null);
        Log.i("WakeUpRestoreReceiver", "Course refresh notified action=" + action);
    }
}
