package com.attackmode.planner;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RemindersPlugin.class);
        super.onCreate(savedInstanceState);
        captureUrl(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureUrl(intent);
    }

    private void captureUrl(Intent intent) {
        if (intent == null) {
            return;
        }
        String url = intent.getStringExtra(ReminderNotifier.EXTRA_URL);
        if (url != null && !url.isEmpty()) {
            RemindersPlugin.pendingUrl = url;
            intent.removeExtra(ReminderNotifier.EXTRA_URL);
        }
    }
}
