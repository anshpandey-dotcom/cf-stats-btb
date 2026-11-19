package com.attackmode.planner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ReminderNotifier.ACTION_FIRE.equals(intent.getAction())) {
            return;
        }
        ReminderNotifier.show(
            context,
            intent.getIntExtra(ReminderNotifier.EXTRA_ID, 0),
            intent.getStringExtra(ReminderNotifier.EXTRA_TITLE),
            intent.getStringExtra(ReminderNotifier.EXTRA_BODY),
            intent.getStringExtra(ReminderNotifier.EXTRA_URL)
        );
        ReminderNotifier.advance(context, intent.getIntExtra(ReminderNotifier.EXTRA_ID, 0));
    }
}
