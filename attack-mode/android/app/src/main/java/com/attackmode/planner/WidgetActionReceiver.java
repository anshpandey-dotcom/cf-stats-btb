package com.attackmode.planner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

public class WidgetActionReceiver extends BroadcastReceiver {

    static final String ACTION_TOGGLE = "com.attackmode.planner.WIDGET_TOGGLE";
    static final String ACTION_CHECK = "com.attackmode.planner.HABIT_CHECK";
    static final String ACTION_SNOOZE = "com.attackmode.planner.HABIT_SNOOZE";

    private static final long SNOOZE_MILLIS = 10L * 60L * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String habit = intent.getStringExtra(ReminderNotifier.EXTRA_HABIT);
        if (action == null || habit == null || habit.isEmpty()) {
            return;
        }

        if (ACTION_TOGGLE.equals(action)) {
            JSONObject snapshot = HabitData.snapshot(context);
            String today = HabitData.today();
            String current = HabitData.checksFor(snapshot, habit).optString(today, "");
            boolean skipDays = snapshot.optBoolean("skipDays", true);
            HabitData.apply(context, habit, today, HabitData.nextState(current, skipDays));
            WidgetUpdater.updateAll(context);
            return;
        }

        int id = intent.getIntExtra(ReminderNotifier.EXTRA_ID, 0);
        NotificationManagerCompat.from(context).cancel(id);

        if (ACTION_CHECK.equals(action)) {
            HabitData.apply(context, habit, HabitData.today(), "y");
            WidgetUpdater.updateAll(context);
        } else if (ACTION_SNOOZE.equals(action)) {
            ReminderNotifier.snooze(
                context,
                id,
                intent.getStringExtra(ReminderNotifier.EXTRA_TITLE),
                intent.getStringExtra(ReminderNotifier.EXTRA_BODY),
                intent.getStringExtra(ReminderNotifier.EXTRA_URL),
                habit,
                System.currentTimeMillis() + SNOOZE_MILLIS
            );
        }
    }
}
