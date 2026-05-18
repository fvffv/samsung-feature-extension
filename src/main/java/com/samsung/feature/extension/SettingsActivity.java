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
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
                    "Bixby OpenAI 接入（测试版）",
                    "测试版功能：让 Bixby 能调用自定义模型输出内容，目前仍在持续调整。",
                    BixbyOpenAiSettingsActivity.class,
                    "com.samsung.android.bixby.agent"
            ),
            new FeatureItem(
                    "One UI 主屏幕图标自定义",
                    "为每个应用单独设置桌面图标、名称和字体样式，并在 One UI 主屏幕设置中增加入口。",
                    LauncherIconCustomizerActivity.class,
                    "com.sec.android.app.launcher"
            )
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView currentVersionValue;
    private TextView latestVersionValue;
    private TextView updateStatusValue;
    private TextView githubUrlValue;
    private TextView checkUpdateAction;

    private String currentVersionName = "";
    private int currentVersionCode;
    private boolean updatePromptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("三星功能扩展");

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

        root.addView(buildVersionCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ListView listView = new ListView(this);
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
                Class<?> activityClass = FEATURES[position].activityClass;
                if (activityClass != null) {
                    startActivity(new Intent(SettingsActivity.this, activityClass));
                }
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
        beginLatestVersionCheck(false);
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
        cardSubtitle.setText("通过 GitHub Releases 检查是否为最新版。");
        cardSubtitle.setTextSize(13);
        cardSubtitle.setTextColor(Color.rgb(98, 105, 117));
        cardSubtitle.setPadding(0, dp(8), 0, dp(14));
        card.addView(cardSubtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        currentVersionValue = makeValueText(currentVersionLabel());
        card.addView(makeInfoRow("当前版本", currentVersionValue));

        latestVersionValue = makeValueText("检查中...");
        card.addView(makeInfoRow("最新版本", latestVersionValue));

        updateStatusValue = makeValueText("正在检查");
        updateStatusValue.setTextColor(Color.rgb(59, 130, 246));
        card.addView(makeInfoRow("更新状态", updateStatusValue));

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
        testNote.setText("提示：当前发布版本仅在 One UI 8.0 中测试过，请尽量保持系统、LSPosed 和相关三星应用为最新版本。");
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
            return "未知";
        }
        return currentVersionName + " (" + currentVersionCode + ")";
    }

    private void beginLatestVersionCheck(final boolean manual) {
        latestVersionValue.setText("检查中...");
        updateStatusValue.setText("正在检查");
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
                            latestVersionValue.setText("检查失败");
                            updateStatusValue.setText("无法获取版本信息");
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

        latestVersionValue.setText(latestTag.length() == 0 ? "未知" : latestTag);
        int compare = compareVersions(currentNormalized, latestNormalized);
        if (latestNormalized.length() == 0) {
            updateStatusValue.setText("未获取到版本号");
            updateStatusValue.setTextColor(Color.rgb(180, 83, 9));
            if (manual) {
                showSimpleDialog("检查更新", "未从 GitHub Releases 获取到有效的版本号。");
            }
            return;
        }
        if (compare == 0) {
            updateStatusValue.setText("已是最新版本");
            updateStatusValue.setTextColor(Color.rgb(22, 163, 74));
            if (manual) {
                showSimpleDialog("检查更新", "当前已经是最新版本。");
            }
            return;
        }
        if (compare > 0) {
            updateStatusValue.setText("当前版本高于 Releases 最新版");
            updateStatusValue.setTextColor(Color.rgb(180, 83, 9));
            if (manual) {
                showSimpleDialog("检查更新", "当前安装的是比 Releases 更新的版本。");
            }
            return;
        }

        updateStatusValue.setText("发现新版本");
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
                + "\n\n目前发布版本仅在 One UI 8.0 环境中测试过，请尽量保持系统、LSPosed 和相关三星应用为最新版本。";
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
            return FEATURES[position].activityClass != null;
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
            boolean clickable = item.activityClass != null;
            icon.setImageDrawable(resolveIcon(item.packageNames));
            name.setText(item.name);
            description.setText(item.description);
            row.setBackground(makeRowBackground(clickable));
            action.setText(clickable ? "查看设置 >" : "已集成");
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
        final String[] packageNames;

        FeatureItem(String name, String description, Class<?> activityClass, String... packageNames) {
            this.name = name;
            this.description = description;
            this.activityClass = activityClass;
            this.packageNames = packageNames;
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
