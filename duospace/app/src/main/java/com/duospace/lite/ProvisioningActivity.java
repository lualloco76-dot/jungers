package com.duospace.lite;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public final class ProvisioningActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String action = getIntent().getAction();
        if (DevicePolicyManager.ACTION_GET_PROVISIONING_MODE.equals(action)) {
            Intent result = new Intent();
            result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE);
            setResult(RESULT_OK,result);
        } else {
            if (DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL.equals(action) ||
                    DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE.equals(action)) {
                ProfileAdminReceiver.finishSetup(this);
            }
            setResult(RESULT_OK);
        }
        finish();
    }
}
