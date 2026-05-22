package com.samsung.feature.extension.passthrough;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import com.samsung.feature.extension.LogSettingsProvider;
import com.samsung.feature.extension.PassThroughChargingSettingsProvider;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class PassThroughChargingHook implements IXposedHookLoadPackage {
    private static final String TAG = "PassThroughCharging";
    private static final String SDHMS_PACKAGE = "com.sec.android.sdhms";
    private static final String GAMETOOLS_PACKAGE = "com.samsung.android.game.gametools";
    private static final Uri SETTINGS_URI = PassThroughChargingSettingsProvider.URI;
    private static final String METHOD_GET = PassThroughChargingSettingsProvider.METHOD_GET;
    private static final String EXTRA_ENABLED = PassThroughChargingSettingsProvider.EXTRA_ENABLED;
    private static final String SYSTEM_SETTING = PassThroughChargingSettingsProvider.SYSTEM_SETTING;
    private static final long CACHE_MS = 1200L;
    private static final long KEEPALIVE_MS = 15000L;
    private static final long FIRST_REAPPLY_MS = 1200L;

    private static volatile Context appContext;
    private static volatile Handler mainHandler;
    private static volatile boolean observerRegistered;
    private static volatile boolean receiverRegistered;
    private static volatile long lastReadAt;
    private static volatile boolean lastEnabled;
    private static volatile boolean lastAppliedEnabled;

    private static final Runnable keepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isEnabled()) {
                return;
            }
            applySystemSetting(true, "keepalive");
            scheduleKeepAlive(KEEPALIVE_MS);
        }
    };

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || (!SDHMS_PACKAGE.equals(lpparam.packageName)
                && !GAMETOOLS_PACKAGE.equals(lpparam.packageName))) {
            return;
        }
        try {
            log("loading in " + lpparam.packageName + ", process=" + lpparam.processName);
            hookApplicationOnCreate();
        } catch (Throwable throwable) {
            log("hook failed: " + throwable);
            log(throwable);
        }
    }

    private static void hookApplicationOnCreate() throws Exception {
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Application)) {
                    return;
                }
                appContext = ((Application) param.thisObject).getApplicationContext();
                mainHandler = new Handler(Looper.getMainLooper());
                registerSettingsObserver(appContext);
                registerPowerReceiver(appContext);
                refreshPolicy("application-created", true);
            }
        });
        log("Application.onCreate hook installed");
    }

    private static void registerSettingsObserver(Context context) {
        if (context == null || observerRegistered) {
            return;
        }
        try {
            observerRegistered = true;
            context.getContentResolver().registerContentObserver(
                    SETTINGS_URI,
                    false,
                    new ContentObserver(mainHandler()) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            lastReadAt = 0L;
                            refreshPolicy("setting-changed", true);
                        }
                    });
            log("settings observer registered");
        } catch (Throwable throwable) {
            observerRegistered = false;
            log("settings observer failed: " + throwable);
        }
    }

    private static void registerPowerReceiver(Context context) {
        if (context == null || receiverRegistered) {
            return;
        }
        try {
            receiverRegistered = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    refreshPolicy(intent.getAction(), false);
                }
            }, filter);
            log("power receiver registered");
        } catch (Throwable throwable) {
            receiverRegistered = false;
            log("power receiver failed: " + throwable);
        }
    }

    private static void refreshPolicy(String reason, boolean allowDisableWrite) {
        boolean enabled = isEnabled();
        if (enabled) {
            applySystemSetting(true, reason);
            scheduleKeepAlive(FIRST_REAPPLY_MS);
            return;
        }
        cancelKeepAlive();
        if (allowDisableWrite || lastAppliedEnabled) {
            applySystemSetting(false, reason);
        }
    }

    private static void applySystemSetting(boolean enabled, String reason) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            int target = enabled ? 1 : 0;
            int current = Settings.System.getInt(context.getContentResolver(), SYSTEM_SETTING, 0);
            if (current != target) {
                Settings.System.putInt(context.getContentResolver(), SYSTEM_SETTING, target);
                log("set " + SYSTEM_SETTING + "=" + target + ", reason=" + reason);
            }
            lastAppliedEnabled = enabled;
        } catch (Throwable throwable) {
            log("set " + SYSTEM_SETTING + " failed, enabled=" + enabled
                    + ", reason=" + reason + ": " + throwable);
        }
    }

    private static boolean isEnabled() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastReadAt < CACHE_MS) {
            return lastEnabled;
        }
        lastReadAt = now;
        Context context = appContext;
        if (context == null) {
            lastEnabled = false;
            return false;
        }
        try {
            Bundle result = context.getContentResolver().call(SETTINGS_URI, METHOD_GET, null, null);
            lastEnabled = result != null && result.getBoolean(EXTRA_ENABLED, false);
        } catch (Throwable throwable) {
            lastEnabled = false;
            log("read setting failed: " + throwable);
        }
        return lastEnabled;
    }

    private static void scheduleKeepAlive(long delayMs) {
        Handler handler = mainHandler();
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(keepAliveRunnable);
        handler.postDelayed(keepAliveRunnable, Math.max(1000L, delayMs));
    }

    private static void cancelKeepAlive() {
        Handler handler = mainHandler();
        if (handler != null) {
            handler.removeCallbacks(keepAliveRunnable);
        }
    }

    private static Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler != null) {
            return handler;
        }
        try {
            handler = new Handler(Looper.getMainLooper());
            mainHandler = handler;
            return handler;
        } catch (Throwable throwable) {
            log("main handler unavailable: " + throwable);
            return null;
        }
    }

    private static void log(String message) {
        if (!LogSettingsProvider.isLogEnabled(appContext)) {
            return;
        }
        XposedBridge.log(TAG + ": " + message);
    }

    private static void log(Throwable throwable) {
        if (!LogSettingsProvider.isLogEnabled(appContext)) {
            return;
        }
        XposedBridge.log(throwable);
    }
}
