package com.attackmode.planner;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListWidgetProvider extends AppWidgetProvider {

    private static final int[] ROWS = {
        R.id.list_row0, R.id.list_row1, R.id.list_row2, R.id.list_row3, R.id.list_row4, R.id.list_row5
    };
    private static final int[] NAMES = {
        R.id.list_name0, R.id.list_name1, R.id.list_name2, R.id.list_name3, R.id.list_name4, R.id.list_name5
    };
    private static final int[] CHECKS = {
        R.id.list_check0, R.id.list_check1, R.id.list_check2, R.id.list_check3, R.id.list_check4, R.id.list_check5
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            render(context, manager, id);
        }
    }

    static void render(Context context, AppWidgetManager manager, int widgetId) {
        JSONObject snapshot = HabitData.snapshot(context);
        boolean dark = HabitData.isDark(snapshot);
        String today = HabitData.today();

        List<JSONObject> habits = new ArrayList<>();
        JSONArray all = snapshot.optJSONArray("habits");
        if (all != null) {
            for (int index = 0; index < all.length(); index++) {
                JSONObject habit = all.optJSONObject(index);
                if (habit != null && !habit.optBoolean("archived", false)) {
                    habits.add(habit);
                }
            }
        }
        Collections.sort(habits, (a, b) -> Integer.compare(a.optInt("order"), b.optInt("order")));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_list);
        views.setInt(R.id.list_root, "setBackgroundColor", WidgetDrawing.paper(dark));
        views.setTextColor(R.id.list_title, WidgetDrawing.ink(dark));
        views.setOnClickPendingIntent(R.id.list_title, HabitWidgetProvider.openHabit(context, widgetId, "/habits"));
        views.setViewVisibility(R.id.list_empty, habits.isEmpty() ? View.VISIBLE : View.GONE);
        views.setTextColor(R.id.list_empty, WidgetDrawing.muted(dark));

        for (int row = 0; row < ROWS.length; row++) {
            if (row >= habits.size()) {
                views.setViewVisibility(ROWS[row], View.GONE);
                continue;
            }
            JSONObject habit = habits.get(row);
            String habitId = habit.optString("id");
            String state = HabitData.checksFor(snapshot, habitId).optString(today, "");

            views.setViewVisibility(ROWS[row], View.VISIBLE);
            views.setTextViewText(NAMES[row], habit.optString("name"));
            views.setTextColor(NAMES[row], WidgetDrawing.ink(dark));
            views.setImageViewBitmap(CHECKS[row], WidgetDrawing.rowCheck(96, snapshot, habit, state));

            Intent toggle = new Intent(context, WidgetActionReceiver.class);
            toggle.setAction(WidgetActionReceiver.ACTION_TOGGLE);
            toggle.putExtra(ReminderNotifier.EXTRA_HABIT, habitId);
            PendingIntent click = PendingIntent.getBroadcast(
                context, widgetId * 16 + row, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(ROWS[row], click);
        }
        manager.updateAppWidget(widgetId, views);
    }
}
