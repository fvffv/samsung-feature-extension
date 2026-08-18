package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** UI for keeping Android's app compatibility-policy developer switch enabled. */
public final class CompatibilityPolicySettingsActivity extends Activity {
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(LanguageManager.text(this, "持续禁用应用兼容策略"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText(LanguageManager.text(this, "持续禁用应用兼容策略"));
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, wrap());

        TextView description = new TextView(this);
        description.setText(LanguageManager.text(this,
                "保持开发者选项中的“Disable app compatibility policies”处于开启状态。开启后模块会在系统设置写回该值，避免它运行一段时间后自动关闭。"));
        description.setTextColor(Color.rgb(92, 99, 111));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(12), 0, dp(18));
        root.addView(description, wrap());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText(LanguageManager.text(this, "保持 Disable app compatibility policies"));
        label.setTextColor(Color.rgb(24, 29, 36));
        label.setTextSize(16);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(CompatibilityPolicySettingsProvider.isEnabled(this));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                CompatibilityPolicySettingsProvider.setEnabled(
                        CompatibilityPolicySettingsActivity.this, isChecked);
                updateStatus(isChecked);
            }
        });
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(row, wrap());

        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setPadding(0, dp(8), 0, 0);
        card.addView(statusView, wrap());
        root.addView(card, wrap());

        setContentView(root);
        updateStatus(toggle.isChecked());
        LanguageManager.applyToActivity(this);
    }

    private void updateStatus(boolean enabled) {
        if (statusView == null) {
            return;
        }
        statusView.setText(LanguageManager.text(this, enabled
                ? "已开启：系统设置会持续保持该策略为禁用。"
                : "已关闭：不再干预系统的应用兼容策略设置。"));
        statusView.setTextColor(enabled ? Color.rgb(22, 163, 74) : Color.rgb(92, 99, 111));
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
