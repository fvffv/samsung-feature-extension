package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CameraBitrateSettingsActivity extends Activity {
    private final Switch[] videoSwitches = new Switch[CameraBitrateSettingsProvider.VIDEO_COUNT];
    private final EditText[] videoInputs = new EditText[CameraBitrateSettingsProvider.VIDEO_COUNT];
    private TextView observedValue;
    private TextView statusValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("相机视频码率");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("相机视频码率");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrapParams());

        TextView description = new TextView(this);
        description.setText("按 8K、4K、FHD 分别控制视频录制码率。检测方法：先打开此页面，然后打开相机视频录制界面（无需录制）即可读取默认码率，修改后的码率请以实际录制出来的为准！");
        description.setTextColor(Color.rgb(92, 99, 111));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(12), 0, dp(18));
        root.addView(description, matchWrapParams());

        root.addView(buildObservedCard(), matchWrapParams());
        root.addView(buildVideoCard(), cardParams());

        setContentView(scrollView);
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private View buildObservedCard() {
        LinearLayout card = newCard();
        card.addView(sectionTitle("最近检测到的默认码率"), matchWrapParams());

        TextView help = new TextView(this);
        help.setText("检测方法：先打开此页面，然后打开相机视频录制界面（无需录制）即可读取默认码率，修改后的码率请以实际录制出来的为准！");
        help.setTextColor(Color.rgb(92, 99, 111));
        help.setTextSize(13);
        help.setLineSpacing(dp(3), 1.0f);
        help.setPadding(0, dp(10), 0, dp(8));
        card.addView(help, matchWrapParams());

        observedValue = new TextView(this);
        observedValue.setTextColor(Color.rgb(27, 34, 45));
        observedValue.setTextSize(14);
        observedValue.setLineSpacing(dp(3), 1.0f);
        observedValue.setPadding(0, dp(4), 0, dp(12));
        card.addView(observedValue, matchWrapParams());

        Button refresh = new Button(this);
        refresh.setText("刷新检测结果");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshState();
            }
        });
        card.addView(refresh, wrapWrapParams());
        return card;
    }

    private View buildVideoCard() {
        LinearLayout card = newCard();
        card.addView(sectionTitle("视频录制码率"), matchWrapParams());
        addVideoRow(card, CameraBitrateSettingsProvider.VIDEO_8K, "8K", "目标 Mbps");
        addVideoRow(card, CameraBitrateSettingsProvider.VIDEO_4K, "4K / UHD", "目标 Mbps");
        addVideoRow(card, CameraBitrateSettingsProvider.VIDEO_FHD, "FHD", "目标 Mbps");
        addSaveResetRow(card);

        statusValue = new TextView(this);
        statusValue.setTextSize(13);
        statusValue.setPadding(0, dp(12), 0, 0);
        card.addView(statusValue, matchWrapParams());
        return card;
    }

    private void addVideoRow(LinearLayout card, final int category, String label, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);

        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(Color.rgb(24, 29, 36));
        toggle.setTextSize(16);
        row.addView(toggle, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(hint);
        input.setGravity(Gravity.CENTER);
        row.addView(input, new LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT));

        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateInputEnabled(videoInputs[category], isChecked);
            }
        });
        videoSwitches[category] = toggle;
        videoInputs[category] = input;
        card.addView(row, matchWrapParams());
    }

    private void addSaveResetRow(LinearLayout card) {
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(14), 0, 0);

        Button save = new Button(this);
        save.setText("保存");
        save.setAllCaps(false);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        buttons.addView(save, wrapWrapParams());

        Button reset = new Button(this);
        reset.setText("全部恢复默认");
        reset.setAllCaps(false);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraBitrateSettingsProvider.setSettings(
                        CameraBitrateSettingsActivity.this,
                        new boolean[CameraBitrateSettingsProvider.VIDEO_COUNT],
                        new int[CameraBitrateSettingsProvider.VIDEO_COUNT]);
                refreshState();
                Toast.makeText(CameraBitrateSettingsActivity.this, "已恢复系统默认", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams resetParams = wrapWrapParams();
        resetParams.setMargins(dp(10), 0, 0, 0);
        buttons.addView(reset, resetParams);
        card.addView(buttons, matchWrapParams());
    }

    private void refreshState() {
        CameraBitrateSettingsProvider.Settings settings =
                CameraBitrateSettingsProvider.getLocalSettings(this);
        for (int i = 0; i < CameraBitrateSettingsProvider.VIDEO_COUNT; i++) {
            videoSwitches[i].setChecked(settings.videoEnabled[i]);
            videoInputs[i].setText(settings.videoTargetMbps[i] > 0 ? String.valueOf(settings.videoTargetMbps[i]) : "");
            updateInputEnabled(videoInputs[i], settings.videoEnabled[i]);
        }
        updateObserved(settings);
        updateStatus(settings);
    }

    private void saveSettings() {
        boolean[] videoEnabled = new boolean[CameraBitrateSettingsProvider.VIDEO_COUNT];
        int[] videoTarget = new int[CameraBitrateSettingsProvider.VIDEO_COUNT];

        for (int i = 0; i < videoEnabled.length; i++) {
            videoEnabled[i] = videoSwitches[i].isChecked();
            videoTarget[i] = parseInt(videoInputs[i], 0, 1000);
            if (videoEnabled[i] && videoTarget[i] <= 0) {
                Toast.makeText(this, "启用视频码率前请填写 Mbps", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        CameraBitrateSettingsProvider.setSettings(this, videoEnabled, videoTarget);
        refreshState();
        Toast.makeText(this, "已保存，相机重启后更稳", Toast.LENGTH_SHORT).show();
    }

    private int parseInt(EditText input, int min, int max) {
        String value = input.getText() != null ? input.getText().toString().trim() : "";
        if (value.length() == 0) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void updateInputEnabled(EditText input, boolean enabled) {
        if (input == null) {
            return;
        }
        input.setEnabled(enabled);
        input.setAlpha(enabled ? 1f : 0.55f);
    }

    private void updateObserved(CameraBitrateSettingsProvider.Settings settings) {
        StringBuilder builder = new StringBuilder();
        builder.append("视频默认码率\n");
        for (int i = 0; i < CameraBitrateSettingsProvider.VIDEO_COUNT; i++) {
            appendVideoObserved(builder, settings, i);
        }
        observedValue.setText(builder.toString());
    }

    private void appendVideoObserved(StringBuilder builder, CameraBitrateSettingsProvider.Settings settings, int category) {
        builder.append(CameraBitrateSettingsProvider.videoLabel(category)).append(": ");
        if (settings.videoLastBitrateBps[category] <= 0L) {
            builder.append("尚未检测到\n");
            return;
        }
        builder.append(settings.videoLastWidth[category])
                .append(" x ")
                .append(settings.videoLastHeight[category]);
        if (settings.videoLastFps[category] > 0) {
            builder.append(" @ ").append(settings.videoLastFps[category]).append(" fps");
        }
        builder.append(", ")
                .append(formatMbps(settings.videoLastBitrateBps[category]))
                .append(" Mbps");
        appendTime(builder, settings.videoLastTimeMillis[category]);
        builder.append('\n');
    }

    private void appendTime(StringBuilder builder, long timeMillis) {
        if (timeMillis > 0L) {
            builder.append(", ")
                    .append(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
                            .format(new Date(timeMillis)));
        }
    }

    private void updateStatus(CameraBitrateSettingsProvider.Settings settings) {
        StringBuilder builder = new StringBuilder("当前启用：");
        boolean any = false;
        for (int i = 0; i < CameraBitrateSettingsProvider.VIDEO_COUNT; i++) {
            if (settings.videoEnabled[i] && settings.videoTargetMbps[i] > 0) {
                if (any) {
                    builder.append("；");
                }
                builder.append(CameraBitrateSettingsProvider.videoLabel(i))
                        .append(" ")
                        .append(settings.videoTargetMbps[i])
                        .append("Mbps");
                any = true;
            }
        }
        if (!any) {
            builder.append("保持系统默认，只记录检测结果。");
            statusValue.setTextColor(Color.rgb(92, 99, 111));
        } else {
            statusValue.setTextColor(Color.rgb(22, 163, 74));
        }
        statusValue.setText(builder.toString());
    }

    private String formatMbps(long bitrateBps) {
        double mbps = bitrateBps / 1000000.0d;
        if (Math.abs(mbps - Math.round(mbps)) < 0.05d) {
            return String.valueOf(Math.round(mbps));
        }
        return String.format(Locale.US, "%.1f", mbps);
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(20, 24, 31));
        view.setTextSize(18);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), Color.rgb(224, 228, 236));
        card.setBackground(drawable);
        return card;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
