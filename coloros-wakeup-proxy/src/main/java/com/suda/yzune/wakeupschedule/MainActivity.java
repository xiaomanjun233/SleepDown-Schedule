package com.suda.yzune.wakeupschedule;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String SOURCE_PACKAGE = "com.xiaomanjun.sleepdownschedule";
    private static final String WARM_UP_ACTION =
            "com.suda.yzune.wakeupschedule.action.WARM_UP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WakeUpProxyProvider.notifySystem(this);
        if (WARM_UP_ACTION.equals(getIntent().getAction())) {
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(SOURCE_PACKAGE);
        if (launchIntent == null) {
            Toast.makeText(this, R.string.source_app_missing, Toast.LENGTH_LONG).show();
        } else {
            startActivity(launchIntent);
        }
        finish();
    }
}
