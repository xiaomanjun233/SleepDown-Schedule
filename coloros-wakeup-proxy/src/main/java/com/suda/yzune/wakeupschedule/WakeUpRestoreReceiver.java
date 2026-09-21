package com.suda.yzune.wakeupschedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Republishes the persisted export when Android starts us after boot or an update. */
public final class WakeUpRestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        // Provider initialization restores the snapshot before onReceive. Notify once;
        // the system can query it without starting an Activity or a permanent service.
        WakeUpProxyProvider.notifySystem(context);
        Log.i("WakeUpRestoreReceiver", "Course refresh notified action=" + action);
    }
}
