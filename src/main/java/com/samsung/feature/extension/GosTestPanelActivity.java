package com.samsung.feature.extension;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.samsung.feature.extension.gos.GosTestPanelBridge;

import java.util.UUID;

/** Launch screen for the built-in, non-exported GOS engineering panel. */
public final class GosTestPanelActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView statusView;
    private Button launchButton;
    private String requestId;
    private boolean serviceBound;
    private boolean responseReceived;

    private final ServiceConnection gosServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            sendLaunchRequest();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !GosTestPanelBridge.ACTION_OPEN_TEST_PANEL_RESULT.equals(intent.getAction())) {
                return;
            }
            String receivedId = intent.getStringExtra(GosTestPanelBridge.EXTRA_REQUEST_ID);
            if (requestId == null || !requestId.equals(receivedId)) {
                return;
            }
            responseReceived = true;
            boolean success = intent.getBooleanExtra(GosTestPanelBridge.EXTRA_SUCCESS, false);
            String message = intent.getStringExtra(GosTestPanelBridge.EXTRA_MESSAGE);
            if (success) {
                PendingIntent pendingIntent = readPendingIntent(intent);
                try {
                    if (pendingIntent == null) {
                        throw new IllegalStateException("GOS 未返回界面启动凭据");
                    }
                    sendPendingIntent(pendingIntent);
                    setStatus("GOS 内置测试面板已打开。", Color.rgb(34, 120, 72));
                } catch (Throwable throwable) {
                    setStatus("无法从前台页面打开 GOS 测试面板："
                            + throwable.getClass().getSimpleName(), Color.rgb(180, 71, 51));
                }
            } else {
                setStatus(message == null ? "启动失败。" : message, Color.rgb(180, 71, 51));
            }
            if (launchButton != null) {
                launchButton.setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(LanguageManager.text(this, "GOS 隐藏面板"));
        buildUi();
        registerResultReceiver();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(resultReceiver);
        } catch (Throwable ignored) {
            // The receiver may not have been registered if Activity creation failed.
        }
        if (serviceBound) {
            try {
                unbindService(gosServiceConnection);
            } catch (Throwable ignored) {
                // The remote process may already have exited.
            }
            serviceBound = false;
        }
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("GOS 隐藏面板", 24, Color.rgb(20, 24, 31), Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView description = text(
                "打开 Game Optimizing Service 内置的工程测试面板，用于查看游戏性能策略、设备信息和当前特性开关。",
                15, Color.rgb(82, 90, 104), Typeface.DEFAULT);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(10), 0, dp(14));
        root.addView(description, matchWrap());

        TextView warning = text(
                "仅建议查看。面板中的 Fake device name、固定 FPS、JSON/SIOP policy、CPU/GPU lock 等为三星工程调试项，修改后可能影响游戏稳定性；本模块不会替你启用这些项目。",
                13, Color.rgb(141, 84, 8), Typeface.DEFAULT_BOLD);
        warning.setLineSpacing(dp(2), 1.0f);
        warning.setPadding(dp(12), dp(10), dp(12), dp(10));
        warning.setBackgroundColor(Color.rgb(255, 244, 214));
        root.addView(warning, matchWrap());

        launchButton = new Button(this);
        launchButton.setText(LanguageManager.text(this, "打开 GOS 测试面板"));
        launchButton.setAllCaps(false);
        launchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPanelLaunch();
            }
        });
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(16);
        root.addView(launchButton, buttonParams);

        statusView = text(
                "准备就绪。若首次启动失败，请在 LSPosed 中勾选 Game Optimizing Service 作用域后重启 GOS 或重启手机。",
                13, Color.rgb(91, 99, 112), Typeface.DEFAULT);
        statusView.setLineSpacing(dp(2), 1.0f);
        statusView.setPadding(0, dp(10), 0, dp(16));
        root.addView(statusView, matchWrap());

        addSection(root, "面板包含的页面", new String[]{
                "Device info：设备与当前 GOS 状态",
                "Global feature flags：全局特性开关",
                "Common / JSON features：常用与 JSON 策略测试",
                "SPA、GFI、SIOP Mode：性能、插帧与游戏温控策略",
                "Resume Boost、Update alarm、Galaxy Apps：恢复加速、更新与游戏包数据"
        });

        setContentView(scrollView);
        LanguageManager.applyToActivity(this);
    }

    private void requestPanelLaunch() {
        requestId = UUID.randomUUID().toString();
        responseReceived = false;
        if (launchButton != null) {
            launchButton.setEnabled(false);
        }
        setStatus("正在连接 GOS 并请求打开测试面板...", Color.rgb(47, 99, 180));
        sendLaunchRequest();
        ensureGosServiceStarted();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!responseReceived) {
                    sendLaunchRequest();
                }
            }
        }, 700L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!responseReceived) {
                    sendLaunchRequest();
                }
            }
        }, 1600L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!responseReceived) {
                    setStatus("未收到 GOS 响应。请确认 LSPosed 已勾选 Game Optimizing Service 作用域，并重启 GOS 或手机后重试。",
                            Color.rgb(180, 71, 51));
                    if (launchButton != null) {
                        launchButton.setEnabled(true);
                    }
                }
            }
        }, 3000L);
    }

    private void ensureGosServiceStarted() {
        if (serviceBound) {
            return;
        }
        try {
            Intent serviceIntent = new Intent();
            serviceIntent.setComponent(new ComponentName(
                    GosTestPanelBridge.TARGET_PACKAGE,
                    GosTestPanelBridge.TARGET_SERVICE));
            serviceBound = bindService(serviceIntent, gosServiceConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {
            // The broadcast retries still work when GOS is already running.
        }
    }

    private void sendLaunchRequest() {
        if (requestId == null) {
            return;
        }
        try {
            Intent request = new Intent(GosTestPanelBridge.ACTION_OPEN_TEST_PANEL);
            request.setPackage(GosTestPanelBridge.TARGET_PACKAGE);
            request.putExtra(GosTestPanelBridge.EXTRA_REQUEST_ID, requestId);
            request.putExtra(GosTestPanelBridge.EXTRA_USE_ENGLISH,
                    LanguageManager.isEnglish(this));
            sendBroadcast(request);
        } catch (Throwable ignored) {
            // A later retry can still succeed after GOS finishes starting.
        }
    }

    private void registerResultReceiver() {
        IntentFilter filter = new IntentFilter(GosTestPanelBridge.ACTION_OPEN_TEST_PANEL_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(resultReceiver, filter);
        }
    }

    private PendingIntent readPendingIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(
                    GosTestPanelBridge.EXTRA_PENDING_INTENT, PendingIntent.class);
        }
        return (PendingIntent) intent.getParcelableExtra(
                GosTestPanelBridge.EXTRA_PENDING_INTENT);
    }

    private void sendPendingIntent(PendingIntent pendingIntent) throws Exception {
        Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            optionsBundle = options.toBundle();
        }
        pendingIntent.send(this, 0, null, null, null, null, optionsBundle);
    }

    private void addSection(LinearLayout root, String title, String[] entries) {
        TextView heading = text(title, 17, Color.rgb(28, 34, 42), Typeface.DEFAULT_BOLD);
        heading.setPadding(0, dp(8), 0, dp(8));
        root.addView(heading, matchWrap());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);
        for (String entry : entries) {
            TextView row = text("• " + entry, 14, Color.rgb(69, 77, 90), Typeface.DEFAULT);
            row.setLineSpacing(dp(2), 1.0f);
            row.setPadding(0, 0, 0, dp(8));
            card.addView(row, matchWrap());
        }
        root.addView(card, matchWrap());
    }

    private void setStatus(String value, int color) {
        if (statusView != null) {
            statusView.setText(LanguageManager.text(this, value));
            statusView.setTextColor(color);
        }
    }

    private TextView text(String value, float size, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(LanguageManager.text(this, value));
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(typeface);
        return view;
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
