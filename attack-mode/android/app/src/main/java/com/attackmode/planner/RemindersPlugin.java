package com.attackmode.planner;

import android.Manifest;
import android.os.Build;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;

@CapacitorPlugin(
    name = "Reminders",
    permissions = {
        @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
    }
)
public class RemindersPlugin extends Plugin {

    static volatile String pendingUrl;

    private JSObject status() {
        JSObject result = new JSObject();
        result.put("notifications", ReminderNotifier.canNotify(getContext()));
        return result;
    }

    @PluginMethod
    public void check(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void request(PluginCall call) {
        ReminderNotifier.ensureChannel(getContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && getPermissionState("notifications") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "afterRequest");
            return;
        }
        call.resolve(status());
    }

    @PermissionCallback
    private void afterRequest(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray items = call.getArray("items");
        ReminderNotifier.ensureChannel(getContext());
        ReminderNotifier.scheduleAll(getContext(), items == null ? new JSONArray() : items, true);
        call.resolve();
    }

    @PluginMethod
    public void startTimer(PluginCall call) {
        ReminderNotifier.showTimer(
            getContext(), call.getString("title", "Focus"), call.getString("text", ""), call.getLong("endAt", 0L)
        );
        call.resolve();
    }

    @PluginMethod
    public void stopTimer(PluginCall call) {
        ReminderNotifier.cancelTimer(getContext());
        call.resolve();
    }

    @PluginMethod
    public void keepAwake(PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        getActivity().runOnUiThread(() -> {
            if (on) {
                getActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void scheduleOne(PluginCall call) {
        ReminderNotifier.ensureChannel(getContext());
        ReminderNotifier.scheduleOne(
            getContext(),
            call.getInt("id", 0),
            call.getString("title", ""),
            call.getString("body", ""),
            call.getString("url", "/focus"),
            call.getLong("at", 0L)
        );
        call.resolve();
    }

    @PluginMethod
    public void cancelOne(PluginCall call) {
        ReminderNotifier.cancelOne(getContext(), call.getInt("id", 0));
        call.resolve();
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        ReminderNotifier.cancelAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void test(PluginCall call) {
        ReminderNotifier.show(
            getContext(),
            2000000000,
            "Attack Mode",
            "Reminders are working. Time to attack.",
            "/today"
        );
        call.resolve();
    }

    @PluginMethod
    public void consumeUrl(PluginCall call) {
        JSObject result = new JSObject();
        result.put("url", pendingUrl == null ? "" : pendingUrl);
        pendingUrl = null;
        call.resolve(result);
    }
}
