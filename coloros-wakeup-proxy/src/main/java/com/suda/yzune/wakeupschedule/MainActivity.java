package com.suda.yzune.wakeupschedule;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String SOURCE_PACKAGE = "com.xiaomanjun.sleepdownschedule";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WakeUpProxyProvider.notifySystem(this);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(SOURCE_PACKAGE);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
            finish();
            return;
        }

        TextView message = new TextView(this);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        message.setPadding(padding, padding, padding, padding);
        message.setTextSize(18);
        message.setText(R.string.source_app_missing);
        setContentView(message);
        Toast.makeText(this, R.string.source_app_missing, Toast.LENGTH_LONG).show();
    }
}
