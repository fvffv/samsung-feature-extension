package com.samsung.feature.extension.gos;

import android.app.Application;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.PendingIntent;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Opens GOS's built-in test activity from inside the GOS process.
 *
 * <p>The activity is deliberately non-exported. Its own test-mode gate is enabled immediately
 * before launch and GOS clears the flag again when the activity is destroyed.</p>
 */
public final class GosTestPanelHook implements IXposedHookLoadPackage {
    private static final String TAG = "SFE.GosTestPanel";
    private static final String TEST_MODE_CLASS = "com.samsung.android.game.gos.data.l";
    private static volatile boolean receiverInstalled;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !GosTestPanelBridge.TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        try {
            Method onCreate = Application.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Application)) {
                        return;
                    }
                    Application application = (Application) param.thisObject;
                    if (!GosTestPanelBridge.TARGET_PACKAGE.equals(application.getPackageName())) {
                        return;
                    }
                    GosPanelLocalization.install(application);
                    installReceiver(application.getApplicationContext());
                }
            });
        } catch (Throwable throwable) {
            log("failed to hook Application.onCreate: " + throwable.getClass().getSimpleName());
        }
    }

    private static void installReceiver(final Context context) {
        if (context == null || receiverInstalled) {
            return;
        }
        receiverInstalled = true;
        IntentFilter filter = new IntentFilter(GosTestPanelBridge.ACTION_OPEN_TEST_PANEL);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context receiverContext, final Intent intent) {
                if (intent == null
                        || !GosTestPanelBridge.ACTION_OPEN_TEST_PANEL.equals(intent.getAction())) {
                    return;
                }
                final String requestId = intent.getStringExtra(GosTestPanelBridge.EXTRA_REQUEST_ID);
                final boolean useEnglish = intent.getBooleanExtra(
                        GosTestPanelBridge.EXTRA_USE_ENGLISH, false);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        boolean success = false;
                        String message;
                        PendingIntent panelPendingIntent = null;
                        try {
                            GosPanelLocalization.setUseEnglish(useEnglish);
                            enableTestMode(context);
                            Intent panelIntent = new Intent();
                            panelIntent.setComponent(new ComponentName(
                                    GosTestPanelBridge.TARGET_PACKAGE,
                                    GosTestPanelBridge.TEST_ACTIVITY));
                            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            int requestCode = requestId == null ? 0 : requestId.hashCode();
                            Bundle pendingIntentOptions = null;
                            if (Build.VERSION.SDK_INT >= 34) {
                                ActivityOptions options = ActivityOptions.makeBasic();
                                options.setPendingIntentCreatorBackgroundActivityStartMode(
                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                                pendingIntentOptions = options.toBundle();
                            }
                            panelPendingIntent = PendingIntent.getActivity(
                                    context,
                                    requestCode,
                                    panelIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                                    pendingIntentOptions);
                            success = true;
                            message = "GOS 内置测试面板已准备，由前台页面触发打开。";
                        } catch (Throwable throwable) {
                            clearTestMode(context);
                            message = "无法启动 GOS 测试面板："
                                    + throwable.getClass().getSimpleName();
                            log(message);
                        }
                        sendResult(context, requestId, success, message, panelPendingIntent);
                    }
                });
            }

        };
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            log("command receiver installed");
        } catch (Throwable throwable) {
            receiverInstalled = false;
            log("failed to install command receiver: " + throwable.getClass().getSimpleName());
        }
    }

    private static void enableTestMode(Context context) throws Exception {
        setTestMode(context, true);
    }

    private static void clearTestMode(Context context) {
        try {
            setTestMode(context, false);
        } catch (Throwable ignored) {
            // Best effort only: a successfully opened TestActivity clears it on destroy.
        }
    }

    private static void setTestMode(Context context, boolean enabled) throws Exception {
        ClassLoader loader = context != null
                ? context.getClassLoader()
                : GosTestPanelHook.class.getClassLoader();
        Class<?> testMode = Class.forName(TEST_MODE_CLASS, false, loader);
        Method setTestMode = testMode.getDeclaredMethod("c", Boolean.TYPE);
        setTestMode.setAccessible(true);
        setTestMode.invoke(null, Boolean.valueOf(enabled));
    }

    private static void sendResult(Context context, String requestId, boolean success, String message,
            PendingIntent panelPendingIntent) {
        try {
            Intent response = new Intent(GosTestPanelBridge.ACTION_OPEN_TEST_PANEL_RESULT);
            response.setPackage(GosTestPanelBridge.MODULE_PACKAGE);
            response.putExtra(GosTestPanelBridge.EXTRA_REQUEST_ID, requestId);
            response.putExtra(GosTestPanelBridge.EXTRA_SUCCESS, success);
            response.putExtra(GosTestPanelBridge.EXTRA_MESSAGE, message);
            if (panelPendingIntent != null) {
                response.putExtra(GosTestPanelBridge.EXTRA_PENDING_INTENT, panelPendingIntent);
            }
            context.sendBroadcast(response);
        } catch (Throwable ignored) {
            // The panel launch must not depend on the module activity remaining alive.
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
