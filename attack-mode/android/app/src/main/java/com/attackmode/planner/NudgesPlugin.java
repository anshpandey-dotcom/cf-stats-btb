package com.attackmode.planner;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Nudges")
public class NudgesPlugin extends Plugin {

    @PluginMethod
    public void configure(PluginCall call) {
        String json = call.getString("json", "[]");
        NudgeNotifier.saveConfig(getContext(), json);
        boolean unlock = NudgeNotifier.anyUnlock(getContext());
        if (unlock) {
            NudgeService.start(getContext());
        } else {
            NudgeService.stop(getContext());
        }
        JSObject result = new JSObject();
        result.put("unlock", unlock);
        call.resolve(result);
    }

    @PluginMethod
    public void showNow(PluginCall call) {
        NudgeNotifier.show(
            getContext(),
            call.getString("title", "Attack Mode"),
            call.getString("body", "Check your tasks."),
            call.getString("url", "/today")
        );
        call.resolve();
    }
}
