package com.attackmode.planner;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfigActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        final int widgetId = getIntent().getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        );
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        JSONObject snapshot = HabitData.snapshot(this);
        final List<String> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        JSONArray habits = snapshot.optJSONArray("habits");
        if (habits != null) {
            for (int index = 0; index < habits.length(); index++) {
                JSONObject habit = habits.optJSONObject(index);
                if (habit != null && !habit.optBoolean("archived", false)) {
                    ids.add(habit.optString("id"));
                    names.add(habit.optString("name"));
                }
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setTextSize(18f);
        title.setText(ids.isEmpty() ? "Add a habit in Attack Mode first" : "Choose a habit");
        root.addView(title);

        if (!ids.isEmpty()) {
            ListView list = new ListView(this);
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            list.setOnItemClickListener((parent, view, position, itemId) -> {
                HabitWidgetProvider.bind(this, widgetId, ids.get(position));
                AppWidgetManager manager = AppWidgetManager.getInstance(this);
                WidgetUpdater.updateAll(this);
                Intent result = new Intent();
                result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                setResult(RESULT_OK, result);
                finish();
            });
            root.addView(list);
        } else {
            title.setOnClickListener((View view) -> finish());
        }
        setContentView(root);
    }
}
