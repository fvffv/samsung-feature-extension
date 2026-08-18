package com.samsung.feature.extension;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public final class ChargingDisplaySettingsActivity extends Activity {
    private static final int REQ_PLUG_SOUND = 8101;
    private static final int REQ_UNPLUG_SOUND = 8102;

    private Switch displaySwitch;
    private EditText contentTemplateInput;
    private TextView previewTitle;
    private TextView previewContent;
    private Spinner plugModeSpinner;
    private Spinner unplugModeSpinner;
    private TextView plugSoundValue;
    private TextView unplugSoundValue;
    private TextView systemPlugValue;
    private TextView systemUnplugValue;

    private MediaPlayer previewPlayer;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("充电显示与提示音");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("充电显示与提示音");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("修改 SystemUI 里的充电通知文字，并设置插入/拔出充电器时的提示音。修改后需要重启 SystemUI 或重启手机让 hook 生效。");
        subtitle.setTextColor(Color.rgb(98, 105, 117));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle, matchWrap());

        root.addView(buildDisplayCard(), cardParams(0));
        root.addView(buildSoundCard(), cardParams(dp(14)));

        setContentView(scrollView);
        bindSettings();
        LanguageManager.applyToActivity(this);
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        String soundType = requestCode == REQ_PLUG_SOUND
                ? ChargingStyleSettingsProvider.SOUND_PLUG
                : requestCode == REQ_UNPLUG_SOUND
                ? ChargingStyleSettingsProvider.SOUND_UNPLUG
                : null;
        if (soundType == null) {
            return;
        }
        boolean saved = ChargingStyleSettingsProvider.saveSoundFile(this, data.getData(), soundType);
        Toast.makeText(this, saved ? "已保存提示音" : "保存提示音失败", Toast.LENGTH_SHORT).show();
        bindSettings();
    }

    private View buildDisplayCard() {
        LinearLayout card = makeCard();

        displaySwitch = new Switch(this);
        displaySwitch.setText("启用自定义充电显示内容");
        displaySwitch.setTextColor(Color.rgb(20, 24, 31));
        displaySwitch.setTextSize(16);
        displaySwitch.setPadding(0, 0, 0, dp(12));
        displaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!binding) {
                saveDisplaySettings();
            }
        });
        card.addView(displaySwitch, matchWrap());

        card.addView(label("内容模板"));
        contentTemplateInput = makeEditText(true);
        card.addView(contentTemplateInput, matchWrap());

        TextView variables = new TextView(this);
        variables.setText("变量：{level} 电量，{time} 剩余时间，{time_min} 剩余分钟，{type} 充电类型，{plug} 接入方式，{status} 状态，{current} 电流 mA，{voltage} 电压 V，{power} 功率 W，{temp} 温度，{system} 系统原文。");
        variables.setTextColor(Color.rgb(98, 105, 117));
        variables.setTextSize(13);
        variables.setPadding(0, dp(10), 0, dp(10));
        card.addView(variables, matchWrap());

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(14), dp(12), dp(14), dp(12));
        preview.setBackground(makePreviewBackground());
        previewTitle = new TextView(this);
        previewTitle.setTextColor(Color.rgb(20, 24, 31));
        previewTitle.setTextSize(16);
        previewTitle.setIncludeFontPadding(false);
        previewContent = new TextView(this);
        previewContent.setTextColor(Color.rgb(84, 92, 105));
        previewContent.setTextSize(14);
        previewContent.setPadding(0, dp(6), 0, 0);
        preview.addView(previewTitle, matchWrap());
        preview.addView(previewContent, matchWrap());
        card.addView(preview, matchWrap());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        contentTemplateInput.addTextChangedListener(watcher);

        LinearLayout row = actionRow();
        Button save = button("保存显示设置");
        save.setOnClickListener(v -> {
            saveDisplaySettings();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        });
        Button reset = button("恢复默认模板");
        reset.setOnClickListener(v -> {
            contentTemplateInput.setText(ChargingStyleSettingsProvider.DEFAULT_CONTENT_TEMPLATE);
            saveDisplaySettings();
        });
        row.addView(save, actionButtonParams());
        row.addView(reset, actionButtonParams());
        card.addView(row, matchWrap());

        return card;
    }

    private View buildSoundCard() {
        LinearLayout card = makeCard();

        TextView title = cardTitle("提示音设置");
        card.addView(title, matchWrap());

        card.addView(label("插入充电器提示音"));
        plugModeSpinner = makeSpinner(new String[]{"跟随系统", "使用自定义文件", "关闭插入提示音"});
        card.addView(plugModeSpinner, matchWrap());
        plugSoundValue = valueText();
        card.addView(plugSoundValue, matchWrap());
        LinearLayout plugActions = actionRow();
        Button choosePlug = button("选择插入音");
        choosePlug.setOnClickListener(v -> chooseSound(REQ_PLUG_SOUND));
        Button clearPlug = button("清除插入音");
        clearPlug.setOnClickListener(v -> {
            ChargingStyleSettingsProvider.clearSoundFile(this, ChargingStyleSettingsProvider.SOUND_PLUG);
            bindSettings();
        });
        plugActions.addView(choosePlug, actionButtonParams());
        plugActions.addView(clearPlug, actionButtonParams());
        card.addView(plugActions, matchWrap());

        card.addView(label("拔出充电器提示音"));
        unplugModeSpinner = makeSpinner(new String[]{"使用系统拔出音", "使用自定义文件", "关闭拔出提示音"});
        card.addView(unplugModeSpinner, matchWrap());
        unplugSoundValue = valueText();
        card.addView(unplugSoundValue, matchWrap());
        LinearLayout unplugActions = actionRow();
        Button chooseUnplug = button("选择拔出音");
        chooseUnplug.setOnClickListener(v -> chooseSound(REQ_UNPLUG_SOUND));
        Button clearUnplug = button("清除拔出音");
        clearUnplug.setOnClickListener(v -> {
            ChargingStyleSettingsProvider.clearSoundFile(this, ChargingStyleSettingsProvider.SOUND_UNPLUG);
            bindSettings();
        });
        unplugActions.addView(chooseUnplug, actionButtonParams());
        unplugActions.addView(clearUnplug, actionButtonParams());
        card.addView(unplugActions, matchWrap());

        TextView systemTitle = label("当前系统提示音");
        systemTitle.setPadding(0, dp(16), 0, dp(6));
        card.addView(systemTitle);
        systemPlugValue = valueText();
        systemUnplugValue = valueText();
        card.addView(systemPlugValue, matchWrap());
        card.addView(systemUnplugValue, matchWrap());

        LinearLayout systemActions = actionRow();
        Button playPlug = button("播放系统插入音");
        playPlug.setOnClickListener(v -> playPath(ChargingStyleSettingsProvider.currentSystemPlugSound(this)));
        Button playUnplug = button("播放系统拔出音");
        playUnplug.setOnClickListener(v -> playPath(ChargingStyleSettingsProvider.currentSystemUnplugSound(this)));
        systemActions.addView(playPlug, actionButtonParams());
        systemActions.addView(playUnplug, actionButtonParams());
        card.addView(systemActions, matchWrap());

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding) {
                    saveSoundSettings();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        plugModeSpinner.setOnItemSelectedListener(listener);
        unplugModeSpinner.setOnItemSelectedListener(listener);

        return card;
    }

    private void bindSettings() {
        binding = true;
        ChargingStyleSettingsProvider.SettingsSnapshot settings =
                ChargingStyleSettingsProvider.getLocalSettings(this);
        displaySwitch.setChecked(settings.displayEnabled);
        contentTemplateInput.setText(settings.contentTemplate);
        plugModeSpinner.setSelection(settings.plugSoundMode);
        unplugModeSpinner.setSelection(unplugSpinnerPosition(settings.unplugSoundMode));
        plugSoundValue.setText(LanguageManager.text(this, settings.plugSoundAvailable
                ? "自定义文件：" + settings.plugSoundName
                : "未选择自定义插入音"));
        unplugSoundValue.setText(LanguageManager.text(this, settings.unplugSoundAvailable
                ? "自定义文件：" + settings.unplugSoundName
                : "未选择自定义拔出音"));
        systemPlugValue.setText(LanguageManager.text(this, "\u7cfb\u7edf\u63d2\u5165\u97f3\uff1a\n\u666e\u901a\uff1a"
                + ChargingStyleSettingsProvider.currentSystemPlugSound(this)
                + "\n\u5feb\u5145\uff1a"
                + ChargingStyleSettingsProvider.currentSystemFastPlugSound(this)));
        String systemUnplug = ChargingStyleSettingsProvider.currentSystemUnplugSound(this);
        systemUnplugValue.setText(LanguageManager.text(this, systemUnplug.length() > 0
                ? "\u7cfb\u7edf\u62d4\u51fa\u97f3\uff1a" + systemUnplug
                : "\u7cfb\u7edf\u672a\u53d1\u73b0\u72ec\u7acb\u7684\u5145\u7535\u62d4\u51fa\u63d0\u793a\u97f3\uff0c\u53ef\u4f7f\u7528\u81ea\u5b9a\u4e49\u62d4\u51fa\u97f3"));
        binding = false;
        updatePreview();
    }

    private void saveDisplaySettings() {
        ChargingStyleSettingsProvider.SettingsSnapshot oldSettings =
                ChargingStyleSettingsProvider.getLocalSettings(this);
        ChargingStyleSettingsProvider.SettingsSnapshot next =
                new ChargingStyleSettingsProvider.SettingsSnapshot(
                        displaySwitch.isChecked(),
                        ChargingStyleSettingsProvider.DEFAULT_TITLE_TEMPLATE,
                        textOf(contentTemplateInput),
                        oldSettings.plugSoundMode,
                        oldSettings.unplugSoundMode,
                        oldSettings.plugSoundName,
                        oldSettings.unplugSoundName,
                        oldSettings.plugSoundAvailable,
                        oldSettings.unplugSoundAvailable,
                        oldSettings.updatedAt);
        ChargingStyleSettingsProvider.setSettings(this, next);
        updatePreview();
    }

    private void saveSoundSettings() {
        ChargingStyleSettingsProvider.SettingsSnapshot oldSettings =
                ChargingStyleSettingsProvider.getLocalSettings(this);
        ChargingStyleSettingsProvider.SettingsSnapshot next =
                new ChargingStyleSettingsProvider.SettingsSnapshot(
                        oldSettings.displayEnabled,
                        ChargingStyleSettingsProvider.DEFAULT_TITLE_TEMPLATE,
                        oldSettings.contentTemplate,
                        plugModeSpinner.getSelectedItemPosition(),
                        unplugModeFromSpinner(unplugModeSpinner.getSelectedItemPosition()),
                        oldSettings.plugSoundName,
                        oldSettings.unplugSoundName,
                        oldSettings.plugSoundAvailable,
                        oldSettings.unplugSoundAvailable,
                        oldSettings.updatedAt);
        ChargingStyleSettingsProvider.setSettings(this, next);
        bindSettings();
    }

    private void updatePreview() {
        if (previewTitle == null || previewContent == null) {
            return;
        }
        ChargingStyleSettingsProvider.BatteryValues values =
                ChargingStyleSettingsProvider.currentBatteryValues(this);
        String content = ChargingStyleSettingsProvider.formatTemplate(
                textOf(contentTemplateInput), values, "系统内容");
        previewTitle.setText(LanguageManager.text(this, "预览"));
        previewContent.setText(LanguageManager.text(this,
                content.length() == 0 ? "还剩 XX 分钟充满电" : content));
    }

    private void chooseSound(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(intent, requestCode);
        } catch (Throwable first) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            startActivityForResult(fallback, requestCode);
        }
    }

    private void playPath(String path) {
        if (path == null || path.trim().length() == 0) {
            Toast.makeText(this, "没有读取到提示音路径", Toast.LENGTH_SHORT).show();
            return;
        }
        stopPreview();
        try {
            previewPlayer = new MediaPlayer();
            previewPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            String trimmed = path.trim();
            if (trimmed.startsWith("/")) {
                previewPlayer.setDataSource(trimmed);
            } else {
                previewPlayer.setDataSource(this, Uri.parse(trimmed));
            }
            previewPlayer.setOnCompletionListener(mp -> stopPreview());
            previewPlayer.prepare();
            previewPlayer.start();
        } catch (Throwable throwable) {
            stopPreview();
            Toast.makeText(this, "播放失败：" + throwable.getClass().getSimpleName(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        if (previewPlayer != null) {
            try {
                previewPlayer.stop();
            } catch (Throwable ignored) {
                // Ignore.
            }
            try {
                previewPlayer.release();
            } catch (Throwable ignored) {
                // Ignore.
            }
            previewPlayer = null;
        }
    }

    private static int unplugSpinnerPosition(int mode) {
        if (mode == ChargingStyleSettingsProvider.SOUND_MODE_CUSTOM) {
            return 1;
        }
        if (mode == ChargingStyleSettingsProvider.SOUND_MODE_OFF) {
            return 2;
        }
        return 0;
    }

    private static int unplugModeFromSpinner(int position) {
        if (position == 1) {
            return ChargingStyleSettingsProvider.SOUND_MODE_CUSTOM;
        }
        if (position == 2) {
            return ChargingStyleSettingsProvider.SOUND_MODE_OFF;
        }
        return ChargingStyleSettingsProvider.SOUND_MODE_SYSTEM;
    }

    private static String textOf(EditText editText) {
        return editText == null || editText.getText() == null ? "" : editText.getText().toString();
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(makeCardBackground());
        return card;
    }

    private TextView cardTitle(String text) {
        TextView view = new TextView(this);
        view.setText(LanguageManager.text(this, text));
        view.setTextColor(Color.rgb(20, 24, 31));
        view.setTextSize(18);
        view.setIncludeFontPadding(false);
        view.setPadding(0, 0, 0, dp(12));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(LanguageManager.text(this, text));
        view.setTextColor(Color.rgb(46, 52, 64));
        view.setTextSize(14);
        view.setPadding(0, dp(10), 0, dp(6));
        return view;
    }

    private TextView valueText() {
        TextView view = new TextView(this);
        view.setTextColor(Color.rgb(98, 105, 117));
        view.setTextSize(13);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private EditText makeEditText(boolean multiLine) {
        EditText editText = new EditText(this);
        editText.setTextSize(15);
        editText.setTextColor(Color.rgb(20, 24, 31));
        editText.setSingleLine(!multiLine);
        editText.setMinLines(multiLine ? 2 : 1);
        editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));
        editText.setBackground(makeInputBackground());
        return editText;
    }

    private Spinner makeSpinner(String[] values) {
        Spinner spinner = new Spinner(this);
        String[] localizedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            localizedValues[i] = LanguageManager.text(this, values[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, localizedValues);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPadding(0, 0, 0, 0);
        return spinner;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(LanguageManager.text(this, text));
        button.setTextSize(14);
        return button;
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        return row;
    }

    private LinearLayout.LayoutParams actionButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardParams(int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private GradientDrawable makeCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setStroke(dp(1), Color.rgb(225, 229, 237));
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable makeInputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(250, 251, 253));
        drawable.setStroke(dp(1), Color.rgb(218, 224, 235));
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private GradientDrawable makePreviewBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(242, 245, 250));
        drawable.setStroke(dp(1), Color.rgb(220, 226, 236));
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
