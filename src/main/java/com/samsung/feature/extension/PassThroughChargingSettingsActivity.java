package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class PassThroughChargingSettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView systemValueView;
    private Switch enableSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("全局旁路供电");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("全局旁路供电");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("基于 Game Booster 的“游戏时暂停 USB PD 充电”机制，开启后会全局维持系统 pass_through 状态，让支持的 USB PD 充电器直接给手机供电并减少电池充放电。");
        description.setTextColor(Color.rgb(92, 99, 111));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(12), 0, dp(18));
        root.addView(description, matchWrap());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        enableSwitch = new Switch(this);
        enableSwitch.setText("启用全局旁路供电");
        enableSwitch.setTextColor(Color.rgb(24, 29, 36));
        enableSwitch.setTextSize(16);
        enableSwitch.setPadding(0, 0, 0, dp(8));
        card.addView(enableSwitch, matchWrap());

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(92, 99, 111));
        statusView.setTextSize(13);
        statusView.setLineSpacing(dp(2), 1.0f);
        card.addView(statusView, matchWrap());

        systemValueView = new TextView(this);
        systemValueView.setTextColor(Color.rgb(59, 130, 246));
        systemValueView.setTextSize(13);
        systemValueView.setPadding(0, dp(10), 0, 0);
        card.addView(systemValueView, matchWrap());

        root.addView(card, matchWrap());

        TextView note = new TextView(this);
        note.setText("提示：该功能需要手机、充电器和线材都支持三星的 USB PD 旁路供电。开启后如果系统值没有立刻变为 1，请确认 LSPosed 中本模块已勾选“设备健康管理”和“Game Booster/游戏助推器”，然后重启手机。关闭后模块会写回 0，不再干预，Game Booster 自身逻辑继续按系统默认工作。");
        note.setTextColor(Color.rgb(120, 83, 0));
        note.setTextSize(13);
        note.setLineSpacing(dp(3), 1.0f);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, matchWrap());

        enableSwitch.setChecked(PassThroughChargingSettingsProvider.isEnabled(this));
        updateStatus();
        enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PassThroughChargingSettingsProvider.setEnabled(
                        PassThroughChargingSettingsActivity.this,
                        isChecked);
                updateStatus();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus();
                    }
                }, 800L);
            }
        });

        setContentView(scrollView);
    }

    private void updateStatus() {
        boolean enabled = PassThroughChargingSettingsProvider.isEnabled(this);
        if (statusView != null) {
            statusView.setText(enabled
                    ? "当前状态：已启用。模块会在后台维持旁路供电开启。"
                    : "当前状态：已关闭。模块不再全局强制旁路供电。");
        }
        if (systemValueView != null) {
            int value = PassThroughChargingSettingsProvider.readSystemValue(this);
            systemValueView.setText("系统 pass_through 当前值：" + value);
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
