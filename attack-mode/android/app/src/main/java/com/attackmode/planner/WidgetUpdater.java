package com.attackmode.planner;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

final class WidgetUpdater {

    private WidgetUpdater() {}

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        refresh(context, manager, CheckmarkWidgetProvider.class, HabitWidgetProvider.KIND_CHECK);
        refresh(context, manager, HistoryWidgetProvider.class, HabitWidgetProvider.KIND_HISTORY);
        refresh(context, manager, ScoreWidgetProvider.class, HabitWidgetProvider.KIND_SCORE);
        refresh(context, manager, FrequencyWidgetProvider.class, HabitWidgetProvider.KIND_FREQUENCY);

        int[] lists = manager.getAppWidgetIds(new ComponentName(context, ListWidgetProvider.class));
        for (int id : lists) {
            ListWidgetProvider.render(context, manager, id);
        }
    }

    private static void refresh(Context context, AppWidgetManager manager, Class<?> provider, int kind) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int id : ids) {
            HabitWidgetProvider.render(context, manager, id, kind);
        }
    }
}
