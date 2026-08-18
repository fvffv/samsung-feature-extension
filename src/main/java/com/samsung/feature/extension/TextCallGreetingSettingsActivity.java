package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings UI for the two Samsung Text Call greeting modes. */
public final class TextCallGreetingSettingsActivity extends Activity {
    private EditText consentGreetingInput;
    private EditText nonConsentGreetingInput;
    private Switch playRemoteAudioSwitch;
    private TextView statusValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("文本通话开场语");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("文本通话开场语");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrapParams());

        TextView description = new TextView(this);
        description.setText("自定义内容会在来电接听、主动拨号和通话中切换为文本通话时播报。留空即可保留三星的系统默认文案，也可选择继续播放对方说话声音。");
        description.setTextColor(Color.rgb(92, 99, 111));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(12), 0, dp(18));
        root.addView(description, matchWrapParams());

        LinearLayout card = newCard();
        card.addView(sectionTitle("两套原生开场语"), matchWrapParams());
        card.addView(helpText("语音转文字模式对应“将您的语音转换为文本并回复您”；代为说话模式对应“正在使用语音助手替我说话”。"),
                matchWrapParams());

        card.addView(label("语音转文字模式开场语"), topParams(16));
        consentGreetingInput = makeInput(
                "留空使用默认：您好。我正在使用语音助手将您的语音转换为文本并回复您……");
        card.addView(consentGreetingInput, matchWrapParams());

        card.addView(label("代为说话模式开场语"), topParams(16));
        nonConsentGreetingInput = makeInput(
                "留空使用默认：您好。我正在使用语音助手替我说话。请告诉我您来电的原因。");
        card.addView(nonConsentGreetingInput, matchWrapParams());

        playRemoteAudioSwitch = new Switch(this);
        playRemoteAudioSwitch.setText("文本通话时播放对方说话声音");
        playRemoteAudioSwitch.setTextSize(16);
        playRemoteAudioSwitch.setTextColor(Color.rgb(24, 29, 36));
        playRemoteAudioSwitch.setPadding(0, dp(14), 0, 0);
        card.addView(playRemoteAudioSwitch, matchWrapParams());
        card.addView(helpText("默认关闭。开启后，对方的原始语音会继续从当前通话输出设备播放，同时仍保留语音转文字内容。"),
                matchWrapParams());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);

        Button save = new Button(this);
        save.setText("保存");
        save.setAllCaps(false);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });
        buttons.addView(save, wrapWrapParams());

        Button restore = new Button(this);
        restore.setText("恢复系统默认");
        restore.setAllCaps(false);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextCallGreetingSettingsProvider.setSettings(
                        TextCallGreetingSettingsActivity.this, "", "", false);
                refreshState();
                Toast.makeText(TextCallGreetingSettingsActivity.this,
                        LanguageManager.text(TextCallGreetingSettingsActivity.this,
                                "已恢复系统默认开场语"), Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams restoreParams = wrapWrapParams();
        restoreParams.setMargins(dp(10), 0, 0, 0);
        buttons.addView(restore, restoreParams);
        card.addView(buttons, matchWrapParams());

        statusValue = new TextView(this);
        statusValue.setTextSize(13);
        statusValue.setLineSpacing(dp(2), 1.0f);
        statusValue.setPadding(0, dp(12), 0, 0);
        card.addView(statusValue, matchWrapParams());

        root.addView(card, matchWrapParams());
        setContentView(scrollView);
        refreshState();
        LanguageManager.applyToActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
        LanguageManager.applyToActivity(this);
    }

    private void saveSettings() {
        TextCallGreetingSettingsProvider.setSettings(
                this,
                textOf(consentGreetingInput),
                textOf(nonConsentGreetingInput),
                playRemoteAudioSwitch != null && playRemoteAudioSwitch.isChecked());
        refreshState();
        Toast.makeText(this, LanguageManager.text(this,
                "已保存，下次启动文本通话时生效"), Toast.LENGTH_SHORT).show();
    }

    private void refreshState() {
        TextCallGreetingSettingsProvider.Settings settings =
                TextCallGreetingSettingsProvider.getLocalSettings(this);
        if (consentGreetingInput != null) {
            consentGreetingInput.setText(settings.consentGreeting);
        }
        if (nonConsentGreetingInput != null) {
            nonConsentGreetingInput.setText(settings.nonConsentGreeting);
        }
        if (playRemoteAudioSwitch != null) {
            playRemoteAudioSwitch.setChecked(settings.playRemoteAudio);
        }
        if (statusValue != null) {
            boolean consentCustom = settings.consentGreeting.length() > 0;
            boolean nonConsentCustom = settings.nonConsentGreeting.length() > 0;
            if (LanguageManager.isEnglish(this)) {
                if (!consentCustom && !nonConsentCustom) {
                    statusValue.setText("Current: both opening messages use Samsung defaults. "
                            + (settings.playRemoteAudio
                            ? "Playing the caller's voice is enabled."
                            : "The caller's voice is muted."));
                    statusValue.setTextColor(Color.rgb(92, 99, 111));
                } else {
                    statusValue.setText("Customized: "
                            + (consentCustom ? "speech-to-text mode" : "")
                            + (consentCustom && nonConsentCustom ? ", " : "")
                            + (nonConsentCustom ? "speak-for-me mode" : "")
                            + "; "
                            + (settings.playRemoteAudio
                            ? "playing the caller's voice is enabled."
                            : "the caller's voice is muted."));
                    statusValue.setTextColor(Color.rgb(22, 163, 74));
                }
                return;
            }
            if (!consentCustom && !nonConsentCustom) {
                statusValue.setText("当前：两套开场语均使用三星系统默认内容。"
                        + (settings.playRemoteAudio ? "已开启播放对方声音。" : "对方声音保持关闭。"));
                statusValue.setTextColor(Color.rgb(92, 99, 111));
            } else {
                statusValue.setText("当前已自定义："
                        + (consentCustom ? "语音转文字模式" : "")
                        + (consentCustom && nonConsentCustom ? "、" : "")
                        + (nonConsentCustom ? "代为说话模式" : "")
                        + "；"
                        + (settings.playRemoteAudio ? "已开启播放对方声音。" : "对方声音保持关闭。"));
                statusValue.setTextColor(Color.rgb(22, 163, 74));
            }
        }
    }

    private EditText makeInput(String hint) {
        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setTextColor(Color.rgb(27, 34, 45));
        input.setHintTextColor(Color.rgb(130, 136, 146));
        input.setHint(hint);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        return input;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(20, 24, 31));
        view.setTextSize(18);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView helpText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(92, 99, 111));
        view.setTextSize(13);
        view.setLineSpacing(dp(2), 1.0f);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(24, 29, 36));
        view.setTextSize(16);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), Color.rgb(224, 228, 236));
        card.setBackground(background);
        return card;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topParams(int topMargin) {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.setMargins(0, dp(topMargin), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams wrapWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static String textOf(EditText input) {
        return input != null && input.getText() != null ? input.getText().toString() : "";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
