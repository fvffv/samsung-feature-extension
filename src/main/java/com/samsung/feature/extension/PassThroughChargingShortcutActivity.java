package com.samsung.feature.extension;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public final class PassThroughChargingShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean enabled = !PassThroughChargingSettingsProvider.isEnabled(this);
        PassThroughChargingSettingsProvider.setEnabled(this, enabled);
        Toast.makeText(
                getApplicationContext(),
                enabled ? "旁路供电已开启" : "旁路供电已关闭",
                Toast.LENGTH_SHORT)
                .show();
        finish();
        overridePendingTransition(0, 0);
    }
}
