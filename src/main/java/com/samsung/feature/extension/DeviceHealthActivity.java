package com.samsung.feature.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.samsung.feature.extension.sdhmsmanager.SdhmsBridge;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class DeviceHealthActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView statusText;
    private TextView thermalText;
    private TextView temperatureText;
    private LinearLayout batteryList;
    private LinearLayout anomalyList;
    private LinearLayout highCpuList;
    private LinearLayout fasList;
    private Switch masterSwitch;
    private Switch brightnessSwitch;
    private Switch cpSwitch;
    private EditText packageInput;
    private String lastRequestId;
    private boolean updatingUi;

    private final BroadcastReceiver responseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !SdhmsBridge.ACTION_RESPONSE.equals(intent.getAction())) {
                return;
            }
            String requestId = intent.getStringExtra(SdhmsBridge.EXTRA_REQUEST_ID);
            if (lastRequestId != null && requestId != null && !lastRequestId.equals(requestId)) {
                return;
            }
            Bundle data = intent.getBundleExtra(SdhmsBridge.EXTRA_DATA);
            applyResponse(data);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("设备健康管理");
        buildUi();
        registerResponseReceiver();
        requestRefresh();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(responseReceiver);
        } catch (Throwable ignored) {
            // Receiver may not have been registered if Activity creation failed early.
        }
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("设备健康管理", 24, Color.rgb(20, 24, 31), Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(8));
        content.addView(title);

        TextView desc = text(
                "通过 LSPosed 接入 Samsung Device Health Manager Service，查看温控、电池、异常检测和后台限制状态。",
                14,
                Color.rgb(85, 92, 105),
                Typeface.DEFAULT
        );
        desc.setPadding(0, 0, 0, dp(12));
        content.addView(desc);

        TextView warning = text(
                "注意：关闭温控降频会提高发热和耗电风险，原生总开关可能触发手机重启。",
                14,
                Color.rgb(168, 87, 31),
                Typeface.DEFAULT_BOLD
        );
        warning.setPadding(dp(12), dp(10), dp(12), dp(10));
        warning.setBackgroundColor(Color.rgb(255, 241, 224));
        content.addView(warning, matchWrap());

        statusText = text("正在等待 SDHMS Hook 响应...", 13, Color.rgb(95, 102, 116), Typeface.DEFAULT);
        statusText.setPadding(0, dp(12), 0, dp(8));
        content.addView(statusText);

        Button refreshButton = new Button(this);
        refreshButton.setText("刷新数据");
        refreshButton.setAllCaps(false);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestRefresh();
            }
        });
        content.addView(refreshButton, matchWrap());

        masterSwitch = addSwitch(
                "禁用温控降频",
                "调用 SDHMS 原生隐藏总开关，开启后可能重启设备。"
        );
        brightnessSwitch = addSwitch(
                "关闭亮度温控限制",
                "发热时不再由该策略降低屏幕亮度。"
        );
        cpSwitch = addSwitch(
                "关闭 CP/蜂窝温控限制",
                "发热时不再由该策略限制蜂窝相关温控。"
        );

        masterSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton button, final boolean checked) {
                if (updatingUi) {
                    return;
                }
                if (checked) {
                    new AlertDialog.Builder(DeviceHealthActivity.this)
                            .setTitle("确认关闭温控降频")
                            .setMessage("这个开关会调用三星原生隐藏逻辑，可能导致设备重启，并带来更高发热风险。")
                            .setPositiveButton("继续", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    sendBooleanCommand(SdhmsBridge.CMD_SET_THERMAL_MASTER, true);
                                }
                            })
                            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    updatingUi = true;
                                    masterSwitch.setChecked(false);
                                    updatingUi = false;
                                }
                            })
                            .show();
                } else {
                    sendBooleanCommand(SdhmsBridge.CMD_SET_THERMAL_MASTER, false);
                }
            }
        });
        brightnessSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!updatingUi) {
                    sendBooleanCommand(SdhmsBridge.CMD_SET_BRIGHTNESS_LIMIT_OFF, isChecked);
                }
            }
        });
        cpSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!updatingUi) {
                    sendBooleanCommand(SdhmsBridge.CMD_SET_CP_TM_OFF, isChecked);
                }
            }
        });

        thermalText = addSection("温控状态");
        temperatureText = addSection("温度传感器");
        batteryList = addAppSection("电池信息");
        anomalyList = addAppSection("异常检测");
        highCpuList = addAppSection("高 CPU 记录");

        addFasControls();
        fasList = addAppSection("后台管控 / 限制应用");

        setContentView(scrollView);
    }

    private void addFasControls() {
        TextView header = text("手动限制应用后台", 18, Color.rgb(25, 31, 39), Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(18), 0, dp(8));
        content.addView(header);

        packageInput = new EditText(this);
        packageInput.setHint("输入包名，例如 com.example.app");
        packageInput.setSingleLine(true);
        content.addView(packageInput, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        Button restrict = new Button(this);
        restrict.setText("限制后台");
        restrict.setAllCaps(false);
        restrict.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendFasCommand(true);
            }
        });
        Button allow = new Button(this);
        allow.setText("解除限制");
        allow.setAllCaps(false);
        allow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendFasCommand(false);
            }
        });
        row.addView(restrict, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(allow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(row, matchWrap());
    }

    private Switch addSwitch(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackgroundColor(Color.WHITE);

        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(16);
        sw.setTextColor(Color.rgb(24, 29, 36));
        sw.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(sw, matchWrap());

        TextView sub = text(subtitle, 13, Color.rgb(92, 99, 111), Typeface.DEFAULT);
        sub.setPadding(0, dp(4), 0, 0);
        box.addView(sub, matchWrap());

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(10), 0, 0);
        content.addView(box, params);
        return sw;
    }

    private TextView addSection(String title) {
        TextView header = text(title, 18, Color.rgb(25, 31, 39), Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(18), 0, dp(8));
        content.addView(header);
        TextView body = text("等待刷新...", 13, Color.rgb(46, 52, 62), Typeface.MONOSPACE);
        body.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.setBackgroundColor(Color.WHITE);
        content.addView(body, matchWrap());
        return body;
    }

    private LinearLayout addAppSection(String title) {
        TextView header = text(title, 18, Color.rgb(25, 31, 39), Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(18), 0, dp(8));
        content.addView(header);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.WHITE);
        list.setPadding(0, dp(4), 0, dp(4));
        content.addView(list, matchWrap());
        return list;
    }

    private void registerResponseReceiver() {
        IntentFilter filter = new IntentFilter(SdhmsBridge.ACTION_RESPONSE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(responseReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(responseReceiver, filter);
        }
    }

    private void requestRefresh() {
        sendCommand(SdhmsBridge.CMD_REFRESH, null);
    }

    private void sendBooleanCommand(String command, boolean enabled) {
        Intent intent = baseRequest(command);
        intent.putExtra(SdhmsBridge.EXTRA_ENABLED, enabled);
        sendCommandIntent(intent);
    }

    private void sendFasCommand(boolean restricted) {
        String pkg = packageInput.getText() == null ? "" : packageInput.getText().toString().trim();
        if (pkg.length() == 0) {
            Toast.makeText(this, "请先输入包名", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = baseRequest(SdhmsBridge.CMD_SET_FAS_RESTRICTED);
        intent.putExtra(SdhmsBridge.EXTRA_PACKAGE_NAME, pkg);
        intent.putExtra(SdhmsBridge.EXTRA_ENABLED, restricted);
        sendCommandIntent(intent);
    }

    private void sendCommand(String command, Bundle extras) {
        Intent intent = baseRequest(command);
        if (extras != null) {
            intent.putExtras(extras);
        }
        sendCommandIntent(intent);
    }

    private Intent baseRequest(String command) {
        lastRequestId = String.valueOf(System.currentTimeMillis());
        Intent intent = new Intent(SdhmsBridge.ACTION_REQUEST);
        intent.setPackage(SdhmsBridge.TARGET_PACKAGE);
        intent.putExtra(SdhmsBridge.EXTRA_REQUEST_ID, lastRequestId);
        intent.putExtra(SdhmsBridge.EXTRA_COMMAND, command);
        return intent;
    }

    private void sendCommandIntent(Intent intent) {
        statusText.setText("已发送请求，等待 SDHMS 响应...");
        sendBroadcast(intent);
        final String requestId = lastRequestId;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (requestId != null && requestId.equals(lastRequestId)) {
                    statusText.setText("还没有收到响应。请确认 LSPosed 已勾选 Samsung Device Health Manager Service 作用域并重启。");
                }
            }
        }, 3000L);
    }

    private void applyResponse(Bundle data) {
        lastRequestId = null;
        if (data == null) {
            statusText.setText("收到空响应。");
            return;
        }
        boolean success = data.getBoolean("success", false);
        String message = data.getString("message", "");
        String error = data.getString("error", "");
        Bundle snapshot = data.getBundle("snapshot");
        statusText.setText((success ? "已连接 SDHMS Hook" : "请求失败")
                + (message.length() > 0 ? "\n" + message : "")
                + (error.length() > 0 ? "\n" + error : ""));
        if (snapshot != null) {
            applySnapshot(snapshot);
        }
    }

    private void applySnapshot(Bundle snapshot) {
        Bundle thermal = snapshot.getBundle("thermal");
        if (thermal == null) {
            thermal = new Bundle();
        }
        updatingUi = true;
        masterSwitch.setChecked(thermal.getBoolean("thermalMasterOff", false));
        brightnessSwitch.setChecked(thermal.getBoolean("brightnessLimitOff", false));
        cpSwitch.setChecked(thermal.getBoolean("cpTmOff", false));
        updatingUi = false;

        StringBuilder thermalBuilder = new StringBuilder();
        thermalBuilder.append("刷新时间: ").append(formatTime(snapshot.getLong("time", 0))).append('\n');
        thermalBuilder.append("Binder: ").append(snapshot.getString("binderClass", "null")).append('\n');
        thermalBuilder.append("Limiter 可用: ").append(thermal.getBoolean("limiterAvailable", false)).append('\n');
        thermalBuilder.append("总禁用温控降频: ").append(onOff(thermal.getBoolean("thermalMasterOff", false))).append('\n');
        thermalBuilder.append("亮度温控限制关闭: ").append(onOff(thermal.getBoolean("brightnessLimitOff", false))).append('\n');
        thermalBuilder.append("CP 温控限制关闭: ").append(onOff(thermal.getBoolean("cpTmOff", false))).append('\n');
        thermalBuilder.append("当前亮度限制: ").append(onOff(thermal.getBoolean("brightnessLimited", false))).append('\n');
        thermalBuilder.append("当前 HRR 限制: ").append(onOff(thermal.getBoolean("hrrLimited", false))).append('\n');
        thermalBuilder.append("当前 CP Low Mode: ").append(onOff(thermal.getBoolean("cpLowMode", false))).append('\n');
        thermalBuilder.append("当前 CP Cooling: ").append(onOff(thermal.getBoolean("cpCoolingDown", false))).append('\n');
        thermalBuilder.append("ThermalControlFlag: ").append(thermal.getInt("thermalControlFlag", -1)).append('\n');
        thermalBuilder.append("ThrottlingDelta: ").append(thermal.getInt("thermalThrottlingDelta", 0)).append('\n');
        ArrayList<String> history = thermal.getStringArrayList("history");
        if (history != null && !history.isEmpty()) {
            thermalBuilder.append("\n历史:\n").append(joinRows(history));
        }
        thermalText.setText(thermalBuilder.toString());

        temperatureText.setText(joinRows(snapshot.getStringArrayList("temperatures")));
        renderAppRows(batteryList, snapshot.getStringArrayList("batteryRows"));
        renderAppRows(anomalyList, snapshot.getStringArrayList("anomalyRows"));
        renderAppRows(highCpuList, snapshot.getStringArrayList("highCpuRows"));
        renderAppRows(fasList, snapshot.getStringArrayList("fasRows"));
    }

    private void renderAppRows(LinearLayout container, ArrayList<String> rows) {
        container.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            TextView empty = text("暂无数据", 13, Color.rgb(46, 52, 62), Typeface.MONOSPACE);
            empty.setPadding(dp(12), dp(10), dp(12), dp(10));
            container.addView(empty, matchWrap());
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            addAppRow(container, rows.get(i), i);
        }
    }

    private void addAppRow(LinearLayout container, String rowText, int index) {
        String packageName = extractPackageName(rowText);
        if (packageName == null) {
            packageName = packageForUid(extractIntValue(rowText, "uid"));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundColor(index % 2 == 0 ? Color.WHITE : Color.rgb(250, 251, 253));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageDrawable(iconForPackage(packageName));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        String title = packageName == null ? "系统记录" : labelForPackage(packageName);
        TextView name = text(title, 15, Color.rgb(24, 29, 36), Typeface.DEFAULT_BOLD);
        texts.addView(name, matchWrap());

        if (packageName != null) {
            TextView pkg = text(packageName, 12, Color.rgb(95, 102, 116), Typeface.MONOSPACE);
            pkg.setPadding(0, dp(2), 0, dp(2));
            texts.addView(pkg, matchWrap());
        }

        TextView detail = text(rowText == null ? "" : rowText, 12, Color.rgb(68, 74, 86), Typeface.MONOSPACE);
        detail.setPadding(0, dp(4), 0, 0);
        texts.addView(detail, matchWrap());

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(row, matchWrap());
    }

    private String extractPackageName(String rowText) {
        String[] keys = {"package_name", "packageName", "pkgName", "pkg", "package"};
        for (int i = 0; i < keys.length; i++) {
            String value = extractValue(rowText, keys[i]);
            if (value != null && value.indexOf('.') > 0) {
                return value;
            }
        }
        return null;
    }

    private String extractValue(String rowText, String key) {
        if (rowText == null || key == null) {
            return null;
        }
        String prefix = key + "=";
        int start = rowText.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        start += prefix.length();
        int end = rowText.indexOf(" | ", start);
        if (end < 0) {
            end = rowText.length();
        }
        String value = rowText.substring(start, end).trim();
        return value.length() == 0 || "null".equals(value) ? null : value;
    }

    private int extractIntValue(String rowText, String key) {
        String value = extractValue(rowText, key);
        if (value == null) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private String packageForUid(int uid) {
        if (uid <= 0) {
            return null;
        }
        try {
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return packages[0];
            }
        } catch (Throwable ignored) {
            // Leave this row as a system record.
        }
        return null;
    }

    private Drawable iconForPackage(String packageName) {
        PackageManager pm = getPackageManager();
        if (packageName != null) {
            try {
                return pm.getApplicationIcon(packageName);
            } catch (Throwable ignored) {
                // Fall back below.
            }
        }
        return pm.getDefaultActivityIcon();
    }

    private String labelForPackage(String packageName) {
        if (packageName == null) {
            return "系统记录";
        }
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null) {
                return label.toString();
            }
        } catch (Throwable ignored) {
            // Package may no longer be installed or hidden by package visibility.
        }
        return packageName;
    }

    private String joinRows(ArrayList<String> rows) {
        if (rows == null || rows.isEmpty()) {
            return "暂无数据";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(rows.get(i));
        }
        return builder.toString();
    }

    private String formatTime(long time) {
        if (time <= 0) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(time));
    }

    private String onOff(boolean value) {
        return value ? "是" : "否";
    }

    private TextView text(String value, int sp, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setLineSpacing(dp(2), 1.0f);
        return view;
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
