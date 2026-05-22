package com.samsung.feature.extension.touchsampling;

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
import android.os.PowerManager;

import com.samsung.feature.extension.LogSettingsProvider;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class TouchSamplingHook implements IXposedHookLoadPackage {
    private static final String TAG = "TouchSampling";
    private static final String SDHMS_PACKAGE = "com.sec.android.sdhms";
    private static final String GOS_TSP_ACTION = "com.samsung.android.game.gos.action.TSP";
    private static final String SET_GAME_MODE = "set_game_mode";
    private static final String SET_SCAN_RATE = "set_scan_rate";
    private static final String SET_FAST_RESPONSE = "set_fast_response";
    private static final String HIGH_SCAN_RATE_POLICY = "1,2";
    private static final String AUTHORITY = "com.samsung.feature.extension.touchsampling";
    private static final Uri SETTINGS_URI = Uri.parse("content://" + AUTHORITY);
    private static final String METHOD_GET = "get";
    private static final String EXTRA_ENABLED = "enabled";
    private static final long CACHE_MS = 2000L;
    private static final long KEEPALIVE_MS = 10000L;
    private static final long FIRST_REAPPLY_MS = 1500L;
    private static final long SCREEN_ON_REENABLE_DELAY_MS = 350L;

    private static volatile Context appContext;
    private static volatile boolean observerRegistered;
    private static volatile boolean screenReceiverRegistered;
    private static volatile Handler mainHandler;
    private static volatile boolean keepAliveScheduled;
    private static volatile long lastReadAt;
    private static volatile boolean lastEnabled;
    private static volatile boolean screenWasOff;

    private static final Runnable keepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            keepAliveScheduled = false;
            boolean enabled = isEnabled();
            if (!enabled) {
                return;
            }
            if (isDeviceInteractive(appContext)) {
                sendTouchPolicy(true, "keepalive", false);
            }
            scheduleKeepAlive(KEEPALIVE_MS);
        }
    };

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !SDHMS_PACKAGE.equals(lpparam.packageName)) {
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
                registerSettingObserver(appContext);
                registerScreenReceiver(appContext);
                refreshTouchPolicy("application-created");
            }
        });
        log("Application.onCreate hook installed");
    }

    private static void registerSettingObserver(Context context) {
        if (context == null || observerRegistered) {
            return;
        }
        try {
            observerRegistered = true;
            Handler handler = new Handler(Looper.getMainLooper());
            context.getContentResolver().registerContentObserver(SETTINGS_URI, false, new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    lastReadAt = 0L;
                    refreshTouchPolicy("setting-changed");
                }
            });
            log("setting observer registered");
        } catch (Throwable throwable) {
            observerRegistered = false;
            log("setting observer failed: " + throwable);
        }
    }

    private static void registerScreenReceiver(Context context) {
        if (context == null || screenReceiverRegistered) {
            return;
        }
        try {
            screenReceiverRegistered = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    handleScreenAction(intent.getAction());
                }
            }, filter);
            log("screen receiver registered");
        } catch (Throwable throwable) {
            screenReceiverRegistered = false;
            log("screen receiver failed: " + throwable);
        }
    }

    private static void handleScreenAction(String action) {
        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            screenWasOff = true;
            log("screen off observed");
            return;
        }
        if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
            if (screenWasOff) {
                screenWasOff = false;
                simulateEnableSwitchAfterScreenOn(action);
                return;
            }
        }
        refreshTouchPolicy(action);
    }

    private static void simulateEnableSwitchAfterScreenOn(String reason) {
        if (!isEnabled()) {
            refreshTouchPolicy(reason);
            return;
        }
        log("screen on pulse started, reason=" + reason);
        sendTouchPolicy(false, reason + "-pulse-off", true);
        Handler handler = getMainHandler();
        if (handler == null) {
            sendTouchPolicy(true, reason + "-pulse-on", true);
            scheduleKeepAlive(FIRST_REAPPLY_MS);
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isEnabled()) {
                    refreshTouchPolicy("screen-on-pulse-cancelled");
                    return;
                }
                sendTouchPolicy(true, "screen-on-pulse-on", true);
                scheduleKeepAlive(FIRST_REAPPLY_MS);
            }
        }, SCREEN_ON_REENABLE_DELAY_MS);
    }

    private static void refreshTouchPolicy(String reason) {
        boolean enabled = isEnabled();
        sendTouchPolicy(enabled, reason, true);
        if (enabled) {
            scheduleKeepAlive(FIRST_REAPPLY_MS);
        } else {
            cancelKeepAlive();
        }
    }

    private static void sendTouchPolicy(boolean enabled, String reason, boolean verboseLog) {
        Context context = appContext;
        if (context == null) {
            log("policy skipped, context=null, reason=" + reason);
            return;
        }
        try {
            Intent intent = new Intent(GOS_TSP_ACTION);
            intent.putExtra(SET_GAME_MODE, enabled ? "1" : "0");
            intent.putExtra(SET_SCAN_RATE, enabled ? HIGH_SCAN_RATE_POLICY : "0");
            intent.putExtra(SET_FAST_RESPONSE, enabled ? "1" : "0");
            context.sendBroadcast(intent);
            if (verboseLog) {
                log("GOS TSP broadcast sent enabled=" + enabled
                        + ", scanRate=" + (enabled ? HIGH_SCAN_RATE_POLICY : "0")
                        + ", reason=" + reason);
            }
        } catch (Throwable throwable) {
            log("GOS TSP broadcast failed, reason=" + reason + ": " + throwable);
            log(throwable);
        }
    }

    private static void scheduleKeepAlive(long delayMs) {
        Handler handler = getMainHandler();
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(keepAliveRunnable);
        keepAliveScheduled = true;
        handler.postDelayed(keepAliveRunnable, Math.max(1000L, delayMs));
    }

    private static void cancelKeepAlive() {
        Handler handler = getMainHandler();
        if (handler != null) {
            handler.removeCallbacks(keepAliveRunnable);
        }
        keepAliveScheduled = false;
    }

    private static Handler getMainHandler() {
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

    private static boolean isDeviceInteractive(Context context) {
        if (context == null) {
            return true;
        }
        try {
            Object service = context.getSystemService(Context.POWER_SERVICE);
            if (service instanceof PowerManager) {
                return ((PowerManager) service).isInteractive();
            }
        } catch (Throwable ignored) {
            // If the state cannot be read, keep the old behavior and reapply.
        }
        return true;
    }

    private static boolean isEnabled() {
        long now = System.currentTimeMillis();
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
