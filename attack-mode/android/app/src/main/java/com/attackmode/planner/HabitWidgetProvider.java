package com.attackmode.planner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONObject;

public abstract class HabitWidgetProvider extends AppWidgetProvider {

    static final int KIND_CHECK = 0;
    static final int KIND_HISTORY = 1;
    static final int KIND_SCORE = 2;
    static final int KIND_FREQUENCY = 3;

    private static final String PREFS = "attackmode_widgets";

    abstract int kind();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            render(context, manager, id, kind());
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        render(context, manager, id, kind());
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) {
            editor.remove("w" + id);
        }
        editor.apply();
    }

    static void bind(Context context, int widgetId, String habitId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("w" + widgetId, habitId).apply();
    }

    static String boundHabit(Context context, int widgetId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("w" + widgetId, "");
    }

    static int[] size(Context context, AppWidgetManager manager, int id) {
        Bundle options = manager.getAppWidgetOptions(id);
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.max(96, Math.round(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) * density));
        int height = Math.max(96, Math.round(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) * density));
        double scale = Math.sqrt(600000.0 / Math.max(1.0, (double) width * height));
        if (scale < 1.0) {
            width = (int) (width * scale);
            height = (int) (height * scale);
        }
        return new int[] {width, height};
    }

    static void render(Context context, AppWidgetManager manager, int widgetId, int kind) {
        JSONObject snapshot = HabitData.snapshot(context);
        boolean dark = HabitData.isDark(snapshot);
        String habitId = boundHabit(context, widgetId);
        JSONObject habit = HabitData.habit(snapshot, habitId);
        int[] size = size(context, manager, widgetId);

        Bitmap bitmap;
        PendingIntent click;
        if (habit == null) {
            bitmap = WidgetDrawing.message(size[0], size[1], dark, "Tap to pick a habit");
            Intent configure = new Intent(context, WidgetConfigActivity.class);
            configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            configure.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            click = PendingIntent.getActivity(
                context, widgetId, configure, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            JSONObject checks = HabitData.checksFor(snapshot, habitId);
            if (kind == KIND_CHECK) {
                bitmap = WidgetDrawing.check(size[0], size[1], snapshot, habit, checks.optString(HabitData.today(), ""));
                Intent toggle = new Intent(context, WidgetActionReceiver.class);
                toggle.setAction(WidgetActionReceiver.ACTION_TOGGLE);
                toggle.putExtra(ReminderNotifier.EXTRA_HABIT, habitId);
                click = PendingIntent.getBroadcast(
                    context, widgetId, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            } else {
                if (kind == KIND_HISTORY) {
                    bitmap = WidgetDrawing.history(size[0], size[1], snapshot, habit, checks);
                } else if (kind == KIND_SCORE) {
                    bitmap = WidgetDrawing.score(size[0], size[1], snapshot, habit, checks);
                } else {
                    bitmap = WidgetDrawing.frequency(size[0], size[1], snapshot, habit, checks);
                }
                click = openHabit(context, widgetId, "/habits/" + habitId);
            }
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_image);
        views.setImageViewBitmap(R.id.widget_image, bitmap);
        views.setOnClickPendingIntent(R.id.widget_image, click);
        manager.updateAppWidget(widgetId, views);
    }

    static PendingIntent openHabit(Context context, int requestCode, String url) {
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(ReminderNotifier.EXTRA_URL, url);
        return PendingIntent.getActivity(
            context, requestCode, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
