package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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

public final class BixbyOpenAiSettingsActivity extends Activity {
    private Switch enableSwitch;
    private EditText baseUrlInput;
    private EditText apiKeyInput;
    private EditText modelInput;
    private EditText promptInput;
    private TextView statusText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Bixby OpenAI 接入");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(22));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Bixby OpenAI 接入");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("把已定位到的 Bixby 大模型配置改为你自己的 Chat Completions 兼容接口，并提供接口连通性测试。接口地址会按你填写的完整 URL 原样请求。");
        subtitle.setTextColor(Color.rgb(92, 99, 111));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(10), 0, dp(18));
        root.addView(subtitle, matchWrap());

        enableSwitch = new Switch(this);
        enableSwitch.setText("启用自定义 OpenAI 兼容后端");
        enableSwitch.setTextColor(Color.rgb(24, 29, 36));
        enableSwitch.setTextSize(16);
        enableSwitch.setPadding(0, 0, 0, dp(12));
        root.addView(enableSwitch, matchWrap());

        baseUrlInput = addInput(root, "接口完整地址", "https://api.openai.com/v1/chat/completions", false);
        apiKeyInput = addInput(root, "API Key", "sk-...", true);
        modelInput = addInput(root, "模型名称", "gpt-4o-mini / deepseek-chat / 你的兼容模型名", false);
        promptInput = addInput(root, "系统提示词", "可留空，建议用中文短提示", false);
        promptInput.setSingleLine(false);
        promptInput.setMinLines(4);
        promptInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(14), 0, dp(10));
        root.addView(buttonRow, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText("保存");
        saveButton.setAllCaps(false);
        buttonRow.addView(saveButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button testButton = new Button(this);
        testButton.setText("测试接口");
        testButton.setAllCaps(false);
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        testParams.setMargins(dp(12), 0, 0, 0);
        buttonRow.addView(testButton, testParams);

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(92, 99, 111));
        statusText.setTextSize(14);
        statusText.setPadding(0, dp(8), 0, 0);
        root.addView(statusText, matchWrap());

        loadConfig();
        enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                statusText.setText(isChecked ? "已启用，保存后 Bixby 重启或进程刷新时生效。" : "已关闭，保存后不再替换 Bixby 配置。");
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BixbyOpenAiConfig config = saveConfig();
                Toast.makeText(BixbyOpenAiSettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                statusText.setText("已保存：" + config.summary());
            }
        });
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testApi();
            }
        });

        setContentView(scrollView);
    }

    private void loadConfig() {
        BixbyOpenAiConfig config = BixbyOpenAiConfig.loadLocal(this);
        enableSwitch.setChecked(config.enabled);
        baseUrlInput.setText(config.baseUrl);
        apiKeyInput.setText(config.apiKey);
        modelInput.setText(config.model);
        promptInput.setText(config.systemPrompt);
        statusText.setText("当前配置：" + config.summary());
    }

    private BixbyOpenAiConfig saveConfig() {
        BixbyOpenAiConfig config = new BixbyOpenAiConfig(
                enableSwitch.isChecked(),
                baseUrlInput.getText().toString(),
                apiKeyInput.getText().toString(),
                modelInput.getText().toString(),
                promptInput.getText().toString()
        );
        BixbyOpenAiConfig.saveLocal(this, config);
        return config;
    }

    private void testApi() {
        final BixbyOpenAiConfig config = saveConfig();
        statusText.setText("正在测试 " + config.chatCompletionsEndpoint());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String reply = BixbyOpenAiClient.chat(config, "请只回复：Bixby OpenAI 接入测试成功");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("测试成功：\n" + reply);
                        }
                    });
                } catch (final Throwable throwable) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("测试失败：\n" + throwable.getMessage());
                        }
                    });
                }
            }
        }, "BixbyOpenAiTest").start();
    }

    private EditText addInput(LinearLayout root, String label, String hint, boolean password) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(35, 40, 48));
        labelView.setTextSize(14);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setPadding(0, dp(10), 0, dp(6));
        root.addView(labelView, matchWrap());

        EditText input = new EditText(this);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setSelectAllOnFocus(false);
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(input, matchWrap());
        return input;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
