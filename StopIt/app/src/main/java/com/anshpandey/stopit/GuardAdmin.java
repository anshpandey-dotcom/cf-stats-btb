package com.anshpandey.stopit;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class GuardAdmin extends DeviceAdminReceiver {

    static ComponentName component(Context context) {
        return new ComponentName(context, GuardAdmin.class);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return context.getString(R.string.admin_disable_warning);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        new Store(context).setLocked(false);
    }
}
