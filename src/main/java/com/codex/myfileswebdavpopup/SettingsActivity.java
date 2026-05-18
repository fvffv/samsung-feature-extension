package com.codex.myfileswebdavpopup;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
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
                    "为 Galaxy Expert RAW 解锁隐藏的 200MP 超高分辨率功能，只支持S23U S24U",
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
                    "控制是否允许 NFC 在息屏状态下按解锁亮屏状态处理刷卡请求。默认关闭，可在此单独启用。",
                    NfcScreenOffSettingsActivity.class,
                    "com.android.nfc"
            ),
            new FeatureItem(
                    "Bixby OpenAI 接入（测试版）",
                    "测试版功能：让bixby能调用自定义模型来输出,目前只是测试版还有很多问题。",
                    BixbyOpenAiSettingsActivity.class,
                    "com.samsung.android.bixby.agent"
            ),
            new FeatureItem(
                    "One UI 主屏幕图标自定义",
                    "从相册为每个应用单独设置桌面显示图标名字颜色字体，并在 One UI 主屏幕设置页中增加入口。",
                    LauncherIconCustomizerActivity.class,
                    "com.sec.android.app.launcher"
            )
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("三星功能扩展");

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
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle, new LinearLayout.LayoutParams(
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
}
