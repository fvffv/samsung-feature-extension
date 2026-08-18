package com.samsung.feature.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class SettingsActivity extends Activity {
    private static final String GITHUB_REPO_URL =
            "https://github.com/fvffv/samsung-feature-extension";
    private static final String GITHUB_RELEASES_API =
            "https://api.github.com/repos/fvffv/samsung-feature-extension/releases/latest";

    private static final FeatureItem[] FEATURES = {
            new FeatureItem(
                    "三星文件管理 WebDAV",
                    "在添加网络存储中增加 WebDAV，支持目录浏览、上传、下载、复制、移动和删除等常用文件操作。",
                    null,
                    "com.sec.android.app.myfiles",
                    "com.samsung.android.app.myfiles",
                    "com.samsung.android.app.networkstoragemanager"
            ),
            new FeatureItem(
                    "Expert RAW 200MP",
                    "为 Galaxy Expert RAW 解锁隐藏的 200MP 超高分辨率能力，目前主要面向 Ultra 机型。",
                    null,
                    "com.samsung.android.app.galaxyraw"
            ),
            new FeatureItem(
                    "三星相机专业视频增强",
                    "解锁专业模式 4K120 HDR10+、8K 24/30/60 HDR10+ 录制，以及慢动作 UHD 240 帧录制。支持按 8K、4K、FHD 自定义视频录制码率。",
                    CameraBitrateSettingsActivity.class,
                    "com.sec.android.app.camera"
            ),
            new FeatureItem(
                    "文本通话自定义开场语",
                    "自定义三星电话“语音转文字”和“代为说话”两种文本通话模式的开场播报内容；留空即可使用系统默认文案。",
                    TextCallGreetingSettingsActivity.class,
                    "com.samsung.android.incallui"
            ),
            new FeatureItem(
                    "应用分身全应用列表",
                    "把应用分身的可用列表扩展为已安装的非系统应用。",
                    null,
                    "com.samsung.android.da.daagent"
            ),
            new FeatureItem(
                    "设备健康管理",
                    "查看 Samsung Device Health Manager Service 的温控、电池、异常检测和后台限制状态，并提供常用控制开关。",
                    DeviceHealthActivity.class,
                    "com.sec.android.sdhms"
            ),
            new FeatureItem(
                    "NFC 息屏刷卡",
                    "控制是否允许 NFC 在息屏状态下按照亮屏解锁状态处理刷卡请求，默认关闭，可在此单独启用。",
                    NfcScreenOffSettingsActivity.class,
                    "com.android.nfc"
            ),
            new FeatureItem(
                    "触控高采样率",
                    "给手指触控补发三星原生 GOS TSP 高扫描率策略，熄屏后亮屏会自动脉冲重开一次。",
                    TouchSamplingSettingsActivity.class,
                    "com.sec.android.sdhms"
            ),
            new FeatureItem(
                    "全局旁路供电",
                    "基于 Game Booster 的“游戏时暂停 USB PD 充电”机制，开启后全局维持 pass_through 旁路供电状态，关闭后恢复系统默认。",
                    PassThroughChargingSettingsActivity.class,
                    "com.sec.android.sdhms",
                    "com.samsung.android.game.gametools"
            ),
            new FeatureItem(
                    "充电显示与提示音",
                    "自定义 SystemUI 充电通知显示内容，支持变量模板，并可设置插入和拔出充电器时的提示音。",
                    ChargingDisplaySettingsActivity.class,
                    "com.android.systemui"
            ),
            new FeatureItem(
                    "One UI 8.5 音频橡皮擦",
                    "为 S24 Ultra 以下机型开放视频编辑工作室和快速面板的音频橡皮擦；快速面板会在检测到符合条件的媒体音频播放时自动显示。",
                    null,
                    "com.sec.android.app.vepreload",
                    "com.android.systemui"
            ),
            new FeatureItem(
                    "One UI 主屏幕图标自定义",
                    "为每个应用单独设置桌面图标、名称和字体样式，并在 One UI 主屏幕设置中增加入口。",
                    LauncherIconCustomizerActivity.class,
                    "com.sec.android.app.launcher"
            ),
            new FeatureItem(
                    "自定义指纹图标",
                    "替换系统指纹验证位置显示的指纹图标，支持 Lottie JSON 动画或 PNG 静态图。",
                    FingerprintStyleSettingsActivity.class,
                    "com.samsung.android.biometrics.app.setting"
            ),
            FeatureItem.external(
                    "系统字体自定义",
                    "在三星设置的字体风格页面中添加本地字体入口，可选择并应用本地 .ttf 字体文件。目前仅支持 ttf 格式。",
                    "com.samsung.settings.FontStyleActivity",
                    "com.android.settings",
                    "com.android.settings"
            )
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView currentVersionValue;
    private TextView latestVersionValue;
    private TextView updateStatusValue;
    private TextView githubUrlValue;
    private TextView checkUpdateAction;
    private TextView logStatusValue;
    private Switch logSwitch;

    private String currentVersionName = "";
    private int currentVersionCode;
    private boolean updatePromptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(LanguageManager.text(this, "三星功能扩展"));

        resolveCurrentVersion();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));
        root.setPadding(dp(20), dp(18), dp(20), dp(16));

        TextView title = new TextView(this);
        title.setText("三星功能扩展");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("当前模块包含以下功能。");
        subtitle.setTextColor(Color.rgb(98, 105, 117));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, 0, 0, dp(14));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ListView listView = new ListView(this);
        listView.addHeaderView(buildVersionHeader(), null, false);
        listView.setAdapter(new FeatureAdapter());
        listView.setDivider(null);
        listView.setDividerHeight(dp(10));
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(14), 0, 0);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object itemObject = parent.getItemAtPosition(position);
                if (!(itemObject instanceof FeatureItem)) {
                    return;
                }
                openFeature((FeatureItem) itemObject);
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
        beginLatestVersionCheck(false);
        LanguageManager.applyToActivity(this);
    }

    private void openFeature(FeatureItem item) {
        if (item == null) {
            return;
        }
        if (item.activityClass != null) {
            startActivity(new Intent(SettingsActivity.this, item.activityClass));
            return;
        }
        if (item.launchAction == null) {
            return;
        }
        try {
            Intent intent = new Intent(item.launchAction);
            if (item.launchPackage != null) {
                intent.setPackage(item.launchPackage);
            }
            startActivity(intent);
        } catch (Throwable first) {
            try {
                Intent fallback = new Intent();
                fallback.setClassName("com.android.settings",
                        "com.android.settings.Settings.SecFontStyleActivity");
                startActivity(fallback);
            } catch (Throwable ignored) {
                showSimpleDialog("无法打开",
                        "没有找到对应的系统设置页面，请确认系统设置应用为最新版本。");
            }
        }
    }

    private View buildVersionHeader() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(Color.TRANSPARENT);
        wrapper.setPadding(0, 0, 0, dp(14));
        wrapper.addView(buildVersionCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        languageParams.setMargins(0, dp(12), 0, 0);
        wrapper.addView(buildLanguageCard(), languageParams);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        logParams.setMargins(0, dp(12), 0, 0);
        wrapper.addView(buildLogSwitchCard(), logParams);
        return wrapper;
    }

    private View buildLanguageCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(makeInfoCardBackground());

        TextView title = new TextView(this);
        title.setText(LanguageManager.text(this, "语言"));
        title.setTextSize(18);
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setIncludeFontPadding(false);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = new TextView(this);
        description.setText(LanguageManager.text(this,
                "选择模块界面语言。切换后会立即刷新当前页面，后续打开的设置页也会保持此语言。"));
        description.setTextSize(13);
        description.setTextColor(Color.rgb(98, 105, 117));
        description.setLineSpacing(dp(2), 1.0f);
        description.setPadding(0, dp(8), 0, dp(8));
        card.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        final Spinner selector = new Spinner(this);
        final boolean english = LanguageManager.isEnglish(this);
        String[] languages = english
                ? new String[]{"Simplified Chinese", "English"}
                : new String[]{"简体中文", "English"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector.setAdapter(adapter);
        selector.setSelection(english ? 1 : 0, false);
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean selectedEnglish = position == 1;
                if (selectedEnglish == LanguageManager.isEnglish(SettingsActivity.this)) {
                    return;
                }
                LanguageManager.setEnglish(SettingsActivity.this, selectedEnglish);
                recreate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep the current preference.
            }
        });
        card.addView(selector, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private View buildLogSwitchCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(makeInfoCardBackground());

        TextView cardTitle = new TextView(this);
        cardTitle.setText("诊断日志");
        cardTitle.setTextSize(18);
        cardTitle.setTextColor(Color.rgb(20, 24, 31));
        cardTitle.setIncludeFontPadding(false);
        card.addView(cardTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView cardSubtitle = new TextView(this);
        cardSubtitle.setText("关闭后不再写入 LSPosed 日志和本地诊断文件，排查问题时再临时开启。");
        cardSubtitle.setTextSize(13);
        cardSubtitle.setTextColor(Color.rgb(98, 105, 117));
        cardSubtitle.setLineSpacing(dp(2), 1.0f);
        cardSubtitle.setPadding(0, dp(8), 0, dp(14));
        card.addView(cardSubtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText("日志输出");
        label.setTextColor(Color.rgb(24, 29, 36));
        label.setTextSize(15);
        textColumn.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        logStatusValue = new TextView(this);
        logStatusValue.setTextSize(13);
        logStatusValue.setPadding(0, dp(4), 0, 0);
        textColumn.addView(logStatusValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        logSwitch = new Switch(this);
        logSwitch.setText("");
        logSwitch.setChecked(LogSettingsProvider.getLocalEnabled(this));
        updateLogStatus(logSwitch.isChecked());
        logSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LogSettingsProvider.setEnabled(SettingsActivity.this, isChecked);
                updateLogStatus(isChecked);
            }
        });
        row.addView(logSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private View buildVersionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(makeInfoCardBackground());

        TextView cardTitle = new TextView(this);
        cardTitle.setText("版本与更新");
        cardTitle.setTextSize(18);
        cardTitle.setTextColor(Color.rgb(20, 24, 31));
        cardTitle.setIncludeFontPadding(false);
        card.addView(cardTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView cardSubtitle = new TextView(this);
        cardSubtitle.setText("已安装版本取自当前 APK；GitHub 发布版仅反映远端 Releases。");
        cardSubtitle.setTextSize(13);
        cardSubtitle.setTextColor(Color.rgb(98, 105, 117));
        cardSubtitle.setPadding(0, dp(8), 0, dp(14));
        card.addView(cardSubtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        currentVersionValue = makeValueText(currentVersionLabel());
        card.addView(makeInfoRow("已安装版本", currentVersionValue));

        latestVersionValue = makeValueText("检查中...");
        card.addView(makeInfoRow("GitHub 发布版", latestVersionValue));

        updateStatusValue = makeValueText("正在检查");
        updateStatusValue.setTextColor(Color.rgb(59, 130, 246));
        card.addView(makeInfoRow("版本比较", updateStatusValue));

        githubUrlValue = makeValueText(GITHUB_REPO_URL);
        githubUrlValue.setTextColor(Color.rgb(37, 99, 235));
        githubUrlValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGithubPage();
            }
        });
        card.addView(makeInfoRow("GitHub", githubUrlValue));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(14), 0, 0);

        TextView openGithubAction = makeActionButton("打开 GitHub", true);
        openGithubAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGithubPage();
            }
        });
        actionRow.addView(openGithubAction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        checkUpdateAction = makeActionButton("检查更新", false);
        checkUpdateAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginLatestVersionCheck(true);
            }
        });
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        checkParams.setMargins(dp(10), 0, 0, 0);
        actionRow.addView(checkUpdateAction, checkParams);

        card.addView(actionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView testNote = new TextView(this);
        testNote.setText("提示：当前发布版本主要在 One UI 8.0 / 8.5 中测试；音频橡皮擦兼容层面向 One UI 8.5，请尽量保持系统、LSPosed 和相关三星应用为最新版本。");
        testNote.setTextColor(Color.rgb(120, 83, 0));
        testNote.setTextSize(12);
        testNote.setLineSpacing(dp(2), 1.0f);
        testNote.setPadding(0, dp(14), 0, 0);
        card.addView(testNote, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return card;
    }

    private View makeInfoRow(String label, TextView valueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(84, 91, 103));
        labelView.setTextSize(14);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.34f
        ));

        row.addView(valueView, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.66f
        ));
        return row;
    }

    private TextView makeValueText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(27, 34, 45));
        view.setTextSize(14);
        view.setGravity(Gravity.END);
        return view;
    }

    private void updateLogStatus(boolean enabled) {
        if (logStatusValue == null) {
            return;
        }
        if (enabled) {
            logStatusValue.setText(LanguageManager.text(this,
                    "已开启，会输出诊断日志，可能轻微增加后台开销。"));
            logStatusValue.setTextColor(Color.rgb(22, 163, 74));
        } else {
            logStatusValue.setText(LanguageManager.text(this,
                    "已关闭，推荐日常使用保持关闭。"));
            logStatusValue.setTextColor(Color.rgb(92, 99, 111));
        }
    }

    private TextView makeActionButton(String text, boolean primary) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextSize(13);
        action.setIncludeFontPadding(false);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), dp(8), dp(12), dp(8));
        action.setTextColor(primary ? Color.WHITE : Color.rgb(37, 99, 235));
        action.setBackground(makeVersionActionBackground(primary));
        return action;
    }

    private Drawable makeInfoCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), Color.rgb(224, 228, 236));
        return drawable;
    }

    private Drawable makeVersionActionBackground(boolean primary) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(18));
        drawable.setColor(primary ? Color.rgb(37, 99, 235) : Color.rgb(239, 244, 255));
        if (!primary) {
            drawable.setStroke(dp(1), Color.rgb(191, 219, 254));
        }
        return drawable;
    }

    private void resolveCurrentVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersionName = info.versionName != null ? info.versionName : "";
            currentVersionCode = info.versionCode;
        } catch (Throwable ignored) {
            currentVersionName = "";
            currentVersionCode = 0;
        }
    }

    private String currentVersionLabel() {
        if (currentVersionName == null || currentVersionName.length() == 0) {
            return LanguageManager.text(this, "未知");
        }
        return currentVersionName + " (" + currentVersionCode + ")";
    }

    private void beginLatestVersionCheck(final boolean manual) {
        latestVersionValue.setText(LanguageManager.text(this, "检查中..."));
        updateStatusValue.setText(LanguageManager.text(this, "正在检查"));
        updateStatusValue.setTextColor(Color.rgb(59, 130, 246));
        checkUpdateAction.setEnabled(false);
        checkUpdateAction.setAlpha(0.6f);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ReleaseInfo info;
                try {
                    info = fetchLatestRelease();
                } catch (Throwable t) {
                    DiagnosticLogger.log("Latest release check failed");
                    DiagnosticLogger.log(t);
                    returnResult(null, manual, t);
                    return;
                }
                returnResult(info, manual, null);
            }

            private void returnResult(final ReleaseInfo info,
                                      final boolean manualCheck,
                                      final Throwable throwable) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        checkUpdateAction.setEnabled(true);
                        checkUpdateAction.setAlpha(1f);
                        if (throwable != null) {
                            latestVersionValue.setText(LanguageManager.text(SettingsActivity.this, "检查失败"));
                            updateStatusValue.setText(LanguageManager.text(SettingsActivity.this, "无法获取版本信息"));
                            updateStatusValue.setTextColor(Color.rgb(220, 38, 38));
                            if (manualCheck) {
                                showSimpleDialog(
                                        "检查更新失败",
                                        "当前无法从 GitHub Releases 获取最新版本信息，请稍后重试。"
                                );
                            }
                            return;
                        }
                        applyLatestRelease(info, manualCheck);
                    }
                });
            }
        }, "release-check").start();
    }

    private ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(GITHUB_RELEASES_API).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "SamsungFeatureExtension/" + currentVersionName);
            connection.connect();

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("GitHub API HTTP " + code);
            }

            input = connection.getInputStream();
            String json = readFully(input);
            JSONObject object = new JSONObject(json);
            String tagName = object.optString("tag_name", "");
            String htmlUrl = object.optString("html_url", GITHUB_REPO_URL);
            return new ReleaseInfo(tagName, htmlUrl);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                    // Ignore.
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readFully(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    private void applyLatestRelease(ReleaseInfo info, boolean manual) {
        String latestTag = info != null ? info.tagName : "";
        String currentNormalized = normalizeVersion(currentVersionName);
        String latestNormalized = normalizeVersion(latestTag);

        latestVersionValue.setText(latestTag.length() == 0 ? LanguageManager.text(this, "未知") : latestTag);
        int compare = compareVersions(currentNormalized, latestNormalized);
        if (latestNormalized.length() == 0) {
            updateStatusValue.setText(LanguageManager.text(this, "未获取到版本号"));
            updateStatusValue.setTextColor(Color.rgb(180, 83, 9));
            if (manual) {
                showSimpleDialog("检查更新", "未从 GitHub Releases 获取到有效的版本号。");
            }
            return;
        }
        if (compare == 0) {
            updateStatusValue.setText(LanguageManager.text(this, "已是最新版本"));
            updateStatusValue.setTextColor(Color.rgb(22, 163, 74));
            if (manual) {
                showSimpleDialog("检查更新", "当前已经是最新版本。");
            }
            return;
        }
        if (compare > 0) {
            updateStatusValue.setText(LanguageManager.text(this, "当前版本高于 Releases 最新版"));
            updateStatusValue.setTextColor(Color.rgb(180, 83, 9));
            if (manual) {
                showSimpleDialog("检查更新", "当前安装的是比 Releases 更新的版本。");
            }
            return;
        }

        updateStatusValue.setText(LanguageManager.text(this, "发现新版本"));
        updateStatusValue.setTextColor(Color.rgb(220, 38, 38));
        if (!updatePromptShown || manual) {
            updatePromptShown = true;
            showUpdateDialog(info, latestTag);
        }
    }

    private void showUpdateDialog(final ReleaseInfo info, String latestTag) {
        String message = "检测到新版本："
                + latestTag
                + "\n当前版本："
                + currentVersionName
                + "\n\n目前发布版本主要在 One UI 8.0 / 8.5 环境中测试；音频橡皮擦兼容层面向 One UI 8.5，请尽量保持系统、LSPosed 和相关三星应用为最新版本。";
        new AlertDialog.Builder(this)
                .setTitle("发现新版本")
                .setMessage(message)
                .setNegativeButton("知道了", null)
                .setPositiveButton("前往 GitHub", (dialog, which) -> openReleasePage(info != null ? info.htmlUrl : GITHUB_REPO_URL))
                .show();
    }

    private void showSimpleDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void openGithubPage() {
        openReleasePage(GITHUB_REPO_URL);
    }

    private void openReleasePage(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Throwable ignored) {
            // Ignore.
        }
    }

    private String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        while (trimmed.length() > 0) {
            char c = trimmed.charAt(0);
            if ((c >= '0' && c <= '9') || c == '.') {
                break;
            }
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private int compareVersions(String current, String latest) {
        if (current.equals(latest)) {
            return 0;
        }
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");
        int count = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < count; i++) {
            int left = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int right = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            if (left != right) {
                return left > right ? 1 : -1;
            }
        }
        return current.compareTo(latest);
    }

    private int parseVersionPart(String part) {
        if (part == null) {
            return 0;
        }
        int value = 0;
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class FeatureAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return FEATURES.length;
        }

        @Override
        public Object getItem(int position) {
            return FEATURES[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            return FEATURES[position].isClickable();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView icon;
            TextView name;
            TextView description;
            TextView action;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof ViewHolder) {
                row = (LinearLayout) convertView;
                ViewHolder holder = (ViewHolder) convertView.getTag();
                icon = holder.icon;
                name = holder.name;
                description = holder.description;
                action = holder.action;
            } else {
                row = new LinearLayout(SettingsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(18), dp(16), dp(18), dp(16));
                row.setMinimumHeight(dp(92));
                row.setGravity(Gravity.CENTER_VERTICAL);

                icon = new ImageView(SettingsActivity.this);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
                iconParams.setMargins(0, 0, dp(14), 0);
                row.addView(icon, iconParams);

                LinearLayout textColumn = new LinearLayout(SettingsActivity.this);
                textColumn.setOrientation(LinearLayout.VERTICAL);

                name = new TextView(SettingsActivity.this);
                name.setTextColor(Color.rgb(24, 29, 36));
                name.setTextSize(17);
                name.setIncludeFontPadding(false);
                textColumn.addView(name, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                description = new TextView(SettingsActivity.this);
                description.setTextColor(Color.rgb(92, 99, 111));
                description.setTextSize(14);
                description.setLineSpacing(dp(2), 1.0f);
                description.setPadding(0, dp(8), 0, 0);
                textColumn.addView(description, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                row.addView(textColumn, new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                ));

                action = new TextView(SettingsActivity.this);
                action.setGravity(Gravity.CENTER);
                action.setIncludeFontPadding(false);
                action.setTextSize(13);
                action.setMinWidth(dp(72));
                action.setPadding(dp(10), dp(7), dp(10), dp(7));
                LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                actionParams.setMargins(dp(12), 0, 0, 0);
                row.addView(action, actionParams);

                row.setTag(new ViewHolder(icon, name, description, action));
            }

            FeatureItem item = FEATURES[position];
            boolean clickable = item.isClickable();
            icon.setImageDrawable(resolveIcon(item.packageNames));
            name.setText(LanguageManager.text(SettingsActivity.this, item.name));
            description.setText(LanguageManager.text(SettingsActivity.this, item.description));
            row.setBackground(makeRowBackground(clickable));
            action.setText(LanguageManager.text(SettingsActivity.this,
                    clickable ? "查看设置 >" : "已集成"));
            action.setTextColor(clickable ? Color.WHITE : Color.rgb(105, 112, 124));
            action.setBackground(makeActionBackground(clickable));
            return row;
        }

        private Drawable makeRowBackground(boolean clickable) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.WHITE);
            drawable.setCornerRadius(dp(8));
            drawable.setStroke(
                    clickable ? dp(2) : dp(1),
                    clickable ? Color.rgb(48, 105, 240) : Color.rgb(224, 228, 236)
            );
            return drawable;
        }

        private Drawable makeActionBackground(boolean clickable) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(dp(18));
            drawable.setColor(clickable ? Color.rgb(48, 105, 240) : Color.rgb(238, 241, 246));
            return drawable;
        }

        private Drawable resolveIcon(String[] packageNames) {
            PackageManager pm = getPackageManager();
            if (packageNames != null) {
                for (int i = 0; i < packageNames.length; i++) {
                    try {
                        return pm.getApplicationIcon(packageNames[i]);
                    } catch (Throwable ignored) {
                        // Try the next candidate package name.
                    }
                }
            }
            return pm.getDefaultActivityIcon();
        }
    }

    private static final class ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView description;
        final TextView action;

        ViewHolder(ImageView icon, TextView name, TextView description, TextView action) {
            this.icon = icon;
            this.name = name;
            this.description = description;
            this.action = action;
        }
    }

    private static final class FeatureItem {
        final String name;
        final String description;
        final Class<?> activityClass;
        final String launchAction;
        final String launchPackage;
        final String[] packageNames;

        FeatureItem(String name, String description, Class<?> activityClass, String... packageNames) {
            this(name, description, activityClass, null, null, packageNames);
        }

        static FeatureItem external(String name, String description, String launchAction,
                                    String launchPackage, String... packageNames) {
            return new FeatureItem(name, description, null, launchAction, launchPackage, packageNames);
        }

        private FeatureItem(String name, String description, Class<?> activityClass,
                            String launchAction, String launchPackage, String[] packageNames) {
            this.name = name;
            this.description = description;
            this.activityClass = activityClass;
            this.launchAction = launchAction;
            this.launchPackage = launchPackage;
            this.packageNames = packageNames;
        }

        boolean isClickable() {
            return activityClass != null || launchAction != null;
        }
    }

    private static final class ReleaseInfo {
        final String tagName;
        final String htmlUrl;

        ReleaseInfo(String tagName, String htmlUrl) {
            this.tagName = tagName != null ? tagName : "";
            this.htmlUrl = htmlUrl != null ? htmlUrl : GITHUB_REPO_URL;
        }
    }
}
