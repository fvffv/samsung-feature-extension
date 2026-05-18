package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public final class NfcScreenOffSettingsActivity extends Activity {
    private static final int ID_MODE_OFF = 1001;
    private static final int ID_MODE_SCREEN_ON = 1002;
    private static final int ID_MODE_SCREEN_OFF = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("NFC 刷卡模式");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("NFC 刷卡模式");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = new TextView(this);
        description.setText("选择 NFC 绕过锁屏限制的范围。切换后通常会在几秒内即时生效，不需要重启手机。");
        description.setTextColor(Color.rgb(92, 99, 111));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(12), 0, dp(18));
        root.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(dp(12), dp(10), dp(12), dp(10));
        group.setBackgroundColor(Color.WHITE);

        group.addView(createOption(
                ID_MODE_OFF,
                "关闭",
                "使用系统默认 NFC 行为，需要解锁或亮屏时仍按系统要求处理。"
        ));
        group.addView(createOption(
                ID_MODE_SCREEN_ON,
                "仅亮屏免解锁",
                "屏幕点亮但仍在锁屏界面时可刷卡；熄屏时不绕过系统限制。"
        ));
        group.addView(createOption(
                ID_MODE_SCREEN_OFF,
                "息屏免解锁",
                "熄屏和亮屏锁屏时都按已解锁亮屏状态处理，适合支付宝碰一碰等场景。"
        ));

        group.check(idForMode(NfcSettingsProvider.getMode(this)));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                NfcSettingsProvider.setMode(NfcScreenOffSettingsActivity.this, modeForId(checkedId));
            }
        });

        root.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private RadioButton createOption(int id, String title, String description) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(title + "\n" + description);
        button.setTextColor(Color.rgb(24, 29, 36));
        button.setTextSize(16);
        button.setLineSpacing(dp(3), 1.0f);
        button.setPadding(dp(4), dp(10), dp(4), dp(10));
        button.setMinHeight(dp(64));
        return button;
    }

    private int idForMode(int mode) {
        if (mode == NfcSettingsProvider.MODE_SCREEN_ON_UNLOCKED) {
            return ID_MODE_SCREEN_ON;
        }
        if (mode == NfcSettingsProvider.MODE_SCREEN_OFF_UNLOCKED) {
            return ID_MODE_SCREEN_OFF;
        }
        return ID_MODE_OFF;
    }

    private int modeForId(int id) {
        if (id == ID_MODE_SCREEN_ON) {
            return NfcSettingsProvider.MODE_SCREEN_ON_UNLOCKED;
        }
        if (id == ID_MODE_SCREEN_OFF) {
            return NfcSettingsProvider.MODE_SCREEN_OFF_UNLOCKED;
        }
        return NfcSettingsProvider.MODE_OFF;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
