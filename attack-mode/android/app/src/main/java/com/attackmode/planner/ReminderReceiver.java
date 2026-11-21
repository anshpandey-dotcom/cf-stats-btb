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
        int soft = intent.getBooleanExtra(ReminderNotifier.EXTRA_SOFT, false) ? 1 : 0;
        int attempt = intent.getIntExtra(ReminderNotifier.EXTRA_TRY, 0);
        int fired = intent.getIntExtra(ReminderNotifier.EXTRA_ID, 0);
        if (soft == 1 && ReminderNotifier.trayCount(context, fired) >= ReminderNotifier.TRAY_LIMIT) {
            if (attempt < ReminderNotifier.MAX_DEFERS) {
                ReminderNotifier.deferSoft(
                    context, fired,
                    intent.getStringExtra(ReminderNotifier.EXTRA_TITLE),
                    intent.getStringExtra(ReminderNotifier.EXTRA_BODY),
                    intent.getStringExtra(ReminderNotifier.EXTRA_URL),
                    attempt + 1
                );
            }
            return;
        }
        ReminderNotifier.show(
            context,
            intent.getIntExtra(ReminderNotifier.EXTRA_ID, 0),
            intent.getStringExtra(ReminderNotifier.EXTRA_TITLE),
            intent.getStringExtra(ReminderNotifier.EXTRA_BODY),
            intent.getStringExtra(ReminderNotifier.EXTRA_URL),
            intent.getStringExtra(ReminderNotifier.EXTRA_HABIT)
        );
        int firedId = intent.getIntExtra(ReminderNotifier.EXTRA_ID, 0);
        if (firedId == ReminderNotifier.TIMER_ALARM_ID) {
            ReminderNotifier.cancelTimer(context);
        }
        ReminderNotifier.advance(context, firedId);
    }
}
