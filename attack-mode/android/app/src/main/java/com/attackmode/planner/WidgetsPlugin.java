package com.attackmode.planner;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;

@CapacitorPlugin(name = "Widgets")
public class WidgetsPlugin extends Plugin {

    @PluginMethod
    public void sync(PluginCall call) {
        String json = call.getString("json");
        if (json != null) {
            HabitData.saveSnapshot(getContext(), json);
            WidgetUpdater.updateAll(getContext());
        }
        call.resolve();
    }

    @PluginMethod
    public void drain(PluginCall call) {
        JSObject result = new JSObject();
        try {
            result.put("items", new JSONArray(HabitData.drain(getContext())));
        } catch (JSONException error) {
            result.put("items", new JSONArray());
        }
        call.resolve(result);
    }
}
