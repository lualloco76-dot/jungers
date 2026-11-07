package com.duospace.lite;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import java.util.Collections;

public final class ProfileAdminReceiver extends DeviceAdminReceiver {
    static ComponentName admin(Context context) {
        return new ComponentName(context, ProfileAdminReceiver.class);
    }

    static void finishSetup(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        if (dpm == null || !dpm.isProfileOwnerApp(context.getPackageName())) return;
        ComponentName admin = admin(context);
        try {
            dpm.setProfileName(admin, "DuoSpace");
            dpm.setProfileEnabled(admin);
            IntentFilter filter = new IntentFilter();
            filter.addAction(BridgeActivity.ACTION_CLONE);
            filter.addAction(BridgeActivity.ACTION_OPEN_SPACE);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            dpm.addCrossProfileIntentFilter(admin, filter,
                    DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);
            context.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(context, BridgeActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (RuntimeException e) {
            Log.e("DuoSpace", "Profile setup failed", e);
        }
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        finishSetup(context);
    }
}
